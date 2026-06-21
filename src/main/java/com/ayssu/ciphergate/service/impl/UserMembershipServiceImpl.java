package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.BalanceTransaction;
import com.ayssu.ciphergate.entity.MembershipChangeLog;
import com.ayssu.ciphergate.entity.MembershipLevel;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.BalanceTransactionMapper;
import com.ayssu.ciphergate.mapper.MembershipChangeLogMapper;
import com.ayssu.ciphergate.mapper.MembershipLevelMapper;
import com.ayssu.ciphergate.mapper.UserMembershipMapper;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.ayssu.ciphergate.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMembershipServiceImpl extends ServiceImpl<UserMembershipMapper, UserMembership> implements UserMembershipService {

    @Autowired
    private UserMembershipMapper userMembershipMapper;

    @Autowired
    private MembershipLevelMapper membershipLevelMapper;

    @Autowired
    private MembershipChangeLogMapper changeLogMapper;

    @Autowired
    private BalanceTransactionMapper balanceTransactionMapper;

    @Lazy
    @Autowired
    private UserService userService;

    @Override
    public UserMembership getByUserId(Long userId) {
        return userMembershipMapper.selectByUserId(userId);
    }

    @Override
    public UserMembership getByInviteCode(String inviteCode) {
        return userMembershipMapper.selectByInviteCode(inviteCode);
    }

    @Override
    @Transactional
    public void initMembershipForUser(Long userId) {
        UserMembership existing = getByUserId(userId);
        if (existing != null) {
            return;
        }

        MembershipLevel defaultLevel = membershipLevelMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MembershipLevel>()
                        .eq("level", 1));
        if (defaultLevel == null) {
            log.error("默认会员等级1不存在，无法初始化用户会员");
            return;
        }

        UserMembership membership = new UserMembership();
        membership.setUserId(userId);
        membership.setLevelId(defaultLevel.getId());
        membership.setAppUsed(0);
        membership.setLicenseUsed(0L);
        membership.setUserRegisterUsed(0L);
        membership.setTrafficUsed(0L);
        membership.setBalance(0L);
        membership.setInviteCode(generateInviteCode());
        membership.setInviteCount(0);
        membership.setConsecutiveCheckinDays(0);
        membership.setTotalCheckinDays(0);
        save(membership);

        MembershipChangeLog changeLog = new MembershipChangeLog();
        changeLog.setUserId(userId);
        changeLog.setChangeType("REGISTER");
        changeLog.setToLevelId(defaultLevel.getId());
        changeLog.setRemark("新用户注册，初始化为初级开发者");
        changeLogMapper.insert(changeLog);

        log.info("用户[{}]会员初始化完成，等级：{}", userId, defaultLevel.getLevelName());
    }

    @Override
    @Transactional
    public void upgradeLevel(Long userId, Long toLevelId, Long operatorId, String remark) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) {
            initMembershipForUser(userId);
            membership = getByUserId(userId);
        }

        Long fromLevelId = membership.getLevelId();
        MembershipLevel toLevel = membershipLevelMapper.selectById(toLevelId);
        if (toLevel == null) {
            throw new RuntimeException("目标等级不存在");
        }

        membership.setLevelId(toLevelId);
        if (toLevel.getDurationDays() != null && toLevel.getDurationDays() > 0) {
            membership.setMemberExpiresAt(LocalDateTime.now().plusDays(toLevel.getDurationDays()));
        } else if (toLevel.getDurationDays() == 0 && toLevel.getLevel() == 5) {
            membership.setMemberExpiresAt(null);
        }
        updateById(membership);

        MembershipChangeLog changeLog = new MembershipChangeLog();
        changeLog.setUserId(userId);
        changeLog.setChangeType(operatorId != null ? "ADMIN_ADJUST" : "UPGRADE");
        changeLog.setFromLevelId(fromLevelId);
        changeLog.setToLevelId(toLevelId);
        changeLog.setOperatorId(operatorId);
        changeLog.setRemark(remark);
        changeLogMapper.insert(changeLog);

        log.info("用户[{}]等级变更：{} -> {}", userId, fromLevelId, toLevelId);
    }

    @Override
    @Transactional
    public void grantBalance(Long userId, Long amount, Long operatorId, String description) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) {
            initMembershipForUser(userId);
            membership = getByUserId(userId);
        }

        Long before = membership.getBalance();
        membership.setBalance(before + amount);
        updateById(membership);

        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType("ADMIN_GRANT");
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(before + amount);
        transaction.setDescription(description);
        transaction.setOperatorId(operatorId);
        balanceTransactionMapper.insert(transaction);

        log.info("用户[{}]管理员充值：{}分，余额：{} -> {}", userId, amount, before, before + amount);
    }

    @Override
    @Transactional
    public boolean deductBalance(Long userId, Long amount, String orderNo, String description) {
        UserMembership membership = getByUserId(userId);
        if (membership == null || membership.getBalance() < amount) {
            return false;
        }

        Long before = membership.getBalance();
        membership.setBalance(before - amount);
        updateById(membership);

        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType("PURCHASE");
        transaction.setAmount(-amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(before - amount);
        transaction.setRelatedOrderNo(orderNo);
        transaction.setDescription(description);
        balanceTransactionMapper.insert(transaction);

        return true;
    }

    @Override
    public boolean checkAppQuota(Long userId) {
        if (isSuperAdmin(userId)) return true;
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        if (level.getAppQuota() == -1) return true;
        return membership.getAppUsed() < level.getAppQuota();
    }

    @Override
    public boolean checkLicenseQuota(Long userId, int count) {
        if (isSuperAdmin(userId)) return true;
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        if (level.getLicenseQuota() == -1) return true;
        return membership.getLicenseUsed() + count <= level.getLicenseQuota();
    }

    @Override
    public boolean checkUserRegisterQuota(Long userId, int count) {
        if (isSuperAdmin(userId)) return true;
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        if (level.getUserRegisterQuota() == -1) return true;
        return membership.getUserRegisterUsed() + count <= level.getUserRegisterQuota();
    }

    @Override
    public boolean checkTrafficQuota(Long userId, long bytes) {
        if (isSuperAdmin(userId)) return true;
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        if (level.getTrafficQuota() == -1) return true;
        return membership.getTrafficUsed() + bytes <= level.getTrafficQuota();
    }

    @Override
    public void consumeAppQuota(Long userId) {
        UserMembership membership = getByUserId(userId);
        if (membership != null) {
            membership.setAppUsed(membership.getAppUsed() + 1);
            updateById(membership);
        }
    }

    @Override
    public void consumeLicenseQuota(Long userId, long count) {
        UserMembership membership = getByUserId(userId);
        if (membership != null) {
            membership.setLicenseUsed(membership.getLicenseUsed() + count);
            updateById(membership);
        }
    }

    @Override
    public void consumeUserRegisterQuota(Long userId, int count) {
        UserMembership membership = getByUserId(userId);
        if (membership != null) {
            membership.setUserRegisterUsed(membership.getUserRegisterUsed() + count);
            updateById(membership);
        }
    }

    @Override
    public void consumeTrafficQuota(Long userId, long bytes) {
        UserMembership membership = getByUserId(userId);
        if (membership != null) {
            membership.setTrafficUsed(membership.getTrafficUsed() + bytes);
            updateById(membership);
        }
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        return userService.hasRole(userId, "SUPER_ADMIN");
    }

    private String generateInviteCode() {
        String code;
        do {
            code = "CG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (getByInviteCode(code) != null);
        return code;
    }
}
