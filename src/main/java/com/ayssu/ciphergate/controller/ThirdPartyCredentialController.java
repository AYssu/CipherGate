package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialDTO;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialQueryDTO;
import com.ayssu.ciphergate.dto.ThirdPartyRechargeLogQueryDTO;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.entity.ThirdPartyRechargeLog;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.ThirdPartyCredentialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/third-party")
@Tag(name = "三方凭证管理", description = "三方凭证与调用日志管理")
public class ThirdPartyCredentialController {

    private final ThirdPartyCredentialService credentialService;
    private final UserMapper userMapper;

    @GetMapping("/credentials")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_LIST")
    @Operation(summary = "凭证分页")
    public Result<Page<ThirdPartyCredential>> pageCredentials(ThirdPartyCredentialQueryDTO queryDTO) {
        try {
            return Result.success(credentialService.pageCredentials(queryDTO, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/credentials/{id}")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_DETAIL")
    @Operation(summary = "凭证详情")
    public Result<ThirdPartyCredential> getCredential(@PathVariable Long id) {
        try {
            return Result.success(credentialService.getCredential(id, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/credentials")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "THIRD_PARTY_CREDENTIAL", description = "创建三方凭证")
    @Operation(summary = "创建凭证")
    public Result<ThirdPartyCredential> createCredential(@RequestBody ThirdPartyCredentialDTO dto) {
        try {
            return Result.success("创建成功", credentialService.createCredential(dto, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/credentials/{id}")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "THIRD_PARTY_CREDENTIAL", description = "更新三方凭证")
    @Operation(summary = "更新凭证")
    public Result<ThirdPartyCredential> updateCredential(@PathVariable Long id, @RequestBody ThirdPartyCredentialDTO dto) {
        try {
            return Result.success("更新成功", credentialService.updateCredential(id, dto, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/credentials/{id}/rotate-secret")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "THIRD_PARTY_CREDENTIAL", description = "重置三方凭证Secret")
    @Operation(summary = "重置凭证Secret")
    public Result<ThirdPartyCredential> rotateSecret(@PathVariable Long id) {
        try {
            return Result.success("重置成功", credentialService.rotateSecret(id, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("重置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/credentials/{id}")
    @RequirePermission("THIRD_PARTY_CREDENTIAL_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "THIRD_PARTY_CREDENTIAL", description = "删除三方凭证")
    @Operation(summary = "删除凭证")
    public Result<Void> deleteCredential(@PathVariable Long id) {
        try {
            credentialService.deleteCredential(id, getCurrentUser().getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/recharge-logs")
    @RequirePermission("THIRD_PARTY_CALL_LOG_LIST")
    @Operation(summary = "调用日志分页")
    public Result<Page<ThirdPartyRechargeLog>> pageLogs(ThirdPartyRechargeLogQueryDTO queryDTO) {
        try {
            return Result.success(credentialService.pageRechargeLogs(queryDTO, getCurrentUser().getId()));
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2User oauth2User)) {
            throw new RuntimeException("无效认证信息");
        }
        Object idObj = oauth2User.getAttribute("id");
        String githubId = idObj == null ? null : idObj.toString();
        if (githubId == null) {
            throw new RuntimeException("无法获取GitHub用户ID");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getGithubId, githubId));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
}
