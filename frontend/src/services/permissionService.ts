import type { ApiResponse } from './apiResponse';
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
  getPermissions: () => request.get<unknown, ApiResponse<Permission[]>>('/permissions'),

  // 获取权限详情
  getPermissionById: (id: number) => request.get<unknown, ApiResponse<Permission>>(`/permissions/${id}`),

  // 创建权限
  createPermission: (permission: Omit<Permission, 'id'>) => request.post<unknown, ApiResponse<string>>('/permissions', permission),

  // 更新权限
  updatePermission: (id: number, permission: Partial<Permission>) => 
    request.put<unknown, ApiResponse<string>>(`/permissions/${id}`, permission),

  // 删除权限
  deletePermission: (id: number) => request.delete<unknown, ApiResponse<string>>(`/permissions/${id}`),

  // 批量删除权限
  batchDeletePermissions: (ids: number[]) => request.delete<unknown, ApiResponse<string>>('/permissions/batch', { data: ids }),

  // 获取资源类型列表
  getResourceTypes: () => request.get<unknown, ApiResponse<string[]>>('/permissions/resource-types'),

  // 获取HTTP方法列表
  getHttpMethods: () => request.get<unknown, ApiResponse<string[]>>('/permissions/http-methods'),
};