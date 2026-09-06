package io.libcni.types;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

/**
 * An error as defined by the CNI specification: a numeric {@code code},
 * a human-readable {@code msg}, and optional {@code details}.
 *
 * <p>It doubles as a Java {@link RuntimeException} so it can be thrown by the
 * library, and can be (de)serialized to the JSON shape plugins emit on stdout
 * when they fail.</p>
 *
 * @see <a href="https://github.com/containernetworking/cni/blob/main/SPEC.md#error">CNI SPEC, Error</a>
 */
public class CniError extends RuntimeException {

    private final int code;
    private final String msg;
    private final String details;

    public CniError(int code, String msg, String details) {
        super(buildMessage(msg, details));
        this.code = code;
        this.msg = msg;
        this.details = details;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }

    public String details() {
        return details;
    }

    private static String buildMessage(String msg, String details) {
        if (details == null || details.isEmpty()) {
            return msg;
        }
        return msg + "; " + details;
    }

    /** Serializes this error to the CNI error JSON shape. */
    public String toJsonString() {
        JsonObject o = new JsonObject();
        o.addProperty("code", code);
        o.addProperty("msg", msg);
        if (details != null && !details.isEmpty()) {
            o.addProperty("details", details);
        }
        return new Gson().toJson(o);
    }

    /** Parses a CNI error from its JSON representation. */
    public static CniError fromJsonString(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        int code = o.has("code") ? o.get("code").getAsInt() : 0;
        String msg = o.has("msg") ? o.get("msg").getAsString() : "";
        String details = o.has("details") ? o.get("details").getAsString() : "";
        return new CniError(code, msg, details);
    }
}
