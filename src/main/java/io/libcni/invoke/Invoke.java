package io.libcni.invoke;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.libcni.types.CniError;
import io.libcni.types.Result;
import io.libcni.types.ResultFactory;
import io.libcni.version.PluginInfo;
import io.libcni.version.Version;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for invoking CNI plugins, mirroring {@code invoke/exec.go} in libcni.
 */
public final class Invoke {

    private Invoke() {
    }

    /** Executes a plugin that is expected to return a result on stdout. */
    public static Result execPluginWithResult(String pluginPath, String netconf, Args args, Exec exec) {
        Exec e = exec != null ? exec : new DefaultExec();
        byte[] stdout = e.execPlugin(pluginPath, netconf.getBytes(StandardCharsets.UTF_8), args.asEnv());
        String[] fixed = fixupResultVersion(netconf, new String(stdout, StandardCharsets.UTF_8));
        return ResultFactory.create(fixed[0], fixed[1]);
    }

    /** Executes a plugin whose stdout is ignored. */
    public static void execPluginWithoutResult(String pluginPath, String netconf, Args args, Exec exec) {
        Exec e = exec != null ? exec : new DefaultExec();
        e.execPlugin(pluginPath, netconf.getBytes(StandardCharsets.UTF_8), args.asEnv());
    }

    /**
     * Reports the CNI spec versions supported by a plugin. For plugins that do not
     * understand the {@code VERSION} command, reports {@code 0.1.0}.
     */
    public static PluginInfo getVersionInfo(String pluginPath, Exec exec) {
        Exec e = exec != null ? exec : new DefaultExec();
        Args args = new Args();
        args.command = "VERSION";
        args.netNS = "dummy";
        args.ifName = "dummy";
        args.path = "dummy";

        String stdin = "{\"cniVersion\":\"" + Version.current() + "\"}";
        byte[] stdout;
        try {
            stdout = e.execPlugin(pluginPath, stdin.getBytes(StandardCharsets.UTF_8), args.asEnv());
        } catch (CniError ce) {
            if ("unknown CNI_COMMAND: VERSION".equals(ce.msg())) {
                return PluginInfo.pluginSupports("0.1.0");
            }
            throw ce;
        }
        return e.decode(stdout);
    }

    /**
     * Ensures a plugin result has a {@code cniVersion}: if the result declares one,
     * it is kept; otherwise the config's version is injected. Returns
     * {@code [resultVersion, fixedResultJson]}.
     */
    static String[] fixupResultVersion(String netconf, String result) {
        String confVersion = ResultFactory.decodeVersion(netconf);

        JsonElement el = JsonParser.parseString(result);
        JsonObject raw = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();

        if (raw.has("cniVersion")) {
            JsonElement v = raw.get("cniVersion");
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && !v.getAsString().isEmpty()) {
                return new String[]{v.getAsString(), result};
            }
        }

        raw.addProperty("cniVersion", confVersion);
        return new String[]{confVersion, new Gson().toJson(raw)};
    }
}
