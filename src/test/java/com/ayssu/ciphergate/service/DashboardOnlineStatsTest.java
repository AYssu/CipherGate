package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardOnlineStatsTest {
    @Test
    void wsAndHttpUseOneScopedUserCountNotSumOfOverlappingPopulations() {
        for (Class<?> type : List.of(AppUser.class, LicenseKey.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
        var users = mock(AppUserMapper.class);
        var cards = mock(LicenseKeyMapper.class);
        var events = mock(AccessEventMapper.class);
        var ws = mock(AppUserWsPresenceRegistry.class);
        when(ws.listOnlineAppUserIds()).thenReturn(List.of(1L, 2L));
        when(users.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(cards.selectCount(any(LambdaQueryWrapper.class))).thenReturn(4L);
        var service = new DashboardStatsService(cards, users, events, mock(ActivityLogMapper.class),
                new AppUserPresenceService(users, ws));
        var result = service.getOnlineStats(List.of(10L));
        assertEquals(3, result.getAppUserOnlineCount()); assertEquals(4, result.getCardOnlineCount());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(users, times(1)).selectCount(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("app_id IN")); assertTrue(sql.contains("deleted ="));
        assertTrue(sql.contains("AND (id IN")); assertTrue(sql.contains(" OR (app_user.deleted"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(10L));
        verifyNoInteractions(events); // no extra daily / seven-day aggregate just to count online
    }

    @Test
    void emptyOwnershipCannotQueryAllUsers() {
        var users = mock(AppUserMapper.class);
        var cards = mock(LicenseKeyMapper.class);
        var presence = mock(AppUserPresenceService.class);
        var service = new DashboardStatsService(cards, users, mock(AccessEventMapper.class), mock(ActivityLogMapper.class), presence);
        assertEquals(0, service.getOnlineStats(List.of()).getAppUserOnlineCount());
        assertEquals(0, service.getOnlineStats(null).getAppUserOnlineCount());
        verifyNoInteractions(users, cards, presence);
    }
}
