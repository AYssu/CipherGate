import type { ApiResponse } from '../../services/apiResponse';
import type { PortalCreatedOrder, PortalOrder } from './types';
import portalRequest from './portalRequest';

export const portalPaymentApi = {
  createOrder: (planId: number) => {
    return portalRequest.post<unknown, ApiResponse<PortalCreatedOrder>>('/payment/create', { planId });
  },
  getOrders: (page = 1, size = 20) => {
    return portalRequest.get<unknown, ApiResponse<PortalOrder[]>>('/payment/orders', { params: { page, size } });
  },
};
