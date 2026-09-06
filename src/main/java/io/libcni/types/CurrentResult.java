package io.libcni.types;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/**
 * The CNI result returned by plugins for spec versions 0.3.0 through 1.1.0,
 * which all share the same structure. Mirrors {@code types040.Result} and
 * {@code types100.Result} in libcni.
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

        CurrentResult r = new CurrentResult();
        r.cniVersion = version;
        r.dns = dns == null ? null : dns.copy();
        r.interfaces = copyInterfaces();
        r.ips = copyIps();
        r.routes = copyRoutes();
        return r;
    }

    @Override
    public String toJsonString() {
        return new Gson().toJson(this);
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
