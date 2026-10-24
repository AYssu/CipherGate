package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryRequest;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryResponse;
import com.ayssu.ciphergate.service.PublicAppUserSelfService;
import com.ayssu.ciphergate.service.AppUserLicenseRechargeService;
import com.ayssu.ciphergate.dto.AppUserLicenseRechargeRequest;
import com.ayssu.ciphergate.dto.AppUserLicenseRechargeResponse;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUserRateLimitService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUserLoginException;
import com.ayssu.ciphergate.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
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
    private final AppUserLicenseRechargeService rechargeService;
    private final ThirdPartyUserRateLimitService rateLimiter;

    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> invalidRequest() {
        // Validation exceptions include rejected values: do not send them to generic error logging.
        return ResponseEntity.badRequest().body(Result.badRequest("请检查应用ID、邮箱和卡密格式"));
    }

    @PostMapping("/redeem-license")
    @Operation(summary = "兑换未使用时长卡，为同应用终端用户充值会员")
    public Result<AppUserLicenseRechargeResponse> redeemLicense(
            @Valid @RequestBody AppUserLicenseRechargeRequest body, HttpServletRequest http) {
        try {
            String ip = IpUtil.getIpAddr(http);
            rateLimiter.checkBeforeRedemption(body.getAppId(), ip, body.getEmail());
            return Result.success("充值成功，卡密已兑换", rechargeService.redeem(body, ip));
        } catch (ThirdPartyUserLoginException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Result.badRequest("卡密已兑换，请勿重复充值");
        } catch (Exception e) {
            // Never log the card, email, SQL parameters, or exception message.
            log.error("License redemption failed: errorType={}", e.getClass().getSimpleName());
            return Result.error("充值服务暂不可用，请稍后重试");
        }
    }

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
