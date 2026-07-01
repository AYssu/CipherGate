package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.BalanceTransaction;
import com.ayssu.ciphergate.entity.MembershipChangeLog;
import com.ayssu.ciphergate.entity.MembershipLevel;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.BalanceTransactionMapper;
import com.ayssu.ciphergate.mapper.MembershipChangeLogMapper;
import com.ayssu.ciphergate.mapper.MembershipLevelMapper;
import com.ayssu.ciphergate.mapper.UserMembershipMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.ayssu.ciphergate.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    @Autowired
    private LicenseKeyMapper licenseKeyMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private ApplicationMapper applicationMapper;

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
    public void regenerateInviteCode(Long userId) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) {
            log.warn("用户[{}]会员不存在，无法重新生成邀请码", userId);
            return;
        }
        if (membership.getInviteCode() != null && !membership.getInviteCode().isEmpty()) {
            return;
        }
        membership.setInviteCode(generateInviteCode());
        updateById(membership);
        log.info("用户[{}]邀请码已重新生成: {}", userId, membership.getInviteCode());
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
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        long baseQuota = level.getAppQuota() == -1 ? Long.MAX_VALUE : level.getAppQuota();
        long extraQuota = membership.getExtraAppQuota() != null ? membership.getExtraAppQuota() : 0;
        long totalQuota = baseQuota + extraQuota;
        if (totalQuota < 0) totalQuota = Long.MAX_VALUE;
        long actualUsed = countUserApps(userId);
        return actualUsed < totalQuota;
    }

    @Override
    public boolean checkLicenseQuota(Long userId, int count) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        long baseQuota = level.getLicenseQuota() == -1 ? Long.MAX_VALUE : level.getLicenseQuota();
        long extraQuota = membership.getExtraLicenseQuota() != null ? membership.getExtraLicenseQuota() : 0;
        long totalQuota = baseQuota + extraQuota;
        if (totalQuota < 0) totalQuota = Long.MAX_VALUE;
        long actualUsed = countUserLicenses(userId);
        return actualUsed + count <= totalQuota;
    }

    @Override
    public boolean checkUserRegisterQuota(Long userId, int count) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        long baseQuota = level.getUserRegisterQuota() == -1 ? Long.MAX_VALUE : level.getUserRegisterQuota();
        long extraQuota = membership.getExtraUserRegisterQuota() != null ? membership.getExtraUserRegisterQuota() : 0;
        long totalQuota = baseQuota + extraQuota;
        if (totalQuota < 0) totalQuota = Long.MAX_VALUE;
        long actualUsed = countUserAppUsers(userId);
        return actualUsed + count <= totalQuota;
    }

    @Override
    public boolean checkTrafficQuota(Long userId, long bytes) {
        UserMembership membership = getByUserId(userId);
        if (membership == null) return false;
        MembershipLevel level = membershipLevelMapper.selectById(membership.getLevelId());
        if (level == null) return false;
        long baseQuota = level.getTrafficQuota() == -1 ? Long.MAX_VALUE : level.getTrafficQuota();
        long extraQuota = membership.getExtraTrafficQuota() != null ? membership.getExtraTrafficQuota() : 0;
        long totalQuota = baseQuota + extraQuota;
        if (totalQuota < 0) totalQuota = Long.MAX_VALUE;
        long actualUsed = membership.getTrafficUsed() != null ? membership.getTrafficUsed() : 0;
        return actualUsed + bytes <= totalQuota;
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

    /**
     * 统计用户所有应用下的卡密总数（包括已删除的）
     */
    public long countUserLicenses(Long userId) {
        return licenseKeyMapper.countAllByKeyOwner(userId);
    }

    /**
     * 统计用户所有应用下的终端用户总数（包括已删除的）
     */
    public long countUserAppUsers(Long userId) {
        // 获取用户所有应用ID
        var appIds = applicationMapper.selectList(
                new QueryWrapper<com.ayssu.ciphergate.entity.Application>()
                        .eq("owner_id", userId)
                        .select("id"))
                .stream()
                .map(com.ayssu.ciphergate.entity.Application::getId)
                .toList();
        if (appIds.isEmpty()) return 0;

        return appUserMapper.selectCount(
                new QueryWrapper<com.ayssu.ciphergate.entity.AppUser>()
                        .in("app_id", appIds));
    }

    /**
     * 统计用户创建的应用总数（包括已删除的）
     */
    public long countUserApps(Long userId) {
        return applicationMapper.selectCount(
                new QueryWrapper<com.ayssu.ciphergate.entity.Application>()
                        .eq("owner_id", userId));
    }

    private String generateInviteCode() {
        String code;
        do {
            code = "CG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (getByInviteCode(code) != null);
        return code;
    }
}
