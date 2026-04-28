package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryRequest;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryResponse;
import com.ayssu.ciphergate.service.PublicAppUserSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 终端用户自助查询（公开接口，无需登录）。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/app-user/self")
@RequiredArgsConstructor
@Validated
@Tag(name = "公开-终端用户自助", description = "终端用户邮箱查询会员到期时间")
public class PublicAppUserSelfController {

    private final PublicAppUserSelfService publicAppUserSelfService;

    @PostMapping("/query-expire")
    @Operation(summary = "按邮箱查询会员到期时间")
    public Result<PublicAppUserExpireQueryResponse> queryExpire(@Valid @RequestBody PublicAppUserExpireQueryRequest body) {
        try {
            return Result.success(publicAppUserSelfService.queryExpire(body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("公开终端用户到期查询异常", e);
            return Result.error("查询失败，请稍后重试");
        }
    }
}
