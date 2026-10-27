import { normalizeResponse, shouldExpireSession, isCurrentPortalSession, responseRequestId, toApiError, getErrorMessage } from '../../services/apiResponse';
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
    try { return normalizeResponse(response.data, responseRequestId(response.headers)) as unknown as typeof response; }
    catch (error) { message.error(getErrorMessage(error, '请求失败')); return Promise.reject(error); }
  },
  (error) => {
    const failure = toApiError(error);
    if (error.response?.status === 401 && shouldExpireSession(error.config?.url, 'portal') && isCurrentPortalSession(error.config?.headers?.Authorization, localStorage.getItem('portal_token'))) {
      localStorage.removeItem('portal_token');
      localStorage.removeItem('portal_app_id');
      window.location.href = '/portal/login';
    } else {
      message.error(getErrorMessage(failure));
    }
    // Preserve structured HTTP error payloads used by import and validation workflows.
    return Promise.reject(error);
  }
);

export default portalRequest;
