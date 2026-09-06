package io.libcni.utils;

import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import java.util.regex.Pattern;

/**
 * Validation helpers mirroring {@code pkg/utils/utils.go} in libcni.
 *
 * <p>Each method throws a {@link CniError} when the value is invalid.</p>
 */
public final class Validation {

    private Validation() {
    }

    /** Valid characters for container IDs and network names. */
    private static final Pattern VALID_NAME =
        Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$");

    /** The maximum allowed length of a Linux interface name. */
    private static final int MAX_INTERFACE_NAME_LENGTH = 15;

    public static void validateContainerID(String containerID) {
        if (containerID == null || containerID.isEmpty()) {
            throw new CniError(CniErrorCode.UNKNOWN_CONTAINER, "missing containerID", "");
        }
        if (!VALID_NAME.matcher(containerID).matches()) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES,
                "invalid characters in containerID", containerID);
        }
    }

    public static void validateNetworkName(String networkName) {
        if (networkName == null || networkName.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG, "missing network name", "");
        }
        if (!VALID_NAME.matcher(networkName).matches()) {
            throw new CniError(CniErrorCode.INVALID_NETWORK_CONFIG,
                "invalid characters found in network name", networkName);
        }
    }

    /**
     * Validates an interface name against the four Linux rules: non-empty, shorter
     * than 16 characters, not {@code .} or {@code ..}, and free of {@code /},
     * {@code :} or whitespace.
     */
    public static void validateInterfaceName(String ifName) {
        if (ifName == null || ifName.isEmpty()) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES, "interface name is empty", "");
        }
        if (ifName.length() > MAX_INTERFACE_NAME_LENGTH) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES, "interface name is too long",
                "interface name should be less than " + (MAX_INTERFACE_NAME_LENGTH + 1) + " characters");
        }
        if (ifName.equals(".") || ifName.equals("..")) {
            throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES, "interface name is . or ..", "");
        }
        for (int i = 0; i < ifName.length(); i++) {
            char c = ifName.charAt(i);
            if (c == '/' || c == ':' || Character.isWhitespace(c)) {
                throw new CniError(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES,
                    "interface name contains / or : or whitespace characters", "");
            }
        }
    }
}
