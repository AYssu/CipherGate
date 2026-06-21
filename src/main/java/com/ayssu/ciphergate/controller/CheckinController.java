package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.CheckinRecord;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/checkin")
@RequiredArgsConstructor
@Tag(name = "每日签到", description = "用户签到获取奖励")
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping
    @Operation(summary = "签到")
    public Result<Map<String, Object>> doCheckin(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        try {
            Map<String, Object> result = checkinService.doCheckin(user.getId());
            return Result.success("签到成功", result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "今日签到状态")
    public Result<Map<String, Object>> getCheckinStatus(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        boolean checkedIn = checkinService.hasCheckedInToday(user.getId());
        CheckinRecord todayRecord = checkinService.getTodayRecord(user.getId());
        Map<String, Object> result = Map.of(
                "checkedIn", checkedIn,
                "todayRecord", todayRecord != null ? todayRecord : Map.of()
        );
        return Result.success(result);
    }
}
