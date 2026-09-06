package io.libcni.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class PluginConfTest {

    @Test
    void parsesKnownFieldsAndIgnoresUnknown() {
        String json = "{\"cniVersion\":\"0.4.0\",\"name\":\"mynet\",\"type\":\"bridge\","
            + "\"capabilities\":{\"portMappings\":true},\"somePluginSpecific\":\"value\"}";

        PluginConf c = new Gson().fromJson(json, PluginConf.class);

        assertEquals("0.4.0", c.cniVersion);
        assertEquals("mynet", c.name);
        assertEquals("bridge", c.type);
        assertEquals(Boolean.TRUE, c.capabilities.get("portMappings"));
    }

    @Test
    void missingCapabilitiesIsNull() {
        PluginConf c = new Gson().fromJson("{\"type\":\"bridge\"}", PluginConf.class);
        assertEquals("bridge", c.type);
        assertNull(c.capabilities);
    }

    @Test
    void missingTypeIsEmptyString() {
        PluginConf c = new Gson().fromJson("{}", PluginConf.class);
        assertTrue(c.type == null || c.type.isEmpty());
    }
}
