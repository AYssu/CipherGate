import React from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { DashboardContent } from '../components';
import type { User } from '../services/userService';

interface OutletContext {
  userInfo: User | null;
}

const DashboardPage: React.FC = () => {
  const { userInfo } = useOutletContext<OutletContext>();
  const navigate = useNavigate();

  const isAdmin = () => {
    return userInfo?.roles?.some(role => 
      role.roleCode === 'SUPER_ADMIN' || role.roleCode === 'ADMIN'
    ) || false;
  };

  const handleMenuChange = (menu: string) => {
    const menuRouteMap: Record<string, string> = {
      dashboard: '/dashboard',
      profile: '/profile',
      user_management: '/system/users',
      role_management: '/system/roles',
      menu_management: '/system/menus',
      permission_management: '/system/permissions',
      system_config: '/system/info',
      system_setting: '/system/config',
      system_settings: '/system/config',
      app_list_page: '/applications/list',
      license_management: '/applications/licenses',
      app_user_management: '/applications/users',
      app_variable_management: '/applications/variables',
      plugin_list_page: '/plugins/list',
    };

    navigate(menuRouteMap[menu] || '/dashboard');
  };

  return (
    <DashboardContent 
      userInfo={userInfo}
      isAdmin={isAdmin}
      setSelectedMenu={handleMenuChange}
    />
  );
};

export default DashboardPage;