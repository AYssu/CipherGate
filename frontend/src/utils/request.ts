import { csrfHeaders } from './csrf';
import { normalizeResponse, shouldExpireSession } from '../services/apiResponse';
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
    try { return normalizeResponse(response.data) as unknown as typeof response; }
    catch (error) { message.error(error instanceof Error ? error.message : '请求失败'); return Promise.reject(error); }
  },
  (error) => {
    // 处理 HTTP 错误响应
    if (error.response) {
      const { status, data } = error.response;

      switch (status) {
        case 401:
          // 未登录或登录已过期，跳转到首页登录
          if (shouldExpireSession(error.config?.url, 'admin') && !isRedirectingToLogin) {
            isRedirectingToLogin = true;
            const errorMsg = data?.message || '登录已过期，请重新登录';
            message.warning(errorMsg, 2, () => {
              window.location.href = '/';
            });
          }
          break;
        case 403:
          message.error('权限不足');
          break;
        case 404:
          message.error('请求的资源不存在');
          break;
        case 500:
          message.error('服务器内部错误');
          break;
        default:
          message.error(data?.message || `请求失败 (${status})`);
      }
    } else if (error.request) {
      // 请求发出但没有收到响应（网络不通）
      message.error('网络错误，请检查网络连接');
    } else {
      message.error('请求配置错误');
    }

    return Promise.reject(error);
  }
);

export default request;