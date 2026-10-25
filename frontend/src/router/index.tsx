import { Home, AppUserRegisterPage, LicenseSelfServicePage, AppUserSelfServicePage, MainLayout, DashboardPage, ProfilePage, UserManagementPage, RoleManagementPage, MenuManagementPage, PermissionManagementPage, SystemConfigPage, SystemInfoPage, ApplicationManagementPage, ThirdPartyCredentialManagementPage, CallLogManagementPage, LicenseManagementPage, AppUserManagementPage, AppUserTransferPage, AppVariableManagementPage, PluginManagementPage, FunctionPluginManagementPage, AnnouncementManagementPage, MembershipInfoPage, BalancePage, CheckinPage, InvitePage, UserOrderPage, UserTicketPage, MembershipLevelPage, UserMembershipPage, QuotaProductPage, AdminOrderPage, AdminTicketPage, DocManagementPage, DocViewPage, PortalLoginPage, PortalAppSelectPage, PortalLayout, PortalDashboardPage, PortalMembershipPage, PortalRechargePage, PortalOrderHistoryPage, PortalSettingsPage, PortalPasswordRecoveryPage } from './lazyPages';
import { createBrowserRouter, Navigate } from 'react-router-dom';

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
          { path: 'user-transfer', element: <AppUserTransferPage /> },
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
