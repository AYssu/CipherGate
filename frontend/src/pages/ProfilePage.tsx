import React from 'react';
import { useOutletContext } from 'react-router-dom';
import { ProfileContent } from '../components';
import type { User } from '../services/userService';

interface OutletContext {
  userInfo: User | null;
}

const ProfilePage: React.FC = () => {
  const { userInfo } = useOutletContext<OutletContext>();

  return <ProfileContent userInfo={userInfo} />;
};

export default ProfilePage;