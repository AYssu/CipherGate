import React, { useState } from 'react';
import { Card, Row, Col, Avatar, Typography, Space, Tag, Statistic, Descriptions, Button, Modal, Form, Input, message, Grid } from 'antd';
import { UserOutlined, GithubOutlined, MailOutlined, TeamOutlined, IdcardOutlined, SafetyOutlined, KeyOutlined } from '@ant-design/icons';
import { systemApi } from '../services';
import type { User } from '../services';

const { Title, Text } = Typography;

interface ProfileContentProps {
  userInfo?: User | null;
}

const ProfileContent: React.FC<ProfileContentProps> = ({ userInfo }) => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [passwordForm] = Form.useForm();
  const [passwordLoading, setPasswordLoading] = useState(false);

  const handleSetPassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      setPasswordLoading(true);
      const response: any = await systemApi.setPassword({
        newPassword: values.newPassword,
      });
      if (response?.success) {
        message.success('密码设置成功');
        setPasswordModalVisible(false);
        passwordForm.resetFields();
      } else {
        message.error(response?.message || '密码设置失败');
      }
    } catch (error: any) {
      if (error.errorFields) {
        message.error('请填写所有必填项');
      }
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div style={{ padding: 0 }}>
      {/* 用户概览卡片 */}
      <Card style={{ marginBottom: isMobile ? 16 : 24 }}>
        <Row align="middle" gutter={isMobile ? [0, 16] : 32} style={isMobile ? { flexDirection: 'column', textAlign: 'center' } : undefined}>
          <Col>
            <div style={{ display: 'flex', justifyContent: isMobile ? 'center' : undefined }}>
              <Avatar
                src={userInfo?.avatarUrl}
                size={isMobile ? 64 : 88}
                icon={<UserOutlined />}
                style={{ border: '3px solid #f0f0f0' }}
              />
            </div>
          </Col>
          <Col flex={1}>
            <div style={{ marginBottom: isMobile ? 8 : 16 }}>
              <Title level={isMobile ? 4 : 2} style={{ margin: '0 0 8px 0', color: '#1a1a2e' }}>
                {userInfo?.name || userInfo?.login}
              </Title>
              <Space size={isMobile ? 8 : 16} wrap style={isMobile ? { justifyContent: 'center' } : undefined}>
                <Text type="secondary" style={{ fontSize: isMobile ? 13 : 16 }}>
                  <GithubOutlined /> @{userInfo?.login}
                </Text>
                {userInfo?.email && (
                  <Text type="secondary" style={{ fontSize: isMobile ? 13 : 16 }}>
                    <MailOutlined /> {userInfo?.email}
                  </Text>
                )}
              </Space>
            </div>
            <Space wrap size={8} style={isMobile ? { justifyContent: 'center' } : undefined}>
              {userInfo?.roles?.map(role => (
                <Tag
                  key={role.id}
                  color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                  style={{
                    padding: isMobile ? '2px 8px' : '4px 12px',
                    fontSize: isMobile ? 12 : 13,
                    borderRadius: '16px',
                    border: 'none'
                  }}
                >
                  {role.roleName}
                </Tag>
              ))}
              <Button
                type="link"
                icon={<KeyOutlined />}
                onClick={() => setPasswordModalVisible(true)}
                style={{ padding: '4px 8px', fontSize: isMobile ? 12 : 13 }}
              >
                设置密码
              </Button>
            </Space>
          </Col>
          {!isMobile && (
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
          )}
        </Row>
      </Card>

      <Row gutter={isMobile ? [0, 16] : 24}>
        {/* 账户详细信息 */}
        <Col span={isMobile ? 24 : 14}>
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
              column={isMobile ? 1 : 2}
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
        <Col span={isMobile ? 24 : 10}>
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

      {/* 设置密码弹窗 */}
      <Modal
        title={
          <Space>
            <KeyOutlined style={{ color: '#1890ff' }} />
            <span>设置密码</span>
          </Space>
        }
        open={passwordModalVisible}
        onCancel={() => {
          setPasswordModalVisible(false);
          passwordForm.resetFields();
        }}
        onOk={handleSetPassword}
        confirmLoading={passwordLoading}
        okText="确认设置"
        cancelText="取消"
        width={isMobile ? '100%' : 400}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <div style={{ padding: '8px 0' }}>
          <div style={{
            padding: '12px 16px',
            background: '#fff7e6',
            border: '1px solid #ffd591',
            borderRadius: 6,
            marginBottom: 16,
            fontSize: 13,
            color: '#ad6800'
          }}>
            设置密码后，网络不佳时可使用 GitHub 账号名 + 密码登录
          </div>
          <Form form={passwordForm} layout="vertical">
            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 6, message: '密码至少 6 个字符' }
              ]}
            >
              <Input.Password
                placeholder="请输入新密码（至少 6 个字符）"
                size="large"
                autoComplete="new-password"
              />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次密码输入不一致'));
                  },
                }),
              ]}
            >
              <Input.Password
                placeholder="请再次输入密码"
                size="large"
                autoComplete="new-password"
              />
            </Form.Item>
          </Form>
        </div>
      </Modal>
    </div>
  );
};

export default ProfileContent;