package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class PublicIdentitySecurityTest {
    @Test void arbitraryForwardedHeadersCannotChangeNormalizedAddress() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("198.51.100.17");
        for (String header : new String[]{"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"}) request.addHeader(header, "192.0.2.99");
        assertEquals("198.51.100.17", IpUtil.getIpAddr(request));
        request.setRemoteAddr("2001:db8::1"); assertEquals("2001:db8::1", IpUtil.getIpAddr(request));
        assertEquals("", IpUtil.getIpAddr(null));
    }
    @Test void publicQueryRejectsMissingPasswordMissingUserAndMalformedHashesUniformly() {
        var user = new AppUser(); user.setDeleted(0);
        for (String hash : new String[]{null, "", "legacy-invalid"}) {
            user.setPassword(hash);
            assertEquals("邮箱或密码错误", assertThrows(IllegalArgumentException.class,
                () -> PublicUserCredentials.verify(user, "unavailable-public-account")).getMessage());
        }
        assertEquals("邮箱或密码错误", assertThrows(IllegalArgumentException.class,
            () -> PublicUserCredentials.verify(null, "password123")).getMessage());
        user.setPassword(new BCryptPasswordEncoder().encode("password123"));
        PublicUserCredentials.verify(user, "password123");
        assertThrows(IllegalArgumentException.class, () -> PublicUserCredentials.verify(user, "wrong"));
        user.setDeleted(1); assertThrows(IllegalArgumentException.class, () -> PublicUserCredentials.verify(user, "password123"));
    }
    @Test void passwordHashIsWriteOnlyForBothJsonGenerations() throws Exception {
        var user = new AppUser(); user.setId(9L); user.setPassword("stored-password-hash");
        var jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();
        var jackson3 = tools.jackson.databind.json.JsonMapper.builder().build();
        assertFalse(jackson2.writeValueAsString(user).contains("password"));
        assertFalse(jackson3.writeValueAsString(user).contains("password"));
        assertEquals("input", jackson2.readValue("{\"password\":\"input\"}", AppUser.class).getPassword());
        assertEquals("input", jackson3.readValue("{\"password\":\"input\"}", AppUser.class).getPassword());
    }
    @Test void platformUserPasswordIsWriteOnlyForBothJsonGenerations() throws Exception {
        var user = new com.ayssu.ciphergate.entity.User(); user.setId(9L); user.setPassword("synthetic-hash");
        var jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();
        var jackson3 = tools.jackson.databind.json.JsonMapper.builder().build();
        assertFalse(jackson2.writeValueAsString(user).contains("password"));
        assertFalse(jackson3.writeValueAsString(user).contains("synthetic-hash"));
        assertEquals("input", jackson2.readValue("{\"password\":\"input\"}", com.ayssu.ciphergate.entity.User.class).getPassword());
        assertEquals("input", jackson3.readValue("{\"password\":\"input\"}", com.ayssu.ciphergate.entity.User.class).getPassword());
    }

}
