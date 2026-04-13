package com.ayssu.ciphergate.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.ActivityLogEntity;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.ActivityLogService;
import com.ayssu.ciphergate.service.SystemMessageService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动日志控制器
 */
@Tag(name = "活动日志管理", description = "用户活动日志的查询、统计和管理接口")
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityLogController {
    
    private final ActivityLogService activityLogService;
    private final SystemMessageService systemMessageService;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        return userMapper.selectOne(new QueryWrapper<User>().eq("github_id", githubId));
    }
    
    /**
     * 获取最近活动（分页）
     */
    @Operation(summary = "获取最近活动（分页）", description = "分页查询系统最近的活动日志记录")
    @GetMapping("/recent")
    public Result<Page<ActivityLogEntity>> getRecentActivities(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") int pageSize,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        Long userIdFilter = securityUtils.isAdmin(user.getId()) ? null : user.getId();
        Page<ActivityLogEntity> page = activityLogService.getRecentActivities(pageNum, pageSize, userIdFilter);
        return Result.success(page);
    }
    
    /**
     * 获取最近N条活动（用于首页展示）
     */
    @Operation(summary = "获取最近活动列表", description = "获取最近N条活动日志，用于首页快速展示")
    @GetMapping("/recent/list")
    public Result<List<ActivityLogEntity>> getRecentActivitiesList(
            @Parameter(description = "获取数量", example = "10") @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        Long userIdFilter = securityUtils.isAdmin(user.getId()) ? null : user.getId();
        List<ActivityLogEntity> activities = activityLogService.getRecentActivities(limit, userIdFilter);
        return Result.success(activities);
    }
    
    /**
     * 获取用户最近活动
     */
    @Operation(summary = "获取指定用户的最近活动", description = "查询指定用户的最近活动日志记录")
    @GetMapping("/user/{userId}")
    public Result<List<ActivityLogEntity>> getUserRecentActivities(
            @Parameter(description = "用户ID", example = "1") @PathVariable Long userId,
            @Parameter(description = "获取数量", example = "10") @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        User current = currentUser(authentication);
        if (current == null) {
            return Result.unauthorized("未登录");
        }
        if (!current.getId().equals(userId) && !securityUtils.isAdmin(current.getId())) {
            return Result.forbidden("无权限查看该用户活动");
        }
        List<ActivityLogEntity> activities = activityLogService.getUserRecentActivities(userId, limit);
        return Result.success(activities);
    }
    
    /**
     * 获取当前用户未读消息统计（包括活动日志和系统消息）
     */
    @Operation(summary = "获取未读消息统计", description = "获取当前登录用户的未读消息数量统计，包括活动日志和系统消息")
    @GetMapping("/unread/count")
    public Result<Map<String, Object>> getUnreadCount(Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        // 获取用户ID
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_id", githubId);
        User user = userMapper.selectOne(userQuery);
        
        if (user == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("activityTotal", 0);
            result.put("systemMessageTotal", 0);
            result.put("total", 0);
            result.put("showBadge", false);
            return Result.success(result);
        }
        
        // 获取活动日志未读统计
        Map<String, Object> activityStats = activityLogService.getUnreadCount(githubId);
        
        // 获取系统消息未读统计
        Map<String, Object> messageStats = systemMessageService.getUnreadCount(user.getId());
        
        // 合并统计
        Map<String, Object> result = new HashMap<>();
        result.put("activityTotal", activityStats.get("total"));
        result.put("activityMedium", activityStats.get("medium"));
        result.put("activityHigh", activityStats.get("high"));
        result.put("activityUrgent", activityStats.get("urgent"));
        result.put("systemMessageTotal", messageStats.get("total"));
        result.put("total", (long)activityStats.get("total") + (long)messageStats.get("total"));
        result.put("showBadge", (boolean)activityStats.get("showBadge") || (boolean)messageStats.get("showBadge"));
        
        return Result.success(result);
    }
    
    /**
     * 标记活动为已读
     */
    @Operation(summary = "标记单条活动为已读", description = "将指定的活动日志标记为已读状态")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(
            @Parameter(description = "活动日志ID", example = "1") @PathVariable Long id, 
            Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        activityLogService.markAsRead(id, githubId);
        return Result.success(null);
    }
    
    /**
     * 批量标记活动为已读
     */
    @Operation(summary = "批量标记活动为已读", description = "批量将多条活动日志标记为已读状态")
    @PutMapping("/read/batch")
    public Result<Void> markBatchAsRead(
            @Parameter(description = "活动日志ID列表") @RequestBody List<Long> ids, 
            Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        activityLogService.markBatchAsRead(ids, githubId);
        return Result.success(null);
    }
    
    /**
     * 标记所有活动为已读
     */
    @Operation(summary = "标记所有活动为已读", description = "将当前用户的所有未读活动日志标记为已读状态")
    @PutMapping("/read/all")
    public Result<Void> markAllAsRead(Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        activityLogService.markAllAsRead(githubId);
        return Result.success(null);
    }
}
