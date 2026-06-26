import React, { useState, useEffect, useCallback } from 'react';
import { Layout, Card, Form, Input, Button, Typography, message, Space, Divider } from 'antd';
import { MailOutlined, LockOutlined, SafetyOutlined, KeyOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Content } = Layout;
const { Title, Text } = Typography;

const PortalLoginPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [captchaId, setCaptchaId] = useState('');
  const navigate = useNavigate();

  const loadCaptcha = useCallback(async () => {
    try {
      const res: any = await portalAuthApi.getCaptcha();
      if (res?.data) {
        setCaptchaUrl(res.data.image);
        setCaptchaId(res.data.captchaId);
      }
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    loadCaptcha();
  }, [loadCaptcha]);

  const handleLogin = async (values: any) => {
    setLoading(true);
    try {
      const res: any = await portalAuthApi.login({
        email: values.email,
        password: values.password,
        captchaCode: values.captchaCode,
        captchaId: captchaId,
      });

      if (res?.data?.needSelectApp) {
        localStorage.setItem('portal_temp_token', res.data.token);
        localStorage.setItem('portal_email', values.email);
        localStorage.setItem('portal_apps', JSON.stringify(res.data.apps));
        navigate('/portal/select-app');
      } else {
        localStorage.setItem('portal_token', res.data.token);
        localStorage.setItem('portal_email', values.email);
        if (res.data.apps?.[0]) {
          localStorage.setItem('portal_app_id', String(res.data.apps[0].appId));
        }
        message.success('登录成功');
        navigate('/portal/dashboard');
      }
    } catch (err: any) {
      loadCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Content style={{ width: '100%', maxWidth: 420, padding: '24px' }}>
        <Card style={{ borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.08)' }}>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <SafetyOutlined style={{ fontSize: 40, color: '#1890ff' }} />
            <Title level={3} style={{ margin: '12px 0 4px' }}>应用用户登录</Title>
            <Text type="secondary">登录后管理您的应用会员与账号</Text>
          </div>

          <Form form={form} layout="vertical" onFinish={handleLogin} autoComplete="off">
            <Form.Item name="email" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
              <Input prefix={<MailOutlined />} placeholder="邮箱地址" size="large" />
            </Form.Item>

            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" autoComplete="current-password" />
            </Form.Item>

            <Form.Item name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <Input prefix={<KeyOutlined />} placeholder="验证码" size="large" style={{ flex: 1 }} />
                <div
                  onClick={loadCaptcha}
                  style={{ cursor: 'pointer', height: 40, borderRadius: 6, overflow: 'hidden', border: '1px solid #d9d9d9', flexShrink: 0 }}
                >
                  {captchaUrl && <img src={captchaUrl} alt="验证码" style={{ height: 40, display: 'block' }} />}
                </div>
              </div>
            </Form.Item>

            <Form.Item style={{ marginBottom: 12 }}>
              <Button type="primary" htmlType="submit" loading={loading} block size="large" style={{ height: 44, fontWeight: 500 }}>
                登录
              </Button>
            </Form.Item>
          </Form>

          <Divider plain style={{ margin: '12px 0' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>其他操作</Text>
          </Divider>

          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <Link to="/portal/recovery">
              <Button block size="large">找回密码</Button>
            </Link>
            <Link to="/">
              <Button block size="large" type="link">返回首页</Button>
            </Link>
          </Space>
        </Card>
      </Content>
    </Layout>
  );
};

export default PortalLoginPage;
