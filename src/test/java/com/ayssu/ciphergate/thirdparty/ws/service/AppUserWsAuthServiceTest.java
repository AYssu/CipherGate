package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SuppressWarnings("unchecked")
class AppUserWsAuthServiceTest {
    private AppUserMapper users;
    private AppUserWsAuthService auth;
    private AppUser user;
    private final String password = " test&=密码 ";

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppUser.class);
        users = mock(AppUserMapper.class); auth = new AppUserWsAuthService(mock(ApplicationMapper.class), users);
        user = new AppUser(); user.setId(7L); user.setAppId(1L);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(4)));
        when(users.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(user));
    }

    @Test
    void usernameOrEmailLookupIsAppScopedAndDoesNotTrimPassword() {
        assertSame(user, auth.loginAppUser(1L, " Alice@example.test ", password));
        var captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(users).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("app_id =")); assertTrue(sql.contains("deleted ="));
        assertTrue(sql.contains("username =")); assertTrue(sql.contains("lower(email)"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("Alice@example.test"));
        assertThrows(IllegalArgumentException.class, () -> auth.loginAppUser(1L, "alice", password.trim()));
    }

    @Test
    void nonexistentAmbiguousAndMissingPasswordAccountsFailClosed() {
        when(users.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> auth.loginAppUser(1L, "alice", password));
        when(users.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(user, user));
        assertThrows(IllegalArgumentException.class, () -> auth.loginAppUser(1L, "alice", password));
        when(users.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(user));
        user.setPassword(null);
        assertThrows(IllegalArgumentException.class, () -> auth.loginAppUser(1L, "alice", password));
    }
}
