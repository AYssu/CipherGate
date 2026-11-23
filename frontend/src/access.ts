import type { InitialState } from './app';

export default function access(initialState: InitialState | undefined) {
  const roles = initialState?.currentUser?.roles || [];
  return {
    signedIn: Boolean(initialState?.currentUser),
    administrator: roles.some(role => ['SUPER_ADMIN', 'ADMIN'].includes(role.roleCode)),
    superAdministrator: roles.some(role => role.roleCode === 'SUPER_ADMIN'),
  };
}
