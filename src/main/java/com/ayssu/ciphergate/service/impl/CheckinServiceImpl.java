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

        int licenseReward = calculateLicenseReward(consecutiveDays);
        int userRegisterReward = calculateUserRegisterReward(consecutiveDays);
        long trafficReward = calculateTrafficReward(consecutiveDays);

        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setCheckinDate(today);
        record.setLicenseReward(licenseReward);
        record.setUserRegisterReward(userRegisterReward);
        record.setTrafficReward(trafficReward);
        record.setConsecutiveDays(consecutiveDays);
        save(record);

        membership.setLastCheckinDate(today);
        membership.setConsecutiveCheckinDays(consecutiveDays);
        membership.setTotalCheckinDays(membership.getTotalCheckinDays() + 1);
        membership.setExtraLicenseQuota((membership.getExtraLicenseQuota() != null ? membership.getExtraLicenseQuota() : 0) + licenseReward);
        membership.setExtraUserRegisterQuota((membership.getExtraUserRegisterQuota() != null ? membership.getExtraUserRegisterQuota() : 0) + userRegisterReward);
        membership.setExtraTrafficQuota((membership.getExtraTrafficQuota() != null ? membership.getExtraTrafficQuota() : 0) + trafficReward);
        userMembershipService.updateById(membership);

        Map<String, Object> result = new HashMap<>();
        result.put("licenseReward", licenseReward);
        result.put("userRegisterReward", userRegisterReward);
        result.put("trafficReward", trafficReward);
        result.put("consecutiveDays", consecutiveDays);
        result.put("totalDays", membership.getTotalCheckinDays());

        log.info("用户[{}]签到成功：连续{}天，奖励：卡密{}张，用户注册{}个，流量{}B",
                userId, consecutiveDays, licenseReward, userRegisterReward, trafficReward);

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

    private int calculateLicenseReward(int consecutiveDays) {
        int base = ThreadLocalRandom.current().nextInt(5, 21);
        double multiplier = 1.0 + (consecutiveDays - 1) * 0.05;
        return (int) (base * multiplier);
    }

    private int calculateUserRegisterReward(int consecutiveDays) {
        int base = ThreadLocalRandom.current().nextInt(2, 11);
        double multiplier = 1.0 + (consecutiveDays - 1) * 0.05;
        return (int) (base * multiplier);
    }

    private long calculateTrafficReward(int consecutiveDays) {
        long baseMB = ThreadLocalRandom.current().nextLong(10, 51);
        double multiplier = 1.0 + (consecutiveDays - 1) * 0.05;
        return (long) (baseMB * multiplier * 1024 * 1024);
    }
}
