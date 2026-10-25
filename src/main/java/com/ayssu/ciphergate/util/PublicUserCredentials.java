package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.AppUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Identical response and a BCrypt operation for absent accounts prevent easy enumeration. */
public final class PublicUserCredentials {
    private PublicUserCredentials() {}
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String DUMMY = ENCODER.encode("unavailable-public-account");
    public static void verify(AppUser user, String password) {
        String hash = user != null && user.getPassword() != null ? user.getPassword() : DUMMY;
        boolean matches = false;
        if (password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72) {
            try { matches = ENCODER.matches(password, hash); } catch (IllegalArgumentException ignored) { /* invalid legacy hash */ }
        }
        if (user == null || user.getPassword() == null || user.getPassword().isBlank() || !matches || Integer.valueOf(1).equals(user.getDeleted())) throw new IllegalArgumentException("邮箱或密码错误");
    }
}
