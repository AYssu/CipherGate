package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/invite")
@RequiredArgsConstructor
@Tag(name = "邀请有奖", description = "邀请好友获取奖励")
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/code")
    @Operation(summary = "获取邀请码")
    public Result<String> getInviteCode(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(inviteService.getInviteCode(user.getId()));
    }

    @GetMapping("/records")
    @Operation(summary = "邀请记录列表")
    public Result<?> getInviteRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(inviteService.getInviteRecords(user.getId(), page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "邀请统计")
    public Result<Map<String, Object>> getInviteStats(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(inviteService.getInviteStats(user.getId()));
    }
}
