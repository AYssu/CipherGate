package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ayssu.ciphergate.entity.ActivityLogEntity;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ActivityLogMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {
    
    private final ActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    
    /**
     * 记录活动日志
     */
    public void log(Long userId, String username, String actionType, String actionTarget, 
                    String description, String ipAddress, String userAgent, String status) {
        log(userId, username, actionType, actionTarget, description, ipAddress, userAgent, status, "LOW");
    }
    
    /**
     * 记录活动日志（带重要程度）
     */
    public void log(Long userId, String username, String actionType, String actionTarget, 
                    String description, String ipAddress, String userAgent, String status, String importanceLevel) {
        ActivityLogEntity entity = new ActivityLogEntity();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setActionType(actionType);
        entity.setActionTarget(actionTarget);
        entity.setActionDescription(description);
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);
        entity.setStatus(status);
        entity.setImportanceLevel(importanceLevel);
        
        // LOW 级别自动标记为已读，其他级别默认未读
        if ("LOW".equals(importanceLevel)) {
            entity.setIsRead(true);
            entity.setReadTime(LocalDateTime.now());
        } else {
            entity.setIsRead(false);
        }
        
        entity.setCreatedTime(LocalDateTime.now());
        
        activityLogMapper.insert(entity);
        log.info("记录活动日志: {} - {} - {} - 重要度: {}", username, actionType, description, importanceLevel);
        
        // TODO: 如果是紧急级别，发送邮件通知
        if ("URGENT".equals(importanceLevel)) {
            // sendEmailNotification(userId, actionType, description);
            log.warn("紧急活动需要邮件通知: {} - {}", username, description);
        }
    }
    
    /**
     * 获取最近活动（分页）
     */
    public Page<ActivityLogEntity> getRecentActivities(int pageNum, int pageSize) {
        Page<ActivityLogEntity> page = new Page<>(pageNum, pageSize);
        QueryWrapper<ActivityLogEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_time");
        return activityLogMapper.selectPage(page, queryWrapper);
    }
    
    /**
     * 获取用户最近活动
     */
    public List<ActivityLogEntity> getUserRecentActivities(Long userId, int limit) {
        QueryWrapper<ActivityLogEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                   .orderByDesc("created_time")
                   .last("LIMIT " + limit);
        return activityLogMapper.selectList(queryWrapper);
    }
    
    /**
     * 获取最近N条活动
     */
    public List<ActivityLogEntity> getRecentActivities(int limit) {
        QueryWrapper<ActivityLogEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_time")
                   .last("LIMIT " + limit);
        return activityLogMapper.selectList(queryWrapper);
    }
    
    /**
     * 获取用户未读消息统计
     */
    public Map<String, Object> getUnreadCount(String githubId) {
        // 根据 githubId 查询用户
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_id", githubId);
        User user = userMapper.selectOne(userQuery);
        
        if (user == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("medium", 0);
            result.put("high", 0);
            result.put("urgent", 0);
            result.put("showBadge", false);
            return result;
        }
        
        // 统计未读消息总数（排除 LOW 级别）
        QueryWrapper<ActivityLogEntity> totalQuery = new QueryWrapper<>();
        totalQuery.eq("user_id", user.getId())
                 .eq("is_read", false)
                 .in("importance_level", "MEDIUM", "HIGH", "URGENT");
        long total = activityLogMapper.selectCount(totalQuery);
        
        // 统计中重要度未读消息
        QueryWrapper<ActivityLogEntity> mediumQuery = new QueryWrapper<>();
        mediumQuery.eq("user_id", user.getId())
                .eq("is_read", false)
                .eq("importance_level", "MEDIUM");
        long medium = activityLogMapper.selectCount(mediumQuery);
        
        // 统计高重要度未读消息
        QueryWrapper<ActivityLogEntity> highQuery = new QueryWrapper<>();
        highQuery.eq("user_id", user.getId())
                .eq("is_read", false)
                .eq("importance_level", "HIGH");
        long high = activityLogMapper.selectCount(highQuery);
        
        // 统计紧急未读消息
        QueryWrapper<ActivityLogEntity> urgentQuery = new QueryWrapper<>();
        urgentQuery.eq("user_id", user.getId())
                  .eq("is_read", false)
                  .eq("importance_level", "URGENT");
        long urgent = activityLogMapper.selectCount(urgentQuery);
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("medium", medium);
        result.put("high", high);
        result.put("urgent", urgent);
        result.put("showBadge", high > 0 || urgent > 0); // 只有高或紧急时显示红点
        
        return result;
    }
    
    /**
     * 标记活动为已读
     */
    public void markAsRead(Long id, String githubId) {
        ActivityLogEntity entity = activityLogMapper.selectById(id);
        if (entity != null) {
            entity.setIsRead(true);
            entity.setReadTime(LocalDateTime.now());
            activityLogMapper.updateById(entity);
            log.info("标记活动为已读: {} - {}", githubId, id);
        }
    }
    
    /**
     * 批量标记活动为已读
     */
    public void markBatchAsRead(List<Long> ids, String githubId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        for (Long id : ids) {
            markAsRead(id, githubId);
        }
        log.info("批量标记活动为已读: {} - {} 条", githubId, ids.size());
    }
    
    /**
     * 标记所有活动为已读
     */
    public void markAllAsRead(String githubId) {
        // 根据 githubId 查询用户
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_id", githubId);
        User user = userMapper.selectOne(userQuery);
        
        if (user == null) {
            return;
        }
        
        ActivityLogEntity updateEntity = new ActivityLogEntity();
        updateEntity.setIsRead(true);
        updateEntity.setReadTime(LocalDateTime.now());
        
        QueryWrapper<ActivityLogEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", user.getId())
                   .eq("is_read", false);
        
        activityLogMapper.update(updateEntity, queryWrapper);
        log.info("标记所有活动为已读: {}", githubId);
    }
}
