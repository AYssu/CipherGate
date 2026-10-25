import type { ApiResponse } from './apiResponse';
import type { InviteStats, InviteRecord, PageResult } from './contracts';
import request from '../utils/request';

export const inviteApi = {
  getInviteCode: () => {
    return request.get<unknown, ApiResponse<string>>('/user/invite/code');
  },

  getInviteStats: () => {
    return request.get<unknown, ApiResponse<InviteStats>>('/user/invite/stats');
  },

  getInviteRecords: (page = 1, size = 10) => {
    return request.get<unknown, ApiResponse<PageResult<InviteRecord>>>('/user/invite/records', { params: { page, size } });
  },

  bindInviteCode: (inviteCode: string) => {
    return request.post<unknown, ApiResponse<null>>('/user/invite/bind', { inviteCode });
  },
};

export default inviteApi;
