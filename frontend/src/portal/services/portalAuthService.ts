import type { ApiResponse } from '../../services/apiResponse';
import type { PortalAppInfo, PortalCaptcha, PortalLoginResult } from './types';
import portalRequest from './portalRequest';

export const portalAuthApi = {
  getCaptcha: () => {
    return portalRequest.get<unknown, ApiResponse<PortalCaptcha>>('/auth/captcha', { params: { _t: Date.now() } });
  },
  login: (data: { email: string; password: string; captchaCode: string; captchaId: string }) => {
    return portalRequest.post<unknown, ApiResponse<PortalLoginResult>>('/auth/login', data);
  },
  selectApp: (appId: number) => {
    return portalRequest.post<unknown, ApiResponse<string>>(`/auth/select-app?appId=${appId}`);
  },
  sendRecoveryCode: (email: string, captchaId: string, captchaCode: string) => {
    return portalRequest.post<unknown, ApiResponse<null>>('/auth/recovery/send-code', { email, captchaId, captchaCode });
  },

  getRecoveryApps: (email: string, verifyCode: string) => {
    return portalRequest.post<unknown, ApiResponse<PortalAppInfo[]>>('/auth/recovery/apps', { email, verifyCode });
  },

  resetPassword: (data: { email: string; verifyCode: string; appId: number; newPassword: string }) => {
    return portalRequest.post<unknown, ApiResponse<null>>('/auth/recovery/reset-password', data);
  },
  sendEmailVerifyCode: (email: string) => {
    return portalRequest.post<unknown, ApiResponse<null>>('/auth/verify-email-code', { email });
  },
};
