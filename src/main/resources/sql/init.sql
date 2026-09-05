-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    github_id VARCHAR(50) NOT NULL UNIQUE,
    login VARCHAR(100) NOT NULL,
    name VARCHAR(200),
    email VARCHAR(200),
    avatar_url TEXT,
    access_token TEXT,
    password VARCHAR(128) NULL COMMENT '密码(BCrypt加密)，本地账号登录使用',
    status TINYINT DEFAULT 1 COMMENT '用户状态：1-正常，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    INDEX idx_github_id (github_id),
    INDEX idx_login (login),
    INDEX idx_status (status)
);

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(200) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '角色状态：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role_code (role_code),
    INDEX idx_status (status)
);

-- 权限表
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型：API, MENU, BUTTON',
    resource_path VARCHAR(200) COMMENT '资源路径',
    http_method VARCHAR(10) COMMENT 'HTTP方法：GET, POST, PUT, DELETE',
    description VARCHAR(200) COMMENT '权限描述',
    status TINYINT DEFAULT 1 COMMENT '权限状态：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_permission_code (permission_code),
    INDEX idx_resource_type (resource_type),
    INDEX idx_resource_path (resource_path),
    INDEX idx_status (status)
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
);

-- 插入默认角色
INSERT IGNORE INTO roles (role_name, role_code, description) VALUES
('超级管理员', 'SUPER_ADMIN', '系统超级管理员，拥有所有权限'),
('管理员', 'ADMIN', '系统管理员，拥有大部分管理权限'),
('普通用户', 'USER', '普通用户，拥有基本功能权限');

-- 插入默认权限
INSERT IGNORE INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description) VALUES
-- 用户管理权限
('查看用户列表', 'USER_LIST', 'API', '/api/users', 'GET', '查看用户列表'),
('查看用户详情', 'USER_DETAIL', 'API', '/api/users/*', 'GET', '查看用户详情'),
('创建用户', 'USER_CREATE', 'API', '/api/users', 'POST', '创建新用户'),
('更新用户', 'USER_UPDATE', 'API', '/api/users/*', 'PUT', '更新用户信息'),
('删除用户', 'USER_DELETE', 'API', '/api/users/*', 'DELETE', '删除用户'),

-- 角色管理权限
('查看角色列表', 'ROLE_LIST', 'API', '/api/roles', 'GET', '查看角色列表'),
('创建角色', 'ROLE_CREATE', 'API', '/api/roles', 'POST', '创建新角色'),
('更新角色', 'ROLE_UPDATE', 'API', '/api/roles/*', 'PUT', '更新角色信息'),
('删除角色', 'ROLE_DELETE', 'API', '/api/roles/*', 'DELETE', '删除角色'),

-- 菜单管理权限
('菜单管理', 'MENU_MANAGEMENT', 'API', '/api/menus', 'GET,POST,PUT,DELETE', '菜单管理权限'),
('查看菜单列表', 'MENU_LIST', 'API', '/api/menus', 'GET', '查看菜单列表'),
('创建菜单', 'MENU_CREATE', 'API', '/api/menus', 'POST', '创建新菜单'),
('更新菜单', 'MENU_UPDATE', 'API', '/api/menus/*', 'PUT', '更新菜单信息'),
('删除菜单', 'MENU_DELETE', 'API', '/api/menus/*', 'DELETE', '删除菜单'),

-- 权限管理权限
('查看权限列表', 'PERMISSION_LIST', 'API', '/api/permissions', 'GET', '查看权限列表'),
('创建权限', 'PERMISSION_CREATE', 'API', '/api/permissions', 'POST', '创建新权限'),
('更新权限', 'PERMISSION_UPDATE', 'API', '/api/permissions/*', 'PUT', '更新权限信息'),
('删除权限', 'PERMISSION_DELETE', 'API', '/api/permissions/*', 'DELETE', '删除权限'),

-- 系统配置权限
('查看系统配置', 'CONFIG_LIST', 'API', '/api/config', 'GET', '查看系统配置'),
('更新系统配置', 'CONFIG_UPDATE', 'API', '/api/config', 'PUT', '更新系统配置'),

-- 系统消息权限
('查看消息列表', 'MESSAGE_LIST', 'API', '/api/messages', 'GET', '查看系统消息列表'),
('创建系统消息', 'MESSAGE_CREATE', 'API', '/api/messages', 'POST', '创建系统消息'),
('删除系统消息', 'MESSAGE_DELETE', 'API', '/api/messages/*', 'DELETE', '删除系统消息'),

-- 文件上传权限
('文件上传', 'FILE_UPLOAD', 'API', '/api/upload', 'POST', '文件分片上传'),

-- 个人信息权限
('查看个人信息', 'PROFILE_VIEW', 'API', '/api/user/info,/api/user/profile', 'GET', '查看个人信息'),
('更新个人信息', 'PROFILE_UPDATE', 'API', '/api/user/profile', 'PUT', '更新个人信息');

-- 系统配置表（前置，供后续初始化条件判断）
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description VARCHAR(500),
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_key (config_key)
);

-- 系统初始化标记默认未完成；完成首次向导后由后端写为 true
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
('SYSTEM_INITIALIZED', 'false', '系统初始化标记，用于判断是否已完成首次初始化');

-- 邀请有奖默认配置
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
('invite.enabled', 'true', '邀请功能开关'),
('invite.max-count', '20', '最大邀请人数'),
('invite.reward-amount', '300', '邀请奖励金额(分)');

-- 检查是否是首次初始化，如果是则分配默认权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.role_code = 'SUPER_ADMIN'
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.role_code = 'ADMIN' 
AND p.permission_code NOT IN (
    'USER_LIST', 'USER_DETAIL', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE',
    'ROLE_LIST', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE',
    'MENU_MANAGEMENT', 'MENU_LIST', 'MENU_CREATE', 'MENU_UPDATE', 'MENU_DELETE',
    'PERMISSION_LIST', 'PERMISSION_CREATE', 'PERMISSION_UPDATE', 'PERMISSION_DELETE',
    'CONFIG_LIST', 'CONFIG_UPDATE'
)
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('PROFILE_VIEW', 'PROFILE_UPDATE', 'FILE_UPLOAD')
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

-- 确保 FILE_UPLOAD 权限已分配（兼容已初始化的系统）
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code IN ('SUPER_ADMIN', 'USER')
AND p.permission_code = 'FILE_UPLOAD';

-- 菜单表
CREATE TABLE IF NOT EXISTS menus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_code VARCHAR(100) NOT NULL UNIQUE COMMENT '菜单编码',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID，0表示顶级菜单',
    menu_type TINYINT DEFAULT 1 COMMENT '菜单类型：1-目录，2-菜单，3-按钮',
    path VARCHAR(200) COMMENT '路由路径',
    component VARCHAR(200) COMMENT '组件路径',
    icon VARCHAR(100) COMMENT '菜单图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    visible TINYINT DEFAULT 1 COMMENT '是否显示：1-显示，0-隐藏',
    status TINYINT DEFAULT 1 COMMENT '菜单状态：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_menu_code (menu_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_menu_type (menu_type),
    INDEX idx_sort_order (sort_order),
    INDEX idx_status (status)
);

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS role_menus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_id) REFERENCES menus(id) ON DELETE CASCADE,
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
);

-- 插入默认菜单
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status) VALUES
-- 顶级菜单
('仪表板', 'DASHBOARD', 0, 2, '/dashboard', 'Dashboard', 'dashboard', 1, 1, 1),
('系统管理', 'SYSTEM_MANAGEMENT', 0, 1, '/system', '', 'setting', 2, 1, 1),
('个人中心', 'PROFILE', 0, 2, '/profile', 'UserInfo', 'user', 3, 1, 1);

-- 插入系统管理子菜单（使用变量方式）
SET @system_management_id = (SELECT id FROM menus WHERE menu_code = 'SYSTEM_MANAGEMENT');

INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status) VALUES
('用户管理', 'USER_MANAGEMENT', @system_management_id, 2, '/system/users', 'SystemManagement', 'team', 1, 1, 1),
('角色管理', 'ROLE_MANAGEMENT', @system_management_id, 2, '/system/roles', 'SystemManagement', 'safety', 2, 1, 1),
('菜单管理', 'MENU_MANAGEMENT', @system_management_id, 2, '/system/menus', 'SystemManagement', 'menu', 3, 1, 1),
('权限管理', 'PERMISSION_MANAGEMENT', @system_management_id, 2, '/system/permissions', 'SystemManagement', 'lock', 4, 1, 1),
('系统信息', 'SYSTEM_CONFIG', @system_management_id, 2, '/system/info', 'SystemManagement', 'tool', 5, 1, 1),
('系统配置', 'SYSTEM_SETTING', @system_management_id, 2, '/system/config', 'SystemManagement', 'setting', 6, 1, 1);

-- 兼容存量数据：确保系统信息/系统配置菜单存在并命名正确
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('系统信息', 'SYSTEM_CONFIG', @system_management_id, 2, '/system/info', 'SystemManagement', 'tool', 5, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    sort_order = VALUES(sort_order);

INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('系统配置', 'SYSTEM_SETTING', @system_management_id, 2, '/system/config', 'SystemManagement', 'setting', 6, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    sort_order = VALUES(sort_order);

INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
  AND m.menu_code = 'SYSTEM_SETTING'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);


-- 检查是否是首次初始化，如果是则分配默认菜单权限
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'SUPER_ADMIN'
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'ADMIN' 
AND m.menu_code NOT IN (
    'SYSTEM_MANAGEMENT',
    'USER_MANAGEMENT', 'ROLE_MANAGEMENT', 'MENU_MANAGEMENT', 'PERMISSION_MANAGEMENT',
    'SYSTEM_CONFIG', 'SYSTEM_SETTING'
)
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'USER' 
AND m.menu_code IN ('DASHBOARD', 'PROFILE')
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

-- Spring Session 主表
CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(500),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Spring Session 属性表
CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES LONGBLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- system_config 与 SYSTEM_INITIALIZED 已在前文创建并写入默认值

-- 活动日志表
CREATE TABLE IF NOT EXISTS activity_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(100) COMMENT '用户名',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    action_target VARCHAR(100) COMMENT '操作对象',
    action_description VARCHAR(500) COMMENT '操作描述',
    ip_address VARCHAR(50) COMMENT '操作IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理信息',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '操作状态',
    importance_level VARCHAR(20) DEFAULT 'LOW' COMMENT '重要程度：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急',
    is_read BOOLEAN DEFAULT FALSE COMMENT '是否已读',
    read_time DATETIME COMMENT '阅读时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_created_time (created_time),
    INDEX idx_action_type (action_type),
    INDEX idx_importance_level (importance_level),
    INDEX idx_is_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动日志表';

-- 系统消息表
CREATE TABLE IF NOT EXISTS system_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    message_type VARCHAR(50) NOT NULL COMMENT '消息类型',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    importance_level VARCHAR(20) DEFAULT 'LOW' COMMENT '重要程度：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急',
    target_type VARCHAR(50) DEFAULT 'ALL' COMMENT '目标类型：ALL-所有用户，USER-指定用户，ROLE-指定角色',
    target_id BIGINT COMMENT '目标ID',
    email_sent BOOLEAN DEFAULT FALSE COMMENT '是否已发送邮件',
    email_sent_time DATETIME COMMENT '邮件发送时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expire_time DATETIME COMMENT '过期时间',
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_time (created_time),
    INDEX idx_importance_level (importance_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息表';

-- 用户消息关联表
CREATE TABLE IF NOT EXISTS user_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    message_id BIGINT NOT NULL COMMENT '消息ID',
    is_read BOOLEAN DEFAULT FALSE COMMENT '是否已读',
    read_time DATETIME COMMENT '阅读时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_message (user_id, message_id),
    INDEX idx_user_id (user_id),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息关联表';


-- ========================================
-- 应用管理模块表
-- ========================================

-- 应用表
CREATE TABLE IF NOT EXISTS application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '应用ID',
    owner_id BIGINT NOT NULL COMMENT '所属用户ID',
    app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
    app_key VARCHAR(64) NOT NULL UNIQUE COMMENT 'API密钥',
    app_secret VARCHAR(128) NOT NULL COMMENT 'API密钥(加密存储)',
    
    -- 基础信息
    description VARCHAR(500) COMMENT '应用描述',
    notice TEXT COMMENT '应用公告',
    update_notice TEXT COMMENT '更新公告',
    update_file_storage_key VARCHAR(255) COMMENT '更新文件存储Key(MinIO)',
    category VARCHAR(50) COMMENT '应用分类',
    tags VARCHAR(255) COMMENT '标签(逗号分隔)',
    icon_url VARCHAR(255) DEFAULT '/default-app-icon.png' COMMENT '应用图标',
    
    -- 业务模式
    business_model TINYINT NOT NULL DEFAULT 1 COMMENT '业务模式: 1=付费, 2=免费, 3=试用+付费',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 2=维护, 3=停用',
    
    -- 加密配置
    encryption_plugin VARCHAR(100) DEFAULT 'aes-default' COMMENT '加密插件标识',
    encryption_config JSON COMMENT '加密配置参数',
    
    -- 功能开关
    features JSON COMMENT '功能开关配置',
    
    -- 流量统计
    traffic_limit BIGINT DEFAULT 0 COMMENT '流量限制(字节)',
    traffic_used BIGINT DEFAULT 0 COMMENT '已使用流量',
    
    -- 版本管理
    current_version VARCHAR(20) COMMENT '当前版本号',
    min_version VARCHAR(20) COMMENT '最低支持版本',

    -- 卡密解绑扣时（解绑设备或解绑 IP 时，从卡密 expires_at 扣减）
    unbind_time_deduct_mode VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '解绑扣时模式: NONE=不扣, PERCENT=按剩余时长百分比, HOURS=固定扣小时',
    unbind_time_deduct_value DECIMAL(10, 2) NULL COMMENT '扣时数值: PERCENT 为 0-100；HOURS 为小时数(可小数)',
    unbind_cooldown_hours INT NOT NULL DEFAULT 0 COMMENT '解绑冷却时间（小时）；0=不限制，仅影响三方换绑',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    
    INDEX idx_owner (owner_id),
    INDEX idx_app_key (app_key),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用表';

-- 应用操作日志表
CREATE TABLE IF NOT EXISTS application_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人名称',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型',
    operation_desc VARCHAR(255) COMMENT '操作描述',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT 'User Agent',
    request_params JSON COMMENT '请求参数',
    response_result VARCHAR(20) COMMENT '响应结果: SUCCESS, FAILED',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    
    INDEX idx_app (app_id),
    INDEX idx_operator (operator_id),
    INDEX idx_created (created_at),
    INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用操作日志表';

-- 插入应用管理相关权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('应用列表', 'APP_LIST', 'API', '/api/applications', 'GET', '查看应用列表', 1),
('应用详情', 'APP_DETAIL', 'API', '/api/applications/*', 'GET', '查看应用详情', 1),
('创建应用', 'APP_CREATE', 'API', '/api/applications', 'POST', '创建新应用', 1),
('更新应用', 'APP_UPDATE', 'API', '/api/applications/*', 'PUT', '更新应用信息', 1),
('删除应用', 'APP_DELETE', 'API', '/api/applications/*', 'DELETE', '删除应用', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员角色分配应用管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN ('APP_LIST', 'APP_DETAIL', 'APP_CREATE', 'APP_UPDATE', 'APP_DELETE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户角色分配应用创建和查看权限（如果有 USER 角色）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('APP_LIST', 'APP_DETAIL', 'APP_CREATE', 'APP_UPDATE', 'APP_DELETE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);


-- 插入应用管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('应用管理', 'APP_MANAGEMENT', 0, 1, '/applications', NULL, 'AppstoreOutlined', 2, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 调整其他菜单的顺序
UPDATE menus SET sort_order = 3 WHERE menu_code = 'SYSTEM_MANAGEMENT';
UPDATE menus SET sort_order = 4 WHERE menu_code = 'PROFILE';

-- 获取应用管理菜单ID
SET @app_menu_id = (SELECT id FROM menus WHERE menu_code = 'APP_MANAGEMENT');

-- 插入应用管理子菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('应用列表', 'APP_LIST_PAGE', @app_menu_id, 2, '/applications/list', 'ApplicationList', NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name);

-- 为超级管理员角色分配应用管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code IN ('APP_MANAGEMENT', 'APP_LIST_PAGE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配应用管理父级与应用列表（否则仅有子菜单时无法挂到根节点，侧栏不显示「应用管理」）
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'USER'
AND m.menu_code IN ('APP_MANAGEMENT', 'APP_LIST_PAGE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);





-- 卡密表
CREATE TABLE IF NOT EXISTS license_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '卡密ID',
    app_id BIGINT NOT NULL COMMENT '所属应用ID',
    owner_id BIGINT NOT NULL COMMENT '创建者ID',
    agent_id BIGINT NULL COMMENT '代理ID（为空表示非代理创建）',
    key_code VARCHAR(128) NOT NULL UNIQUE COMMENT '卡密码',
    
    -- 卡密类型
    key_type VARCHAR(20) NOT NULL COMMENT '卡密类型: DAY,WEEK,MONTH,QUARTER,HALF_YEAR,YEAR,PERMANENT,CUSTOM',
    duration_value INT COMMENT '时长数值',
    duration_unit VARCHAR(10) COMMENT '时长单位: HOUR,DAY,MONTH',
    
    -- 批次管理
    batch_id BIGINT COMMENT '批次ID',
    source VARCHAR(50) DEFAULT 'MANUAL' COMMENT '来源: MANUAL,BATCH,API,IMPORT',
    
    -- 绑定信息
    bind_device_id VARCHAR(255) COMMENT '绑定设备标识',
    bind_ip VARCHAR(50) COMMENT '绑定IP',
    bind_user_id BIGINT COMMENT '绑定的终端用户ID',
    
    -- 时间管理
    first_used_at DATETIME COMMENT '首次使用时间',
    last_used_at DATETIME COMMENT '最后使用时间',
    expires_at DATETIME COMMENT '到期时间',
    
    -- 使用限制
    use_count INT DEFAULT 0 COMMENT '使用次数',
    use_limit INT DEFAULT 0 COMMENT '使用次数限制(0=不限)',
    unbind_count INT DEFAULT 0 COMMENT '解绑次数',
    unbind_limit INT DEFAULT 0 COMMENT '解绑次数限制(0=不限)',
    
    -- 时间段限制
    use_time_start TIME COMMENT '可使用时间段-开始',
    use_time_end TIME COMMENT '可使用时间段-结束',
    
    -- 验证开关
    device_check_enabled BOOLEAN DEFAULT TRUE COMMENT '是否验证设备',
    ip_check_enabled BOOLEAN DEFAULT FALSE COMMENT '是否验证IP',
    
    -- WebSocket 相关
    last_heartbeat_at DATETIME COMMENT '最后心跳时间',
    heartbeat_interval INT DEFAULT 60 COMMENT '心跳间隔(秒)',
    connection_id VARCHAR(64) COMMENT '当前连接ID',
    is_online BOOLEAN DEFAULT FALSE COMMENT '是否在线',
    
    -- 扩展字段
    remark VARCHAR(500) COMMENT '备注',
    core_data TEXT COMMENT '核心标记数据',
    metadata JSON COMMENT '扩展元数据',
    
    -- 状态
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=未使用, 2=使用中, 3=已过期, 4=已禁用',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    
    INDEX idx_app (app_id),
    INDEX idx_owner (owner_id),
    INDEX idx_agent (agent_id),
    INDEX idx_batch (batch_id),
    INDEX idx_bind_user (bind_user_id),
    INDEX idx_status (status),
    INDEX idx_expires (expires_at),
    INDEX idx_deleted (deleted),
    INDEX idx_heartbeat (last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密表';

-- 卡密批次表
CREATE TABLE IF NOT EXISTS license_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '批次ID',
    app_id BIGINT NOT NULL COMMENT '所属应用ID',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    batch_name VARCHAR(100) NOT NULL COMMENT '批次名称',
    batch_code VARCHAR(50) NOT NULL UNIQUE COMMENT '批次编号',
    
    -- 批次配置
    key_type VARCHAR(20) NOT NULL COMMENT '卡密类型',
    duration_value INT COMMENT '时长数值',
    duration_unit VARCHAR(10) COMMENT '时长单位: HOUR,DAY,MONTH,YEAR',
    total_count INT NOT NULL COMMENT '生成总数',
    used_count INT DEFAULT 0 COMMENT '已使用数量',
    
    -- 批次配置（继承到卡密）
    use_limit INT DEFAULT 0 COMMENT '使用次数限制',
    unbind_limit INT DEFAULT 0 COMMENT '解绑次数限制',
    device_check_enabled BOOLEAN DEFAULT TRUE COMMENT '是否验证设备',
    ip_check_enabled BOOLEAN DEFAULT FALSE COMMENT '是否验证IP',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    remark VARCHAR(500) COMMENT '备注',
    
    INDEX idx_app (app_id),
    INDEX idx_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密批次表';

-- 插入卡密管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('卡密列表', 'LICENSE_LIST', 'API', '/api/licenses', 'GET', '查看卡密列表', 1),
('卡密详情', 'LICENSE_DETAIL', 'API', '/api/licenses/*', 'GET', '查看卡密详情', 1),
('创建卡密', 'LICENSE_CREATE', 'API', '/api/licenses', 'POST', '创建卡密', 1),
('批量生成卡密', 'LICENSE_BATCH_CREATE', 'API', '/api/licenses/batch', 'POST', '批量生成卡密', 1),
('编辑卡密', 'LICENSE_UPDATE', 'API', '/api/licenses/*', 'PUT', '编辑卡密', 1),
('删除卡密', 'LICENSE_DELETE', 'API', '/api/licenses/*', 'DELETE', '删除卡密', 1),
('导出卡密', 'LICENSE_EXPORT', 'API', '/api/licenses/export', 'GET', '导出卡密', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员分配卡密权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN ('LICENSE_LIST', 'LICENSE_DETAIL', 'LICENSE_CREATE', 'LICENSE_BATCH_CREATE', 'LICENSE_UPDATE', 'LICENSE_DELETE', 'LICENSE_EXPORT')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配基础卡密权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('LICENSE_LIST', 'LICENSE_DETAIL', 'LICENSE_CREATE', 'LICENSE_BATCH_CREATE', 'LICENSE_UPDATE', 'LICENSE_DELETE', 'LICENSE_EXPORT')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入卡密管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('卡密管理', 'LICENSE_MANAGEMENT', (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'APP_MANAGEMENT') AS tmp), 2, '/applications/licenses', 'LicenseManagement', NULL, 2, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 为超级管理员分配卡密管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code = 'LICENSE_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配卡密管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'USER'
AND m.menu_code = 'LICENSE_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);


-- ========================================
-- 终端用户管理模块表
-- ========================================

-- 应用终端用户表（按应用隔离）
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    app_id BIGINT NOT NULL COMMENT '所属应用ID',
    agent_id BIGINT NULL COMMENT '代理ID（为空表示非代理创建）',
    
    -- 账号信息
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    password VARCHAR(128) COMMENT '密码(加密存储)',
    
    -- 基础信息
    nickname VARCHAR(50) COMMENT '昵称',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    signature VARCHAR(200) COMMENT '个性签名',
    
    -- 统计信息
    login_count INT DEFAULT 0 COMMENT '登录次数',
    last_login_at DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    last_device_id VARCHAR(255) COMMENT '最后登录设备标识(WS)',
    member_expires_at DATETIME COMMENT '会员到期时间（空=未开通会员；充值/管理员加时长后写入）',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    
    UNIQUE KEY uk_app_username (app_id, username),
    UNIQUE KEY uk_app_email (app_id, email),
    INDEX idx_app (app_id),
    INDEX idx_agent (agent_id),
    INDEX idx_phone (phone),
    INDEX idx_deleted (deleted),
    INDEX idx_member_expires (member_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用终端用户表';

-- 应用用户绑定表（用户与设备的绑定关系）
CREATE TABLE IF NOT EXISTS app_user_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '绑定ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    user_id BIGINT NOT NULL COMMENT '终端用户ID',
    
    -- 绑定类型
    bind_type VARCHAR(20) NOT NULL COMMENT '绑定类型: LICENSE=卡密, TRIAL=试用, VIP=会员, ACCOUNT=WS账号登录设备',
    license_key_id BIGINT COMMENT '关联的卡密ID(bind_type=LICENSE时)',
    
    -- 设备信息
    device_id VARCHAR(255) NOT NULL COMMENT '设备标识',
    device_name VARCHAR(100) COMMENT '设备名称',
    device_os VARCHAR(50) COMMENT '设备系统',
    device_ip VARCHAR(50) COMMENT '设备IP',
    
    -- 时间管理
    expires_at DATETIME COMMENT '到期时间',
    first_bind_at DATETIME COMMENT '首次绑定时间',
    last_active_at DATETIME COMMENT '最后活跃时间',
    
    -- 使用统计
    use_count INT DEFAULT 0 COMMENT '使用次数',
    unbind_count INT DEFAULT 0 COMMENT '解绑次数',
    
    -- 试用相关
    is_trial BOOLEAN DEFAULT FALSE COMMENT '是否试用',
    trial_expires_at DATETIME COMMENT '试用到期时间',
    
    -- 权限控制
    allow_unbind BOOLEAN DEFAULT TRUE COMMENT '允许解绑',
    is_banned BOOLEAN DEFAULT FALSE COMMENT '是否封禁',
    ban_reason VARCHAR(255) COMMENT '封禁原因',
    ban_at DATETIME COMMENT '封禁时间',
    
    -- 扩展字段
    remark VARCHAR(500) COMMENT '备注',
    metadata JSON COMMENT '扩展元数据',
    
    -- 状态
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 2=已过期, 3=已封禁, 4=已解绑',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    
    UNIQUE KEY uk_app_user_device (app_id, user_id, device_id),
    INDEX idx_app (app_id),
    INDEX idx_user (user_id),
    INDEX idx_license (license_key_id),
    INDEX idx_device (device_id),
    INDEX idx_expires (expires_at),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用用户绑定表';

-- 用户试用记录表（记录每个用户在每个应用的试用情况）
CREATE TABLE IF NOT EXISTS app_user_trial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    
    -- 试用信息
    trial_started_at DATETIME NOT NULL COMMENT '试用开始时间',
    trial_expires_at DATETIME NOT NULL COMMENT '试用到期时间',
    device_id VARCHAR(255) COMMENT '试用设备',
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    UNIQUE KEY uk_app_user (app_id, user_id),
    INDEX idx_app (app_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户试用记录表';

-- 插入终端用户管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('终端用户列表', 'APP_USER_LIST', 'API', '/api/app-users', 'GET', '查看终端用户列表', 1),
('终端用户详情', 'APP_USER_DETAIL', 'API', '/api/app-users/*', 'GET', '查看终端用户详情', 1),
('创建终端用户', 'APP_USER_CREATE', 'API', '/api/app-users', 'POST', '创建终端用户', 1),
('编辑终端用户', 'APP_USER_UPDATE', 'API', '/api/app-users/*', 'PUT', '编辑终端用户', 1),
('删除终端用户', 'APP_USER_DELETE', 'API', '/api/app-users/*', 'DELETE', '删除终端用户', 1),
('重置用户密码', 'APP_USER_RESET_PWD', 'API', '/api/app-users/*/reset-password', 'POST', '重置用户密码', 1),
('封禁用户', 'APP_USER_BAN', 'API', '/api/app-users/*/ban', 'POST', '封禁用户', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员分配终端用户管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN ('APP_USER_LIST', 'APP_USER_DETAIL', 'APP_USER_CREATE', 'APP_USER_UPDATE', 'APP_USER_DELETE', 'APP_USER_RESET_PWD', 'APP_USER_BAN')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配终端用户管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('APP_USER_LIST', 'APP_USER_DETAIL', 'APP_USER_CREATE', 'APP_USER_UPDATE', 'APP_USER_DELETE', 'APP_USER_RESET_PWD', 'APP_USER_BAN')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入终端用户管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('终端用户', 'APP_USER_MANAGEMENT', (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'APP_MANAGEMENT') AS tmp), 2, '/applications/users', 'AppUserManagement', NULL, 3, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 为超级管理员分配终端用户管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code = 'APP_USER_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配终端用户管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'USER'
AND m.menu_code = 'APP_USER_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 应用变量表
CREATE TABLE IF NOT EXISTS app_variable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id BIGINT NOT NULL COMMENT '应用ID',
    variable_name VARCHAR(100) NOT NULL COMMENT '变量名称',
    display_name VARCHAR(200) NOT NULL COMMENT '显示名称',
    description VARCHAR(500) COMMENT '变量描述',
    variable_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '变量类型: STRING, NUMBER, BOOLEAN, JSON, ARRAY',
    variable_value TEXT COMMENT '变量值 (JSON格式存储)',
    required BOOLEAN DEFAULT FALSE COMMENT '是否必填',
    sort_order INT DEFAULT 0 COMMENT '排序权重',
    validation_rules TEXT COMMENT '验证规则 (JSON格式)',
    options TEXT COMMENT '变量选项 (JSON格式)',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    version VARCHAR(50) COMMENT '版本号',
    tags TEXT COMMENT '标签 (JSON数组格式)',
    metadata JSON COMMENT '扩展元数据',
    created_by BIGINT COMMENT '创建者ID',
    updated_by BIGINT COMMENT '更新者ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',
    security_tier INT NOT NULL DEFAULT 2 COMMENT '安全分级: 0=STANDARD 1=SENSITIVE 2=CRITICAL（WS 分桶；新建默认最高档）',
    UNIQUE KEY uk_app_variable_name (app_id, variable_name, deleted),
    INDEX idx_app_id (app_id),
    INDEX idx_variable_name (variable_name),
    INDEX idx_variable_type (variable_type),
    INDEX idx_enabled (enabled),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at),
    INDEX idx_deleted (deleted)
);

-- 应用变量历史记录表
CREATE TABLE IF NOT EXISTS app_variable_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variable_id BIGINT NOT NULL COMMENT '变量ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    variable_name VARCHAR(100) NOT NULL COMMENT '变量名称',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: CREATE, UPDATE, DELETE',
    old_value TEXT COMMENT '变更前的值 (JSON格式)',
    new_value TEXT COMMENT '变更后的值 (JSON格式)',
    change_reason VARCHAR(500) COMMENT '变更原因/备注',
    operator_id BIGINT COMMENT '操作者ID',
    operator_ip VARCHAR(50) COMMENT '操作者IP',
    operated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    version VARCHAR(50) COMMENT '版本号',
    INDEX idx_variable_id (variable_id),
    INDEX idx_app_id (app_id),
    INDEX idx_variable_name (variable_name),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operator_id (operator_id),
    INDEX idx_operated_at (operated_at)
);

-- 插入变量管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('变量列表', 'APP_VARIABLE_LIST', 'API', '/api/app-variables', 'GET', '查看变量列表', 1),
('变量详情', 'APP_VARIABLE_DETAIL', 'API', '/api/app-variables/*', 'GET', '查看变量详情', 1),
('创建变量', 'APP_VARIABLE_CREATE', 'API', '/api/app-variables', 'POST', '创建变量', 1),
('编辑变量', 'APP_VARIABLE_UPDATE', 'API', '/api/app-variables/*', 'PUT', '编辑变量', 1),
('删除变量', 'APP_VARIABLE_DELETE', 'API', '/api/app-variables/*', 'DELETE', '删除变量', 1),
('复制变量', 'APP_VARIABLE_COPY', 'API', '/api/app-variables/*/copy', 'POST', '复制变量', 1),
('批量更新变量', 'APP_VARIABLE_BATCH_UPDATE', 'API', '/api/app-variables/app/*/batch', 'PUT', '批量更新变量', 1),
('变量历史记录', 'APP_VARIABLE_HISTORY', 'API', '/api/app-variables/*/history', 'GET', '查看变量历史记录', 1),
('导出变量配置', 'APP_VARIABLE_EXPORT', 'API', '/api/app-variables/app/*/export', 'GET', '导出变量配置', 1),
('导入变量配置', 'APP_VARIABLE_IMPORT', 'API', '/api/app-variables/app/*/import', 'POST', '导入变量配置', 1),
('验证变量值', 'APP_VARIABLE_VALIDATE', 'API', '/api/app-variables/*/validate', 'POST', '验证变量值', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员分配变量管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN (
    'APP_VARIABLE_LIST', 'APP_VARIABLE_DETAIL', 'APP_VARIABLE_CREATE', 'APP_VARIABLE_UPDATE',
    'APP_VARIABLE_DELETE', 'APP_VARIABLE_COPY', 'APP_VARIABLE_BATCH_UPDATE', 'APP_VARIABLE_HISTORY',
    'APP_VARIABLE_EXPORT', 'APP_VARIABLE_IMPORT', 'APP_VARIABLE_VALIDATE'
)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配变量管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN (
    'APP_VARIABLE_LIST', 'APP_VARIABLE_DETAIL', 'APP_VARIABLE_CREATE', 'APP_VARIABLE_UPDATE',
    'APP_VARIABLE_DELETE', 'APP_VARIABLE_COPY', 'APP_VARIABLE_BATCH_UPDATE', 'APP_VARIABLE_HISTORY',
    'APP_VARIABLE_EXPORT', 'APP_VARIABLE_IMPORT', 'APP_VARIABLE_VALIDATE'
)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入变量管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('变量管理', 'APP_VARIABLE_MANAGEMENT', (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'APP_MANAGEMENT') AS tmp), 2, '/applications/variables', 'AppVariableManagement', NULL, 4, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 为超级管理员分配变量管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code = 'APP_VARIABLE_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配变量管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'USER'
AND m.menu_code = 'APP_VARIABLE_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- ========================================
-- 插件管理权限与菜单（与系统管理/应用管理同级）
-- ========================================

-- 插件管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('插件列表', 'PLUGIN_LIST', 'API', '/api/plugins', 'GET', '查看插件列表', 1),
('上传插件', 'PLUGIN_UPLOAD', 'API', '/api/plugins/upload', 'POST', '上传插件包', 1),
('启用插件', 'PLUGIN_ENABLE', 'API', '/api/plugins/*/enable', 'POST', '启用插件', 1),
('停用插件', 'PLUGIN_DISABLE', 'API', '/api/plugins/*/disable', 'POST', '停用插件', 1),
('删除插件', 'PLUGIN_DELETE', 'API', '/api/plugins/*', 'DELETE', '删除插件', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 函数插件管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('函数插件列表', 'FUNCTION_PLUGIN_LIST', 'API', '/api/function-plugins', 'GET', '查看函数插件列表', 1),
('上传函数插件', 'FUNCTION_PLUGIN_UPLOAD', 'API', '/api/function-plugins/upload', 'POST', '上传函数插件包', 1),
('启用函数插件', 'FUNCTION_PLUGIN_ENABLE', 'API', '/api/function-plugins/*/enable', 'POST', '启用函数插件', 1),
('停用函数插件', 'FUNCTION_PLUGIN_DISABLE', 'API', '/api/function-plugins/*/disable', 'POST', '停用函数插件', 1),
('删除函数插件', 'FUNCTION_PLUGIN_DELETE', 'API', '/api/function-plugins/*', 'DELETE', '删除函数插件', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员和管理员分配插件管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND p.permission_code IN ('PLUGIN_LIST', 'PLUGIN_UPLOAD', 'PLUGIN_ENABLE', 'PLUGIN_DISABLE', 'PLUGIN_DELETE',
                           'FUNCTION_PLUGIN_LIST', 'FUNCTION_PLUGIN_UPLOAD', 'FUNCTION_PLUGIN_ENABLE', 'FUNCTION_PLUGIN_DISABLE', 'FUNCTION_PLUGIN_DELETE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入顶级菜单：插件管理（和系统管理、应用管理同级）
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('插件管理', 'PLUGIN_MANAGEMENT', 0, 1, '/plugins', NULL, 'tool', 3, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 调整顶级菜单顺序，确保插件管理与系统管理/应用管理同级展示
UPDATE menus SET sort_order = 2 WHERE menu_code = 'APP_MANAGEMENT';
UPDATE menus SET sort_order = 3 WHERE menu_code = 'PLUGIN_MANAGEMENT';
UPDATE menus SET sort_order = 4 WHERE menu_code = 'SYSTEM_MANAGEMENT';
UPDATE menus SET sort_order = 5 WHERE menu_code = 'PROFILE';

-- 获取插件管理菜单ID
SET @plugin_menu_id = (SELECT id FROM menus WHERE menu_code = 'PLUGIN_MANAGEMENT');

-- 插件管理子菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('插件列表', 'PLUGIN_LIST_PAGE', @plugin_menu_id, 2, '/plugins/list', 'PluginManagement', NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name);

-- 函数插件管理子菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('函数插件', 'FUNCTION_PLUGIN_LIST_PAGE', @plugin_menu_id, 2, '/plugins/function', 'FunctionPluginManagement', NULL, 2, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name);

-- 为超级管理员和管理员分配插件管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND m.menu_code IN ('PLUGIN_MANAGEMENT', 'PLUGIN_LIST_PAGE', 'FUNCTION_PLUGIN_LIST_PAGE')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 兜底：超级管理员始终拥有全部菜单（避免历史初始化顺序导致 role_menus 缺失）
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
  AND m.status = 1
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- ========================================
-- 插件管理模块表
-- ========================================

-- ========================================
-- 应用代理模块表（代理配置、权限与额度）
-- ========================================

CREATE TABLE IF NOT EXISTS app_agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '代理ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    agent_code VARCHAR(64) NOT NULL COMMENT '代理编码/名称',
    user_id BIGINT NOT NULL COMMENT '绑定后台用户ID',
    scope_mode VARCHAR(20) NOT NULL DEFAULT 'OWN_ONLY' COMMENT '范围：ALL_IN_APP / OWN_ONLY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_app_user (app_id, user_id, deleted),
    INDEX idx_app (app_id),
    INDEX idx_user_id (user_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用代理';

CREATE TABLE IF NOT EXISTS app_agent_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_id BIGINT NOT NULL COMMENT '代理ID',
    permission_code VARCHAR(80) NOT NULL COMMENT '代理权限项编码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_perm (agent_id, permission_code),
    INDEX idx_agent (agent_id),
    INDEX idx_perm (permission_code),
    FOREIGN KEY (agent_id) REFERENCES app_agent(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用代理权限项';

CREATE TABLE IF NOT EXISTS app_agent_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_id BIGINT NOT NULL COMMENT '代理ID',
    key_type VARCHAR(20) NOT NULL COMMENT '卡密类型（与 license_key.key_type 一致）',
    quota_total BIGINT NOT NULL DEFAULT 0 COMMENT '额度总量',
    quota_used BIGINT NOT NULL DEFAULT 0 COMMENT '已用额度（创建时扣减）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_keytype (agent_id, key_type),
    INDEX idx_agent (agent_id),
    INDEX idx_key_type (key_type),
    FOREIGN KEY (agent_id) REFERENCES app_agent(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用代理卡密额度';

CREATE TABLE IF NOT EXISTS plugin_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '插件记录ID',
    plugin_id VARCHAR(100) NOT NULL COMMENT '插件标识',
    plugin_name VARCHAR(200) COMMENT '插件名称',
    plugin_version VARCHAR(50) NOT NULL COMMENT '插件版本',
    bucket_name VARCHAR(100) NOT NULL COMMENT '对象存储桶',
    object_key VARCHAR(255) NOT NULL COMMENT '对象存储路径',
    sha256 VARCHAR(64) NOT NULL COMMENT '文件摘要',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=已上传,1=已启用,2=已停用,3=加载失败',
    loaded_plugin_id VARCHAR(150) COMMENT 'PF4J运行时插件ID',
    remark VARCHAR(500) COMMENT '备注',
    config_schema TEXT NULL COMMENT '插件配置Schema(JSON)',
    config_defaults TEXT NULL COMMENT '插件配置默认值(JSON)',
    config_values TEXT NULL COMMENT '插件配置值(JSON)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '逻辑删除时间',
    UNIQUE KEY uk_plugin_version (plugin_id, plugin_version, deleted),
    INDEX idx_status (status),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件模块表';

-- 函数插件模块表
CREATE TABLE IF NOT EXISTS function_plugin_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '函数插件记录ID',
    plugin_id VARCHAR(100) NOT NULL COMMENT '插件标识',
    plugin_name VARCHAR(200) COMMENT '插件名称',
    plugin_version VARCHAR(50) NOT NULL COMMENT '插件版本',
    bucket_name VARCHAR(100) NOT NULL COMMENT '对象存储桶',
    object_key VARCHAR(255) NOT NULL COMMENT '对象存储路径',
    sha256 VARCHAR(64) NOT NULL COMMENT '文件摘要',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=已上传,1=已启用,2=已停用,3=加载失败',
    loaded_plugin_id VARCHAR(150) COMMENT 'PF4J运行时插件ID',
    remark VARCHAR(500) COMMENT '备注',
    functions TEXT NULL COMMENT '提供的函数列表(JSON数组)',
    config_schema TEXT NULL COMMENT '插件配置Schema(JSON)',
    config_defaults TEXT NULL COMMENT '插件配置默认值(JSON)',
    config_values TEXT NULL COMMENT '插件配置值(JSON)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '逻辑删除时间',
    INDEX idx_status (status),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='函数插件模块表';

-- 卡密登录 / 终端用户 WS 登录流水（按次记录，供仪表盘统计）
CREATE TABLE IF NOT EXISTS access_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_type VARCHAR(40) NOT NULL COMMENT 'CARD_LOGIN | CARD_LOGIN_FREE | APP_USER_WS_LOGIN',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    ref_id BIGINT NOT NULL COMMENT 'license_key.id 或 app_user.id；免费卡密登录为 0',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    INDEX idx_type_time (event_type, created_at),
    INDEX idx_app_time (app_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务访问事件（登录等）';

-- 三方凭证（用于调用三方加时接口）
CREATE TABLE IF NOT EXISTS third_party_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_id BIGINT NOT NULL COMMENT '绑定应用ID',
    name VARCHAR(120) NOT NULL COMMENT '凭证名称',
    api_key VARCHAR(120) NOT NULL COMMENT '调用凭证Key',
    api_secret VARCHAR(120) NOT NULL COMMENT '调用凭证Secret',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用,0禁用',
    allowed_ips TEXT NULL COMMENT 'IP白名单，逗号分隔',
    daily_limit INT NULL COMMENT '每日调用上限，NULL=不限制',
    total_call_limit BIGINT NULL COMMENT '总调用上限，NULL=不限制',
    total_days_limit BIGINT NULL COMMENT '总消耗天数上限，NULL=不限制',
    used_call_count BIGINT NOT NULL DEFAULT 0 COMMENT '已调用次数',
    used_days_count BIGINT NOT NULL DEFAULT 0 COMMENT '已消耗天数',
    expires_at DATETIME NULL COMMENT '凭证过期时间',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_tp_credential_api_key (api_key),
    INDEX idx_tp_credential_app_status (app_id, status),
    INDEX idx_tp_credential_expires (expires_at),
    INDEX idx_tp_credential_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三方加时调用凭证';

-- 三方加时调用日志
CREATE TABLE IF NOT EXISTS third_party_recharge_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    credential_id BIGINT NULL COMMENT '凭证ID',
    app_id BIGINT NULL COMMENT '应用ID',
    api_key VARCHAR(120) NULL COMMENT '调用方apiKey快照',
    user_email VARCHAR(200) NULL COMMENT '目标用户邮箱',
    days INT NULL COMMENT '加时天数',
    out_trade_no VARCHAR(120) NULL COMMENT '外部订单号',
    request_ip VARCHAR(64) NULL COMMENT '调用IP',
    request_ts BIGINT NULL COMMENT '请求时间戳(毫秒)',
    sign_valid TINYINT NOT NULL DEFAULT 0 COMMENT '签名是否通过:1是0否',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态:1成功,2失败',
    error_code VARCHAR(80) NULL COMMENT '错误码',
    error_message VARCHAR(500) NULL COMMENT '错误信息',
    idempotent_hit TINYINT NOT NULL DEFAULT 0 COMMENT '是否命中幂等',
    before_expires_at DATETIME NULL COMMENT '加时前到期时间',
    after_expires_at DATETIME NULL COMMENT '加时后到期时间',
    trace_id VARCHAR(80) NULL COMMENT '链路追踪ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tp_recharge_app_time (app_id, created_at),
    INDEX idx_tp_recharge_credential_time (credential_id, created_at),
    INDEX idx_tp_recharge_status_time (status, created_at),
    INDEX idx_tp_recharge_email_time (user_email, created_at),
    UNIQUE KEY uk_tp_recharge_credential_trade (credential_id, out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三方加时调用日志';

-- 三方凭证权限
INSERT IGNORE INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('查看三方凭证', 'THIRD_PARTY_CREDENTIAL_LIST', 'API', '/api/third-party/credentials', 'GET', '查看三方凭证列表', 1),
('查看三方凭证详情', 'THIRD_PARTY_CREDENTIAL_DETAIL', 'API', '/api/third-party/credentials/*', 'GET', '查看三方凭证详情', 1),
('创建三方凭证', 'THIRD_PARTY_CREDENTIAL_CREATE', 'API', '/api/third-party/credentials', 'POST', '创建三方凭证', 1),
('更新三方凭证', 'THIRD_PARTY_CREDENTIAL_UPDATE', 'API', '/api/third-party/credentials/*', 'PUT,POST', '更新/重置三方凭证', 1),
('删除三方凭证', 'THIRD_PARTY_CREDENTIAL_DELETE', 'API', '/api/third-party/credentials/*', 'DELETE', '删除三方凭证', 1),
('查看三方调用日志', 'THIRD_PARTY_CALL_LOG_LIST', 'API', '/api/third-party/recharge-logs', 'GET', '查看三方调用日志', 1);

-- 给超级管理员分配新增权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code IN (
      'THIRD_PARTY_CREDENTIAL_LIST',
      'THIRD_PARTY_CREDENTIAL_DETAIL',
      'THIRD_PARTY_CREDENTIAL_CREATE',
      'THIRD_PARTY_CREDENTIAL_UPDATE',
      'THIRD_PARTY_CREDENTIAL_DELETE',
      'THIRD_PARTY_CALL_LOG_LIST'
  );

-- 给管理员分配新增权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
      'THIRD_PARTY_CREDENTIAL_LIST',
      'THIRD_PARTY_CREDENTIAL_DETAIL',
      'THIRD_PARTY_CREDENTIAL_CREATE',
      'THIRD_PARTY_CREDENTIAL_UPDATE',
      'THIRD_PARTY_CREDENTIAL_DELETE',
      'THIRD_PARTY_CALL_LOG_LIST'
  );

-- 追加菜单（挂在应用管理下）
SET @app_management_menu_id = (SELECT id FROM menus WHERE menu_code = 'APP_MANAGEMENT' LIMIT 1);
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('三方凭证', 'THIRD_PARTY_CREDENTIAL_MANAGEMENT', IFNULL(@app_management_menu_id, 0), 2, '/applications/credentials', 'ThirdPartyCredentialManagement', 'lock', 50, 1, 1, NOW(), NOW()),
('调用日志', 'THIRD_PARTY_CALL_LOG_MANAGEMENT', IFNULL(@app_management_menu_id, 0), 2, '/applications/call-logs', 'CallLogManagement', 'audit', 51, 1, 1, NOW(), NOW());

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND m.menu_code IN ('THIRD_PARTY_CREDENTIAL_MANAGEMENT', 'THIRD_PARTY_CALL_LOG_MANAGEMENT');

-- ========================================
-- 会员体系表
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
    extra_app_quota INT NOT NULL DEFAULT 0 COMMENT '额外应用额度（购买）',
    extra_license_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外卡密额度（购买）',
    extra_user_register_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外用户注册额度（购买）',
    extra_traffic_quota BIGINT NOT NULL DEFAULT 0 COMMENT '额外流量额度（字节，购买）',
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

-- ========================================
-- 会员体系初始数据
-- ========================================

-- 会员等级初始数据
INSERT IGNORE INTO membership_level (level, level_name, price, app_quota, license_quota, user_register_quota, traffic_quota, duration_days, description, sort_order) VALUES
(1, '初级开发者', 0, 1, 200, 10, 52428800, 0, '新用户默认等级，基础功能体验', 1),
(2, '铜牌开发者', 9.90, 3, 1000, 50, 2147483648, 0, '适合个人开发者，满足基本需求', 2),
(3, '银牌开发者', 29.90, 10, 5000, 200, 5368709120, 0, '适合小型团队，更多额度支持', 3),
(4, '金牌开发者', 59.90, 30, 20000, 1000, 10737418240, 0, '适合中型团队，丰富功能体验', 4),
(5, '永久会员', 99.00, -1, -1, -1, 21474836480, 0, '尊享永久会员，不限额度', 5);

-- 额度商品初始数据
INSERT IGNORE INTO quota_product (product_code, product_name, product_type, quota_value, price, description, sort_order) VALUES
('APP_QUOTA_1', '创建应用 x1', 'APP_QUOTA', 1, 500, '增加1个应用创建额度', 1),
('APP_QUOTA_5', '创建应用 x5', 'APP_QUOTA', 5, 2000, '增加5个应用创建额度', 2),
('APP_QUOTA_10', '创建应用 x10', 'APP_QUOTA', 10, 3500, '增加10个应用创建额度', 3),
('LICENSE_QUOTA_100', '卡密额度 x100', 'LICENSE_QUOTA', 100, 300, '增加100张卡密创建额度', 10),
('LICENSE_QUOTA_500', '卡密额度 x500', 'LICENSE_QUOTA', 500, 1200, '增加500张卡密创建额度', 11),
('LICENSE_QUOTA_1000', '卡密额度 x1000', 'LICENSE_QUOTA', 1000, 2000, '增加1000张卡密创建额度', 12),
('USER_REGISTER_QUOTA_10', '终端用户注册 x10', 'USER_REGISTER_QUOTA', 10, 200, '增加10个终端用户注册额度', 20),
('USER_REGISTER_QUOTA_50', '终端用户注册 x50', 'USER_REGISTER_QUOTA', 50, 800, '增加50个终端用户注册额度', 21),
('USER_REGISTER_QUOTA_100', '终端用户注册 x100', 'USER_REGISTER_QUOTA', 100, 1500, '增加100个终端用户注册额度', 22),
('TRAFFIC_QUOTA_1GB', '流量额度 x1GB', 'TRAFFIC_QUOTA', 1073741824, 500, '增加1GB流量额度', 30),
('TRAFFIC_QUOTA_5GB', '流量额度 x5GB', 'TRAFFIC_QUOTA', 5368709120, 2000, '增加5GB流量额度', 31),
('TRAFFIC_QUOTA_10GB', '流量额度 x10GB', 'TRAFFIC_QUOTA', 10737418240, 3500, '增加10GB流量额度', 32),
('MEMBERSHIP_UPGRADE_2', '升级铜牌会员', 'MEMBERSHIP', 2, 990, '升级为铜牌开发者会员', 40),
('MEMBERSHIP_UPGRADE_3', '升级银牌会员', 'MEMBERSHIP', 3, 2990, '升级为银牌开发者会员', 41),
('MEMBERSHIP_UPGRADE_4', '升级金牌会员', 'MEMBERSHIP', 4, 5990, '升级为金牌开发者会员', 42),
('MEMBERSHIP_UPGRADE_5', '升级永久会员', 'MEMBERSHIP', 5, 9900, '升级为永久会员', 43);

-- 会员管理权限
INSERT IGNORE INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('会员等级列表', 'MEMBERSHIP_LEVEL_LIST', 'API', '/api/membership/levels', 'GET', '查看会员等级列表', 1),
('创建会员等级', 'MEMBERSHIP_LEVEL_CREATE', 'API', '/api/membership/levels', 'POST', '创建会员等级', 1),
('更新会员等级', 'MEMBERSHIP_LEVEL_UPDATE', 'API', '/api/membership/levels/*', 'PUT', '更新会员等级', 1),
('删除会员等级', 'MEMBERSHIP_LEVEL_DELETE', 'API', '/api/membership/levels/*', 'DELETE', '删除会员等级', 1),
('用户会员列表', 'USER_MEMBERSHIP_LIST', 'API', '/api/membership/users', 'GET', '查看用户会员列表', 1),
('用户会员详情', 'USER_MEMBERSHIP_DETAIL', 'API', '/api/membership/users/*', 'GET', '查看用户会员详情', 1),
('更新用户会员', 'USER_MEMBERSHIP_UPDATE', 'API', '/api/membership/users/*', 'PUT', '更新用户会员信息', 1),
('管理员充值余额', 'USER_MEMBERSHIP_GRANT_BALANCE', 'API', '/api/membership/users/*/grant-balance', 'POST', '管理员为用户充值余额', 1),
('额度商品列表', 'QUOTA_PRODUCT_LIST', 'API', '/api/quota-products', 'GET', '查看额度商品列表', 1),
('创建额度商品', 'QUOTA_PRODUCT_CREATE', 'API', '/api/quota-products', 'POST', '创建额度商品', 1),
('更新额度商品', 'QUOTA_PRODUCT_UPDATE', 'API', '/api/quota-products/*', 'PUT', '更新额度商品', 1),
('删除额度商品', 'QUOTA_PRODUCT_DELETE', 'API', '/api/quota-products/*', 'DELETE', '删除额度商品', 1),
('支付订单列表', 'PAYMENT_ORDER_LIST', 'API', '/api/payment/orders', 'GET', '查看支付订单列表', 1),
('支付订单详情', 'PAYMENT_ORDER_DETAIL', 'API', '/api/payment/orders/*', 'GET', '查看支付订单详情', 1),
('管理员订单列表', 'PAYMENT_ORDER_ADMIN_LIST', 'API', '/api/admin/payment/orders', 'GET', '管理员查看所有订单', 1),
('管理员订单操作', 'PAYMENT_ORDER_ADMIN_UPDATE', 'API', '/api/admin/payment/orders/*', 'PUT', '管理员修改订单状态', 1),
('管理员订单退款', 'PAYMENT_ORDER_ADMIN_REFUND', 'API', '/api/admin/payment/orders/*/refund', 'POST', '管理员退款', 1),
('管理员手动发放', 'PAYMENT_ORDER_ADMIN_GRANT', 'API', '/api/admin/payment/grant', 'POST', '管理员手动发放订单', 1),
('工单列表', 'TICKET_LIST', 'API', '/api/tickets', 'GET', '查看工单列表', 1),
('工单详情', 'TICKET_DETAIL', 'API', '/api/tickets/*', 'GET', '查看工单详情', 1),
('创建工单', 'TICKET_CREATE', 'API', '/api/tickets', 'POST', '创建工单', 1),
('工单消息', 'TICKET_MESSAGE', 'API', '/api/tickets/*/messages', 'POST', '发送工单消息', 1),
('关闭工单', 'TICKET_CLOSE', 'API', '/api/tickets/*/close', 'POST', '关闭工单', 1),
('催办工单', 'TICKET_URGE', 'API', '/api/tickets/*/urge', 'POST', '催办工单', 1),
('管理员工单列表', 'TICKET_ADMIN_LIST', 'API', '/api/admin/tickets', 'GET', '管理员查看所有工单', 1),
('管理员工单详情', 'TICKET_ADMIN_DETAIL', 'API', '/api/admin/tickets/*', 'GET', '管理员查看工单详情', 1),
('管理员分配工单', 'TICKET_ADMIN_ASSIGN', 'API', '/api/admin/tickets/*/assign', 'PUT', '管理员分配工单', 1),
('管理员回复工单', 'TICKET_ADMIN_REPLY', 'API', '/api/admin/tickets/*/messages', 'POST', '管理员回复工单', 1),
('管理员更新工单状态', 'TICKET_ADMIN_UPDATE_STATUS', 'API', '/api/admin/tickets/*/status', 'PUT', '管理员更新工单状态', 1);

-- 为超级管理员分配新增权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN (
    'MEMBERSHIP_LEVEL_LIST', 'MEMBERSHIP_LEVEL_CREATE', 'MEMBERSHIP_LEVEL_UPDATE', 'MEMBERSHIP_LEVEL_DELETE',
    'USER_MEMBERSHIP_LIST', 'USER_MEMBERSHIP_DETAIL', 'USER_MEMBERSHIP_UPDATE', 'USER_MEMBERSHIP_GRANT_BALANCE',
    'QUOTA_PRODUCT_LIST', 'QUOTA_PRODUCT_CREATE', 'QUOTA_PRODUCT_UPDATE', 'QUOTA_PRODUCT_DELETE',
    'PAYMENT_ORDER_LIST', 'PAYMENT_ORDER_DETAIL', 'PAYMENT_ORDER_ADMIN_LIST', 'PAYMENT_ORDER_ADMIN_UPDATE',
    'PAYMENT_ORDER_ADMIN_REFUND', 'PAYMENT_ORDER_ADMIN_GRANT',
    'TICKET_LIST', 'TICKET_DETAIL', 'TICKET_CREATE', 'TICKET_MESSAGE', 'TICKET_CLOSE', 'TICKET_URGE',
    'TICKET_ADMIN_LIST', 'TICKET_ADMIN_DETAIL', 'TICKET_ADMIN_ASSIGN', 'TICKET_ADMIN_REPLY', 'TICKET_ADMIN_UPDATE_STATUS'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 为管理员分配部分新增权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'ADMIN'
AND p.permission_code IN (
    'TICKET_LIST', 'TICKET_DETAIL', 'TICKET_CREATE', 'TICKET_MESSAGE', 'TICKET_CLOSE', 'TICKET_URGE',
    'TICKET_ADMIN_LIST', 'TICKET_ADMIN_DETAIL', 'TICKET_ADMIN_ASSIGN', 'TICKET_ADMIN_REPLY', 'TICKET_ADMIN_UPDATE_STATUS'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 为普通用户分配用户端权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN (
    'TICKET_LIST', 'TICKET_DETAIL', 'TICKET_CREATE', 'TICKET_MESSAGE', 'TICKET_CLOSE', 'TICKET_URGE'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 我的会员菜单（顶级，所有登录用户可见）
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('我的会员', 'MY_MEMBERSHIP', 0, 1, '/user', NULL, 'crown', 5, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

SET @my_membership_id = (SELECT id FROM menus WHERE menu_code = 'MY_MEMBERSHIP');

INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('会员信息', 'MEMBERSHIP_INFO', @my_membership_id, 2, '/user/membership', 'MembershipInfo', NULL, 1, 1, 1, NOW(), NOW()),
('余额管理', 'BALANCE_MANAGEMENT', @my_membership_id, 2, '/user/balance', 'Balance', NULL, 2, 1, 1, NOW(), NOW()),
('每日签到', 'DAILY_CHECKIN', @my_membership_id, 2, '/user/checkin', 'Checkin', NULL, 3, 1, 1, NOW(), NOW()),
('邀请有奖', 'INVITE_REWARD', @my_membership_id, 2, '/user/invite', 'InviteReward', NULL, 4, 1, 1, NOW(), NOW()),
('我的订单', 'MY_ORDERS', @my_membership_id, 2, '/user/orders', 'UserOrders', NULL, 5, 1, 1, NOW(), NOW()),
('我的工单', 'MY_TICKETS', @my_membership_id, 2, '/user/tickets', 'UserTickets', NULL, 6, 1, 1, NOW(), NOW());

-- 为所有角色分配我的会员菜单
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'USER')
AND m.menu_code IN ('MY_MEMBERSHIP', 'MEMBERSHIP_INFO', 'BALANCE_MANAGEMENT', 'DAILY_CHECKIN', 'INVITE_REWARD', 'MY_ORDERS', 'MY_TICKETS')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 调整系统管理菜单顺序
UPDATE menus SET sort_order = 6 WHERE menu_code = 'SYSTEM_MANAGEMENT';
UPDATE menus SET sort_order = 7 WHERE menu_code = 'PROFILE';

-- 创建会员管理顶级菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('会员管理', 'MEMBERSHIP_MANAGEMENT', 0, 1, '/membership', NULL, 'crown', 3, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE path = '/membership', icon = 'crown', sort_order = 3;

SET @membership_mgmt_id = (SELECT id FROM menus WHERE menu_code = 'MEMBERSHIP_MANAGEMENT');

-- 创建运营管理顶级菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('运营管理', 'OPERATION_MANAGEMENT', 0, 1, '/operation', NULL, 'file-text', 4, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE path = '/operation', icon = 'file-text', sort_order = 4;

SET @operation_mgmt_id = (SELECT id FROM menus WHERE menu_code = 'OPERATION_MANAGEMENT');

-- 会员管理子菜单
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('会员等级管理', 'MEMBERSHIP_LEVEL_MANAGEMENT', @membership_mgmt_id, 2, '/membership/levels', 'SystemManagement', 'crown', 1, 1, 1, NOW(), NOW()),
('用户会员管理', 'USER_MEMBERSHIP_MANAGEMENT', @membership_mgmt_id, 2, '/membership/users', 'SystemManagement', 'team', 2, 1, 1, NOW(), NOW()),
('额度商品管理', 'QUOTA_PRODUCT_MANAGEMENT', @membership_mgmt_id, 2, '/membership/products', 'SystemManagement', 'shopping', 3, 1, 1, NOW(), NOW());

-- 运营管理子菜单
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('订单管理', 'ORDER_MANAGEMENT', @operation_mgmt_id, 2, '/operation/orders', 'SystemManagement', 'file-text', 1, 1, 1, NOW(), NOW()),
('工单管理', 'TICKET_MANAGEMENT', @operation_mgmt_id, 2, '/operation/tickets', 'SystemManagement', 'message', 2, 1, 1, NOW(), NOW());

-- 为超级管理员分配新增菜单
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code IN ('MEMBERSHIP_MANAGEMENT', 'OPERATION_MANAGEMENT', 'MEMBERSHIP_LEVEL_MANAGEMENT', 'USER_MEMBERSHIP_MANAGEMENT', 'QUOTA_PRODUCT_MANAGEMENT', 'ORDER_MANAGEMENT', 'TICKET_MANAGEMENT')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ========================================
-- 文档管理模块表
-- ========================================

-- 文档分类表
CREATE TABLE IF NOT EXISTS doc_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description VARCHAR(500) COMMENT '分类描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sort_order (sort_order),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分类表';

-- 文档表
CREATE TABLE IF NOT EXISTS doc_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    category_id BIGINT NOT NULL COMMENT '所属分类ID',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content MEDIUMTEXT COMMENT '文档内容(Markdown)',
    author_name VARCHAR(100) COMMENT '作者名称',
    author_avatar VARCHAR(255) COMMENT '作者头像URL',
    author_id BIGINT COMMENT '作者用户ID',
    author_github VARCHAR(255) COMMENT '作者GitHub地址',
    author_qq VARCHAR(50) COMMENT '作者QQ',
    author_bilibili VARCHAR(255) COMMENT '作者B站主页',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-草稿，-1-已删除',
    view_count BIGINT DEFAULT 0 COMMENT '浏览次数',
    created_by BIGINT COMMENT '创建者ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category_id (category_id),
    INDEX idx_status (status),
    INDEX idx_author_id (author_id),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 文档附件表
CREATE TABLE IF NOT EXISTS doc_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '附件ID',
    doc_id BIGINT NOT NULL COMMENT '所属文档ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
    file_url VARCHAR(500) NOT NULL COMMENT '文件URL',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    file_type VARCHAR(100) COMMENT '文件类型(MIME)',
    download_count INT DEFAULT 0 COMMENT '下载次数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_doc_id (doc_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档附件表';

-- 文档管理权限
INSERT IGNORE INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('文档分类列表', 'DOC_CATEGORY_LIST', 'API', '/api/doc-categories', 'GET', '查看文档分类列表', 1),
('文档分类详情', 'DOC_CATEGORY_DETAIL', 'API', '/api/doc-categories/*', 'GET', '查看文档分类详情', 1),
('创建文档分类', 'DOC_CATEGORY_CREATE', 'API', '/api/doc-categories', 'POST', '创建文档分类', 1),
('更新文档分类', 'DOC_CATEGORY_UPDATE', 'API', '/api/doc-categories/*', 'PUT', '更新文档分类', 1),
('删除文档分类', 'DOC_CATEGORY_DELETE', 'API', '/api/doc-categories/*', 'DELETE', '删除文档分类', 1),
('文档列表', 'DOC_ITEM_LIST', 'API', '/api/doc-items', 'GET', '查看文档列表', 1),
('文档详情', 'DOC_ITEM_DETAIL', 'API', '/api/doc-items/*', 'GET', '查看文档详情', 1),
('创建文档', 'DOC_ITEM_CREATE', 'API', '/api/doc-items', 'POST', '创建文档', 1),
('更新文档', 'DOC_ITEM_UPDATE', 'API', '/api/doc-items/*', 'PUT', '更新文档', 1),
('删除文档', 'DOC_ITEM_DELETE', 'API', '/api/doc-items/*', 'DELETE', '删除文档', 1);

-- 超级管理员分配文档管理权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN (
    'DOC_CATEGORY_LIST', 'DOC_CATEGORY_DETAIL', 'DOC_CATEGORY_CREATE', 'DOC_CATEGORY_UPDATE', 'DOC_CATEGORY_DELETE',
    'DOC_ITEM_LIST', 'DOC_ITEM_DETAIL', 'DOC_ITEM_CREATE', 'DOC_ITEM_UPDATE', 'DOC_ITEM_DELETE'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 普通用户分配文档查看权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN (
    'DOC_CATEGORY_LIST', 'DOC_CATEGORY_DETAIL',
    'DOC_ITEM_LIST', 'DOC_ITEM_DETAIL'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 对接文档（顶级菜单，子菜单由API动态构建）
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('对接文档', 'DOC_MANAGEMENT', 0, 1, '/docs', NULL, 'book', 8, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name='对接文档', sort_order=VALUES(sort_order);

-- 所有角色分配对接文档菜单
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'USER')
AND m.menu_code = 'DOC_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
SET @sys_mgmt_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'SYSTEM_MANAGEMENT') AS tmp);
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('文档管理', 'DOC_MANAGEMENT_ADMIN', @sys_mgmt_id, 2, '/admin/docs/categories', 'DocManagement', 'book', 8, 1, 1, NOW(), NOW());

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
AND m.menu_code = 'DOC_MANAGEMENT_ADMIN';

-- ========================================
-- 系统公告模块表
-- ========================================

-- 系统公告表
CREATE TABLE IF NOT EXISTS system_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID',
    title VARCHAR(200) NOT NULL COMMENT '公告标题',
    content MEDIUMTEXT COMMENT '公告内容',
    status TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    created_by BIGINT COMMENT '创建者ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 公告管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('公告列表', 'ANNOUNCEMENT_LIST', 'API', '/api/announcements', 'GET', '查看公告列表', 1),
('公告详情', 'ANNOUNCEMENT_DETAIL', 'API', '/api/announcements/*', 'GET', '查看公告详情', 1),
('创建公告', 'ANNOUNCEMENT_CREATE', 'API', '/api/announcements', 'POST', '创建公告', 1),
('更新公告', 'ANNOUNCEMENT_UPDATE', 'API', '/api/announcements/*', 'PUT', '更新公告', 1),
('删除公告', 'ANNOUNCEMENT_DELETE', 'API', '/api/announcements/*', 'DELETE', '删除公告', 1),
('公告查看', 'ANNOUNCEMENT_VIEW', 'API', '/api/announcements/latest', 'GET', '查看最新公告', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 超级管理员分配公告管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN ('ANNOUNCEMENT_LIST', 'ANNOUNCEMENT_DETAIL', 'ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT_UPDATE', 'ANNOUNCEMENT_DELETE', 'ANNOUNCEMENT_VIEW')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 普通用户分配公告查看权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('ANNOUNCEMENT_VIEW')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入公告管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('公告管理', 'ANNOUNCEMENT_MANAGEMENT', (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'SYSTEM_MANAGEMENT' LIMIT 1) AS tmp), 2, '/system/announcements', 'SystemManagement', 'notification', 7, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 超级管理员分配公告管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code = 'ANNOUNCEMENT_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
