package io.libcni.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    void parsesCurrentResult() {
        String json = "{\"cniVersion\":\"0.4.0\","
            + "\"interfaces\":[{\"name\":\"eth0\",\"mac\":\"02:00:00:00:00:01\"}],"
            + "\"ips\":[{\"version\":\"4\",\"interface\":0,\"address\":\"10.0.0.2/24\",\"gateway\":\"10.0.0.1\"}],"
            + "\"routes\":[{\"dst\":\"0.0.0.0/0\",\"gw\":\"10.0.0.1\"}],"
            + "\"dns\":{\"nameservers\":[\"8.8.8.8\"]}}";

        CurrentResult cr = (CurrentResult) ResultFactory.createFromBytes(json);

        assertEquals("0.4.0", cr.version());
        assertEquals(1, cr.interfaces.size());
        assertEquals("eth0", cr.interfaces.get(0).name);
        assertEquals("02:00:00:00:00:01", cr.interfaces.get(0).mac);
        assertEquals(1, cr.ips.size());
        assertEquals("4", cr.ips.get(0).version);
        assertEquals(Integer.valueOf(0), cr.ips.get(0).iface);
        assertEquals("10.0.0.2/24", cr.ips.get(0).address);
        assertEquals("10.0.0.1", cr.ips.get(0).gateway);
        assertEquals("0.0.0.0/0", cr.routes.get(0).dst);
        assertEquals("8.8.8.8", cr.dns.nameservers.get(0));
    }

    @Test
    void getAsVersionConvertsVersionButKeepsData() {
        String json = "{\"cniVersion\":\"0.4.0\",\"ips\":[{\"version\":\"4\",\"address\":\"10.0.0.2/24\"}]}";
        Result original = ResultFactory.createFromBytes(json);

        Result converted = original.getAsVersion("1.0.0");

        assertEquals("1.0.0", converted.version());
        assertEquals("10.0.0.2/24", ((CurrentResult) converted).ips.get(0).address);
        // original object is untouched
        assertEquals("0.4.0", original.version());
    }

    @Test
    void getAsVersionSameVersionReturnsSameInstance() {
        Result r = ResultFactory.create("0.4.0", "{\"cniVersion\":\"0.4.0\"}");
        assertSame(r, r.getAsVersion("0.4.0"));
    }

    @Test
    void decodeVersionDefaultsTo010() {
        assertEquals("0.1.0", ResultFactory.decodeVersion("{}"));
        assertEquals("0.4.0", ResultFactory.decodeVersion("{\"cniVersion\":\"0.4.0\"}"));
    }

    @Test
    void rejectsUnsupportedResultVersion() {
        assertThrows(CniError.class,
            () -> ResultFactory.create("0.2.0", "{\"cniVersion\":\"0.2.0\"}"));
    }

    @Test
    void roundTripsThroughJson() {
        String json = "{\"cniVersion\":\"1.0.0\",\"ips\":[{\"version\":\"6\",\"address\":\"fd00::1/64\"}]}";
        Result r = ResultFactory.createFromBytes(json);

        CurrentResult reparsed = (CurrentResult) ResultFactory.createFromBytes(r.toJsonString());

        assertEquals("1.0.0", reparsed.version());
        assertEquals("fd00::1/64", reparsed.ips.get(0).address);
    }

    @Test
    void createFromBytesRejectsMalformedJson() {
        CniError e = assertThrows(CniError.class, () -> ResultFactory.createFromBytes("{"));
        assertNotNull(e.getCause());
    }
}
