package io.libcni.types;

import java.util.Map;

/**
 * The known fields of a plugin configuration, mirroring {@code types.PluginConf}
 * in libcni. Unknown, plugin-specific fields are preserved in the raw bytes of
 * the surrounding {@code PluginConfig} rather than here.
 */
public class PluginConf {
    public String cniVersion;
    public String name;
    public String type;
    public Map<String, Boolean> capabilities;

    public PluginConf() {
    }
}
