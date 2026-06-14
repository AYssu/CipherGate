import React, { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Layout,
  Typography,
  Card,
  Form,
  Input,
  Button,
  Space,
  message,
  Alert,
  Grid,
} from 'antd';
import {
  MailOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ArrowLeftOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  sendAppUserRegisterEmailCode,
  submitAppUserRegister,
} from '../services/appUserRegisterPublicService';

const { Header, Content, Footer } = Layout;
const { Title, Text, Paragraph } = Typography;

/**
 * 终端用户注册页（与首页同级路由）。
 * 通过 URL 查询参数 {@code ?id=} 指定所属应用（不向用户展示）；验证码走公开接口并写入 Redis（仅注册）。
 */
const AppUserRegisterPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  // 初始 {} 时 md 为 undefined，仅当明确 md === false 才用窄屏布局，避免桌面首屏误判
  const isNarrow = screens.md === false;
  const cardMaxWidth =
    screens.md === false ? 440 : screens.lg === true ? 600 : 520;

  const [searchParams] = useSearchParams();
  const appId = searchParams.get('id');
  const [form] = Form.useForm();
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const iconMuted = 'rgba(0,0,0,0.25)';

  useEffect(() => {
    if (countdown <= 0) return;
    const t = window.setInterval(() => setCountdown((c) => (c <= 1 ? 0 : c - 1)), 1000);
    return () => window.clearInterval(t);
  }, [countdown]);

  useEffect(() => {
    if (!appId) {
      message.warning('当前链接无效或已过期，请通过应用方提供的注册入口重新打开');
    }
  }, [appId]);

  const handleSendCode = useCallback(async () => {
    if (!appId) {
      message.error('注册链接无效');
      return;
    }
    const id = Number(appId);
    if (!Number.isFinite(id) || id <= 0) {
      message.error('注册链接无效');
      return;
    }
    try {
      await form.validateFields(['username', 'email']);
    } catch {
      message.error('请先填写用户名与有效邮箱');
      return;
    }
    const email = form.getFieldValue('email') as string;
    setSending(true);
    try {
      await sendAppUserRegisterEmailCode(id, email);
      message.success('验证码已发送，请查收邮件');
      setCountdown(60);
    } catch {
      /* axios 拦截器已提示错误 */
    } finally {
      setSending(false);
    }
  }, [form, appId]);

  const onFinish = async (values: {
    username: string;
    email: string;
    emailCode: string;
    password: string;
  }) => {
    if (!appId) {
      message.error('注册链接无效');
      return;
    }
    const id = Number(appId);
    if (!Number.isFinite(id) || id <= 0) {
      message.error('注册链接无效');
      return;
    }
    setSubmitting(true);
    try {
      await submitAppUserRegister({
        appId: id,
        username: values.username.trim(),
        email: values.email,
        emailCode: values.emailCode,
        password: values.password,
      });
      message.success('注册成功');
      form.resetFields();
    } catch {
      /* 拦截器已提示 */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#fff' }}>
      <Header
        style={{
          background: 'rgba(255, 255, 255, 0.95)',
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          borderBottom: '1px solid #f0f0f0',
          position: 'fixed',
          width: '100%',
          zIndex: 1000,
          backdropFilter: 'blur(10px)',
          padding: 0,
          height: 64,
          lineHeight: '64px',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            maxWidth: 1400,
            margin: '0 auto',
            padding: isNarrow ? '0 14px' : '0 24px',
            height: '100%',
            boxSizing: 'border-box',
          }}
        >
          {isNarrow ? (
            <>
              <Link
                to="/"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  textDecoration: 'none',
                  color: 'inherit',
                  minWidth: 0,
                  flex: 1,
                }}
              >
                <img
                  src="/favicon.svg"
                  alt="CipherGate"
                  style={{ marginRight: 10, width: 32, height: 32, flexShrink: 0 }}
                />
                <span
                  style={{
                    fontSize: 18,
                    fontWeight: 700,
                    background: 'linear-gradient(135deg, #00d4aa, #1890ff)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    letterSpacing: '-0.5px',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  CipherGate
                </span>
              </Link>
              <Link
                to="/"
                style={{
                  color: '#595959',
                  fontWeight: 500,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  flexShrink: 0,
                  marginLeft: 8,
                }}
              >
                <ArrowLeftOutlined />
                返回
              </Link>
            </>
          ) : (
            <>
              <Link
                to="/"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  textDecoration: 'none',
                  color: 'inherit',
                }}
              >
                <img
                  src="/favicon.svg"
                  alt="CipherGate"
                  style={{ marginRight: 12, width: 40, height: 40 }}
                />
                <span
                  style={{
                    fontSize: 22,
                    fontWeight: 700,
                    background: 'linear-gradient(135deg, #00d4aa, #1890ff)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    letterSpacing: '-0.5px',
                  }}
                >
                  CipherGate
                </span>
                <span
                  style={{
                    marginLeft: 16,
                    paddingLeft: 16,
                    borderLeft: '1px solid #e8e8e8',
                    fontSize: 15,
                    fontWeight: 600,
                    color: '#262626',
                    WebkitTextFillColor: '#262626',
                  }}
                >
                  账号注册
                </span>
              </Link>
              <Link
                to="/"
                style={{
                  color: '#595959',
                  fontWeight: 500,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <ArrowLeftOutlined />
                返回首页
              </Link>
            </>
          )}
        </div>
      </Header>

      <Content
        style={{
          marginTop: 64,
          minHeight: 'calc(100vh - 64px - 52px)',
          padding: '48px 16px 40px',
          background: '#f5f7fa',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'flex-start',
        }}
      >
        <Card
          style={{
            width: '100%',
            maxWidth: cardMaxWidth,
            borderRadius: 12,
            border: '1px solid #f0f0f0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            background: '#fff',
          }}
          styles={{ body: { padding: '40px 36px 32px' } }}
        >
          <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 28 }}>
            <div>
              <Title level={3} style={{ margin: 0, color: '#262626', fontWeight: 600 }}>
                创建应用账号
              </Title>
              <Paragraph style={{ marginTop: 8, marginBottom: 0, color: '#8c8c8c', fontSize: 14 }}>
                请设置登录用户名并填写工作邮箱；用户名将用于登录，验证码将发送至邮箱以完成校验。
              </Paragraph>
            </div>
            {!appId && (
              <Alert
                type="warning"
                showIcon
                message="无法完成注册"
                description="请通过应用管理员下发的专用注册链接访问本页面。若您有邀请链接，请确认地址完整。"
              />
            )}
          </Space>

          <Form form={form} layout="vertical" requiredMark="optional" onFinish={onFinish}>
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 2, max: 50, message: '用户名为 2～50 个字符' },
                {
                  pattern: /^[a-zA-Z0-9_-]+$/,
                  message: '仅支持字母、数字、下划线、中划线',
                },
              ]}
            >
              <Input
                size="large"
                prefix={<UserOutlined style={{ color: iconMuted }} />}
                placeholder="例如 myname_01"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              name="email"
              label="工作邮箱"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <Input
                size="large"
                prefix={<MailOutlined style={{ color: iconMuted }} />}
                placeholder="name@company.com"
                autoComplete="email"
              />
            </Form.Item>

            <Form.Item label="邮箱验证码" required>
              <Space.Compact style={{ width: '100%' }}>
                <Form.Item
                  name="emailCode"
                  noStyle
                  rules={[
                    { required: true, message: '请输入验证码' },
                    { len: 6, message: '验证码为 6 位' },
                    { pattern: /^\d{6}$/, message: '请输入 6 位数字' },
                  ]}
                >
                  <Input
                    size="large"
                    style={{ flex: 1 }}
                    prefix={<SafetyCertificateOutlined style={{ color: iconMuted }} />}
                    placeholder="6 位数字"
                    maxLength={6}
                    autoComplete="one-time-code"
                  />
                </Form.Item>
                <Button
                  size="large"
                  loading={sending}
                  disabled={countdown > 0 || !appId}
                  onClick={() => void handleSendCode()}
                  style={{ minWidth: 120 }}
                >
                  {countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
                </Button>
              </Space.Compact>
            </Form.Item>

            <Form.Item
              name="password"
              label="登录密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 8, message: '至少 8 位字符' },
                {
                  pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
                  message: '需同时包含字母与数字',
                },
              ]}
              hasFeedback
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined style={{ color: iconMuted }} />}
                placeholder="至少 8 位，含字母与数字"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请再次输入密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
              hasFeedback
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined style={{ color: iconMuted }} />}
                placeholder="再次输入密码"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 8 }}>
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  loading={submitting}
                  disabled={!appId}
                  style={{ height: 44, fontWeight: 600 }}
                >
                  注册
                </Button>
                {appId && (
                  <Button
                    size="large"
                    block
                    href={`/app-user?id=${appId}`}
                    style={{ height: 44, fontWeight: 500 }}
                  >
                    用户查询
                  </Button>
                )}
              </Space>
            </Form.Item>
          </Form>

          <Text type="secondary" style={{ display: 'block', textAlign: 'center', fontSize: 12, color: '#8c8c8c' }}>
            注册即表示您已阅读并同意
            <a href="#" style={{ color: '#1890ff', margin: '0 4px' }} onClick={(e) => e.preventDefault()}>
              服务条款
            </a>
            与
            <a href="#" style={{ color: '#1890ff', margin: '0 4px' }} onClick={(e) => e.preventDefault()}>
              隐私政策
            </a>
          </Text>
        </Card>
      </Content>

      <Footer
        style={{
          textAlign: 'center',
          padding: '16px 24px',
          background: '#fff',
          borderTop: '1px solid #f0f0f0',
          color: '#8c8c8c',
          fontSize: 13,
        }}
      >
        <Text type="secondary">CipherGate · 企业级访问与安全管控</Text>
      </Footer>
    </Layout>
  );
};

export default AppUserRegisterPage;
