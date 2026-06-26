import React, { useEffect, useState } from 'react';
import { Card, Typography, Form, Input, Button, message, Space, Row, Col, Tag } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, SendOutlined, SafetyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { portalSettingsApi } from '../services/portalSettingsService';
import { portalAuthApi } from '../services/portalAuthService';

const { Title, Text } = Typography;

const PortalSettingsPage: React.FC = () => {
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [emailForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [codeSending, setCodeSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [profile, setProfile] = useState<any>(null);

  useEffect(() => {
    loadProfile();
  }, []);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const loadProfile = async () => {
    try {
      const res: any = await portalSettingsApi.getProfile();
      if (res?.data) {
        setProfile(res.data);
        profileForm.setFieldsValue({ nickname: res.data.nickname });
        localStorage.setItem('portal_email', res.data.email);
      }
    } catch {
      // ignore
    }
  };

  const handleUpdateNickname = async (values: any) => {
    setLoading(true);
    try {
      await portalSettingsApi.updateNickname(values.nickname);
      message.success('昵称更新成功');
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: any) => {
    setLoading(true);
    try {
      await portalSettingsApi.changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('密码修改成功');
      passwordForm.resetFields();
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const handleSendCode = async () => {
    const email = emailForm.getFieldValue('newEmail');
    if (!email) {
      message.warning('请先输入新邮箱');
      return;
    }
    setCodeSending(true);
    try {
      await portalAuthApi.sendEmailVerifyCode(email);
      message.success('验证码已发送到新邮箱');
      setCountdown(60);
    } catch {
      // ignore
    } finally {
      setCodeSending(false);
    }
  };

  const handleChangeEmail = async (values: any) => {
    setLoading(true);
    try {
      await portalSettingsApi.changeEmail({
        currentPassword: values.currentPassword,
        newEmail: values.newEmail,
        verifyCode: values.verifyCode,
      });
      message.success('邮箱更换成功，请使用新邮箱登录');
      emailForm.resetFields();
      loadProfile();
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>账号设置</Title>

      {/* 当前账号信息 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <SafetyOutlined style={{ fontSize: 24, color: '#1890ff' }} />
          <div>
            <Text strong style={{ fontSize: 15 }}>当前登录邮箱</Text>
            <div style={{ marginTop: 4 }}>
              <Tag icon={<CheckCircleOutlined />} color="success" style={{ fontSize: 14, padding: '4px 12px' }}>
                {profile?.email || '-'}
              </Tag>
            </div>
          </div>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title={<><UserOutlined /> 昵称设置</>} style={{ height: '100%' }}>
            <Form form={profileForm} layout="vertical" onFinish={handleUpdateNickname}>
              <Form.Item name="nickname" rules={[{ required: true, message: '请输入昵称' }]}>
                <Input placeholder="请输入昵称" size="large" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" loading={loading} size="large" block>
                  保存昵称
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={<><LockOutlined /> 修改密码</>} style={{ height: '100%' }}>
            <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
              <Form.Item name="oldPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
                <Input.Password placeholder="当前密码" size="large" />
              </Form.Item>
              <Form.Item name="newPassword" rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少6位' }]}>
                <Input.Password placeholder="新密码" size="large" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: '请确认新密码' },
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
                <Input.Password placeholder="确认新密码" size="large" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" loading={loading} size="large" block>
                  修改密码
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>
      </Row>

      <Card title={<><MailOutlined /> 更换邮箱</>} style={{ marginTop: 16 }}>
        <div style={{ background: '#fff7e6', border: '1px solid #ffd591', borderRadius: 6, padding: '12px 16px', marginBottom: 16 }}>
          <Text style={{ color: '#ad6800', fontSize: 13 }}>
            更换邮箱需要：1. 验证当前密码 2. 输入新邮箱并获取验证码 3. 输入验证码完成绑定
          </Text>
        </div>
        <Form form={emailForm} layout="vertical" onFinish={handleChangeEmail} style={{ maxWidth: 480 }}>
          <Form.Item
            name="currentPassword"
            rules={[{ required: true, message: '请输入当前密码以验证身份' }]}
            label="当前密码"
          >
            <Input.Password placeholder="请输入当前密码" size="large" />
          </Form.Item>
          <Form.Item
            name="newEmail"
            rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}
            label="新邮箱"
          >
            <Input placeholder="请输入新邮箱地址" size="large" />
          </Form.Item>
          <Form.Item label="验证码">
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item name="verifyCode" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                <Input placeholder="请输入验证码" size="large" style={{ flex: 1 }} />
              </Form.Item>
              <Button
                icon={<SendOutlined />}
                onClick={handleSendCode}
                loading={codeSending}
                disabled={countdown > 0}
                size="large"
                style={{ minWidth: 120 }}
              >
                {countdown > 0 ? `${countdown}s` : '获取验证码'}
              </Button>
            </Space.Compact>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={loading} size="large" block>
              确认更换邮箱
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default PortalSettingsPage;
