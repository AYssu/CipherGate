package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.FunctionPluginModule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FunctionPluginModuleMapper extends BaseMapper<FunctionPluginModule> {

    @Update("""
            UPDATE function_plugin_module
            SET deleted = 1,
                deleted_at = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int softDeleteWithTimestamp(@Param("id") Long id);
}
