package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ayssu.ciphergate.dto.UserMessageDTO;
import com.ayssu.ciphergate.entity.SystemMessageEntity;
import com.ayssu.ciphergate.entity.UserMessageEntity;
import com.ayssu.ciphergate.mapper.SystemMessageMapper;
import com.ayssu.ciphergate.mapper.UserMessageMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMessageService {
    
    private final SystemMessageMapper systemMessageMapper;
    private final UserMessageMapper userMessageMapper;
    private final UserMapper userMapper;
    
    /**
     * 创建系统消息
     */
    @Transactional
    public SystemMessageEntity createMessage(String messageType, String title, String content,
                                             String importanceLevel, String targetType, Long targetId) {
        SystemMessageEntity message = new SystemMessageEntity();
        message.setMessageType(messageType);
        message.setTitle(title);
        message.setContent(content);
        message.setImportanceLevel(importanceLevel);
        message.setTargetType(targetType);
        message.setTargetId(targetId);
        message.setEmailSent(false);
        message.setCreatedTime(LocalDateTime.now());
        
        systemMessageMapper.insert(message);
        log.info("创建系统消息: {} - {} - 重要度: {}", messageType, title, importanceLevel);
        
        // 根据目标类型创建用户消息关联
        createUserMessageRelations(message);
        
        // TODO: 如果是紧急级别，发送邮件通知
        if ("URGENT".equals(importanceLevel)) {
            log.warn("紧急消息需要邮件通知: {}", title);
        }
        
        return message;
    }
    
    /**
     * 创建用户消息关联
     */
    private void createUserMessageRelations(SystemMessageEntity message) {
        List<Long> userIds;
        
        switch (message.getTargetType()) {
            case "ALL":
                // 所有用户
                userIds = userMapper.selectList(new QueryWrapper<>())
                        .stream()
                        .map(user -> user.getId())
                        .toList();
                break;
            case "USER":
                // 指定用户
                if (message.getTargetId() != null) {
                    userIds = List.of(message.getTargetId());
                } else {
                    log.warn("目标类型为 USER 但未指定 targetId");
                    return;
                }
                break;
            case "ROLE":
                // 指定角色的所有用户
                // TODO: 实现根据角色查询用户
                log.warn("暂不支持按角色发送消息");
                return;
            default:
                log.warn("未知的目标类型: {}", message.getTargetType());
                return;
        }
        
        // 为每个用户创建消息关联
        for (Long userId : userIds) {
            UserMessageEntity userMessage = new UserMessageEntity();
            userMessage.setUserId(userId);
            userMessage.setMessageId(message.getId());
            userMessage.setIsRead(false);
            userMessage.setCreatedTime(LocalDateTime.now());
            userMessageMapper.insert(userMessage);
        }
        
        log.info("为 {} 个用户创建消息关联", userIds.size());
    }
    
    /**
     * 获取系统消息列表（分页）
     */
    public Page<SystemMessageEntity> getMessages(int pageNum, int pageSize) {
        Page<SystemMessageEntity> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SystemMessageEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_time");
        return systemMessageMapper.selectPage(page, queryWrapper);
    }
    
    /**
     * 获取用户的消息列表（包含已读状态）
     */
    public List<UserMessageDTO> getUserMessages(Long userId, int limit) {
        // 查询用户消息关联
        QueryWrapper<UserMessageEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                   .orderByDesc("created_time")
                   .last("LIMIT " + limit);
        
        List<UserMessageEntity> userMessages = userMessageMapper.selectList(queryWrapper);
        
        if (userMessages.isEmpty()) {
            return List.of();
        }
        
        // 获取消息ID列表
        List<Long> messageIds = userMessages.stream()
                .map(UserMessageEntity::getMessageId)
                .toList();
        
        // 查询消息详情
        List<SystemMessageEntity> messages = systemMessageMapper.selectBatchIds(messageIds);
        
        // 创建消息ID到已读状态的映射
        Map<Long, Boolean> readStatusMap = userMessages.stream()
                .collect(Collectors.toMap(
                        UserMessageEntity::getMessageId,
                        UserMessageEntity::getIsRead
                ));
        
        // 转换为DTO并设置已读状态，按创建时间倒序排列（新消息在前）
        return messages.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedTime() == null) return 1;
                    if (b.getCreatedTime() == null) return -1;
                    return b.getCreatedTime().compareTo(a.getCreatedTime());
                })
                .map(message -> {
                    UserMessageDTO dto = new UserMessageDTO();
                    BeanUtils.copyProperties(message, dto);
                    dto.setIsRead(readStatusMap.getOrDefault(message.getId(), false));
                    return dto;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户未读系统消息统计
     */
    public Map<String, Object> getUnreadCount(Long userId) {
        QueryWrapper<UserMessageEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                   .eq("is_read", false);
        
        long total = userMessageMapper.selectCount(queryWrapper);
        
        // 统计各级别未读数
        // TODO: 需要关联查询 system_message 表获取重要程度
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("showBadge", total > 0);
        
        return result;
    }
    
    /**
     * 标记消息为已读
     */
    @Transactional
    public void markAsRead(Long messageId, Long userId) {
        QueryWrapper<UserMessageEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                   .eq("message_id", messageId);
        
        UserMessageEntity userMessage = userMessageMapper.selectOne(queryWrapper);
        if (userMessage != null && !userMessage.getIsRead()) {
            userMessage.setIsRead(true);
            userMessage.setReadTime(LocalDateTime.now());
            userMessageMapper.updateById(userMessage);
            log.info("用户 {} 标记消息 {} 为已读", userId, messageId);
        }
    }
}
