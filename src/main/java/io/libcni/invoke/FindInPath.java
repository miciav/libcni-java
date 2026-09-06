package io.libcni.invoke;

import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import java.io.File;
import java.util.List;

/**
 * Locates a CNI plugin binary within the given search paths, mirroring
 * {@code invoke/find.go} in libcni.
 */
public final class FindInPath {

    /** Executable filename extensions for the current platform. */
    public static final String[] EXECUTABLE_FILE_EXTENSIONS =
        isWindows() ? new String[]{".exe"} : new String[]{""};

    private FindInPath() {
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Returns the full path of {@code plugin} by searching {@code paths}, or throws
     * a {@link CniError} if it cannot be found.
     *
     * @throws IllegalArgumentException for a missing or invalid plugin name, or empty paths
     */
    public static String find(String plugin, List<String> paths) {
        if (plugin == null || plugin.isEmpty()) {
            throw new IllegalArgumentException("no plugin name provided");
        }
        if (plugin.contains(File.separator)) {
            throw new IllegalArgumentException("invalid plugin name: " + plugin);
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("no paths provided");
        }

        for (String path : paths) {
            for (String ext : EXECUTABLE_FILE_EXTENSIONS) {
                File f = new File(path, plugin + ext);
                if (f.isFile()) {
                    return f.getPath();
                }
            }
        }
        throw new CniError(CniErrorCode.PLUGIN_NOT_AVAILABLE,
            "failed to find plugin \"" + plugin + "\" in path " + paths, "");
    }
}
