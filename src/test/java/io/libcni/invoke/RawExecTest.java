package io.libcni.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.libcni.types.CniError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawExecTest {

    @TempDir
    Path dir;

    private Path script(String name, String body) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, "#!/bin/sh\n" + body + "\n");
        p.toFile().setExecutable(true);
        return p;
    }

    @Test
    void echoesStdinToStdout() {
        RawExec re = new RawExec();
        byte[] out = re.execPlugin("/bin/cat", "hello".getBytes(StandardCharsets.UTF_8), Map.of());
        assertEquals("hello", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void passesEnvironmentVariables() throws IOException {
        Path p = script("env-plugin", "printf %s \"$CNI_COMMAND:$CNI_IFNAME\"");
        Map<String, String> env = Map.of("CNI_COMMAND", "ADD", "CNI_IFNAME", "eth0");

        byte[] out = new RawExec().execPlugin(p.toString(), new byte[0], env);

        assertEquals("ADD:eth0", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void parsesPluginErrorJsonOnNonZeroExit() throws IOException {
        Path p = script("err-plugin", "echo '{\"code\":7,\"msg\":\"boom\",\"details\":\"d\"}'\nexit 1");

        CniError e = assertThrows(CniError.class,
            () -> new RawExec().execPlugin(p.toString(), new byte[0], Map.of()));

        assertEquals(7, e.code());
        assertEquals("boom", e.msg());
        assertEquals("d", e.details());
    }

    @Test
    void wrapsStderrWhenNoStdout() throws IOException {
        Path p = script("err2-plugin", "echo 'something bad' >&2\nexit 1");

        CniError e = assertThrows(CniError.class,
            () -> new RawExec().execPlugin(p.toString(), new byte[0], Map.of()));

        assertTrue(e.getMessage().contains("something bad"));
    }

    @Test
    void timeoutStopsHungPlugin() throws IOException {
        Path p = script("hang-plugin", "while true; do :; done");

        RawExec re = new RawExec(500);
        long start = System.nanoTime();
        assertThrows(CniError.class, () -> re.execPlugin(p.toString(), new byte[0], Map.of()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 10_000, "hung plugin took " + elapsedMs + "ms to stop");
    }

    @Test
    void largeOutputOnBothStreamsDoesNotDeadlock() throws IOException {
        Path p = script("big-plugin",
            "head -c 200000 /dev/zero | tr '\\0' 'a'\n"
                + "head -c 200000 /dev/zero | tr '\\0' 'b' >&2\n");

        RawExec re = new RawExec();
        re.stderr = null;
        byte[] out = re.execPlugin(p.toString(), new byte[0], Map.of());

        assertEquals(200000, out.length);
    }

    @Test
    void largeStdinToPluginThatIgnoresIt() throws IOException {
        Path p = script("ignore-plugin", "echo done");

        byte[] big = new byte[200000];
        Arrays.fill(big, (byte) 'x');
        byte[] out = new RawExec().execPlugin(p.toString(), big, Map.of());

        assertEquals("done\n", new String(out, StandardCharsets.UTF_8));
    }
}
