package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.AppAgentDTO;
import com.ayssu.ciphergate.dto.AgentBindUserDTO;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.AppAgentService;
import com.ayssu.ciphergate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applications/{appId}/agents")
@Tag(name = "应用代理", description = "应用代理配置管理")
public class AppAgentController {
    private final AppAgentService appAgentService;
    private final UserService userService;

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        User user = userService.getUserByGithubId(githubId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user.getId();
    }

    @GetMapping
    @RequirePermission("APP_DETAIL")
    @Operation(summary = "代理列表")
    public Result<List<AppAgentDTO>> list(@PathVariable Long appId) {
        return Result.success(appAgentService.listByAppId(appId, currentUserId()));
    }

    @PostMapping
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "创建代理")
    public Result<AppAgentDTO> create(@PathVariable Long appId, @RequestBody AppAgentDTO dto) {
        return Result.success(appAgentService.create(appId, dto, currentUserId()));
    }

    @PutMapping("/{agentId}")
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "更新代理")
    public Result<AppAgentDTO> update(@PathVariable Long appId, @PathVariable Long agentId, @RequestBody AppAgentDTO dto) {
        return Result.success(appAgentService.update(appId, agentId, dto, currentUserId()));
    }

    @PutMapping("/{agentId}/permissions")
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "更新代理权限")
    public Result<String> updatePermissions(@PathVariable Long appId, @PathVariable Long agentId, @RequestBody List<String> permissions) {
        appAgentService.updatePermissions(appId, agentId, permissions, currentUserId());
        return Result.success("OK");
    }

    @PutMapping("/{agentId}/quotas")
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "更新代理额度")
    public Result<String> updateQuotas(@PathVariable Long appId, @PathVariable Long agentId, @RequestBody Map<String, Long> quotas) {
        appAgentService.updateQuotas(appId, agentId, quotas, currentUserId());
        return Result.success("OK");
    }

    @GetMapping("/lookup-user")
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "按 GitHub ID 查询可绑定用户")
    public Result<AgentBindUserDTO> lookupUser(@PathVariable Long appId, @RequestParam String githubId) {
        AgentBindUserDTO user = appAgentService.findBindUserByGithubId(appId, githubId, currentUserId());
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }
}

