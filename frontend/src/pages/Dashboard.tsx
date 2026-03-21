import React, { useState, useEffect } from 'react';
import { Layout, Menu, Button, Typography, Space, Avatar, Dropdown } from 'antd';
import { 
  SafetyOutlined,
  UserOutlined, 
  LogoutOutlined,
  DashboardOutlined,
  SecurityScanOutlined,
  SettingOutlined,
  BellOutlined,
  TeamOutlined,
  MenuOutlined,
  LockOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import type { User, Menu as UserMenu } from '../services/userService';

// 导入内容组件
import {
  UserManagementContent,
  RoleManagementContent,
  MenuManagementContent,
  PermissionManagementContent,
  SystemConfigContent,
  ProfileContent,
  DashboardContent
} from '../components';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const Dashboard: React.FC = () => {
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedMenu, setSelectedMenu] = useState('dashboard');

  useEffect(() => {
    // 获取用户完整信息（包含菜单）
    const fetchUserInfo = async () => {
      try {
        const result = await userApi.getCurrentUserInfo();
        console.log('获取用户信息结果：', result);
        setUserInfo((result as any).data);
        console.log('用户菜单数据：', (result as any).data.menus);
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
      // 使用原生fetch处理登出，因为这不是标准的API响应
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
      onClick: () => setSelectedMenu('profile'),
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

  // 根据用户菜单权限生成侧边栏菜单
  const generateSidebarMenus = (menus: UserMenu[]) => {
    console.log('生成侧边栏菜单，菜单数据：', menus);
    
    return menus.map(menu => {
      console.log(`处理菜单: ${menu.menuName} (${menu.menuCode}), 子菜单数量: ${menu.children?.length || 0}`);
      
      return {
        key: menu.menuCode.toLowerCase(),
        icon: getMenuIcon(menu.icon),
        label: menu.menuName,
        onClick: menu.children && menu.children.length > 0 ? undefined : () => {
          console.log(`点击菜单: ${menu.menuName} (${menu.menuCode})`);
          setSelectedMenu(menu.menuCode.toLowerCase());
        },
        children: menu.children && menu.children.length > 0 ? menu.children.map((child: any) => ({
          key: child.menuCode.toLowerCase(),
          label: child.menuName,
          onClick: () => {
            console.log(`点击子菜单: ${child.menuName} (${child.menuCode})`);
            setSelectedMenu(child.menuCode.toLowerCase());
          },
        })) : undefined,
      };
    });
  };

  const getMenuIcon = (iconName: string) => {
    switch (iconName) {
      case 'dashboard':
        return <DashboardOutlined />;
      case 'user':
        return <UserOutlined />;
      case 'setting':
        return <SettingOutlined />;
      case 'safety':
        return <SafetyOutlined />;
      case 'security':
        return <SecurityScanOutlined />;
      case 'team':
        return <TeamOutlined />;
      case 'menu':
        return <MenuOutlined />;
      case 'lock':
        return <LockOutlined />;
      case 'tool':
        return <SettingOutlined />;
      default:
        return <DashboardOutlined />;
    }
  };

  const isAdmin = () => {
    return userInfo?.roles?.some(role => 
      role.roleCode === 'SUPER_ADMIN' || role.roleCode === 'ADMIN'
    ) || false;
  };

  // 渲染右侧内容
  const renderContent = () => {
    switch (selectedMenu) {
      case 'user_management':
        return <UserManagementContent />;
      case 'role_management':
        return <RoleManagementContent />;
      case 'menu_management':
        return <MenuManagementContent />;
      case 'permission_management':
        return <PermissionManagementContent />;
      case 'system_config':
        return <SystemConfigContent />;
      case 'profile':
        return <ProfileContent userInfo={userInfo} />;
      case 'dashboard':
      default:
        return (
          <DashboardContent 
            userInfo={userInfo}
            isAdmin={isAdmin}
            setSelectedMenu={setSelectedMenu}
          />
        );
    }
  };

  const getPageTitle = () => {
    switch (selectedMenu) {
      case 'user_management':
        return '用户管理';
      case 'role_management':
        return '角色管理';
      case 'menu_management':
        return '菜单管理';
      case 'permission_management':
        return '权限管理';
      case 'system_config':
        return '系统配置';
      case 'profile':
        return '个人信息';
      case 'dashboard':
      default:
        return '控制台';
    }
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
            {getPageTitle()}
          </Title>
          
          <Space size="middle">
            <Button type="text" icon={<BellOutlined />} />
            
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
          {renderContent()}
        </Content>
      </Layout>
    </Layout>
  );
};

export default Dashboard;