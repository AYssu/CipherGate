import React from 'react';
import { Card, Row, Col, Avatar, Typography, Space, Tag, Statistic, Descriptions } from 'antd';
import { UserOutlined, GithubOutlined, MailOutlined, TeamOutlined, IdcardOutlined, SafetyOutlined } from '@ant-design/icons';
import type { User } from '../services';

const { Title, Text } = Typography;

interface ProfileContentProps {
  userInfo?: User | null;
}

const ProfileContent: React.FC<ProfileContentProps> = ({ userInfo }) => {
  return (
    <div style={{ padding: 0 }}>
      {/* 用户概览卡片 */}
      <Card style={{ marginBottom: 24 }}>
        <Row align="middle" gutter={32}>
          <Col>
            <Avatar
              src={userInfo?.avatarUrl}
              size={88}
              icon={<UserOutlined />}
              style={{ border: '3px solid #f0f0f0' }}
            />
          </Col>
          <Col flex={1}>
            <div style={{ marginBottom: 16 }}>
              <Title level={2} style={{ margin: '0 0 8px 0', color: '#1a1a2e' }}>
                {userInfo?.name || userInfo?.login}
              </Title>
              <Space size={16}>
                <Text type="secondary" style={{ fontSize: 16 }}>
                  <GithubOutlined /> @{userInfo?.login}
                </Text>
                {userInfo?.email && (
                  <Text type="secondary" style={{ fontSize: 16 }}>
                    <MailOutlined /> {userInfo?.email}
                  </Text>
                )}
              </Space>
            </div>
            <Space wrap size={8}>
              {userInfo?.roles?.map(role => (
                <Tag
                  key={role.id}
                  color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                  style={{ 
                    padding: '4px 12px',
                    fontSize: '13px',
                    borderRadius: '16px',
                    border: 'none'
                  }}
                >
                  {role.roleName}
                </Tag>
              ))}
            </Space>
          </Col>
          <Col>
            <Row gutter={32}>
              <Col>
                <Statistic
                  title="角色权限"
                  value={userInfo?.roles?.length || 0}
                  prefix={<TeamOutlined />}
                  valueStyle={{ color: '#1890ff', fontSize: 28 }}
                />
              </Col>
              <Col>
                <Statistic
                  title="账户状态"
                  value={userInfo?.status === 1 ? '正常' : '禁用'}
                  prefix={<SafetyOutlined />}
                  valueStyle={{ 
                    color: userInfo?.status === 1 ? '#52c41a' : '#ff4d4f',
                    fontSize: 20
                  }}
                />
              </Col>
            </Row>
          </Col>
        </Row>
      </Card>

      <Row gutter={24}>
        {/* 账户详细信息 */}
        <Col span={14}>
          <Card 
            title={
              <Space>
                <IdcardOutlined style={{ color: '#1890ff' }} />
                <span>账户详细信息</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <Descriptions
              column={2}
              size="middle"
              styles={{
                label: {
                  fontWeight: 600,
                  color: '#666',
                  width: 120,
                },
                content: {
                  color: '#333',
                  fontWeight: 500,
                },
              }}
            >
              <Descriptions.Item label="用户ID">{userInfo?.id}</Descriptions.Item>
              <Descriptions.Item label="GitHub ID">{userInfo?.githubId}</Descriptions.Item>
              <Descriptions.Item label="用户名">{userInfo?.login}</Descriptions.Item>
              <Descriptions.Item label="显示名称">{userInfo?.name || '-'}</Descriptions.Item>
              <Descriptions.Item label="邮箱地址" span={2}>
                {userInfo?.email || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {userInfo?.createdAt ? new Date(userInfo.createdAt).toLocaleString() : '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 角色与权限 */}
        <Col span={10}>
          <Card 
            title={
              <Space>
                <TeamOutlined style={{ color: '#1890ff' }} />
                <span>角色与权限</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              {userInfo?.roles?.map((role) => (
                <div 
                  key={role.id} 
                  style={{ 
                    padding: '16px',
                    background: '#fafafa',
                    borderRadius: '8px',
                    border: '1px solid #f0f0f0',
                    transition: 'all 0.3s ease'
                  }}
                >
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center',
                    marginBottom: 8
                  }}>
                    <Text strong style={{ fontSize: 15, color: '#1a1a2e' }}>
                      {role.roleName}
                    </Text>
                    <Tag 
                      color={role.roleCode === 'SUPER_ADMIN' ? 'red' : 
                             role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                      style={{ 
                        padding: '2px 8px',
                        fontSize: '11px',
                        borderRadius: '12px',
                        fontWeight: 500
                      }}
                    >
                      {role.roleCode}
                    </Tag>
                  </div>
                  <Text 
                    type="secondary" 
                    style={{ 
                      fontSize: 13, 
                      display: 'block',
                      lineHeight: 1.4
                    }}
                  >
                    {role.description || '暂无描述'}
                  </Text>
                </div>
              )) || (
                <div style={{ 
                  textAlign: 'center', 
                  padding: '32px',
                  color: '#999'
                }}>
                  <TeamOutlined style={{ fontSize: 32, marginBottom: 8 }} />
                  <div>暂无分配角色</div>
                </div>
              )}
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default ProfileContent;