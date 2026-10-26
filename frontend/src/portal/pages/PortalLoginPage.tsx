import type { PortalLoginInput } from '../services/types';
import CaptchaImage from '../../components/CaptchaImage';
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
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaFailed, setCaptchaFailed] = useState(false);
  const navigate = useNavigate();

  const loadCaptcha = useCallback(async () => {
    setCaptchaId(''); setCaptchaUrl(''); setCaptchaLoading(true); setCaptchaFailed(false);
    form.setFieldValue('captchaCode', '');
    try {
      const res = await portalAuthApi.getCaptcha();
      if (res?.data?.image && res.data.captchaId) {
        setCaptchaUrl(res.data.image);
        setCaptchaId(res.data.captchaId);
      } else { setCaptchaFailed(true); }
    } catch {
      setCaptchaFailed(true);
    } finally { setCaptchaLoading(false); }
  }, [form]);

  useEffect(() => {
    loadCaptcha();
  }, [loadCaptcha]);

  const handleLogin = async (values: Omit<PortalLoginInput, 'captchaId'>) => {
    setLoading(true);
    try {
      const res = await portalAuthApi.login({
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
              <Input prefix={<MailOutlined />} placeholder="请输入注册邮箱" autoComplete="email" inputMode="email"  />
            </Form.Item>

            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="请输入密码"  autoComplete="current-password" />
            </Form.Item>

            <Form.Item label="图形验证码" extra={captchaFailed ? <span role="status" className="public-service-error">验证码加载失败，请点击重试。</span> : "看不清可点击图片换一张"}>
              <div className="public-service-row">
                <Form.Item name="captchaCode" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                  <Input prefix={<KeyOutlined />} placeholder="验证码" aria-label="图形验证码" autoComplete="off" maxLength={4} style={{ flex: 1, minWidth: 0 }} />
                </Form.Item>
                <CaptchaImage image={captchaUrl} loading={captchaLoading} failed={captchaFailed}
                  onRefresh={() => void loadCaptcha()} onError={() => { setCaptchaFailed(true); setCaptchaUrl(''); setCaptchaId(''); }} />
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
