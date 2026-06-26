import request from '../utils/request';

export const applicationEpayConfigApi = {
  getConfig: (appId: number) => {
    return request.get(`/applications/${appId}/epay-config`);
  },
  saveConfig: (appId: number, data: Record<string, string>) => {
    return request.post(`/applications/${appId}/epay-config`, data);
  },
  togglePayment: (appId: number, enabled: boolean) => {
    return request.post(`/applications/${appId}/toggle-payment`, { enabled });
  },
};
