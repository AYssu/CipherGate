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

// 从 activityService 导出
export { activityApi } from './activityService';
export type { ActivityLog, PageResult } from './activityService';

export { dashboardApi } from './dashboardService';
export type { DashboardTodayStats, DashboardOverview, DashboardOnlineStats, DashboardTrendPoint } from './dashboardService';

// 从 appUserService 导出
export * from './appUserService';

// 从 pluginService 导出
export * from './pluginService';
export * from './appAgentService';

// 从 docService 导出
export { docApi } from './docService';
export type { DocCategory, DocMenuItem, DocMenuCategory, DocDetail, DocAttachment, DocItem } from './docService';