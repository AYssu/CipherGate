package com.ayssu.ciphergate.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 两阶段分页：只让主键和排序列参与分页排序，再按本页主键读取完整记录。
 * 不裁剪接口字段；两阶段均保留原有权限/筛选条件和 MyBatis-Plus 逻辑删除条件。
 */
public final class IdPageQuery {
    private IdPageQuery() {
    }

    public static <T> Page<T> page(Integer current, Integer size) {
        return page(current, size, 100);
    }

    public static <T> Page<T> page(Integer current, Integer size, int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        return new Page<>(current == null ? 1 : Math.max(1, current),
                size == null ? Math.min(10, maxSize) : Math.max(1, Math.min(maxSize, size)));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> Page<T> select(BaseMapper<T> mapper, Page<T> page,
                                      LambdaQueryWrapper<T> filter,
                                      SFunction<T, ?> id,
                                      SFunction<T, ?>... sortColumns) {
        List<SFunction<T, ?>> columns = new ArrayList<>();
        columns.add(id);
        columns.addAll(List.of(sortColumns));
        // 不修改调用方 wrapper，避免后续导出/详情操作误用窄投影。
        LambdaQueryWrapper<T> idQuery = filter.clone().select(columns);
        Page<T> result = mapper.selectPage(page, idQuery);
        if (result.getRecords().isEmpty()) {
            return result;
        }

        List<?> ids = result.getRecords().stream().map(id).toList();
        LambdaQueryWrapper<T> detailQuery = filter.clone();
        detailQuery.select((Class<T>) LambdaUtils.extract(id).getInstantiatedClass(), TableFieldInfo::isSelect);
        detailQuery.getExpression().getOrderBy().clear();
        detailQuery.in(id, ids);
        Map<Object, T> recordsById = new HashMap<>();
        for (T record : mapper.selectList(detailQuery)) {
            recordsById.put(id.apply(record), record);
        }
        List<T> records = new ArrayList<>();
        for (Object key : ids) {
            T record = recordsById.get(key);
            // 查询间若被删除/移出授权范围，不返回它，也不补入其他页的记录。
            if (record != null) {
                records.add(record);
            }
        }
        result.setRecords(records);
        return result;
    }
}
