import { createBrowserRouter, Navigate } from 'react-router-dom';
import Home from '../pages/Home';
import MainLayout from '../layouts/MainLayout';
import DashboardPage from '../pages/DashboardPage';
import ProfilePage from '../pages/ProfilePage';
import UserManagementPage from '../pages/System/UserManagementPage';
import RoleManagementPage from '../pages/System/RoleManagementPage';
import MenuManagementPage from '../pages/System/MenuManagementPage';
import PermissionManagementPage from '../pages/System/PermissionManagementPage';
import SystemConfigPage from '../pages/System/SystemConfigPage';
import SystemInfoPage from '../pages/System/SystemInfoPage';
import ApplicationManagementPage from '../pages/Application/ApplicationManagementPage';
import LicenseManagementPage from '../pages/License/LicenseManagementPage';
import AppUserManagementPage from '../pages/AppUser/AppUserManagementPage';
import AppVariableManagementPage from '../pages/AppVariable/AppVariableManagementPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Home />,
  },
  {
    path: '/login',
    element: <Navigate to="/" replace />,
  },
  {
    element: <MainLayout />,
    children: [
      {
        path: '/dashboard',
        element: <DashboardPage />,
      },
      {
        path: '/profile',
        element: <ProfilePage />,
      },
      {
        path: '/applications',
        children: [
          {
            path: 'list',
            element: <ApplicationManagementPage />,
          },
          {
            path: 'licenses',
            element: <LicenseManagementPage />,
          },
          {
            path: 'users',
            element: <AppUserManagementPage />,
          },
          {
            path: 'variables',
            element: <AppVariableManagementPage />,
          },
        ],
      },
      {
        path: '/system',
        children: [
          {
            path: 'users',
            element: <UserManagementPage />,
          },
          {
            path: 'roles',
            element: <RoleManagementPage />,
          },
          {
            path: 'menus',
            element: <MenuManagementPage />,
          },
          {
            path: 'permissions',
            element: <PermissionManagementPage />,
          },
          {
            path: 'info',
            element: <SystemInfoPage />,
          },
          {
            path: 'config',
            element: <SystemConfigPage />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]);