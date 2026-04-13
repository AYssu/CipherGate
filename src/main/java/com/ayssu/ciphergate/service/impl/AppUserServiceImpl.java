package com.ayssu.ciphergate.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.agent.AgentAuthorizationService;
import com.ayssu.ciphergate.agent.AgentPermissionCodes;
import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.entity.AppAgent;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.AppAgentMapper;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppUserService;
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
    private final ApplicationMapper applicationMapper;
    private final AppAgentMapper appAgentMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final AgentAuthorizationService agentAuthorizationService;
    private final SystemMessageService systemMessageService;
    private final AppUserWsPresenceRegistry appUserWsPresenceRegistry;
    private final AppUserWsSessionKickService appUserWsSessionKickService;
    
    @Override
    public Page<AppUser> getAppUserPage(AppUserQueryDTO queryDTO, Long operatorId) {
        Page<AppUser> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        log.info("终端用户列表查询开始: operatorId={}, appId={}, username={}, email={}, phone={}",
                operatorId, queryDTO.getAppId(), queryDTO.getUsername(), queryDTO.getEmail(), queryDTO.getPhone());

        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        applyApplicationScopeForAppUserQuery(wrapper, queryDTO, operatorId);
        wrapper.like(StringUtils.hasText(queryDTO.getUsername()), AppUser::getUsername, queryDTO.getUsername())
               .like(StringUtils.hasText(queryDTO.getEmail()), AppUser::getEmail, queryDTO.getEmail())
               .like(StringUtils.hasText(queryDTO.getPhone()), AppUser::getPhone, queryDTO.getPhone())
               .like(StringUtils.hasText(queryDTO.getNickname()), AppUser::getNickname, queryDTO.getNickname())
               .eq(AppUser::getDeleted, 0)
               .orderByDesc(AppUser::getCreatedAt);
        
        Page<AppUser> result = appUserMapper.selectPage(page, wrapper);
        log.info("终端用户列表查询完成: operatorId={}, total={}, records={}",
                operatorId, result.getTotal(), result.getRecords() == null ? 0 : result.getRecords().size());
        
        // 填充关联信息
        result.getRecords().forEach(this::fillRelatedInfo);
        
        return result;
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
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
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
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
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
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
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
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        
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

        enrichMemberStatus(appUser);
        enrichWsPresence(appUser);
    }

    private void enrichMemberStatus(AppUser appUser) {
        LocalDateTime me = appUser.getMemberExpiresAt();
        appUser.setMemberActive(me != null && me.isAfter(LocalDateTime.now()));
    }

    private void enrichWsPresence(AppUser appUser) {
        if (appUser == null || appUser.getId() == null) {
            return;
        }
        AppUserWsPresenceRegistry.PresenceSnapshot snap = appUserWsPresenceRegistry.snapshot(appUser.getId());
        appUser.setWsOnline(snap.isOnline());
        appUser.setWsSessionCount(snap.getSessionCount());
        if (snap.isOnline()) {
            appUser.setWsEarliestConnectedAtEpochMs(snap.getEarliestConnectedAtEpochMs());
            long sec = (System.currentTimeMillis() - snap.getEarliestConnectedAtEpochMs()) / 1000L;
            appUser.setWsOnlineSeconds(Math.max(0L, sec));
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
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
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
    public AppUser setMemberExpiresAt(Long id, LocalDateTime memberExpiresAt, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        ensureAppUserPermission(appUser.getAppId(), operatorId, AgentPermissionCodes.APP_USER_UPDATE, "无权限操作此用户");
        appUser.setMemberExpiresAt(memberExpiresAt);
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        log.info("设置会员到期: userId={}, memberExpiresAt={}, operatorId={}", id, memberExpiresAt, operatorId);
        fillRelatedInfo(appUser);
        return appUser;
    }
}
