-- 修复文档管理菜单路径：/docs/categories -> /admin/docs/categories
-- 原因：nginx 将 /docs/ 映射到静态文档，前端路由需要使用 /admin/docs/

UPDATE menus SET path = '/admin/docs/categories' WHERE menu_code = 'DOC_MANAGEMENT_ADMIN' AND path = '/docs/categories';
