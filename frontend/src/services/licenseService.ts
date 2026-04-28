import request from '../utils/request';

export interface LicenseKey {
  id: number;
  appId: number;
  appName?: string;
  ownerId: number;
  ownerName?: string;
  creatorType?: 'SELF' | 'AGENT';
  agentDisplayName?: string;
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
  ids?: number[];
  appId?: number;
  keyCode?: string;
  remark?: string;
  keyType?: string;
  batchId?: number;
  batchName?: string;
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
  bindDeviceId?: string | null;
  bindIp?: string | null;
}

export interface LicenseBatchAddTimeDTO {
  ids: number[];
  durationValue: number;
  durationUnit: string;
}

export interface LicenseBatchAddTimeFailItem {
  id: number;
  keyCode?: string;
  reason: string;
}

export interface LicenseBatchAddTimeResult {
  successCount: number;
  failCount: number;
  failures: LicenseBatchAddTimeFailItem[];
}

export interface LicenseBatchOperateDTO {
  ids: number[];
}

export interface LicenseBatchOperateFailItem {
  id: number;
  keyCode?: string;
  reason: string;
}

export interface LicenseBatchOperateResult {
  successCount: number;
  failCount: number;
  failures: LicenseBatchOperateFailItem[];
}

export interface LicenseBatchStatusDTO extends LicenseBatchOperateDTO {
  status: number;
}

export interface LicenseBatchUnbindDTO extends LicenseBatchOperateDTO {
  unbindDevice?: boolean;
  unbindIp?: boolean;
}

export interface LicenseBatchSetUseLimitDTO extends LicenseBatchOperateDTO {
  useLimit: number;
}

export interface LicenseBatchSetUnbindLimitDTO extends LicenseBatchOperateDTO {
  unbindLimit: number;
}

export interface LicenseBatchSetUseTimeDTO extends LicenseBatchOperateDTO {
  useTimeStart?: string;
  useTimeEnd?: string;
  clearTimeRange?: boolean;
}

export interface LicenseBatchCreateDTO {
  appId: number;
  batchName: string;
  keyType: string;
  durationValue?: number;
  totalCount: number;
  keyPrefix?: string;
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

/** 批量加时（仅已激活卡密） */
export const batchAddLicenseTime = (data: LicenseBatchAddTimeDTO) => {
  return request.post('/licenses/batch-add-time', data);
};

export const batchUpdateLicenseStatus = (data: LicenseBatchStatusDTO) => {
  return request.post('/licenses/batch-status', data);
};

export const batchUnbindLicenses = (data: LicenseBatchUnbindDTO) => {
  return request.post('/licenses/batch-unbind', data);
};

export const batchSetLicenseUseLimit = (data: LicenseBatchSetUseLimitDTO) => {
  return request.post('/licenses/batch-use-limit', data);
};

export const batchSetLicenseUnbindLimit = (data: LicenseBatchSetUnbindLimitDTO) => {
  return request.post('/licenses/batch-unbind-limit', data);
};

export const batchSetLicenseUseTime = (data: LicenseBatchSetUseTimeDTO) => {
  return request.post('/licenses/batch-use-time', data);
};

export const batchDeleteLicenses = (data: LicenseBatchOperateDTO) => {
  return request.post('/licenses/batch-delete', data);
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

/** 解绑设备（清空绑定，下次卡密登录可绑新设备） */
export const unbindLicenseDevice = (id: number) => {
  return request.post(`/licenses/${id}/unbind-device`);
};

/** 解绑 IP */
export const unbindLicenseIp = (id: number) => {
  return request.post(`/licenses/${id}/unbind-ip`);
};

/**
 * 导出卡密（Excel .xlsx 二进制流）
 */
export const exportLicenses = (params: LicenseKeyQuery) => {
  return request.get<Blob>('/licenses/export', { params, responseType: 'blob' });
};
