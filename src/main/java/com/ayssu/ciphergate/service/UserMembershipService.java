package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.UserMembership;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserMembershipService extends IService<UserMembership> {

    UserMembership getByUserId(Long userId);

    UserMembership getByInviteCode(String inviteCode);

    void initMembershipForUser(Long userId);

    void upgradeLevel(Long userId, Long toLevelId, Long operatorId, String remark);

    void grantBalance(Long userId, Long amount, Long operatorId, String description);

    boolean deductBalance(Long userId, Long amount, String orderNo, String description);

    boolean checkAppQuota(Long userId);

    boolean checkLicenseQuota(Long userId, int count);

    boolean checkUserRegisterQuota(Long userId, int count);

    boolean checkTrafficQuota(Long userId, long bytes);

    void consumeAppQuota(Long userId);

    void consumeLicenseQuota(Long userId, long count);

    void consumeUserRegisterQuota(Long userId, int count);

    void consumeTrafficQuota(Long userId, long bytes);

    boolean isSuperAdmin(Long userId);
}
