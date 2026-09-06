package io.libcni.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The CNI result returned by plugins. Result versions split into two families
 * with different schemas: 0.3.x/0.4.0 IPs carry an explicit {@code version}
 * field and their interfaces lack {@code mtu}/{@code socketPath}/{@code pciID};
 * 1.0.0/1.1.0 IPs omit {@code version} (it is implied by the address) and their
 * interfaces may carry those fields.
 *
 * <p>This class stores the richest form and applies the family-specific rules at
 * the JSON boundary ({@link #toJsonString()} and {@link ResultFactory}) and in
 * {@link #getAsVersion(String)}.</p>
 */
public class CurrentResult implements Result {

    /** CNI result versions that share the current result structure. */
    public static final String[] SUPPORTED_VERSIONS = {"0.3.0", "0.3.1", "0.4.0", "1.0.0", "1.1.0"};

    /** Version used when a result omits its {@code cniVersion}. */
    public static final String IMPLEMENTED_SPEC_VERSION = "1.0.0";

    public String cniVersion;
    public List<Interface> interfaces;
    public List<IPConfig> ips;
    public List<Route> routes;
    public DNS dns;

    public CurrentResult() {
    }

    /** True for the 0.3.x/0.4.0 result family (explicit IP {@code version}). */
    public static boolean isLegacyVersion(String version) {
        return "0.3.0".equals(version) || "0.3.1".equals(version) || "0.4.0".equals(version);
    }

    private static boolean isCurrentVersion(String version) {
        return "1.0.0".equals(version) || "1.1.0".equals(version);
    }

    @Override
    public String version() {
        return cniVersion;
    }

    @Override
    public Result getAsVersion(String version) {
        if (cniVersion == null || cniVersion.isEmpty()) {
            cniVersion = IMPLEMENTED_SPEC_VERSION;
        }
        if (version == null || version.isEmpty()) {
            version = "0.1.0";
        }
        if (cniVersion.equals(version)) {
            return this;
        }
        if (!ResultFactory.isSupported(version)) {
            throw new CniError(CniErrorCode.INCOMPATIBLE_CNI_VERSION,
                "unsupported CNI result version \"" + version + "\"", "");
        }

        boolean fromLegacy = isLegacyVersion(cniVersion);
        boolean toLegacy = isLegacyVersion(version);

        CurrentResult r = new CurrentResult();
        r.cniVersion = version;
        r.dns = dns == null ? null : dns.copy();
        r.interfaces = copyInterfaces();
        r.ips = copyIps();
        r.routes = copyRoutes();

        if (fromLegacy && !toLegacy) {
            // Upgrade to 1.x: drop the explicit IP version (implied by the address).
            if (r.ips != null) {
                for (IPConfig ip : r.ips) {
                    ip.version = null;
                }
            }
        } else if (!fromLegacy && toLegacy) {
            // Downgrade to 0.4.x: derive the IP version and drop 1.x-only interface fields.
            if (r.ips != null) {
                for (IPConfig ip : r.ips) {
                    ip.version = IpAddress.ipVersionOf(ip.address);
                }
            }
            if (r.interfaces != null) {
                for (Interface i : r.interfaces) {
                    i.mtu = null;
                    i.socketPath = null;
                    i.pciID = null;
                }
            }
        }
        // Same family: only the version changes; the types are identical.
        return r;
    }

    @Override
    public String toJsonString() {
        JsonObject o = new JsonObject();
        if (cniVersion != null && !cniVersion.isEmpty()) {
            o.addProperty("cniVersion", cniVersion);
        }
        boolean legacy = isLegacyVersion(cniVersion == null ? IMPLEMENTED_SPEC_VERSION : cniVersion);

        if (interfaces != null && !interfaces.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (Interface i : interfaces) {
                arr.add(interfaceToJson(i, legacy));
            }
            o.add("interfaces", arr);
        }
        if (ips != null && !ips.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (IPConfig ip : ips) {
                arr.add(ipConfigToJson(ip, legacy));
            }
            o.add("ips", arr);
        }
        if (routes != null && !routes.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (Route r : routes) {
                arr.add(routeToJson(r));
            }
            o.add("routes", arr);
        }
        if (dns != null && !dns.isEmpty()) {
            o.add("dns", dnsToJson(dns));
        }
        return o.toString();
    }

    private static JsonObject interfaceToJson(Interface i, boolean legacy) {
        JsonObject o = new JsonObject();
        if (i.name != null) {
            o.addProperty("name", i.name);
        }
        if (i.mac != null && !i.mac.isEmpty()) {
            o.addProperty("mac", i.mac);
        }
        if (!legacy) {
            if (i.mtu != null && i.mtu != 0) {
                o.addProperty("mtu", i.mtu);
            }
            if (i.socketPath != null && !i.socketPath.isEmpty()) {
                o.addProperty("socketPath", i.socketPath);
            }
            if (i.pciID != null && !i.pciID.isEmpty()) {
                o.addProperty("pciID", i.pciID);
            }
        }
        if (i.sandbox != null && !i.sandbox.isEmpty()) {
            o.addProperty("sandbox", i.sandbox);
        }
        return o;
    }

    private static JsonObject ipConfigToJson(IPConfig ip, boolean legacy) {
        JsonObject o = new JsonObject();
        if (legacy) {
            String v = ip.version;
            if (v == null || v.isEmpty()) {
                v = IpAddress.ipVersionOf(ip.address);
            }
            o.addProperty("version", v);
        }
        if (ip.iface != null) {
            o.addProperty("interface", ip.iface);
        }
        if (ip.address != null) {
            o.addProperty("address", ip.address);
        }
        if (ip.gateway != null && !ip.gateway.isEmpty()) {
            o.addProperty("gateway", ip.gateway);
        }
        return o;
    }

    private static JsonObject routeToJson(Route r) {
        JsonObject o = new JsonObject();
        if (r.dst != null) {
            o.addProperty("dst", r.dst);
        }
        if (r.gw != null && !r.gw.isEmpty()) {
            o.addProperty("gw", r.gw);
        }
        if (r.mtu != null) {
            o.addProperty("mtu", r.mtu);
        }
        if (r.advmss != null) {
            o.addProperty("advmss", r.advmss);
        }
        if (r.priority != null) {
            o.addProperty("priority", r.priority);
        }
        if (r.table != null) {
            o.addProperty("table", r.table);
        }
        if (r.scope != null) {
            o.addProperty("scope", r.scope);
        }
        return o;
    }

    private static JsonObject dnsToJson(DNS d) {
        JsonObject o = new JsonObject();
        addStringArray(o, "nameservers", d.nameservers);
        if (d.domain != null && !d.domain.isEmpty()) {
            o.addProperty("domain", d.domain);
        }
        addStringArray(o, "search", d.search);
        addStringArray(o, "options", d.options);
        return o;
    }

    private static void addStringArray(JsonObject o, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String v : values) {
                arr.add(v);
            }
            o.add(key, arr);
        }
    }

    private List<Interface> copyInterfaces() {
        if (interfaces == null) {
            return null;
        }
        List<Interface> out = new ArrayList<>(interfaces.size());
        for (Interface i : interfaces) {
            out.add(i.copy());
        }
        return out;
    }

    private List<IPConfig> copyIps() {
        if (ips == null) {
            return null;
        }
        List<IPConfig> out = new ArrayList<>(ips.size());
        for (IPConfig i : ips) {
            out.add(i.copy());
        }
        return out;
    }

    private List<Route> copyRoutes() {
        if (routes == null) {
            return null;
        }
        List<Route> out = new ArrayList<>(routes.size());
        for (Route r : routes) {
            out.add(r.copy());
        }
        return out;
    }
}
