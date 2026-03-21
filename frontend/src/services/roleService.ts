import request from '../utils/request';

export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
  permissions?: Permission[];
}

export interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  description: string;
  resourceType: string;
  resourcePath: string;
  httpMethod: string;
}

// 角色管理 API
export const roleApi = {
  // 获取角色列表
  getRoles: () => request.get<{ data: Role[] }>('/roles'),

  // 创建角色
  createRole: (role: Omit<Role, 'id'>) => request.post('/roles', role),

  // 更新角色
  updateRole: (id: number, role: Partial<Role>) => request.put(`/roles/${id}`, role),

  // 删除角色
  deleteRole: (id: number) => request.delete(`/roles/${id}`),

  // 分配角色给用户
  assignRoles: (userId: number, roleIds: number[]) => 
    request.post('/roles/assign', null, { params: { userId, roleIds } }),

  // 获取角色的菜单权限
  getRoleMenus: (roleId: number) => request.get<{ data: number[] }>(`/roles/${roleId}/menus`),

  // 分配菜单权限给角色
  assignMenusToRole: (roleId: number, menuIds: number[]) => 
    request.post(`/roles/${roleId}/menus`, menuIds),

  // 获取角色的API权限
  getRolePermissions: (roleId: number) => request.get<{ data: number[] }>(`/roles/${roleId}/permissions`),

  // 分配API权限给角色
  assignPermissionsToRole: (roleId: number, permissionIds: number[]) => 
    request.post(`/roles/${roleId}/permissions`, permissionIds),
};