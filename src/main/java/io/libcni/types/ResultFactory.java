package io.libcni.types;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;

/**
 * Factory for creating {@link Result} objects from JSON, mirroring
 * {@code pkg/types/create} in libcni.
 */
public final class ResultFactory {

    private ResultFactory() {
    }

    /**
     * Returns the CNI version declared by the given config or result JSON, or
     * {@code "0.1.0"} when none is declared (per the CNI spec).
     */
    public static String decodeVersion(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "decoding version from network config: " + e.getMessage(), "", e);
        }
        if (!o.has("cniVersion")) {
            return "0.1.0";
        }
        String v = o.get("cniVersion").isJsonNull() ? "" : o.get("cniVersion").getAsString();
        return v.isEmpty() ? "0.1.0" : v;
    }

    public static boolean isSupported(String version) {
        return Arrays.asList(CurrentResult.SUPPORTED_VERSIONS).contains(version);
    }

    /**
     * Creates a {@link Result} from JSON with the given expected version, or
     * throws a {@link CniError} if the version is not supported.
     */
    public static Result create(String version, String json) {
        if (!isSupported(version)) {
            throw new CniError(CniErrorCode.INCOMPATIBLE_CNI_VERSION,
                "unsupported CNI result version \"" + version + "\"", "");
        }
        CurrentResult r;
        try {
            r = new Gson().fromJson(json, CurrentResult.class);
        } catch (JsonSyntaxException e) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "failed to unmarshal result: " + e.getMessage(), "", e);
        }
        if (r.cniVersion == null || r.cniVersion.isEmpty()) {
            r.cniVersion = version;
        }
        return r;
    }

    /** Creates a {@link Result} from JSON, auto-detecting the CNI spec version. */
    public static Result createFromBytes(String json) {
        return create(decodeVersion(json), json);
    }
}
