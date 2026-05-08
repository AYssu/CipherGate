package com.ayssu.ciphergate.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.agent.AgentAuthorizationService;
import com.ayssu.ciphergate.agent.AgentPermissionCodes;
import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberFailItem;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberResultDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberDurationDTO;
import com.ayssu.ciphergate.dto.AppUserBatchIdsDTO;
import com.ayssu.ciphergate.dto.AppUserBatchBanDTO;
import com.ayssu.ciphergate.dto.AppUserAppNotExpiredDurationDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.dto.ExtendMemberDurationDTO;
import com.ayssu.ciphergate.entity.AppAgent;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.entity.AppUserTrial;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.AppAgentMapper;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.AppUserTrialMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppUserService;
import com.ayssu.ciphergate.service.GeoIpService;
import com.ayssu.ciphergate.service.SystemMessageService;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsSessionKickService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 应用终端用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {
    
    private final AppUserMapper appUserMapper;
    private final AppUserBindingMapper appUserBindingMapper;
    private final AppUserTrialMapper appUserTrialMapper;
    private final ApplicationMapper applicationMapper;
    private final AppAgentMapper appAgentMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final AgentAuthorizationService agentAuthorizationService;
    private final SystemMessageService systemMessageService;
    private final GeoIpService geoIpService;
    private final AppUserWsPresenceRegistry appUserWsPresenceRegistry;
    private final AppUserWsSessionKickService appUserWsSessionKickService;
    
    @Override
    public Page<AppUser> getAppUserPage(AppUserQueryDTO queryDTO, Long operatorId) {
        Page<AppUser> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        log.info("终端用户列表查询开始: operatorId={}, appId={}, username={}, email={}, phone={}",
                operatorId, queryDTO.getAppId(), queryDTO.getUsername(), queryDTO.getEmail(), queryDTO.getPhone());

        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        applyApplicationScopeForAppUserQuery(wrapper, queryDTO, operatorId);
        applyGlobalKeywordFilter(wrapper, queryDTO);
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AppUser::getUsername, queryDTO.getUsername())
               .like(StringUtils.hasText(queryDTO.getEmail()), AppUser::getEmail, queryDTO.getEmail())
               .like(StringUtils.hasText(queryDTO.getPhone()), AppUser::getPhone, queryDTO.getPhone())
               .like(StringUtils.hasText(queryDTO.getNickname()), AppUser::getNickname, queryDTO.getNickname())
               .eq(AppUser::getDeleted, 0)
               .orderByDesc(AppUser::getCreatedAt);

        // 封禁状态：由 app_user_binding 是否存在已封禁且未删除的记录决定
        if (queryDTO.getBanned() != null) {
            // MyBatis-Plus LambdaWrapper 对 exists/not exists 需要用 apply 拼 SQL（表名固定为 app_user / app_user_binding）
            if (Boolean.TRUE.equals(queryDTO.getBanned())) {
                wrapper.apply(
                        "exists (select 1 from app_user_binding b where b.user_id = app_user.id and b.deleted = 0 and b.is_banned = 1)"
                );
            } else {
                wrapper.apply(
                        "not exists (select 1 from app_user_binding b where b.user_id = app_user.id and b.deleted = 0 and b.is_banned = 1)"
                );
            }
        }

        // 会员状态：基于 member_expires_at 与当前时间比较
        if (StringUtils.hasText(queryDTO.getMemberStatus())) {
            String s = queryDTO.getMemberStatus().trim().toUpperCase();
            LocalDateTime now = LocalDateTime.now();
            switch (s) {
                case "ACTIVE" -> wrapper.gt(AppUser::getMemberExpiresAt, now);
                case "EXPIRED" -> wrapper.isNotNull(AppUser::getMemberExpiresAt).le(AppUser::getMemberExpiresAt, now);
                case "NONE" -> wrapper.isNull(AppUser::getMemberExpiresAt);
                default -> {
                }
            }
        }

        // WS 在线状态：基于内存 registry 的在线 userId 集合过滤（单机）
        if (queryDTO.getWsOnline() != null) {
            List<Long> onlineIds = appUserWsPresenceRegistry.listOnlineAppUserIds();
            if (Boolean.TRUE.equals(queryDTO.getWsOnline())) {
                if (onlineIds.isEmpty()) {
                    // 当前无在线用户，直接返回空页（避免全表扫描）
                    wrapper.apply("1=0");
                } else {
                    wrapper.in(AppUser::getId, onlineIds);
                }
            } else {
                // 离线：排除在线集合；若集合为空则无需过滤（全离线）
                if (!onlineIds.isEmpty()) {
                    wrapper.notIn(AppUser::getId, onlineIds);
                }
            }
        }
        
        Page<AppUser> result = appUserMapper.selectPage(page, wrapper);
        log.info("终端用户列表查询完成: operatorId={}, total={}, records={}",
                operatorId, result.getTotal(), result.getRecords() == null ? 0 : result.getRecords().size());
        
        // 填充关联信息
        result.getRecords().forEach(this::fillRelatedInfo);
        // 默认列表：在线优先（在线排前），组内保持原有排序（创建时间倒序）
        result.getRecords().sort((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getWsOnline()), Boolean.TRUE.equals(a.getWsOnline())));
        
        return result;
    }

    /**
     * 全局关键字：对用户名/邮箱/手机号/昵称做 OR like，方便“一个搜索框搜全部”。
     */
    private void applyGlobalKeywordFilter(LambdaQueryWrapper<AppUser> wrapper, AppUserQueryDTO queryDTO) {
        if (wrapper == null || queryDTO == null) {
            return;
        }
        if (!StringUtils.hasText(queryDTO.getKeyword())) {
            return;
        }
        String kw = queryDTO.getKeyword().trim();
        if (!StringUtils.hasText(kw)) {
            return;
        }
        wrapper.and(w -> w.like(AppUser::getUsername, kw)
                .or()
                .like(AppUser::getEmail, kw)
                .or()
                .like(AppUser::getPhone, kw)
                .or()
                .like(AppUser::getNickname, kw));
    }

    /**
     * 非管理员仅能查询本人拥有应用下的终端用户；未传 appId 时自动限定为这些应用（与卡密列表一致）。
     */
    private void applyApplicationScopeForAppUserQuery(LambdaQueryWrapper<AppUser> wrapper,
                                                        AppUserQueryDTO queryDTO,
                                                        Long operatorId) {
        if (securityUtils.isAdmin(operatorId)) {
            wrapper.eq(queryDTO.getAppId() != null, AppUser::getAppId, queryDTO.getAppId());
            return;
        }
        if (queryDTO.getAppId() != null) {
            Long appId = queryDTO.getAppId();
            if (agentAuthorizationService.isOwner(appId, operatorId)) {
                wrapper.eq(AppUser::getAppId, appId);
                return;
            }
            AppAgent agent = ensureAppUserListPermission(appId, operatorId, "无权限查询此应用的终端用户");
            if (agentAuthorizationService.isScopeAllInApp(agent) || hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL)) {
                wrapper.eq(AppUser::getAppId, appId);
            } else {
                wrapper.eq(AppUser::getAppId, appId).eq(AppUser::getAgentId, agent.getId());
            }
            return;
        }
        List<Long> ownedAppIds = listOwnedApplicationIds(operatorId);
        List<AppAgent> agents = agentAuthorizationService.listEnabledAgentsForUser(operatorId);
        log.info("终端用户查询代理范围: operatorId={}, ownedAppIds={}, agentCount={}",
                operatorId, ownedAppIds, agents.size());
        if (ownedAppIds.isEmpty() && agents.isEmpty()) {
            wrapper.apply("1=0");
        } else {
            wrapper.and(w -> {
                boolean hasCond = false;
                if (!ownedAppIds.isEmpty()) {
                    w.in(AppUser::getAppId, ownedAppIds);
                    hasCond = true;
                }
                for (AppAgent agent : agents) {
                    boolean canList = canAgentListAppUser(agent);
                    boolean viewAll = hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL);
                    log.info("终端用户查询代理命中: operatorId={}, agentId={}, appId={}, canList={}, viewAll={}, scopeMode={}",
                            operatorId, agent.getId(), agent.getAppId(), canList, viewAll, agent.getScopeMode());
                    if (!canAgentListAppUser(agent)) {
                        continue;
                    }
                    if (agentAuthorizationService.isScopeAllInApp(agent) || hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL)) {
                        if (hasCond) {
                            w.or();
                        }
                        w.eq(AppUser::getAppId, agent.getAppId());
                        hasCond = true;
                    } else {
                        if (hasCond) {
                            w.or();
                        }
                        w.eq(AppUser::getAppId, agent.getAppId()).eq(AppUser::getAgentId, agent.getId());
                        hasCond = true;
                    }
                }
                if (!hasCond) {
                    w.apply("1=0");
                }
            });
        }
    }

    private List<Long> listOwnedApplicationIds(Long userId) {
        return applicationMapper.selectList(
                        new LambdaQueryWrapper<Application>().eq(Application::getOwnerId, userId))
                .stream()
                .map(Application::getId)
                .toList();
    }
    
    @Override
    public AppUser getAppUserById(Long id, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        AppAgent agent = ensureAppUserListPermission(appUser.getAppId(), operatorId, "无权限查看此用户");
        if (agent != null && !agentAuthorizationService.isScopeAllInApp(agent) && !hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL)) {
            if (!agent.getId().equals(appUser.getAgentId())) {
                throw new RuntimeException("无权限查看此用户");
            }
        }
        
        fillRelatedInfo(appUser);
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser createAppUser(AppUserDTO dto, Long operatorId) {
        // 验证应用是否存在
        Application application = applicationMapper.selectById(dto.getAppId());
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限
        AppAgent agent = ensureAppUserPermission(dto.getAppId(), operatorId, AgentPermissionCodes.APP_USER_CREATE, "无权限操作此应用的用户");
        
        // 检查用户名是否重复
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUser::getAppId, dto.getAppId())
               .eq(AppUser::getUsername, dto.getUsername())
               .eq(AppUser::getDeleted, 0);
        if (appUserMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否重复
        if (StringUtils.hasText(dto.getEmail())) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUser::getAppId, dto.getAppId())
                   .eq(AppUser::getEmail, dto.getEmail())
                   .eq(AppUser::getDeleted, 0);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("邮箱已存在");
            }
        }
        
        AppUser appUser = new AppUser();
        BeanUtils.copyProperties(dto, appUser);
        
        // 使用 Hutool BCrypt 加密密码
        if (StringUtils.hasText(dto.getPassword())) {
            appUser.setPassword(BCrypt.hashpw(dto.getPassword()));
        }
        
        appUser.setLoginCount(0);
        appUser.setDeleted(0);
        if (agent != null) {
            appUser.setAgentId(agent.getId());
        }
        
        LocalDateTime now = LocalDateTime.now();
        appUser.setCreatedAt(now);
        appUser.setUpdatedAt(now);
        
        appUserMapper.insert(appUser);
        
        log.info("创建终端用户成功: username={}, appId={}, operatorId={}", 
                appUser.getUsername(), dto.getAppId(), operatorId);
        
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser updateAppUser(Long id, AppUserDTO dto, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
        // 检查邮箱是否重复
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(appUser.getEmail())) {
            LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUser::getAppId, appUser.getAppId())
                   .eq(AppUser::getEmail, dto.getEmail())
                   .eq(AppUser::getDeleted, 0)
                   .ne(AppUser::getId, id);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("邮箱已存在");
            }
        }

        // 检查用户名是否重复（同一应用下唯一）
        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(appUser.getUsername())) {
            String newUsername = dto.getUsername().trim();
            LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUser::getAppId, appUser.getAppId())
                    .eq(AppUser::getUsername, newUsername)
                    .eq(AppUser::getDeleted, 0)
                    .ne(AppUser::getId, id);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("用户名已存在");
            }
            appUser.setUsername(newUsername);
        }
        
        // 更新字段
        if (StringUtils.hasText(dto.getEmail())) {
            appUser.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            appUser.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getNickname())) {
            appUser.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getAvatarUrl())) {
            appUser.setAvatarUrl(dto.getAvatarUrl());
        }
        if (StringUtils.hasText(dto.getSignature())) {
            appUser.setSignature(dto.getSignature());
        }
        if (dto.getMemberExpiresAt() != null) {
            appUser.setMemberExpiresAt(dto.getMemberExpiresAt());
        }
        
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        
        log.info("更新终端用户成功: id={}, operatorId={}", id, operatorId);
        
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAppUser(Long id, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_DELETE, "无权限操作此用户");
        
        // 逻辑删除（由 @TableLogic + MyBatis-Plus 统一处理）
        int affected = appUserMapper.deleteById(id);
        if (affected <= 0) {
            throw new RuntimeException("删除失败，记录不存在或已删除");
        }
        
        log.info("删除终端用户成功: id={}, username={}, operatorId={}", 
                id, appUser.getUsername(), operatorId);

        String appName = "未知应用";
        Application application = applicationMapper.selectById(appUser.getAppId());
        if (application != null && StringUtils.hasText(application.getAppName())) {
            appName = application.getAppName();
        }

        // 给操作用户发送站内通知（用于前端角标）
        systemMessageService.createMessage(
                "APP_USER_DELETE",
                "终端用户删除成功",
                "你已删除应用「" + appName + "」下的用户「" + appUser.getUsername() + "」(ID: " + id + ")。",
                "LOW",
                "USER",
                operatorId
        );
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
        // 使用 Hutool BCrypt 加密并更新密码
        appUser.setPassword(BCrypt.hashpw(newPassword));
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        
        log.info("重置用户密码成功: id={}, username={}, operatorId={}", 
                id, appUser.getUsername(), operatorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(Long id, Long bindingId, Boolean ban, String reason, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
        // 更新绑定记录的封禁状态
        if (bindingId != null) {
            AppUserBinding binding = appUserBindingMapper.selectById(bindingId);
            if (binding != null) {
                if (!binding.getUserId().equals(id)) {
                    throw new RuntimeException("绑定记录不属于该终端用户");
                }
                if (!binding.getAppId().equals(appUser.getAppId())) {
                    throw new RuntimeException("绑定记录与应用不匹配");
                }
                binding.setIsBanned(ban);
                binding.setBanReason(ban ? reason : null);
                binding.setBanAt(ban ? LocalDateTime.now() : null);
                binding.setStatus(ban ? 3 : 1); // 3=已封禁, 1=正常
                binding.setUpdatedAt(LocalDateTime.now());
                appUserBindingMapper.updateById(binding);
                if (Boolean.TRUE.equals(ban)) {
                    scheduleKickWsSessionsAfterCommit(id, binding.getDeviceId());
                }
            }
        } else {
            // 封禁该用户的所有绑定
            LambdaQueryWrapper<AppUserBinding> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUserBinding::getUserId, id)
                   .eq(AppUserBinding::getDeleted, 0);
            
            appUserBindingMapper.selectList(wrapper).forEach(binding -> {
                binding.setIsBanned(ban);
                binding.setBanReason(ban ? reason : null);
                binding.setBanAt(ban ? LocalDateTime.now() : null);
                binding.setStatus(ban ? 3 : 1);
                binding.setUpdatedAt(LocalDateTime.now());
                appUserBindingMapper.updateById(binding);
            });
            if (Boolean.TRUE.equals(ban)) {
                scheduleKickWsSessionsAfterCommit(id, null);
            }
        }
        
        log.info("{}用户成功: id={}, username={}, operatorId={}", 
                ban ? "封禁" : "解封", id, appUser.getUsername(), operatorId);
    }

    /**
     * 封禁提交后再踢 WS，避免踢线成功但事务回滚；无事务时立即执行。
     */
    private void scheduleKickWsSessionsAfterCommit(Long appUserId, String deviceIdOrNull) {
        if (appUserId == null) {
            return;
        }
        Runnable kick = () -> {
            try {
                appUserWsSessionKickService.kickByAppUserId(appUserId, deviceIdOrNull);
            } catch (Exception e) {
                log.warn("WS kick after ban failed, appUserId={}", appUserId, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            kick.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kick.run();
            }
        });
    }

    @Override
    public void kickWs(Long id, Long operatorId) {
        if (id == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        // 记录级权限校验：与更新/封禁一致
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        // 强制下线：客户端按 MEMBER_EXPIRED 处理即可
        appUserWsSessionKickService.kickByAppUserId(id, null, AppUserWsSessionKickService.KICK_MEMBER_EXPIRED);
        log.info("强制下线终端用户 WS: id={}, username={}, operatorId={}", id, appUser.getUsername(), operatorId);
    }
    
    @Override
    public Page<AppUserBinding> getUserBindings(Long userId, Integer current, Integer size, Long operatorId) {
        // 验证用户是否存在
        AppUser appUser = appUserMapper.selectById(userId);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        ensureAppUserListPermission(appUser.getAppId(), operatorId, "无权限查看此用户绑定信息");
        
        Page<AppUserBinding> page = new Page<>(current, size);
        
        LambdaQueryWrapper<AppUserBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUserBinding::getUserId, userId)
               .eq(AppUserBinding::getDeleted, 0)
               .orderByDesc(AppUserBinding::getCreatedAt);
        
        Page<AppUserBinding> result = appUserBindingMapper.selectPage(page, wrapper);
        
        // 填充用户名和卡密码信息
        result.getRecords().forEach(binding -> {
            binding.setUsername(appUser.getUsername());
            // 这里可以根据需要填充更多关联信息，比如卡密码等
        });
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevice(Long userId, Long bindingId, String reason, Long operatorId) {
        // 验证用户是否存在
        AppUser appUser = appUserMapper.selectById(userId);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
        // 验证绑定记录是否存在
        AppUserBinding binding = appUserBindingMapper.selectById(bindingId);
        if (binding == null || binding.getDeleted() == 1) {
            throw new RuntimeException("绑定记录不存在");
        }
        
        if (!binding.getUserId().equals(userId)) {
            throw new RuntimeException("绑定记录不属于该用户");
        }
        
        // 检查是否允许解绑
        if (binding.getAllowUnbind() != null && !binding.getAllowUnbind()) {
            throw new RuntimeException("该设备不允许解绑");
        }
        
        // 更新解绑次数和状态
        binding.setUnbindCount(binding.getUnbindCount() + 1);
        binding.setStatus(4); // 4=已解绑
        binding.setRemark(reason);
        binding.setUpdatedAt(LocalDateTime.now());
        binding.setDeleted(1); // 软删除
        
        appUserBindingMapper.updateById(binding);
        
        log.info("解绑用户设备成功: userId={}, bindingId={}, deviceId={}, operatorId={}", 
                userId, bindingId, binding.getDeviceId(), operatorId);
    }
    
    /**
     * 检查用户是否有权限操作应用的终端用户
     */
    private boolean hasPermission(Long appId, Long userId) {
        Application application = applicationMapper.selectById(appId);
        if (application == null) {
            return false;
        }
        
        // 应用所有者或管理员可以操作
        if (application.getOwnerId().equals(userId) || securityUtils.isAdmin(userId)) {
            return true;
        }
        return agentAuthorizationService.findEnabledAgentForUser(appId, userId) != null;
    }

    private AppAgent ensureAppUserPermission(Long appId, Long userId, String permissionCode, String message) {
        if (securityUtils.isAdmin(userId) || agentAuthorizationService.isOwner(appId, userId)) {
            return null;
        }
        AppAgent agent = agentAuthorizationService.findEnabledAgentForUser(appId, userId);
        if (agent == null) {
            throw new RuntimeException(message);
        }
        if (!hasAgentPermission(agent, permissionCode)) {
            throw new RuntimeException(message);
        }
        return agent;
    }

    private AppAgent ensureAppUserListPermission(Long appId, Long userId, String message) {
        if (securityUtils.isAdmin(userId) || agentAuthorizationService.isOwner(appId, userId)) {
            return null;
        }
        AppAgent agent = agentAuthorizationService.findEnabledAgentForUser(appId, userId);
        if (agent == null) {
            throw new RuntimeException(message);
        }
        if (!canAgentListAppUser(agent)) {
            throw new RuntimeException(message);
        }
        return agent;
    }

    private boolean canAgentListAppUser(AppAgent agent) {
        return hasAgentPermission(agent, AgentPermissionCodes.APP_USER_LIST)
                || hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL);
    }

    private boolean hasAgentPermission(AppAgent agent, String permissionCode) {
        if (agent == null || !StringUtils.hasText(permissionCode)) {
            return false;
        }
        return agentAuthorizationService.getAgentPermissions(agent.getId()).contains(permissionCode.trim().toUpperCase());
    }
    
    /**
     * 填充关联信息
     */
    private void fillRelatedInfo(AppUser appUser) {
        // 填充应用名称
        if (appUser.getAppId() != null) {
            Application application = applicationMapper.selectById(appUser.getAppId());
            if (application != null) {
                appUser.setAppName(application.getAppName());
            }
        }
        
        // 统计绑定设备数量
        LambdaQueryWrapper<AppUserBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUserBinding::getUserId, appUser.getId())
               .eq(AppUserBinding::getDeleted, 0);
        long count = appUserBindingMapper.selectCount(wrapper);
        appUser.setBindingCount((int) count);

        LambdaQueryWrapper<AppUserBinding> bannedWrap = new LambdaQueryWrapper<>();
        bannedWrap.eq(AppUserBinding::getUserId, appUser.getId())
                .eq(AppUserBinding::getDeleted, 0)
                .eq(AppUserBinding::getIsBanned, true);
        appUser.setIsBanned(appUserBindingMapper.selectCount(bannedWrap) > 0);

        // 填充创建来源与代理名称
        if (appUser.getAgentId() != null) {
            appUser.setCreatorType("AGENT");
            AppAgent agent = appAgentMapper.selectById(appUser.getAgentId());
            if (agent != null && agent.getUserId() != null) {
                User agentUser = userMapper.selectById(agent.getUserId());
                if (agentUser != null) {
                    String base = StringUtils.hasText(agentUser.getName()) ? agentUser.getName() : agentUser.getLogin();
                    appUser.setAgentDisplayName(base + " #" + agentUser.getId());
                } else {
                    appUser.setAgentDisplayName("#" + agent.getUserId());
                }
            }
        } else {
            appUser.setCreatorType("SELF");
        }

        // 填充最后登录IP区域（开启且可解析时）
        if (StringUtils.hasText(appUser.getLastLoginIp())) {
            geoIpService.resolve(appUser.getLastLoginIp().trim()).ifPresent(geo ->
                    appUser.setLastLoginIpRegion(formatRegion(geo.country(), geo.province(), geo.city()))
            );
        }

        enrichMemberStatus(appUser);
        enrichTrialStatus(appUser);
        enrichWsPresence(appUser);
    }

    private String formatRegion(String country, String province, String city) {
        String c = StringUtils.hasText(country) ? country.trim() : "";
        String p = StringUtils.hasText(province) ? province.trim() : "";
        String ci = StringUtils.hasText(city) ? city.trim() : "";
        StringBuilder sb = new StringBuilder();
        if (!c.isEmpty()) sb.append(c);
        if (!p.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(p);
        }
        if (!ci.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(ci);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void enrichMemberStatus(AppUser appUser) {
        LocalDateTime me = appUser.getMemberExpiresAt();
        appUser.setMemberActive(me != null && me.isAfter(LocalDateTime.now()));
    }

    private void enrichTrialStatus(AppUser appUser) {
        if (appUser == null || appUser.getId() == null || appUser.getAppId() == null) {
            return;
        }
        var trial = appUserTrialMapper.selectOne(new LambdaQueryWrapper<AppUserTrial>()
                .eq(AppUserTrial::getAppId, appUser.getAppId())
                .eq(AppUserTrial::getUserId, appUser.getId())
                .last("limit 1"));
        if (trial == null) {
            appUser.setTrialApplied(false);
            appUser.setTrialExpiresAt(null);
            appUser.setTrialActive(false);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        appUser.setTrialApplied(true);
        appUser.setTrialExpiresAt(trial.getTrialExpiresAt());
        appUser.setTrialActive(trial.getTrialExpiresAt() != null && trial.getTrialExpiresAt().isAfter(now));
    }

    private void enrichWsPresence(AppUser appUser) {
        if (appUser == null || appUser.getId() == null) {
            return;
        }
        AppUserWsPresenceRegistry.PresenceSnapshot snap = appUserWsPresenceRegistry.snapshot(appUser.getId());
        long nowMs = System.currentTimeMillis();
        appUser.setWsOnline(snap.isOnline());
        appUser.setWsSessionCount(snap.getSessionCount());
        appUser.setWsTodayOnlineSeconds(appUserWsPresenceRegistry.todayOnlineSeconds(appUser.getId(), snap, nowMs));
        if (snap.isOnline()) {
            appUser.setWsEarliestConnectedAtEpochMs(snap.getEarliestConnectedAtEpochMs());
            appUser.setWsOnlineSeconds(appUserWsPresenceRegistry.continuousOnlineSeconds(snap, nowMs));
        } else {
            appUser.setWsEarliestConnectedAtEpochMs(null);
            appUser.setWsOnlineSeconds(null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser extendMemberByDays(Long id, int days, Long operatorId) {
        if (days < 1) {
            throw new RuntimeException("延长天数至少为 1");
        }
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = appUser.getMemberExpiresAt();
        if (base == null || base.isBefore(now) || base.isEqual(now)) {
            base = now;
        }
        LocalDateTime newExp = base.plus(days, ChronoUnit.DAYS);
        appUser.setMemberExpiresAt(newExp);
        appUser.setUpdatedAt(now);
        appUserMapper.updateById(appUser);
        log.info("延长会员: userId={}, days={}, newExpiresAt={}, operatorId={}", id, days, newExp, operatorId);
        fillRelatedInfo(appUser);
        return appUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser extendMemberByDuration(Long id, ExtendMemberDurationDTO body, Long operatorId) {
        if (body == null || !StringUtils.hasText(body.getUnit())) {
            throw new RuntimeException("单位不能为空");
        }
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = appUser.getMemberExpiresAt();
        if (base == null || !base.isAfter(now)) {
            base = now;
        }

        String unit = body.getUnit().trim().toUpperCase();
        Integer amountObj = body.getAmount();
        int amount = amountObj == null ? 0 : amountObj;

        LocalDateTime newExp = switch (unit) {
            case "PERMANENT" -> LocalDateTime.of(2099, 12, 31, 23, 59, 59);
            case "MINUTE" -> base.plusMinutes(requirePositiveAmount(amount, "分钟"));
            case "HOUR" -> base.plusHours(requirePositiveAmount(amount, "小时"));
            case "DAY" -> base.plusDays(requirePositiveAmount(amount, "天"));
            case "WEEK" -> base.plusWeeks(requirePositiveAmount(amount, "周"));
            case "MONTH" -> base.plusMonths(requirePositiveAmount(amount, "月"));
            case "YEAR" -> base.plusYears(requirePositiveAmount(amount, "年"));
            default -> throw new RuntimeException("不支持的单位: " + unit);
        };

        appUser.setMemberExpiresAt(newExp);
        appUser.setUpdatedAt(now);
        appUserMapper.updateById(appUser);
        log.info("按单位延长会员: userId={}, unit={}, amount={}, newExpiresAt={}, operatorId={}",
                id, unit, amountObj, newExp, operatorId);
        fillRelatedInfo(appUser);
        return appUser;
    }

    private int requirePositiveAmount(int amount, String label) {
        if (amount < 1) {
            throw new RuntimeException("延长" + label + "数至少为 1");
        }
        return amount;
    }

    private LocalDateTime plusByUnit(LocalDateTime base, String unit, int amount, boolean allowPermanent) {
        if (!StringUtils.hasText(unit)) {
            throw new RuntimeException("单位不能为空");
        }
        String u = unit.trim().toUpperCase();
        if ("PERMANENT".equals(u)) {
            if (!allowPermanent) {
                throw new RuntimeException("永久不支持");
            }
            return LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        }
        if (amount < 1) {
            throw new RuntimeException("数值至少为 1");
        }
        return switch (u) {
            case "MINUTE" -> base.plusMinutes(amount);
            case "HOUR" -> base.plusHours(amount);
            case "DAY" -> base.plusDays(amount);
            case "WEEK" -> base.plusWeeks(amount);
            case "MONTH" -> base.plusMonths(amount);
            case "YEAR" -> base.plusYears(amount);
            default -> throw new RuntimeException("不支持的单位: " + u);
        };
    }

    private LocalDateTime minusByUnit(LocalDateTime base, String unit, int amount) {
        if (!StringUtils.hasText(unit)) {
            throw new RuntimeException("单位不能为空");
        }
        String u = unit.trim().toUpperCase();
        if ("PERMANENT".equals(u)) {
            throw new RuntimeException("永久不支持");
        }
        if (amount < 1) {
            throw new RuntimeException("数值至少为 1");
        }
        return switch (u) {
            case "MINUTE" -> base.minusMinutes(amount);
            case "HOUR" -> base.minusHours(amount);
            case "DAY" -> base.minusDays(amount);
            case "WEEK" -> base.minusWeeks(amount);
            case "MONTH" -> base.minusMonths(amount);
            case "YEAR" -> base.minusYears(amount);
            default -> throw new RuntimeException("不支持的单位: " + u);
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser setMemberExpiresAt(Long id, LocalDateTime memberExpiresAt, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        appUser.setMemberExpiresAt(memberExpiresAt);
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        log.info("设置会员到期: userId={}, memberExpiresAt={}, operatorId={}", id, memberExpiresAt, operatorId);
        fillRelatedInfo(appUser);
        return appUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO batchExtendMemberDays(AppUserBatchExtendMemberDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        Integer days = dto.getDays();
        if (days == null || days < 1) {
            throw new RuntimeException("延长天数至少为 1");
        }
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) {
                continue;
            }
            try {
                AppUser appUser = appUserMapper.selectById(id);
                if (appUser == null || appUser.getDeleted() == 1) {
                    result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, null, "用户不存在"));
                    continue;
                }
                ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
                LocalDateTime base = appUser.getMemberExpiresAt();
                if (base == null || !base.isAfter(now)) {
                    base = now;
                }
                LocalDateTime newExp = base.plus(days, ChronoUnit.DAYS);
                appUser.setMemberExpiresAt(newExp);
                appUser.setUpdatedAt(now);
                appUserMapper.updateById(appUser);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                    // ignore
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        log.info("终端用户批量延长会员: operatorId={}, days={}, success={}, fail={}",
                operatorId, days, success, result.getFailCount());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO batchExtendMemberDuration(AppUserBatchExtendMemberDurationDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        if (!StringUtils.hasText(dto.getUnit())) {
            throw new RuntimeException("单位不能为空");
        }
        String unit = dto.getUnit().trim().toUpperCase();
        Integer amountObj = dto.getAmount();
        int amount = amountObj == null ? 0 : amountObj;
        if (!"PERMANENT".equals(unit) && amount < 1) {
            throw new RuntimeException("数值至少为 1");
        }

        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) {
                continue;
            }
            try {
                AppUser appUser = appUserMapper.selectById(id);
                if (appUser == null || appUser.getDeleted() == 1) {
                    result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, null, "用户不存在"));
                    continue;
                }
                ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");

                LocalDateTime base = appUser.getMemberExpiresAt();
                if (base == null || !base.isAfter(now)) {
                    base = now;
                }
                LocalDateTime newExp = switch (unit) {
                    case "PERMANENT" -> LocalDateTime.of(2099, 12, 31, 23, 59, 59);
                    case "MINUTE" -> base.plusMinutes(amount);
                    case "HOUR" -> base.plusHours(amount);
                    case "DAY" -> base.plusDays(amount);
                    case "WEEK" -> base.plusWeeks(amount);
                    case "MONTH" -> base.plusMonths(amount);
                    case "YEAR" -> base.plusYears(amount);
                    default -> throw new RuntimeException("不支持的单位: " + unit);
                };
                appUser.setMemberExpiresAt(newExp);
                appUser.setUpdatedAt(now);
                appUserMapper.updateById(appUser);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        log.info("终端用户批量按单位延长会员: operatorId={}, unit={}, amount={}, success={}, fail={}",
                operatorId, unit, amountObj, success, result.getFailCount());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO batchSubtractMemberDuration(AppUserBatchExtendMemberDurationDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        if (!StringUtils.hasText(dto.getUnit())) {
            throw new RuntimeException("单位不能为空");
        }
        Integer amountObj = dto.getAmount();
        int amount = amountObj == null ? 0 : amountObj;
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) continue;
            try {
                AppUser appUser = appUserMapper.selectById(id);
                if (appUser == null || appUser.getDeleted() == 1) {
                    result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, null, "用户不存在"));
                    continue;
                }
                ensureAppUserRecordPermission(appUser, operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
                LocalDateTime exp = appUser.getMemberExpiresAt();
                if (exp == null) {
                    result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, appUser.getUsername(), "该用户未开通会员"));
                    continue;
                }
                LocalDateTime newExp = minusByUnit(exp, dto.getUnit(), amount);
                appUser.setMemberExpiresAt(newExp);
                appUser.setUpdatedAt(now);
                appUserMapper.updateById(appUser);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    @Override
    public AppUserBatchExtendMemberResultDTO batchKickWs(AppUserBatchIdsDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) continue;
            try {
                kickWs(id, operatorId);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO batchBan(AppUserBatchBanDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) continue;
            try {
                banUser(id, null, dto.getBan(), dto.getReason(), operatorId);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO batchDelete(AppUserBatchIdsDTO dto, Long operatorId) {
        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条终端用户");
        }
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) continue;
            try {
                deleteAppUser(id, operatorId);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                String uname = null;
                try {
                    AppUser u = appUserMapper.selectById(id);
                    uname = u != null ? u.getUsername() : null;
                } catch (Exception ignored) {
                }
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(id, uname, reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO extendNotExpiredInApp(AppUserAppNotExpiredDurationDTO dto, Long operatorId) {
        if (dto == null || dto.getAppId() == null) {
            throw new RuntimeException("appId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ensureAppUserListPermission(dto.getAppId(), operatorId, "无权限操作此应用的终端用户");

        int amount = dto.getAmount() == null ? 0 : dto.getAmount();
        if (amount < 1) {
            throw new RuntimeException("数值至少为 1");
        }

        List<AppUser> list = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, dto.getAppId())
                .eq(AppUser::getDeleted, 0)
                .gt(AppUser::getMemberExpiresAt, now)
                .select(AppUser::getId, AppUser::getUsername, AppUser::getMemberExpiresAt));

        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        int success = 0;
        for (AppUser u : list) {
            if (u == null || u.getId() == null) continue;
            try {
                LocalDateTime exp = u.getMemberExpiresAt();
                if (exp == null || !exp.isAfter(now)) continue;
                LocalDateTime newExp = plusByUnit(exp, dto.getUnit(), amount, false);
                u.setMemberExpiresAt(newExp);
                u.setUpdatedAt(now);
                appUserMapper.updateById(u);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(u.getId(), u.getUsername(), reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserBatchExtendMemberResultDTO subtractNotExpiredInApp(AppUserAppNotExpiredDurationDTO dto, Long operatorId) {
        if (dto == null || dto.getAppId() == null) {
            throw new RuntimeException("appId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ensureAppUserListPermission(dto.getAppId(), operatorId, "无权限操作此应用的终端用户");

        int amount = dto.getAmount() == null ? 0 : dto.getAmount();
        if (amount < 1) {
            throw new RuntimeException("数值至少为 1");
        }

        List<AppUser> list = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, dto.getAppId())
                .eq(AppUser::getDeleted, 0)
                .gt(AppUser::getMemberExpiresAt, now)
                .select(AppUser::getId, AppUser::getUsername, AppUser::getMemberExpiresAt));

        AppUserBatchExtendMemberResultDTO result = new AppUserBatchExtendMemberResultDTO();
        result.setFailures(new ArrayList<>());
        int success = 0;
        for (AppUser u : list) {
            if (u == null || u.getId() == null) continue;
            try {
                LocalDateTime exp = u.getMemberExpiresAt();
                if (exp == null || !exp.isAfter(now)) continue;
                LocalDateTime newExp = minusByUnit(exp, dto.getUnit(), amount);
                u.setMemberExpiresAt(newExp);
                u.setUpdatedAt(now);
                appUserMapper.updateById(u);
                success++;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : "操作失败";
                result.getFailures().add(new AppUserBatchExtendMemberFailItem(u.getId(), u.getUsername(), reason));
            }
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        return result;
    }

    /**
     * 记录级权限校验：非管理员/非应用 owner 的代理，若不具备全量查看权限，则只能操作归属自身代理的终端用户。
     */
    private void ensureAppUserRecordPermission(AppUser appUser, Long operatorId, String permissionCode, String message) {
        if (appUser == null || appUser.getAppId() == null) {
            throw new RuntimeException(message);
        }
        AppAgent agent = ensureAppUserPermission(appUser.getAppId(), operatorId, permissionCode, message);
        if (agent == null) {
            // admin / owner
            return;
        }
        boolean viewAll = agentAuthorizationService.isScopeAllInApp(agent)
                || hasAgentPermission(agent, AgentPermissionCodes.APP_USER_VIEW_ALL);
        if (viewAll) {
            return;
        }
        // 仅允许操作该 agent 自己创建/归属的用户
        if (appUser.getAgentId() == null || !appUser.getAgentId().equals(agent.getId())) {
            throw new RuntimeException(message);
        }
    }
}
