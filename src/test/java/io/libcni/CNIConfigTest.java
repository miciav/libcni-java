package io.libcni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.libcni.invoke.DefaultExec;
import io.libcni.types.CniError;
import io.libcni.types.Result;
import io.libcni.version.PluginInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CNIConfigTest {

    @TempDir
    Path cacheDir;

    private static final String CONFLIST =
        "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"plugins\":["
            + "{\"type\":\"bridge\"},{\"type\":\"tuning\"}]}";

    private static class Call {
        String command;
        String stdin;
        Map<String, String> env;

        Call(String command, String stdin, Map<String, String> env) {
            this.command = command;
            this.stdin = stdin;
            this.env = env;
        }
    }

    private static class FakeExec implements io.libcni.invoke.Exec {
        String resultJson = "";
        CniError error;
        PluginInfo versionInfo;
        List<Call> calls = new ArrayList<>();

        @Override
        public byte[] execPlugin(String pluginPath, byte[] stdinData, Map<String, String> environ) {
            calls.add(new Call(environ.get("CNI_COMMAND"),
                new String(stdinData, StandardCharsets.UTF_8), new LinkedHashMap<>(environ)));
            if (error != null) {
                throw error;
            }
            return resultJson.getBytes(StandardCharsets.UTF_8);
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

    private static RuntimeConf rt(String cid, String ns, String ifName) {
        RuntimeConf r = new RuntimeConf();
        r.containerID = cid;
        r.netNS = ns;
        r.ifName = ifName;
        return r;
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private CNIConfig newCni(FakeExec fe) {
        return new CNIConfig(List.of("/opt/cni/bin"), cacheDir.toString(), fe);
    }

    @Test
    void addNetworkListRunsPluginsInOrderChainingPrevResult() {
        FakeExec fe = new FakeExec();
        fe.resultJson = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);

        Result result = cni.addNetworkList(list, rt("cid", "/ns", "eth0"));

        assertEquals("0.4.0", result.version());
        assertEquals(2, fe.calls.size());

        JsonObject first = parse(fe.calls.get(0).stdin);
        assertEquals("ADD", fe.calls.get(0).command);
        assertEquals("bridge", first.get("type").getAsString());
        assertEquals("mynet", first.get("name").getAsString());
        assertFalse(first.has("prevResult"));
        assertEquals("cid", fe.calls.get(0).env.get("CNI_CONTAINERID"));

        JsonObject second = parse(fe.calls.get(1).stdin);
        assertEquals("tuning", second.get("type").getAsString());
        assertTrue(second.has("prevResult"));
        assertEquals("10.0.0.2/24",
            second.getAsJsonObject("prevResult").getAsJsonArray("ips").get(0)
                .getAsJsonObject().get("address").getAsString());
    }

    @Test
    void addNetworkListCachesResult() {
        FakeExec fe = new FakeExec();
        fe.resultJson = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);

        cni.addNetworkList(list, rt("cid", "/ns", "eth0"));

        Result cached = cni.getNetworkListCachedResult(list, rt("cid", "/ns", "eth0"));
        assertEquals("0.4.0", cached.version());
    }

    @Test
    void delNetworkListRunsPluginsInReverseOrderWithCachedResult() {
        FakeExec fe = new FakeExec();
        fe.resultJson = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);
        cni.addNetworkList(list, rt("cid", "/ns", "eth0"));
        fe.calls.clear();

        cni.delNetworkList(list, rt("cid", "/ns", "eth0"));

        assertEquals(2, fe.calls.size());
        assertEquals("DEL", fe.calls.get(0).command);
        assertEquals("tuning", parse(fe.calls.get(0).stdin).get("type").getAsString());
        assertTrue(parse(fe.calls.get(0).stdin).has("prevResult"));
        assertEquals("bridge", parse(fe.calls.get(1).stdin).get("type").getAsString());
    }

    @Test
    void checkNetworkListUsesCachedResult() {
        FakeExec fe = new FakeExec();
        fe.resultJson = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);
        cni.addNetworkList(list, rt("cid", "/ns", "eth0"));
        fe.calls.clear();

        cni.checkNetworkList(list, rt("cid", "/ns", "eth0"));

        assertEquals(2, fe.calls.size());
        assertEquals("CHECK", fe.calls.get(0).command);
        assertTrue(parse(fe.calls.get(0).stdin).has("prevResult"));
    }

    @Test
    void checkNetworkListRejectsOldVersions() {
        FakeExec fe = new FakeExec();
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(
            "{\"cniVersion\":\"0.3.1\",\"name\":\"mynet\",\"plugins\":[{\"type\":\"bridge\"}]}");

        CniError e = assertThrows(CniError.class, () -> cni.checkNetworkList(list, rt("cid", "/ns", "eth0")));
        assertTrue(e.getMessage().contains("does not support the CHECK command"));
    }

    @Test
    void addNetworkListWrapsPluginFailure() {
        FakeExec fe = new FakeExec();
        fe.error = new CniError(7, "boom", "");
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);

        CniError e = assertThrows(CniError.class, () -> cni.addNetworkList(list, rt("cid", "/ns", "eth0")));
        assertTrue(e.getMessage().contains("plugin type=\"bridge\" failed (add)"));
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    void validateNetworkListReturnsCapabilitiesAndChecksVersions() {
        FakeExec fe = new FakeExec();
        fe.versionInfo = PluginInfo.pluginSupports("0.4.0");
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(
            "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"plugins\":["
                + "{\"type\":\"bridge\",\"capabilities\":{\"portMappings\":true,\"bandwidth\":false}}]}");

        List<String> caps = cni.validateNetworkList(list);

        assertEquals(List.of("portMappings"), caps);
    }

    @Test
    void validateNetworkListRejectsUnsupportedVersion() {
        FakeExec fe = new FakeExec();
        fe.versionInfo = PluginInfo.pluginSupports("0.3.1");
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(
            "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"plugins\":[{\"type\":\"bridge\"}]}");

        assertThrows(CniError.class, () -> cni.validateNetworkList(list));
    }

    @Test
    void getVersionInfoDelegatesToPlugin() {
        FakeExec fe = new FakeExec();
        fe.versionInfo = PluginInfo.pluginSupports("0.4.0", "1.0.0");
        CNIConfig cni = newCni(fe);

        PluginInfo info = cni.getVersionInfo("bridge");

        assertEquals(List.of("0.4.0", "1.0.0"), info.supportedVersions());
    }

    @Test
    void corruptCachedResultDoesNotAbortDel() throws Exception {
        FakeExec fe = new FakeExec();
        fe.resultJson = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);
        cni.addNetworkList(list, rt("cid", "/ns", "eth0"));

        Path cacheFile = cacheDir.resolve("results").resolve("mynet-cid-eth0");
        Files.writeString(cacheFile, "{not json");

        fe.calls.clear();
        cni.delNetworkList(list, rt("cid", "/ns", "eth0"));

        assertEquals(2, fe.calls.size());
        assertFalse(Files.exists(cacheFile));
    }

    @Test
    void addNetworkListWithNoPluginsReturnsNull() {
        FakeExec fe = new FakeExec();
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes("{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\"}");

        assertNull(cni.addNetworkList(list, rt("cid", "/ns", "eth0")));
    }

    @Test
    void malformedCniVersionThrowsCniErrorNotIllegalArgument() {
        FakeExec fe = new FakeExec();
        CNIConfig cni = newCni(fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(
            "{\"cniVersion\":\"0.4.x\",\"name\":\"mynet\",\"plugins\":[{\"type\":\"bridge\"}]}");

        assertThrows(CniError.class, () -> cni.delNetworkList(list, rt("cid", "/ns", "eth0")));
    }

    @Test
    void delNetworkListDoesNotDeleteOutsideCacheDir() throws Exception {
        Path base = Files.createTempDirectory("cni-traversal");
        Path cache = base.resolve("cache");
        Files.createDirectories(cache.resolve("results"));
        Path victim = base.resolve("victim-cid-eth0");
        Files.writeString(victim, "important");

        FakeExec fe = new FakeExec();
        CNIConfig cni = new CNIConfig(List.of("/opt/cni/bin"), cache.toString(), fe);
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(
            "{\"cniVersion\":\"0.3.1\",\"name\":\"../../victim\",\"plugins\":[{\"type\":\"bridge\"}]}");

        cni.delNetworkList(list, rt("cid", "/ns", "eth0"));

        assertTrue(Files.exists(victim));
    }

    @Test
    void getVersionInfoTreatsLegacyPluginAs010(@TempDir Path bin) throws Exception {
        Path plugin = bin.resolve("legacy");
        Files.writeString(plugin, "#!/bin/sh\n"
            + "echo '{\"code\":1,\"msg\":\"unknown CNI_COMMAND: VERSION\"}'\n"
            + "exit 1\n");
        plugin.toFile().setExecutable(true);

        CNIConfig cni = new CNIConfig(List.of(bin.toString()), cacheDir.toString(), new DefaultExec());

        PluginInfo info = cni.getVersionInfo("legacy");

        assertEquals(List.of("0.1.0"), info.supportedVersions());
    }
}
