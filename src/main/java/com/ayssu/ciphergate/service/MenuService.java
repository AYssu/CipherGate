package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ayssu.ciphergate.entity.Menu;

import java.util.List;

/**
 * 菜单服务接口
 */
public interface MenuService extends IService<Menu> {
    
    /**
     * 根据用户ID获取用户可访问的菜单树
     */
    List<Menu> getUserMenuTree(Long userId);
    
    /**
     * 根据角色ID获取菜单列表
     */
    List<Menu> getMenusByRoleId(Long roleId);
    
    /**
     * 获取所有菜单树
     */
    List<Menu> getAllMenuTree();
    
    /**
     * 构建菜单树
     */
    List<Menu> buildMenuTree(List<Menu> menus);
    
    /**
     * 根据菜单编码获取菜单
     */
    Menu getMenuByCode(String menuCode);
}