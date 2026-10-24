-- ========================================
-- 会员体系 + 余额 + 邀请 + 签到 + 支付 + 工单 系统表
-- ========================================

-- 会员等级配置表
CREATE TABLE IF NOT EXISTS membership_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level INT NOT NULL UNIQUE COMMENT '等级编号 1-5',
    level_name VARCHAR(50) NOT NULL COMMENT '等级名称',
    price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '会员价格（元）',
    app_quota INT NOT NULL DEFAULT 0 COMMENT '允许创建应用数量（-1=不限）',
    license_quota INT NOT NULL DEFAULT 0 COMMENT '卡密创建额度（张，-1=不限）',
    user_register_quota INT NOT NULL DEFAULT 0 COMMENT '终端用户注册额度（个，-1=不限）',
    traffic_quota BIGINT NOT NULL DEFAULT 0 COMMENT '流量额度（字节，-1=不限）',
    duration_days INT NOT NULL DEFAULT 0 COMMENT '会员时长（天，0=永久）',
    description VARCHAR(500) COMMENT '等级描述',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级配置表';

-- 用户会员信息表
CREATE TABLE IF NOT EXISTS user_membership (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    level_id BIGINT NOT NULL DEFAULT 1 COMMENT '当前等级ID',
    app_used INT NOT NULL DEFAULT 0 COMMENT '已创建应用数',
    license_used BIGINT NOT NULL DEFAULT 0 COMMENT '已创建卡密数',
    user_register_used INT NOT NULL DEFAULT 0 COMMENT '已注册终端用户数',
    traffic_used BIGINT NOT NULL DEFAULT 0 COMMENT '已使用流量（字节）',
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '余额（分）',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT '邀请码',
    invited_by BIGINT NULL COMMENT '被谁邀请（邀请人用户ID）',
    invite_count INT NOT NULL DEFAULT 0 COMMENT '已邀请人数',
    member_expires_at DATETIME NULL COMMENT '会员到期时间（NULL=永久或未开通）',
    last_checkin_date DATE NULL COMMENT '最后签到日期',
    consecutive_checkin_days INT NOT NULL DEFAULT 0 COMMENT '连续签到天数',
    total_checkin_days INT NOT NULL DEFAULT 0 COMMENT '累计签到天数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_invite_code (invite_code),
    INDEX idx_invited_by (invited_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员信息表';

-- 会员等级变动记录表
CREATE TABLE IF NOT EXISTS membership_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_type VARCHAR(30) NOT NULL COMMENT 'REGISTER/UPGRADE/PAYMENT/ADMIN_ADJUST/EXPIRE',
    from_level_id BIGINT NULL,
    to_level_id BIGINT NOT NULL,
    operator_id BIGINT NULL COMMENT '操作人（NULL=系统）',
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_change_type (change_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级变动记录表';

-- 余额流水表
CREATE TABLE IF NOT EXISTS balance_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL COMMENT 'RECHARGE/PURCHASE/INVITE_REWARD/CHECKIN_REWARD/ADMIN_GRANT/REFUND',
    amount BIGINT NOT NULL COMMENT '变动金额（分，正=入 负=出）',
    balance_before BIGINT NOT NULL COMMENT '变动前余额',
    balance_after BIGINT NOT NULL COMMENT '变动后余额',
    related_order_no VARCHAR(64) NULL COMMENT '关联订单号',
    description VARCHAR(500) COMMENT '描述',
    operator_id BIGINT NULL COMMENT '操作人（NULL=系统）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_type (transaction_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='余额流水表';

-- 额度购买商品表
CREATE TABLE IF NOT EXISTS quota_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL UNIQUE COMMENT '商品编码',
    product_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    product_type VARCHAR(30) NOT NULL COMMENT 'APP_QUOTA/LICENSE_QUOTA/USER_REGISTER_QUOTA/TRAFFIC_QUOTA/MEMBERSHIP',
    quota_value BIGINT NOT NULL COMMENT '额度值',
    price BIGINT NOT NULL COMMENT '价格（分）',
    description VARCHAR(500),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度购买商品表';

-- 订单表
CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_type VARCHAR(30) NOT NULL COMMENT '商品类型',
    product_name VARCHAR(100) NOT NULL COMMENT '商品名称（快照）',
    quantity INT NOT NULL DEFAULT 1,
    total_amount BIGINT NOT NULL COMMENT '订单金额（分）',
    pay_amount BIGINT NOT NULL DEFAULT 0 COMMENT '实付金额（分）',
    payment_channel VARCHAR(30) NULL COMMENT '支付渠道：alipay/wechat/qqpay',
    trade_no VARCHAR(100) NULL COMMENT '第三方交易号',
    pay_url TEXT NULL COMMENT '支付跳转URL',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待支付 1=已支付 2=已取消 3=已退款 4=支付失败',
    paid_at DATETIME NULL COMMENT '支付时间',
    admin_granted BOOLEAN DEFAULT FALSE COMMENT '是否管理员手动发放',
    admin_operator_id BIGINT NULL COMMENT '操作管理员ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_order_no (order_no),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 邀请记录表
CREATE TABLE IF NOT EXISTS invite_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inviter_id BIGINT NOT NULL COMMENT '邀请人ID',
    invitee_id BIGINT NOT NULL COMMENT '被邀请人ID',
    reward_amount BIGINT NOT NULL DEFAULT 300 COMMENT '奖励金额（分，默认3元=300分）',
    reward_granted BOOLEAN DEFAULT FALSE COMMENT '是否已发放',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inviter_invitee (inviter_id, invitee_id),
    INDEX idx_inviter_id (inviter_id),
    INDEX idx_invitee_id (invitee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请记录表';

-- 签到记录表
CREATE TABLE IF NOT EXISTS checkin_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL COMMENT '签到日期',
    license_reward INT NOT NULL DEFAULT 0 COMMENT '卡密额度奖励',
    user_register_reward INT NOT NULL DEFAULT 0 COMMENT '用户注册额度奖励',
    traffic_reward BIGINT NOT NULL DEFAULT 0 COMMENT '流量额度奖励（字节）',
    consecutive_days INT NOT NULL DEFAULT 1 COMMENT '本次签到时的连续天数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date),
    INDEX idx_user_id (user_id),
    INDEX idx_checkin_date (checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- 工单表
CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(30) NOT NULL UNIQUE COMMENT '工单编号',
    user_id BIGINT NOT NULL COMMENT '发起人ID',
    title VARCHAR(200) NOT NULL COMMENT '工单标题',
    category VARCHAR(50) NOT NULL COMMENT '工单分类：TECHNICAL/BILLING/FEATURE/OTHER',
    priority TINYINT NOT NULL DEFAULT 1 COMMENT '优先级：1=普通 2=重要 3=紧急',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理 1=处理中 2=等待回复 3=已解决 4=已关闭',
    assigned_to BIGINT NULL COMMENT '分配给的管理员ID',
    last_reply_user_id BIGINT NULL COMMENT '最后回复的用户ID',
    last_reply_at DATETIME NULL COMMENT '最后回复时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_assigned_to (assigned_to),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 工单消息表
CREATE TABLE IF NOT EXISTS ticket_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    sender_type VARCHAR(10) NOT NULL COMMENT 'USER/ADMIN',
    content TEXT NOT NULL COMMENT '消息内容',
    image_urls TEXT NULL COMMENT '图片URL（JSON数组）',
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_sender_id (sender_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单消息表';

-- 工单催办表
CREATE TABLE IF NOT EXISTS ticket_urge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单催办表';
