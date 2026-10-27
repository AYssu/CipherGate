package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Reserve both traffic counters in a short transaction, before any response bytes are sent. */
@Service
@RequiredArgsConstructor
public class UpdateDownloadTrafficService {
    private final ApplicationMapper applicationMapper;
    private final UserMembershipService memberships;

    @Transactional
    public boolean reserve(Application expected, long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("流量不能为负数");
        // Match creation and membership mutations: owner first, then the application.
        memberships.getByUserIdForUpdate(expected.getOwnerId());
        Application current = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getId, expected.getId()).last("FOR UPDATE"));
        if (current == null || !Objects.equals(current.getOwnerId(), expected.getOwnerId())
                || (current.getStatus() != null && current.getStatus() != 1)
                || !Objects.equals(current.getUpdateFileStorageKey(), expected.getUpdateFileStorageKey())
                || !Objects.equals(current.getAppSecret(), expected.getAppSecret())) return false;
        if (!memberships.checkTrafficQuota(current.getOwnerId(), bytes)) return false;
        long used = current.getTrafficUsed() == null ? 0 : current.getTrafficUsed();
        long next = Math.addExact(used, bytes);
        if (current.getTrafficLimit() != null && current.getTrafficLimit() > 0
                && next > current.getTrafficLimit()) return false;
        memberships.consumeTrafficQuota(current.getOwnerId(), bytes);
        int changed = applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .eq(Application::getId, current.getId()).set(Application::getTrafficUsed, next));
        if (changed != 1) throw new IllegalStateException("应用流量保存失败");
        return true;
    }
}
