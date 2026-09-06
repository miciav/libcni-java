package io.libcni.version;

/**
 * CNI spec version parsing and comparison, mirroring the behaviour of
 * {@code pkg/version/plugin.go} and {@code pkg/version/version.go} in libcni.
 */
public final class Version {

    private Version() {
    }

    /** The CNI spec version implemented by this library. */
    public static String current() {
        return "1.1.0";
    }

    /**
     * Parses a version string like {@code "0.4.0"} into {@code [major, minor, micro]}.
     * An empty string is the special case "no version declared", which per the
     * CNI spec means {@code 0.1.0}.
     *
     * @throws IllegalArgumentException if the version is malformed
     */
    public static int[] parse(String version) {
        if (version == null || version.isEmpty()) {
            return new int[]{0, 1, 0};
        }

        String[] parts = version.split("\\.", -1);
        if (parts.length >= 4) {
            throw new IllegalArgumentException("invalid version \"" + version + "\": too many parts");
        }

        int major = parseInt(parts[0], "major", version);
        int minor = parts.length >= 2 ? parseInt(parts[1], "minor", version) : 0;
        int micro = parts.length >= 3 ? parseInt(parts[2], "micro", version) : 0;
        return new int[]{major, minor, micro};
    }

    private static int parseInt(String part, String label, String version) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "failed to convert " + label + " version part \"" + part + "\" of \"" + version + "\"", e);
        }
    }

    /** Returns {@code true} if {@code version} is greater than {@code other}. */
    public static boolean greaterThan(String version, String other) {
        return compare(version, other) > 0;
    }

    /** Returns {@code true} if {@code version} is greater than or equal to {@code other}. */
    public static boolean greaterThanOrEqualTo(String version, String other) {
        return compare(version, other) >= 0;
    }

    /** Compares two version strings, returning a negative, zero or positive integer. */
    public static int compare(String version, String other) {
        int[] a = parse(version);
        int[] b = parse(other);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }
}
