package com.ayssu.ciphergate.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test void enforcesCharacterAndBcryptUtf8ByteLimits() {
        PasswordPolicy.validate("valid-password"); PasswordPolicy.validate("密".repeat(24));
        for(String value:new String[]{"short", " ".repeat(8), "x".repeat(51), "密".repeat(25)})
            assertThrows(IllegalArgumentException.class,()->PasswordPolicy.validate(value));
        assertThrows(IllegalArgumentException.class,()->PasswordPolicy.validate(null));
    }
}
