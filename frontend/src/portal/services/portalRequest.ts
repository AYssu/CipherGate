import { normalizeResponse, shouldExpireSession, isCurrentPortalSession } from '../../services/apiResponse';
import axios from 'axios';
import { message } from 'antd';

const portalRequest = axios.create({
  baseURL: '/api/portal',
  timeout: 15000,
  withCredentials: true,
});

portalRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem('portal_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

portalRequest.interceptors.response.use(
  (response) => {
    try { return normalizeResponse(response.data) as unknown as typeof response; }
    catch (error) { message.error(error instanceof Error ? error.message : '请求失败'); return Promise.reject(error); }
  },
  (error) => {
    if (error.response?.status === 401 && shouldExpireSession(error.config?.url, 'portal') && isCurrentPortalSession(error.config?.headers?.Authorization, localStorage.getItem('portal_token'))) {
      localStorage.removeItem('portal_token');
      localStorage.removeItem('portal_app_id');
      window.location.href = '/portal/login';
    } else if (error.response?.status === 403) {
      message.error('权限不足');
    } else {
      message.error(error.response?.data?.message || '网络错误');
    }
    return Promise.reject(error);
  }
);

export default portalRequest;
