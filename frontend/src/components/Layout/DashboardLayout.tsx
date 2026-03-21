import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Space, Avatar, Dropdown } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { 
  SafetyOutlined,
  UserOutlined, 
  LogoutOutlined,
  BellOutlined
} from '@ant-design/icons';
import { userApi } from '../../services/userService';
import type { User, Menu as UserMenu } from '../../services/userService';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

interface DashboardLayoutProps {
  children: React.ReactNode;
  pageTitle?: string;
}

const DashboardLayout: React.FC<DashboardLayoutProps> = ({ 
  children, 
  pageTitle = '控制台'
}) => {
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
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
      await fetch('http://localhost:8080/logout', {
        method: 'POST',
        credentials: 'include'
      });
      window.location.href = '/';
    } catch (error) {
      console.error('退出登录失败:', error);
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
    console.log('所有菜单数据:', menus);
    
    return menus.map(menu => {
      const menuKey = menu.menuCode.toLowerCase();
      console.log('处理菜单:', menu.menuName, 'menuCode:', menu.menuCode, 'menuKey:', menuKey);
      
      if (menu.children && menu.children.length > 0) {
        console.log('子菜单:', menu.children);
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          children: menu.children.map((child: any) => {
            const childKey = child.menuCode.toLowerCase();
            console.log('子菜单项:', child.menuName, 'menuCode:', child.menuCode, 'childKey:', childKey);
            
            return {
              key: childKey,
              label: child.menuName,
              onClick: () => {
                console.log('点击子菜单:', child.menuName, 'menuCode:', child.menuCode, 'childKey:', childKey);
                
                if (menuKey === 'system_management') {
                  // 根据实际的菜单代码映射到路由
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
                      // 如果没有匹配的，尝试去掉 _management 后缀
                      const cleanKey = childKey.replace('_management', '');
                      routePath = `/system/${cleanKey}`;
                  }
                  console.log('导航到:', routePath);
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
            console.log('点击一级菜单:', menu.menuName, 'menuCode:', menu.menuCode, 'menuKey:', menuKey);
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
        style={{
          boxShadow: '2px 0 8px rgba(0,0,0,0.1)',
          borderRight: '1px solid #f0f0f0'
        }}
      >
        <div style={{
          padding: '24px 16px',
          borderBottom: '1px solid #f0f0f0',
          textAlign: 'center'
        }}>
          <SafetyOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 8 }} />
          <Title level={4} style={{ margin: 0, color: '#1a1a2e' }}>
            CipherGate
          </Title>
        </div>
        
        <Menu
          mode="inline"
          selectedKeys={[selectedMenu]}
          defaultOpenKeys={selectedMenu?.includes('_management') ? ['system_management'] : []}
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
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <Title level={4} style={{ margin: 0, color: '#1a1a2e' }}>
            {pageTitle}
          </Title>
          
          <Space size="middle">
            <BellOutlined style={{ fontSize: 16, cursor: 'pointer' }} />
            
            <Dropdown
              menu={{ items: userMenuItems }}
              placement="bottomRight"
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar 
                  src={userInfo?.avatarUrl} 
                  icon={<UserOutlined />}
                  size="small"
                />
                <Text strong>{userInfo?.name || userInfo?.login}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ padding: '24px', background: '#f5f5f5' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default DashboardLayout;