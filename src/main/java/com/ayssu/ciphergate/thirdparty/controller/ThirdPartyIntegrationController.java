package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyIntegrationRequest;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyIntegrationService;
import com.ayssu.ciphergate.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/third_party")
public class ThirdPartyIntegrationController {
    private final ThirdPartyIntegrationService service;
    @PostMapping("/trial")
    @Operation(summary="凭证申请试用",description="管理员开通权限；独立凭证HMAC-SHA256签名和IP白名单，不需要用户密码和图形验证码")
    public ResponseEntity<Result<?>> trial(@RequestBody(required=false) ThirdPartyIntegrationRequest request,HttpServletRequest http) {
        return execute(ThirdPartyIntegrationService.Action.TRIAL_APPLY,request,http);
    }
    @PostMapping("/authorization")
    @Operation(summary="凭证查询授权",description="查询已授权应用的终端用户会员到期时间，不需要用户密码和图形验证码")
    public ResponseEntity<Result<?>> authorization(@RequestBody(required=false) ThirdPartyIntegrationRequest request,HttpServletRequest http) {
        return execute(ThirdPartyIntegrationService.Action.AUTH_QUERY,request,http);
    }
    private ResponseEntity<Result<?>> execute(ThirdPartyIntegrationService.Action action,ThirdPartyIntegrationRequest request,HttpServletRequest http) {
        Result<?> result=service.execute(action,request,IpUtil.getIpAddr(http),http.getHeader("User-Agent"));
        return ResponseEntity.status(result.getCode()).body(result);
    }
}
