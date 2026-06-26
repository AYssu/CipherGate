import portalRequest from './portalRequest';

export const portalAuthApi = {
  getCaptcha: () => {
    return portalRequest.get('/auth/captcha');
  },
  login: (data: { email: string; password: string; captchaCode: string; captchaId: string }) => {
    return portalRequest.post('/auth/login', data);
  },
  selectApp: (appId: number) => {
    return portalRequest.post(`/auth/select-app?appId=${appId}`);
  },
  sendRecoveryCode: (email: string) => {
    return portalRequest.post('/auth/recovery/send-code', { email });
  },
  resetPassword: (data: { email: string; verifyCode: string; appId: number; newPassword: string }) => {
    return portalRequest.post('/auth/recovery/reset-password', data);
  },
  sendEmailVerifyCode: (email: string) => {
    return portalRequest.post('/auth/verify-email-code', { email });
  },
};
