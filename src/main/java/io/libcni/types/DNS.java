package io.libcni.types;

import java.util.ArrayList;
import java.util.List;

/** DNS configuration, mirroring {@code types.DNS} in libcni. */
public class DNS {
    public List<String> nameservers;
    public String domain;
    public List<String> search;
    public List<String> options;

    public DNS() {
    }

    public boolean isEmpty() {
        return (nameservers == null || nameservers.isEmpty())
            && (domain == null || domain.isEmpty())
            && (search == null || search.isEmpty())
            && (options == null || options.isEmpty());
    }

    public DNS copy() {
        DNS d = new DNS();
        d.domain = domain;
        d.nameservers = copyList(nameservers);
        d.search = copyList(search);
        d.options = copyList(options);
        return d;
    }

    private static List<String> copyList(List<String> in) {
        return in == null ? null : new ArrayList<>(in);
    }
}
