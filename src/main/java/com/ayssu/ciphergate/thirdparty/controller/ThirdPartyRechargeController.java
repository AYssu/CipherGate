package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyRechargeDTO;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyRechargeService;
import com.ayssu.ciphergate.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方加时", description = "第三方凭证调用终端用户加时接口")
public class ThirdPartyRechargeController {

    private final ThirdPartyRechargeService rechargeService;

    @PostMapping("/third_party/recharge")
    @Operation(summary = "三方凭证加时")
    public Result<?> recharge(@Valid @RequestBody ThirdPartyRechargeDTO dto, HttpServletRequest request) {
        String ip = IpUtil.getIpAddr(request);
        String ua = request.getHeader("User-Agent");
        String apiKeyFingerprint = dto.getApiKey() == null || dto.getApiKey().isBlank()
                ? "missing"
                : cn.hutool.crypto.digest.DigestUtil.sha256Hex(dto.getApiKey().trim())
                        .substring(0, 12);
        log.info("third party recharge request: apiKeyFingerprint={}, email={}, days={}, ip={}",
                apiKeyFingerprint, dto.getUserEmail(), dto.getDays(), ip);
        return rechargeService.recharge(dto, ip, ua);
    }
}
