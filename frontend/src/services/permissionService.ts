import request from '../utils/request';

export interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  description: string;
  resourceType: string;
  resourcePath: string;
  httpMethod: string;
  status: number;
}

// 权限管理 API
export const permissionApi = {
  // 获取权限列表
  getPermissions: () => request.get<{ data: Permission[] }>('/permissions'),

  // 获取权限详情
  getPermissionById: (id: number) => request.get<{ data: Permission }>(`/permissions/${id}`),

  // 创建权限
  createPermission: (permission: Omit<Permission, 'id'>) => request.post('/permissions', permission),

  // 更新权限
  updatePermission: (id: number, permission: Partial<Permission>) => 
    request.put(`/permissions/${id}`, permission),

  // 删除权限
  deletePermission: (id: number) => request.delete(`/permissions/${id}`),

  // 批量删除权限
  batchDeletePermissions: (ids: number[]) => request.delete('/permissions/batch', { data: ids }),

  // 获取资源类型列表
  getResourceTypes: () => request.get<{ data: string[] }>('/permissions/resource-types'),

  // 获取HTTP方法列表
  getHttpMethods: () => request.get<{ data: string[] }>('/permissions/http-methods'),
};