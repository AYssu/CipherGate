import request from '../utils/request';
import type { ApiResponse } from '../types';

export interface CredentialPage<T> {
  records: T[];
  current: number;
  size: number;
  total: number;
}

export interface ThirdPartyCredential {
  id: number;
  appId: number;
  appIds?: number[];
  name: string;
  apiKey: string;
  apiSecret?: string;
  status: number;
  allowedIps?: string;
  dailyLimit?: number;
  totalCallLimit?: number;
  totalDaysLimit?: number;
  usedCallCount?: number;
  usedDaysCount?: number;
  expiresAt?: string;
  remark?: string;
  createdBy?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ThirdPartyCredentialDTO {
  appId?: number;
  appIds?: number[];
  name: string;
  allowedIps?: string;
  dailyLimit?: number;
  totalCallLimit?: number;
  totalDaysLimit?: number;
  expiresAt?: string;
  status?: number;
  remark?: string;
}

export interface ThirdPartyCredentialQuery {
  appId?: number;
  name?: string;
  apiKey?: string;
  status?: number;
  current?: number;
  size?: number;
}

export const getThirdPartyCredentialList = (params: ThirdPartyCredentialQuery) => {
  return request.get<unknown, ApiResponse<CredentialPage<ThirdPartyCredential>>>('/third-party/credentials', { params });
};

export const getThirdPartyCredentialById = (id: number) => {
  return request.get<unknown, ApiResponse<ThirdPartyCredential>>(`/third-party/credentials/${id}`);
};

export const createThirdPartyCredential = (data: ThirdPartyCredentialDTO) => {
  return request.post<unknown, ApiResponse<ThirdPartyCredential>, ThirdPartyCredentialDTO>('/third-party/credentials', data);
};

export const updateThirdPartyCredential = (id: number, data: ThirdPartyCredentialDTO) => {
  return request.put<unknown, ApiResponse<ThirdPartyCredential>, ThirdPartyCredentialDTO>(`/third-party/credentials/${id}`, data);
};

export const rotateThirdPartyCredentialSecret = (id: number) => {
  return request.post<unknown, ApiResponse<ThirdPartyCredential>>(`/third-party/credentials/${id}/rotate-secret`);
};

export const deleteThirdPartyCredential = (id: number) => {
  return request.delete<unknown, ApiResponse<null>>(`/third-party/credentials/${id}`);
};
