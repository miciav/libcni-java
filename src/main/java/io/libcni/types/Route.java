package io.libcni.types;

/** A route, mirroring {@code types.Route} in libcni. */
public class Route {
    /** Destination network in CIDR notation, e.g. {@code "10.0.0.0/24"}. */
    public String dst;
    /** Gateway IP address, e.g. {@code "10.0.0.1"}. */
    public String gw;
    public Integer mtu;
    public Integer advmss;
    public Integer priority;
    public Integer table;
    public Integer scope;

    public Route() {
    }

    public Route copy() {
        Route r = new Route();
        r.dst = dst;
        r.gw = gw;
        r.mtu = mtu;
        r.advmss = advmss;
        r.priority = priority;
        r.table = table;
        r.scope = scope;
        return r;
    }
}
