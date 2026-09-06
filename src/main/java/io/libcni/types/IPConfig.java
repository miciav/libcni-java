package io.libcni.types;

import com.google.gson.annotations.SerializedName;

/** An IP configuration, mirroring {@code types040.IPConfig} in libcni. */
public class IPConfig {
    /** IP version, either {@code "4"} or {@code "6"}. */
    public String version;
    /** Index into the result's interfaces list. */
    @SerializedName("interface")
    public Integer iface;
    /** Address in CIDR notation, e.g. {@code "10.0.0.2/24"}. */
    public String address;
    /** Gateway IP address, e.g. {@code "10.0.0.1"}. */
    public String gateway;

    public IPConfig() {
    }

    public IPConfig copy() {
        IPConfig c = new IPConfig();
        c.version = version;
        c.iface = iface;
        c.address = address;
        c.gateway = gateway;
        return c;
    }
}
