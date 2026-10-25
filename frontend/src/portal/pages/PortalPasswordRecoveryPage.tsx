import type { PortalPasswordReset } from '../services/types';
import PublicServiceLayout from '../../components/PublicServiceLayout';
import React, { useState, useCallback, useEffect } from 'react';
import { Form, Input, Button, message, Steps, Select } from 'antd';
import { MailOutlined, LockOutlined, SendOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';


const PortalPasswordRecoveryPage: React.FC = () => {
  const [form] = Form.useForm();
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState('');
  const [verifiedCode, setVerifiedCode] = useState('');
  const [apps, setApps] = useState<Array<{ appId: number; appName: string }>>([]);
  const navigate = useNavigate();
  const [captcha, setCaptcha] = useState({ captchaId: '', image: '' });
  const refreshCaptcha = useCallback(async () => {
    setCaptcha({ captchaId: '', image: '' });
    form.setFieldValue('captchaCode', '');
    try { const result = await portalAuthApi.getCaptcha(); setCaptcha(result.data); } catch { /* interceptor reports failures */ }
  }, [form]);
  useEffect(() => { void refreshCaptcha(); }, [refreshCaptcha]);

  const handleSendCode = async () => {
    try {
      const emailVal = await form.validateFields(['email', 'captchaCode']);
      setEmail(emailVal.email);
      setLoading(true);
      await portalAuthApi.sendRecoveryCode(emailVal.email, captcha.captchaId, emailVal.captchaCode);
      message.success('验证码已发送');
      setCurrentStep(1);
    } catch {
      void refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    try {
      const values = await form.validateFields(['verifyCode']);
      setLoading(true);
      const response = await portalAuthApi.getRecoveryApps(email, values.verifyCode);
      setApps(response.data || []);
      setVerifiedCode(values.verifyCode);
      setCurrentStep(2);
    } catch { /* Request errors are displayed by portalRequest. */ }
    finally { setLoading(false); }
  };

  const handleReset = async (values: Pick<PortalPasswordReset, 'appId' | 'newPassword'>) => {
    setLoading(true);
    try {
      await portalAuthApi.resetPassword({
        email,
        verifyCode: verifiedCode,
        appId: values.appId,
        newPassword: values.newPassword,
      });
      message.success('密码重置成功');
      navigate('/portal/login');
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  return (
    <PublicServiceLayout title="找回密码" description="验证注册邮箱后，为您选择的应用设置新密码。" help="验证码发送到您的注册邮箱，5 分钟内有效。请留意垃圾邮件文件夹。">
          <Steps current={currentStep} size="small" style={{ marginBottom: 24 }}
            items={[{ title: '发送验证码' }, { title: '验证邮箱' }, { title: '重置密码' }]}
          />

          {currentStep === 0 && (
            <Form form={form} layout="vertical" onFinish={handleSendCode}>
              <Form.Item name="email" label="注册邮箱" rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}>
                <Input prefix={<MailOutlined />} placeholder="注册邮箱" size="large" />
              </Form.Item>
              <Form.Item label="图形验证码" required>
                <div className="public-service-row">
                  <Form.Item name="captchaCode" noStyle rules={[{ required: true, message: '请输入图形验证码' }]}>
                    <Input placeholder="图形验证码" maxLength={4} autoComplete="off" aria-label="图形验证码" style={{ flex: 1, minWidth: 0 }} />
                  </Form.Item>
                  <button type="button" className="public-service-captcha" onClick={() => void refreshCaptcha()} aria-label="刷新图形验证码">
                    {captcha.image ? <img src={captcha.image} alt="图形验证码" /> : '点击刷新'}
                  </button>
                </div>
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" disabled={!captcha.captchaId} loading={loading} block size="large">发送验证码</Button>
              </Form.Item>
            </Form>
          )}

          {currentStep === 1 && (<><p className="public-service-note">验证码已发送至 {email}</p>
            <Form form={form} layout="vertical" onFinish={handleVerify}>
              <Form.Item name="verifyCode" label="邮箱验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input prefix={<SendOutlined />} placeholder="验证码" size="large" autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block size="large">验证邮箱</Button>
            </Form>
          </>)}
          {currentStep === 2 && (
            <Form form={form} layout="vertical" onFinish={handleReset}>
              <Form.Item name="appId" label="需要重置的应用" rules={[{ required: true, message: '请选择应用' }]}>
                <Select placeholder="选择要重置密码的应用" size="large" options={apps.map(app => ({ value: app.appId, label: app.appName }))} />
              </Form.Item>
              <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 6, max: 50, message: '密码长度6-50位' }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="新密码" size="large" autoComplete="new-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block size="large">重置密码</Button>
            </Form>
          )}
          {currentStep > 0 && <>
          <Button type="link" disabled={loading} onClick={() => { setCurrentStep(0); void refreshCaptcha(); setVerifiedCode(''); setApps([]); form.resetFields(['verifyCode', 'newPassword', 'appId']); }}>更换邮箱或重新获取验证码</Button></>}

          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/portal/login"><Button type="link">返回登录</Button></Link>
          </div>
    </PublicServiceLayout>
  );
};

export default PortalPasswordRecoveryPage;
