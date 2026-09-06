package io.libcni;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.libcni.invoke.Args;
import io.libcni.invoke.DefaultExec;
import io.libcni.invoke.Exec;
import io.libcni.invoke.Invoke;
import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import io.libcni.types.PluginConf;
import io.libcni.types.Result;
import io.libcni.types.ResultFactory;
import io.libcni.utils.Validation;
import io.libcni.version.PluginInfo;
import io.libcni.version.Version;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link CNI} implementation, mirroring {@code libcni.CNIConfig} in the
 * Go library. Locates plugins on {@link #path}, invokes them with the correct
 * {@code CNI_*} environment, chains {@code prevResult} between plugins, and
 * caches the result of a successful ADD so DEL/CHECK can restore it.
 */
public class CNIConfig implements CNI {

    public static final String DEFAULT_CACHE_DIR = "/var/lib/cni";

    private static final String CACHE_KIND_V2 = "cniCacheV2";

    private final List<String> path;
    private final String cacheDir;
    private final Exec exec;

    public CNIConfig(List<String> path, Exec exec) {
        this(path, null, exec);
    }

    public CNIConfig(List<String> path, String cacheDir, Exec exec) {
        this.path = path == null ? List.of() : List.copyOf(path);
        this.cacheDir = cacheDir;
        this.exec = exec != null ? exec : new DefaultExec();
    }

    // ------------------------------------------------------------------
    // Network list operations
    // ------------------------------------------------------------------

    @Override
    public Result addNetworkList(NetworkConfigList list, RuntimeConf rt) {
        Result result = null;
        for (PluginConfig net : list.plugins) {
            try {
                result = addNetwork(list.name, list.cniVersion, net, result, rt);
            } catch (CniError e) {
                throw new CniError(e.code(),
                    "plugin " + pluginDescription(net.network) + " failed (add): " + e.msg(), e.details());
            }
        }
        cacheAdd(result, list.name, rt);
        return result;
    }

    @Override
    public void checkNetworkList(NetworkConfigList list, RuntimeConf rt) {
        if (!supportsCachedResult(list.cniVersion)) {
            throw new CniError(CniErrorCode.INCOMPATIBLE_CNI_VERSION,
                "configuration version \"" + list.cniVersion + "\" does not support the CHECK command", "");
        }
        if (list.disableCheck) {
            return;
        }

        Result cachedResult = getCachedResultWrapped(list.name, list.cniVersion, rt);
        for (PluginConfig net : list.plugins) {
            checkNetwork(list.name, list.cniVersion, net, cachedResult, rt);
        }
    }

    @Override
    public void delNetworkList(NetworkConfigList list, RuntimeConf rt) {
        Result cachedResult = null;
        if (supportsCachedResult(list.cniVersion)) {
            cachedResult = getCachedResultForDelete(list.name, list.cniVersion, rt);
        }

        for (int i = list.plugins.size() - 1; i >= 0; i--) {
            PluginConfig net = list.plugins.get(i);
            try {
                delNetwork(list.name, list.cniVersion, net, cachedResult, rt);
            } catch (CniError e) {
                throw new CniError(e.code(),
                    "plugin " + pluginDescription(net.network) + " failed (delete): " + e.msg(), e.details());
            }
        }
        cacheDel(list.name, rt);
    }

    @Override
    public Result getNetworkListCachedResult(NetworkConfigList list, RuntimeConf rt) {
        return getCachedResult(list.name, list.cniVersion, rt);
    }

    // ------------------------------------------------------------------
    // Single network operations
    // ------------------------------------------------------------------

    @Override
    public Result addNetwork(PluginConfig net, RuntimeConf rt) {
        Result result = addNetwork(net.network.name, net.network.cniVersion, net, null, rt);
        cacheAdd(result, net.network.name, rt);
        return result;
    }

    @Override
    public void checkNetwork(PluginConfig net, RuntimeConf rt) {
        if (!supportsCachedResult(net.network.cniVersion)) {
            throw new CniError(CniErrorCode.INCOMPATIBLE_CNI_VERSION,
                "configuration version \"" + net.network.cniVersion + "\" does not support the CHECK command", "");
        }
        Result cachedResult = getCachedResultWrapped(net.network.name, net.network.cniVersion, rt);
        checkNetwork(net.network.name, net.network.cniVersion, net, cachedResult, rt);
    }

    @Override
    public void delNetwork(PluginConfig net, RuntimeConf rt) {
        Result cachedResult = null;
        if (supportsCachedResult(net.network.cniVersion)) {
            cachedResult = getCachedResultForDelete(net.network.name, net.network.cniVersion, rt);
        }
        delNetwork(net.network.name, net.network.cniVersion, net, cachedResult, rt);
        cacheDel(net.network.name, rt);
    }

    @Override
    public Result getNetworkCachedResult(PluginConfig net, RuntimeConf rt) {
        return getCachedResult(net.network.name, net.network.cniVersion, rt);
    }

    // ------------------------------------------------------------------
    // Validation and version queries
    // ------------------------------------------------------------------

    @Override
    public List<String> validateNetworkList(NetworkConfigList list) {
        Set<String> caps = new LinkedHashSet<>();
        List<String> errs = new ArrayList<>();
        for (PluginConfig net : list.plugins) {
            try {
                validatePlugin(net.network.type, list.cniVersion);
            } catch (CniError e) {
                errs.add(e.getMessage());
            }
            if (net.network.capabilities != null) {
                for (Map.Entry<String, Boolean> en : net.network.capabilities.entrySet()) {
                    if (Boolean.TRUE.equals(en.getValue())) {
                        caps.add(en.getKey());
                    }
                }
            }
        }
        if (!errs.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, String.join(", ", errs), "");
        }
        return new ArrayList<>(caps);
    }

    @Override
    public List<String> validateNetwork(PluginConfig net) {
        List<String> caps = new ArrayList<>();
        if (net.network.capabilities != null) {
            for (Map.Entry<String, Boolean> en : net.network.capabilities.entrySet()) {
                if (Boolean.TRUE.equals(en.getValue())) {
                    caps.add(en.getKey());
                }
            }
        }
        validatePlugin(net.network.type, net.network.cniVersion);
        return caps;
    }

    private void validatePlugin(String pluginName, String expectedVersion) {
        String pluginPath = exec.findInPath(pluginName, path);
        if (expectedVersion == null || expectedVersion.isEmpty()) {
            expectedVersion = "0.1.0";
        }
        PluginInfo vi = Invoke.getVersionInfo(pluginPath, exec);
        for (String v : vi.supportedVersions()) {
            if (v.equals(expectedVersion)) {
                return;
            }
        }
        throw new CniError(CniErrorCode.INCOMPATIBLE_CNI_VERSION,
            "plugin " + pluginName + " does not support config version \"" + expectedVersion + "\"", "");
    }

    @Override
    public PluginInfo getVersionInfo(String pluginType) {
        String pluginPath = exec.findInPath(pluginType, path);
        return Invoke.getVersionInfo(pluginPath, exec);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Result addNetwork(String name, String cniVersion, PluginConfig net, Result prevResult, RuntimeConf rt) {
        String pluginPath = exec.findInPath(net.network.type, path);
        Validation.validateContainerID(rt.containerID);
        Validation.validateNetworkName(name);
        Validation.validateInterfaceName(rt.ifName);

        PluginConfig newConf = buildOneConfig(name, cniVersion, net, prevResult, rt);
        return Invoke.execPluginWithResult(pluginPath, newConf.bytes, args("ADD", rt), exec);
    }

    private void checkNetwork(String name, String cniVersion, PluginConfig net, Result prevResult, RuntimeConf rt) {
        String pluginPath = exec.findInPath(net.network.type, path);
        PluginConfig newConf = buildOneConfig(name, cniVersion, net, prevResult, rt);
        Invoke.execPluginWithoutResult(pluginPath, newConf.bytes, args("CHECK", rt), exec);
    }

    private void delNetwork(String name, String cniVersion, PluginConfig net, Result prevResult, RuntimeConf rt) {
        String pluginPath = exec.findInPath(net.network.type, path);
        PluginConfig newConf = buildOneConfig(name, cniVersion, net, prevResult, rt);
        Invoke.execPluginWithoutResult(pluginPath, newConf.bytes, args("DEL", rt), exec);
    }

    private PluginConfig buildOneConfig(String name, String cniVersion, PluginConfig orig, Result prevResult, RuntimeConf rt) {
        Map<String, Object> inject = new LinkedHashMap<>();
        inject.put("name", name);
        inject.put("cniVersion", cniVersion);
        if (prevResult != null) {
            inject.put("prevResult", prevResult);
        }
        Map<String, Object> rc = collectRuntimeConfig(orig, rt);
        if (!rc.isEmpty()) {
            inject.put("runtimeConfig", rc);
        }
        return ConfigLoader.injectConf(orig, inject);
    }

    private Map<String, Object> collectRuntimeConfig(PluginConfig orig, RuntimeConf rt) {
        Map<String, Object> rc = new LinkedHashMap<>();
        if (rt != null && orig.network.capabilities != null) {
            for (Map.Entry<String, Boolean> en : orig.network.capabilities.entrySet()) {
                if (Boolean.TRUE.equals(en.getValue())
                    && rt.capabilityArgs != null && rt.capabilityArgs.containsKey(en.getKey())) {
                    rc.put(en.getKey(), rt.capabilityArgs.get(en.getKey()));
                }
            }
        }
        return rc;
    }

    private Args args(String action, RuntimeConf rt) {
        Args a = new Args();
        a.command = action;
        a.containerID = rt.containerID;
        a.netNS = rt.netNS;
        a.pluginArgs = rt.args;
        a.ifName = rt.ifName;
        a.path = String.join(File.pathSeparator, path);
        return a;
    }

    private static String pluginDescription(PluginConf net) {
        if (net == null) {
            return "<missing>";
        }
        String out = "type=\"" + (net.type == null ? "" : net.type) + "\"";
        if (net.name != null && !net.name.isEmpty()) {
            out += " name=\"" + net.name + "\"";
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Result caching
    // ------------------------------------------------------------------

    private String getCacheDir(RuntimeConf rt) {
        if (cacheDir != null && !cacheDir.isEmpty()) {
            return cacheDir;
        }
        if (rt.cacheDir != null && !rt.cacheDir.isEmpty()) {
            return rt.cacheDir;
        }
        return DEFAULT_CACHE_DIR;
    }

    private String getCacheFilePath(String netName, RuntimeConf rt) {
        if (netName == null || netName.isEmpty()
            || rt.containerID == null || rt.containerID.isEmpty()
            || rt.ifName == null || rt.ifName.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES,
                "cache file path requires network name, container ID, and interface name", "");
        }
        if (containsPathSeparator(netName) || containsPathSeparator(rt.containerID) || containsPathSeparator(rt.ifName)) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES,
                "cache file path fields must not contain path separators", "");
        }
        return Path.of(getCacheDir(rt), "results-v2", cacheKey(netName, rt.containerID, rt.ifName)).toString();
    }

    /** Unambiguous cache key: hex SHA-256 of a JSON array of the three identity fields. */
    static String cacheKey(String netName, String containerID, String ifName) {
        String json = new Gson().toJson(List.of(netName, containerID, ifName));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void cacheAdd(Result result, String netName, RuntimeConf rt) {
        if (result == null) {
            return;
        }
        JsonObject doc = new JsonObject();
        doc.addProperty("kind", CACHE_KIND_V2);
        doc.addProperty("networkName", netName);
        doc.addProperty("containerId", rt.containerID);
        doc.addProperty("ifName", rt.ifName);
        doc.add("result", JsonParser.parseString(result.toJsonString()));

        Path target = Path.of(getCacheFilePath(netName, rt));
        try {
            Path dir = target.getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".tmp-", null);
            try {
                Files.writeString(tmp, doc.toString());
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new CniError(CniErrorCode.IO_FAILURE,
                "failed to set network \"" + netName + "\" cached result: " + e, "");
        }
    }

    private void cacheDel(String netName, RuntimeConf rt) {
        try {
            Files.deleteIfExists(Path.of(getCacheFilePath(netName, rt)));
        } catch (Exception ignored) {
            // cache removal is best-effort
        }
    }

    private Result getCachedResult(String netName, String cniVersion, RuntimeConf rt) {
        String fname = getCacheFilePath(netName, rt);
        String data;
        try {
            data = Files.readString(Path.of(fname));
        } catch (IOException e) {
            // The cached result may simply not exist on disk.
            return null;
        }

        JsonObject doc;
        try {
            doc = JsonParser.parseString(data).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "failed to unmarshal cached result: " + e.getMessage(), "", e);
        }

        if (!CACHE_KIND_V2.equals(str(doc, "kind"))) {
            throw new CniError(CniErrorCode.DECODING_FAILURE, "read cached result has wrong kind", "");
        }
        if (!netName.equals(str(doc, "networkName"))
            || !rt.containerID.equals(str(doc, "containerId"))
            || !rt.ifName.equals(str(doc, "ifName"))) {
            // The entry declares a different identity than the one requested.
            return null;
        }

        JsonElement resultEl = doc.get("result");
        if (resultEl == null || resultEl.isJsonNull()) {
            return null;
        }
        Result result = ResultFactory.createFromBytes(resultEl.toString());
        return result.getAsVersion(cniVersion);
    }

    private Result getCachedResultWrapped(String netName, String cniVersion, RuntimeConf rt) {
        try {
            return getCachedResult(netName, cniVersion, rt);
        } catch (CniError e) {
            throw new CniError(e.code(),
                "failed to get network \"" + netName + "\" cached result: " + e.msg(), e.details());
        }
    }

    /** True if the CNI version supports cached results (0.4.0+); throws a CniError on a malformed version. */
    private boolean supportsCachedResult(String cniVersion) {
        try {
            return Version.greaterThanOrEqualTo(cniVersion, "0.4.0");
        } catch (IllegalArgumentException e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "invalid cniVersion \"" + cniVersion + "\": " + e.getMessage(), "");
        }
    }

    /** Best-effort cached-result restore for DEL: removes the cache entry on failure. */
    private Result getCachedResultForDelete(String netName, String cniVersion, RuntimeConf rt) {
        try {
            return getCachedResult(netName, cniVersion, rt);
        } catch (CniError e) {
            cacheDel(netName, rt);
            return null;
        }
    }

    private static boolean containsPathSeparator(String s) {
        return s.indexOf('/') >= 0 || s.indexOf('\\') >= 0;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }
}
