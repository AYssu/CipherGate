import request from '../utils/request';

export const pricingPlanApi = {
  list: (appId: number) => {
    return request.get(`/applications/${appId}/pricing-plans`);
  },
  create: (appId: number, data: any) => {
    return request.post(`/applications/${appId}/pricing-plans`, data);
  },
  update: (appId: number, id: number, data: any) => {
    return request.put(`/applications/${appId}/pricing-plans/${id}`, data);
  },
  delete: (appId: number, id: number) => {
    return request.delete(`/applications/${appId}/pricing-plans/${id}`);
  },
};
