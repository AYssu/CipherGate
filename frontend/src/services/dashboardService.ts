import request from '../utils/request';

export interface DashboardTodayStats {
  cardFirstActivatedToday: number;
  cardLoginToday: number;
  appUserRegisteredToday: number;
  appUserWsLoginToday: number;
  /** 仅 ADMIN / SUPER_ADMIN 有该字段 */
  platformLoginToday?: number;
}

export const dashboardApi = {
  getTodayStats: () =>
    request.get<DashboardTodayStats>('/dashboard/stats/today'),
};
