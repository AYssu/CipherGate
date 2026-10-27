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
        if (!isInviteEnabled() || inviteCode == null || inviteCode.isBlank()) return;
        bindOnce(newUserId, inviteCode.trim(), false);
    }

    @Override
    @Transactional
    public String bindInviteCode(Long userId, String inviteCode) {
        if (!isInviteEnabled()) throw new IllegalStateException("邀请功能已关闭");
        if (inviteCode == null || inviteCode.isBlank()) throw new IllegalArgumentException("邀请码不能为空");
        return bindOnce(userId, inviteCode.trim(), true);
    }

    private String bindOnce(Long userId, String inviteCode, boolean strict) {
        UserMembership candidate = userMembershipService.getByInviteCode(inviteCode);
        if (candidate == null || candidate.getUserId().equals(userId)) {
            if (strict) throw new IllegalArgumentException("邀请码无效或不能邀请自己");
            return null;
        }
        Long inviterId = candidate.getUserId();
        // A stable ordering also prevents mutual invitations from deadlocking.
        UserMembership first = userMembershipService.getByUserIdForUpdate(Math.min(userId, inviterId));
        UserMembership second = userMembershipService.getByUserIdForUpdate(Math.max(userId, inviterId));
        UserMembership invitee = first.getUserId().equals(userId) ? first : second;
        UserMembership inviter = first.getUserId().equals(inviterId) ? first : second;
        boolean alreadyBound = invitee.getInvitedBy() != null || lambdaQuery()
                .eq(InviteRecord::getInviteeId, userId).count() > 0;
        if (alreadyBound || inviter.getInviteCount() >= getMaxInviteCount()
                || !inviteCode.equals(inviter.getInviteCode())) {
            if (strict) throw new IllegalStateException("已绑定邀请码或该邀请码不可用");
            return null;
        }
        long reward = getRewardAmount();
        if (reward < 0) throw new IllegalStateException("邀请奖励配置无效");
        invitee.setInvitedBy(inviterId);
        if (!userMembershipService.updateById(invitee)) throw new IllegalStateException("邀请关系保存失败");
        // Persist the counter BEFORE granting balance: never save a stale balance snapshot afterwards.
        inviter.setInviteCount(Math.addExact(inviter.getInviteCount(), 1));
        if (!userMembershipService.updateById(inviter)) throw new IllegalStateException("邀请计数保存失败");
        if (reward > 0) userMembershipService.grantBalance(inviterId, reward, null, "邀请奖励：邀请新用户");
        InviteRecord record = new InviteRecord();
        record.setInviterId(inviterId);
        record.setInviteeId(userId);
        record.setRewardAmount(reward);
        record.setRewardGranted(true);
        if (!save(record)) throw new IllegalStateException("邀请记录保存失败");
        log.info("邀请绑定完成: inviterId={}, inviteeId={}", inviterId, userId);
        return inviter.getInviteCode();
    }

    @Override
    public Page<InviteRecord> getInviteRecords(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(InviteRecord::getInviterId, userId)
                .orderByDesc(InviteRecord::getCreatedAt)
                .page(com.ayssu.ciphergate.util.IdPageQuery.page(page, size));
    }

    @Override
    public Map<String, Object> getInviteStats(Long userId) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        Map<String, Object> stats = new HashMap<>();
        boolean enabled = isInviteEnabled();
        int maxInviteCount = getMaxInviteCount();
        long rewardAmount = getRewardAmount();
        stats.put("enabled", enabled);
        stats.put("rewardAmount", rewardAmount);
        stats.put("maxInviteCount", maxInviteCount);
        if (!enabled || membership == null) {
            stats.put("inviteCode", "");
            stats.put("inviteCount", 0);
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
        stats.put("totalReward", totalReward);
        stats.put("invitedBy", membership.getInvitedBy());
        return stats;
    }
}
