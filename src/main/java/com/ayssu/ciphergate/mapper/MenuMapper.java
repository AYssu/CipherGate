package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayssu.ciphergate.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单Mapper接口
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 根据用户ID查询用户可访问的菜单（包含父子关系）
     */
    @Select("SELECT DISTINCT m.* FROM menus m " +
            "LEFT JOIN role_menus rm ON m.id = rm.menu_id " +
            "LEFT JOIN user_roles ur ON rm.role_id = ur.role_id " +
            "WHERE m.status = 1 AND m.visible = 1 AND ur.user_id = #{userId} " +
            "ORDER BY m.parent_id ASC, m.sort_order ASC")
    List<Menu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查询菜单
     */
    @Select("SELECT m.* FROM menus m " +
            "INNER JOIN role_menus rm ON m.id = rm.menu_id " +
            "WHERE rm.role_id = #{roleId} AND m.status = 1 " +
            "ORDER BY m.sort_order ASC")
    List<Menu> selectMenusByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据父菜单ID查询子菜单
     */
    @Select("SELECT * FROM menus WHERE parent_id = #{parentId} AND status = 1 AND visible = 1 ORDER BY sort_order ASC")
    List<Menu> selectMenusByParentId(@Param("parentId") Long parentId);
}