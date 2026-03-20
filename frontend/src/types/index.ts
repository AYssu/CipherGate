// 通用API响应类型（匹配后端Result类）
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
  success: boolean;
}

// 用户类型
export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  status: 'active' | 'inactive';
  createdAt: string;
  updatedAt: string;
}

// 菜单项类型
export interface MenuItem {
  key: string;
  icon?: React.ReactNode;
  label: string;
  children?: MenuItem[];
}

// 菜单管理类型
export interface Menu {
  id: number;
  menuName: string;
  menuCode: string;
  parentId: number;
  menuType: number; // 1-目录，2-菜单，3-按钮
  path?: string;
  component?: string;
  icon?: string;
  sortOrder: number;
  visible: number; // 1-显示，0-隐藏
  status: number; // 1-启用，0-禁用
  createdAt?: string;
  updatedAt?: string;
  children?: Menu[];
}

// 角色类型
export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

// 权限类型
export interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  resourceType: string;
  resourcePath?: string;
  httpMethod?: string;
  description?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}