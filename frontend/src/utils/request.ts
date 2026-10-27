import { csrfHeaders } from './csrf';
import { normalizeResponse, shouldExpireSession, responseRequestId, toApiError, getErrorMessage } from '../services/apiResponse';
import axios from 'axios';
import { message } from 'antd';

// 防止 401 弹窗重复弹出
let isRedirectingToLogin = false;

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
});

// 请求拦截器
request.interceptors.request.use(
  async (config) => {
    for (const [name, value] of Object.entries(await csrfHeaders(config.method, request.getUri(config)))) config.headers.set(name, value);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response;
    }
    try { return normalizeResponse(response.data, responseRequestId(response.headers)) as unknown as typeof response; }
    catch (error) { message.error(getErrorMessage(error, '请求失败')); return Promise.reject(error); }
  },
  (error) => {
    const failure = toApiError(error);
    if (error.response?.status === 401 && shouldExpireSession(error.config?.url, 'admin')) {
      if (!isRedirectingToLogin) {
        isRedirectingToLogin = true;
        message.warning(getErrorMessage(failure), 2, () => { window.location.href = '/'; });
      }
    } else {
      message.error(getErrorMessage(failure));
    }

    // Preserve structured HTTP error payloads used by import and validation workflows.
    return Promise.reject(error);
  }
);

export default request;