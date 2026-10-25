import request from '../utils/request';
import type { Challenge } from '../components/usePublicChallenge';
import type { ApiResponse } from './apiResponse';

export function sendAppUserRegisterEmailCode(appId: number, email: string, challenge: Challenge) {
  return request.post<unknown, ApiResponse<null>>('/public/app-user/register/send-email-code', { appId, email, ...challenge });
}

export function submitAppUserRegister(payload: {
  appId: number;
  username: string;
  email: string;
  emailCode: string;
  password: string;
}) {
  return request.post<unknown, ApiResponse<null>>('/public/app-user/register/submit', payload);
}
