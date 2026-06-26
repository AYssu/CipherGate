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

-- 插入文档管理权限
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

-- 为超级管理员分配文档管理权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'SUPER_ADMIN'
AND p.permission_code IN (
    'DOC_CATEGORY_LIST', 'DOC_CATEGORY_DETAIL', 'DOC_CATEGORY_CREATE', 'DOC_CATEGORY_UPDATE', 'DOC_CATEGORY_DELETE',
    'DOC_ITEM_LIST', 'DOC_ITEM_DETAIL', 'DOC_ITEM_CREATE', 'DOC_ITEM_UPDATE', 'DOC_ITEM_DELETE'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 为普通用户分配文档查看权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.role_code = 'USER'
AND p.permission_code IN (
    'DOC_CATEGORY_LIST', 'DOC_CATEGORY_DETAIL',
    'DOC_ITEM_LIST', 'DOC_ITEM_DETAIL'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 插入文档管理菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('文档管理', 'DOC_MANAGEMENT', 0, 1, '/docs', NULL, 'book', 8, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), sort_order=VALUES(sort_order);

-- 获取文档管理菜单ID
SET @doc_menu_id = (SELECT id FROM menus WHERE menu_code = 'DOC_MANAGEMENT');

-- 插入文档管理子菜单
INSERT IGNORE INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at) VALUES
('文档分类', 'DOC_CATEGORY_PAGE', @doc_menu_id, 2, '/docs/categories', 'DocManagement', NULL, 1, 1, 1, NOW(), NOW());

-- 为超级管理员分配文档管理菜单
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'SUPER_ADMIN'
AND m.menu_code IN ('DOC_MANAGEMENT', 'DOC_CATEGORY_PAGE')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 为普通用户分配文档管理菜单
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r, menus m
WHERE r.role_code = 'USER'
AND m.menu_code IN ('DOC_MANAGEMENT', 'DOC_CATEGORY_PAGE')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
