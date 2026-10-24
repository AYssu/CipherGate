import request from '../utils/request';

export interface TransferApplication { id: number; appName: string; ownerId: number }
export interface TransferUser { id: number; username: string; email?: string; nickname?: string; memberExpiresAt?: string }
export interface TransferUserFilters {
  username?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  memberStatus?: 'ACTIVE' | 'EXPIRED' | 'NONE';
}
export interface TransferRequest {
  sourceAppId: number;
  targetAppId: number;
  mode: 'MOVE' | 'COPY';
  forceOverwrite: boolean;
  userIds: number[];
  previewToken?: string;
}
export interface TransferItem {
  sourceUserId: number;
  username: string;
  targetUserId?: number;
  targetUsername?: string;
  action: 'CREATE' | 'OVERWRITE' | 'RESTORE' | 'SKIP' | 'BLOCK';
  reason: string;
  deviceCount: number;
  bannedDeviceCount: number;
  trialRecorded: boolean;
}
export interface TransferResult {
  sourceAppName: string;
  targetAppName: string;
  previewToken: string;
  createCount: number;
  overwriteCount: number;
  restoreCount: number;
  skipCount: number;
  blockCount: number;
  executed: boolean;
  items: TransferItem[];
}
interface ApiResult<T> { data: T; success: boolean; message: string }
interface Page<T> { records: T[]; total: number; current: number; size: number }
const base = '/app-user-transfers';
export const getTransferApplications = (params: { keyword?: string; current: number; size: number }) =>
  request.get<never, ApiResult<Page<TransferApplication>>>(`${base}/applications`, { params });
export const getTransferUsers = (params: TransferUserFilters & { appId: number; keyword?: string; current: number; size: number; userIds?: string }) =>
  request.get<never, ApiResult<Page<TransferUser>>>(`${base}/users`, { params });
export const previewTransfer = (data: TransferRequest) =>
  request.post<never, ApiResult<TransferResult>>(`${base}/preview`, data, { timeout: 60000 });
export const executeTransfer = (data: TransferRequest) =>
  request.post<never, ApiResult<TransferResult>>(`${base}/execute`, data, { timeout: 60000 });
