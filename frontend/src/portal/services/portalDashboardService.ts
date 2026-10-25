import type { ApiResponse } from '../../services/apiResponse';
import type { PortalDashboardStats, PortalDevice, PortalLoginLog } from './types';
import portalRequest from './portalRequest';

export const portalDashboardApi = {
  getStats: () => {
    return portalRequest.get<unknown, ApiResponse<PortalDashboardStats>>('/dashboard/stats');
  },
  getLoginHistory: (page = 1, size = 20) => {
    return portalRequest.get<unknown, ApiResponse<PortalLoginLog[]>>('/dashboard/login-history', { params: { page, size } });
  },
  getDevices: () => {
    return portalRequest.get<unknown, ApiResponse<PortalDevice[]>>('/dashboard/devices');
  },
};
