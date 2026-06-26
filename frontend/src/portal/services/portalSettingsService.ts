import portalRequest from './portalRequest';

export const portalSettingsApi = {
  getProfile: () => {
    return portalRequest.get('/settings/profile');
  },
  updateNickname: (nickname: string) => {
    return portalRequest.put('/settings/nickname', { nickname });
  },
  changePassword: (data: { oldPassword: string; newPassword: string }) => {
    return portalRequest.put('/settings/password', data);
  },
  changeEmail: (data: { currentPassword: string; newEmail: string; verifyCode: string }) => {
    return portalRequest.put('/settings/email', data);
  },
};
