package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.DashboardAccessRecordDTO;
import com.ayssu.ciphergate.dto.DashboardAccessStatsDTO;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardAccessStatsTest {
    private AccessEventMapper events;
    private DashboardStatsService stats;

    @BeforeEach
    void setUp() {
        events = mock(AccessEventMapper.class);
        stats = new DashboardStatsService(mock(LicenseKeyMapper.class), mock(AppUserMapper.class), events,
                mock(ActivityLogMapper.class), mock(AppUserWsPresenceRegistry.class));
    }

    @Test
    void emptyOwnershipCannotReadAllApplications() {
        assertEquals(0, stats.getAccessStats(List.of()).getActiveDeviceToday());
        assertEquals(0, stats.getAccessStats(null).getActiveVisitor7d());
        assertEquals(0, stats.getRecentAccess(List.of(), 1, 10).getTotal());
        verifyNoInteractions(events);
    }

    @Test
    void summaryUsesOwnedAppsAndSevenNaturalDays() {
        DashboardAccessStatsDTO expected = new DashboardAccessStatsDTO();
        expected.setActiveCardToday(2);
        when(events.selectLoginStats(eq(List.of(1L)), any(), any(), any(), any())).thenReturn(expected);
        assertSame(expected, stats.getAccessStats(List.of(1L)));
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> today = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(events).selectLoginStats(eq(List.of(1L)), start.capture(), today.capture(), end.capture(), any());
        assertEquals(today.getValue().minusDays(6), start.getValue());
        assertEquals(today.getValue().plusDays(1), end.getValue());
        assertEquals(0, today.getValue().getHour());
    }

    @Test
    void recentRecordsAreBoundedAndPaginated() {
        when(events.countRecentLogins(eq(List.of(1L)), any(), any())).thenReturn(25L);
        DashboardAccessRecordDTO record = new DashboardAccessRecordDTO();
        when(events.selectRecentLogins(eq(List.of(1L)), any(), any(), eq(10L), eq(10L)))
                .thenReturn(List.of(record));
        var page = stats.getRecentAccess(List.of(1L), 2, 10);
        assertEquals(25, page.getTotal());
        assertEquals(2, page.getCurrent());
        assertEquals(List.of(record), page.getRecords());
    }

    @Test
    void oversizedAndNegativePageArgumentsAreClampedWithoutOverflow() {
        var page = stats.getRecentAccess(List.of(1L), Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(1_000_000, page.getCurrent());
        assertEquals(100, page.getSize());
        verify(events, never()).selectRecentLogins(anyList(), any(), any(), anyLong(), anyLong());
        var first = stats.getRecentAccess(List.of(), -1, -1);
        assertEquals(1, first.getCurrent());
        assertEquals(1, first.getSize());
    }

    @Test
    void nullSummaryFallsBackToZeroMetrics() {
        assertEquals(0, stats.getAccessStats(List.of(1L)).getRecentCardCount());
    }
}
