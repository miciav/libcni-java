package io.libcni.invoke;

import io.libcni.version.PluginInfo;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over finding and executing CNI plugins, mirroring
 * {@code invoke.Exec} in libcni. Tests may provide a fake implementation.
 */
public interface Exec {

    /** Executes a plugin, feeding {@code stdinData} on stdin and returning its stdout. */
    byte[] execPlugin(String pluginPath, byte[] stdinData, Map<String, String> environ);

    /** Locates the plugin binary within the given search paths. */
    String findInPath(String plugin, List<String> paths);

    /** Decodes the JSON returned by a plugin's {@code VERSION} command. */
    PluginInfo decode(byte[] jsonBytes);
}
