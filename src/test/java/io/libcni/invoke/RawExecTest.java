package io.libcni.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.libcni.types.CniError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
