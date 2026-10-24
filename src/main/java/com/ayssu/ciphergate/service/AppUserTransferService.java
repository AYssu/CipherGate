package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.transfer.AppUserTransferRequest;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferUserQuery;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferBatch;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferResult;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferResult.*;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsSessionKickService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/** 应用所有者/管理员的高级操作。目标新建或复用自己的用户 ID，源历史记录不跨应用改写。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserTransferService {
    private final ApplicationMapper applications;
    private final AppUserMapper users;
    private final AppUserBindingMapper bindings;
    private final AppUserTrialMapper trials;
    private final AppUserTransferMapper transfer;
    private final SecurityUtils security;
    private final UserMembershipService membership;
    private final AppUserWsSessionKickService kicks;

    public Page<ApplicationOption> applicationOptions(Long operatorId, long current, long size, String keyword) {
        validatePage(current, size);
        var query = new LambdaQueryWrapper<Application>()
                .select(Application::getId, Application::getAppName, Application::getOwnerId)
                .orderByAsc(Application::getId);
        if (!security.isAdmin(operatorId)) query.eq(Application::getOwnerId, operatorId);
        if (StringUtils.hasText(keyword)) query.like(Application::getAppName, keyword.trim());
        Page<Application> page = applications.selectPage(new Page<>(current, size), query);
        Page<ApplicationOption> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(a -> new ApplicationOption(a.getId(), a.getAppName(), a.getOwnerId())).toList());
        return result;
    }

    public Page<UserRow> sourceUsers(Long appId, Long operatorId, long current, long size, String keyword) {
        return sourceUsers(appId, operatorId, current, size, keyword, new AppUserTransferUserQuery());
    }

    public Page<UserRow> sourceUsers(Long appId, Long operatorId, long current, long size, String keyword,
                                     AppUserTransferUserQuery filters) {
        validatePage(current, size);
        requireApp(appId, operatorId);
        List<Long> ids = filters.getUserIds();
        if (ids != null && (ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))) {
            throw new IllegalArgumentException("已选用户必须为 1 到 100 个有效 ID");
        }
        if (StringUtils.hasText(filters.getMemberStatus())
                && !Set.of("ACTIVE", "EXPIRED", "NONE").contains(filters.getMemberStatus())) {
            throw new IllegalArgumentException("会员状态筛选无效");
        }
        var query = new LambdaQueryWrapper<AppUser>()
                .select(AppUser::getId, AppUser::getUsername, AppUser::getEmail, AppUser::getNickname, AppUser::getMemberExpiresAt)
                .eq(AppUser::getAppId, appId).orderByAsc(AppUser::getId);
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like(AppUser::getUsername, keyword.trim()).or().like(AppUser::getEmail, keyword.trim()));
        }
        if (StringUtils.hasText(filters.getUsername())) query.like(AppUser::getUsername, filters.getUsername().trim());
        if (StringUtils.hasText(filters.getNickname())) query.like(AppUser::getNickname, filters.getNickname().trim());
        if (StringUtils.hasText(filters.getEmail())) query.like(AppUser::getEmail, filters.getEmail().trim());
        if (StringUtils.hasText(filters.getPhone())) query.like(AppUser::getPhone, filters.getPhone().trim());
        LocalDateTime now = LocalDateTime.now();
        if ("ACTIVE".equals(filters.getMemberStatus())) query.gt(AppUser::getMemberExpiresAt, now);
        if ("EXPIRED".equals(filters.getMemberStatus())) query.le(AppUser::getMemberExpiresAt, now);
        if ("NONE".equals(filters.getMemberStatus())) query.isNull(AppUser::getMemberExpiresAt);
        if (ids != null) query.in(AppUser::getId, ids);
        Page<AppUser> page = users.selectPage(new Page<>(current, size), query);
        Page<UserRow> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(u -> new UserRow(u.getId(), u.getUsername(), u.getEmail(), u.getNickname(), u.getMemberExpiresAt())).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public AppUserTransferResult preview(AppUserTransferRequest request, Long operatorId) {
        Plan plan = buildPlan(request, operatorId, false);
        return result(plan.source, plan.target, UUID.randomUUID() + ":" + plan.token, plan.items, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppUserTransferResult execute(AppUserTransferRequest request, Long operatorId) {
        if (!StringUtils.hasText(request.getPreviewToken())) throw new IllegalArgumentException("请先预检查后再执行");
        String[] parts = request.getPreviewToken().split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !parts[1].matches("[0-9a-f]{64}")) throw new IllegalArgumentException("预检查凭证无效，请重新预检查");
        Plan plan = buildPlan(request, operatorId, true);
        if (!MessageDigest.isEqual(plan.token.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("用户、目标冲突或相关配置已变化，请重新预检查；切勿重复提交");
        }
        if (plan.items.stream().anyMatch(i -> i.action() == Action.BLOCK)) {
            throw new IllegalArgumentException("存在不可处理的用户，请取消勾选后重新预检查");
        }
        AppUserTransferResult checked = plan.result(false);
        try {
            if (transfer.claimBatch(new AppUserTransferBatch(parts[0], operatorId, request.getSourceAppId(), request.getTargetAppId(),
                    request.getMode().name(), request.isForceOverwrite(), checked.createCount(), checked.overwriteCount() + checked.restoreCount(),
                    checked.skipCount(), request.getUserIds().toString())) != 1) throw new IllegalStateException("登记迁移批次失败");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("该批次已执行，请刷新查看结果，不要重复提交");
        }
        Set<Long> kickIds = new LinkedHashSet<>();
        List<Item> results = new ArrayList<>();
        int created = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Entry entry : plan.entries) {
            Item item = entry.item;
            if (item.action() == Action.SKIP) { results.add(item); continue; }
            AppUser destination = copyAccount(entry.source, request.getTargetAppId(), now);
            if (item.action() == Action.OVERWRITE || item.action() == Action.RESTORE) {
                destination.setId(entry.target.getId());
                int affected = item.action() == Action.RESTORE ? transfer.restore(destination) : transfer.overwrite(destination);
                if (affected != 1) throw new IllegalStateException("目标用户状态已变化");
                if (item.action() == Action.RESTORE) created++; // 恢复也增加有效用户数，不能绕过配额。
                // 覆盖只更新账号配置；目标归属、设备限制和历史统计不被源用户覆盖。
                kickIds.add(destination.getId());
            } else {
                if (users.insert(destination) != 1) throw new IllegalStateException("创建目标用户失败");
                created++;
            }
            mergeDeviceRestrictions(entry, destination, now);
            mergeTrial(entry, destination);
            if (request.getMode() == AppUserTransferRequest.Mode.MOVE) {
                // 只停用源账号，订单/卡密/历史设备仍留在原应用供审计。
                if (users.deleteById(entry.source.getId()) != 1) throw new IllegalStateException("源用户状态已变化");
                kickIds.add(entry.source.getId());
            }
            results.add(new Item(item.sourceUserId(), item.username(), destination.getId(), destination.getUsername(),
                    item.action(), "处理成功", item.deviceCount(), item.bannedDeviceCount(), item.trialRecorded()));
        }
        boolean sameOwnerMove = request.getMode() == AppUserTransferRequest.Mode.MOVE
                && Objects.equals(plan.source.getOwnerId(), plan.target.getOwnerId());
        if (created > 0 && !sameOwnerMove) membership.consumeUserRegisterQuota(plan.target.getOwnerId(), created);
        // 不在事务回滚前踢人；数据库提交失败时原会话不受影响。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for (Long id : kickIds) {
                    try { kicks.kickByAppUserId(id, null, new CloseStatus(1008, "ACCOUNT_TRANSFERRED")); }
                    catch (Exception e) { log.warn("迁移提交后断开会话失败: userId={}", id); }
                }
                log.info("终端用户迁移完成: operatorId={}, sourceAppId={}, targetAppId={}, mode={}, force={}, results={}",
                        operatorId, request.getSourceAppId(), request.getTargetAppId(), request.getMode(),
                        request.isForceOverwrite(), results.stream().map(i -> i.sourceUserId() + ":" + i.action() + ":" + i.targetUserId()).toList());
            }
        });
        return result(plan.source, plan.target, request.getPreviewToken(), results, true);
    }

    private Plan buildPlan(AppUserTransferRequest request, Long operatorId, boolean lock) {
        validateRequest(request);
        Application source = requireApp(request.getSourceAppId(), operatorId);
        Application target = requireApp(request.getTargetAppId(), operatorId);
        if (lock) {
            // 同类批次按相同顺序锁所有者/应用，避免双向迁移互相死锁。
            for (Long owner : new TreeSet<>(List.of(source.getOwnerId(), target.getOwnerId()))) {
                if (transfer.lockOwner(owner) == null) throw new IllegalArgumentException("应用所有者不存在");
            }
            Map<Long, Application> locked = new HashMap<>();
            for (Long appId : new TreeSet<>(List.of(source.getId(), target.getId()))) {
                Application app = transfer.lockApplication(appId);
                ensureOwner(app, operatorId);
                locked.put(appId, app);
            }
            source = locked.get(source.getId()); target = locked.get(target.getId());
        }
        List<Long> ids = request.getUserIds().stream().sorted().toList();
        Map<Long, AppUser> selected = new HashMap<>();
        transfer.selectedUsers(source.getId(), ids, lock).forEach(u -> selected.put(u.getId(), u));
        List<Entry> entries = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        // 内容快照只用于 SHA-256，不落日志、不传输密码/摘要原文。
        StringBuilder snapshot = new StringBuilder();
        append(snapshot, operatorId, source, target, request.getMode(), request.isForceOverwrite(), ids);
        Set<Long> claimedTargets = new HashSet<>();
        for (Long id : ids) {
            AppUser user = selected.get(id);
            if (user == null) {
                Item missing = new Item(id, "", null, null, Action.BLOCK, "用户已删除或不属于源应用", 0, 0, false);
                items.add(missing); append(snapshot, missing); continue;
            }
            List<AppUser> sourceConflicts = transfer.conflicts(source.getId(), user.getUsername(), user.getEmail(), true, lock);
            List<AppUser> conflicts = transfer.conflicts(target.getId(), user.getUsername(), user.getEmail(), false, lock);
            List<AppUserBinding> sourceBindings = transfer.bindings(source.getId(), id, lock).stream()
                    .filter(b -> Objects.equals(b.getDeleted(), 0)).toList();
            AppUserTrial sourceTrial = transfer.trial(source.getId(), id, lock);
            AppUser match = conflicts.size() == 1 ? conflicts.get(0) : null;
            List<AppUserBinding> targetBindings = match == null ? List.of() : transfer.bindings(target.getId(), match.getId(), lock);
            AppUserTrial targetTrial = match == null ? null : transfer.trial(target.getId(), match.getId(), lock);
            Action action = Action.CREATE;
            String reason = "创建目标账号（保留密码及会员到期时间）";
            if (sourceConflicts.size() > 1) {
                action = Action.BLOCK; reason = "源账号用户名/邮箱存在歧义，需先修正";
            } else if (conflicts.size() > 1) {
                action = Action.BLOCK; reason = "用户名/邮箱匹配多个目标账号，强制覆盖也不允许";
            } else if (match != null && Objects.equals(match.getDeleted(), 1)) {
                if (!sameIdentity(user, match)) {
                    action = Action.BLOCK; reason = "用户名与目标邮箱交叉冲突，不允许恢复不同身份";
                } else {
                    action = Action.RESTORE; reason = "恢复已删除目标账号，更新密码、资料及会员到期时间；保留原 ID、历史与封禁，无需强制覆盖";
                }
            } else if (match != null && !Objects.equals(match.getDeleted(), 0)) {
                action = Action.BLOCK; reason = "目标账号删除状态异常，请先修正";
            } else if (match != null && !request.isForceOverwrite()) {
                action = Action.SKIP; reason = "目标账号冲突，未开启强制覆盖";
            } else if (match != null && !sameIdentity(user, match)) {
                action = Action.BLOCK; reason = "用户名与目标邮箱交叉冲突，不允许覆盖不同身份";
            } else if (match != null) {
                action = Action.OVERWRITE; reason = "覆盖目标密码、资料及会员到期时间；保留目标历史与封禁";
            }
            if ((action == Action.OVERWRITE || action == Action.RESTORE) && !claimedTargets.add(match.getId())) {
                action = Action.BLOCK; reason = "本批次多个源用户匹配同一目标账号";
            }
            int bannedCount = (int) sourceBindings.stream().filter(AppUserTransferService::isBanned).count();
            Item item = new Item(id, user.getUsername(), match == null ? null : match.getId(),
                    match == null ? null : match.getUsername(), action, reason, sourceBindings.size(), bannedCount, sourceTrial != null);
            Entry entry = new Entry(user, match, sourceBindings, sourceTrial, targetTrial, item);
            entries.add(entry); items.add(item);
            append(snapshot, user, sourceConflicts, conflicts, sourceBindings, sourceTrial, targetBindings, targetTrial, item);
        }
        int createCount = (int) items.stream().filter(i -> i.action() == Action.CREATE || i.action() == Action.RESTORE).count();
        // 配额按现存有效用户计算。同所有者 MOVE 不增加总用户数，不能在满额时阻止正常迁移。
        boolean sameOwnerMove = request.getMode() == AppUserTransferRequest.Mode.MOVE
                && Objects.equals(source.getOwnerId(), target.getOwnerId());
        if (createCount > 0 && !sameOwnerMove && !membership.checkUserRegisterQuota(target.getOwnerId(), createCount)) {
            throw new IllegalArgumentException("目标应用所有者的用户配额不足，需新增或恢复 " + createCount + " 个账号");
        }
        return new Plan(source, target, digest(snapshot.toString()), entries, items);
    }

    private static boolean sameIdentity(AppUser source, AppUser target) {
        return source.getUsername().equalsIgnoreCase(target.getUsername())
                || (StringUtils.hasText(source.getEmail()) && source.getEmail().equalsIgnoreCase(target.getEmail()));
    }

    private Application requireApp(Long appId, Long operatorId) {
        Application app = applications.selectById(appId);
        ensureOwner(app, operatorId);
        return app;
    }

    private void ensureOwner(Application app, Long operatorId) {
        if (app == null) throw new IllegalArgumentException("应用不存在");
        if (operatorId == null || (!Objects.equals(app.getOwnerId(), operatorId) && !security.isAdmin(operatorId))) {
            throw new SecurityException("仅应用所有者或管理员可迁移用户，必须同时有权操作源和目标应用");
        }
    }

    private static void validatePage(long current, long size) {
        if (current < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页大小必须为 1–100");
    }

    private static void validateRequest(AppUserTransferRequest request) {
        if (request == null || request.getSourceAppId() == null || request.getTargetAppId() == null
                || request.getSourceAppId() <= 0 || request.getTargetAppId() <= 0 || request.getMode() == null) {
            throw new IllegalArgumentException("请选择有效的源应用、目标应用和操作类型");
        }
        if (request.getSourceAppId().equals(request.getTargetAppId())) throw new IllegalArgumentException("源和目标应用不能相同");
        if (request.getUserIds() == null || request.getUserIds().isEmpty() || request.getUserIds().size() > 100
                || request.getUserIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.getUserIds()).size() != request.getUserIds().size()) {
            throw new IllegalArgumentException("请选择 1–100 个不同的源用户");
        }
    }

    static AppUser copyAccount(AppUser source, Long appId, LocalDateTime now) {
        AppUser user = new AppUser();
        user.setAppId(appId); user.setUsername(source.getUsername()); user.setEmail(source.getEmail());
        user.setPhone(source.getPhone()); user.setPassword(source.getPassword()); user.setNickname(source.getNickname());
        user.setAvatarUrl(source.getAvatarUrl()); user.setSignature(source.getSignature());
        user.setMemberExpiresAt(source.getMemberExpiresAt());
        // 不复制代理归属、登录记录或会话，不能继承源应用代理的跨应用权限。
        user.setLoginCount(0); user.setCreatedAt(now); user.setUpdatedAt(now); user.setDeleted(0);
        return user;
    }

    private void mergeDeviceRestrictions(Entry entry, AppUser destination, LocalDateTime now) {
        for (AppUserBinding source : entry.sourceBindings) {
            AppUserBinding existing = transfer.deviceBinding(destination.getAppId(), destination.getId(), source.getDeviceId());
            if (existing != null) {
                // 不删除目标设备；封禁取并集，正常源记录绝不能解除目标封禁。
                if (isBanned(source) && (!isBanned(existing) || Objects.equals(existing.getDeleted(), 1))) {
                    if (transfer.retainBan(existing.getId(), source.getBanReason(), source.getBanAt(), now) != 1)
                        throw new IllegalStateException("保留目标设备封禁失败");
                }
                continue;
            }
            AppUserBinding copy = new AppUserBinding();
            copy.setAppId(destination.getAppId()); copy.setUserId(destination.getId()); copy.setBindType("ACCOUNT");
            copy.setDeviceId(source.getDeviceId()); copy.setDeviceName(source.getDeviceName()); copy.setDeviceOs(source.getDeviceOs());
            copy.setFirstBindAt(source.getFirstBindAt()); copy.setUseCount(0); copy.setUnbindCount(source.getUnbindCount());
            copy.setAllowUnbind(source.getAllowUnbind()); copy.setIsTrial(false); copy.setIsBanned(isBanned(source));
            copy.setBanReason(source.getBanReason()); copy.setBanAt(source.getBanAt());
            copy.setStatus(isBanned(source) ? 3 : (Objects.equals(source.getStatus(), 4) ? 4 : 1));
            copy.setCreatedAt(now); copy.setUpdatedAt(now); copy.setDeleted(0);
            // 不移植 LICENSE/VIP/TRIAL 的设备权益、卡密 ID、IP、近期活跃或扩展 metadata。
            if (bindings.insert(copy) != 1) throw new IllegalStateException("复制设备限制失败");
        }
    }

    private void mergeTrial(Entry entry, AppUser destination) {
        if (entry.sourceTrial == null) return;
        if (entry.targetTrial == null) {
            AppUserTrial copy = new AppUserTrial();
            copy.setAppId(destination.getAppId()); copy.setUserId(destination.getId());
            copy.setTrialStartedAt(entry.sourceTrial.getTrialStartedAt()); copy.setTrialExpiresAt(entry.sourceTrial.getTrialExpiresAt());
            copy.setDeviceId(entry.sourceTrial.getDeviceId()); copy.setCreatedAt(LocalDateTime.now());
            if (trials.insert(copy) != 1) throw new IllegalStateException("复制试用记录失败");
        } else {
            // 已领取试用不重新开始；到期时间只允许缩短，不允许覆盖延长。
            LocalDateTime expiry = entry.sourceTrial.getTrialExpiresAt();
            if (expiry == null || (entry.targetTrial.getTrialExpiresAt() != null && expiry.isBefore(entry.targetTrial.getTrialExpiresAt()))) {
                entry.targetTrial.setTrialExpiresAt(expiry);
                if (transfer.updateTrial(entry.targetTrial) != 1) throw new IllegalStateException("保留试用限制失败");
            }
        }
    }

    private static boolean isBanned(AppUserBinding b) {
        return Boolean.TRUE.equals(b.getIsBanned()) || Objects.equals(b.getStatus(), 3);
    }

    private static void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = String.valueOf(value);
            target.append(text.length()).append(':').append(text);
        }
    }

    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("快照摘要不可用", e); }
    }

    private static AppUserTransferResult result(Application source, Application target, String token, List<Item> items, boolean executed) {
        return new AppUserTransferResult(source.getAppName(), target.getAppName(), token,
                (int) items.stream().filter(i -> i.action() == Action.CREATE).count(),
                (int) items.stream().filter(i -> i.action() == Action.OVERWRITE).count(),
                (int) items.stream().filter(i -> i.action() == Action.RESTORE).count(),
                (int) items.stream().filter(i -> i.action() == Action.SKIP).count(),
                (int) items.stream().filter(i -> i.action() == Action.BLOCK).count(), executed, List.copyOf(items));
    }

    private record Entry(AppUser source, AppUser target, List<AppUserBinding> sourceBindings,
                         AppUserTrial sourceTrial, AppUserTrial targetTrial, Item item) {}
    private record Plan(Application source, Application target, String token, List<Entry> entries, List<Item> items) {
        AppUserTransferResult result(boolean executed) { return AppUserTransferService.result(source, target, token, items, executed); }
    }
}
