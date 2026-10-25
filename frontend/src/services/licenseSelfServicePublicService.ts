import request from '../utils/request';
import type { Challenge } from '../components/usePublicChallenge';
import type { ApiResponse } from './apiResponse';

export interface PublicLicenseQueryResponse {
  keyCodeMasked: string;
  status: number;
  redeemedAt?: string;
  expiresAt?: string;
  remainingSeconds: number;
  boundDevice: boolean;
  boundIp: boolean;
  unbindCount: number;
  unbindLimit: number;
  /** -1 表示不限制 */
  unbindRemaining: number;
}

export function queryLicenseRemaining(appId: number, keyCode: string, challenge: Challenge) {
  return request.post<unknown, ApiResponse<PublicLicenseQueryResponse>>('/public/license/query-remaining', { appId, keyCode, ...challenge });
}

export function unbindLicense(payload: Challenge & {
  appId: number;
  keyCode: string;
  unbindDevice?: boolean;
  unbindIp?: boolean;
}) {
  return request.post<unknown, ApiResponse<null>>('/public/license/unbind', payload);
}
