import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Space, Avatar, Dropdown, Button } from 'antd';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { 
  SafetyOutlined,
  UserOutlined, 
  LogoutOutlined,
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import type { User, Menu as UserMenu } from '../services/userService';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const MainLayout: React.FC = () => {
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const navigate = useNavigate();
  const location = useLocation();

  // 根据当前路由自动计算 selectedMenu
  const getSelectedMenuFromPath = (pathname: string) => {
    if (pathname === '/dashboard') return 'dashboard';
    if (pathname === '/profile') return 'profile';
    if (pathname.startsWith('/system/')) {
      const systemPath = pathname.replace('/system/', '');
      switch (systemPath) {
        case 'users': return 'user_management';
        case 'roles': return 'role_management';
        case 'menus': return 'menu_management';
        case 'permissions': return 'permission_management';
        case 'config': return 'system_config';
        default: return 'dashboard';
      }
    }
    return 'dashboard';
  };

  const selectedMenu = getSelectedMenuFromPath(location.pathname);

  // 根据当前选中的菜单自动设置展开的父菜单
  useEffect(() => {
    if (selectedMenu?.includes('_management')) {
      setOpenKeys(['system_management']);
    }
  }, [selectedMenu]);

  // 获取页面标题
  const getPageTitle = (pathname: string) => {
    if (pathname === '/dashboard') return '控制台';
    if (pathname === '/profile') return '个人信息';
    if (pathname.startsWith('/system/')) {
      const systemPath = pathname.replace('/system/', '');
      switch (systemPath) {
        case 'users': return '用户管理';
        case 'roles': return '角色管理';
        case 'menus': return '菜单管理';
        case 'permissions': return '权限管理';
        case 'config': return '系统配置';
        default: return '控制台';
      }
    }
    return '控制台';
  };

  // 处理子菜单展开/收起
  const handleOpenChange = (keys: string[]) => {
    setOpenKeys(keys);
  };

  const pageTitle = getPageTitle(location.pathname);

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
    
    fetchUserInfo();
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

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => navigate('/profile'),
    },
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

  const getMenuIcon = (iconName: string) => {
    const iconMap: Record<string, React.ReactNode> = {
      'dashboard': <SafetyOutlined />,
      'user': <UserOutlined />,
      'setting': <SafetyOutlined />,
      'safety': <SafetyOutlined />,
      'security': <SafetyOutlined />,
      'team': <UserOutlined />,
      'menu': <SafetyOutlined />,
      'lock': <SafetyOutlined />,
      'tool': <SafetyOutlined />,
    };
    return iconMap[iconName] || <SafetyOutlined />;
  };

  const generateSidebarMenus = (menus: UserMenu[]) => {
    return menus.map(menu => {
      const menuKey = menu.menuCode.toLowerCase();
      
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
                if (menuKey === 'system_management') {
                  let routePath = '';
                  switch (childKey) {
                    case 'user_management':
                      routePath = '/system/users';
                      break;
                    case 'role_management':
                      routePath = '/system/roles';
                      break;
                    case 'menu_management':
                      routePath = '/system/menus';
                      break;
                    case 'permission_management':
                      routePath = '/system/permissions';
                      break;
                    case 'system_config':
                      routePath = '/system/config';
                      break;
                    default:
                      const cleanKey = childKey.replace('_management', '');
                      routePath = `/system/${cleanKey}`;
                  }
                  navigate(routePath);
                } else {
                  navigate(`/${childKey}`);
                }
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
            if (menuKey === 'dashboard') {
              navigate('/dashboard');
            } else {
              navigate(`/${menuKey}`);
            }
          },
        };
      }
    });
  };

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

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        theme="light"
        width={250}
        collapsed={collapsed}
        style={{
          boxShadow: '2px 0 8px rgba(0,0,0,0.1)',
          borderRight: '1px solid #f0f0f0'
        }}
      >
        <div className="sider-logo" style={{
          padding: collapsed ? '24px 8px' : '24px 16px',
          borderBottom: '1px solid #f0f0f0',
          textAlign: 'center',
          transition: 'all 0.2s'
        }}>
          <SafetyOutlined style={{ 
            fontSize: 32, 
            color: '#1890ff', 
            marginBottom: collapsed ? 0 : 8
          }} />
          {!collapsed && (
            <Title level={4} style={{ margin: 0, color: '#1a1a2e' }}>
              CipherGate
            </Title>
          )}
        </div>
        
        <Menu
          mode="inline"
          selectedKeys={[selectedMenu]}
          openKeys={collapsed ? [] : openKeys}
          onOpenChange={handleOpenChange}
          inlineCollapsed={collapsed}
          items={sidebarMenuItems}
          style={{ 
            border: 'none', 
            padding: '16px 0',
            fontSize: '14px'
          }}
          className="dashboard-sidebar-menu"
        />
      </Sider>

      <Layout>
        <Header style={{
          background: '#fff',
          padding: '0 24px',
          height: '64px',
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
            left: '24px'
          }}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{
                fontSize: '16px',
                width: 32,
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            />
            <Title 
              level={4}
              style={{ 
                margin: '0 0 0 12px', 
                color: '#1a1a2e',
                fontSize: '18px',
                fontWeight: 500,
                lineHeight: 1
              }}
            >
              {pageTitle}
            </Title>
          </div>
          
          {/* 右侧区域 */}
          <div style={{
            position: 'absolute',
            right: '24px',
            top: '50%',
            transform: 'translateY(-50%)'
          }}>
            <Space size={12} align="center">
              <BellOutlined style={{ 
                fontSize: 16, 
                cursor: 'pointer'
              }} />
              
              <Dropdown
                menu={{ items: userMenuItems }}
                placement="bottomRight"
              >
                <Space style={{ cursor: 'pointer' }} size={8} align="center">
                  <Avatar 
                    src={userInfo?.avatarUrl} 
                    icon={<UserOutlined />}
                    size={32}
                  />
                  <Text strong style={{ fontSize: '14px' }}>
                    {userInfo?.name || userInfo?.login}
                  </Text>
                </Space>
              </Dropdown>
            </Space>
          </div>
        </Header>

        <Content style={{ 
          padding: '24px', 
          background: '#f5f5f5' 
        }}>
          <Outlet context={{ userInfo }} />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;