import request from '../utils/request';
import type { Challenge } from '../components/usePublicChallenge';
import type { ApiResponse } from './apiResponse';

export interface PublicAppUserExpireQueryResponse {
  emailMasked: string;
  memberExpiresAt?: string;
  remainingSeconds: number;
  memberActive: boolean;
}

export function queryAppUserExpire(appId: number, email: string, password: string, challenge: Challenge) {
  return request.post<unknown, ApiResponse<PublicAppUserExpireQueryResponse>>('/public/app-user/self/query-expire', { appId, email, password, ...challenge });
}

export interface AppUserLicenseRechargeResponse extends PublicAppUserExpireQueryResponse {
  beforeExpiresAt?: string;
  keyCodeMasked: string;
}

export function redeemAppUserLicense(appId: number, email: string, keyCode: string) {
  return request.post<unknown, { code: number; data: AppUserLicenseRechargeResponse }>(
    '/public/app-user/self/redeem-license', { appId, email, keyCode },
  );
}
