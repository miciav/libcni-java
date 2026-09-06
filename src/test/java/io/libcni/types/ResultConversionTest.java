package io.libcni.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ResultConversionTest {

    private static JsonObject ip(Result r, int i) {
        return JsonParser.parseString(r.toJsonString()).getAsJsonObject()
            .getAsJsonArray("ips").get(i).getAsJsonObject();
    }

    private static JsonObject intf(Result r, int i) {
        return JsonParser.parseString(r.toJsonString()).getAsJsonObject()
            .getAsJsonArray("interfaces").get(i).getAsJsonObject();
    }

    @Test
    void preserves110InterfaceFieldsThroughParseAndSerialize() {
        String json = "{\"cniVersion\":\"1.1.0\",\"interfaces\":[{\"name\":\"eth0\",\"mtu\":1500,"
            + "\"socketPath\":\"/run/x.sock\",\"pciID\":\"0000:00:1f.6\"}]}";

        CurrentResult r = (CurrentResult) ResultFactory.createFromBytes(json);

        assertEquals(1500, r.interfaces.get(0).mtu);
        assertEquals("/run/x.sock", r.interfaces.get(0).socketPath);
        assertEquals("0000:00:1f.6", r.interfaces.get(0).pciID);

        CurrentResult reparsed = (CurrentResult) ResultFactory.createFromBytes(r.toJsonString());
        assertEquals(1500, reparsed.interfaces.get(0).mtu);
        assertEquals("/run/x.sock", reparsed.interfaces.get(0).socketPath);
        assertEquals("0000:00:1f.6", reparsed.interfaces.get(0).pciID);
    }

    @Test
    void downgradeDerivesIpv4FromAddress() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"address\":\"10.0.0.2/24\",\"gateway\":\"10.0.0.1\"}],"
                + "\"interfaces\":[{\"name\":\"eth0\",\"mtu\":1500}]}");

        Result d = r.getAsVersion("0.4.0");

        JsonObject o = JsonParser.parseString(d.toJsonString()).getAsJsonObject();
        assertEquals("0.4.0", o.get("cniVersion").getAsString());
        assertEquals("4", ip(d, 0).get("version").getAsString());
        assertEquals("10.0.0.2/24", ip(d, 0).get("address").getAsString());
        assertFalse(intf(d, 0).has("mtu"));
    }

    @Test
    void downgradeDerivesIpv6FromAddress() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"address\":\"fd00::1/64\"}]}");

        Result d = r.getAsVersion("0.4.0");

        assertEquals("6", ip(d, 0).get("version").getAsString());
    }

    @Test
    void upgradeOmitsIpVersion() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}");

        Result u = r.getAsVersion("1.0.0");

        assertEquals("1.0.0", u.version());
        assertFalse(ip(u, 0).has("version"));
        assertEquals("10.0.0.2/24", ip(u, 0).get("address").getAsString());
    }

    @Test
    void sameCurrentFamilyKeepsTypes() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"address\":\"10.0.0.2/24\"}],"
                + "\"interfaces\":[{\"name\":\"eth0\",\"mtu\":1500}]}");

        Result d = r.getAsVersion("1.1.0");

        JsonObject o = JsonParser.parseString(d.toJsonString()).getAsJsonObject();
        assertEquals("1.1.0", o.get("cniVersion").getAsString());
        assertEquals(1500, intf(d, 0).get("mtu").getAsInt());
        assertFalse(ip(d, 0).has("version"));
    }

    @Test
    void sameLegacyFamilyKeepsTypes() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}");

        Result d = r.getAsVersion("0.3.1");

        JsonObject o = JsonParser.parseString(d.toJsonString()).getAsJsonObject();
        assertEquals("0.3.1", o.get("cniVersion").getAsString());
        assertEquals("4", ip(d, 0).get("version").getAsString());
    }

    @Test
    void downgradeRejectsInvalidAddress() {
        Result r = ResultFactory.createFromBytes(
            "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"address\":\"not-an-ip\"}]}");

        assertThrows(CniError.class, () -> r.getAsVersion("0.4.0"));
    }

    @Test
    void conversionDoesNotMutateOriginal() {
        CurrentResult r = (CurrentResult) ResultFactory.createFromBytes(
            "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"address\":\"10.0.0.2/24\"}]}");

        r.getAsVersion("0.4.0");

        assertEquals("1.0.0", r.version());
        assertNull(r.ips.get(0).version);
    }
}
