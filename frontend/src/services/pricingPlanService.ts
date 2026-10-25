import type { PortalPricingPlan } from '../portal/services/types';
export type PricingPlan = PortalPricingPlan & { enabled?: boolean };
export type PricingPlanInput = Omit<PricingPlan, 'id'>;
export type { PortalEpayConfig as ApplicationEpayConfig } from '../portal/services/types';
import request from '../utils/request';

export const pricingPlanApi = {
  list: (appId: number) => {
    return request.get(`/applications/${appId}/pricing-plans`);
  },
  create: (appId: number, data: PricingPlanInput) => {
    return request.post(`/applications/${appId}/pricing-plans`, data);
  },
  update: (appId: number, id: number, data: PricingPlanInput) => {
    return request.put(`/applications/${appId}/pricing-plans/${id}`, data);
  },
  delete: (appId: number, id: number) => {
    return request.delete(`/applications/${appId}/pricing-plans/${id}`);
  },
};
