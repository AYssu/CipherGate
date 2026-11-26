import type { PortalProfile, PortalPasswordChange, PortalEmailChange } from '../services/types';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, Typography, Form, Input, Button, message, Space, Row, Col } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, SendOutlined, SafetyOutlined } from '@ant-design/icons';
import { portalSettingsApi } from '../services/portalSettingsService';
import { useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Title } = Typography;

const PortalSettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [emailForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [codeSending, setCodeSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [profile, setProfile] = useState<PortalProfile | null>(null);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const loadProfile = useCallback(async () => {
    try {
      const res = await portalSettingsApi.getProfile();
      if (res?.data) {
        setProfile(res.data);
        profileForm.setFieldsValue({ nickname: res.data.nickname });
        localStorage.setItem('portal_email', res.data.email);
      }
    } catch {
      // ignore
    }
  }, [profileForm]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const handleUpdateNickname = async (values: { nickname: string }) => {
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

  const requireFreshLogin = () => {
    for (const key of ['portal_token', 'portal_app_id', 'portal_temp_token', 'portal_apps']) localStorage.removeItem(key);
    navigate('/portal/login');
  };

  const handleChangePassword = async (values: PortalPasswordChange) => {
    setLoading(true);
    try {
      await portalSettingsApi.changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('密码修改成功，请重新登录');
      passwordForm.resetFields();
      requireFreshLogin();
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

  const handleChangeEmail = async (values: PortalEmailChange) => {
    setLoading(true);
    try {
      await portalSettingsApi.changeEmail({
        currentPassword: values.currentPassword,
        newEmail: values.newEmail,
        verifyCode: values.verifyCode,
      });
      message.success('邮箱更换成功，请使用新邮箱登录');
      emailForm.resetFields();
      requireFreshLogin();
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="portal-settings">
      <header className="portal-page__header"><div><Title level={4} className="portal-page__title">账号设置</Title><p className="portal-page__subtitle">管理个人资料、登录密码和绑定邮箱。</p></div></header>

      <div className="settings-account">
        <span className="portal-icon"><SafetyOutlined /></span>
        <div><span className="membership-label">当前登录邮箱</span><strong>{profile?.email || '—'}</strong></div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card className="portal-panel settings-panel" title={<><UserOutlined /> 昵称设置</>}>
            <p className="settings-guidance">设置你在应用中显示的名称。</p>
            <Form form={profileForm} layout="vertical" onFinish={handleUpdateNickname}>
              <Form.Item label="昵称" name="nickname" rules={[{ required: true, message: '请输入昵称' }]}>
                <Input placeholder="请输入昵称" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" loading={loading}>
                  保存昵称
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24}>
          <Card className="portal-panel settings-panel" title={<><LockOutlined /> 修改密码</>}>
            <p className="settings-guidance">修改后需要使用新密码重新登录。</p>
            <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
              <Form.Item label="当前密码" name="oldPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
                <Input.Password placeholder="当前密码" />
              </Form.Item>
              <Form.Item label="新密码" name="newPassword" rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少6位' }]}>
                <Input.Password placeholder="新密码" />
              </Form.Item>
              <Form.Item
                label="确认新密码"
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
                <Input.Password placeholder="确认新密码" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" loading={loading}>
                  修改密码
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>
      </Row>

      <Card className="portal-panel settings-panel portal-section-gap" title={<><MailOutlined /> 更换邮箱</>}>
        <p className="settings-guidance">验证当前密码和新邮箱后完成更换，更换后需要重新登录。</p>
        <Form form={emailForm} layout="vertical" onFinish={handleChangeEmail} style={{ maxWidth: 480 }}>
          <Form.Item
            name="currentPassword"
            rules={[{ required: true, message: '请输入当前密码以验证身份' }]}
            label="当前密码"
          >
            <Input.Password placeholder="请输入当前密码" />
          </Form.Item>
          <Form.Item
            name="newEmail"
            rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}
            label="新邮箱"
          >
            <Input placeholder="请输入新邮箱地址" />
          </Form.Item>
          <Form.Item label="验证码">
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item name="verifyCode" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                <Input placeholder="请输入验证码" style={{ flex: 1 }} />
              </Form.Item>
              <Button
                icon={<SendOutlined />}
                onClick={handleSendCode}
                loading={codeSending}
                disabled={countdown > 0}

                style={{ minWidth: 120 }}
              >
                {countdown > 0 ? `${countdown}s` : '获取验证码'}
              </Button>
            </Space.Compact>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={loading}>
              确认更换邮箱
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default PortalSettingsPage;
