import type { ApiResponse } from './apiResponse';
import type { PageResult } from './contracts';
import type { JsonObject } from './contracts';
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
  bindIpRegion?: string;
  bindUserId?: number;
  redeemedAt?: string;
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
  metadata?: JsonObject;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface LicenseKeyQuery {
  ids?: number[];
  appId?: number;
  keyCode?: string;
  keyword?: string;
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
  keyCode?: string;
  id?: number;
  appId: number;
  keyType: string;
  durationValue?: number;
  durationUnit?: string;
  expiresAt?: string;
  useLimit?: number;
  unbindLimit?: number;
  useTimeStart?: string;
  useTimeEnd?: string;
  deviceCheckEnabled?: boolean;
  ipCheckEnabled?: boolean;
  remark?: string;
  coreData?: string;
  metadata?: JsonObject;
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
  durationUnit?: string;
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
  return request.get<unknown, ApiResponse<PageResult<LicenseKey>>>('/licenses', { params });
};

/**
 * 获取卡密详情
 */
export const getLicenseById = (id: number) => {
  return request.get<unknown, ApiResponse<LicenseKey>>(`/licenses/${id}`);
};

/**
 * 创建卡密
 */
export const createLicense = (data: LicenseKeyDTO) => {
  return request.post<unknown, ApiResponse<LicenseKey>>('/licenses', data);
};

/**
 * 批量生成卡密
 */
export const batchCreateLicenses = (data: LicenseBatchCreateDTO) => {
  return request.post<unknown, ApiResponse<LicenseKey[]>>('/licenses/batch', data);
};

/** 批量加时（仅已激活卡密） */
export const batchAddLicenseTime = (data: LicenseBatchAddTimeDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchAddTimeResult>>('/licenses/batch-add-time', data);
};

/** 批量扣时（仅已激活卡密） */
export const batchSubtractLicenseTime = (data: LicenseBatchAddTimeDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchAddTimeResult>>('/licenses/batch-subtract-time', data);
};

export const batchUpdateLicenseStatus = (data: LicenseBatchStatusDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-status', data);
};

export const batchUnbindLicenses = (data: LicenseBatchUnbindDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-unbind', data);
};

export const batchSetLicenseUseLimit = (data: LicenseBatchSetUseLimitDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-use-limit', data);
};

export const batchSetLicenseUnbindLimit = (data: LicenseBatchSetUnbindLimitDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-unbind-limit', data);
};

export const batchSetLicenseUseTime = (data: LicenseBatchSetUseTimeDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-use-time', data);
};

export const batchDeleteLicenses = (data: LicenseBatchOperateDTO) => {
  return request.post<unknown, ApiResponse<LicenseBatchOperateResult>>('/licenses/batch-delete', data);
};

/**
 * 更新卡密
 */
export const updateLicense = (id: number, data: Partial<LicenseKeyDTO>) => {
  return request.put<unknown, ApiResponse<LicenseKey>>(`/licenses/${id}`, data);
};

/**
 * 删除卡密
 */
export const deleteLicense = (id: number) => {
  return request.delete<unknown, ApiResponse<string>>(`/licenses/${id}`);
};

/**
 * 更新卡密状态
 */
export const updateLicenseStatus = (id: number, status: number) => {
  return request.put<unknown, ApiResponse<string>>(`/licenses/${id}/status`, null, {
    params: { status }
  });
};

/** 解绑设备（清空绑定，下次卡密登录可绑新设备） */
export const unbindLicenseDevice = (id: number) => {
  return request.post<unknown, ApiResponse<LicenseKey>>(`/licenses/${id}/unbind-device`);
};

/** 解绑 IP */
export const unbindLicenseIp = (id: number) => {
  return request.post<unknown, ApiResponse<LicenseKey>>(`/licenses/${id}/unbind-ip`);
};

/**
 * 导出卡密（Excel .xlsx 二进制流）
 */
export const exportLicenses = (params: LicenseKeyQuery) => {
  return request.get<Blob>('/licenses/export', { params, responseType: 'blob' });
};

/**
 * 下载导入模板
 */
export const downloadImportTemplate = () => {
  return request.get<Blob>('/licenses/import-template', { responseType: 'blob' });
};

export interface LicenseImportResult {
  totalRows: number;
  successCount: number;
  failCount: number;
  failItems: Array<{
    rowNumber: number;
    keyCode: string;
    reason: string;
  }>;
}

/**
 * 批量导入卡密
 */
export const importLicenses = (file: File, appId: number) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('appId', String(appId));
  return request.post<unknown, ApiResponse<LicenseImportResult>>('/licenses/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
