package com.ayssu.ciphergate.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.CreateMessageRequest;
import com.ayssu.ciphergate.dto.UserMessageDTO;
import com.ayssu.ciphergate.entity.SystemMessageEntity;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.SystemMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统消息控制器
 */
@Tag(name = "系统消息管理", description = "系统消息的创建、查询和管理接口")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class SystemMessageController {
    
    private final SystemMessageService systemMessageService;
    private final UserMapper userMapper;
    
    /**
     * 创建系统消息
     */
    @Operation(summary = "创建系统消息", description = "创建一条系统消息，可以发送给所有用户、指定用户或指定角色")
    @PostMapping
    @RequirePermission("MESSAGE_CREATE")
    public Result<SystemMessageEntity> createMessage(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "消息创建请求",
                required = true,
                content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreateMessageRequest.class)
                )
            )
            @RequestBody CreateMessageRequest request) {
        SystemMessageEntity message = systemMessageService.createMessage(
                request.getMessageType(),
                request.getTitle(),
                request.getContent(),
                request.getImportanceLevel(),
                request.getTargetType(),
                request.getTargetId());
        
        return Result.success(message);
    }
    
    /**
     * 获取系统消息列表（分页）
     */
    @Operation(summary = "获取系统消息列表", description = "分页查询系统消息列表")
    @GetMapping
    @RequirePermission("MESSAGE_LIST")
    public Result<Page<SystemMessageEntity>> getMessages(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") int pageSize) {
        Page<SystemMessageEntity> page = systemMessageService.getMessages(pageNum, pageSize);
        return Result.success(page);
    }
    
    /**
     * 获取当前用户的系统消息
     */
    @Operation(summary = "获取用户系统消息", description = "获取当前登录用户的系统消息列表")
    @GetMapping("/my")
    public Result<List<UserMessageDTO>> getMyMessages(
            @Parameter(description = "获取数量", example = "10") @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        // 获取用户ID
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_id", githubId);
        User user = userMapper.selectOne(userQuery);
        
        if (user == null) {
            return Result.success(List.of());
        }
        
        List<UserMessageDTO> messages = systemMessageService.getUserMessages(user.getId(), limit);
        return Result.success(messages);
    }
    
    /**
     * 标记系统消息为已读
     */
    @Operation(summary = "标记系统消息为已读", description = "将指定的系统消息标记为已读状态")
    @PutMapping("/{id}/read")
    public Result<Void> markMessageAsRead(
            @Parameter(description = "消息ID", example = "1") @PathVariable Long id,
            Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        
        // 获取用户ID
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_id", githubId);
        User user = userMapper.selectOne(userQuery);
        
        if (user != null) {
            systemMessageService.markAsRead(id, user.getId());
        }
        
        return Result.success(null);
    }
}
