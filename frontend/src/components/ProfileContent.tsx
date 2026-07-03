import React, { useState } from 'react';
import { Card, Row, Col, Avatar, Typography, Space, Tag, Statistic, Descriptions, Button, Modal, Form, Input, message, Grid } from 'antd';
import { UserOutlined, GithubOutlined, MailOutlined, TeamOutlined, IdcardOutlined, SafetyOutlined, KeyOutlined } from '@ant-design/icons';
import { systemApi } from '../services';
import type { User } from '../services';
import M5BottomSheet from './M5BottomSheet';

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
      <Card style={{ marginBottom: isMobile ? 12 : 24 }}>
        <Row align="middle" gutter={isMobile ? [12, 0] : 32}>
          <Col xs={8} sm="auto">
            <Avatar
              src={userInfo?.avatarUrl}
              size={isMobile ? 56 : 88}
              icon={<UserOutlined />}
              style={{ border: '3px solid #f0f0f0' }}
            />
          </Col>
          <Col xs={16} sm={12} flex={1}>
            <Title level={isMobile ? 5 : 2} style={{ margin: '0 0 4px 0', color: '#1a1a2e', fontSize: isMobile ? 16 : undefined }}>
              {userInfo?.name || userInfo?.login}
            </Title>
            <Space size={isMobile ? 6 : 16} wrap>
              <Text type="secondary" style={{ fontSize: isMobile ? 12 : 16 }}>
                <GithubOutlined /> @{userInfo?.login}
              </Text>
              {userInfo?.email && (
                <Text type="secondary" style={{ fontSize: isMobile ? 12 : 16 }}>
                  <MailOutlined /> {userInfo?.email}
                </Text>
              )}
            </Space>
            <div style={{ marginTop: isMobile ? 6 : 12 }}>
              <Space wrap size={isMobile ? 4 : 8}>
                {userInfo?.roles?.map(role => (
                  <Tag
                    key={role.id}
                    color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                    style={{
                      padding: isMobile ? '1px 6px' : '4px 12px',
                      fontSize: isMobile ? 10 : 13,
                      borderRadius: '16px',
                      border: 'none',
                      lineHeight: isMobile ? '18px' : undefined,
                    }}
                  >
                    {role.roleName}
                  </Tag>
                ))}
                <Button
                  type="link"
                  icon={<KeyOutlined />}
                  onClick={() => setPasswordModalVisible(true)}
                  style={{ padding: isMobile ? '0 4px' : '4px 8px', fontSize: isMobile ? 11 : 13, height: 'auto' }}
                >
                  {isMobile ? '密码' : '设置密码'}
                </Button>
              </Space>
            </div>
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
        {/* 移动端：统计信息行 */}
        {isMobile && (
          <div style={{
            display: 'flex',
            gap: 0,
            marginTop: 12,
            borderTop: '1px solid #f0f0f0',
            paddingTop: 12,
          }}>
            <div style={{ flex: 1, textAlign: 'center' }}>
              <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>角色</div>
              <div style={{ fontSize: 18, fontWeight: 600, color: '#1890ff' }}>{userInfo?.roles?.length || 0}</div>
            </div>
            <div style={{ width: 1, background: '#f0f0f0' }} />
            <div style={{ flex: 1, textAlign: 'center' }}>
              <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>状态</div>
              <div style={{ fontSize: 14, fontWeight: 500, color: userInfo?.status === 1 ? '#52c41a' : '#ff4d4f' }}>
                {userInfo?.status === 1 ? '正常' : '禁用'}
              </div>
            </div>
          </div>
        )}
      </Card>

      <Row gutter={isMobile ? [0, 12] : 24}>
        {/* 账户详细信息 */}
        <Col span={isMobile ? 24 : 14}>
          <Card
            title={
              <Space size={8}>
                <IdcardOutlined style={{ color: '#1890ff' }} />
                <span style={{ fontSize: isMobile ? 14 : 16 }}>账户详细信息</span>
              </Space>
            }
            style={{ height: '100%' }}
            className={isMobile ? 'profile-detail-card' : ''}
          >
            <Descriptions
              column={isMobile ? 1 : 2}
              size={isMobile ? 'small' : 'middle'}
              styles={{
                label: {
                  fontWeight: 600,
                  color: '#666',
                  width: isMobile ? 80 : 120,
                  fontSize: isMobile ? 12 : 14,
                },
                content: {
                  color: '#333',
                  fontWeight: 500,
                  fontSize: isMobile ? 12 : 14,
                },
              }}
            >
              <Descriptions.Item label="用户ID">{userInfo?.id}</Descriptions.Item>
              <Descriptions.Item label="GitHub ID">{userInfo?.githubId}</Descriptions.Item>
              <Descriptions.Item label="用户名">{userInfo?.login}</Descriptions.Item>
              <Descriptions.Item label="显示名称">{userInfo?.name || '-'}</Descriptions.Item>
              <Descriptions.Item label="邮箱" span={isMobile ? 1 : 2}>
                {userInfo?.email || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间" span={isMobile ? 1 : 2}>
                {userInfo?.createdAt ? new Date(userInfo.createdAt).toLocaleString() : '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 角色与权限 */}
        <Col span={isMobile ? 24 : 10}>
          <Card
            title={
              <Space size={8}>
                <TeamOutlined style={{ color: '#1890ff' }} />
                <span style={{ fontSize: isMobile ? 14 : 16 }}>角色与权限</span>
              </Space>
            }
            style={{ height: '100%' }}
            className={isMobile ? 'profile-role-card' : ''}
          >
            <Space direction="vertical" style={{ width: '100%' }} size={isMobile ? 8 : 16}>
              {userInfo?.roles?.map((role) => (
                <div
                  key={role.id}
                  className="profile-role-item"
                  style={{
                    padding: isMobile ? '10px 12px' : '16px',
                    background: '#fafafa',
                    borderRadius: '8px',
                    border: '1px solid #f0f0f0',
                  }}
                >
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: isMobile ? 4 : 8
                  }}>
                    <Text strong style={{ fontSize: isMobile ? 13 : 15, color: '#1a1a2e' }}>
                      {role.roleName}
                    </Text>
                    <Tag
                      color={role.roleCode === 'SUPER_ADMIN' ? 'red' :
                             role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                      style={{
                        padding: '1px 6px',
                        fontSize: '10px',
                        borderRadius: '10px',
                        fontWeight: 500,
                        margin: 0,
                      }}
                    >
                      {role.roleCode}
                    </Tag>
                  </div>
                  <Text
                    type="secondary"
                    style={{
                      fontSize: isMobile ? 11 : 13,
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
                  padding: isMobile ? '24px' : '32px',
                  color: '#999'
                }}>
                  <TeamOutlined style={{ fontSize: isMobile ? 24 : 32, marginBottom: 8 }} />
                  <div style={{ fontSize: isMobile ? 12 : 14 }}>暂无分配角色</div>
                </div>
              )}
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 设置密码弹窗 */}
      {isMobile ? (
        <M5BottomSheet
          open={passwordModalVisible}
          onClose={() => { setPasswordModalVisible(false); passwordForm.resetFields(); }}
          title="设置密码"
          footer={
            <>
              <Button onClick={() => { setPasswordModalVisible(false); passwordForm.resetFields(); }} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" loading={passwordLoading} onClick={handleSetPassword} style={{ flex: 1, height: 44, borderRadius: 10 }}>确认设置</Button>
            </>
          }
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
        </M5BottomSheet>
      ) : (
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
          width={400}
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
      )}
    </div>
  );
};

export default ProfileContent;
