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

export interface SiteInfo {
  icpRecordNo: string;
  publicSecurityRecordNo: string;
  icpLicenseNo: string;
}

export interface SystemSettings {
  githubClientId: string;
  githubRedirectUri: string;
  frontendUrl: string;
  sitePublicSecurityRecordNo: string;
  siteIcpLicenseNo: string;
  siteIcpRecordNo: string;
  emailSmtpHost: string;
  emailSmtpPort: string;
  emailSmtpUsername: string;
  emailFrom: string;
  /** 收件方看到的「发件人」名称，可选 */
  emailFromDisplayName: string;
  emailEnabled: boolean;
  emailPasswordSet: boolean;
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

  // 获取站点公共展示信息（备案等）
  getPublicSiteInfo: () => {
    return request.get('/config/public/site-info');
  },

  getSystemSettings: () => {
    return request.get('/config/settings');
  },

  updateGithubSettings: (data: { clientId: string; clientSecret?: string; redirectUri: string; frontendUrl: string }) => {
    return request.post('/config/settings/github', data);
  },

  updateSiteSettings: (data: { publicSecurityRecordNo: string; icpLicenseNo: string; icpRecordNo?: string }) => {
    return request.post('/config/settings/site', data);
  },

  updateEmailSettings: (data: {
    smtpHost: string;
    smtpPort: string;
    smtpUsername: string;
    smtpPassword?: string;
    fromEmail: string;
    fromDisplayName?: string;
    enabled: boolean;
  }) => {
    return request.post('/config/settings/email', data);
  },
};