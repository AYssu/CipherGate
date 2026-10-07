package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccessEventSchemaInitializerTest {
    @Test
    void existingSchemaIsNotAltered() {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(1);
        new AccessEventSchemaInitializer(mapper).initialize();
        verify(mapper, times(3)).executeRawQueryForInteger(anyString());
        verify(mapper, never()).executeRawSql(anyString());
    }

    @Test
    void oldSchemaAddsNullableColumnsAndIndexWithoutUpdatingHistory() {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(0);
        new AccessEventSchemaInitializer(mapper).initialize();
        verify(mapper).executeRawSql(startsWith("ALTER TABLE access_event ADD COLUMN device_hash VARCHAR(64) NULL"));
        verify(mapper).executeRawSql(startsWith("ALTER TABLE access_event ADD COLUMN client_ip VARCHAR(64) NULL"));
        verify(mapper).executeRawSql(startsWith("CREATE INDEX idx_access_login_stats ON access_event"));
        verify(mapper, times(3)).executeRawSql(anyString());
    }

    @Test
    void partiallyUpgradedSchemaOnlyAddsMissingColumn() {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(1);
        when(mapper.executeRawQueryForInteger(contains("COLUMN_NAME = 'client_ip'"))).thenReturn(0);
        new AccessEventSchemaInitializer(mapper).initialize();
        verify(mapper, times(1)).executeRawSql(anyString());
        verify(mapper).executeRawSql(startsWith("ALTER TABLE access_event ADD COLUMN client_ip"));
    }
}
