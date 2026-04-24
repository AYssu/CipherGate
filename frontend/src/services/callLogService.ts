import request from '../utils/request';

export interface ThirdPartyRechargeLog {
  id: number;
  credentialId?: number;
  appId?: number;
  apiKey?: string;
  userEmail?: string;
  days?: number;
  outTradeNo?: string;
  requestIp?: string;
  requestTs?: number;
  signValid?: number;
  status?: number;
  errorCode?: string;
  errorMessage?: string;
  idempotentHit?: number;
  beforeExpiresAt?: string;
  afterExpiresAt?: string;
  traceId?: string;
  createdAt?: string;
}

export interface ThirdPartyRechargeLogQuery {
  appId?: number;
  credentialId?: number;
  userEmail?: string;
  status?: number;
  requestIp?: string;
  outTradeNo?: string;
  startTime?: string;
  endTime?: string;
  current?: number;
  size?: number;
}

export const getThirdPartyRechargeLogList = (params: ThirdPartyRechargeLogQuery) => {
  return request.get('/third-party/recharge-logs', { params });
};
