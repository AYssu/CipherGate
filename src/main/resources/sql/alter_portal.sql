-- 终端用户门户系统数据库迁移

-- 1. 门户支付订单表
CREATE TABLE IF NOT EXISTS portal_payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    app_user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    plan_id BIGINT,
    plan_name VARCHAR(100),
    duration_days INT,
    amount_fen BIGINT NOT NULL,
    payment_channel VARCHAR(20) DEFAULT 'alipay',
    trade_no VARCHAR(100),
    pay_url VARCHAR(500),
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待支付 1=已支付 2=已关闭 3=已退款',
    paid_at DATETIME,
    notify_received BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_app_user (app_user_id),
    INDEX idx_app (app_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 应用级易支付配置
CREATE TABLE IF NOT EXISTS application_epay_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id BIGINT NOT NULL,
    epay_url VARCHAR(255),
    epay_pid VARCHAR(100),
    epay_key VARCHAR(255),
    notify_url VARCHAR(500),
    return_url VARCHAR(500),
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_epay (app_id),
    INDEX idx_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 门户定价方案（应用创建者配置）
CREATE TABLE IF NOT EXISTS portal_pricing_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id BIGINT NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    plan_type VARCHAR(20) NOT NULL COMMENT 'DAY/PERMANENT',
    duration_days INT,
    price_fen BIGINT NOT NULL,
    sort_order INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_app (app_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 门户登录日志
CREATE TABLE IF NOT EXISTS portal_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    login_ip VARCHAR(50),
    ip_region VARCHAR(200),
    user_agent VARCHAR(500),
    device_info VARCHAR(200),
    login_type VARCHAR(20) DEFAULT 'PASSWORD',
    status VARCHAR(20) DEFAULT 'SUCCESS',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_user (app_user_id),
    INDEX idx_app (app_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 门户验证码记录
CREATE TABLE IF NOT EXISTS portal_verify_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    code VARCHAR(10) NOT NULL,
    purpose VARCHAR(30) NOT NULL COMMENT 'LOGIN/RECOVERY/EMAIL_CHANGE',
    used BOOLEAN DEFAULT FALSE,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_purpose (email, purpose),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. application 表新增字段
ALTER TABLE application
    ADD COLUMN portal_payment_enabled BOOLEAN DEFAULT FALSE AFTER unbind_cooldown_hours,
    ADD COLUMN portal_return_url VARCHAR(500) AFTER portal_payment_enabled;
