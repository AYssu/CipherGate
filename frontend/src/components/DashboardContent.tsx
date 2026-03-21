import React from 'react';
import { Card, Row, Col, Avatar, Typography, Space, Statistic, Button } from 'antd';
import { 
  UserOutlined, 
  GithubOutlined, 
  SecurityScanOutlined, 
  SafetyOutlined, 
  SettingOutlined, 
  BellOutlined 
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface UserInfo {
  id: number;
  githubId: string;
  login: string;
  name?: string;
  email?: string;
  avatarUrl?: string;
  status: number;
  roles?: Array<{
    id: number;
    roleName: string;
    roleCode: string;
    description: string;
  }>;
}

interface DashboardContentProps {
  userInfo?: UserInfo;
  isAdmin: () => boolean;
  setSelectedMenu: (menu: string) => void;
}

const DashboardContent: React.FC<DashboardContentProps> = ({ 
  userInfo, 
  isAdmin, 
  setSelectedMenu 
}) => {
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
};

export default DashboardContent;