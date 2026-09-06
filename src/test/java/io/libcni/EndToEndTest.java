package io.libcni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.libcni.invoke.DefaultExec;
import io.libcni.types.CurrentResult;
import io.libcni.types.Result;
import io.libcni.version.PluginInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test: drives a real (shell script) CNI plugin through the actual
 * subprocess boundary using {@link DefaultExec}.
 */
class EndToEndTest {

    @TempDir
    Path tmp;

    private static final String PLUGIN = "#!/bin/sh\n"
        + "case \"$CNI_COMMAND\" in\n"
        + "  VERSION) echo '{\"cniVersion\":\"0.4.0\",\"supportedVersions\":[\"0.4.0\"]}' ;;\n"
        + "  ADD) echo '{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\",\"gateway\":\"10.0.0.1\"}]}' ;;\n"
        + "  *) : ;;\n"
        + "esac\n";

    private Path binDir() throws Exception {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path plugin = bin.resolve("bridge");
        Files.writeString(plugin, PLUGIN);
        plugin.toFile().setExecutable(true);
        return bin;
    }

    private Path confDir() throws Exception {
        Path conf = Files.createDirectories(tmp.resolve("conf"));
        Files.writeString(conf.resolve("10-mynet.conflist"),
            "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"plugins\":[{\"type\":\"bridge\"}]}");
        return conf;
    }

    private RuntimeConf rt() {
        RuntimeConf r = new RuntimeConf();
        r.containerID = "cid";
        r.netNS = "/var/run/netns/test";
        r.ifName = "eth0";
        return r;
    }

    @Test
    void fullAddDelCycleThroughRealPlugin() throws Exception {
        CNIConfig cni = new CNIConfig(List.of(binDir().toString()), tmp.resolve("cache").toString(), new DefaultExec());
        NetworkConfigList list = ConfigLoader.loadNetworkConf(confDir().toString(), "mynet");

        Result result = cni.addNetworkList(list, rt());

        assertNotNull(result);
        assertEquals("0.4.0", result.version());
        assertEquals("10.0.0.2/24", ((CurrentResult) result).ips.get(0).address);

        // DEL restores prevResult from the cache and tears down cleanly.
        cni.delNetworkList(list, rt());
    }

    @Test
    void getVersionInfoRunsVersionCommand() throws Exception {
        CNIConfig cni = new CNIConfig(List.of(binDir().toString()), tmp.resolve("cache2").toString(), new DefaultExec());

        PluginInfo info = cni.getVersionInfo("bridge");

        assertEquals(List.of("0.4.0"), info.supportedVersions());
    }
}
