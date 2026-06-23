package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.CheckinRecord;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.CheckinRecordMapper;
import com.ayssu.ciphergate.service.CheckinService;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl extends ServiceImpl<CheckinRecordMapper, CheckinRecord> implements CheckinService {

    @Autowired
    private CheckinRecordMapper checkinRecordMapper;

    @Autowired
    private UserMembershipService userMembershipService;

    @Override
    @Transactional
    public Map<String, Object> doCheckin(Long userId) {
        LocalDate today = LocalDate.now();

        if (hasCheckedInToday(userId)) {
            throw new RuntimeException("今日已签到");
        }

        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            userMembershipService.initMembershipForUser(userId);
            membership = userMembershipService.getByUserId(userId);
        }

        int consecutiveDays = 1;
        if (membership.getLastCheckinDate() != null) {
            if (membership.getLastCheckinDate().equals(today.minusDays(1))) {
                consecutiveDays = membership.getConsecutiveCheckinDays() + 1;
            }
        }

        // 随机选择一种奖励类型
        String rewardType = pickRandomRewardType();
        Map<String, Object> reward = calculateReward(rewardType, consecutiveDays);

        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setCheckinDate(today);
        record.setConsecutiveDays(consecutiveDays);

        // 根据奖励类型设置记录和额度
        switch (rewardType) {
            case "LICENSE":
                int licenseAmount = (int) reward.get("amount");
                record.setLicenseReward(licenseAmount);
                record.setUserRegisterReward(0);
                record.setTrafficReward(0L);
                membership.setExtraLicenseQuota((membership.getExtraLicenseQuota() != null ? membership.getExtraLicenseQuota() : 0) + licenseAmount);
                break;
            case "USER_REGISTER":
                int userAmount = (int) reward.get("amount");
                record.setLicenseReward(0);
                record.setUserRegisterReward(userAmount);
                record.setTrafficReward(0L);
                membership.setExtraUserRegisterQuota((membership.getExtraUserRegisterQuota() != null ? membership.getExtraUserRegisterQuota() : 0) + userAmount);
                break;
            case "TRAFFIC":
                long trafficBytes = (long) reward.get("amount");
                record.setLicenseReward(0);
                record.setUserRegisterReward(0);
                record.setTrafficReward(trafficBytes);
                membership.setExtraTrafficQuota((membership.getExtraTrafficQuota() != null ? membership.getExtraTrafficQuota() : 0) + trafficBytes);
                break;
        }

        save(record);

        membership.setLastCheckinDate(today);
        membership.setConsecutiveCheckinDays(consecutiveDays);
        membership.setTotalCheckinDays(membership.getTotalCheckinDays() + 1);
        userMembershipService.updateById(membership);

        Map<String, Object> result = new HashMap<>();
        result.put("rewardType", rewardType);
        result.put("reward", reward);
        result.put("consecutiveDays", consecutiveDays);
        result.put("totalDays", membership.getTotalCheckinDays());

        log.info("用户[{}]签到成功：连续{}天，奖励类型：{}，奖励：{}",
                userId, consecutiveDays, rewardType, reward);

        return result;
    }

    @Override
    public boolean hasCheckedInToday(Long userId) {
        Long count = lambdaQuery()
                .eq(CheckinRecord::getUserId, userId)
                .eq(CheckinRecord::getCheckinDate, LocalDate.now())
                .count();
        return count > 0;
    }

    @Override
    public CheckinRecord getTodayRecord(Long userId) {
        return lambdaQuery()
                .eq(CheckinRecord::getUserId, userId)
                .eq(CheckinRecord::getCheckinDate, LocalDate.now())
                .one();
    }

    private String pickRandomRewardType() {
        int rand = ThreadLocalRandom.current().nextInt(3);
        switch (rand) {
            case 0: return "LICENSE";
            case 1: return "USER_REGISTER";
            default: return "TRAFFIC";
        }
    }

    private Map<String, Object> calculateReward(String rewardType, int consecutiveDays) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", rewardType);

        switch (rewardType) {
            case "LICENSE":
                // 卡密额度: 基础 3-15, 最高 55
                int licenseBase = ThreadLocalRandom.current().nextInt(3, 16);
                double licenseMultiplier = 1.0 + (consecutiveDays - 1) * 0.15;
                int licenseAmount = Math.min(55, (int) (licenseBase * licenseMultiplier));
                result.put("amount", licenseAmount);
                result.put("unit", "张");
                result.put("name", "卡密额度");
                break;

            case "USER_REGISTER":
                // 用户额度: 基础 1-3, 最高 10
                int userBase = ThreadLocalRandom.current().nextInt(1, 4);
                double userMultiplier = 1.0 + (consecutiveDays - 1) * 0.15;
                int userAmount = Math.min(10, (int) (userBase * userMultiplier));
                result.put("amount", userAmount);
                result.put("unit", "个");
                result.put("name", "用户额度");
                break;

            case "TRAFFIC":
                // 流量: 基础 5-15 MB, 最高 100 MB
                long trafficBaseMB = ThreadLocalRandom.current().nextLong(5, 16);
                double trafficMultiplier = 1.0 + (consecutiveDays - 1) * 0.15;
                long trafficMB = Math.min(100, (long) (trafficBaseMB * trafficMultiplier));
                long trafficBytes = trafficMB * 1024 * 1024;
                result.put("amount", trafficBytes);
                result.put("amountMB", trafficMB);
                result.put("unit", "MB");
                result.put("name", "流量额度");
                break;
        }

        return result;
    }
}
