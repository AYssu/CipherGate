import request from '../utils/request';

export interface PublicLicenseQueryResponse {
  keyCodeMasked: string;
  status: number;
  expiresAt?: string;
  remainingSeconds: number;
  boundDevice: boolean;
  boundIp: boolean;
  unbindCount: number;
  unbindLimit: number;
  /** -1 表示不限制 */
  unbindRemaining: number;
}

export function queryLicenseRemaining(appId: number, keyCode: string) {
  return request.post('/public/license/query-remaining', { appId, keyCode });
}

export function unbindLicense(payload: {
  appId: number;
  keyCode: string;
  unbindDevice?: boolean;
  unbindIp?: boolean;
}) {
  return request.post('/public/license/unbind', payload);
}
