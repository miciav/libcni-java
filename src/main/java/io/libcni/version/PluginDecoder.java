package io.libcni.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the JSON returned by a plugin's {@code VERSION} command, mirroring
 * {@code version.PluginDecoder} in libcni.
 */
public class PluginDecoder {

    public PluginInfo decode(String jsonBytes) {
        JsonObject o = JsonParser.parseString(jsonBytes).getAsJsonObject();
        if (!o.has("cniVersion") || o.get("cniVersion").isJsonNull()) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "decoding version info: missing field cniVersion", "");
        }
        String cniVersion = o.get("cniVersion").getAsString();

        List<String> supported = new ArrayList<>();
        if (o.has("supportedVersions")) {
            JsonArray arr = o.getAsJsonArray("supportedVersions");
            for (JsonElement e : arr) {
                supported.add(e.getAsString());
            }
        }

        if (supported.isEmpty()) {
            if (cniVersion.equals("0.2.0")) {
                return PluginInfo.pluginSupports("0.1.0", "0.2.0");
            }
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "decoding version info: missing field supportedVersions", "");
        }

        return new PluginInfo(cniVersion, supported);
    }
}
