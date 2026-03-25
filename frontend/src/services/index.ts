// API 服务统一导出
export { MenuService } from './menuService';

// 从 roleService 导出
export { roleApi } from './roleService';
export type { Role, Permission } from './roleService';

// 从 permissionService 导出（只导出 API，类型使用 roleService 的）
export { permissionApi } from './permissionService';

// 从 userService 导出（只导出 API 和 User、Menu 类型，Role 使用 roleService 的）
export { userApi } from './userService';
export type { User, Menu } from './userService';

// 从 systemService 导出
export * from './systemService';