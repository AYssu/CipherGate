import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert, Button, Form, Input, message } from 'antd';
import { MailOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { usePublicChallenge } from '../components/usePublicChallenge';
import PublicServiceLayout from '../components/PublicServiceLayout';
import { sendAppUserRegisterEmailCode, submitAppUserRegister } from '../services/appUserRegisterPublicService';

type Registration = { username: string; email: string; emailCode: string; password: string };

export default function AppUserRegisterPage() {
  const [params] = useSearchParams();
  const id = Number(params.get('id'));
  const appId = Number.isSafeInteger(id) && id > 0 ? id : null;
  const [form] = Form.useForm<Registration>();
  const challenge = usePublicChallenge('register-email');
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [registered, setRegistered] = useState(false);
  useEffect(() => {
    if (!countdown) return;
    const timer = window.setTimeout(() => setCountdown(countdown - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  async function sendCode() {
    if (!appId || sending || countdown) return;
    try { await form.validateFields(['username', 'email']); }
    catch { message.error('请先填写账号与有效邮箱'); return; }
    const verification = challenge.get(); if (!verification) return;
    setSending(true);
    try {
      await sendAppUserRegisterEmailCode(appId, form.getFieldValue('email').trim().toLowerCase(), verification);
      setCountdown(60);
      message.success('验证码已发送，请查收邮件');
    } catch { /* The request interceptor displays the error. */ }
    finally { setSending(false); void challenge.refresh(); }
  }

  async function register(values: Registration) {
    if (!appId || submitting) return;
    setSubmitting(true);
    try {
      await submitAppUserRegister({ appId, username: values.username.trim(), email: values.email.trim().toLowerCase(), emailCode: values.emailCode.trim(), password: values.password });
      setRegistered(true);
      form.resetFields();
    } catch { /* The request interceptor displays the error. */ }
    finally { setSubmitting(false); }
  }

  return (
    <PublicServiceLayout title="创建应用账户" description="完成邮箱验证，创建您在当前应用中的账号。" help="账号用于应用登录，邮箱用于接收验证码和找回密码。请使用本人可正常收信的邮箱。">
      {!appId && <Alert type="warning" showIcon message="注册链接无效" description="请通过应用方提供的专用链接重新打开。" />}
      {registered ? <div role="status"><h2 className="public-service-section">注册成功</h2><p className="public-service-note">您可以使用刚才的邮箱和密码登录，也可以返回应用继续使用。</p><Link to="/portal/login"><Button type="primary" block>前往登录</Button></Link></div> : (
        <Form name="app-register" form={form} layout="vertical" requiredMark={false} onFinish={register} disabled={!appId || submitting}>
          <Form.Item name="username" label="账号" extra="2～50 位，可使用字母、数字、下划线或短横线" rules={[{ required: true, message: '请输入账号' }, { min: 2, max: 50, message: '请输入 2～50 个字符' }, { pattern: /^[a-zA-Z0-9_-]+$/, message: '可使用字母、数字、下划线和中划线' }]}>
            <Input prefix={<UserOutlined />} placeholder="请输入账号" autoComplete="username" maxLength={50} />
          </Form.Item>
          <Form.Item name="email" label="注册邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }, { max: 100, message: '邮箱不能超过100个字符' }]}>
            <Input prefix={<MailOutlined />} placeholder="请输入注册邮箱" autoComplete="email" inputMode="email" />
          </Form.Item>
          {challenge.fields}
          <Form.Item label="邮箱验证码" extra="验证码 3 分钟内有效。未收到时，请检查垃圾邮件。">
            <div className="public-service-row">
              <Form.Item name="emailCode" noStyle rules={[{ required: true, message: '请输入邮箱验证码' }, { pattern: /^\d{6}$/, message: '请输入6位数字验证码' }]}>
                <Input aria-label="邮箱验证码" placeholder="6位数字" autoComplete="one-time-code" inputMode="numeric" maxLength={6} style={{ flex: 1, minWidth: 0 }} />
              </Form.Item>
              <Button loading={sending} disabled={!appId || countdown > 0} onClick={() => void sendCode()}>{countdown ? `${countdown} 秒后重发` : '发送验证码'}</Button>
            </div>
          </Form.Item>
          <Form.Item name="password" label="设置密码" rules={[{ required: true, message: '请输入密码' }, { min: 6, max: 50, message: '密码长度为6～50位，UTF-8不超过72字节' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="请设置至少 6 位密码" autoComplete="new-password" maxLength={50} />
          </Form.Item>
          <Form.Item name="confirmPassword" label="确认密码" dependencies={['password']} rules={[{ required: true, message: '请再次输入密码' }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}>
            <Input.Password placeholder="请再次输入密码" autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} disabled={!appId} block>创建账户</Button>
        </Form>
      )}
      <div className="public-service-actions"><Link to="/portal/login">已有账号？登录</Link></div>
    </PublicServiceLayout>
  );
}
