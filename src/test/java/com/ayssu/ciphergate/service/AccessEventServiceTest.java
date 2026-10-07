package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessEventServiceTest {
    private AccessEventMapper mapper;
    private AccessEventService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AccessEventMapper.class);
        service = new AccessEventService(mapper);
    }

    @Test
    void freeVisitorIsStableAcrossLoginsAndIpChangesWithoutCreatingRealCard() {
        var first = service.recordFreeModeCardLogin(1L, " device-A ", "192.0.2.1");
        var second = service.recordFreeModeCardLogin(1L, "device-A", "192.0.2.2");
        assertEquals(first, second);
        assertEquals("VISITOR", first.identityType());
        assertTrue(first.identityId().matches("visitor_[0-9a-f]{64}"));
        List<AccessEvent> events = events(2);
        assertEquals(0L, events.get(0).getRefId());
        assertEquals("CARD_LOGIN_FREE", events.get(0).getEventType());
        assertEquals("192.0.2.2", events.get(1).getClientIp());
    }

    @Test
    void sameDeviceInDifferentApplicationsGetsDifferentVisitorIdentity() {
        assertNotEquals(service.recordFreeModeCardLogin(1L, "device", null).identityId(),
                service.recordFreeModeCardLogin(2L, "device", null).identityId());
    }

    @Test
    void differentDevicesInSameApplicationGetDifferentVisitorIdentity() {
        assertNotEquals(service.recordFreeModeCardLogin(1L, "A", null).identityId(),
                service.recordFreeModeCardLogin(1L, "B", null).identityId());
    }

    @Test
    void paidCardReusesCardIdentityWhileRecordingEachDevice() {
        var first = service.recordCardLogin(1L, 11L, "A", "192.0.2.1");
        var second = service.recordCardLogin(1L, 11L, "B", "192.0.2.1");
        assertEquals(first, second);
        assertEquals(new AccessEventService.LoginIdentity("CARD", "card_11"), first);
        List<AccessEvent> events = events(2);
        assertNotEquals(events.get(0).getDeviceHash(), events.get(1).getDeviceHash());
        assertEquals(11L, events.get(0).getRefId());
    }

    @Test
    void paidAndFreeLoginsShareApplicationScopedDeviceDigest() {
        service.recordCardLogin(1L, 11L, "same-device", null);
        service.recordFreeModeCardLogin(1L, "same-device", null);
        List<AccessEvent> events = events(2);
        assertEquals(events.get(0).getDeviceHash(), events.get(1).getDeviceHash());
        assertNotEquals("same-device", events.get(0).getDeviceHash());
    }

    @Test
    void historicalAndWsEventsRemainCompatibleWithoutInventedDevice() {
        service.recordFreeModeCardLogin(1L);
        service.recordCardLogin(1L, 11L);
        service.recordAppUserWsLogin(1L, 7L);
        for (AccessEvent event : events(3)) {
            assertNull(event.getDeviceHash());
            assertNull(event.getClientIp());
            assertNotNull(event.getCreatedAt());
        }
    }

    @Test
    void ledgerFailureDoesNotBlockLoginOrChangeIdentity() {
        when(mapper.insert(any(AccessEvent.class))).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals("CARD", service.recordCardLogin(1L, 11L, "device", null).identityType());
        assertNotNull(service.recordFreeModeCardLogin(1L, "device", null).identityId());
    }

    @Test
    void invalidIpIsOmittedAndMissingDeviceDoesNotInventUniqueVisitor() {
        var identity = service.recordFreeModeCardLogin(1L, " ", "x".repeat(65));
        assertNull(identity.identityId());
        AccessEvent event = events(1).get(0);
        assertNull(event.getDeviceHash());
        assertNull(event.getClientIp());
        assertNull(AccessEventService.deviceHash(null, "device"));
    }

    private List<AccessEvent> events(int count) {
        ArgumentCaptor<AccessEvent> captor = ArgumentCaptor.forClass(AccessEvent.class);
        verify(mapper, times(count)).insert(captor.capture());
        return captor.getAllValues();
    }
}
