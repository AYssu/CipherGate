import React, { useState, useEffect, useMemo } from 'react';
import { Layout, Menu, Typography, Space, Avatar, Dropdown, Button, Badge, Drawer, List, Tag, Empty, Modal, Grid } from 'antd';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import {
  AppstoreOutlined,
  AuditOutlined,
  CrownOutlined,
  DashboardOutlined,
  FolderOutlined,
  LockOutlined,
  LogoutOutlined,
  MenuOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import iconSvg from '../assets/icons/icon.svg';
import { userApi } from '../services/userService';
import { activityApi } from '../services/activityService';
import { messageApi, type SystemMessage } from '../services/messageService';
import { announcementApi, type SystemAnnouncement } from '../services/announcementService';
import { docApi } from '../services/docService';
import ThemeSwitcher from '../components/ThemeSwitcher';
import type { User, Menu as UserMenu } from '../services/userService';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const MainLayout: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md; // < 768px
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [collapsed, setCollapsed] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [showBadge, setShowBadge] = useState(false);
  const [notificationVisible, setNotificationVisible] = useState(false);
  const [messages, setMessages] = useState<SystemMessage[]>([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [selectedMessage, setSelectedMessage] = useState<SystemMessage | null>(null);
  const [messageDetailVisible, setMessageDetailVisible] = useState(false);
  const [announcementVisible, setAnnouncementVisible] = useState(false);
  const [currentAnnouncement, setCurrentAnnouncement] = useState<SystemAnnouncement | null>(null);
  const [docMenuData, setDocMenuData] = useState<any[]>([]);
  const navigate = useNavigate();
  const location = useLocation();

  // 处理子菜单展开/收起
  const handleOpenChange = (keys: string[]) => {
    setOpenKeys(keys);
  };

  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        const result = await userApi.getCurrentUserInfo();
        setUserInfo((result as any).data);
      } catch (error) {
        console.error('获取用户信息失败:', error);
      } finally {
        setLoading(false);
      }
    };
    
    const fetchUnreadCount = async () => {
      try {
        const result = await activityApi.getUnreadCount();
        const data = (result as any).data;
        setUnreadCount(data.total);
        setShowBadge(data.showBadge);
      } catch (error) {
        console.error('获取未读消息数失败:', error);
      }
    };
    
    fetchUserInfo();
    fetchUnreadCount();
    
    // 每30秒刷新一次未读消息数
    const interval = setInterval(fetchUnreadCount, 30000);
    
    return () => clearInterval(interval);
  }, []);

  // 检查公告弹窗
  useEffect(() => {
    const checkAnnouncements = async () => {
      try {
        const result = await announcementApi.getActiveAnnouncements();
        const activeAnnouncements = (result as any).data || [];
        if (activeAnnouncements.length === 0) return;

        const shownKey = 'shown_announcements';
        const shownIds: number[] = JSON.parse(localStorage.getItem(shownKey) || '[]');
        const unshown = activeAnnouncements.find((a: SystemAnnouncement) => !shownIds.includes(a.id));

        if (unshown) {
          setCurrentAnnouncement(unshown);
          setAnnouncementVisible(true);
        }
      } catch (error) {
        console.error('获取公告失败:', error);
      }
    };

    checkAnnouncements();

    // 获取文档菜单数据
    const fetchDocMenu = async () => {
      try {
        const result = await docApi.getDocMenu();
        const data = (result as any).data || [];
        setDocMenuData(data);
      } catch (error) {
        // 文档菜单获取失败静默处理
      }
    };
    fetchDocMenu();
  }, []);

  const handleLogout = async () => {
    try {
      await userApi.logout();
      // 退出成功后跳转到首页
      window.location.href = '/';
    } catch (error) {
      console.error('退出登录失败:', error);
      // 即使退出失败，也跳转到首页（可能是网络问题）
      window.location.href = '/';
    }
  };

  // 打开通知面板
  const handleOpenNotification = async () => {
    setNotificationVisible(true);
    setLoadingMessages(true);
    
    try {
      const result = await messageApi.getMyMessages(20);
      setMessages((result as any).data || []);
    } catch (error) {
      console.error('获取消息失败:', error);
    } finally {
      setLoadingMessages(false);
    }
  };

  // 关闭通知面板
  const handleCloseNotification = () => {
    setNotificationVisible(false);
  };

  // 打开消息详情
  const handleOpenMessageDetail = (message: SystemMessage) => {
    setSelectedMessage(message);
    setMessageDetailVisible(true);
    // 如果是未读消息，标记为已读
    if (!message.isRead) {
      handleMarkMessageAsRead(message.id);
    }
  };

  // 关闭消息详情
  const handleCloseMessageDetail = () => {
    setMessageDetailVisible(false);
    setSelectedMessage(null);
  };

  // 关闭公告弹窗
  const handleAnnouncementClose = () => {
    if (currentAnnouncement) {
      const shownKey = 'shown_announcements';
      const shownIds: number[] = JSON.parse(localStorage.getItem(shownKey) || '[]');
      if (!shownIds.includes(currentAnnouncement.id)) {
        shownIds.push(currentAnnouncement.id);
        localStorage.setItem(shownKey, JSON.stringify(shownIds));
      }
    }
    setAnnouncementVisible(false);
    setCurrentAnnouncement(null);
  };

  // 标记消息为已读
  const handleMarkMessageAsRead = async (id: number) => {
    try {
      await messageApi.markAsRead(id);
      // 刷新消息列表
      const result = await messageApi.getMyMessages(20);
      setMessages((result as any).data || []);
      // 刷新未读数
      const countResult = await activityApi.getUnreadCount();
      const data = (countResult as any).data;
      setUnreadCount(data.total);
      setShowBadge(data.showBadge);
    } catch (error) {
      console.error('标记已读失败:', error);
    }
  };

  // 格式化时间
  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    if (days < 30) return `${days}天前`;
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  };

  // 获取重要程度标签
  const getImportanceTag = (level: string) => {
    const levelMap: Record<string, { color: string; text: string }> = {
      'LOW': { color: 'default', text: '低' },
      'MEDIUM': { color: 'blue', text: '中' },
      'HIGH': { color: 'orange', text: '高' },
      'URGENT': { color: 'red', text: '紧急' }
    };
    return levelMap[level] || levelMap['LOW'];
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => navigate('/profile'),
    },
    // 只有超级管理员才显示 API 文档入口
    ...(userInfo?.roles?.some(role => role.roleCode === 'SUPER_ADMIN') ? [{
      key: 'api-docs',
      icon: <SafetyOutlined />,
      label: 'API 文档',
      onClick: () => {
        // 在新标签页打开 API 文档
        const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
        window.open(`${backendUrl}/doc.html`, '_blank');
      },
    }] : []),
    {
      key: 'divider',
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  /**
   * 与后台 menus.icon 对应（存库多为 Ant Icon 组件名或小写别名）。统一小写匹配；未识别则用中性图标。
   */
  const getMenuIcon = (iconName?: string | null) => {
    const key = (iconName || '').trim().toLowerCase();
    const iconMap: Record<string, React.ReactNode> = {
      dashboard: <DashboardOutlined />,
      user: <UserOutlined />,
      setting: <SettingOutlined />,
      safety: <AuditOutlined />,
      security: <LockOutlined />,
      team: <TeamOutlined />,
      menu: <MenuOutlined />,
      lock: <LockOutlined />,
      tool: <ToolOutlined />,
      appstoreoutlined: <AppstoreOutlined />,
      appstore: <AppstoreOutlined />,
      crown: <CrownOutlined />,
      shopping: <AppstoreOutlined />,
      'file-text': <AuditOutlined />,
      message: <AuditOutlined />,
    };
    return iconMap[key] || <FolderOutlined />;
  };

  const normalizeLegacySystemPath = (path: string) => {
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

  const resolveChildRoutePath = (parentKey: string, childKey: string, rawPath?: string | null) => {
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
      app_variable_management: '/applications/variables',
      plugin_list_page: '/plugins/list',
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
        app_variable_management: '/applications/variables',
      };
      return appRouteMap[childKey] || `/applications/${childKey}`;
    }

    if (parentKey === 'plugin_management') {
      return childKey === 'plugin_list_page' ? '/plugins/list' : `/plugins/${childKey}`;
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

  const generateSidebarMenus = (menus: UserMenu[]) => {
    return menus.map(menu => {
      const menuKey = menu.menuCode.toLowerCase();
      
      // 对接文档：用API返回的动态数据构建子菜单
      if (menuKey === 'doc_management' && docMenuData.length > 0) {
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          children: docMenuData.map((cat: any) => ({
            key: `doc-cat-${cat.id}`,
            label: cat.name,
            children: (cat.items || []).map((item: any) => ({
              key: `doc-item-${item.id}`,
              label: item.title,
              onClick: () => navigate(`/admin/docs/view/${item.id}`),
            })),
          })),
        };
      }

      if (menu.children && menu.children.length > 0) {
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          children: menu.children.map((child: any) => {
            const childKey = child.menuCode.toLowerCase();
            
            return {
              key: childKey,
              label: child.menuName,
              onClick: () => {
                const routePath = resolveChildRoutePath(menuKey, childKey, child.path);
                navigate(routePath);
              },
            };
          }),
        };
      } else {
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          onClick: () => {
            if (menu.path && menu.path.startsWith('/')) {
              navigate(menu.path);
            } else if (menuKey === 'dashboard') {
              navigate('/dashboard');
            } else {
              navigate(`/${menuKey}`);
            }
          },
        };
      }
    });
  };

  const routePath = normalizeLegacySystemPath(location.pathname);
  const { selectedMenu, pageTitle, parentMenuKey } = useMemo(() => {
    const defaultMeta = {
      selectedMenu: 'dashboard',
      pageTitle: '控制台',
      parentMenuKey: '',
    };
    if (!userInfo?.menus?.length) {
      return defaultMeta;
    }

    const walk = (menus: UserMenu[], parentKey = ''): typeof defaultMeta => {
      for (const menu of menus) {
        const menuKey = menu.menuCode.toLowerCase();
        const normalizedMenuPath = menu.path ? normalizeLegacySystemPath(menu.path) : '';
        if (normalizedMenuPath && normalizedMenuPath === routePath) {
          return {
            selectedMenu: menuKey,
            pageTitle: menu.menuName || defaultMeta.pageTitle,
            parentMenuKey: parentKey,
          };
        }

        if (menu.children?.length) {
          const childHit = walk(menu.children as UserMenu[], menuKey);
          if (childHit.selectedMenu !== defaultMeta.selectedMenu || childHit.pageTitle !== defaultMeta.pageTitle || childHit.parentMenuKey) {
            return childHit;
          }
        }
      }
      return defaultMeta;
    };

    const matched = walk(userInfo.menus);
    if (matched.selectedMenu !== defaultMeta.selectedMenu || routePath === '/dashboard') {
      return matched;
    }

    if (routePath === '/profile') {
      return { selectedMenu: 'profile', pageTitle: '个人信息', parentMenuKey: '' };
    }

    if (routePath.startsWith('/admin/docs/view/')) {
      const docId = routePath.split('/admin/docs/view/')[1];
      return {
        selectedMenu: `doc-item-${docId}`,
        pageTitle: '对接文档',
        parentMenuKey: 'doc_management',
      };
    }

    if (routePath === '/admin/docs/categories') {
      return {
        selectedMenu: 'doc_management',
        pageTitle: '对接文档',
        parentMenuKey: '',
      };
    }

    return defaultMeta;
  }, [routePath, userInfo?.menus]);

  useEffect(() => {
    if (!parentMenuKey) {
      return;
    }
    setOpenKeys(prev => (prev.includes(parentMenuKey) ? prev : [parentMenuKey]));
  }, [parentMenuKey]);

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh'
      }}>
        <SafetyOutlined spin style={{ fontSize: 48, color: '#1890ff' }} />
      </div>
    );
  }

  const sidebarMenuItems = userInfo?.menus ? generateSidebarMenus(userInfo.menus) : [];

  // 移动端：点击菜单项后关闭抽屉
  const mobileDrawerMenuItems = sidebarMenuItems.map(item => {
    if (item.children) {
      return {
        ...item,
        children: item.children.map((child: any) => ({
          ...child,
          onClick: () => {
            child.onClick?.();
            setMobileDrawerOpen(false);
          },
        })),
      };
    }
    return {
      ...item,
      onClick: () => {
        (item as any).onClick?.();
        setMobileDrawerOpen(false);
      },
    };
  });

  const siderContent = (
    <>
      <div className="sider-logo" style={{
        padding: collapsed && !isMobile ? '24px 8px' : '24px 16px',
        borderBottom: '1px solid #f0f0f0',
        textAlign: 'center',
        transition: 'all 0.2s'
      }}>
        <img
          src={iconSvg}
          alt="CipherGate"
          style={{
            width: 32,
            height: 32,
            marginBottom: collapsed && !isMobile ? 0 : 8
          }}
        />
        {(!collapsed || isMobile) && (
          <Title level={4} style={{ margin: 0, color: '#1a1a2e' }}>
            CipherGate
          </Title>
        )}
      </div>

      <Menu
        mode="inline"
        selectedKeys={[selectedMenu]}
        openKeys={isMobile ? undefined : (collapsed ? [] : openKeys)}
        onOpenChange={isMobile ? undefined : handleOpenChange}
        inlineCollapsed={isMobile ? false : collapsed}
        items={isMobile ? mobileDrawerMenuItems : sidebarMenuItems}
        style={{
          border: 'none',
          padding: '16px 0',
          fontSize: '14px'
        }}
        className="dashboard-sidebar-menu"
      />
    </>
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 桌面端：固定侧边栏 */}
      {!isMobile && (
        <Sider
          theme="light"
          width={250}
          collapsed={collapsed}
          style={{
            boxShadow: '2px 0 8px rgba(0,0,0,0.1)',
            borderRight: '1px solid #f0f0f0'
          }}
        >
          {siderContent}
        </Sider>
      )}

      {/* 移动端：侧边栏抽屉 */}
      {isMobile && (
        <Drawer
          placement="left"
          open={mobileDrawerOpen}
          onClose={() => setMobileDrawerOpen(false)}
          width={280}
          styles={{ body: { padding: 0 } }}
          closable={false}
        >
          {siderContent}
        </Drawer>
      )}

      <Layout>
        <Header style={{
          background: '#fff',
          padding: isMobile ? '0 16px' : '0 24px',
          height: isMobile ? 56 : 64,
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          position: 'relative'
        }}>
          {/* 左侧区域 */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            position: 'absolute',
            left: isMobile ? 16 : 24
          }}>
            <Button
              type="text"
              icon={isMobile ? <MenuOutlined /> : (collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />)}
              onClick={() => isMobile ? setMobileDrawerOpen(true) : setCollapsed(!collapsed)}
              style={{
                fontSize: isMobile ? 18 : 16,
                width: isMobile ? 40 : 32,
                height: isMobile ? 40 : 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            />
            <Title
              level={4}
              style={{
                margin: '0 0 0 8px',
                color: '#1a1a2e',
                fontSize: isMobile ? 16 : 18,
                fontWeight: 500,
                lineHeight: 1,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: isMobile ? 120 : undefined
              }}
            >
              {pageTitle}
            </Title>
          </div>

          {/* 右侧区域 */}
          <div style={{
            position: 'absolute',
            right: isMobile ? 16 : 24,
            top: '50%',
            transform: 'translateY(-50%)'
          }}>
            <Space size={isMobile ? 8 : 12} align="center">
              <ThemeSwitcher />

              <div className="notification-trigger" onClick={handleOpenNotification}>
                <Badge
                  count={unreadCount}
                  size="small"
                  dot={showBadge && unreadCount === 0}
                  offset={isMobile ? [-2, 0] : [-4, 4]}
                  overflowCount={99}
                  className={isMobile ? 'mobile-bell-badge' : undefined}
                  style={isMobile ? { '--ant-badge-font-size': '10px', '--ant-badge-size-height': '16px', '--ant-badge-dot-size': '6px' } as React.CSSProperties : undefined}
                >
                  <BellOutlined style={{
                    fontSize: isMobile ? 16 : 20,
                    color: showBadge ? '#ff4d4f' : undefined
                  }}
                  />
                </Badge>
              </div>

              <Dropdown
                menu={{ items: userMenuItems }}
                placement="bottomRight"
              >
                <Space style={{ cursor: 'pointer' }} size={isMobile ? 4 : 8} align="center">
                  <Avatar
                    src={userInfo?.avatarUrl}
                    icon={<UserOutlined />}
                    size={isMobile ? 28 : 32}
                  />
                  {!isMobile && (
                    <Text strong style={{ fontSize: '14px' }}>
                      {userInfo?.name || userInfo?.login}
                    </Text>
                  )}
                </Space>
              </Dropdown>
            </Space>
          </div>
        </Header>

        <Content style={{
          padding: isMobile ? '12px' : '24px',
          background: '#f5f5f5'
        }}>
          <Outlet context={{ userInfo }} />
        </Content>
      </Layout>

      {/* 消息通知抽屉 */}
      <Drawer
        title="系统消息"
        placement={isMobile ? 'bottom' : 'right'}
        onClose={handleCloseNotification}
        open={notificationVisible}
        width={isMobile ? undefined : 380}
        height={isMobile ? '70vh' : undefined}
        styles={{
          body: { padding: 0, overflow: 'hidden', overflowY: 'auto' as any },
          header: isMobile ? { borderBottom: '1px solid #f0f0f0' } : undefined,
          content: isMobile ? { borderRadius: '16px 16px 0 0', overflow: 'hidden' } : undefined,
        }}
      >
        {isMobile && (
          <div style={{ 
            display: 'flex', 
            justifyContent: 'center', 
            padding: '8px 0 4px',
            position: 'sticky',
            top: 0,
            background: '#fff',
            zIndex: 1
          }}>
            <div style={{
              width: 36,
              height: 4,
              borderRadius: 2,
              background: '#d9d9d9'
            }} />
          </div>
        )}
        {messages.length === 0 ? (
          <div style={{ padding: '80px 24px', textAlign: 'center' }}>
            <Empty 
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无消息"
            />
          </div>
        ) : (
          <List
            loading={loadingMessages}
            dataSource={messages}
            renderItem={(message) => {
              const importanceTag = getImportanceTag(message.importanceLevel);
              const isUnread = !message.isRead;
              
              return (
                <List.Item
                  style={{
                    padding: isMobile ? '12px 16px' : '12px 16px',
                    backgroundColor: isUnread ? '#f0f7ff' : '#fff',
                    cursor: 'pointer',
                    transition: 'background-color 0.2s',
                    borderBottom: '1px solid #f0f0f0',
                    position: 'relative'
                  }}
                  onClick={() => handleOpenMessageDetail(message)}
                  className={isUnread ? 'msg-item-unread' : 'msg-item-read'}
                >
                  <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                    {/* 未读标记 */}
                    {isUnread && (
                      <div style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: '#1890ff',
                        marginTop: 6,
                        flexShrink: 0
                      }} />
                    )}
                    {!isUnread && <div style={{ width: 8, flexShrink: 0 }} />}
                    
                    <div style={{ flex: 1, minWidth: 0 }}>
                      {/* 标题行 */}
                      <div style={{ 
                        display: 'flex', 
                        justifyContent: 'space-between', 
                        alignItems: 'center',
                        marginBottom: 4,
                        gap: 8
                      }}>
                        <Text 
                          strong 
                          style={{ 
                            fontSize: 14,
                            color: '#1a1a1a',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            flex: 1,
                            minWidth: 0
                          }}
                        >
                          {message.title}
                        </Text>
                        
                        <Tag 
                          color={importanceTag.color}
                          style={{ 
                            margin: 0,
                            fontSize: 11,
                            lineHeight: '18px',
                            padding: '0 6px'
                          }}
                        >
                          {importanceTag.text}
                        </Tag>
                      </div>
                      
                      {/* 消息内容预览 */}
                      <Text
                        style={{
                          fontSize: 13,
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          marginBottom: 6,
                          color: '#666',
                          lineHeight: '1.5',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          wordBreak: 'break-all'
                        }}
                      >
                        {message.content}
                      </Text>
                      
                      {/* 时间 */}
                      <Text type="secondary" style={{ fontSize: 12, color: '#999' }}>
                        {formatTime(message.createdTime)}
                      </Text>
                    </div>
                  </div>
                </List.Item>
              );
            }}
          />
        )}
      </Drawer>

      {/* 消息详情弹窗 */}
      <Modal
        title={
          <Space>
            <Text strong style={{ fontSize: isMobile ? 15 : 16 }}>消息详情</Text>
            {selectedMessage && !selectedMessage.isRead && (
              <Badge status="processing" text="未读" />
            )}
          </Space>
        }
        open={messageDetailVisible}
        onCancel={handleCloseMessageDetail}
        footer={[
          <Button key="close" type="primary" onClick={handleCloseMessageDetail} block={isMobile}>
            关闭
          </Button>
        ]}
        width={isMobile ? '92vw' : 600}
        styles={isMobile ? { content: { padding: '12px' } } : undefined}
      >
        {selectedMessage && (
          <div>
            <div style={{ marginBottom: isMobile ? 12 : 16 }}>
              <Space>
                <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>重要程度：</Text>
                <Tag color={getImportanceTag(selectedMessage.importanceLevel).color} style={{ fontSize: isMobile ? 11 : 12 }}>
                  {getImportanceTag(selectedMessage.importanceLevel).text}
                </Tag>
              </Space>
            </div>

            <div style={{ marginBottom: isMobile ? 12 : 16 }}>
              <Text strong style={{ fontSize: isMobile ? 15 : 16, display: 'block', marginBottom: isMobile ? 6 : 8 }}>
                {selectedMessage.title}
              </Text>
            </div>

            <div style={{
              marginBottom: isMobile ? 12 : 16,
              padding: isMobile ? 12 : 16,
              backgroundColor: '#fafafa',
              borderRadius: 8,
              lineHeight: '1.7'
            }}>
              <Text style={{ fontSize: isMobile ? 13 : 14, whiteSpace: 'pre-wrap' }}>
                {selectedMessage.content}
              </Text>
            </div>

            <div>
              <Space size={4}>
                <ClockCircleOutlined style={{ fontSize: 11, color: '#8c8c8c' }} />
                <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>
                  {new Date(selectedMessage.createdTime).toLocaleString('zh-CN')}
                </Text>
              </Space>
            </div>
          </div>
        )}
      </Modal>

      {/* 公告弹窗 */}
      <Modal
        title="系统公告"
        open={announcementVisible}
        onCancel={handleAnnouncementClose}
        footer={[
          <Button key="close" type="primary" onClick={handleAnnouncementClose} block={isMobile}>
            我知道了
          </Button>,
        ]}
        width={isMobile ? '92vw' : 600}
        styles={isMobile ? { content: { padding: '12px' } } : undefined}
      >
        {currentAnnouncement && (
          <div>
            <Text strong style={{ fontSize: isMobile ? 15 : 16, display: 'block', marginBottom: isMobile ? 8 : 12 }}>
              {currentAnnouncement.title}
            </Text>
            <div style={{
              padding: isMobile ? 12 : 16,
              backgroundColor: '#fafafa',
              borderRadius: 8,
              lineHeight: 1.8,
              whiteSpace: 'pre-wrap',
            }}>
              {currentAnnouncement.content}
            </div>
            <div style={{ marginTop: isMobile ? 8 : 12 }}>
              <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>
                {new Date(currentAnnouncement.createdAt).toLocaleString('zh-CN')}
              </Text>
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  );
};



export default MainLayout;
