package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccessEventMapperSqlTest {
    private MybatisConfiguration configuration;
    private Map<String, Object> parameters;
    private static final String NAMESPACE = "com.ayssu.ciphergate.mapper.AccessEventMapper.";

    @BeforeEach
    void setUp() throws IOException {
        configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (var stream = Resources.getResourceAsStream("mapper/AccessEventMapper.xml")) {
            new XMLMapperBuilder(stream, configuration, "mapper/AccessEventMapper.xml", configuration.getSqlFragments()).parse();
        }
        LocalDateTime today = LocalDateTime.of(2026, 10, 7, 0, 0);
        parameters = new HashMap<>();
        parameters.put("appIds", List.of(1L, 2L));
        parameters.put("start7d", today.minusDays(6));
        parameters.put("todayStart", today);
        parameters.put("recentCutoff", today.plusHours(12).minusMinutes(5));
        parameters.put("start", today.minusDays(6));
        parameters.put("end", today.plusDays(1));
        parameters.put("limit", 10L);
        parameters.put("offset", 0L);
    }

    @Test
    void allQueriesBindOwnerIdsAndDatesRatherThanConcatenatingUserInput() {
        for (String method : List.of("selectLoginStats", "countRecentLogins", "selectRecentLogins")) {
            var bound = configuration.getMappedStatement(NAMESPACE + method).getBoundSql(parameters);
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("e.app_id IN"));
            assertTrue(sql.contains("e.created_at >= ?"));
            assertTrue(sql.contains("e.created_at < ?"));
            assertFalse(sql.contains("APP_USER_WS_LOGIN"));
            assertFalse(sql.contains("${"));
            assertTrue(bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().startsWith("__frch_appId")));
        }
    }

    @Test
    void emptyAndNullOwnershipFailClosedEvenWhenMapperCalledDirectly() {
        for (String method : List.of("selectLoginStats", "countRecentLogins", "selectRecentLogins")) {
            parameters.put("appIds", List.of());
            assertTrue(sql(method).contains("1 = 0"));
            parameters.put("appIds", null);
            assertTrue(sql(method).contains("1 = 0"));
        }
    }

    @Test
    void recordsNeverSelectCardSecretsOrTokensAndHaveStablePaginationOrder() {
        String sql = sql("selectRecentLogins");
        assertFalse(sql.contains("key_code"));
        assertFalse(sql.contains("token"));
        assertFalse(sql.contains("app_secret"));
        assertTrue(sql.contains("ORDER BY e.created_at DESC, e.id DESC"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        assertTrue(sql.contains("a.app_name AS app_name"));
    }

    private String sql(String method) {
        return configuration.getMappedStatement(NAMESPACE + method).getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
