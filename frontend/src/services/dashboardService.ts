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

export const dashboardApi = {
  getTodayStats: () =>
    request.get<DashboardTodayStats>('/dashboard/stats/today'),
  getOverview: () =>
    request.get<DashboardOverview>('/dashboard/overview'),
  getOnline: () =>
    request.get<DashboardOnlineStats>('/dashboard/online'),
  getTrend7d: () =>
    request.get<DashboardTrendPoint[]>('/dashboard/trend/7d'),
};
