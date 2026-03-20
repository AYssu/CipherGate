package com.ayssu.ciphergate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayssu.ciphergate.entity.Menu;
import com.ayssu.ciphergate.mapper.MenuMapper;
import com.ayssu.ciphergate.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {
    
    private final MenuMapper menuMapper;
    
    @Override
    public List<Menu> getUserMenuTree(Long userId) {
        log.info("开始获取用户 {} 的菜单树", userId);
        List<Menu> userMenus = menuMapper.selectMenusByUserId(userId);
        log.info("查询到 {} 个菜单项", userMenus.size());
        
        for (Menu menu : userMenus) {
            log.info("菜单: {} (ID: {}, 父ID: {}, 类型: {})", 
                menu.getMenuName(), menu.getId(), menu.getParentId(), menu.getMenuType());
        }
        
        List<Menu> menuTree = buildMenuTree(userMenus);
        log.info("构建菜单树完成，根菜单数量: {}", menuTree.size());
        
        return menuTree;
    }
    
    @Override
    public List<Menu> getMenusByRoleId(Long roleId) {
        return menuMapper.selectMenusByRoleId(roleId);
    }
    
    @Override
    public List<Menu> getAllMenuTree() {
        QueryWrapper<Menu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        queryWrapper.orderByAsc("sort_order");
        List<Menu> allMenus = list(queryWrapper);
        return buildMenuTree(allMenus);
    }
    
    @Override
    public List<Menu> buildMenuTree(List<Menu> menus) {
        List<Menu> rootMenus = new ArrayList<>();
        
        log.info("开始构建菜单树，总菜单数: {}", menus.size());
        
        // 找出所有根菜单（parent_id = 0）
        List<Menu> roots = menus.stream()
                .filter(menu -> menu.getParentId() == 0)
                .collect(Collectors.toList());
        
        log.info("找到 {} 个根菜单", roots.size());
        
        // 为每个根菜单构建子菜单树
        for (Menu root : roots) {
            log.info("为根菜单 {} (ID: {}) 构建子菜单", root.getMenuName(), root.getId());
            List<Menu> children = getChildren(root.getId(), menus);
            root.setChildren(children);
            log.info("根菜单 {} 有 {} 个子菜单", root.getMenuName(), children.size());
            rootMenus.add(root);
        }
        
        return rootMenus;
    }
    
    /**
     * 递归获取子菜单
     */
    private List<Menu> getChildren(Long parentId, List<Menu> allMenus) {
        List<Menu> children = new ArrayList<>();
        
        log.debug("查找父菜单ID为 {} 的子菜单", parentId);
        
        for (Menu menu : allMenus) {
            if (parentId.equals(menu.getParentId())) {
                log.debug("找到子菜单: {} (ID: {}, 父ID: {})", menu.getMenuName(), menu.getId(), menu.getParentId());
                menu.setChildren(getChildren(menu.getId(), allMenus));
                children.add(menu);
            }
        }
        
        log.debug("父菜单ID {} 共有 {} 个子菜单", parentId, children.size());
        return children;
    }
    
    @Override
    public Menu getMenuByCode(String menuCode) {
        QueryWrapper<Menu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("menu_code", menuCode);
        return getOne(queryWrapper);
    }
}