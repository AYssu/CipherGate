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
