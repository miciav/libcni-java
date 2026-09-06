package io.libcni;

import io.libcni.types.PluginConf;

/**
 * A single plugin's configuration, mirroring {@code PluginConfig} in libcni: the
 * raw JSON bytes plus the known fields parsed into a {@link PluginConf}.
 */
public class PluginConfig {

    public final PluginConf network;
    public final String bytes;

    public PluginConfig(PluginConf network, String bytes) {
        this.network = network;
        this.bytes = bytes;
    }
}
