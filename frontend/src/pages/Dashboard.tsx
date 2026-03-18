import React, { useState, useEffect } from 'react';
import { Layout, Menu, Button, Card, Row, Col, Typography, Space, Avatar, Dropdown, Statistic } from 'antd';
import {
  SafetyOutlined,
  UserOutlined,
  LogoutOutlined,
  DashboardOutlined,
  SecurityScanOutlined,
  SettingOutlined,
  BellOutlined,
  GithubOutlined
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

interface UserInfo {
  name: string;
  login: string;
  avatar_url: string;
  email?: string;
}

const Dashboard: React.FC = () => {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 获取用户信息
    fetch('http://localhost:8080/user', {
      credentials: 'include'
    })
    .then(response => response.json())
    .then(data => {
      setUserInfo(data);
      setLoading(false);
    })
    .catch(error => {
      console.error('获取用户信息失败:', error);
      setLoading(false);
    });
  }, []);

  const handleLogout = () => {
    fetch('http://localhost:8080/logout', {
      method: 'POST',
      credentials: 'include'
    })
    .then(() => {
      window.location.href = '/';
    })
    .catch(error => {
      console.error('退出登录失败:', error);
    });
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  const sidebarMenuItems = [
    {
      key: 'dashboard',
      icon: <DashboardOutlined />,
      label: '仪表板',
    },
    {
      key: 'userinfo',
      icon: <UserOutlined />,
      label: '用户信息',
      onClick: () => window.location.href = '/userinfo',
    },
    {
      key: 'security',
      icon: <SecurityScanOutlined />,
      label: '安全监控',
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    },
  ];

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
          defaultSelectedKeys={['dashboard']}
          items={sidebarMenuItems}
          style={{ border: 'none', padding: '16px 0' }}
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
            仪表板
          </Title>
          
          <Space size="middle">
            <Button type="text" icon={<BellOutlined />} />
            
            <Dropdown
              menu={{ items: userMenuItems }}
              placement="bottomRight"
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar 
                  src={userInfo?.avatar_url} 
                  icon={<UserOutlined />}
                  size="small"
                />
                <Text strong>{userInfo?.name || userInfo?.login}</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ padding: '24px', background: '#f5f5f5' }}>
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col span={24}>
              <Card style={{ borderRadius: 8 }}>
                <Row align="middle" gutter={16}>
                  <Col>
                    <Avatar 
                      src={userInfo?.avatar_url} 
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
                  <Button icon={<SettingOutlined />}>
                    系统配置
                  </Button>
                  <Button icon={<BellOutlined />}>
                    查看告警
                  </Button>
                </Space>
              </Card>
            </Col>
          </Row>
        </Content>
      </Layout>
    </Layout>
  );
};

export default Dashboard;