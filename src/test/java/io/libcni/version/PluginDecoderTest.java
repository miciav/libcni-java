package io.libcni.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.libcni.types.CniError;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PluginDecoderTest {

    @Test
    void decodesSupportedVersions() {
        PluginInfo i = new PluginDecoder().decode(
            "{\"cniVersion\":\"0.4.0\",\"supportedVersions\":[\"0.3.0\",\"0.4.0\"]}");
        assertEquals(Arrays.asList("0.3.0", "0.4.0"), i.supportedVersions());
        assertEquals("0.4.0", i.cniVersion());
    }

    @Test
    void rejectsMissingCniVersion() {
        assertThrows(CniError.class,
            () -> new PluginDecoder().decode("{\"supportedVersions\":[\"0.4.0\"]}"));
    }

    @Test
    void rejectsMissingSupportedVersions() {
        assertThrows(CniError.class,
            () -> new PluginDecoder().decode("{\"cniVersion\":\"0.4.0\"}"));
    }

    @Test
    void treats020WithoutVersionsAsLegacy() {
        PluginInfo i = new PluginDecoder().decode("{\"cniVersion\":\"0.2.0\"}");
        assertEquals(Arrays.asList("0.1.0", "0.2.0"), i.supportedVersions());
    }

    @Test
    void pluginSupportsReportsGivenVersions() {
        PluginInfo i = PluginInfo.pluginSupports("0.1.0", "0.2.0");
        assertEquals(Arrays.asList("0.1.0", "0.2.0"), i.supportedVersions());
        assertEquals(Version.current(), i.cniVersion());
    }

    @Test
    void decodeRejectsMalformedJson() {
        CniError e = assertThrows(CniError.class, () -> new PluginDecoder().decode("{"));
        assertNotNull(e.getCause());
    }
}
