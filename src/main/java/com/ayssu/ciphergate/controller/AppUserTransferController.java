package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.transfer.*;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferResult.*;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppUserTransferService;
import com.ayssu.ciphergate.util.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/app-user-transfers")
@RequirePermission(value = "APP_USER_TRANSFER", description = "终端用户迁移/复制")
@Tag(name = "终端用户迁移", description = "跨应用迁移/复制，必须先预检查；仅应用所有者或管理员")
@RequiredArgsConstructor
public class AppUserTransferController {
    private final AppUserTransferService service;
    private final UserMapper users;

    @GetMapping("/applications")
    @Operation(summary = "分页获取可迁移的自有应用；管理员可查看全部")
    public Result<Page<ApplicationOption>> applications(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "50") long size,
                                                        @RequestParam(required = false) String keyword) {
        return call(() -> service.applicationOptions(operatorId(), current, size, keyword));
    }

    @GetMapping("/users")
    @Operation(summary = "源应用可选用户（不包含密码或密钥）")
    public Result<Page<UserRow>> users(@RequestParam Long appId, @RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String keyword,
                                      @ModelAttribute com.ayssu.ciphergate.dto.transfer.AppUserTransferUserQuery filters) {
        return call(() -> service.sourceUsers(appId, operatorId(), current, size, keyword, filters));
    }

    @PostMapping("/preview")
    @Operation(summary = "预检查迁移/复制；不会修改任何用户")
    public Result<AppUserTransferResult> preview(@Valid @RequestBody AppUserTransferRequest request) {
        return call(() -> service.preview(request, operatorId()));
    }

    @PostMapping("/execute")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER_TRANSFER", description = "跨应用迁移/复制终端用户（含冲突预检查）")
    @Operation(summary = "执行已预检查的批次；跳过普通冲突，其余原子提交或回滚")
    public Result<AppUserTransferResult> execute(@Valid @RequestBody AppUserTransferRequest request) {
        return call(() -> service.execute(request, operatorId()));
    }

    @ExceptionHandler(TransferValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> validationError(TransferValidationException e) { return Result.badRequest(e.getMessage()); }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidBody() { return Result.badRequest("请选择有效的应用、操作类型及 1–100 个不同的用户"); }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.validation.BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> malformedRequest() { return Result.badRequest("请求格式或参数无效"); }

    private static class TransferValidationException extends RuntimeException {
        TransferValidationException(String message) { super(message); }
    }

    private Long operatorId() {
        User user = AuthUtils.getCurrentUser();
        if (user == null && AuthUtils.getAuthentication() != null
                && AuthUtils.getAuthentication().getPrincipal() instanceof OAuth2User oauth) {
            Object githubId = oauth.getAttribute("id");
            if (githubId != null) user = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getGithubId, githubId.toString()));
        }
        if (user == null) throw new SecurityException("未登录");
        return user.getId();
    }

    /** 权限异常不吞成业务 200，数据库内部异常不暴露 SQL 或账号凭证。 */
    private <T> Result<T> call(java.util.function.Supplier<T> action) {
        try { return Result.success(action.get()); }
        catch (IllegalArgumentException e) { throw new TransferValidationException(e.getMessage()); }
    }
}
