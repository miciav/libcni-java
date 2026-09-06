package io.libcni.version;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void currentIsImplementedSpecVersion() {
        assertEquals("1.1.0", Version.current());
    }

    @Test
    void emptyVersionParsesAs010() {
        assertArrayEquals(new int[]{0, 1, 0}, Version.parse(""));
    }

    @Test
    void parsesFullVersion() {
        assertArrayEquals(new int[]{1, 1, 0}, Version.parse("1.1.0"));
    }

    @Test
    void parsesShortVersionWithZeroMicro() {
        assertArrayEquals(new int[]{0, 4, 0}, Version.parse("0.4"));
    }

    @Test
    void parsesMajorOnly() {
        assertArrayEquals(new int[]{1, 0, 0}, Version.parse("1"));
    }

    @Test
    void rejectsTooManyParts() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3.4"));
    }

    @Test
    void rejectsNonNumericMajor() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("x.0.0"));
    }

    @Test
    void greaterThanComparesMajorMinorMicro() {
        assertTrue(Version.greaterThan("1.1.0", "1.0.0"));
        assertTrue(Version.greaterThan("0.4.0", "0.3.1"));
        assertFalse(Version.greaterThan("0.4.0", "0.4.0"));
        assertFalse(Version.greaterThan("0.3.0", "0.4.0"));
    }

    @Test
    void greaterThanOrEqualIncludesEquality() {
        assertTrue(Version.greaterThanOrEqualTo("0.4.0", "0.4.0"));
        assertTrue(Version.greaterThanOrEqualTo("1.0.0", "0.4.0"));
        assertFalse(Version.greaterThanOrEqualTo("0.3.1", "0.4.0"));
    }
}
