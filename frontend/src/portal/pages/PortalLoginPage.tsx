import PublicServiceLayout from '../../components/PublicServiceLayout';
import React, { useState, useEffect, useCallback } from 'react';
import { Form, Input, Button, Typography, message } from 'antd';
import { MailOutlined, LockOutlined, KeyOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Text } = Typography;

const PortalLoginPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [captchaId, setCaptchaId] = useState('');
  const navigate = useNavigate();

  const loadCaptcha = useCallback(async () => {
    setCaptchaId('');
    form.setFieldValue('captchaCode', '');
    try {
      const res: any = await portalAuthApi.getCaptcha();
      if (res?.data) {
        setCaptchaUrl(res.data.image);
        setCaptchaId(res.data.captchaId);
      }
    } catch {
      // ignore
    }
  }, [form]);

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
    } catch {
      loadCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <PublicServiceLayout title="应用用户登录" description="登录后查看应用会员、充值记录，管理您的账号。" help="使用注册邮箱与密码登录。忘记密码时，可通过邮箱验证码找回。">
          <Form form={form} layout="vertical" onFinish={handleLogin} size="large">
            <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
              <Input prefix={<MailOutlined />} placeholder="邮箱地址"  />
            </Form.Item>

            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码"  autoComplete="current-password" />
            </Form.Item>

            <Form.Item label="图形验证码">
              <div className="public-service-row">
                <Form.Item name="captchaCode" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                  <Input prefix={<KeyOutlined />} placeholder="验证码" aria-label="图形验证码" autoComplete="off" maxLength={4} style={{ flex: 1, minWidth: 0 }} />
                </Form.Item>
                <button type="button" className="public-service-captcha" onClick={() => void loadCaptcha()} aria-label="刷新图形验证码">
                  {captchaUrl ? <img src={captchaUrl} alt="图形验证码" /> : '点击刷新'}
                </button>
              </div>
            </Form.Item>

            <Form.Item style={{ marginBottom: 12 }}>
              <Button type="primary" htmlType="submit" loading={loading} block  disabled={!captchaId}>
                登录
              </Button>
            </Form.Item>
          </Form>

          <div className="public-service-actions"><Link to="/portal/recovery">找回密码</Link><Text type="secondary">请勿向他人透露密码或验证码</Text></div>
    </PublicServiceLayout>
  );
};

export default PortalLoginPage;
