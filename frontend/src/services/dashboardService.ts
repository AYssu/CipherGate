import request from '../utils/request';

export interface DashboardTodayStats {
  cardFirstActivatedToday: number;
  cardLoginToday: number;
  appUserRegisteredToday: number;
  appUserWsLoginToday: number;
  /** 仅 ADMIN / SUPER_ADMIN 有该字段 */
  platformLoginToday?: number;
}

export interface DashboardOverview {
  appCount: number;
  appUserTotal: number;
  licenseTotal: number;
  cardLogin7d: number;
  appUserWsLogin7d: number;
}

export interface DashboardOnlineStats {
  cardOnlineCount: number;
  appUserOnlineCount: number;
}

export interface DashboardTrendPoint {
  date: string;
  appUserRegistered: number;
  cardLogin: number;
  appUserWsLogin: number;
}

export interface DashboardAccessStats {
  paidCardLoginToday: number;
  freeLoginToday: number;
  activeCardToday: number;
  activeVisitorToday: number;
  activeDeviceToday: number;
  activeCard7d: number;
  activeVisitor7d: number;
  activeDevice7d: number;
  recentCardCount: number;
  recentVisitorCount: number;
  unidentifiedFreeLogin7d: number;
}

export interface DashboardAccessRecord {
  id: number;
  appId: number;
  appName?: string;
  identityType: 'CARD' | 'VISITOR';
  identityId?: string;
  deviceHash?: string;
  clientIp?: string;
  createdAt: string;
}

export interface DashboardAccessPage {
  records: DashboardAccessRecord[];
  total: number;
  current: number;
  size: number;
}

export const dashboardApi = {
  getTodayStats: () =>
    request.get<DashboardTodayStats>('/dashboard/stats/today'),
  getOverview: () =>
    request.get<DashboardOverview>('/dashboard/overview'),
  getOnline: () =>
    request.get<DashboardOnlineStats>('/dashboard/online'),
  getAccessStats: () =>
    request.get<DashboardAccessStats>('/dashboard/access/stats'),
  getRecentAccess: (page = 1, size = 10) =>
    request.get<DashboardAccessPage>('/dashboard/access/recent', { params: { page, size } }),
  getTrend7d: () =>
    request.get<DashboardTrendPoint[]>('/dashboard/trend/7d'),
};
