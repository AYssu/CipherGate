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
    @RequirePermission("MENU_MANAGEMENT")
    public Result<List<Menu>> getAllMenus() {
        List<Menu> menuTree = menuService.getAllMenuTree();
        return Result.success(menuTree);
    }
    
    /**
     * 根据ID获取菜单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("MENU_MANAGEMENT")
    public Result<Menu> getMenuById(@PathVariable Long id) {
        Menu menu = menuService.getById(id);
        if (menu == null) {
            return Result.error("菜单不存在");
        }
        return Result.success(menu);
    }
    
    /**
     * 创建菜单
     */
    @PostMapping
    @RequirePermission("MENU_MANAGEMENT")
    public Result<String> createMenu(@RequestBody Menu menu) {
        boolean success = menuService.save(menu);
        if (success) {
            return Result.success("菜单创建成功");
        } else {
            return Result.error("菜单创建失败");
        }
    }
    
    /**
     * 更新菜单
     */
    @PutMapping("/{id}")
    @RequirePermission("MENU_MANAGEMENT")
    public Result<String> updateMenu(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);
        boolean success = menuService.updateById(menu);
        if (success) {
            return Result.success("菜单更新成功");
        } else {
            return Result.error("菜单更新失败");
        }
    }
    
    /**
     * 删除菜单
     */
    @DeleteMapping("/{id}")
    @RequirePermission("MENU_MANAGEMENT")
    public Result<String> deleteMenu(@PathVariable Long id) {
        boolean success = menuService.removeById(id);
        if (success) {
            return Result.success("菜单删除成功");
        } else {
            return Result.error("菜单删除失败");
        }
    }
}