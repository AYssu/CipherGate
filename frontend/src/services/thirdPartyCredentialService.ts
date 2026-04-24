import request from '../utils/request';

export interface ThirdPartyCredential {
  id: number;
  appId: number;
  name: string;
  apiKey: string;
  apiSecret: string;
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
  appId: number;
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
  return request.get('/third-party/credentials', { params });
};

export const getThirdPartyCredentialById = (id: number) => {
  return request.get(`/third-party/credentials/${id}`);
};

export const createThirdPartyCredential = (data: ThirdPartyCredentialDTO) => {
  return request.post('/third-party/credentials', data);
};

export const updateThirdPartyCredential = (id: number, data: ThirdPartyCredentialDTO) => {
  return request.put(`/third-party/credentials/${id}`, data);
};

export const rotateThirdPartyCredentialSecret = (id: number) => {
  return request.post(`/third-party/credentials/${id}/rotate-secret`);
};

export const deleteThirdPartyCredential = (id: number) => {
  return request.delete(`/third-party/credentials/${id}`);
};
