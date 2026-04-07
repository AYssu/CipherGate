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
  },

  // 检查系统是否已初始化
  checkInitStatus: () => {
    return request.get('/config/init/status');
  },

  // 初始化系统配置
  initializeSystem: (data: { clientId: string; clientSecret: string; redirectUri: string; frontendUrl: string }) => {
    return request.post('/config/init', data);
  },
};