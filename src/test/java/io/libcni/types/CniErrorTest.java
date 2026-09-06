package io.libcni.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class CniErrorTest {

    @Test
    void messageIncludesDetails() {
        CniError e = new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "bad config", "line 3");
        assertEquals("bad config; line 3", e.getMessage());
    }

    @Test
    void messageOmitsEmptyDetails() {
        CniError e = new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "bad config", "");
        assertEquals("bad config", e.getMessage());
    }

    @Test
    void serializesToJsonAndOmitsEmptyDetails() {
        CniError e = new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "bad config", "");
        JsonObject o = JsonParser.parseString(e.toJsonString()).getAsJsonObject();
        assertEquals(7, o.get("code").getAsInt());
        assertEquals("bad config", o.get("msg").getAsString());
        assertFalse(o.has("details"));
    }

    @Test
    void parsesFromJson() {
        CniError e = CniError.fromJsonString("{\"code\":5,\"msg\":\"io\",\"details\":\"d\"}");
        assertEquals(5, e.code());
        assertEquals("io", e.msg());
        assertEquals("d", e.details());
    }

    @Test
    void carriesCause() {
        RuntimeException cause = new RuntimeException("boom");
        CniError e = new CniError(CniErrorCode.DECODING_FAILURE, "msg", "", cause);
        assertSame(cause, e.getCause());
    }
}
