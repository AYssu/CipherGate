import React, { useState, useEffect } from 'react';
import { Layout, Menu, Button, Card, Row, Col, Typography, Space, Avatar, Dropdown, Statistic, Tag } from 'antd';
import { 
  SafetyOutlined,
  UserOutlined, 
  LogoutOutlined,
  DashboardOutlined,
  SecurityScanOutlined,
  SettingOutlined,
  BellOutlined,
  GithubOutlined,
  TeamOutlined,
  MenuOutlined,
  LockOutlined,
  MailOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import type { User, Menu as UserMenu } from '../services/userService';

// 导入内容组件
import {
  UserManagementContent,
  RoleManagementContent,
  MenuManagementContent,
  PermissionManagementContent,
  SystemConfigContent
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
        return (
          <div style={{ padding: 0 }}>
            <Card>
              <Row align="middle" gutter={24}>
                <Col>
                  <Avatar 
                    src={userInfo?.avatarUrl} 
                    size={80}
                    icon={<UserOutlined />}
                  />
                </Col>
                <Col flex={1}>
                  <Title level={3} style={{ margin: 0 }}>
                    {userInfo?.name || userInfo?.login}
                  </Title>
                  <Space direction="vertical" size="small">
                    <Text type="secondary">
                      <GithubOutlined /> @{userInfo?.login}
                    </Text>
                    {userInfo?.email && (
                      <Text type="secondary">
                        <MailOutlined /> {userInfo?.email}
                      </Text>
                    )}
                    <Space wrap>
                      {userInfo?.roles?.map(role => (
                        <Tag 
                          key={role.id}
                          color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                          style={{ marginBottom: 4 }}
                        >
                          {role.roleName}
                        </Tag>
                      ))}
                    </Space>
                  </Space>
                </Col>
                <Col>
                  <Space direction="vertical" style={{ textAlign: 'center' }}>
                    <Statistic
                      title="角色数量"
                      value={userInfo?.roles?.length || 0}
                      prefix={<TeamOutlined />}
                    />
                  </Space>
                </Col>
              </Row>
            </Card>
            
            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
              <Col span={12}>
                <Card title="账户信息" style={{ height: '100%' }}>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <div><Text strong>用户ID:</Text> {userInfo?.id}</div>
                    <div><Text strong>GitHub ID:</Text> {userInfo?.githubId}</div>
                    <div><Text strong>用户名:</Text> {userInfo?.login}</div>
                    <div><Text strong>显示名称:</Text> {userInfo?.name || '-'}</div>
                    <div><Text strong>邮箱:</Text> {userInfo?.email || '-'}</div>
                    <div>
                      <Text strong>账户状态:</Text>{' '}
                      <Tag color={userInfo?.status === 1 ? 'green' : 'red'}>
                        {userInfo?.status === 1 ? '正常' : '禁用'}
                      </Tag>
                    </div>
                  </Space>
                </Card>
              </Col>
              
              <Col span={12}>
                <Card title="我的角色" style={{ height: '100%' }}>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {userInfo?.roles?.map(role => (
                      <Card key={role.id} size="small" style={{ marginBottom: 8 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div>
                            <Text strong>{role.roleName}</Text>
                            <br />
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {role.description}
                            </Text>
                          </div>
                          <Tag color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}>
                            {role.roleCode}
                          </Tag>
                        </div>
                      </Card>
                    )) || <Text type="secondary">暂无角色</Text>}
                  </Space>
                </Card>
              </Col>
            </Row>
          </div>
        );
      case 'dashboard':
      default:
        // 仪表板内容
        return (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col span={24}>
                <Card style={{ borderRadius: 8 }}>
                  <Row align="middle" gutter={16}>
                    <Col>
                      <Avatar 
                        src={userInfo?.avatarUrl} 
                        size={64}
                        icon={<UserOutlined />}
                      />
                    </Col>
                    <Col flex={1}>
                      <Title level={3} style={{ margin: 0 }}>
                        欢迎回来, {userInfo?.name || userInfo?.login}!
                      </Title>
                      <Space>
                        <GithubOutlined />
                        <Text type="secondary">@{userInfo?.login}</Text>
                        {userInfo?.email && (
                          <>
                            <Text type="secondary">•</Text>
                            <Text type="secondary">{userInfo.email}</Text>
                          </>
                        )}
                        <Text type="secondary">•</Text>
                        <Text type="secondary">
                          {userInfo?.roles?.map(role => role.roleName).join(', ')}
                        </Text>
                      </Space>
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} lg={6}>
                <Card>
                  <Statistic
                    title="安全事件"
                    value={0}
                    valueStyle={{ color: '#52c41a' }}
                    prefix={<SecurityScanOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} lg={6}>
                <Card>
                  <Statistic
                    title="威胁拦截"
                    value={156}
                    valueStyle={{ color: '#1890ff' }}
                    prefix={<SafetyOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} lg={6}>
                <Card>
                  <Statistic
                    title="系统状态"
                    value="正常"
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} lg={6}>
                <Card>
                  <Statistic
                    title="在线时间"
                    value="99.9%"
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
              <Col span={24}>
                <Card title="快速操作" style={{ borderRadius: 8 }}>
                  <Space wrap>
                    <Button type="primary" icon={<SecurityScanOutlined />}>
                      开始安全扫描
                    </Button>
                    {isAdmin() && (
                      <Button icon={<SettingOutlined />} onClick={() => setSelectedMenu('user_management')}>
                        用户管理
                      </Button>
                    )}
                    <Button icon={<UserOutlined />} onClick={() => setSelectedMenu('profile')}>
                      个人信息
                    </Button>
                    <Button icon={<BellOutlined />}>
                      查看通知
                    </Button>
                  </Space>
                </Card>
              </Col>
            </Row>
          </>
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