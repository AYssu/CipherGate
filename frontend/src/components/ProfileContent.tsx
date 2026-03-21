import React from 'react';
import { Card, Row, Col, Avatar, Typography, Space, Tag, Statistic } from 'antd';
import { UserOutlined, GithubOutlined, MailOutlined, TeamOutlined } from '@ant-design/icons';
import type { User } from '../services/userService';

const { Title, Text } = Typography;

interface ProfileContentProps {
  userInfo?: User | null;
}

const ProfileContent: React.FC<ProfileContentProps> = ({ userInfo }) => {
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
};

export default ProfileContent;