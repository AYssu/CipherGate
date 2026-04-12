package com.ayssu.ciphergate.thirdparty.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SemverThreeTest {

    @Test
    void isThreePartNumeric() {
        assertTrue(SemverThree.isThreePartNumeric("1.0.0"));
        assertTrue(SemverThree.isThreePartNumeric(" 2.10.3 "));
        assertFalse(SemverThree.isThreePartNumeric("1.0"));
        assertFalse(SemverThree.isThreePartNumeric("v1.0.0"));
        assertFalse(SemverThree.isThreePartNumeric("1.0.0-beta"));
        assertFalse(SemverThree.isThreePartNumeric(""));
        assertFalse(SemverThree.isThreePartNumeric(null));
    }

    @Test
    void compare() {
        assertTrue(SemverThree.compare("1.0.0", "1.0.1") < 0);
        assertTrue(SemverThree.compare("2.0.0", "1.9.9") > 0);
        assertEquals(0, SemverThree.compare("1.2.3", "1.2.3"));
        assertTrue(SemverThree.compare("0.0.10", "0.0.9") > 0);
    }

    @Test
    void compare_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> SemverThree.compare("1.0", "1.0.0"));
    }
}
