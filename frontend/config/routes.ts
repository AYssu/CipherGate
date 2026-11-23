const routes = [
  {
    path: '/',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/pages/Home',
  },
  {
    path: '/register',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/pages/AppUserRegisterPage',
  },
  {
    path: '/license',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/pages/LicenseSelfServicePage',
  },
  {
    path: '/app-user',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/pages/AppUserSelfServicePage',
  },
  {
    path: '/login',
    layout: false,
    redirect: '/',
  },
  {
    path: '/',
    component: '@/layouts/MainLayout',
    routes: [
      {
        path: '/dashboard',
        component: '@/pages/DashboardPage',
      },
      {
        path: '/profile',
        component: '@/pages/ProfilePage',
      },
      {
        path: '/applications',
        routes: [
          { path: 'list', component: '@/pages/Application/ApplicationManagementPage' },
          { path: 'licenses', component: '@/pages/License/LicenseManagementPage' },
          { path: 'users', component: '@/pages/AppUser/AppUserManagementPage' },
          { path: 'user-transfer', component: '@/pages/AppUser/AppUserTransferPage' },
          { path: 'variables', component: '@/pages/AppVariable/AppVariableManagementPage' },
          { path: 'credentials', component: '@/pages/Application/ThirdPartyCredentialManagementPage' },
          { path: 'call-logs', component: '@/pages/Application/CallLogManagementPage' },
        ],
      },
      {
        path: '/user',
        routes: [
          { path: 'membership', component: '@/pages/Membership/MembershipInfoPage' },
          { path: 'balance', component: '@/pages/Membership/BalancePage' },
          { path: 'checkin', component: '@/pages/Membership/CheckinPage' },
          { path: 'invite', component: '@/pages/Membership/InvitePage' },
          { path: 'orders', component: '@/pages/Membership/UserOrderPage' },
          { path: 'tickets', component: '@/pages/Membership/UserTicketPage' },
        ],
      },
      {
        path: '/system',
        routes: [
          { path: 'users', component: '@/pages/System/UserManagementPage' },
          { path: 'roles', component: '@/pages/System/RoleManagementPage' },
          { path: 'menus', component: '@/pages/System/MenuManagementPage' },
          { path: 'permissions', component: '@/pages/System/PermissionManagementPage' },
          { path: 'info', component: '@/pages/System/SystemInfoPage' },
          { path: 'config', component: '@/pages/System/SystemConfigPage' },
          { path: 'announcements', component: '@/pages/System/AnnouncementManagementPage' },
        ],
      },
      {
        path: '/membership',
        routes: [
          { path: 'levels', component: '@/pages/System/MembershipLevelPage' },
          { path: 'users', component: '@/pages/System/UserMembershipPage' },
          { path: 'products', component: '@/pages/System/QuotaProductPage' },
        ],
      },
      {
        path: '/operation',
        routes: [
          { path: 'orders', component: '@/pages/System/AdminOrderPage' },
          { path: 'tickets', component: '@/pages/System/AdminTicketPage' },
        ],
      },
      {
        path: '/plugins',
        routes: [
          { path: 'list', component: '@/pages/Plugin/PluginManagementPage' },
          { path: 'function', component: '@/pages/Plugin/FunctionPluginManagementPage' },
        ],
      },
      {
        path: '/admin/docs',
        routes: [
          { path: 'categories', component: '@/pages/Doc/DocManagementPage' },
          { path: 'view/:id', component: '@/pages/Doc/DocViewPage' },
        ],
      },
    ],
  },
  {
    path: '/portal/login',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/portal/pages/PortalLoginPage',
  },
  {
    path: '/portal/recovery',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/portal/pages/PortalPasswordRecoveryPage',
  },
  {
    path: '/portal/select-app',
    layout: false,
    wrappers: ['@/layouts/PublicLayout'],
    component: '@/portal/pages/PortalAppSelectPage',
  },
  {
    path: '/portal',
    layout: false,
    component: '@/portal/layouts/PortalLayout',
    routes: [
      { path: 'dashboard', component: '@/portal/pages/PortalDashboardPage' },
      { path: 'membership', component: '@/portal/pages/PortalMembershipPage' },
      { path: 'recharge', component: '@/portal/pages/PortalRechargePage' },
      { path: 'orders', component: '@/portal/pages/PortalOrderHistoryPage' },
      { path: 'settings', component: '@/portal/pages/PortalSettingsPage' },
    ],
  },
  {
    path: '*',
    layout: false,
    redirect: '/',
  },
];

export default routes;
