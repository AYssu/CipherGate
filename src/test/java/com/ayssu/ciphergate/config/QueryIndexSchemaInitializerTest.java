package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryIndexSchemaInitializerTest {
    @Test
    void existingIndexesAreNotRebuilt() {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(1);
        new QueryIndexSchemaInitializer(mapper).initialize();
        verify(mapper, times(11)).executeRawQueryForInteger(anyString());
        verify(mapper, never()).executeRawSql(anyString());
    }

    @Test
    void missingIndexesUseTableScopedChecksOnlineDdlAndMatchFreshSchema() throws Exception {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(0);
        new QueryIndexSchemaInitializer(mapper).initialize();
        ArgumentCaptor<String> checks = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(11)).executeRawQueryForInteger(checks.capture());
        assertTrue(checks.getAllValues().stream().allMatch(sql ->
                sql.contains("TABLE_SCHEMA = DATABASE()") && sql.contains("TABLE_NAME = '") && sql.contains("INDEX_NAME = '")));
        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(11)).executeRawSql(ddl.capture());
        String schema = Files.readString(Path.of("src/main/resources/sql/init.sql"));
        for (String sql : ddl.getAllValues()) {
            assertTrue(sql.endsWith("ALGORITHM=INPLACE LOCK=NONE"));
            String[] tokens = sql.split(" ");
            String index = tokens[2];
            String table = tokens[4];
            String columns = sql.substring(sql.indexOf('(') + 1, sql.lastIndexOf(')'));
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS " + table + " (");
            String tableDdl = schema.substring(start, schema.indexOf("\n)", start));
            assertTrue(tableDdl.contains("INDEX " + index + " (" + columns + ")"), sql);
        }
    }

    @Test
    void oneIndexFailureDoesNotBlockOtherIndexesOrAttemptLockingFallback() {
        RawSqlMapper mapper = mock(RawSqlMapper.class);
        when(mapper.executeRawQueryForInteger(anyString())).thenReturn(0);
        doThrow(new IllegalStateException("DDL not supported"))
                .when(mapper).executeRawSql(startsWith("CREATE INDEX idx_application_page "));
        assertDoesNotThrow(() -> new QueryIndexSchemaInitializer(mapper).initialize());
        verify(mapper, times(11)).executeRawSql(endsWith("ALGORITHM=INPLACE LOCK=NONE"));
        verify(mapper).executeRawSql(startsWith("CREATE INDEX idx_variable_history_page "));
    }
}
