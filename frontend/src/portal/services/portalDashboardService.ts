import portalRequest from './portalRequest';

export const portalDashboardApi = {
  getStats: () => {
    return portalRequest.get('/dashboard/stats');
  },
  getLoginHistory: (page = 1, size = 20) => {
    return portalRequest.get('/dashboard/login-history', { params: { page, size } });
  },
  getDevices: () => {
    return portalRequest.get('/dashboard/devices');
  },
};
