package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 展示用在线状态：真实 WS 会话或最近 5 分钟成功的 HTTP 登录。
 * HTTP 活跃从已提交登录流水查询，不创建 WS 会话，不累计在线时长，不作为授权依据。
 */
@Service
@RequiredArgsConstructor
public class AppUserPresenceService {
    public static final int HTTP_ACTIVE_MINUTES = 5;

    // 所有值通过 MyBatis 参数绑定；表名为固定标识符。
    // 登录后应用停用、会员过期或设备被封禁时，不再显示 HTTP 近期在线。
    // idx_access_login_stats 覆盖 app_id / event_type / created_at 时间窗查询。
    static final String HTTP_ACTIVE_SQL = """
            (app_user.deleted = 0
             AND EXISTS (SELECT 1 FROM application a WHERE a.id = app_user.app_id
                 AND a.deleted = 0 AND (a.status IS NULL OR a.status = 1)
                 AND (a.business_model = 2 OR app_user.member_expires_at > {1}))
             AND NOT EXISTS (SELECT 1 FROM app_user_binding b WHERE b.user_id = app_user.id
                 AND b.app_id = app_user.app_id AND b.deleted = 0 AND b.is_banned = 1)
             AND EXISTS (SELECT 1 FROM access_event e WHERE e.app_id = app_user.app_id
                 AND e.event_type = 'APP_USER_HTTP_LOGIN'
                 AND e.created_at > {0} AND e.created_at <= {1} AND e.ref_id = app_user.id))
            """;

    private final AppUserMapper appUserMapper;
    private final AppUserWsPresenceRegistry wsPresenceRegistry;

    /** 在原有应用/代理权限筛选内部追加条件，不替换或扩大数据范围。 */
    public void applyOnlineFilter(LambdaQueryWrapper<AppUser> wrapper, boolean online, LocalDateTime now) {
        List<Long> wsIds = wsPresenceRegistry.listOnlineAppUserIds();
        LocalDateTime cutoff = now.minusMinutes(HTTP_ACTIVE_MINUTES);
        if (online) {
            wrapper.and(w -> {
                if (wsIds != null && !wsIds.isEmpty()) {
                    w.in(AppUser::getId, wsIds).or();
                }
                w.apply(HTTP_ACTIVE_SQL, cutoff, now);
            });
        } else {
            if (wsIds != null && !wsIds.isEmpty()) {
                wrapper.notIn(AppUser::getId, wsIds);
            }
            wrapper.apply("NOT " + HTTP_ACTIVE_SQL, cutoff, now);
        }
    }

    /** 一页只发一条 HTTP 状态查询，不逐用户查询访问流水。WS 字段由原逻辑填充。 */
    public void enrich(List<AppUser> users, LocalDateTime now) {
        if (users == null || users.isEmpty()) return;
        List<Long> ids = users.stream().map(AppUser::getId).filter(java.util.Objects::nonNull).toList();
        Set<Long> activeIds = new HashSet<>();
        if (!ids.isEmpty()) {
            var active = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                    .select(AppUser::getId).in(AppUser::getId, ids)
                    .apply(HTTP_ACTIVE_SQL, now.minusMinutes(HTTP_ACTIVE_MINUTES), now));
            if (active != null) active.forEach(u -> activeIds.add(u.getId()));
        }
        for (AppUser user : users) {
            user.setHttpOnline(activeIds.contains(user.getId()));
            user.setOnline(Boolean.TRUE.equals(user.getWsOnline()) || Boolean.TRUE.equals(user.getHttpOnline()));
        }
    }
}
