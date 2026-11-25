package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.portal.dto.PortalPricingPlanRequest;
import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
import com.ayssu.ciphergate.portal.mapper.PortalPricingPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PortalPricingPlanService {
    private final PortalPricingPlanMapper plans;
    private final PortalPaymentOrderMapper orders;

    private void validate(PortalPricingPlanRequest input) {
        if (input.getPriceFen() == null || input.getPriceFen() <= 0
                || input.getOriginalPriceFen() != null && input.getOriginalPriceFen() < input.getPriceFen()
                || input.getStockRemaining() != null && input.getStockRemaining() < 0) {
            throw new IllegalArgumentException("现价必须大于0，原价不能低于现价，库存不能小于0");
        }
        if (!"PERMANENT".equals(input.getPlanType())
                && (input.getDurationDays() == null || input.getDurationDays() <= 0)) {
            throw new IllegalArgumentException("非永久方案必须设置有效天数");
        }
    }

    @Transactional
    public void create(Long appId, PortalPricingPlanRequest input) {
        validate(input);
        PortalPricingPlan plan = new PortalPricingPlan();
        plan.setAppId(appId);
        plan.setPlanName(input.getPlanName());
        plan.setPlanType(input.getPlanType());
        plan.setDurationDays(input.getDurationDays());
        plan.setPriceFen(input.getPriceFen());
        plan.setOriginalPriceFen(input.getOriginalPriceFen());
        plan.setStockRemaining(input.getStockRemaining());
        plan.setRevision(0L);
        plan.setSortOrder(input.getSortOrder() == null ? 0 : input.getSortOrder());
        plan.setEnabled(input.getEnabled() == null || input.getEnabled());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        if (plans.insert(plan) != 1) throw new IllegalStateException("方案保存失败");
    }

    @Transactional
    public void update(Long appId, Long id, PortalPricingPlanRequest input) {
        validate(input);
        PortalPricingPlan plan = requirePlan(appId, id);
        if (input.getRevision() == null || !input.getRevision().equals(plan.getRevision())) {
            throw new IllegalArgumentException("方案或库存已变化，请刷新列表后重新编辑");
        }
        if (input.getStockRemaining() == null && plan.getStockRemaining() != null && hasReservations(id)) {
            throw new IllegalArgumentException("尚有占用库存的待支付订单，请处理后再切换为不限量");
        }
        int changed = plans.update(null, new LambdaUpdateWrapper<PortalPricingPlan>()
            .eq(PortalPricingPlan::getId, id).eq(PortalPricingPlan::getRevision, input.getRevision())
            .set(PortalPricingPlan::getPlanName, input.getPlanName())
            .set(PortalPricingPlan::getPlanType, input.getPlanType())
            .set(PortalPricingPlan::getDurationDays, input.getDurationDays())
            .set(PortalPricingPlan::getPriceFen, input.getPriceFen())
            .set(PortalPricingPlan::getOriginalPriceFen, input.getOriginalPriceFen())
            .set(PortalPricingPlan::getStockRemaining, input.getStockRemaining())
            .set(PortalPricingPlan::getEnabled, input.getEnabled() == null ? plan.getEnabled() : input.getEnabled())
            .set(PortalPricingPlan::getSortOrder, input.getSortOrder() == null ? plan.getSortOrder() : input.getSortOrder())
            .set(PortalPricingPlan::getRevision, Math.addExact(plan.getRevision(), 1L))
            .set(PortalPricingPlan::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("方案更新失败");
    }

    @Transactional
    public void delete(Long appId, Long id) {
        requirePlan(appId, id);
        if (hasReservations(id)) throw new IllegalArgumentException("方案尚有占用库存的待支付订单，请先停用，待订单处理后删除");
        if (plans.deleteById(id) != 1) throw new IllegalStateException("方案删除失败");
    }

    private PortalPricingPlan requirePlan(Long appId, Long id) {
        PortalPricingPlan plan = plans.selectForUpdate(id);
        if (plan == null || !appId.equals(plan.getAppId())) throw new IllegalArgumentException("方案不存在");
        return plan;
    }

    private boolean hasReservations(Long id) {
        return orders.selectCount(new LambdaQueryWrapper<PortalPaymentOrder>()
            .eq(PortalPaymentOrder::getPlanId, id).eq(PortalPaymentOrder::getStockReserved, true)) > 0;
    }
}
