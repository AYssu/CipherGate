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

-- 检查是否是首次初始化，如果是则分配默认权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.role_code = 'SUPER_ADMIN'
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.role_code = 'ADMIN' 
AND p.permission_code NOT IN ('USER_DELETE', 'PERMISSION_CREATE', 'PERMISSION_UPDATE', 'PERMISSION_DELETE')
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.role_code = 'USER' 
AND p.permission_code IN ('PROFILE_VIEW', 'PROFILE_UPDATE')
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

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
('个人中心', 'PROFILE', 0, 2, '/userinfo', 'UserInfo', 'user', 3, 1, 1);

-- 插入系统管理子菜单（使用变量方式）
SET @system_management_id = (SELECT id FROM menus WHERE menu_code = 'SYSTEM_MANAGEMENT');

INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status) VALUES
('用户管理', 'USER_MANAGEMENT', @system_management_id, 2, '/system?tab=users', 'SystemManagement', 'team', 1, 1, 1),
('角色管理', 'ROLE_MANAGEMENT', @system_management_id, 2, '/system?tab=roles', 'SystemManagement', 'safety', 2, 1, 1),
('菜单管理', 'MENU_MANAGEMENT', @system_management_id, 2, '/system?tab=menus', 'SystemManagement', 'menu', 3, 1, 1),
('权限管理', 'PERMISSION_MANAGEMENT', @system_management_id, 2, '/system?tab=permissions', 'SystemManagement', 'lock', 4, 1, 1),
('系统配置', 'SYSTEM_CONFIG', @system_management_id, 2, '/system?tab=config', 'SystemManagement', 'tool', 5, 1, 1);


-- 检查是否是首次初始化，如果是则分配默认菜单权限
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'SUPER_ADMIN'
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'ADMIN' 
AND m.menu_code NOT IN ('PERMISSION_MANAGEMENT')
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m 
WHERE r.role_code = 'USER' 
AND m.menu_code IN ('DASHBOARD', 'PROFILE')
AND NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'SYSTEM_INITIALIZED');

-- 系统配置表
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

-- 标记系统已完成初始化（只在首次运行时插入）
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES 
('SYSTEM_INITIALIZED', 'true', '系统初始化标记，用于判断是否已完成首次初始化');

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
