package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.InviteRecord;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.InviteRecordMapper;
import com.ayssu.ciphergate.service.InviteService;
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

    private static final Long REWARD_AMOUNT = 300L;
    private static final int MAX_INVITE_COUNT = 20;

    @Override
    public String getInviteCode(Long userId) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            userMembershipService.initMembershipForUser(userId);
            membership = userMembershipService.getByUserId(userId);
        }
        return membership.getInviteCode();
    }

    @Override
    @Transactional
    public void processInvite(String inviteCode, Long newUserId) {
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

        if (inviterMembership.getInviteCount() >= MAX_INVITE_COUNT) {
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

        InviteRecord record = new InviteRecord();
        record.setInviterId(inviterMembership.getUserId());
        record.setInviteeId(newUserId);
        record.setRewardAmount(REWARD_AMOUNT);
        record.setRewardGranted(false);
        save(record);

        userMembershipService.grantBalance(
                inviterMembership.getUserId(),
                REWARD_AMOUNT,
                null,
                "邀请奖励：邀请新用户"
        );

        inviterMembership.setInviteCount(inviterMembership.getInviteCount() + 1);
        userMembershipService.updateById(inviterMembership);

        record.setRewardGranted(true);
        updateById(record);

        log.info("邀请奖励发放：邀请人[{}]，被邀请人[{}]，奖励{}分",
                inviterMembership.getUserId(), newUserId, REWARD_AMOUNT);
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
        if (membership == null) {
            stats.put("inviteCode", "");
            stats.put("inviteCount", 0);
            stats.put("maxInviteCount", MAX_INVITE_COUNT);
            stats.put("totalReward", 0);
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
        stats.put("maxInviteCount", MAX_INVITE_COUNT);
        stats.put("totalReward", totalReward);
        return stats;
    }
}
