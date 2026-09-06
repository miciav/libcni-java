package io.libcni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.libcni.types.CniError;
import io.libcni.types.Result;
import io.libcni.types.ResultFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private static final String CONFLIST =
        "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"plugins\":["
            + "{\"type\":\"bridge\",\"name\":\"mynet\",\"ipam\":{\"type\":\"host-local\"}},"
            + "{\"type\":\"tuning\"}]}";

    @Test
    void parsesConfList() {
        NetworkConfigList list = ConfigLoader.networkConfFromBytes(CONFLIST);

        assertEquals("mynet", list.name);
        assertEquals("0.4.0", list.cniVersion);
        assertEquals(2, list.plugins.size());
        assertEquals("bridge", list.plugins.get(0).network.type);
        assertEquals("tuning", list.plugins.get(1).network.type);
        assertTrue(list.plugins.get(0).bytes.contains("host-local"));
    }

    @Test
    void rejectsMissingName() {
        assertThrows(CniError.class,
            () -> ConfigLoader.networkConfFromBytes("{\"cniVersion\":\"0.4.0\",\"plugins\":[{\"type\":\"x\"}]}"));
    }

    @Test
    void rejectsPluginMissingType() {
        assertThrows(CniError.class,
            () -> ConfigLoader.networkConfFromBytes("{\"name\":\"mynet\",\"plugins\":[{\"foo\":\"bar\"}]}"));
    }

    @Test
    void networkPluginConfFromBytesRejectsMissingType() {
        assertThrows(CniError.class,
            () -> ConfigLoader.networkPluginConfFromBytes("{\"name\":\"mynet\"}"));
    }

    @Test
    void injectConfAddsValuesPreservingExisting() {
        PluginConfig original = ConfigLoader.networkPluginConfFromBytes(
            "{\"type\":\"bridge\",\"ipam\":{\"type\":\"host-local\"}}");
        Result prev = ResultFactory.createFromBytes("{\"cniVersion\":\"0.4.0\",\"ips\":[]}");

        PluginConfig injected = ConfigLoader.injectConf(original,
            Map.of("name", "mynet", "cniVersion", "0.4.0", "prevResult", prev));

        JsonObject o = JsonParser.parseString(injected.bytes).getAsJsonObject();
        assertEquals("mynet", o.get("name").getAsString());
        assertEquals("0.4.0", o.get("cniVersion").getAsString());
        assertTrue(o.has("prevResult"));
        assertEquals("host-local", o.getAsJsonObject("ipam").get("type").getAsString());
    }

    @Test
    void confListFromConfUpconvertsSingleConf() {
        PluginConfig conf = ConfigLoader.networkPluginConfFromBytes(
            "{\"cniVersion\":\"0.3.1\",\"name\":\"mynet\",\"type\":\"bridge\"}");

        NetworkConfigList list = ConfigLoader.confListFromConf(conf);

        assertEquals("mynet", list.name);
        assertEquals("0.3.1", list.cniVersion);
        assertEquals(1, list.plugins.size());
        assertEquals("bridge", list.plugins.get(0).network.type);
    }

    @Test
    void confFilesListsOnlyMatchingExtensions(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("a.conflist"));
        Files.createFile(dir.resolve("b.conf"));
        Files.createFile(dir.resolve("c.json"));
        Files.createFile(dir.resolve("d.txt"));

        List<String> files = ConfigLoader.confFiles(dir.toString(), List.of(".conflist"));

        assertEquals(1, files.size());
        assertTrue(files.get(0).endsWith("a.conflist"));
    }

    @Test
    void confFilesReturnsEmptyForMissingDir() {
        List<String> files = ConfigLoader.confFiles("/nonexistent/dir", List.of(".conflist"));
        assertEquals(0, files.size());
    }

    @Test
    void loadNetworkConfLoadsByName(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("10-mynet.conflist"), CONFLIST);

        NetworkConfigList list = ConfigLoader.loadNetworkConf(dir.toString(), "mynet");

        assertEquals("mynet", list.name);
        assertEquals(2, list.plugins.size());
    }

    @Test
    void loadNetworkConfThrowsWhenNameNotFound(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("10-mynet.conflist"), CONFLIST);

        assertThrows(CniError.class, () -> ConfigLoader.loadNetworkConf(dir.toString(), "othernet"));
    }

    @Test
    void networkPluginConfFromBytesRejectsMalformedJson() {
        CniError e = assertThrows(CniError.class, () -> ConfigLoader.networkPluginConfFromBytes("{"));
        assertNotNull(e.getCause());
    }

    @Test
    void networkConfFromBytesPreservesPluginIndexOnMalformedPlugin() {
        CniError e = assertThrows(CniError.class, () ->
            ConfigLoader.networkConfFromBytes("{\"name\":\"mynet\",\"plugins\":[{\"type\":\"ok\"},123]}"));
        assertTrue(e.getMessage().contains("plugin config 1"));
    }
}
