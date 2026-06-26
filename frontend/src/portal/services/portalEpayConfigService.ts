import portalRequest from './portalRequest';

export const portalEpayConfigApi = {
  getConfig: (appId: number) => {
    return portalRequest.get(`/epay-config/${appId}`);
  },
  saveConfig: (appId: number, data: Record<string, string>) => {
    return portalRequest.post(`/epay-config/${appId}`, data);
  },
  togglePayment: (appId: number, enabled: boolean) => {
    return portalRequest.post(`/epay-config/${appId}/toggle-payment`, { enabled });
  },
};
