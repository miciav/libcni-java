package io.libcni.invoke;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The arguments passed to a single CNI plugin invocation, mirroring
 * {@code invoke.Args} in libcni. These are turned into the {@code CNI_*}
 * environment variables consumed by the plugin.
 */
public class Args {

    public String command;
    public String containerID;
    public String netNS;
    /** Ordered list of {@code [key, value]} pairs. */
    public List<String[]> pluginArgs;
    /** Overrides {@link #pluginArgs} when set (already stringified). */
    public String pluginArgsStr;
    public String ifName;
    public String path;

    public Args() {
    }

    /** Builds the {@code CNI_*} environment variables for this invocation. */
    public Map<String, String> asEnv() {
        String argsStr = pluginArgsStr != null ? pluginArgsStr : stringify(pluginArgs);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("CNI_COMMAND", command == null ? "" : command);
        env.put("CNI_CONTAINERID", containerID == null ? "" : containerID);
        env.put("CNI_NETNS", netNS == null ? "" : netNS);
        env.put("CNI_ARGS", argsStr);
        env.put("CNI_IFNAME", ifName == null ? "" : ifName);
        env.put("CNI_PATH", path == null ? "" : path);
        return env;
    }

    private static String stringify(List<String[]> pluginArgs) {
        if (pluginArgs == null || pluginArgs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String[] kv : pluginArgs) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(kv[0]).append('=').append(kv[1]);
        }
        return sb.toString();
    }
}
