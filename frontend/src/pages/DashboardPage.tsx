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
    if (menu.includes('_management')) {
      const menuType = menu.replace('_management', '');
      navigate(`/system/${menuType}`);
    } else if (menu === 'profile') {
      navigate('/profile');
    } else {
      navigate(`/${menu}`);
    }
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