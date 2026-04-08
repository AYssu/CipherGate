import request from '../utils/request';

// 应用终端用户类型定义
export interface AppUser {
  id: number;
  appId: number;
  username: string;
  email?: string;
  phone?: string;
  nickname?: string;
  avatarUrl?: string;
  signature?: string;
  loginCount: number;
  lastLoginAt?: string;
  lastLoginIp?: string;
  createdAt: string;
  updatedAt: string;
  appName?: string;
  bindingCount?: number;
}

export interface AppUserDTO {
  id?: number;
  appId: number;
  username: string;
  email?: string;
  phone?: string;
  password?: string;
  nickname?: string;
  avatarUrl?: string;
  signature?: string;
}

export interface AppUserQueryDTO {
  appId?: number;
  username?: string;
  email?: string;
  phone?: string;
  nickname?: string;
  current?: number;
  size?: number;
}

// 获取终端用户列表
export const getAppUserList = (params: AppUserQueryDTO) => {
  return request.get('/app-users', { params });
};

// 获取终端用户详情
export const getAppUserById = (id: number) => {
  return request.get(`/app-users/${id}`);
};

// 创建终端用户
export const createAppUser = (data: AppUserDTO) => {
  return request.post('/app-users', data);
};

// 更新终端用户
export const updateAppUser = (id: number, data: AppUserDTO) => {
  return request.put(`/app-users/${id}`, data);
};

// 删除终端用户
export const deleteAppUser = (id: number) => {
  return request.delete(`/app-users/${id}`);
};

// 重置用户密码
export const resetPassword = (id: number, newPassword: string) => {
  return request.post(`/app-users/${id}/reset-password`, { newPassword });
};

// 封禁/解封用户
export const banUser = (id: number, ban: boolean, reason?: string, bindingId?: number) => {
  return request.post(`/app-users/${id}/ban`, { ban, reason, bindingId });
};
