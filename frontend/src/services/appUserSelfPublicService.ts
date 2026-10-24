import request from '../utils/request';

export interface PublicAppUserExpireQueryResponse {
  emailMasked: string;
  memberExpiresAt?: string;
  remainingSeconds: number;
  memberActive: boolean;
}

export function queryAppUserExpire(appId: number, email: string) {
  return request.post('/public/app-user/self/query-expire', { appId, email });
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
