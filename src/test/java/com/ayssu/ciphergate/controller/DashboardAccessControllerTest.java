package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.dto.DashboardAccessStatsDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.DashboardStatsService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardAccessControllerTest {
    private DashboardStatsController controller;
    private DashboardStatsService stats;
    private ApplicationMapper apps;
    private UserService users;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Application.class);
        stats = mock(DashboardStatsService.class);
        apps = mock(ApplicationMapper.class);
        users = mock(UserService.class);
        controller = new DashboardStatsController(stats, users, mock(SecurityUtils.class), apps);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unauthenticatedRequestsCannotReadSummaryOrIpRecords() {
        assertEquals(401, controller.accessStats(null).getCode());
        assertEquals(401, controller.recentAccess(null, 1, 10).getCode());
        verifyNoInteractions(stats, apps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void passwordUserCanOnlyQueryTheirOwnedApplications() {
        User user = new User();
        user.setId(7L);
        var authentication = new UsernamePasswordAuthenticationToken(user, null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Application app = new Application();
        app.setId(11L);
        when(apps.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(app));
        when(stats.getAccessStats(List.of(11L))).thenReturn(new DashboardAccessStatsDTO());
        assertEquals(200, controller.accessStats(authentication).getCode());
        controller.recentAccess(authentication, 2, 20);
        verify(stats).getAccessStats(List.of(11L));
        verify(stats).getRecentAccess(List.of(11L), 2, 20);
        ArgumentCaptor<LambdaQueryWrapper<Application>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(apps, times(2)).selectList(wrapper.capture());
        for (var query : wrapper.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("owner_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void githubLoginUsesSameOwnershipBoundary() {
        var principal = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of("id", "github-test-id", "login", "test-user"), "login");
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = new User();
        user.setId(8L);
        when(users.getUserByGithubId("github-test-id")).thenReturn(user);
        when(apps.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(stats.getAccessStats(List.of())).thenReturn(new DashboardAccessStatsDTO());
        assertEquals(200, controller.accessStats(authentication).getCode());
        verify(stats).getAccessStats(List.of());
    }
}
