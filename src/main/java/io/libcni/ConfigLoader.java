package io.libcni;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import io.libcni.types.PluginConf;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loading and manipulation of CNI network configuration files, mirroring
 * {@code libcni/conf.go} in libcni.
 */
public final class ConfigLoader {

    private static final Gson GSON = new Gson();

    private ConfigLoader() {
    }

    /**
     * Parses a single plugin configuration. Throws a {@link CniError} if the
     * JSON is malformed or the required {@code type} field is missing.
     */
    public static PluginConfig networkPluginConfFromBytes(String bytes) {
        PluginConf conf;
        try {
            conf = GSON.fromJson(bytes, PluginConf.class);
        } catch (JsonSyntaxException e) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration: " + e.getMessage(), "", e);
        }
        if (conf == null) {
            conf = new PluginConf();
        }
        if (conf.type == null || conf.type.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration: missing 'type'", "");
        }
        return new PluginConfig(conf, bytes);
    }

    /** Loads all plugin definitions found at {@code <networkConfPath>/<networkName>/*.conf}. */
    public static List<PluginConfig> networkPluginConfsFromFiles(String networkConfPath, String networkName) {
        String pluginConfPath = new File(networkConfPath, networkName).getPath();
        List<String> pluginConfFiles = confFiles(pluginConfPath, List.of(".conf"));
        List<PluginConfig> out = new ArrayList<>();
        for (String f : pluginConfFiles) {
            out.add(networkPluginConfFromBytes(readFile(f)));
        }
        return out;
    }

    /** Parses a network configuration list (a {@code .conflist} file). */
    public static NetworkConfigList networkConfFromBytes(String bytes) {
        JsonObject raw;
        try {
            raw = JsonParser.parseString(bytes).getAsJsonObject();
        } catch (Exception e) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: " + e, "");
        }

        if (!raw.has("name")) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: no name", "");
        }
        JsonElement rawName = raw.get("name");
        if (!rawName.isJsonPrimitive() || !rawName.getAsJsonPrimitive().isString()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: invalid name type " + rawName, "");
        }
        String name = rawName.getAsString();

        String cniVersion = "";
        if (raw.has("cniVersion")) {
            JsonElement rv = raw.get("cniVersion");
            if (!rv.isJsonPrimitive() || !rv.getAsJsonPrimitive().isString()) {
                throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                    "error parsing configuration list: invalid cniVersion type " + rv, "");
            }
            cniVersion = rv.getAsString();
        }

        boolean disableCheck = readBool(raw, "disableCheck");
        boolean disableGC = readBool(raw, "disableGC");
        boolean loadOnlyInlinedPlugins = readBool(raw, "loadOnlyInlinedPlugins");

        NetworkConfigList list = new NetworkConfigList();
        list.name = name;
        list.cniVersion = cniVersion;
        list.disableCheck = disableCheck;
        list.disableGC = disableGC;
        list.loadOnlyInlinedPlugins = loadOnlyInlinedPlugins;
        list.bytes = bytes;
        list.plugins = new ArrayList<>();

        if (!raw.has("plugins")) {
            if (loadOnlyInlinedPlugins) {
                throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                    "error parsing configuration list: `loadOnlyInlinedPlugins` is true, and no 'plugins' key", "");
            }
            return list;
        }

        JsonElement plug = raw.get("plugins");
        if (!plug.isJsonArray()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: invalid 'plugins' type " + plug, "");
        }
        JsonArray plugins = plug.getAsJsonArray();
        if (plugins.size() == 0) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: no plugins in list", "");
        }

        for (int i = 0; i < plugins.size(); i++) {
            String newBytes = GSON.toJson(plugins.get(i));
            try {
                list.plugins.add(networkPluginConfFromBytes(newBytes));
            } catch (CniError e) {
                throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                    "failed to parse plugin config " + i + ": " + e.getMessage(), "");
            }
        }
        return list;
    }

    /** Loads a network configuration list from a file, including directory-based plugins. */
    public static NetworkConfigList networkConfFromFile(String filename) {
        String bytes = readFile(filename);
        NetworkConfigList conf = networkConfFromBytes(bytes);
        if (!conf.loadOnlyInlinedPlugins) {
            File f = new File(filename);
            conf.plugins.addAll(networkPluginConfsFromFiles(f.getParent(), conf.name));
        }
        if (conf.plugins.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "no plugin configs found", "");
        }
        return conf;
    }

    /** Alias of {@link #networkConfFromBytes}. */
    public static NetworkConfigList confListFromBytes(String bytes) {
        return networkConfFromBytes(bytes);
    }

    /** Alias of {@link #networkConfFromFile}. */
    public static NetworkConfigList confListFromFile(String filename) {
        return networkConfFromFile(filename);
    }

    /**
     * Loads the network configuration with the given name from a directory,
     * preferring {@code .conflist} files and falling back to legacy single
     * {@code .conf} / {@code .json} files.
     */
    public static NetworkConfigList loadNetworkConf(String dir, String name) {
        List<String> conflistFiles = confFiles(dir, List.of(".conflist"));
        for (String f : conflistFiles) {
            NetworkConfigList conf = networkConfFromFile(f);
            if (name.equals(conf.name)) {
                return conf;
            }
        }

        List<String> singleFiles = confFiles(dir, List.of(".conf", ".json"));
        for (String f : singleFiles) {
            PluginConfig conf = networkPluginConfFromBytes(readFile(f));
            if (name.equals(conf.network.name)) {
                return confListFromConf(conf);
            }
        }

        if (conflistFiles.isEmpty() && singleFiles.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "no net configurations found in " + dir, "");
        }
        throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
            "no net configuration with name \"" + name + "\" in " + dir, "");
    }

    /** Returns all files in {@code dir} whose extension is one of {@code extensions}. */
    public static List<String> confFiles(String dir, List<String> extensions) {
        File[] files = new File(dir).listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory()) {
                continue;
            }
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String ext = dot < 0 ? "" : name.substring(dot);
            if (extensions.contains(ext)) {
                out.add(f.getPath());
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Inserts additional values into a plugin configuration, preserving unknown fields. */
    public static PluginConfig injectConf(PluginConfig original, Map<String, Object> newValues) {
        JsonObject config;
        try {
            config = JsonParser.parseString(original.bytes).getAsJsonObject();
        } catch (Exception e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "unmarshal existing network bytes: " + e, "");
        }

        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String key = entry.getKey();
            if (key.isEmpty()) {
                throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "keys cannot be empty", "");
            }
            Object value = entry.getValue();
            if (value == null) {
                throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                    "key '" + key + "' value must not be nil", "");
            }
            config.add(key, GSON.toJsonTree(value));
        }

        return networkPluginConfFromBytes(GSON.toJson(config));
    }

    /** Up-converts a single plugin config into a one-plugin network config list. */
    public static NetworkConfigList confListFromConf(PluginConfig original) {
        JsonObject rawConfig;
        try {
            rawConfig = JsonParser.parseString(original.bytes).getAsJsonObject();
        } catch (Exception e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE, e.getMessage(), "");
        }

        JsonObject rawList = new JsonObject();
        rawList.addProperty("name", original.network.name == null ? "" : original.network.name);
        rawList.addProperty("cniVersion",
            original.network.cniVersion == null ? "" : original.network.cniVersion);
        JsonArray plugins = new JsonArray();
        plugins.add(rawConfig);
        rawList.add("plugins", plugins);

        return networkConfFromBytes(GSON.toJson(rawList));
    }

    private static boolean readBool(JsonObject raw, String key) {
        if (!raw.has(key)) {
            return false;
        }
        JsonElement v = raw.get(key);
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
            return v.getAsBoolean();
        }
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
            String s = v.getAsString().toLowerCase();
            if (s.equals("true")) {
                return true;
            }
            if (s.equals("false")) {
                return false;
            }
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "error parsing configuration list: invalid value \"" + s + "\" for " + key, "");
        }
        throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
            "error parsing configuration list: invalid type " + v + " for " + key, "");
    }

    private static String readFile(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new CniError(CniErrorCode.IO_FAILURE,
                "error reading " + filename + ": " + e, "");
        }
    }
}
