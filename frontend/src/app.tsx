import type { ReactNode } from 'react';
import type { RunTimeLayoutConfig } from '@umijs/max';
import { history, Link } from '@umijs/max';
import { userApi, type User } from './services/userService';
import { docApi, type DocMenuCategory } from './services/docService';
import { buildMenuData } from './navigation/menu';
import { isAdminPath } from './navigation/routePolicy';
import AppProviders from './providers/AppProviders';
import HeaderActions from './components/HeaderActions';
import NavigationToggle from './components/NavigationToggle';
import iconSvg from './assets/icons/icon.svg';
import defaultSettings from '../config/defaultSettings';
import renderHeader from './layouts/renderHeader';

export interface InitialState {
  currentUser?: User;
  documents: DocMenuCategory[];
  adminSessionLoaded: boolean;
  colorPrimary?: string;
}
export async function getInitialState(): Promise<InitialState> {
  if (!isAdminPath(history.location.pathname)) return { documents: [], adminSessionLoaded: false };
  try {
    const { data: currentUser } = await userApi.getCurrentUserInfo();
    const documents = await docApi.getDocMenu().then(result => result.data || []).catch(() => []);
    return { currentUser, documents, adminSessionLoaded: true };
  } catch (error) {
    // A transport failure stays recoverable instead of being mistaken for a logged-out account.
    if (error && typeof error === 'object' && 'response' in error
      && (error.response as { status?: number })?.status === 401) history.replace('/');
    return { documents: [], adminSessionLoaded: true };
  }
}
export function rootContainer(container: ReactNode) {
  return <AppProviders>{container}</AppProviders>;
}
export const layout: RunTimeLayoutConfig = ({ initialState }) => ({
  ...defaultSettings,
  logo: iconSvg,
  headerRender: renderHeader,
  collapsedButtonRender: collapsed => <NavigationToggle collapsed={Boolean(collapsed)} />,
  colorPrimary: initialState?.colorPrimary || '#1677ff',
  menu: {
    locale: false,
    params: { userId: initialState?.currentUser?.id },
    request: async () => buildMenuData(initialState?.currentUser?.menus, initialState?.documents),
  },
  menuItemRender: (item, dom, menuProps) => item.path
    ? <Link to={item.path} onClick={() => { if (menuProps.isMobile) item.onClick(); }}>{dom}</Link> : dom,
  actionsRender: () => initialState?.currentUser ? [<HeaderActions key="account-actions" />] : [],
  avatarProps: undefined,
  footerRender: false,
  menuFooterRender: collapsed => !collapsed && <span className="ciphergate-menu-footer">身份与授权管理</span>,
});
