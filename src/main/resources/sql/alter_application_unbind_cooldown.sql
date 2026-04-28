-- 应用配置：新增解绑冷却时间（小时）
ALTER TABLE `application`
    ADD COLUMN IF NOT EXISTS `unbind_cooldown_hours` INT NOT NULL DEFAULT 0 COMMENT '解绑冷却时间（小时）；0=不限制'
    AFTER `unbind_time_deduct_value`;

