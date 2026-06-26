import portalRequest from './portalRequest';

export const portalMembershipApi = {
  getInfo: () => {
    return portalRequest.get('/membership/info');
  },
  getPlans: () => {
    return portalRequest.get('/membership/plans');
  },
};
