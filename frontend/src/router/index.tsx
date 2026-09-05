import { createBrowserRouter, Navigate } from 'react-router-dom';
import Home from '../pages/Home';
import AppUserRegisterPage from '../pages/AppUserRegisterPage';
import LicenseSelfServicePage from '../pages/LicenseSelfServicePage';
import AppUserSelfServicePage from '../pages/AppUserSelfServicePage';
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
import ThirdPartyCredentialManagementPage from '../pages/Application/ThirdPartyCredentialManagementPage';
import CallLogManagementPage from '../pages/Application/CallLogManagementPage';
import LicenseManagementPage from '../pages/License/LicenseManagementPage';
import AppUserManagementPage from '../pages/AppUser/AppUserManagementPage';
import AppVariableManagementPage from '../pages/AppVariable/AppVariableManagementPage';
import PluginManagementPage from '../pages/Plugin/PluginManagementPage';
import FunctionPluginManagementPage from '../pages/Plugin/FunctionPluginManagementPage';
import AnnouncementManagementPage from '../pages/System/AnnouncementManagementPage';
import MembershipInfoPage from '../pages/Membership/MembershipInfoPage';
import BalancePage from '../pages/Membership/BalancePage';
import CheckinPage from '../pages/Membership/CheckinPage';
import InvitePage from '../pages/Membership/InvitePage';
import UserOrderPage from '../pages/Membership/UserOrderPage';
import UserTicketPage from '../pages/Membership/UserTicketPage';
import MembershipLevelPage from '../pages/System/MembershipLevelPage';
import UserMembershipPage from '../pages/System/UserMembershipPage';
import QuotaProductPage from '../pages/System/QuotaProductPage';
import AdminOrderPage from '../pages/System/AdminOrderPage';
import AdminTicketPage from '../pages/System/AdminTicketPage';
import DocManagementPage from '../pages/Doc/DocManagementPage';
import DocViewPage from '../pages/Doc/DocViewPage';
import PortalLoginPage from '../portal/pages/PortalLoginPage';
import PortalAppSelectPage from '../portal/pages/PortalAppSelectPage';
import PortalLayout from '../portal/layouts/PortalLayout';
import PortalDashboardPage from '../portal/pages/PortalDashboardPage';
import PortalMembershipPage from '../portal/pages/PortalMembershipPage';
import PortalRechargePage from '../portal/pages/PortalRechargePage';
import PortalOrderHistoryPage from '../portal/pages/PortalOrderHistoryPage';
import PortalSettingsPage from '../portal/pages/PortalSettingsPage';
import PortalPasswordRecoveryPage from '../portal/pages/PortalPasswordRecoveryPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Home />,
  },
  {
    path: '/register',
    element: <AppUserRegisterPage />,
  },
  {
    path: '/license',
    element: <LicenseSelfServicePage />,
  },
  {
    path: '/app-user',
    element: <AppUserSelfServicePage />,
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
          { path: 'list', element: <ApplicationManagementPage /> },
          { path: 'licenses', element: <LicenseManagementPage /> },
          { path: 'users', element: <AppUserManagementPage /> },
          { path: 'variables', element: <AppVariableManagementPage /> },
          { path: 'credentials', element: <ThirdPartyCredentialManagementPage /> },
          { path: 'call-logs', element: <CallLogManagementPage /> },
        ],
      },
      {
        path: '/user',
        children: [
          { path: 'membership', element: <MembershipInfoPage /> },
          { path: 'balance', element: <BalancePage /> },
          { path: 'checkin', element: <CheckinPage /> },
          { path: 'invite', element: <InvitePage /> },
          { path: 'orders', element: <UserOrderPage /> },
          { path: 'tickets', element: <UserTicketPage /> },
        ],
      },
      {
        path: '/system',
        children: [
          { path: 'users', element: <UserManagementPage /> },
          { path: 'roles', element: <RoleManagementPage /> },
          { path: 'menus', element: <MenuManagementPage /> },
          { path: 'permissions', element: <PermissionManagementPage /> },
          { path: 'info', element: <SystemInfoPage /> },
          { path: 'config', element: <SystemConfigPage /> },
          { path: 'announcements', element: <AnnouncementManagementPage /> },
        ],
      },
      {
        path: '/membership',
        children: [
          { path: 'levels', element: <MembershipLevelPage /> },
          { path: 'users', element: <UserMembershipPage /> },
          { path: 'products', element: <QuotaProductPage /> },
        ],
      },
      {
        path: '/operation',
        children: [
          { path: 'orders', element: <AdminOrderPage /> },
          { path: 'tickets', element: <AdminTicketPage /> },
        ],
      },
      {
        path: '/plugins',
        children: [
          { path: 'list', element: <PluginManagementPage /> },
          { path: 'function', element: <FunctionPluginManagementPage /> },
        ],
      },
      {
        path: '/admin/docs',
        children: [
          { path: 'categories', element: <DocManagementPage /> },
          { path: 'view/:id', element: <DocViewPage /> },
        ],
      },
    ],
  },
  {
    path: '/portal/login',
    element: <PortalLoginPage />,
  },
  {
    path: '/portal/recovery',
    element: <PortalPasswordRecoveryPage />,
  },
  {
    path: '/portal/select-app',
    element: <PortalAppSelectPage />,
  },
  {
    path: '/portal',
    element: <PortalLayout />,
    children: [
      { path: 'dashboard', element: <PortalDashboardPage /> },
      { path: 'membership', element: <PortalMembershipPage /> },
      { path: 'recharge', element: <PortalRechargePage /> },
      { path: 'orders', element: <PortalOrderHistoryPage /> },
      { path: 'settings', element: <PortalSettingsPage /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]);
