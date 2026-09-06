package io.libcni.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.libcni.types.CniError;
import io.libcni.types.CurrentResult;
import io.libcni.types.Result;
import io.libcni.version.PluginInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvokeTest {

    private static class FakeExec implements Exec {
        byte[] stdout = new byte[0];
        PluginInfo versionInfo;
        CniError errorToThrow;
        String lastStdin;
        Map<String, String> lastEnv;

        @Override
        public byte[] execPlugin(String pluginPath, byte[] stdinData, Map<String, String> environ) {
            if (errorToThrow != null) {
                throw errorToThrow;
            }
            lastStdin = new String(stdinData, StandardCharsets.UTF_8);
            lastEnv = environ;
            return stdout;
        }

        @Override
        public String findInPath(String plugin, List<String> paths) {
            return "/fake/" + plugin;
        }

        @Override
        public PluginInfo decode(byte[] jsonBytes) {
            return versionInfo;
        }
    }

    private static Args addArgs() {
        Args args = new Args();
        args.command = "ADD";
        args.containerID = "cid";
        args.ifName = "eth0";
        return args;
    }

    @Test
    void execPluginWithResultParsesResult() {
        FakeExec fe = new FakeExec();
        fe.stdout = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}"
            .getBytes(StandardCharsets.UTF_8);

        Result r = Invoke.execPluginWithResult("/x/bridge", "{\"cniVersion\":\"0.4.0\"}", addArgs(), fe);

        assertEquals("0.4.0", r.version());
        assertEquals("10.0.0.2/24", ((CurrentResult) r).ips.get(0).address);
        assertEquals("ADD", fe.lastEnv.get("CNI_COMMAND"));
    }

    @Test
    void execPluginWithResultFixesUpMissingVersion() {
        FakeExec fe = new FakeExec();
        fe.stdout = "{\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}"
            .getBytes(StandardCharsets.UTF_8);

        Result r = Invoke.execPluginWithResult("/x/bridge", "{\"cniVersion\":\"0.4.0\"}", addArgs(), fe);

        assertEquals("0.4.0", r.version());
    }

    @Test
    void fixupResultVersionKeepsExplicitVersion() {
        String[] r = Invoke.fixupResultVersion("{\"cniVersion\":\"0.4.0\"}", "{\"cniVersion\":\"0.3.1\"}");
        assertEquals("0.3.1", r[0]);
    }

    @Test
    void fixupResultVersionInjectsConfigVersionWhenMissing() {
        String[] r = Invoke.fixupResultVersion("{\"cniVersion\":\"0.4.0\"}", "{\"ips\":[]}");
        assertEquals("0.4.0", r[0]);
        assertTrue(r[1].contains("\"cniVersion\":\"0.4.0\""));
    }

    @Test
    void getVersionInfoDecodesPluginResponse() {
        FakeExec fe = new FakeExec();
        fe.versionInfo = PluginInfo.pluginSupports("0.4.0");

        PluginInfo pi = Invoke.getVersionInfo("/x/bridge", fe);

        assertEquals(List.of("0.4.0"), pi.supportedVersions());
    }

    @Test
    void getVersionInfoReturns010ForUnknownVersionCommand() {
        FakeExec fe = new FakeExec();
        fe.errorToThrow = new CniError(0, "unknown CNI_COMMAND: VERSION", "");

        PluginInfo pi = Invoke.getVersionInfo("/x/bridge", fe);

        assertEquals(List.of("0.1.0"), pi.supportedVersions());
    }

    @Test
    void fixupResultVersionRejectsNonObjectResponses() {
        String netconf = "{\"cniVersion\":\"0.4.0\"}";
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, ""));
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, "   "));
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, "[]"));
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, "42"));
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, "null"));
        assertThrows(CniError.class, () -> Invoke.fixupResultVersion(netconf, "{bad"));
    }

    @Test
    void fixupResultVersionAcceptsEmptyObject() {
        String[] r = Invoke.fixupResultVersion("{\"cniVersion\":\"0.4.0\"}", "{}");
        assertEquals("0.4.0", r[0]);
    }

    @Test
    void execPluginWithResultWrapsInvalidResultWithPluginContext() {
        FakeExec fe = new FakeExec();
        fe.stdout = "[]".getBytes(StandardCharsets.UTF_8);

        CniError e = assertThrows(CniError.class,
            () -> Invoke.execPluginWithResult("/x/bridge", "{\"cniVersion\":\"0.4.0\"}", addArgs(), fe));

        assertTrue(e.getMessage().contains("bridge"));
    }
}
