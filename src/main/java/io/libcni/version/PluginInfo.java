package io.libcni.version;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Version information reported by a CNI plugin's {@code VERSION} command,
 * mirroring {@code version.PluginInfo} in libcni.
 */
public class PluginInfo {

    private final String cniVersion;
    private final List<String> supportedVersions;

    public PluginInfo(String cniVersion, List<String> supportedVersions) {
        this.cniVersion = cniVersion;
        this.supportedVersions = new ArrayList<>(supportedVersions);
    }

    public String cniVersion() {
        return cniVersion;
    }

    public List<String> supportedVersions() {
        return supportedVersions;
    }

    /** Returns a PluginInfo reporting the given versions as supported. */
    public static PluginInfo pluginSupports(String... versions) {
        if (versions.length < 1) {
            throw new IllegalArgumentException("programmer error: you must support at least one version");
        }
        return new PluginInfo(Version.current(), Arrays.asList(versions));
    }

    public String toJsonString() {
        JsonObject o = new JsonObject();
        o.addProperty("cniVersion", cniVersion);
        JsonArray arr = new JsonArray();
        for (String v : supportedVersions) {
            arr.add(v);
        }
        o.add("supportedVersions", arr);
        return new Gson().toJson(o);
    }
}
