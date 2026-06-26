import axios from 'axios';
import { message } from 'antd';

const portalRequest = axios.create({
  baseURL: '/api/portal',
  timeout: 15000,
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
    const data = response.data;
    if (data.code && data.code !== 200 && data.code !== 0) {
      message.error(data.message || '请求失败');
      return Promise.reject(new Error(data.message));
    }
    return data;
  },
  (error) => {
    if (error.response?.status === 401) {
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
