-- 将会员和工单管理从系统管理移到独立的顶级菜单

-- 创建会员管理顶级菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('会员管理', 'MEMBERSHIP_MANAGEMENT', 0, 1, '/membership', NULL, 'crown', 3, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE path = '/membership', icon = 'crown', sort_order = 3;

-- 创建运营管理顶级菜单
INSERT INTO menus (menu_name, menu_code, parent_id, menu_type, path, component, icon, sort_order, visible, status, created_at, updated_at)
VALUES ('运营管理', 'OPERATION_MANAGEMENT', 0, 1, '/operation', NULL, 'file-text', 4, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE path = '/operation', icon = 'file-text', sort_order = 4;

-- 移动会员等级管理到会员管理下
UPDATE menus SET parent_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'MEMBERSHIP_MANAGEMENT' LIMIT 1) AS tmp), path = '/membership/levels', sort_order = 1
WHERE menu_code = 'MEMBERSHIP_LEVEL_MANAGEMENT';

-- 移动用户会员管理到会员管理下
UPDATE menus SET parent_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'MEMBERSHIP_MANAGEMENT' LIMIT 1) AS tmp), path = '/membership/users', sort_order = 2
WHERE menu_code = 'USER_MEMBERSHIP_MANAGEMENT';

-- 移动额度商品管理到会员管理下
UPDATE menus SET parent_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'MEMBERSHIP_MANAGEMENT' LIMIT 1) AS tmp), path = '/membership/products', sort_order = 3
WHERE menu_code = 'QUOTA_PRODUCT_MANAGEMENT';

-- 移动订单管理到运营管理下
UPDATE menus SET parent_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'OPERATION_MANAGEMENT' LIMIT 1) AS tmp), path = '/operation/orders', sort_order = 1
WHERE menu_code = 'ORDER_MANAGEMENT';

-- 移动工单管理到运营管理下
UPDATE menus SET parent_id = (SELECT id FROM (SELECT id FROM menus WHERE menu_code = 'OPERATION_MANAGEMENT' LIMIT 1) AS tmp), path = '/operation/tickets', sort_order = 2
WHERE menu_code = 'TICKET_MANAGEMENT';
