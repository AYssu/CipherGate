package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Menu;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.MenuService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {
    
    private final MenuService menuService;
    
    /**
     * 获取当前用户的菜单树
     */
    @GetMapping("/user")
    public Result<List<Menu>> getUserMenus(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error("用户未登录");
        }
        
        List<Menu> menuTree = menuService.getUserMenuTree(user.getId());
        return Result.success(menuTree);
    }
    
    /**
     * 获取所有菜单树（管理员用）
     */
    @GetMapping("/all")
    @RequirePermission("MENU_LIST")
    public Result<List<Menu>> getAllMenus() {
        List<Menu> menuTree = menuService.getAllMenuTree();
        return Result.success(menuTree);
    }
    
    /**
     * 根据ID获取菜单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("MENU_LIST")
    public Result<Menu> getMenuById(@PathVariable Long id) {
        Menu menu = menuService.getById(id);
        if (menu == null) {
            return Result.error("菜单不存在");
        }
        return Result.success(menu);
    }
    
    /**
     * 获取父菜单选项列表
     */
    @GetMapping("/parent-options")
    @RequirePermission("MENU_LIST")
    public Result<List<Menu>> getParentMenuOptions() {
        List<Menu> parentOptions = menuService.getParentMenuOptions();
        return Result.success(parentOptions);
    }
    
    /**
     * 创建菜单
     */
    @PostMapping
    @RequirePermission("MENU_CREATE")
    public Result<String> createMenu(@RequestBody Menu menu) {
        try {
            // 验证菜单编码唯一性
            if (menuService.getMenuByCode(menu.getMenuCode()) != null) {
                return Result.error("菜单编码已存在");
            }
            
            boolean success = menuService.createMenu(menu);
            if (success) {
                return Result.success("菜单创建成功");
            } else {
                return Result.error("菜单创建失败");
            }
        } catch (Exception e) {
            log.error("创建菜单失败", e);
            return Result.error("菜单创建失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新菜单
     */
    @PutMapping("/{id}")
    @RequirePermission("MENU_UPDATE")
    public Result<String> updateMenu(@PathVariable Long id, @RequestBody Menu menu) {
        try {
            menu.setId(id);
            
            // 验证菜单编码唯一性（排除自己）
            Menu existingMenu = menuService.getMenuByCode(menu.getMenuCode());
            if (existingMenu != null && !existingMenu.getId().equals(id)) {
                return Result.error("菜单编码已存在");
            }
            
            boolean success = menuService.updateMenu(menu);
            if (success) {
                return Result.success("菜单更新成功");
            } else {
                return Result.error("菜单更新失败");
            }
        } catch (Exception e) {
            log.error("更新菜单失败", e);
            return Result.error("菜单更新失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除菜单
     */
    @DeleteMapping("/{id}")
    @RequirePermission("MENU_DELETE")
    public Result<String> deleteMenu(@PathVariable Long id) {
        try {
            // 检查是否有子菜单
            if (menuService.hasChildren(id)) {
                return Result.error("该菜单下有子菜单，无法删除");
            }
            
            boolean success = menuService.deleteMenu(id);
            if (success) {
                return Result.success("菜单删除成功");
            } else {
                return Result.error("菜单删除失败");
            }
        } catch (Exception e) {
            log.error("删除菜单失败", e);
            return Result.error("菜单删除失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量删除菜单
     */
    @DeleteMapping("/batch")
    @RequirePermission("MENU_DELETE")
    public Result<String> batchDeleteMenus(@RequestBody List<Long> ids) {
        try {
            boolean success = menuService.batchDeleteMenus(ids);
            if (success) {
                return Result.success("批量删除成功");
            } else {
                return Result.error("批量删除失败");
            }
        } catch (Exception e) {
            log.error("批量删除菜单失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }
}