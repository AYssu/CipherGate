package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.service.AccessEventService;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.service.LicenseUnbindTimeDeductionService;
import com.ayssu.ciphergate.thirdparty.dto.CardLoginRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThirdPartyCardServiceLoginTest {
    private ApplicationMapper apps;
    private LicenseKeyMapper cards;
    private AccessEventMapper events;
    private ThirdPartyAppVariableService variables;
    private ThirdPartyHeartbeatService heartbeat;
    private ThirdPartyCardService service;
    private Application app;
    private CardLoginRequest request;

    @BeforeEach
    void setUp() {
        apps = mock(ApplicationMapper.class);
        cards = mock(LicenseKeyMapper.class);
        events = mock(AccessEventMapper.class);
        variables = mock(ThirdPartyAppVariableService.class);
        heartbeat = mock(ThirdPartyHeartbeatService.class);
        service = new ThirdPartyCardService(apps, cards, variables, heartbeat, mock(LicenseKeyService.class),
                mock(LicenseUnbindTimeDeductionService.class), new AccessEventService(events));
        app = new Application();
        app.setId(1L);
        app.setBusinessModel(2);
        when(apps.selectById(1L)).thenReturn(app);
        when(variables.getEnabledVariablesMap(eq(1L), any(AppVariableTemplateContext.class))).thenReturn(Map.of());
        request = new CardLoginRequest();
        request.setDeviceId("test-device");
    }

    @Test
    void legacyFreeClientNeedsOnlyDeviceAndStillGetsOriginalResponseFields() {
        var response = service.login(1L, request, "192.0.2.1");
        assertEquals(0L, response.getCardId());
        assertEquals("", response.getCardCode());
        assertEquals("免费版", response.getCardType());
        assertEquals(99_999L, response.getAvailable());
        assertEquals("{}", response.getVariables());
        assertEquals(false, response.getOnline());
        assertNull(response.getToken());
        assertEquals("VISITOR", response.getIdentityType());
        assertTrue(response.getIdentityId().startsWith("visitor_"));
        verify(events).insert(any(AccessEvent.class));
        verifyNoInteractions(cards, heartbeat);
    }

    @Test
    void freeLoginsAreRecordedIndividuallyButIdentityIsStableWithoutHeartbeat() {
        var first = service.login(1L, request, "192.0.2.1");
        var second = service.login(1L, request, "192.0.2.2");
        assertEquals(first.getIdentityId(), second.getIdentityId());
        verify(events, times(2)).insert(any(AccessEvent.class));
        verifyNoInteractions(heartbeat);
    }

    @Test
    void invalidFreeDeviceAndNullRequestAreNotCountedAsSuccessfulLogins() {
        request.setDeviceId(" ");
        assertThrows(RuntimeException.class, () -> service.login(1L, request, null));
        assertThrows(RuntimeException.class, () -> service.login(1L, null, null));
        verifyNoInteractions(events, cards, heartbeat);
    }

    @Test
    void variableFailureDoesNotCreateSuccessfulFreeLoginEvent() {
        when(variables.getEnabledVariablesMap(eq(1L), any(AppVariableTemplateContext.class)))
                .thenThrow(new IllegalStateException("variable unavailable"));
        assertThrows(IllegalStateException.class, () -> service.login(1L, request, null));
        verifyNoInteractions(events);
    }

    @Test
    @SuppressWarnings("unchecked")
    void paidLoginIsCountedEvenIfClientNeverUsesReturnedHeartbeatToken() {
        preparePaidCard();
        when(heartbeat.storeToken(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn("test-only-heartbeat-token");
        var response = service.login(1L, request, "192.0.2.1");
        assertEquals(11L, response.getCardId());
        assertEquals("CARD", response.getIdentityType());
        assertEquals("card_11", response.getIdentityId());
        assertEquals("test-only-heartbeat-token", response.getToken());
        verify(events).insert(any(AccessEvent.class));
        verify(heartbeat, never()).exchange(anyString());
    }

    @Test
    void failedPaidTokenIssuanceDoesNotCreateSuccessfulLoginEvent() {
        preparePaidCard();
        when(heartbeat.storeToken(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(IllegalStateException.class, () -> service.login(1L, request, null));
        verifyNoInteractions(events);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonexistentCardIsNotCounted() {
        app.setBusinessModel(1);
        request.setCardCode("TEST-CARD-NOT-REAL");
        assertThrows(RuntimeException.class, () -> service.login(1L, request, null));
        verifyNoInteractions(events, heartbeat);
    }

    @SuppressWarnings("unchecked")
    private void preparePaidCard() {
        app.setBusinessModel(1);
        request.setCardCode("TEST-CARD-NOT-REAL");
        LicenseKey card = new LicenseKey();
        card.setId(11L);
        card.setAppId(1L);
        card.setKeyCode("TEST-CARD-NOT-REAL");
        card.setKeyType("DAY");
        card.setStatus(2);
        card.setFirstUsedAt(LocalDateTime.now().minusDays(1));
        card.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(cards.selectOne(any(LambdaQueryWrapper.class))).thenReturn(card);
    }
}
