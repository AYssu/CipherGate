package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayssu.ciphergate.entity.Role;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色Mapper接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    
    /**
     * 根据用户ID查询角色列表
     */
    @Select("SELECT r.* FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);
    
    /**
     * 根据角色编码查询角色
     */
    @Select("SELECT * FROM roles WHERE role_code = #{roleCode} AND status = 1")
    Role selectByRoleCode(@Param("roleCode") String roleCode);
    
    /**
     * 根据角色ID查询菜单ID列表
     */
    @Select("SELECT menu_id FROM role_menus WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
    
    /**
     * 删除角色的菜单权限
     */
    @Delete("DELETE FROM role_menus WHERE role_id = #{roleId}")
    void deleteRoleMenus(@Param("roleId") Long roleId);
    
    /**
     * 批量插入角色菜单权限
     */
    @Insert("<script>" +
            "INSERT INTO role_menus (role_id, menu_id) VALUES " +
            "<foreach collection='menuIds' item='menuId' separator=','>" +
            "(#{roleId}, #{menuId})" +
            "</foreach>" +
            "</script>")
    void insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
    
    /**
     * 根据角色ID查询权限ID列表
     */
    @Select("SELECT permission_id FROM role_permissions WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
    
    /**
     * 删除角色的权限
     */
    @Delete("DELETE FROM role_permissions WHERE role_id = #{roleId}")
    void deleteRolePermissions(@Param("roleId") Long roleId);
    
    /**
     * 批量插入角色权限
     */
    @Insert("<script>" +
            "INSERT INTO role_permissions (role_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>" +
            "(#{roleId}, #{permissionId})" +
            "</foreach>" +
            "</script>")
    void insertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}