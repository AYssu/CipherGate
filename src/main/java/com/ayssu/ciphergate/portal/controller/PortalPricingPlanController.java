package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.ayssu.ciphergate.portal.mapper.PortalPricingPlanMapper;
import com.ayssu.ciphergate.util.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/applications/{appId}/pricing-plans")
@Tag(name = "价格方案管理", description = "应用价格方案CRUD")
@RequiredArgsConstructor
public class PortalPricingPlanController {

    private final PortalPricingPlanMapper planMapper;
    private final ApplicationMapper applicationMapper;

    @GetMapping
    @Operation(summary = "获取价格方案列表")
    public Result<List<PortalPricingPlan>> list(@PathVariable Long appId) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) return Result.error(401, "未登录");
        checkPermission(appId, user);
        List<PortalPricingPlan> plans = planMapper.selectList(
            new LambdaQueryWrapper<PortalPricingPlan>()
                .eq(PortalPricingPlan::getAppId, appId)
                .orderByAsc(PortalPricingPlan::getSortOrder)
        );
        return Result.success(plans);
    }

    @PostMapping
    @Operation(summary = "创建价格方案")
    public Result<Void> create(@PathVariable Long appId, @RequestBody PortalPricingPlan plan) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) return Result.error(401, "未登录");
        checkPermission(appId, user);
        plan.setAppId(appId);
        if (plan.getEnabled() == null) plan.setEnabled(true);
        if (plan.getSortOrder() == null) plan.setSortOrder(0);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新价格方案")
    public Result<Void> update(@PathVariable Long appId, @PathVariable Long id, @RequestBody PortalPricingPlan plan) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) return Result.error(401, "未登录");
        checkPermission(appId, user);
        PortalPricingPlan existing = planMapper.selectById(id);
        if (existing == null || !existing.getAppId().equals(appId)) {
            return Result.badRequest("方案不存在");
        }
        plan.setId(id);
        plan.setAppId(appId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除价格方案")
    public Result<Void> delete(@PathVariable Long appId, @PathVariable Long id) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) return Result.error(401, "未登录");
        checkPermission(appId, user);
        PortalPricingPlan existing = planMapper.selectById(id);
        if (existing == null || !existing.getAppId().equals(appId)) {
            return Result.badRequest("方案不存在");
        }
        planMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    private void checkPermission(Long appId, User user) {
        Application app = applicationMapper.selectById(appId);
        if (app == null) throw new IllegalArgumentException("应用不存在");
        boolean isOwner = user.getId().equals(app.getOwnerId());
        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
            .anyMatch(r -> "SUPER_ADMIN".equals(r.getRoleCode()));
        if (!isOwner && !isAdmin) throw new IllegalArgumentException("无权限操作此应用");
    }
}
