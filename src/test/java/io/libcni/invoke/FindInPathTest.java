package io.libcni.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.libcni.types.CniError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindInPathTest {

    @TempDir
    Path dir;

    @Test
    void findsPluginInPath() throws IOException {
        Files.createFile(dir.resolve("bridge"));

        String found = FindInPath.find("bridge", List.of(dir.toString()));

        assertEquals(dir.resolve("bridge").toString(), found);
    }

    @Test
    void notFoundThrows() {
        assertThrows(CniError.class, () -> FindInPath.find("nope", List.of(dir.toString())));
    }

    @Test
    void rejectsEmptyPluginName() {
        assertThrows(IllegalArgumentException.class, () -> FindInPath.find("", List.of(dir.toString())));
    }

    @Test
    void rejectsPluginNameWithSeparator() {
        assertThrows(IllegalArgumentException.class, () -> FindInPath.find("sub/plugin", List.of(dir.toString())));
    }

    @Test
    void rejectsEmptyPaths() {
        assertThrows(IllegalArgumentException.class, () -> FindInPath.find("bridge", List.of()));
    }
}
