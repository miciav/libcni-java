package io.libcni;

import java.util.List;

/**
 * An ordered list of plugin configurations for one network, mirroring
 * {@code NetworkConfigList} in libcni.
 */
public class NetworkConfigList {

    public String name;
    public String cniVersion;
    public boolean disableCheck;
    public boolean disableGC;
    public boolean loadOnlyInlinedPlugins;
    public List<PluginConfig> plugins;
    public String bytes;

    public NetworkConfigList() {
    }
}
