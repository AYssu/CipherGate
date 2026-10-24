-- 成功执行的批次才提交；唯一 request_id 阻止重复请求，失败时随事务回滚。
CREATE TABLE IF NOT EXISTS app_user_transfer_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    operator_id BIGINT NOT NULL,
    source_app_id BIGINT NOT NULL,
    target_app_id BIGINT NOT NULL,
    mode VARCHAR(8) NOT NULL,
    force_overwrite BOOLEAN NOT NULL DEFAULT FALSE,
    create_count INT NOT NULL,
    overwrite_count INT NOT NULL,
    skip_count INT NOT NULL,
    selected_ids JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_transfer_request (request_id),
    INDEX idx_transfer_operator (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终端用户迁移批次（幂等与审计）';

-- 独立的高级用户迁移菜单。沿用角色授权，实际操作额外校验双应用所有权。
INSERT INTO permissions (permission_name, permission_code, resource_type, resource_path, http_method, description, status)
VALUES ('迁移/复制终端用户', 'APP_USER_TRANSFER', 'API', '/api/app-user-transfers/**', NULL, '仅源和目标应用所有者或管理员可操作', 1)
ON DUPLICATE KEY UPDATE permission_code=VALUES(permission_code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'USER') AND p.permission_code='APP_USER_TRANSFER'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('用户迁移', 'APP_USER_TRANSFER', (SELECT id FROM (SELECT id FROM menus WHERE menu_code='APP_MANAGEMENT') AS tmp),
2, '/applications/user-transfer', 'AppUserTransfer', 'tool', 8, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);

INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'USER') AND m.menu_code='APP_USER_TRANSFER'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
