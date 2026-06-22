package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.InviteRecord;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.InviteRecordMapper;
import com.ayssu.ciphergate.service.InviteService;
import com.ayssu.ciphergate.service.SystemConfigService;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl extends ServiceImpl<InviteRecordMapper, InviteRecord> implements InviteService {

    @Autowired
    private InviteRecordMapper inviteRecordMapper;

    @Autowired
    private UserMembershipService userMembershipService;

    @Autowired
    private SystemConfigService systemConfigService;

    private long getRewardAmount() {
        try {
            return Long.parseLong(systemConfigService.getConfigValue("invite.reward-amount", "300"));
        } catch (Exception e) {
            return 300L;
        }
    }

    private int getMaxInviteCount() {
        try {
            return Integer.parseInt(systemConfigService.getConfigValue("invite.max-count", "20"));
        } catch (Exception e) {
            return 20;
        }
    }

    private boolean isInviteEnabled() {
        return Boolean.parseBoolean(systemConfigService.getConfigValue("invite.enabled", "true"));
    }

    @Override
    public String getInviteCode(Long userId) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            userMembershipService.initMembershipForUser(userId);
            membership = userMembershipService.getByUserId(userId);
        }
        if (membership.getInviteCode() == null || membership.getInviteCode().isEmpty()) {
            userMembershipService.regenerateInviteCode(userId);
            membership = userMembershipService.getByUserId(userId);
        }
        return membership.getInviteCode();
    }

    @Override
    @Transactional
    public void processInvite(String inviteCode, Long newUserId) {
        if (!isInviteEnabled()) return;
        if (inviteCode == null || inviteCode.isEmpty()) return;

        UserMembership inviterMembership = userMembershipService.getByInviteCode(inviteCode);
        if (inviterMembership == null) {
            log.warn("邀请码无效: {}", inviteCode);
            return;
        }

        if (inviterMembership.getUserId().equals(newUserId)) {
            log.warn("不能邀请自己");
            return;
        }

        if (inviterMembership.getInviteCount() >= getMaxInviteCount()) {
            log.warn("用户[{}]已达到最大邀请人数", inviterMembership.getUserId());
            return;
        }

        Long existingCount = lambdaQuery()
                .eq(InviteRecord::getInviterId, inviterMembership.getUserId())
                .eq(InviteRecord::getInviteeId, newUserId)
                .count();
        if (existingCount > 0) {
            log.warn("已邀请过该用户");
            return;
        }

        long rewardAmount = getRewardAmount();

        InviteRecord record = new InviteRecord();
        record.setInviterId(inviterMembership.getUserId());
        record.setInviteeId(newUserId);
        record.setRewardAmount(rewardAmount);
        record.setRewardGranted(false);
        save(record);

        userMembershipService.grantBalance(
                inviterMembership.getUserId(),
                rewardAmount,
                null,
                "邀请奖励：邀请新用户"
        );

        inviterMembership.setInviteCount(inviterMembership.getInviteCount() + 1);
        userMembershipService.updateById(inviterMembership);

        record.setRewardGranted(true);
        updateById(record);

        log.info("邀请奖励发放：邀请人[{}]，被邀请人[{}]，奖励{}分",
                inviterMembership.getUserId(), newUserId, rewardAmount);
    }

    @Override
    @Transactional
    public String bindInviteCode(Long userId, String inviteCode) {
        if (!isInviteEnabled()) {
            throw new IllegalStateException("邀请功能已关闭");
        }
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new IllegalArgumentException("邀请码不能为空");
        }

        UserMembership userMembership = userMembershipService.getByUserId(userId);
        if (userMembership == null) {
            userMembershipService.initMembershipForUser(userId);
            userMembership = userMembershipService.getByUserId(userId);
        }

        if (userMembership.getInvitedBy() != null) {
            throw new IllegalStateException("您已绑定过邀请码，无法重复绑定");
        }

        UserMembership inviterMembership = userMembershipService.getByInviteCode(inviteCode.trim());
        if (inviterMembership == null) {
            throw new IllegalArgumentException("邀请码无效");
        }

        if (inviterMembership.getUserId().equals(userId)) {
            throw new IllegalArgumentException("不能使用自己的邀请码");
        }

        if (inviterMembership.getInviteCount() >= getMaxInviteCount()) {
            throw new IllegalStateException("该邀请人已达到最大邀请人数");
        }

        long rewardAmount = getRewardAmount();

        userMembership.setInvitedBy(inviterMembership.getUserId());
        userMembershipService.updateById(userMembership);

        InviteRecord record = new InviteRecord();
        record.setInviterId(inviterMembership.getUserId());
        record.setInviteeId(userId);
        record.setRewardAmount(rewardAmount);
        record.setRewardGranted(false);
        save(record);

        userMembershipService.grantBalance(
                inviterMembership.getUserId(),
                rewardAmount,
                null,
                "邀请奖励：邀请新用户"
        );

        inviterMembership.setInviteCount(inviterMembership.getInviteCount() + 1);
        userMembershipService.updateById(inviterMembership);

        record.setRewardGranted(true);
        updateById(record);

        log.info("邀请码绑定成功：邀请人[{}]，被邀请人[{}]，奖励{}分",
                inviterMembership.getUserId(), userId, rewardAmount);

        return inviterMembership.getInviteCode();
    }

    @Override
    public Page<InviteRecord> getInviteRecords(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(InviteRecord::getInviterId, userId)
                .orderByDesc(InviteRecord::getCreatedAt)
                .page(new Page<>(page, size));
    }

    @Override
    public Map<String, Object> getInviteStats(Long userId) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        Map<String, Object> stats = new HashMap<>();
        int maxInviteCount = getMaxInviteCount();
        if (membership == null) {
            stats.put("inviteCode", "");
            stats.put("inviteCount", 0);
            stats.put("maxInviteCount", maxInviteCount);
            stats.put("totalReward", 0);
            stats.put("invitedBy", null);
            return stats;
        }

        Long totalReward = lambdaQuery()
                .eq(InviteRecord::getInviterId, userId)
                .list()
                .stream()
                .mapToLong(InviteRecord::getRewardAmount)
                .sum();

        stats.put("inviteCode", membership.getInviteCode());
        stats.put("inviteCount", membership.getInviteCount());
        stats.put("maxInviteCount", maxInviteCount);
        stats.put("totalReward", totalReward);
        stats.put("invitedBy", membership.getInvitedBy());
        return stats;
    }
}
