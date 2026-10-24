-- A redemption permanently consumes a license; the ledger survives application/user soft deletion.
ALTER TABLE license_key ADD COLUMN redeemed_at DATETIME NULL COMMENT '兑换为终端用户会员的时间';
CREATE TABLE app_user_license_recharge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_key_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    app_user_id BIGINT NOT NULL,
    key_code_masked VARCHAR(32) NOT NULL,
    duration_value INT NOT NULL,
    duration_unit VARCHAR(20) NOT NULL,
    before_expires_at DATETIME NULL,
    after_expires_at DATETIME NOT NULL,
    request_ip VARCHAR(45) NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_license_recharge_license (license_key_id),
    KEY idx_license_recharge_user (app_id, app_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
