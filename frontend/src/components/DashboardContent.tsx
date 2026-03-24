import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Row, 
  Col, 
  Avatar, 
  Typography, 
  Space, 
  Statistic, 
  Button, 
  List, 
  Badge, 
  Divider,
  Alert,
  Timeline,
  Tag
} from 'antd';
import { 
  UserOutlined, 
  GithubOutlined, 
  SecurityScanOutlined, 
  SafetyOutlined, 
  SettingOutlined, 
  BellOutlined,
  TeamOutlined,
  LockOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  RiseOutlined,
  EyeOutlined,
  BarChartOutlined
} from '@ant-design/icons';
import type { User } from '../services/userService';

const { Title, Text } = Typography;

interface DashboardContentProps {
  userInfo?: User | null;
  isAdmin: () => boolean;
  setSelectedMenu: (menu: string) => void;
}

const DashboardContent: React.FC<DashboardContentProps> = ({ 
  userInfo, 
  isAdmin, 
  setSelectedMenu 
}) => {
  const [currentTime, setCurrentTime] = useState(new Date());
  const isMobile = window.innerWidth < 768;

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // 模拟数据
  const securityStats = {
    totalThreats: 156,
    blockedToday: 23,
    systemHealth: 99.9,
    activeUsers: 12
  };

  const recentActivities = [
    { type: 'login', user: '管理员', time: '2分钟前', status: 'success' },
    { type: 'permission', user: '用户001', time: '15分钟前', status: 'info' },
    { type: 'security', user: '系统', time: '1小时前', status: 'warning' },
    { type: 'config', user: '管理员', time: '2小时前', status: 'success' }
  ];

  const quickActions = [
    { 
      title: '用户管理', 
      icon: <TeamOutlined />, 
      description: '管理系统用户和权限',
      action: () => setSelectedMenu('user_management'),
      adminOnly: true
    },
    { 
      title: '角色管理', 
      icon: <LockOutlined />, 
      description: '配置用户角色和权限',
      action: () => setSelectedMenu('role_management'),
      adminOnly: true
    },
    { 
      title: '个人信息', 
      icon: <UserOutlined />, 
      description: '查看和编辑个人资料',
      action: () => setSelectedMenu('profile'),
      adminOnly: false
    },
    { 
      title: '系统配置', 
      icon: <SettingOutlined />, 
      description: '系统参数和安全设置',
      action: () => setSelectedMenu('system_config'),
      adminOnly: true
    }
  ];

  const getGreeting = () => {
    const hour = currentTime.getHours();
    if (hour < 12) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  };

  return (
    <div style={{ padding: 0 }}>
      {/* 简洁的欢迎区域 */}
      <Card style={{ marginBottom: 24 }}>
        <Row align="middle" gutter={24}>
          <Col>
            <Avatar
              src={userInfo?.avatarUrl}
              size={isMobile ? 64 : 72}
              icon={<UserOutlined />}
            />
          </Col>
          <Col flex={1}>
            <Title level={isMobile ? 4 : 3} style={{ margin: '0 0 8px 0', color: '#1a1a2e' }}>
              {getGreeting()}, {userInfo?.name || userInfo?.login}
            </Title>
            <Space direction={isMobile ? 'vertical' : 'horizontal'} size={16}>
              <Text type="secondary" style={{ fontSize: 14 }}>
                <GithubOutlined /> @{userInfo?.login}
              </Text>
              <Text type="secondary" style={{ fontSize: 14 }}>
                <ClockCircleOutlined /> {currentTime.toLocaleString()}
              </Text>
            </Space>
            <div style={{ marginTop: 8 }}>
              <Space wrap>
                {userInfo?.roles?.map(role => (
                  <Tag
                    key={role.id}
                    color={role.roleCode === 'SUPER_ADMIN' ? 'red' : 
                           role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                  >
                    {role.roleName}
                  </Tag>
                ))}
              </Space>
            </div>
          </Col>
          {!isMobile && (
            <Col>
              <div style={{ textAlign: 'right' }}>
                <Text type="secondary" style={{ fontSize: 12 }}>系统状态</Text>
                <div style={{ fontSize: 16, fontWeight: 500, color: '#52c41a' }}>
                  <CheckCircleOutlined /> 正常运行
                </div>
              </div>
            </Col>
          )}
        </Row>
      </Card>

      {/* 统计数据 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={12} lg={6}>
          <Card>
            <Statistic
              title="安全事件"
              value={securityStats.totalThreats}
              valueStyle={{ color: '#52c41a' }}
              prefix={<SecurityScanOutlined />}
              suffix="个"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card>
            <Statistic
              title="今日拦截"
              value={securityStats.blockedToday}
              valueStyle={{ color: '#1890ff' }}
              prefix={<SafetyOutlined />}
              suffix="次"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card>
            <Statistic
              title="系统健康度"
              value={securityStats.systemHealth}
              precision={1}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />}
              suffix="%"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card>
            <Statistic
              title="在线用户"
              value={securityStats.activeUsers}
              valueStyle={{ color: '#722ed1' }}
              prefix={<UserOutlined />}
              suffix="人"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* 快速操作 */}
        <Col xs={24} lg={12}>
          <Card 
            title={
              <Space>
                <BarChartOutlined style={{ color: '#1890ff' }} />
                <span>快速操作</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <Row gutter={[12, 12]}>
              {quickActions
                .filter(action => !action.adminOnly || isAdmin())
                .map((action, index) => (
                <Col xs={12} key={index}>
                  <Card 
                    size="small"
                    hoverable
                    onClick={action.action}
                    style={{ 
                      textAlign: 'center',
                      cursor: 'pointer',
                      transition: 'all 0.3s ease'
                    }}
                    bodyStyle={{ padding: '16px 8px' }}
                  >
                    <div style={{ fontSize: 24, color: '#1890ff', marginBottom: 8 }}>
                      {action.icon}
                    </div>
                    <div style={{ fontWeight: 500, marginBottom: 4 }}>
                      {action.title}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {action.description}
                    </Text>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>

        {/* 最近活动 */}
        <Col xs={24} lg={12}>
          <Card 
            title={
              <Space>
                <EyeOutlined style={{ color: '#1890ff' }} />
                <span>最近活动</span>
              </Space>
            }
            extra={<Button type="link" size="small">查看全部</Button>}
            style={{ height: '100%' }}
          >
            <List
              size="small"
              dataSource={recentActivities}
              renderItem={(activity) => (
                <List.Item style={{ padding: '12px 0', borderBottom: '1px solid #f0f0f0' }}>
                  <List.Item.Meta
                    avatar={
                      <div style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        backgroundColor: 
                          activity.status === 'success' ? '#52c41a' :
                          activity.status === 'warning' ? '#faad14' : '#1890ff',
                        marginTop: 6
                      }} />
                    }
                    title={
                      <div style={{ fontSize: 13, lineHeight: 1.4 }}>
                        <Text strong style={{ color: '#1a1a2e' }}>{activity.user}</Text>
                        <Text type="secondary"> 执行了 </Text>
                        <Text strong>
                          {activity.type === 'login' ? '登录操作' : 
                           activity.type === 'permission' ? '权限变更' :
                           activity.type === 'security' ? '安全检查' : '配置更新'}
                        </Text>
                      </div>
                    }
                    description={
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {activity.time}
                        </Text>
                        <Tag 
                          color={
                            activity.status === 'success' ? 'green' :
                            activity.status === 'warning' ? 'orange' : 'blue'
                          }
                          size="small"
                          style={{ fontSize: 10, margin: 0 }}
                        >
                          {activity.status === 'success' ? '成功' :
                           activity.status === 'warning' ? '警告' : '信息'}
                        </Tag>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default DashboardContent;