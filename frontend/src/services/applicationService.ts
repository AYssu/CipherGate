import request from '../utils/request';

export interface Application {
  id: number;
  ownerId: number;
  ownerName?: string;
  appName: string;
  appKey: string;
  appSecret?: string;
  description?: string;
  notice?: string;
  category?: string;
  tags?: string;
  iconUrl?: string;
  businessModel: number;
  status: number;
  encryptionPlugin?: string;
  encryptionConfig?: Record<string, any>;
  features?: Record<string, any>;
  trafficLimit?: number;
  trafficUsed?: number;
  currentVersion?: string;
  minVersion?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationQuery {
  appName?: string;
  category?: string;
  businessModel?: number;
  status?: number;
  ownerId?: number;
  current?: number;
  size?: number;
}

export interface ApplicationDTO {
  id?: number;
  appName: string;
  description?: string;
  notice?: string;
  category?: string;
  tags?: string;
  iconUrl?: string;
  businessModel: number;
  status?: number;
  encryptionPlugin?: string;
  encryptionConfig?: Record<string, any>;
  features?: Record<string, any>;
  trafficLimit?: number;
  currentVersion?: string;
  minVersion?: string;
}

/**
 * 获取应用列表
 */
export const getApplicationList = (params: ApplicationQuery) => {
  return request.get('/applications', { params });
};

/**
 * 获取应用详情
 */
export const getApplicationById = (id: number) => {
  return request.get(`/applications/${id}`);
};

/**
 * 创建应用
 */
export const createApplication = (data: ApplicationDTO) => {
  return request.post('/applications', data);
};

/**
 * 更新应用
 */
export const updateApplication = (id: number, data: ApplicationDTO) => {
  return request.put(`/applications/${id}`, data);
};

/**
 * 删除应用
 */
export const deleteApplication = (id: number) => {
  return request.delete(`/applications/${id}`);
};

/**
 * 生成应用密钥
 */
export const generateAppKeys = () => {
  return request.post('/applications/generate-keys');
};

/**
 * 重置应用密钥
 */
export const resetAppKeys = (id: number) => {
  return request.post(`/applications/${id}/reset-keys`);
};

/**
 * 生成加密密钥对
 */
export const generateEncryptionKeys = (pluginId: string) => {
  return request.post('/applications/generate-encryption-keys', null, {
    params: { pluginId }
  });
};

/**
 * 更新应用状态
 */
export const updateApplicationStatus = (id: number, status: number) => {
  return request.put(`/applications/${id}/status`, null, {
    params: { status }
  });
};

/**
 * 获取应用统计信息
 */
export const getApplicationStats = (id: number) => {
  return request.get(`/applications/${id}/stats`);
};
