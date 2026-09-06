package io.libcni.invoke;

import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Executes CNI plugins as subprocesses, mirroring {@code invoke.RawExec} in libcni.
 */
public class RawExec {

    /** Where a plugin's stderr is copied on success; informational only. */
    public PrintStream stderr = System.err;

    public RawExec() {
    }

    /**
     * Runs the plugin, feeding {@code stdinData} on stdin and returning stdout.
     * Retries on "text file busy" errors, and turns a failed plugin run into a
     * {@link CniError} parsed from the plugin's diagnostic output.
     */
    public byte[] execPlugin(String pluginPath, byte[] stdinData, Map<String, String> environ) {
        for (int attempt = 0; attempt <= 5; attempt++) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(pluginPath);
                if (environ != null && !environ.isEmpty()) {
                    pb.environment().putAll(environ);
                }
                process = pb.start();
                final Process proc = process;

                try (OutputStream os = proc.getOutputStream()) {
                    os.write(stdinData);
                }

                Thread stderrReader = new Thread(() -> {
                    try (InputStream es = proc.getErrorStream()) {
                        es.transferTo(stderrBuf);
                    } catch (IOException ignored) {
                    }
                });
                stderrReader.start();
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(stdout);
                }
                stderrReader.join();

                int exit = process.waitFor();
                byte[] out = stdout.toByteArray();
                byte[] err = stderrBuf.toByteArray();

                if (exit == 0) {
                    if (stderr != null && err.length > 0) {
                        stderr.print(new String(err, StandardCharsets.UTF_8));
                    }
                    return out;
                }
                throw pluginErr(new IOException("plugin exited with status " + exit), out, err);
            } catch (IOException e) {
                if (attempt < 5 && isTextFileBusy(e)) {
                    sleepOneSecond();
                    continue;
                }
                throw pluginErr(e, stdout.toByteArray(), stderrBuf.toByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while running plugin " + pluginPath, e);
            } finally {
                if (process != null) {
                    process.destroyForcibly();
                }
            }
        }
        throw new CniError(CniErrorCode.IO_FAILURE,
            "plugin " + pluginPath + " failed after retries", "");
    }

    public String findInPath(String plugin, List<String> paths) {
        return FindInPath.find(plugin, paths);
    }

    private static boolean isTextFileBusy(IOException e) {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("text file busy");
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while retrying plugin execution", ie);
        }
    }

    private static CniError pluginErr(Exception err, byte[] stdout, byte[] stderr) {
        if (stdout.length == 0) {
            if (stderr.length == 0) {
                return new CniError(CniErrorCode.UNKNOWN,
                    "netplugin failed with no error message: " + err, "");
            }
            String errStr = new String(stderr, StandardCharsets.UTF_8);
            return new CniError(CniErrorCode.UNKNOWN,
                "netplugin failed: \"" + errStr + "\": " + err, "");
        }
        String out = new String(stdout, StandardCharsets.UTF_8);
        try {
            return CniError.fromJsonString(out);
        } catch (Exception pe) {
            return new CniError(CniErrorCode.UNKNOWN,
                "netplugin failed but error parsing its diagnostic message \"" + out + "\": " + pe, "");
        }
    }
}
