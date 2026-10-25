import type { ApiResponse } from './apiResponse';
import type { PortalEpayConfig } from '../portal/services/types';
export type ApplicationEpayConfig = PortalEpayConfig;
import request from '../utils/request';

export const applicationEpayConfigApi = {
  getConfig: (appId: number) => {
    return request.get<unknown, ApiResponse<ApplicationEpayConfig | null>>(`/applications/${appId}/epay-config`);
  },
  saveConfig: (appId: number, data: Record<string, string>) => {
    return request.post<unknown, ApiResponse<null>>(`/applications/${appId}/epay-config`, data);
  },
  togglePayment: (appId: number, enabled: boolean) => {
    return request.post<unknown, ApiResponse<null>>(`/applications/${appId}/toggle-payment`, { enabled });
  },
};
