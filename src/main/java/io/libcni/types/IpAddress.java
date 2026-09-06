package io.libcni.types;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal IP-literal validation and IPv4/IPv6 family detection. Never performs
 * DNS resolution; it only classifies and validates literal addresses.
 */
final class IpAddress {

    private static final Pattern IPV4 =
        Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private IpAddress() {
    }

    /**
     * Returns {@code "4"} or {@code "6"} for a CIDR or bare IP literal, or throws a
     * {@link CniError} when the address is not a valid IP literal.
     */
    static String ipVersionOf(String address) {
        if (address == null || address.isEmpty()) {
            throw new CniError(CniErrorCode.DECODING_FAILURE,
                "cannot determine IP version of empty address", "");
        }
        String ip = address;
        int slash = ip.indexOf('/');
        if (slash >= 0) {
            ip = ip.substring(0, slash);
        }
        if (ip.contains(":")) {
            if (isValidIpv6(ip)) {
                return "6";
            }
            throw new CniError(CniErrorCode.DECODING_FAILURE, "invalid IPv6 address: " + address, "");
        }
        if (isValidIpv4(ip)) {
            return "4";
        }
        throw new CniError(CniErrorCode.DECODING_FAILURE, "invalid IP address: " + address, "");
    }

    static boolean isValidIpv4(String s) {
        Matcher m = IPV4.matcher(s);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            String part = m.group(i);
            if (part.length() > 1 && part.charAt(0) == '0') {
                return false; // no leading zeros
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidIpv6(String s) {
        int pct = s.indexOf('%');
        if (pct >= 0) {
            s = s.substring(0, pct);
        }
        if (s.isEmpty()) {
            return false;
        }

        // Embedded IPv4 tail (e.g. "::ffff:192.0.2.1")
        int lastColon = s.lastIndexOf(':');
        if (lastColon >= 0) {
            String tail = s.substring(lastColon + 1);
            if (tail.indexOf('.') >= 0) {
                if (!isValidIpv4(tail)) {
                    return false;
                }
                s = s.substring(0, lastColon + 1) + "0";
            }
        }

        String[] halves = s.split("::", -1);
        if (halves.length > 2) {
            return false;
        }
        String left = halves[0];
        String right = halves.length == 2 ? halves[1] : null;

        String[] leftGroups = left.isEmpty() ? new String[0] : left.split(":");
        String[] rightGroups = (right == null || right.isEmpty()) ? new String[0] : right.split(":");

        if (!validGroups(leftGroups) || !validGroups(rightGroups)) {
            return false;
        }

        if (halves.length == 2) {
            return leftGroups.length + rightGroups.length <= 7;
        }
        return leftGroups.length == 8;
    }

    private static boolean validGroups(String[] groups) {
        for (String g : groups) {
            if (g.isEmpty() || g.length() > 4) {
                return false;
            }
            for (int i = 0; i < g.length(); i++) {
                if (Character.digit(g.charAt(i), 16) < 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
