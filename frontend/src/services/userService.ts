import request from '../utils/request';

export interface User {
  id: number;
  githubId: string;
  login: string;
  name: string;
  email: string;
  avatarUrl: string;
  status: number;
  createdAt: string;
  updatedAt: string;
  lastLoginAt: string;
  roles?: Role[];
  menus?: Menu[];
}

export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
}

export interface Menu {
  id: number;
  menuName: string;
  menuCode: string;
  parentId: number;
  menuType: number;
  path: string;
  component: string;
  icon: string;
  sortOrder: number;
  visible: number;
  status: number;
  children?: Menu[];
}

// 用户管理 API
export const userApi = {
  // 获取用户列表
  getUsers: () => request.get<{ data: User[] }>('/users'),

  // 获取用户详情
  getUserById: (id: number) => request.get<{ data: User }>(`/users/${id}`),

  // 更新用户
  updateUser: (id: number, userData: Record<string, any>) => 
    request.put(`/users/${id}`, userData),

  // 删除用户
  deleteUser: (id: number) => request.delete(`/users/${id}`),

  // 更新用户状态
  updateUserStatus: (id: number, status: number) => 
    request.put(`/users/${id}/status`, null, { params: { status } }),

  // 获取当前用户信息
  getCurrentUserInfo: () => request.get<{ data: User }>('/user/info'),

  // 获取当前用户基本信息
  getCurrentUserProfile: () => request.get<{ data: User }>('/user/profile'),

  // 更新当前用户信息
  updateCurrentUserProfile: (userData: Partial<User>) => 
    request.put('/user/profile', userData),

  // 退出登录
  logout: () => request.post('/logout'),
};