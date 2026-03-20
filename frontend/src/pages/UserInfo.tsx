import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Typography, 
  Space, 
  Button, 
  Row, 
  Col, 
  Tag, 
  Avatar, 
  Descriptions,
  Divider,
  Statistic,
  message
} from 'antd';
import { 
  UserOutlined, 
  GithubOutlined, 
  MailOutlined, 
  GlobalOutlined,
  CalendarOutlined,
  TeamOutlined,
  SafetyOutlined,
  ReloadOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface UserInfo {
  id: number;
  name: string;
  login: string;
  email: string;
  avatarUrl: string;
  githubId: string;
  status: number;
  createdAt: string;
  updatedAt: string;
  lastLoginAt: string;
  roles: Role[];
  permissions: Permission[];
  menus: Menu[];
}

interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
}

interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  description: string;
}

interface Menu {
  id: number;
  menuName: string;
  menuCode: string;
  path: string;
  icon: string;
  children?: Menu[];
}

const UserInfo: React.FC = () => {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchUserInfo = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/user/info', {
        credentials: 'include'
      });
      
      if (response.ok) {
        const result = await response.json();
        if (result.success) {
          setUserInfo(result.data);
        } else {
          message.error(result.message || '获取用户信息失败');
        }
      } else {
        message.error('获取用户信息失败');
      }
    } catch (error) {
      console.error('获取用户信息失败:', error);
      message.error('网络错误，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUserInfo();
  }, []);

  const getRoleColor = (roleCode: string) => {
    switch (roleCode) {
      case 'SUPER_ADMIN':
        return 'red';
      case 'ADMIN':
        return 'blue';
      case 'USER':
        return 'green';
      default:
        return 'default';
    }
  };

  const formatDate = (dateString: string) => {
    return dateString ? new Date(dateString).toLocaleString('zh-CN') : '-';
  };

  if (!userInfo) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        background: '#f5f5f5'
      }}>
        <SafetyOutlined spin style={{ fontSize: 48, color: '#1890ff' }} />
      </div>
    );
  }

  return (
    <div style={{ padding: 24, background: '#f5f5f5', minHeight: '100vh' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <Title level={2}>
            <UserOutlined /> 个人信息
          </Title>
          <Button 
            type="primary" 
            icon={<ReloadOutlined />}
            onClick={fetchUserInfo} 
            loading={loading}
          >
            刷新数据
          </Button>
        </div>

        <Row gutter={[16, 16]}>
          {/* 基本信息卡片 */}
          <Col span={24}>
            <Card>
              <Row align="middle" gutter={24}>
                <Col>
                  <Avatar 
                    src={userInfo.avatarUrl} 
                    size={80}
                    icon={<UserOutlined />}
                  />
                </Col>
                <Col flex={1}>
                  <Title level={3} style={{ margin: 0 }}>
                    {userInfo.name || userInfo.login}
                  </Title>
                  <Space direction="vertical" size="small">
                    <Text type="secondary">
                      <GithubOutlined /> @{userInfo.login}
                    </Text>
                    {userInfo.email && (
                      <Text type="secondary">
                        <MailOutlined /> {userInfo.email}
                      </Text>
                    )}
                    <Space wrap>
                      {userInfo.roles?.map(role => (
                        <Tag 
                          key={role.id}
                          color={getRoleColor(role.roleCode)}
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
                      value={userInfo.roles?.length || 0}
                      prefix={<TeamOutlined />}
                    />
                    <Statistic
                      title="权限数量"
                      value={userInfo.permissions?.length || 0}
                      prefix={<SafetyOutlined />}
                    />
                  </Space>
                </Col>
              </Row>
            </Card>
          </Col>

          {/* 详细信息 */}
          <Col span={12}>
            <Card title="账户信息" style={{ height: '100%' }}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="用户ID">
                  {userInfo.id}
                </Descriptions.Item>
                <Descriptions.Item label="GitHub ID">
                  {userInfo.githubId}
                </Descriptions.Item>
                <Descriptions.Item label="用户名">
                  {userInfo.login}
                </Descriptions.Item>
                <Descriptions.Item label="显示名称">
                  {userInfo.name || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="邮箱">
                  {userInfo.email || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="账户状态">
                  <Tag color={userInfo.status === 1 ? 'green' : 'red'}>
                    {userInfo.status === 1 ? '正常' : '禁用'}
                  </Tag>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>

          <Col span={12}>
            <Card title="时间信息" style={{ height: '100%' }}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="注册时间">
                  <Space>
                    <CalendarOutlined />
                    {formatDate(userInfo.createdAt)}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="最后更新">
                  <Space>
                    <CalendarOutlined />
                    {formatDate(userInfo.updatedAt)}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="最后登录">
                  <Space>
                    <CalendarOutlined />
                    {formatDate(userInfo.lastLoginAt)}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>

          {/* 角色权限 */}
          <Col span={12}>
            <Card title="我的角色" style={{ height: '100%' }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                {userInfo.roles?.map(role => (
                  <Card key={role.id} size="small" style={{ marginBottom: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <Text strong>{role.roleName}</Text>
                        <br />
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {role.description}
                        </Text>
                      </div>
                      <Tag color={getRoleColor(role.roleCode)}>
                        {role.roleCode}
                      </Tag>
                    </div>
                  </Card>
                )) || <Text type="secondary">暂无角色</Text>}
              </Space>
            </Card>
          </Col>

          <Col span={12}>
            <Card title="我的权限" style={{ height: '100%', maxHeight: 400, overflow: 'auto' }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                {userInfo.permissions?.map(permission => (
                  <div key={permission.id} style={{ padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                    <Text strong>{permission.permissionName}</Text>
                    <br />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {permission.permissionCode}
                    </Text>
                    {permission.description && (
                      <>
                        <br />
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          {permission.description}
                        </Text>
                      </>
                    )}
                  </div>
                )) || <Text type="secondary">暂无权限</Text>}
              </Space>
            </Card>
          </Col>

          {/* 可访问菜单 */}
          <Col span={24}>
            <Card title="可访问菜单">
              <Row gutter={[16, 16]}>
                {userInfo.menus?.map(menu => (
                  <Col key={menu.id} xs={24} sm={12} md={8} lg={6}>
                    <Card size="small" hoverable>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 24, marginBottom: 8 }}>
                          {menu.icon ? (
                            <span className={`anticon anticon-${menu.icon}`} />
                          ) : (
                            <UserOutlined />
                          )}
                        </div>
                        <Text strong>{menu.menuName}</Text>
                        <br />
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {menu.path}
                        </Text>
                      </div>
                    </Card>
                  </Col>
                )) || <Text type="secondary">暂无可访问菜单</Text>}
              </Row>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default UserInfo;