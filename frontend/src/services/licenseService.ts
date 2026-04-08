import request from '../utils/request';

export interface LicenseKey {
  id: number;
  appId: number;
  appName?: string;
  ownerId: number;
  ownerName?: string;
  keyCode: string;
  keyType: string;
  durationValue?: number;
  durationUnit?: string;
  batchId?: number;
  batchName?: string;
  source: string;
  bindDeviceId?: string;
  bindIp?: string;
  bindUserId?: number;
  firstUsedAt?: string;
  lastUsedAt?: string;
  expiresAt?: string;
  useCount: number;
  useLimit: number;
  unbindCount: number;
  unbindLimit: number;
  useTimeStart?: string;
  useTimeEnd?: string;
  deviceCheckEnabled: boolean;
  ipCheckEnabled: boolean;
  lastHeartbeatAt?: string;
  heartbeatInterval: number;
  connectionId?: string;
  isOnline: boolean;
  remark?: string;
  coreData?: string;
  metadata?: Record<string, any>;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface LicenseKeyQuery {
  appId?: number;
  keyCode?: string;
  keyType?: string;
  batchId?: number;
  status?: number;
  ownerId?: number;
  isOnline?: boolean;
  current?: number;
  size?: number;
}

export interface LicenseKeyDTO {
  id?: number;
  appId: number;
  keyType: string;
  durationValue?: number;
  durationUnit?: string;
  useLimit?: number;
  unbindLimit?: number;
  useTimeStart?: string;
  useTimeEnd?: string;
  deviceCheckEnabled?: boolean;
  ipCheckEnabled?: boolean;
  remark?: string;
  coreData?: string;
  metadata?: Record<string, any>;
}

export interface LicenseBatchCreateDTO {
  appId: number;
  batchName: string;
  keyType: string;
  durationValue?: number;
  totalCount: number;
  useLimit?: number;
  unbindLimit?: number;
  deviceCheckEnabled?: boolean;
  ipCheckEnabled?: boolean;
  remark?: string;
}

/**
 * 获取卡密列表
 */
export const getLicenseList = (params: LicenseKeyQuery) => {
  return request.get('/licenses', { params });
};

/**
 * 获取卡密详情
 */
export const getLicenseById = (id: number) => {
  return request.get(`/licenses/${id}`);
};

/**
 * 创建卡密
 */
export const createLicense = (data: LicenseKeyDTO) => {
  return request.post('/licenses', data);
};

/**
 * 批量生成卡密
 */
export const batchCreateLicenses = (data: LicenseBatchCreateDTO) => {
  return request.post('/licenses/batch', data);
};

/**
 * 更新卡密
 */
export const updateLicense = (id: number, data: LicenseKeyDTO) => {
  return request.put(`/licenses/${id}`, data);
};

/**
 * 删除卡密
 */
export const deleteLicense = (id: number) => {
  return request.delete(`/licenses/${id}`);
};

/**
 * 更新卡密状态
 */
export const updateLicenseStatus = (id: number, status: number) => {
  return request.put(`/licenses/${id}/status`, null, {
    params: { status }
  });
};

/**
 * 导出卡密
 */
export const exportLicenses = (params: LicenseKeyQuery) => {
  return request.get('/licenses/export', { params });
};
