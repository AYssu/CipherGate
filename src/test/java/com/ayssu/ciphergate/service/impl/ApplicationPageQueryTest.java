package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.agent.AgentAuthorizationService;
import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.dto.ApplicationQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.*;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ApplicationPageQueryTest {
    private ApplicationMapper apps;
    private UserMapper users;
    private SecurityUtils security;
    private AgentAuthorizationService agents;
    private ApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        for (Class<?> type : List.of(Application.class, User.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
        apps = mock(ApplicationMapper.class);
        users = mock(UserMapper.class);
        security = mock(SecurityUtils.class);
        agents = mock(AgentAuthorizationService.class);
        service = new ApplicationServiceImpl(apps, mock(ApplicationLogMapper.class), mock(PluginModuleMapper.class),
                users, security, agents, mock(SystemMessageService.class), mock(MinioObjectService.class),
                mock(MinioProperties.class), mock(UserMembershipService.class));
    }

    @Test
    void adminUsesOwnerFilterPreservesFullConfigAndBatchesOwnerLookup() {
        when(security.isAdmin(7L)).thenReturn(true);
        ApplicationQueryDTO query = new ApplicationQueryDTO();
        query.setOwnerId(7L);
        query.setSize(1000); // 兼容前端应用选择器。
        Application app = app(2L, 7L);
        when(apps.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<Application>) invocation.getArgument(1);
            assertEquals("id,created_at", wrapper.getSqlSelect());
            assertTrue(wrapper.getSqlSegment().contains("owner_id ="));
            Page<Application> page = invocation.getArgument(0);
            assertEquals(1000, page.getSize());
            page.setRecords(List.of(app(2L, 7L)));
            page.setTotal(1);
            return page;
        });
        when(apps.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<Application>) invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("owner_id ="));
            assertFalse(wrapper.getSqlSegment().contains("ORDER BY"));
            assertTrue(wrapper.getSqlSelect().contains("features"));
            return List.of(app);
        });
        User owner = new User();
        owner.setId(7L);
        owner.setName("owner");
        when(users.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<User>) invocation.getArgument(0);
            assertEquals("id,name,login", wrapper.getSqlSelect());
            return List.of(owner);
        });
        var result = service.getApplicationPage(query, 7L);
        assertEquals("owner", result.getRecords().get(0).getOwnerName());
        assertEquals(Map.of("test", true), result.getRecords().get(0).getFeatures());
        assertEquals("test-only-secret", result.getRecords().get(0).getAppSecret());
        verify(users, never()).selectById(any());
        verify(security, times(1)).isAdmin(7L);
        verify(apps, never()).selectList(isNull());
    }

    @Test
    void ownedAndDelegatedScopesSurviveBothQueriesAndDelegatedSecretsRemainMasked() {
        Application own = app(1L, 7L);
        Application delegated = app(2L, 9L);
        when(agents.listDelegatedAppIds(7L)).thenReturn(List.of(2L));
        when(apps.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<Application>) invocation.getArgument(0);
            if ("id".equals(wrapper.getSqlSelect())) {
                assertTrue(wrapper.getSqlSegment().contains("owner_id ="));
                return List.of(app(1L, 7L));
            }
            assertTrue(wrapper.getSqlSegment().contains("id IN"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(2L));
            return List.of(own, delegated);
        });
        when(apps.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<Application>) invocation.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("id IN"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(2L));
            assertFalse(wrapper.getParamNameValuePairs().containsValue(999L));
            Page<Application> page = invocation.getArgument(0);
            page.setRecords(List.of(app(2L, 9L), app(1L, 7L)));
            page.setTotal(2);
            return page;
        });
        var query = new ApplicationQueryDTO();
        query.setOwnerId(999L); // 普通用户不能通过 ownerId 扩大范围。
        var result = service.getApplicationPage(query, 7L);
        assertEquals(List.of(2L, 1L), result.getRecords().stream().map(Application::getId).toList());
        assertNull(delegated.getAppSecret());
        assertTrue(delegated.getFeatures().isEmpty());
        assertEquals("test-only-secret", own.getAppSecret());
        verify(apps, never()).selectList(isNull());
    }

    @Test
    void noAccessibleApplicationsFailClosedAndDoNotLoadOwners() {
        when(agents.listDelegatedAppIds(7L)).thenReturn(List.of());
        when(apps.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(apps.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            var wrapper = (LambdaQueryWrapper<Application>) invocation.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("1=0"));
            return invocation.getArgument(0);
        });
        assertTrue(service.getApplicationPage(new ApplicationQueryDTO(), 7L).getRecords().isEmpty());
        verify(apps, times(1)).selectList(any(LambdaQueryWrapper.class));
        verifyNoInteractions(users);
    }

    private Application app(Long id, Long ownerId) {
        Application app = new Application();
        app.setId(id);
        app.setOwnerId(ownerId);
        app.setFeatures(Map.of("test", true));
        app.setAppSecret("test-only-secret");
        return app;
    }
}
