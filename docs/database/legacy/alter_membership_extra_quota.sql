-- 为 user_membership 添加额外额度字段（购买的额度）
-- 拆分为独立语句，兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本
ALTER TABLE user_membership
    ADD COLUMN extra_app_quota INT NOT NULL DEFAULT 0 COMMENT '额外应用额度（购买）' AFTER traffic_used;

ALTER TABLE user_membership
    ADD COLUMN extra_license_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外卡密额度（购买）' AFTER extra_app_quota;

ALTER TABLE user_membership
    ADD COLUMN extra_user_register_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外用户注册额度（购买）' AFTER extra_license_quota;

ALTER TABLE user_membership
    ADD COLUMN extra_traffic_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外流量额度（字节，购买）' AFTER extra_user_register_quota;
