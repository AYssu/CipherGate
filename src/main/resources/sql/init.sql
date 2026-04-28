-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    github_id VARCHAR(50) NOT NULL UNIQUE,
    login VARCHAR(100) NOT NULL,
    name VARCHAR(200),
    email VARCHAR(200),
    avatar_url TEXT,
    access_token TEXT,
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
AND p.permission_code IN ('PROFILE_VIEW', 'PROFILE_UPDATE')
AND EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED' AND config_value = 'false');

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
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

-- Spring Session 属性表
CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES LONGBLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

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
) COMMENT='活动日志表';

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
) COMMENT='系统消息表';

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
) COMMENT='用户消息关联表';


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

-- 为超级管理员和管理员分配插件管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND p.permission_code IN ('PLUGIN_LIST', 'PLUGIN_UPLOAD', 'PLUGIN_ENABLE', 'PLUGIN_DISABLE', 'PLUGIN_DELETE')
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

-- 为超级管理员和管理员分配插件管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND m.menu_code IN ('PLUGIN_MANAGEMENT', 'PLUGIN_LIST_PAGE')
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
    UNIQUE KEY uk_plugin_version (plugin_id, plugin_version, deleted, deleted_at),
    INDEX idx_status (status),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件模块表';

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

-- 用户试用记录表
CREATE TABLE IF NOT EXISTS app_user_trial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    trial_started_at DATETIME NOT NULL COMMENT '试用开始时间',
    trial_expires_at DATETIME NOT NULL COMMENT '试用到期时间',
    device_id VARCHAR(255) COMMENT '试用设备',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_app_user (app_id, user_id),
    INDEX idx_app (app_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户试用记录表';
