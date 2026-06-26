import portalRequest from './portalRequest';

export const portalPaymentApi = {
  createOrder: (planId: number) => {
    return portalRequest.post('/payment/create', { planId });
  },
  getOrders: (page = 1, size = 20) => {
    return portalRequest.get('/payment/orders', { params: { page, size } });
  },
};
