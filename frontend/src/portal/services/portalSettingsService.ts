import type { ApiResponse } from '../../services/apiResponse';
import type { PortalProfile } from './types';
import portalRequest from './portalRequest';

export const portalSettingsApi = {
  getProfile: () => {
    return portalRequest.get<unknown, ApiResponse<PortalProfile>>('/settings/profile');
  },
  updateNickname: (nickname: string) => {
    return portalRequest.put<unknown, ApiResponse<null>>('/settings/nickname', { nickname });
  },
  changePassword: (data: { oldPassword: string; newPassword: string }) => {
    return portalRequest.put<unknown, ApiResponse<null>>('/settings/password', data);
  },
  changeEmail: (data: { currentPassword: string; newEmail: string; verifyCode: string }) => {
    return portalRequest.put<unknown, ApiResponse<null>>('/settings/email', data);
  },
};
