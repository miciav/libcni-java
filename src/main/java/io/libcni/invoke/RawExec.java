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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes CNI plugins as subprocesses, mirroring {@code invoke.RawExec} in libcni.
 *
 * <p>The process is driven through dedicated helper threads for stdin/stdout/stderr
 * so that the calling thread only ever performs an interruptible wait. A
 * configurable {@link #timeoutMillis} bounds the whole invocation, including
 * retries; on timeout or interruption the process and its descendants are
 * terminated.</p>
 */
public class RawExec {

    /** Where a plugin's stderr is copied on success; informational only. */
    public PrintStream stderr = System.err;

    /**
     * Maximum wall-clock time for one invocation (including "text file busy"
     * retries), in milliseconds; {@code 0} means no limit.
     */
    public long timeoutMillis = 0;

    /** How long to wait for the IO helper threads to finish after the process exits. */
    private static final long CLEANUP_TIMEOUT_MILLIS = 5_000;

    public RawExec() {
    }

    public RawExec(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public byte[] execPlugin(String pluginPath, byte[] stdinData, Map<String, String> environ) {
        long deadline = timeoutMillis <= 0
            ? Long.MAX_VALUE
            : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        for (int attempt = 0; attempt <= 5; attempt++) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw timeoutError(pluginPath);
            }

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
            AtomicReference<IOException> stdinError = new AtomicReference<>();
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(pluginPath);
                if (environ != null && !environ.isEmpty()) {
                    pb.environment().putAll(environ);
                }
                process = pb.start();
                final Process proc = process;

                Thread stdinWriter = new Thread(() -> {
                    try (OutputStream os = proc.getOutputStream()) {
                        os.write(stdinData);
                    } catch (IOException e) {
                        stdinError.set(e);
                    }
                });
                stdinWriter.setDaemon(true);
                stdinWriter.start();

                Thread stdoutReader = new Thread(() -> {
                    try (InputStream in = proc.getInputStream()) {
                        in.transferTo(stdout);
                    } catch (IOException ignored) {
                    }
                });
                stdoutReader.setDaemon(true);
                stdoutReader.start();

                Thread stderrReader = new Thread(() -> {
                    try (InputStream es = proc.getErrorStream()) {
                        es.transferTo(stderrBuf);
                    } catch (IOException ignored) {
                    }
                });
                stderrReader.setDaemon(true);
                stderrReader.start();

                boolean finished;
                if (deadline == Long.MAX_VALUE) {
                    proc.waitFor();
                    finished = true;
                } else {
                    long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                    finished = proc.waitFor(remainingMillis, TimeUnit.MILLISECONDS);
                }
                if (!finished) {
                    terminate(proc);
                    throw timeoutError(pluginPath);
                }

                stdinWriter.join(CLEANUP_TIMEOUT_MILLIS);
                stdoutReader.join(CLEANUP_TIMEOUT_MILLIS);
                stderrReader.join(CLEANUP_TIMEOUT_MILLIS);

                int exit = proc.exitValue();
                byte[] out = stdout.toByteArray();
                byte[] err = stderrBuf.toByteArray();

                if (exit == 0) {
                    IOException se = stdinError.get();
                    if (se != null && !isEarlyExit(se)) {
                        throw new CniError(CniErrorCode.IO_FAILURE, "failed to write plugin stdin: " + se, "");
                    }
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
                if (process != null) {
                    terminate(process);
                }
                throw new CniError(CniErrorCode.UNKNOWN,
                    "interrupted while running plugin " + pluginPath, "");
            } finally {
                if (process != null) {
                    process.destroyForcibly();
                }
            }
        }
        throw new CniError(CniErrorCode.IO_FAILURE, "plugin " + pluginPath + " failed after retries", "");
    }

    public String findInPath(String plugin, List<String> paths) {
        return FindInPath.find(plugin, paths);
    }

    private static CniError timeoutError(String pluginPath) {
        return new CniError(CniErrorCode.TRY_AGAIN_LATER, "plugin " + pluginPath + " timed out", "");
    }

    /** Terminates the process and, best-effort, any descendants that can still be discovered. */
    private static void terminate(Process p) {
        try {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
            // descendant tracking is best-effort
        }
        p.destroyForcibly();
    }

    private static boolean isEarlyExit(IOException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("broken pipe") || m.contains("stream closed");
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
