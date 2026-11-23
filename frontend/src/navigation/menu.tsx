import React from 'react';
import { AppstoreOutlined, AuditOutlined, CrownOutlined, DashboardOutlined, FolderOutlined, LockOutlined, MenuOutlined, SettingOutlined, TeamOutlined, ToolOutlined, UserOutlined } from '@ant-design/icons';
import type { MenuDataItem } from '@ant-design/pro-components';
import type { Menu } from '../services/userService';
import type { DocMenuCategory } from '../services/docService';

export const normalizeLegacySystemPath = (path: string) => {
    if (!path.startsWith('/system?')) return path;
    try {
      const url = new URL(path, 'http://localhost');
      const tab = url.searchParams.get('tab');
      const tabRouteMap: Record<string, string> = {
        users: '/system/users',
        roles: '/system/roles',
        menus: '/system/menus',
        permissions: '/system/permissions',
        info: '/system/info',
        config: '/system/config',
      };
      return tab ? (tabRouteMap[tab] || '/system/info') : '/system/info';
    } catch {
      return path;
    }
  };

export const resolveChildRoutePath = (parentKey: string, childKey: string, rawPath?: string | null) => {
    const normalizedPath = rawPath ? normalizeLegacySystemPath(rawPath) : '';
    if (normalizedPath.startsWith('/')) {
      return normalizedPath;
    }

    // 优先按子菜单编码做全局兜底，避免依赖父级 menuCode 命名
    const globalRouteMap: Record<string, string> = {
      dashboard: '/dashboard',
      profile: '/profile',
      user_management: '/system/users',
      users: '/system/users',
      role_management: '/system/roles',
      roles: '/system/roles',
      menu_management: '/system/menus',
      menus: '/system/menus',
      permission_management: '/system/permissions',
      permissions: '/system/permissions',
      system_config: '/system/info',
      system_info: '/system/info',
      system_setting: '/system/config',
      system_settings: '/system/config',
      membership_info: '/user/membership',
      balance_management: '/user/balance',
      daily_checkin: '/user/checkin',
      invite_reward: '/user/invite',
      my_orders: '/user/orders',
      my_tickets: '/user/tickets',
      membership_level_management: '/membership/levels',
      user_membership_management: '/membership/users',
      quota_product_management: '/membership/products',
      order_management: '/operation/orders',
      ticket_management: '/operation/tickets',
      app_list_page: '/applications/list',
      license_management: '/applications/licenses',
      app_user_management: '/applications/users',
      app_user_transfer: '/applications/user-transfer',
      app_variable_management: '/applications/variables',
      plugin_list_page: '/plugins/list',
      function_plugin_list_page: '/plugins/function',
      doc_management_admin: '/admin/docs/categories',
      announcement_management: '/system/announcements',
    };
    const globalMatchedPath = globalRouteMap[childKey];
    if (globalMatchedPath) {
      return globalMatchedPath;
    }

    if (parentKey === 'system_management') {
      const systemRouteMap: Record<string, string> = {
        user_management: '/system/users',
        users: '/system/users',
        user: '/system/users',
        role_management: '/system/roles',
        roles: '/system/roles',
        role: '/system/roles',
        menu_management: '/system/menus',
        menus: '/system/menus',
        menu: '/system/menus',
        permission_management: '/system/permissions',
        permissions: '/system/permissions',
        permission: '/system/permissions',
        system_config: '/system/info',
        system_info: '/system/info',
        info: '/system/info',
        system_setting: '/system/config',
        system_settings: '/system/config',
        config: '/system/config',
        announcement_management: '/system/announcements',
        announcements: '/system/announcements',
      };
      const matchedPath = systemRouteMap[childKey];
      if (matchedPath) return matchedPath;
      return '/system/info';
    }

    if (parentKey === 'membership_management') {
      const membershipRouteMap: Record<string, string> = {
        membership_level_management: '/membership/levels',
        user_membership_management: '/membership/users',
        quota_product_management: '/membership/products',
      };
      const matchedPath = membershipRouteMap[childKey];
      if (matchedPath) return matchedPath;
      return '/membership/levels';
    }

    if (parentKey === 'operation_management') {
      const operationRouteMap: Record<string, string> = {
        order_management: '/operation/orders',
        ticket_management: '/operation/tickets',
      };
      const matchedPath = operationRouteMap[childKey];
      if (matchedPath) return matchedPath;
      return '/operation/orders';
    }

    if (parentKey === 'app_management') {
      const appRouteMap: Record<string, string> = {
        app_list_page: '/applications/list',
        license_management: '/applications/licenses',
        app_user_management: '/applications/users',
        app_user_transfer: '/applications/user-transfer',
        app_variable_management: '/applications/variables',
      };
      return appRouteMap[childKey] || `/applications/${childKey}`;
    }

    if (parentKey === 'plugin_management') {
      if (childKey === 'plugin_list_page') return '/plugins/list';
      if (childKey === 'function_plugin_list_page') return '/plugins/function';
      return `/plugins/${childKey}`;
    }

    if (parentKey === 'my_membership') {
      const membershipRouteMap: Record<string, string> = {
        membership_info: '/user/membership',
        balance_management: '/user/balance',
        daily_checkin: '/user/checkin',
        invite_reward: '/user/invite',
        my_orders: '/user/orders',
        my_tickets: '/user/tickets',
      };
      return membershipRouteMap[childKey] || `/user/${childKey}`;
    }

    // 最终兜底：尽量落到控制台，避免命中 * 路由被重定向回 /
    return '/dashboard';
  };


const icons: Record<string, React.ReactNode> = {
  dashboard: <DashboardOutlined />, user: <UserOutlined />, setting: <SettingOutlined />,
  safety: <AuditOutlined />, security: <LockOutlined />, team: <TeamOutlined />,
  menu: <MenuOutlined />, lock: <LockOutlined />, tool: <ToolOutlined />,
  appstoreoutlined: <AppstoreOutlined />, appstore: <AppstoreOutlined />,
  crown: <CrownOutlined />, shopping: <AppstoreOutlined />, 'file-text': <AuditOutlined />,
  message: <AuditOutlined />,
};

/** Render only server-authorized menus; document categories inherit their parent permission. */
export function buildMenuData(menus: Menu[] = [], docs: DocMenuCategory[] = [], parent = ''): MenuDataItem[] {
  return menus.filter(menu => menu.visible !== 0 && menu.status !== 0).map(menu => {
    const key = menu.menuCode.toLowerCase();
    const item: MenuDataItem = { key, name: menu.menuName, icon: icons[(menu.icon || '').toLowerCase()] || <FolderOutlined /> };
    if (key === 'doc_management' && docs.length) {
      item.children = docs.map(category => ({ key: `doc-cat-${category.id}`, name: category.name,
        children: category.items.map(doc => ({ key: `doc-item-${doc.id}`, name: doc.title, path: `/admin/docs/view/${doc.id}` })) }));
    } else if (menu.children?.length) {
      item.children = buildMenuData(menu.children, docs, key);
    } else {
      item.path = resolveChildRoutePath(parent, key, menu.path);
    }
    return item;
  });
}
