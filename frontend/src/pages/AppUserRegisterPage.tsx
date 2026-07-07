import React, { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Form, Input, Button, message, Grid, Typography } from 'antd';
import { motion, AnimatePresence } from 'framer-motion';
import {
  MailOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ArrowLeftOutlined,
  UserOutlined,
  CheckCircleFilled,
} from '@ant-design/icons';
import {
  sendAppUserRegisterEmailCode,
  submitAppUserRegister,
} from '../services/appUserRegisterPublicService';
import portalRequest from '../portal/services/portalRequest';

const { Text, Paragraph } = Typography;

/* ── 设计令牌 ── */
const T = {
  primary: '#2563EB',
  primaryHover: '#1D4ED8',
  primaryLight: '#EFF6FF',
  primaryBorder: '#BFDBFE',
  success: '#059669',
  successLight: '#ECFDF5',
  bg: '#F8FAFC',
  surface: '#FFFFFF',
  surfaceAlt: '#F1F5F9',
  darkBg: '#0F172A',
  darkSurface: '#1E293B',
  text: '#0F172A',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  textOnDark: '#F8FAFC',
  textOnDarkMuted: '#94A3B8',
  border: '#E2E8F0',
  borderHover: '#CBD5E1',
  radius: '8px',
  radiusLg: '12px',
};

/* ── 动画变体 ── */
const fadeIn = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.1, 0.25, 1] } },
};

const stagger = {
  visible: { transition: { staggerChildren: 0.06 } },
};

const panelSlideLeft = {
  enter: { x: '100%', opacity: 0 },
  center: { x: 0, opacity: 1, transition: { duration: 0.5, ease: [0.25, 0.1, 0.25, 1] } },
  exit: { x: '100%', opacity: 0, transition: { duration: 0.4 } },
};

const panelSlideRight = {
  enter: { x: '-100%', opacity: 0 },
  center: { x: 0, opacity: 1, transition: { duration: 0.5, ease: [0.25, 0.1, 0.25, 1] } },
  exit: { x: '-100%', opacity: 0, transition: { duration: 0.4 } },
};

type Mode = 'register' | 'login';

const AppUserRegisterPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [searchParams] = useSearchParams();
  const appId = searchParams.get('id');
  const [mode, setMode] = useState<Mode>('register');
  const [registerForm] = Form.useForm();
  const [loginForm] = Form.useForm();
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [captchaId, setCaptchaId] = useState('');

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

  const loadCaptcha = useCallback(async () => {
    try {
      const res: any = await portalRequest.get('/auth/captcha');
      if (res?.data) {
        setCaptchaUrl(res.data.image);
        setCaptchaId(res.data.captchaId);
      }
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    if (mode === 'login') {
      loadCaptcha();
    }
  }, [mode, loadCaptcha]);

  const handleSendCode = useCallback(async () => {
    if (!appId) { message.error('注册链接无效'); return; }
    const id = Number(appId);
    if (!Number.isFinite(id) || id <= 0) { message.error('注册链接无效'); return; }
    try { await registerForm.validateFields(['username', 'email']); }
    catch { message.error('请先填写用户名与有效邮箱'); return; }
    const email = registerForm.getFieldValue('email') as string;
    setSending(true);
    try {
      await sendAppUserRegisterEmailCode(id, email);
      message.success('验证码已发送，请查收邮件');
      setCountdown(60);
    } catch { /* interceptor */ } finally { setSending(false); }
  }, [registerForm, appId]);

  const handleRegister = async (values: { username: string; email: string; emailCode: string; password: string }) => {
    if (!appId) { message.error('注册链接无效'); return; }
    const id = Number(appId);
    if (!Number.isFinite(id) || id <= 0) { message.error('注册链接无效'); return; }
    setSubmitting(true);
    try {
      await submitAppUserRegister({ appId: id, username: values.username.trim(), email: values.email, emailCode: values.emailCode, password: values.password });
      message.success('注册成功');
      registerForm.resetFields();
    } catch { /* interceptor */ } finally { setSubmitting(false); }
  };

  const handleLogin = async (values: { email: string; password: string; captchaCode: string }) => {
    if (!appId) { message.error('应用ID无效'); return; }
    setLoginLoading(true);
    try {
      const res: any = await portalRequest.post('/auth/login', {
        email: values.email,
        password: values.password,
        captchaCode: values.captchaCode,
        captchaId,
      });
      if (res?.data?.token) {
        localStorage.setItem('portal_token', res.data.token);
        localStorage.setItem('portal_email', values.email);
        localStorage.setItem('portal_app_id', appId);
        message.success('登录成功');
        window.location.href = '/portal/dashboard';
      }
    } catch {
      message.error('登录失败，请检查邮箱和密码');
      loadCaptcha();
      loginForm.setFieldsValue({ captchaCode: '' });
    } finally {
      setLoginLoading(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
  };

  /* ─── 安全特性列表 ─── */
  const features = [
    { icon: <CheckCircleFilled style={{ color: T.success }} />, text: '端到端数据加密' },
    { icon: <CheckCircleFilled style={{ color: T.success }} />, text: '企业级安全认证' },
    { icon: <CheckCircleFilled style={{ color: T.success }} />, text: '实时操作审计日志' },
  ];

  /* ─── 注册表单 ─── */
  const renderRegisterForm = () => (
    <motion.div
      key="register-form"
      variants={panelSlideLeft}
      initial="enter"
      animate="center"
      exit="exit"
      style={{
        position: isMobile ? 'relative' : 'absolute',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: isMobile ? 'flex-start' : 'center',
        padding: isMobile ? '16px 20px 24px' : '48px 64px',
        background: T.surface,
        overflow: 'auto',
      }}
    >
      {/* 顶部导航 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: isMobile ? 20 : 32,
        }}
      >
        <Link
          to="/"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            textDecoration: 'none',
            color: T.textSecondary,
            fontSize: 13,
            fontWeight: 500,
          }}
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </Link>
        <button
          type="button"
          onClick={() => switchMode('login')}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            color: T.textSecondary,
            fontSize: 13,
            padding: 0,
          }}
        >
          已有账号？登录
        </button>
      </motion.div>

      {/* 标题区域 */}
      <motion.div
        initial="hidden"
        animate="visible"
        variants={stagger}
        style={{ marginBottom: isMobile ? 20 : 28 }}
      >
        <motion.div variants={fadeIn}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <img src="/favicon.svg" alt="" style={{ width: 28, height: 28 }} />
            <Text style={{ fontSize: isMobile ? 18 : 20, fontWeight: 700, color: T.text }}>
              CipherGate
            </Text>
          </div>
        </motion.div>
        <motion.div variants={fadeIn}>
          <Text style={{ fontSize: isMobile ? 22 : 26, fontWeight: 700, color: T.text, display: 'block', marginBottom: 6 }}>
            创建应用账户
          </Text>
        </motion.div>
        <motion.div variants={fadeIn}>
          <Text style={{ fontSize: 13, color: T.textSecondary }}>
            填写以下信息完成应用用户注册
          </Text>
        </motion.div>
      </motion.div>

      {/* 表单 */}
      <motion.div initial="hidden" animate="visible" variants={stagger}>
        <Form
          form={registerForm}
          layout="vertical"
          requiredMark={false}
          onFinish={handleRegister}
          size={isMobile ? 'small' : 'large'}
          style={{ maxWidth: isMobile ? '100%' : 420 }}
        >
          {/* 用户名 + 邮箱 */}
          <motion.div variants={fadeIn} style={{ display: 'flex', gap: isMobile ? 0 : 12, flexDirection: isMobile ? 'column' : 'row' }}>
            <Form.Item
              name="username"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>用户名</Text>}
              rules={[
                { required: true, message: '必填' },
                { min: 2, max: 50, message: '2~50字符' },
                { pattern: /^[a-zA-Z0-9_-]+$/, message: '仅字母数字' },
              ]}
              style={{ flex: 1, marginBottom: isMobile ? 12 : 16 }}
            >
              <Input prefix={<UserOutlined style={{ color: T.textMuted }} />} placeholder="myname_01" autoComplete="username" />
            </Form.Item>
            <Form.Item
              name="email"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>工作邮箱</Text>}
              rules={[
                { required: true, message: '必填' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
              style={{ flex: 1, marginBottom: isMobile ? 12 : 16 }}
            >
              <Input prefix={<MailOutlined style={{ color: T.textMuted }} />} placeholder="name@company.com" autoComplete="email" />
            </Form.Item>
          </motion.div>

          {/* 验证码 */}
          <motion.div variants={fadeIn}>
            <Form.Item
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>邮箱验证码</Text>}
              required
              style={{ marginBottom: isMobile ? 12 : 16 }}
            >
              <Form.Item
                name="emailCode"
                noStyle
                rules={[
                  { required: true, message: '必填' },
                  { len: 6, message: '6位数字' },
                  { pattern: /^\d{6}$/, message: '仅数字' },
                ]}
              >
                <Input
                  prefix={<SafetyCertificateOutlined style={{ color: T.textMuted }} />}
                  placeholder="6位验证码"
                  maxLength={6}
                  autoComplete="one-time-code"
                  addonAfter={
                    <Button
                      type="link"
                      loading={sending}
                      disabled={countdown > 0 || !appId}
                      onClick={() => void handleSendCode()}
                      style={{ margin: 0, padding: 0, height: 'auto', fontSize: isMobile ? 12 : 14, color: countdown > 0 ? T.textMuted : T.primary }}
                    >
                      {countdown > 0 ? `${countdown}s` : '发送验证码'}
                    </Button>
                  }
                />
              </Form.Item>
            </Form.Item>
          </motion.div>

          {/* 密码 + 确认密码 */}
          <motion.div variants={fadeIn} style={{ display: 'flex', gap: isMobile ? 0 : 12, flexDirection: isMobile ? 'column' : 'row' }}>
            <Form.Item
              name="password"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>登录密码</Text>}
              rules={[
                { required: true, message: '必填' },
                { min: 8, message: '至少8位' },
                { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '含字母与数字' },
              ]}
              hasFeedback
              style={{ flex: 1, marginBottom: isMobile ? 12 : 16 }}
            >
              <Input.Password prefix={<LockOutlined style={{ color: T.textMuted }} />} placeholder="至少8位" autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>确认密码</Text>}
              dependencies={['password']}
              rules={[
                { required: true, message: '必填' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) return Promise.resolve();
                    return Promise.reject(new Error('密码不一致'));
                  },
                }),
              ]}
              hasFeedback
              style={{ flex: 1, marginBottom: isMobile ? 12 : 16 }}
            >
              <Input.Password prefix={<LockOutlined style={{ color: T.textMuted }} />} placeholder="再次输入" autoComplete="new-password" />
            </Form.Item>
          </motion.div>

          {/* 注册按钮 */}
          <motion.div variants={fadeIn}>
            <Form.Item style={{ marginBottom: 12 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                disabled={!appId}
                style={{
                  height: isMobile ? 32 : 44,
                  fontWeight: 600,
                  fontSize: isMobile ? 13 : 14,
                  background: T.primary,
                  borderColor: T.primary,
                  borderRadius: T.radius,
                }}
              >
                注册
              </Button>
            </Form.Item>
          </motion.div>

          {/* 用户查询 */}
          {!isMobile && (
            <motion.div variants={fadeIn}>
              <div style={{ textAlign: 'center', marginTop: 8 }}>
                <Link to={`/app-user?id=${appId}`} style={{ fontSize: 13, color: T.textSecondary, textDecoration: 'none' }}>
                  用户查询
                </Link>
              </div>
            </motion.div>
          )}
        </Form>
      </motion.div>
    </motion.div>
  );

  /* ─── 登录表单 ─── */
  const renderLoginForm = () => (
    <motion.div
      key="login-form"
      variants={panelSlideRight}
      initial="enter"
      animate="center"
      exit="exit"
      style={{
        position: isMobile ? 'relative' : 'absolute',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: isMobile ? 'flex-start' : 'center',
        padding: isMobile ? '16px 20px 24px' : '48px 64px',
        background: T.surface,
        overflow: 'auto',
      }}
    >
      {/* 顶部导航 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: isMobile ? 20 : 32,
        }}
      >
        <Link
          to="/"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            textDecoration: 'none',
            color: T.textSecondary,
            fontSize: 13,
            fontWeight: 500,
          }}
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </Link>
        <button
          type="button"
          onClick={() => switchMode('register')}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            color: T.textSecondary,
            fontSize: 13,
            padding: 0,
          }}
        >
          还没有账号？注册
        </button>
      </motion.div>

      {/* 标题区域 */}
      <motion.div
        initial="hidden"
        animate="visible"
        variants={stagger}
        style={{ marginBottom: isMobile ? 20 : 28 }}
      >
        <motion.div variants={fadeIn}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <img src="/favicon.svg" alt="" style={{ width: 28, height: 28 }} />
            <Text style={{ fontSize: isMobile ? 18 : 20, fontWeight: 700, color: T.text }}>
              CipherGate
            </Text>
          </div>
        </motion.div>
        <motion.div variants={fadeIn}>
          <Text style={{ fontSize: isMobile ? 22 : 26, fontWeight: 700, color: T.text, display: 'block', marginBottom: 6 }}>
            应用用户登录
          </Text>
        </motion.div>
        <motion.div variants={fadeIn}>
          <Text style={{ fontSize: 13, color: T.textSecondary }}>
            使用邮箱和密码登录应用
          </Text>
        </motion.div>
      </motion.div>

      {/* 表单 */}
      <motion.div initial="hidden" animate="visible" variants={stagger}>
        <Form
          form={loginForm}
          layout="vertical"
          requiredMark={false}
          onFinish={handleLogin}
          size={isMobile ? 'small' : 'large'}
          style={{ maxWidth: isMobile ? '100%' : 420 }}
        >
          {/* 邮箱 */}
          <motion.div variants={fadeIn}>
            <Form.Item
              name="email"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>工作邮箱</Text>}
              rules={[
                { required: true, message: '必填' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
              style={{ marginBottom: isMobile ? 12 : 16 }}
            >
              <Input prefix={<MailOutlined style={{ color: T.textMuted }} />} placeholder="name@company.com" autoComplete="email" />
            </Form.Item>
          </motion.div>

          {/* 密码 */}
          <motion.div variants={fadeIn}>
            <Form.Item
              name="password"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>登录密码</Text>}
              rules={[
                { required: true, message: '必填' },
              ]}
              style={{ marginBottom: isMobile ? 12 : 16 }}
            >
              <Input.Password prefix={<LockOutlined style={{ color: T.textMuted }} />} placeholder="输入密码" autoComplete="current-password" />
            </Form.Item>
          </motion.div>

          {/* 验证码 */}
          <motion.div variants={fadeIn}>
            <Form.Item
              name="captchaCode"
              label={<Text style={{ fontSize: 12, fontWeight: 600, color: T.textSecondary }}>图形验证码</Text>}
              rules={[
                { required: true, message: '必填' },
              ]}
              style={{ marginBottom: isMobile ? 12 : 16 }}
            >
              <Input
                prefix={<SafetyCertificateOutlined style={{ color: T.textMuted }} />}
                placeholder="输入验证码"
                maxLength={4}
                addonAfter={
                  <div
                    onClick={loadCaptcha}
                    style={{
                      cursor: 'pointer',
                      height: 30,
                      margin: '-6px -16px -6px 0',
                      padding: 0,
                      display: 'flex',
                      alignItems: 'center',
                      background: 'transparent',
                    }}
                  >
                    {captchaUrl && <img src={captchaUrl} alt="验证码" style={{ height: 30, display: 'block', borderRadius: '0 6px 6px 0' }} />}
                  </div>
                }
              />
            </Form.Item>
          </motion.div>

          {/* 登录按钮 */}
          <motion.div variants={fadeIn}>
            <Form.Item style={{ marginBottom: 12 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={loginLoading}
                disabled={!appId}
                style={{
                  height: isMobile ? 32 : 44,
                  fontWeight: 600,
                  fontSize: isMobile ? 13 : 14,
                  background: T.primary,
                  borderColor: T.primary,
                  borderRadius: T.radius,
                }}
              >
                登录
              </Button>
            </Form.Item>
          </motion.div>

          {/* 用户查询 */}
          {!isMobile && (
            <motion.div variants={fadeIn}>
              <div style={{ textAlign: 'center', marginTop: 8 }}>
                <Link to={`/app-user?id=${appId}`} style={{ fontSize: 13, color: T.textSecondary, textDecoration: 'none' }}>
                  用户查询
                </Link>
              </div>
            </motion.div>
          )}
        </Form>
      </motion.div>
    </motion.div>
  );

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: isMobile ? 'column' : 'row',
        background: isMobile ? T.bg : T.darkBg,
        position: 'relative',
      }}
    >
      {/* ═══ 表单区 ═══ */}
      <div
        style={{
          flex: isMobile ? 1 : '0 0 58%',
          position: 'relative',
          minHeight: isMobile ? '100dvh' : undefined,
          overflow: isMobile ? 'hidden' : undefined,
        }}
      >
        <AnimatePresence mode="wait">
          {mode === 'register' ? renderRegisterForm() : renderLoginForm()}
        </AnimatePresence>
      </div>

      {/* ═══ 品牌面板（桌面端） ═══ */}
      {!isMobile && (
        <div
          style={{
            flex: '0 0 42%',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            padding: '48px 56px',
            background: `linear-gradient(135deg, ${T.darkBg} 0%, ${T.darkSurface} 100%)`,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* 装饰性几何图案 */}
          <div style={{ position: 'absolute', top: -80, right: -80, width: 320, height: 320, border: `1px solid rgba(255,255,255,0.06)`, borderRadius: '50%', pointerEvents: 'none' }} />
          <div style={{ position: 'absolute', bottom: -60, left: -60, width: 240, height: 240, border: `1px solid rgba(255,255,255,0.04)`, borderRadius: '50%', pointerEvents: 'none' }} />

          {/* Logo + 品牌名 */}
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5 }}
            style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 40 }}
          >
            <img src="/favicon.svg" alt="" style={{ width: 36, height: 36 }} />
            <Text style={{ fontSize: 20, fontWeight: 700, color: T.textOnDark, letterSpacing: '-0.3px' }}>
              CipherGate
            </Text>
          </motion.div>

          {/* 标语 */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
            style={{ textAlign: 'center', marginBottom: 40, maxWidth: 280 }}
          >
            <Text style={{ fontSize: 22, fontWeight: 700, color: T.textOnDark, display: 'block', lineHeight: 1.4, marginBottom: 12 }}>
              企业级访问
              <br />
              安全管控平台
            </Text>
            <Paragraph style={{ fontSize: 13, color: T.textOnDarkMuted, margin: 0, lineHeight: 1.6 }}>
              统一管理应用权限与用户身份，构建零信任安全架构
            </Paragraph>
          </motion.div>

          {/* 安全特性 */}
          <motion.div initial="hidden" animate="visible" variants={stagger} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {features.map((f, i) => (
              <motion.div
                key={i}
                variants={fadeIn}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '10px 16px',
                  background: 'rgba(255,255,255,0.04)', borderRadius: T.radius, border: '1px solid rgba(255,255,255,0.06)',
                }}
              >
                {f.icon}
                <Text style={{ fontSize: 13, color: T.textOnDark }}>{f.text}</Text>
              </motion.div>
            ))}
          </motion.div>

          {/* 底部条款 */}
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.4 }} style={{ position: 'absolute', bottom: 24, left: 0, right: 0, textAlign: 'center' }}>
            <Text style={{ fontSize: 11, color: T.textOnDarkMuted }}>
              注册即表示您同意{' '}
              <a href="#" style={{ color: T.textOnDarkMuted, textDecoration: 'underline' }} onClick={(e) => e.preventDefault()}>服务条款</a>
              {' '}与{' '}
              <a href="#" style={{ color: T.textOnDarkMuted, textDecoration: 'underline' }} onClick={(e) => e.preventDefault()}>隐私政策</a>
            </Text>
          </motion.div>
        </div>
      )}
    </div>
  );
};

export default AppUserRegisterPage;
