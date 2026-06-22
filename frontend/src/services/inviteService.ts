import request from '../utils/request';

export const inviteApi = {
  getInviteCode: () => {
    return request.get('/user/invite/code');
  },

  getInviteStats: () => {
    return request.get('/user/invite/stats');
  },

  getInviteRecords: (page = 1, size = 10) => {
    return request.get('/user/invite/records', { params: { page, size } });
  },

  bindInviteCode: (inviteCode: string) => {
    return request.post('/user/invite/bind', { inviteCode });
  },
};

export default inviteApi;
