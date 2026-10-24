package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppUserPresenceServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 20, 0);
    private AppUserMapper mapper;
    private AppUserWsPresenceRegistry ws;
    private AppUserPresenceService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppUser.class);
        mapper = mock(AppUserMapper.class);
        ws = mock(AppUserWsPresenceRegistry.class);
        service = new AppUserPresenceService(mapper, ws);
        when(ws.listOnlineAppUserIds()).thenReturn(List.of());
    }

    @Test
    void httpWindowIsExactlyFiveMinutesAndAllTimeValuesAreBound() {
        var query = new LambdaQueryWrapper<AppUser>().eq(AppUser::getAppId, 10L);
        service.applyOnlineFilter(query, true, NOW);
        String sql = query.getSqlSegment();
        assertTrue(sql.startsWith("(app_id ="));
        assertTrue(sql.contains("AND ((app_user.deleted"));
        assertFalse(sql.contains("2026-10-08"));
        assertTrue(query.getParamNameValuePairs().containsValue(NOW.minusMinutes(5)));
        assertTrue(query.getParamNameValuePairs().containsValue(NOW));
        assertTrue(sql.contains("e.created_at >"));
        assertTrue(sql.contains("e.created_at <="));
        assertTrue(sql.contains("e.event_type = 'APP_USER_HTTP_LOGIN'"));
        assertTrue(sql.contains("e.app_id = app_user.app_id"));
        assertTrue(sql.contains("e.ref_id = app_user.id"));
    }

    @Test
    void wsOrHttpIsGroupedInsideExistingOwnershipScope() {
        when(ws.listOnlineAppUserIds()).thenReturn(List.of(1L, 2L));
        var query = new LambdaQueryWrapper<AppUser>().in(AppUser::getAppId, List.of(10L, 11L));
        service.applyOnlineFilter(query, true, NOW);
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("AND (id IN"));
        assertTrue(sql.contains(" OR (app_user.deleted"));
        assertTrue(query.getParamNameValuePairs().containsValue(10L));
        assertTrue(query.getParamNameValuePairs().containsValue(11L));
        assertTrue(query.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void offlineExcludesWsAndRecentHttpWithoutEmptyNotIn() {
        var emptyWsQuery = new LambdaQueryWrapper<AppUser>();
        service.applyOnlineFilter(emptyWsQuery, false, NOW);
        assertTrue(emptyWsQuery.getSqlSegment().contains("NOT (app_user.deleted"));
        assertFalse(emptyWsQuery.getSqlSegment().contains("NOT IN"));
        when(ws.listOnlineAppUserIds()).thenReturn(List.of(1L));
        var query = new LambdaQueryWrapper<AppUser>();
        service.applyOnlineFilter(query, false, NOW);
        assertTrue(query.getSqlSegment().contains("id NOT IN"));
        assertTrue(query.getSqlSegment().contains("AND NOT (app_user.deleted"));
    }

    @Test
    void databaseHttpStatusIsBatchReadAndDoesNotCreateWsStateOrTime() {
        AppUser http = user(1L, false), both = user(2L, true), wsOnly = user(3L, true), offline = user(4L, false);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(user(1L, false), user(2L, false)));
        service.enrich(List.of(http, both, wsOnly, offline), NOW);
        assertTrue(http.getOnline()); assertTrue(http.getHttpOnline()); assertFalse(http.getWsOnline());
        assertNull(http.getWsSessionCount()); assertNull(http.getWsOnlineSeconds()); assertNull(http.getWsTodayOnlineSeconds());
        assertTrue(both.getOnline()); assertTrue(both.getHttpOnline()); assertTrue(both.getWsOnline());
        assertTrue(wsOnly.getOnline()); assertFalse(wsOnly.getHttpOnline());
        assertFalse(offline.getOnline()); assertFalse(offline.getHttpOnline());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper, times(1)).selectList(query.capture());
        assertEquals("id", query.getValue().getSqlSelect());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(4L));
        verifyNoInteractions(ws);
    }

    @Test
    void expiryRefreshClearsStaleHttpFlagsWithoutClearingRealWs() {
        AppUser http = user(1L, false), both = user(2L, true);
        http.setHttpOnline(true); http.setOnline(true); both.setHttpOnline(true); both.setOnline(true);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        service.enrich(List.of(http, both), NOW.plusMinutes(5));
        assertFalse(http.getHttpOnline()); assertFalse(http.getOnline());
        assertFalse(both.getHttpOnline()); assertTrue(both.getOnline());
    }

    @Test
    void emptyPageAndUnsavedUserDoNotQueryAllUsers() {
        service.enrich(List.of(), NOW); service.enrich(null, NOW);
        AppUser unsaved = user(null, false);
        service.enrich(List.of(unsaved), NOW);
        assertFalse(unsaved.getOnline()); assertFalse(unsaved.getHttpOnline());
        verifyNoInteractions(mapper, ws);
    }

    private static AppUser user(Long id, boolean wsOnline) {
        AppUser user = new AppUser(); user.setId(id); user.setAppId(10L); user.setWsOnline(wsOnline);
        return user;
    }
}
