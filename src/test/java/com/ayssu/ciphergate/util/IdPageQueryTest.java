package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
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

class IdPageQueryTest {
    @BeforeEach
    void initMetadata() {
        for (Class<?> type : List.of(Application.class, LicenseKey.class, AppVariable.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
    }

    @Test
    void normalizesNullNegativeAndOversizedPagination() {
        assertEquals(1, IdPageQuery.page(null, null).getCurrent());
        assertEquals(10, IdPageQuery.page(null, null).getSize());
        assertEquals(1, IdPageQuery.page(-1, 0).getCurrent());
        assertEquals(1, IdPageQuery.page(-1, 0).getSize());
        assertEquals(100, IdPageQuery.page(2, Integer.MAX_VALUE).getSize());
        assertEquals(2, IdPageQuery.page(2, 20).getCurrent());
        assertEquals(20, IdPageQuery.page(2, 20).getSize());
        assertEquals(500, IdPageQuery.page(1, 500, 1000).getSize());
        assertEquals(1000, IdPageQuery.page(1, 5000, 1000).getSize());
        assertThrows(IllegalArgumentException.class, () -> IdPageQuery.page(1, 10, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sortsOnlyNarrowColumnsThenReturnsFullRecordsInPageOrder() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        Application first = app(2L);
        first.setNotice("large notice");
        first.setFeatures(Map.of("free", true));
        Application second = app(1L);
        LambdaQueryWrapper<Application> filter = new LambdaQueryWrapper<Application>()
                .eq(Application::getOwnerId, 7L)
                .like(Application::getAppName, "example")
                .orderByDesc(Application::getCreatedAt, Application::getId);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Application> query = invocation.getArgument(1);
            assertEquals("id,created_at", query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("ORDER BY created_at DESC,id DESC"));
            assertTrue(query.getSqlSegment().contains("owner_id ="));
            assertFalse(query.getSqlSelect().contains("features"));
            Page<Application> page = invocation.getArgument(0);
            page.setTotal(42);
            page.setRecords(List.of(app(2L), app(1L)));
            return page;
        });
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<Application> query = invocation.getArgument(0);
            assertTrue(query.getSqlSelect().contains("features"));
            assertTrue(query.getSqlSelect().contains("notice"));
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("owner_id ="));
            assertTrue(sql.contains("app_name LIKE"));
            assertTrue(sql.contains("id IN"));
            assertFalse(sql.contains("ORDER BY"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            return List.of(second, first); // 数据库按主键返回；由工具恢复分页顺序。
        });
        Page<Application> result = IdPageQuery.select(mapper, IdPageQuery.page(2, 10), filter,
                Application::getId, Application::getCreatedAt);
        assertEquals(List.of(first, second), result.getRecords());
        assertEquals(42, result.getTotal());
        assertEquals(2, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals("large notice", result.getRecords().get(0).getNotice());
        assertEquals(Map.of("free", true), result.getRecords().get(0).getFeatures());
        assertNull(filter.getSqlSelect());
        assertTrue(filter.getSqlSegment().contains("ORDER BY"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyOrOutOfRangePageSkipsDetailQueryAndPreservesTotal() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Application> page = invocation.getArgument(0);
            page.setTotal(3);
            page.setRecords(List.of());
            return page;
        });
        Page<Application> result = IdPageQuery.select(mapper, IdPageQuery.page(9, 10),
                new LambdaQueryWrapper<Application>().apply("1=0"),
                Application::getId, Application::getCreatedAt);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(3, result.getTotal());
        verify(mapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletionBetweenQueriesDoesNotLeakOrReplaceRecords() {
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Application> page = invocation.getArgument(0);
            page.setRecords(List.of(app(3L), app(2L), app(1L)));
            page.setTotal(20);
            return page;
        });
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(app(1L), app(3L)));
        var result = IdPageQuery.select(mapper, IdPageQuery.page(1, 10),
                new LambdaQueryWrapper<Application>().eq(Application::getOwnerId, 7L)
                        .orderByDesc(Application::getCreatedAt, Application::getId),
                Application::getId, Application::getCreatedAt);
        assertEquals(List.of(3L, 1L), result.getRecords().stream().map(Application::getId).toList());
        assertEquals(20, result.getTotal());
    }

    @Test
    @SuppressWarnings("unchecked")
    void licenseMetadataAndCoreDataAreNotInSortProjection() {
        LicenseKeyMapper mapper = mock(LicenseKeyMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<LicenseKey> query = invocation.getArgument(1);
            assertEquals("id,created_at", query.getSqlSelect());
            assertFalse(query.getSqlSelect().contains("metadata"));
            assertFalse(query.getSqlSelect().contains("core_data"));
            return invocation.getArgument(0);
        });
        IdPageQuery.select(mapper, IdPageQuery.page(1, 10),
                new LambdaQueryWrapper<LicenseKey>().eq(LicenseKey::getAppId, 8L)
                        .orderByDesc(LicenseKey::getCreatedAt, LicenseKey::getId),
                LicenseKey::getId, LicenseKey::getCreatedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void variableMixedDirectionSortProjectsBothSortColumns() {
        AppVariableMapper mapper = mock(AppVariableMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AppVariable> query = invocation.getArgument(1);
            assertEquals("id,sort_order,created_at", query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("ORDER BY sort_order ASC,created_at DESC,id DESC"));
            assertFalse(query.getSqlSelect().contains("variable_value"));
            return invocation.getArgument(0);
        });
        IdPageQuery.select(mapper, IdPageQuery.page(1, 10),
                new LambdaQueryWrapper<AppVariable>().orderByAsc(AppVariable::getSortOrder)
                        .orderByDesc(AppVariable::getCreatedAt, AppVariable::getId),
                AppVariable::getId, AppVariable::getSortOrder, AppVariable::getCreatedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedMybatisSqlKeepsLogicalDeletionAndBoundScopeInBothStages() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ApplicationMapper.class);
        var statement = configuration.getMappedStatement(ApplicationMapper.class.getName() + ".selectList");
        ApplicationMapper mapper = mock(ApplicationMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Application> query = invocation.getArgument(1);
            String sql = statement.getBoundSql(Map.of("ew", query)).getSql().replaceAll("\\s+", " ");
            assertTrue(sql.startsWith("SELECT id,created_at FROM application"), sql);
            assertTrue(sql.contains("deleted=0"), sql);
            assertTrue(sql.contains("owner_id = ?"), sql);
            assertTrue(sql.contains("ORDER BY created_at DESC,id DESC"), sql);
            Page<Application> page = invocation.getArgument(0);
            page.setRecords(List.of(app(2L)));
            return page;
        });
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<Application> query = invocation.getArgument(0);
            String sql = statement.getBoundSql(Map.of("ew", query)).getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("features"), sql);
            assertTrue(sql.contains("deleted=0"), sql);
            assertTrue(sql.contains("owner_id = ?"), sql);
            assertTrue(sql.contains("id IN (?)"), sql);
            assertFalse(sql.contains("ORDER BY"), sql);
            return List.of(app(2L));
        });
        IdPageQuery.select(mapper, IdPageQuery.page(1, 10),
                new LambdaQueryWrapper<Application>().eq(Application::getOwnerId, 7L)
                        .orderByDesc(Application::getCreatedAt, Application::getId),
                Application::getId, Application::getCreatedAt);
    }

    private Application app(Long id) {
        Application app = new Application();
        app.setId(id);
        app.setOwnerId(7L);
        return app;
    }
}
