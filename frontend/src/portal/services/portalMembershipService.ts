import type { ApiResponse } from '../../services/apiResponse';
import type { PortalMembershipInfo, PortalPricingPlan } from './types';
import portalRequest from './portalRequest';

export const portalMembershipApi = {
  getInfo: () => {
    return portalRequest.get<unknown, ApiResponse<PortalMembershipInfo>>('/membership/info');
  },
  getPlans: () => {
    return portalRequest.get<unknown, ApiResponse<PortalPricingPlan[]>>('/membership/plans');
  },
};
