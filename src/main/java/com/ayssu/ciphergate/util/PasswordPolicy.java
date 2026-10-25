package com.ayssu.ciphergate.util;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 50
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码长度须为6-50位，UTF-8编码不能超过72字节");
        }
    }
}
