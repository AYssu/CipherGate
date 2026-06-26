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

-- 插入公告管理权限
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status) VALUES
('公告列表', 'ANNOUNCEMENT_LIST', 'API', '/api/announcements', 'GET', '查看公告列表', 1),
('公告详情', 'ANNOUNCEMENT_DETAIL', 'API', '/api/announcements/*', 'GET', '查看公告详情', 1),
('创建公告', 'ANNOUNCEMENT_CREATE', 'API', '/api/announcements', 'POST', '创建公告', 1),
('更新公告', 'ANNOUNCEMENT_UPDATE', 'API', '/api/announcements/*', 'PUT', '更新公告', 1),
('删除公告', 'ANNOUNCEMENT_DELETE', 'API', '/api/announcements/*', 'DELETE', '删除公告', 1),
('公告查看', 'ANNOUNCEMENT_VIEW', 'API', '/api/announcements/latest', 'GET', '查看最新公告', 1)
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name);

-- 为超级管理员分配公告管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN ('ANNOUNCEMENT_LIST', 'ANNOUNCEMENT_DETAIL', 'ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT_UPDATE', 'ANNOUNCEMENT_DELETE', 'ANNOUNCEMENT_VIEW')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 为普通用户分配公告查看权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN ('ANNOUNCEMENT_VIEW')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 插入公告管理菜单（在系统管理子菜单下）
SET @system_management_id = (SELECT id FROM menus WHERE menu_code = 'SYSTEM_MANAGEMENT');

INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('公告管理', 'ANNOUNCEMENT_MANAGEMENT', @system_management_id, 2, '/system/announcements', 'SystemManagement', 'notification', 7, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 为超级管理员分配公告管理菜单
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code = 'ANNOUNCEMENT_MANAGEMENT'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
