package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.FunctionPluginAppAccess;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FunctionPluginAppAccessMapper extends BaseMapper<FunctionPluginAppAccess> {

    @Select("""
            SELECT app_id
            FROM function_plugin_app_access
            WHERE plugin_id = #{pluginId}
            ORDER BY app_id
            """)
    List<Long> selectAppIdsByPluginId(@Param("pluginId") String pluginId);

    @Delete("DELETE FROM function_plugin_app_access WHERE plugin_id = #{pluginId}")
    int deleteByPluginId(@Param("pluginId") String pluginId);
}
