import request from '../utils/request';

export function sendAppUserRegisterEmailCode(appId: number, email: string) {
  return request.post('/public/app-user/register/send-email-code', { appId, email });
}

export function submitAppUserRegister(payload: {
  appId: number;
  username: string;
  email: string;
  emailCode: string;
  password: string;
}) {
  return request.post('/public/app-user/register/submit', payload);
}
