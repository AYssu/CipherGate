package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.PluginModule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PluginModuleMapper extends BaseMapper<PluginModule> {
    @Update("""
            UPDATE plugin_module
            SET deleted = 1,
                deleted_at = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int softDeleteWithTimestamp(@Param("id") Long id);
}
