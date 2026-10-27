package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.mapper.SystemConfigMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class SystemConfigPersistenceTest {
    SystemConfigMapper mapper = mock(SystemConfigMapper.class);
    SystemConfigService service = new SystemConfigService(mapper);
    @AfterEach void cleanup() { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization(); }
    @Test void failureAndZeroAffectedRowsMustNotBecomeSuccessOrPopulateCache() {
        when(mapper.insert(any(com.ayssu.ciphergate.entity.SystemConfig.class))).thenThrow(new IllegalStateException("synthetic-db-error"));
        assertThrows(IllegalStateException.class,()->service.setConfigValue("test","new"));
        when(mapper.getConfigValue("test")).thenReturn("old");assertEquals("old",service.getConfigValue("test"));
        reset(mapper);service.refreshCache();
        assertThrows(IllegalStateException.class,()->service.setConfigValue("test","new"));
        when(mapper.getConfigValue("test")).thenReturn("old");assertEquals("old",service.getConfigValue("test"));
    }
    @Test void cachePublicationWaitsForCommitAndRollbackRetainsPriorValue() {
        when(mapper.getConfigValue("test")).thenReturn("old");assertEquals("old",service.getConfigValue("test"));
        when(mapper.insert(any(com.ayssu.ciphergate.entity.SystemConfig.class))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        service.setConfigValue("test","new");assertEquals("old",service.getConfigValue("test"));
        var listeners=TransactionSynchronizationManager.getSynchronizations();
        listeners.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals("old",service.getConfigValue("test"));
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.initSynchronization();
        service.setConfigValue("test","new");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertEquals("new",service.getConfigValue("test"));
    }
}
