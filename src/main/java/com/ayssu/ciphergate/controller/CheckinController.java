package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.CheckinRecord;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.service.CheckinService;
import com.ayssu.ciphergate.service.UserMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/checkin")
@RequiredArgsConstructor
@Tag(name = "每日签到", description = "用户签到获取奖励")
public class CheckinController {

    private final CheckinService checkinService;
    private final UserMembershipService userMembershipService;

    @PostMapping
    @Operation(summary = "签到")
    @ActivityLog(actionType = "CREATE", actionTarget = "CHECKIN", description = "每日签到")
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

        // 获取会员信息（连续签到天数、累计签到天数）
        UserMembership membership = userMembershipService.getByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("checkedIn", checkedIn);
        result.put("todayRecord", todayRecord);
        result.put("consecutiveDays", membership != null ? membership.getConsecutiveCheckinDays() : 0);
        result.put("totalDays", membership != null ? membership.getTotalCheckinDays() : 0);

        return Result.success(result);
    }
}
