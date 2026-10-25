import type { ApiResponse } from '../../services/apiResponse';
import type { PortalEpayConfig } from './types';
import portalRequest from './portalRequest';

export const portalEpayConfigApi = {
  getConfig: (appId: number) => {
    return portalRequest.get<unknown, ApiResponse<PortalEpayConfig | null>>(`/epay-config/${appId}`);
  },
  saveConfig: (appId: number, data: Record<string, string>) => {
    return portalRequest.post<unknown, ApiResponse<null>>(`/epay-config/${appId}`, data);
  },
  togglePayment: (appId: number, enabled: boolean) => {
    return portalRequest.post<unknown, ApiResponse<null>>(`/epay-config/${appId}/toggle-payment`, { enabled });
  },
};
