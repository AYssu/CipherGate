import request from '../utils/request';

export interface SystemInfo {
  application: {
    name: string;
    version: string;
    startTime: number;
    uptime: number;
  };
  operatingSystem: {
    name: string;
    version: string;
    arch: string;
    processors: number;
  };
  java: {
    version: string;
    vendor: string;
    home: string;
  };
  memory: {
    max: number;
    used: number;
    free: number;
    usagePercent: number;
  };
  techStack: {
    backend: string;
    frontend: string;
    database: string;
    authentication: string;
  };
}

export interface SystemStatus {
  systemLoad: number;
  memoryUsage: number;
  uptime: number;
  status: string;
  processors: number;
}

export const systemApi = {
  // 获取系统信息
  getSystemInfo: () => {
    return request.get('/system/info');
  },

  // 获取系统状态
  getSystemStatus: () => {
    return request.get('/system/status');
  }
};