package io.libcni.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.libcni.types.CniError;
import io.libcni.types.CniErrorCode;
import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void acceptsValidContainerID() {
        assertDoesNotThrow(() -> Validation.validateContainerID("abc-123.def_ghi"));
    }

    @Test
    void rejectsEmptyContainerID() {
        CniError e = assertThrows(CniError.class, () -> Validation.validateContainerID(""));
        assertEquals(CniErrorCode.UNKNOWN_CONTAINER, e.code());
    }

    @Test
    void rejectsInvalidCharactersInContainerID() {
        CniError e = assertThrows(CniError.class, () -> Validation.validateContainerID("bad id!"));
        assertEquals(CniErrorCode.INVALID_ENVIRONMENT_VARIABLES, e.code());
    }

    @Test
    void acceptsValidNetworkName() {
        assertDoesNotThrow(() -> Validation.validateNetworkName("my.net_1"));
    }

    @Test
    void rejectsEmptyNetworkName() {
        CniError e = assertThrows(CniError.class, () -> Validation.validateNetworkName(""));
        assertEquals(CniErrorCode.INVALID_NETWORK_CONFIG, e.code());
    }

    @Test
    void rejectsInvalidCharactersInNetworkName() {
        CniError e = assertThrows(CniError.class, () -> Validation.validateNetworkName("net@home"));
        assertEquals(CniErrorCode.INVALID_NETWORK_CONFIG, e.code());
    }

    @Test
    void acceptsValidInterfaceName() {
        assertDoesNotThrow(() -> Validation.validateInterfaceName("eth0"));
    }

    @Test
    void rejectsEmptyInterfaceName() {
        assertThrows(CniError.class, () -> Validation.validateInterfaceName(""));
    }

    @Test
    void rejectsTooLongInterfaceName() {
        assertThrows(CniError.class, () -> Validation.validateInterfaceName("eth0123456789012"));
    }

    @Test
    void rejectsDotAndDotDot() {
        assertThrows(CniError.class, () -> Validation.validateInterfaceName("."));
        assertThrows(CniError.class, () -> Validation.validateInterfaceName(".."));
    }

    @Test
    void rejectsSeparatorsAndWhitespace() {
        assertThrows(CniError.class, () -> Validation.validateInterfaceName("eth/0"));
        assertThrows(CniError.class, () -> Validation.validateInterfaceName("eth:0"));
        assertThrows(CniError.class, () -> Validation.validateInterfaceName("eth 0"));
    }
}
