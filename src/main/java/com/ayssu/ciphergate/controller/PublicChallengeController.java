package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.service.PublicChallengeService;
import com.ayssu.ciphergate.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/challenge")
public class PublicChallengeController {
    private final PublicChallengeService challenges;
    @GetMapping
    public Result<Map<String, String>> issue(@RequestParam String purpose, HttpServletRequest http, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        return Result.success(challenges.issue(purpose, IpUtil.getIpAddr(http)));
    }
}
