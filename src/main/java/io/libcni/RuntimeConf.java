package io.libcni;

import java.util.List;
import java.util.Map;

/**
 * The arguments to one invocation of a CNI plugin, mirroring {@code RuntimeConf}
 * in libcni.
 */
public class RuntimeConf {

    public String containerID;
    public String netNS;
    public String ifName;
    /** Ordered list of {@code [key, value]} pairs. */
    public List<String[]> args;
    /**
     * Capability-specific data passed to plugins as top-level keys of the
     * {@code runtimeConfig} dictionary. libcni only passes keys that match a
     * plugin's advertised capabilities.
     */
    public Map<String, Object> capabilityArgs;
    /** Deprecated; superseded by the cache directory on {@code CNIConfig}. */
    public String cacheDir;

    public RuntimeConf() {
    }
}
