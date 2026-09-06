package io.libcni.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgsTest {

    @Test
    void buildsCniEnvironment() {
        Args args = new Args();
        args.command = "ADD";
        args.containerID = "cid";
        args.netNS = "/var/run/netns/x";
        args.pluginArgs = List.<String[]>of(new String[]{"K", "V"});
        args.ifName = "eth0";
        args.path = "/a:/b";

        Map<String, String> env = args.asEnv();

        assertEquals("ADD", env.get("CNI_COMMAND"));
        assertEquals("cid", env.get("CNI_CONTAINERID"));
        assertEquals("/var/run/netns/x", env.get("CNI_NETNS"));
        assertEquals("K=V", env.get("CNI_ARGS"));
        assertEquals("eth0", env.get("CNI_IFNAME"));
        assertEquals("/a:/b", env.get("CNI_PATH"));
    }

    @Test
    void stringifiesMultipleArgsSemicolonSeparated() {
        Args args = new Args();
        args.pluginArgs = List.<String[]>of(new String[]{"K1", "V1"}, new String[]{"K2", "V2"});
        assertEquals("K1=V1;K2=V2", args.asEnv().get("CNI_ARGS"));
    }

    @Test
    void emptyPluginArgsProduceEmptyArgs() {
        Args args = new Args();
        args.pluginArgs = List.of();
        assertEquals("", args.asEnv().get("CNI_ARGS"));
    }
}
