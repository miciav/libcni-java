package io.libcni.types;

/**
 * Well-known CNI error codes.
 *
 * @see <a href="https://github.com/containernetworking/cni/blob/main/SPEC.md#error">CNI SPEC, Error</a>
 */
public final class CniErrorCode {

    private CniErrorCode() {
    }

    public static final int UNKNOWN = 0;
    public static final int INCOMPATIBLE_CNI_VERSION = 1;
    public static final int UNSUPPORTED_FIELD = 2;
    public static final int UNKNOWN_CONTAINER = 3;
    public static final int INVALID_ENVIRONMENT_VARIABLES = 4;
    public static final int IO_FAILURE = 5;
    public static final int DECODING_FAILURE = 6;
    public static final int INVALID_NETWORK_CONFIG = 7;
    public static final int INVALID_NETNS = 8;
    public static final int TRY_AGAIN_LATER = 11;
    public static final int PLUGIN_NOT_AVAILABLE = 50;
    public static final int LIMITED_CONNECTIVITY = 51;
    public static final int INTERNAL = 999;
}
