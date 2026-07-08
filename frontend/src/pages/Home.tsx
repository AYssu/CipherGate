import React, { useState, useEffect, useCallback } from 'react';
import { Layout, Button, Form, Input, message, Modal, Tabs, Typography } from 'antd';
import {
  SafetyCertificateOutlined,
  GithubOutlined,
  SettingOutlined,
  UserOutlined,
  KeyOutlined,
  MailOutlined,
  LockOutlined,
  SafetyOutlined,
  SecurityScanOutlined,
  CloudServerOutlined,
  MonitorOutlined,
} from '@ant-design/icons';
import { systemApi } from '../services';
import safeIcon from '../assets/icons/safe.svg';
import portalRequest from '../portal/services/portalRequest';

const { Header, Content, Footer } = Layout;
const { Title, Text } = Typography;

// ─── Design tokens ───────────────────────────────────────────
const C = {
  midnight: '#0C1222',
  navy: '#111B2E',
  slate: '#1A2540',
  emerald: '#00C9A7',
  emeraldDim: 'rgba(0,201,167,0.12)',
  sky: '#38BDF8',
  amber: '#FBBF24',
  text: '#E2E8F0',
  textMuted: '#94A3B8',
  textDim: '#64748B',
  border: 'rgba(255,255,255,0.06)',
} as const;

const T = {
  mono: "'JetBrains Mono', 'Fira Code', 'SF Mono', monospace",
  sans: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
  display: "'DM Sans', 'Inter', sans-serif",
} as const;

// ─── CSS-in-JS styles ────────────────────────────────────────
const styles: Record<string, React.CSSProperties> = {
  // Page reset
  page: {
    fontFamily: T.sans,
    background: C.midnight,
    color: C.text,
    minHeight: '100vh',
    overflow: 'hidden',
  },

  // ── Header
  header: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1000,
    background: 'rgba(12,18,34,0.85)',
    backdropFilter: 'blur(20px)',
    borderBottom: `1px solid ${C.border}`,
    height: 64,
    display: 'flex',
    alignItems: 'center',
  },
  headerInner: {
    maxWidth: '100%',
    margin: '0 auto',
    padding: '0 32px',
    width: '100%',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logo: {
    fontSize: 22,
    fontWeight: 700,
    color: C.text,
    fontFamily: T.mono,
    letterSpacing: '-0.5px',
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    textDecoration: 'none',
  },
  logoAccent: {
    color: C.emerald,
  },

  // ── Hero
  hero: {
    position: 'relative',
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '100px 24px 60px',
    overflow: 'hidden',
    background: C.midnight,
  },
  heroBg: {
    position: 'absolute',
    inset: 0,
    background: `
      radial-gradient(ellipse 80% 50% at 50% -10%, rgba(0,201,167,0.08) 0%, transparent 60%),
      radial-gradient(ellipse 60% 40% at 80% 50%, rgba(56,189,248,0.05) 0%, transparent 60%)
    `,
    pointerEvents: 'none',
  },
  heroGrid: {
    position: 'absolute',
    inset: 0,
    backgroundImage: `
      linear-gradient(${C.border} 1px, transparent 1px),
      linear-gradient(90deg, ${C.border} 1px, transparent 1px)
    `,
    backgroundSize: '64px 64px',
    maskImage: 'radial-gradient(ellipse 70% 50% at 50% 50%, black, transparent)',
    WebkitMaskImage: 'radial-gradient(ellipse 70% 50% at 50% 50%, black, transparent)',
    pointerEvents: 'none',
  },
  heroContent: {
    position: 'relative',
    zIndex: 10,
    textAlign: 'center',
    maxWidth: 820,
  },
  eyebrow: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 8,
    padding: '6px 16px',
    background: C.emeraldDim,
    border: `1px solid rgba(0,201,167,0.2)`,
    borderRadius: 100,
    fontSize: 13,
    fontFamily: T.mono,
    color: C.emerald,
    marginBottom: 32,
    letterSpacing: '0.5px',
  },
  heroTitle: {
    fontFamily: T.display,
    fontSize: 'clamp(36px, 6vw, 72px)',
    fontWeight: 700,
    lineHeight: 1.1,
    letterSpacing: '-1.5px',
    color: C.text,
    marginBottom: 24,
  },
  heroTitleAccent: {
    background: `linear-gradient(135deg, ${C.emerald}, ${C.sky})`,
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text',
  },
  heroDesc: {
    fontSize: 'clamp(16px, 2vw, 20px)',
    lineHeight: 1.6,
    color: C.textMuted,
    marginBottom: 48,
    maxWidth: 560,
    margin: '0 auto 48px',
  },

  // ── Gate animation
  gateContainer: {
    position: 'absolute',
    bottom: '12%',
    left: '50%',
    transform: 'translateX(-50%)',
    zIndex: 5,
    display: 'flex',
    gap: 2,
    fontFamily: T.mono,
    fontSize: 11,
    color: 'rgba(0,201,167,0.3)',
    lineHeight: 1.3,
    userSelect: 'none',
    pointerEvents: 'none',
  },
  gateLeft: {
    animation: 'gateSlideLeft 1.8s cubic-bezier(0.23, 1, 0.32, 1) forwards',
    animationDelay: '0.3s',
    opacity: 0,
    whiteSpace: 'pre',
  },
  gateRight: {
    animation: 'gateSlideRight 1.8s cubic-bezier(0.23, 1, 0.32, 1) forwards',
    animationDelay: '0.3s',
    opacity: 0,
    whiteSpace: 'pre',
  },

  // ── Buttons
  btnPrimary: {
    height: 52,
    padding: '0 32px',
    fontSize: 15,
    fontWeight: 600,
    fontFamily: T.sans,
    borderRadius: 10,
    background: C.emerald,
    border: 'none',
    color: C.midnight,
    boxShadow: '0 0 20px rgba(0,201,167,0.25)',
    transition: 'all 0.25s ease',
  },
  btnGhost: {
    height: 52,
    padding: '0 32px',
    fontSize: 15,
    fontWeight: 600,
    fontFamily: T.sans,
    borderRadius: 10,
    background: 'transparent',
    border: `1px solid rgba(255,255,255,0.12)`,
    color: C.text,
    transition: 'all 0.25s ease',
  },

  // ── Section wrapper
  section: {
    padding: 'clamp(80px, 10vw, 140px) 24px',
    position: 'relative',
  },
  sectionInner: {
    maxWidth: 1200,
    margin: '0 auto',
  },

  // ── Section headers
  sectionLabel: {
    fontFamily: T.mono,
    fontSize: 12,
    color: C.emerald,
    letterSpacing: '2px',
    textTransform: 'uppercase',
    marginBottom: 16,
  },
  sectionTitle: {
    fontFamily: T.display,
    fontSize: 'clamp(28px, 4vw, 44px)',
    fontWeight: 700,
    letterSpacing: '-1px',
    lineHeight: 1.15,
    color: C.text,
    marginBottom: 16,
  },
  sectionDesc: {
    fontSize: 'clamp(15px, 1.6vw, 18px)',
    color: C.textMuted,
    lineHeight: 1.65,
    maxWidth: 520,
  },

  // ── Stats
  statsRow: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: 2,
    background: C.border,
    borderRadius: 16,
    overflow: 'hidden',
    marginTop: 64,
  },
  statCell: {
    background: C.navy,
    padding: 'clamp(24px, 3vw, 40px)',
    textAlign: 'center',
  },
  statValue: {
    fontFamily: T.mono,
    fontSize: 'clamp(28px, 3.5vw, 48px)',
    fontWeight: 700,
    color: C.text,
    lineHeight: 1.1,
    marginBottom: 8,
  },
  statValueAccent: {
    color: C.emerald,
  },
  statLabel: {
    fontFamily: T.sans,
    fontSize: 'clamp(12px, 1.2vw, 14px)',
    color: C.textDim,
    letterSpacing: '0.3px',
  },

  // ── Features
  featuresGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
    gap: 16,
    marginTop: 64,
  },
  featureCard: {
    background: C.navy,
    border: `1px solid ${C.border}`,
    borderRadius: 16,
    padding: 'clamp(28px, 3vw, 40px)',
    position: 'relative',
    overflow: 'hidden',
    transition: 'border-color 0.3s ease',
  },
  featureIcon: {
    width: 48,
    height: 48,
    borderRadius: 12,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    fontSize: 22,
  },
  featureTitle: {
    fontFamily: T.display,
    fontSize: 'clamp(17px, 1.5vw, 20px)',
    fontWeight: 600,
    color: C.text,
    marginBottom: 12,
    letterSpacing: '-0.3px',
  },
  featureDesc: {
    fontSize: 'clamp(14px, 1.2vw, 15px)',
    color: C.textMuted,
    lineHeight: 1.65,
    marginBottom: 16,
  },
  featureTag: {
    display: 'inline-block',
    fontFamily: T.mono,
    fontSize: 11,
    color: C.emerald,
    background: C.emeraldDim,
    padding: '3px 10px',
    borderRadius: 4,
    letterSpacing: '0.5px',
  },

  // ── CTA
  ctaSection: {
    textAlign: 'center',
    position: 'relative',
    overflow: 'hidden',
  },
  ctaBg: {
    position: 'absolute',
    inset: 0,
    background: `radial-gradient(ellipse 60% 50% at 50% 50%, rgba(0,201,167,0.06) 0%, transparent 70%)`,
    pointerEvents: 'none',
  },

  // ── Footer
  footer: {
    background: C.midnight,
    borderTop: `1px solid ${C.border}`,
    padding: 'clamp(48px, 6vw, 80px) 24px clamp(32px, 4vw, 48px)',
  },
  footerInner: {
    maxWidth: 1200,
    margin: '0 auto',
  },
  footerGrid: {
    display: 'grid',
    gridTemplateColumns: '2fr 1fr 1fr 1fr',
    gap: 'clamp(32px, 4vw, 64px)',
    marginBottom: 48,
  },
  footerColTitle: {
    fontFamily: T.mono,
    fontSize: 12,
    color: C.emerald,
    letterSpacing: '1.5px',
    textTransform: 'uppercase',
    marginBottom: 20,
  },
  footerLink: {
    display: 'block',
    color: C.textMuted,
    textDecoration: 'none',
    fontSize: 14,
    marginBottom: 10,
    transition: 'color 0.2s ease',
    lineHeight: 1.5,
  },
  footerBottom: {
    borderTop: `1px solid ${C.border}`,
    paddingTop: 24,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap' as const,
    gap: 12,
  },

  // ── Modal shared
  modalLogo: {
    width: 48,
    height: 48,
    borderRadius: 12,
    background: C.emeraldDim,
    border: `1px solid rgba(0,201,167,0.2)`,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: '0 auto 20px',
  },
};

// ─── Feature data ────────────────────────────────────────────
const features = [
  {
    icon: <SecurityScanOutlined />,
    color: '#38BDF8',
    title: 'OAuth2 登录接入',
    desc: '支持 GitHub OAuth2 登录与 Session 会话管理，初始化后即可完成统一身份接入。',
    tag: 'GITHUB OAUTH2',
  },
  {
    icon: <SafetyOutlined />,
    color: '#00C9A7',
    title: 'RBAC 权限模型',
    desc: '内置用户、角色、菜单、权限四层管理，并支持细粒度接口权限校验。',
    tag: '细粒度权限',
  },
  {
    icon: <CloudServerOutlined />,
    color: '#A78BFA',
    title: '应用与卡密管理',
    desc: '支持应用、卡密、批次、状态等全链路管理，覆盖常见授权运营场景。',
    tag: '授权运营',
  },
  {
    icon: <SafetyCertificateOutlined />,
    color: '#FBBF24',
    title: '终端用户管理',
    desc: '支持终端用户创建、封禁、密码重置、设备解绑与绑定记录管理。',
    tag: '用户生命周期',
  },
  {
    icon: <LockOutlined />,
    color: '#F472B6',
    title: '应用变量管理',
    desc: '支持变量增删改查、批量更新、导入导出与历史记录追踪，便于配置治理。',
    tag: '配置治理',
  },
  {
    icon: <MonitorOutlined />,
    color: '#22D3EE',
    title: '审计与系统消息',
    desc: '关键操作支持活动日志审计，并可向目标用户推送站内通知消息。',
    tag: '可追踪可通知',
  },
];

const stats = [
  { value: '8+', label: '核心管理模块' },
  { value: '30+', label: '权限控制接口' },
  { value: '20+', label: '覆盖审计操作' },
  { value: '100%', label: '统一返回契约' },
];

// ─── ASCII Gate Art ──────────────────────────────────────────
const gateLeft = `  ┌──────┐
  │ ┌──┐ │
  │ │░░│ │
  │ │░░│ │
  │ └──┘ │
  ├──────┤
  │ ┌──┐ │
  │ │▓▓│ │
  │ │▓▓│ │
  │ └──┘ │
  ├──────┤
  │ ┌──┐ │
  │ │██│ │
  │ │██│ │
  │ └──┘ │
  └──────┘`;

const gateRight = `┌──────┐
│ ┌──┐ │
│ │░░│ │
│ │░░│ │
│ └──┘ │
├──────┤
│ ┌──┐ │
│ │▓▓│ │
│ │▓▓│ │
│ └──┘ │
├──────┤
│ ┌──┐ │
│ │██│ │
│ │██│ │
│ └──┘ │
└──────┘`;

// ─── Component ───────────────────────────────────────────────
const Home: React.FC = () => {
  const [loginModalVisible, setLoginModalVisible] = useState(false);
  const [initModalVisible, setInitModalVisible] = useState(false);
  const [initForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [siteInfo, setSiteInfo] = useState({ icpRecordNo: '', publicSecurityRecordNo: '', icpLicenseNo: '' });
  const [loginMode, setLoginMode] = useState<'github' | 'password'>('github');
  const [passwordLoginForm] = Form.useForm();
  const [passwordLoginLoading, setPasswordLoginLoading] = useState(false);

  const [appUserModalVisible, setAppUserModalVisible] = useState(false);
  const [appUserLoginForm] = Form.useForm();
  const [appUserLoading, setAppUserLoading] = useState(false);
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [captchaId, setCaptchaId] = useState('');
  const [tempToken, setTempToken] = useState('');
  const [appList, setAppList] = useState<any[]>([]);
  const [selectAppVisible, setSelectAppVisible] = useState(false);

  // ── Init check
  useEffect(() => {
    checkSystemInit();
    loadSiteInfo();
    showOAuthErrorMessage();
  }, []);

  const showOAuthErrorMessage = () => {
    const url = new URL(window.location.href);
    const error = url.searchParams.get('error');
    if (error === 'oauth2_failed') {
      message.error('登录失败，请重试');
      url.searchParams.delete('error');
      const clean = `${url.pathname}${url.search}${url.hash}`;
      window.history.replaceState({}, '', clean || '/');
    }
  };

  const checkSystemInit = async () => {
    try {
      const response: any = await systemApi.checkInitStatus();
      const initialized = response?.data?.initialized;
      if (response?.success && initialized === false) {
        setInitModalVisible(true);
      }
    } catch (error) {
      console.error('检查初始化状态失败:', error);
    }
  };

  const loadSiteInfo = async () => {
    try {
      const response = await systemApi.getPublicSiteInfo();
      const data = response?.data || {};
      setSiteInfo({
        icpRecordNo: (data.icpRecordNo || '').trim(),
        publicSecurityRecordNo: (data.publicSecurityRecordNo || '').trim(),
        icpLicenseNo: (data.icpLicenseNo || '').trim()
      });
    } catch {
      setSiteInfo({ icpRecordNo: '', publicSecurityRecordNo: '', icpLicenseNo: '' });
    }
  };

  const handleInitSubmit = async () => {
    try {
      const values = await initForm.validateFields();
      setLoading(true);
      const response: any = await systemApi.initializeSystem({
        clientId: values.clientId,
        clientSecret: values.clientSecret,
        redirectUri: values.redirectUri,
        frontendUrl: values.frontendUrl
      });
      if (response?.success) {
        message.success('系统初始化成功！');
        setInitModalVisible(false);
        initForm.resetFields();
      } else {
        message.error(response?.message || '初始化失败');
      }
    } catch (error: any) {
      if (error.errorFields) {
        message.error('请填写所有必填项');
      } else {
        message.error('初始化失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGithubLogin = async () => {
    try {
      const response: any = await systemApi.getPublicOAuth2Login();
      const url = response?.data?.oauth2AuthorizationUrl as string | undefined;
      if (url) {
        window.location.href = url;
      } else {
        message.error('无法获取登录地址，请检查后端 OAuth 配置');
      }
    } catch {
      // handled by interceptor
    }
  };

  const handlePasswordLogin = async () => {
    try {
      const values = await passwordLoginForm.validateFields();
      setPasswordLoginLoading(true);
      const response: any = await systemApi.passwordLogin({
        login: values.login,
        password: values.password,
      });
      if (response?.success) {
        message.success('登录成功');
        setLoginModalVisible(false);
        passwordLoginForm.resetFields();
        window.location.href = '/dashboard';
      } else {
        message.error(response?.message || '登录失败');
      }
    } catch (error: any) {
      if (error.errorFields) {
        message.error('请填写所有必填项');
      }
    } finally {
      setPasswordLoginLoading(false);
    }
  };

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

  const handleAppUserLogin = () => {
    setAppUserModalVisible(true);
    loadCaptcha();
  };

  const handleAppUserLoginSubmit = async () => {
    try {
      const values = await appUserLoginForm.validateFields();
      setAppUserLoading(true);
      const res: any = await portalRequest.post('/auth/login', {
        email: values.email,
        password: values.password,
        captchaCode: values.captchaCode,
        captchaId: captchaId,
      });
      if (res?.data?.needSelectApp) {
        setTempToken(res.data.token);
        localStorage.setItem('portal_email', values.email);
        setAppList(res.data.apps || []);
        setSelectAppVisible(true);
        setAppUserModalVisible(false);
      } else {
        localStorage.setItem('portal_token', res.data.token);
        localStorage.setItem('portal_email', values.email);
        if (res.data.apps?.[0]) {
          localStorage.setItem('portal_app_id', String(res.data.apps[0].appId));
        }
        message.success('登录成功');
        setAppUserModalVisible(false);
        appUserLoginForm.resetFields();
        window.location.href = '/portal/dashboard';
      }
    } catch (err: any) {
      loadCaptcha();
    } finally {
      setAppUserLoading(false);
    }
  };

  const handleSelectApp = async (appId: number) => {
    try {
      localStorage.setItem('portal_token', tempToken);
      localStorage.setItem('portal_app_id', String(appId));
      const res: any = await portalRequest.post(`/auth/select-app?appId=${appId}`);
      if (res?.data?.token) {
        localStorage.setItem('portal_token', res.data.token);
      }
      message.success('登录成功');
      setSelectAppVisible(false);
      window.location.href = '/portal/dashboard';
    } catch {
      // ignore
    }
  };

  return (
    <Layout style={styles.page}>
      {/* ── Inject keyframes ── */}
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600;700&display=swap');

        @keyframes gateSlideLeft {
          0%   { opacity: 0; transform: translateX(0); }
          30%  { opacity: 1; }
          100% { opacity: 0; transform: translateX(-280px); }
        }
        @keyframes gateSlideRight {
          0%   { opacity: 0; transform: translateX(0); }
          30%  { opacity: 1; }
          100% { opacity: 0; transform: translateX(280px); }
        }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translateY(24px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        @keyframes shimmer {
          0%   { background-position: -200% center; }
          100% { background-position: 200% center; }
        }
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50%      { opacity: 0.5; }
        }

        .home-hero-content {
          animation: fadeInUp 0.8s cubic-bezier(0.23, 1, 0.32, 1) forwards;
        }
        .home-btn-primary:hover {
          background: #00E6BF !important;
          box-shadow: 0 0 32px rgba(0,201,167,0.35) !important;
          transform: translateY(-1px);
        }
        .home-btn-ghost:hover {
          border-color: rgba(0,201,167,0.4) !important;
          color: #00C9A7 !important;
          transform: translateY(-1px);
        }
        .home-feature-card:hover {
          border-color: rgba(0,201,167,0.25) !important;
        }
        .home-feature-card:hover .home-feature-icon {
          transform: scale(1.08);
        }
        .home-feature-icon {
          transition: transform 0.3s ease;
        }
        .home-footer-link:hover {
          color: #00C9A7 !important;
        }
        .home-cta-btn:hover {
          background: #00E6BF !important;
          box-shadow: 0 0 40px rgba(0,201,167,0.35) !important;
          transform: translateY(-2px);
        }

        /* Stats number shimmer on hover */
        .home-stat-value {
          background: linear-gradient(90deg, ${C.text} 40%, ${C.emerald} 50%, ${C.text} 60%);
          background-size: 200% auto;
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }

        /* Responsive grid for stats */
        @media (max-width: 768px) {
          .home-stats-grid {
            grid-template-columns: repeat(2, 1fr) !important;
          }
          .home-footer-grid {
            grid-template-columns: 1fr 1fr !important;
          }
          .home-features-grid {
            grid-template-columns: 1fr !important;
          }
        }
        @media (max-width: 480px) {
          .home-footer-grid {
            grid-template-columns: 1fr !important;
          }
        }

        /* ── Modal dark theme overrides ── */
        /* Use :where() with high specificity to beat Ant Design CSS-in-JS */
        .home-modal.ant-modal .ant-modal-content,
        :where(.home-modal, .home-modal).ant-modal .ant-modal-content {
          background: ${C.navy} !important;
          border: 1px solid ${C.border} !important;
          border-radius: 16px !important;
          box-shadow: 0 24px 48px rgba(0,0,0,0.4) !important;
          color: ${C.text} !important;
        }
        .home-modal.ant-modal .ant-modal-header,
        :where(.home-modal, .home-modal).ant-modal .ant-modal-header {
          background: transparent !important;
          border-bottom: 1px solid ${C.border} !important;
        }
        .home-modal.ant-modal .ant-modal-title,
        :where(.home-modal, .home-modal).ant-modal .ant-modal-title {
          color: ${C.text} !important;
        }

        /* Tabs */
        .home-modal .ant-tabs-tab,
        :where(.home-modal, .home-modal) .ant-tabs-tab {
          color: ${C.textDim} !important;
        }
        .home-modal .ant-tabs-tab-btn,
        :where(.home-modal, .home-modal) .ant-tabs-tab-btn {
          color: ${C.textDim} !important;
        }
        .home-modal .ant-tabs-tab-active .ant-tabs-tab-btn,
        :where(.home-modal, .home-modal) .ant-tabs-tab-active .ant-tabs-tab-btn {
          color: ${C.emerald} !important;
        }
        .home-modal .ant-tabs-ink-bar,
        :where(.home-modal, .home-modal) .ant-tabs-ink-bar {
          background: ${C.emerald} !important;
        }
        .home-modal .ant-tabs-nav::before,
        :where(.home-modal, .home-modal) .ant-tabs-nav::before {
          border-bottom-color: ${C.border} !important;
        }
        .home-modal .ant-tabs-content-holder,
        :where(.home-modal, .home-modal) .ant-tabs-content-holder {
          background: transparent !important;
        }

        /* Inputs — override ALL Ant Design input selectors with max specificity */
        .home-modal .ant-input,
        .home-modal .ant-input-affix-wrapper,
        .home-modal input.ant-input,
        .home-modal input[type="text"],
        .home-modal input[type="password"],
        .home-modal .ant-input-password .ant-input,
        :where(.home-modal, .home-modal) .ant-input,
        :where(.home-modal, .home-modal) .ant-input-affix-wrapper,
        :where(.home-modal, .home-modal) input.ant-input,
        :where(.home-modal, .home-modal) input[type="text"],
        :where(.home-modal, .home-modal) input[type="password"],
        :where(.home-modal, .home-modal) .ant-input-password .ant-input {
          background: ${C.slate} !important;
          border-color: ${C.border} !important;
          color: ${C.text} !important;
          box-shadow: none !important;
        }
        .home-modal .ant-input::placeholder,
        .home-modal .ant-input-affix-wrapper::placeholder,
        .home-modal input::placeholder,
        :where(.home-modal, .home-modal) .ant-input::placeholder,
        :where(.home-modal, .home-modal) .ant-input-affix-wrapper::placeholder,
        :where(.home-modal, .home-modal) input::placeholder {
          color: ${C.textDim} !important;
        }
        .home-modal .ant-input:focus,
        .home-modal .ant-input-focused,
        .home-modal .ant-input-affix-wrapper:focus,
        .home-modal .ant-input-affix-wrapper-focused,
        .home-modal input:focus,
        :where(.home-modal, .home-modal) .ant-input:focus,
        :where(.home-modal, .home-modal) .ant-input-focused,
        :where(.home-modal, .home-modal) .ant-input-affix-wrapper:focus,
        :where(.home-modal, .home-modal) .ant-input-affix-wrapper-focused,
        :where(.home-modal, .home-modal) input:focus {
          border-color: ${C.emerald} !important;
          box-shadow: 0 0 0 2px rgba(0,201,167,0.15) !important;
        }
        .home-modal .ant-input-prefix,
        :where(.home-modal, .home-modal) .ant-input-prefix {
          color: ${C.textDim} !important;
        }

        /* Form labels */
        .home-modal .ant-form-item-label > label,
        :where(.home-modal, .home-modal) .ant-form-item-label > label {
          color: ${C.textMuted} !important;
        }

        /* Form errors */
        .home-modal .ant-form-item-explain-error,
        :where(.home-modal, .home-modal) .ant-form-item-explain-error {
          color: #F87171 !important;
        }

        /* Modal close button */
        .home-modal .ant-modal-close,
        :where(.home-modal, .home-modal) .ant-modal-close {
          color: ${C.textDim} !important;
        }
        .home-modal .ant-modal-close:hover,
        :where(.home-modal, .home-modal) .ant-modal-close:hover {
          color: ${C.text} !important;
        }

        /* OK/Cancel buttons in init modal */
        .home-modal .ant-btn-primary:not(.ant-btn-dangerous),
        :where(.home-modal, .home-modal) .ant-btn-primary:not(.ant-btn-dangerous) {
          background: ${C.emerald} !important;
          border-color: ${C.emerald} !important;
          color: ${C.midnight} !important;
        }

        /* Divider inside modal */
        .home-modal .ant-divider,
        :where(.home-modal, .home-modal) .ant-divider {
          border-color: ${C.border} !important;
        }

        /* Select-like elements */
        .home-modal .ant-select-selector,
        :where(.home-modal, .home-modal) .ant-select-selector {
          background: ${C.slate} !important;
          border-color: ${C.border} !important;
          color: ${C.text} !important;
        }

        /* Reduced motion */
        @media (prefers-reduced-motion: reduce) {
          .home-hero-content,
          .home-gate-left,
          .home-gate-right {
            animation: none !important;
            opacity: 1 !important;
            transform: none !important;
          }
        }

        /* 移动端按钮文字切换 */
        .btn-app-text-mobile { display: none; }
        .btn-app-text-desktop { display: inline; }
        .btn-dev-text-mobile { display: none; }
        .btn-dev-text-desktop { display: inline; }
        @media (max-width: 768px) {
          .btn-app-text-mobile { display: inline; }
          .btn-app-text-desktop { display: none; }
          .btn-dev-text-mobile { display: inline; }
          .btn-dev-text-desktop { display: none; }
        }
      `}</style>

      {/* ── Header ── */}
      <Header style={styles.header}>
        <div style={styles.headerInner}>
          <a href="/" style={styles.logo}>
            <img src="/favicon.svg" alt="CipherGate" style={{ width: 28, height: 28 }} />
            <span>CipherGate</span>
          </a>

          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <a
              href={import.meta.env.VITE_DOCS_URL || '/docs/'}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                color: C.textMuted,
                fontSize: 14,
                textDecoration: 'none',
                padding: '0 12px',
                height: 40,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              使用文档
            </a>
          </div>
        </div>
      </Header>

      <Content>
        {/* ════════════════════════════════════════════════════ HERO */}
        <section style={styles.hero}>
          <div style={styles.heroBg} />
          <div style={styles.heroGrid} />

          {/* ASCII Gate animation */}
          <div style={styles.gateContainer}>
            <pre style={{ ...styles.gateLeft } as any} className="home-gate-left">{gateLeft}</pre>
            <pre style={{ ...styles.gateRight } as any} className="home-gate-right">{gateRight}</pre>
          </div>

          <div style={styles.heroContent} className="home-hero-content">
            <div style={styles.eyebrow}>
              <span style={{ animation: 'pulse 2s ease-in-out infinite' }}>●</span>
              Enterprise Authorization Platform
            </div>

            <h1 style={styles.heroTitle}>
              企业级授权与配置
              <br />
              <span style={styles.heroTitleAccent}>统一管理平台</span>
            </h1>

            <p style={styles.heroDesc}>
              入驻成为开发者，开发和管理自己的应用。
              一站式解决授权分发、用户管理、卡密运营与安全审计。
            </p>

            <div style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap' }}>
              <Button
                type="primary"
                className="home-btn-primary"
                style={styles.btnPrimary}
                icon={<UserOutlined />}
                onClick={handleAppUserLogin}
              >
                <span className="btn-app-text-mobile">应用</span>
                <span className="btn-app-text-desktop">应用用户登录</span>
              </Button>
              <Button
                className="home-btn-ghost"
                style={styles.btnGhost}
                onClick={() => setLoginModalVisible(true)}
              >
                <span className="btn-dev-text-mobile">开发者</span>
                <span className="btn-dev-text-desktop">开发者登录</span>
              </Button>
              <Button
                className="home-btn-ghost"
                style={styles.btnGhost}
                icon={<GithubOutlined />}
                onClick={() => window.open('https://github.com/AYssu/CipherGate', '_blank', 'noopener,noreferrer')}
              >
                GitHub
              </Button>
            </div>
          </div>
        </section>

        {/* ════════════════════════════════════════════════════ STATS */}
        <section style={{ ...styles.section, background: C.navy }}>
          <div style={styles.sectionInner}>
            <div className="home-stats-grid" style={styles.statsRow}>
              {stats.map((s, i) => (
                <div key={i} style={styles.statCell}>
                  <div className="home-stat-value" style={styles.statValue}>{s.value}</div>
                  <div style={styles.statLabel}>{s.label}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ════════════════════════════════════════════════════ FEATURES */}
        <section style={styles.section}>
          <div style={styles.sectionInner}>
            <div style={styles.sectionLabel}>// CAPABILITIES</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 24 }}>
              <div>
                <h2 style={styles.sectionTitle}>核心产品能力</h2>
                <p style={styles.sectionDesc}>
                  聚焦"可落地"的授权与管理能力，覆盖鉴权、权限、配置、审计与消息通知等核心场景。
                </p>
              </div>
              <img src={safeIcon} alt="Security" style={{ width: 64, height: 64, opacity: 0.6 }} />
            </div>

            <div className="home-features-grid" style={styles.featuresGrid}>
              {features.map((f, i) => (
                <div key={i} className="home-feature-card" style={styles.featureCard}>
                  <div
                    className="home-feature-icon"
                    style={{
                      ...styles.featureIcon,
                      background: `${f.color}15`,
                      color: f.color,
                    }}
                  >
                    {f.icon}
                  </div>
                  <h3 style={styles.featureTitle}>{f.title}</h3>
                  <p style={styles.featureDesc}>{f.desc}</p>
                  <span style={styles.featureTag}>{f.tag}</span>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ════════════════════════════════════════════════════ CTA */}
        <section style={{ ...styles.section, ...styles.ctaSection }}>
          <div style={styles.ctaBg} />
          <div style={{ ...styles.sectionInner, position: 'relative', zIndex: 1 }}>
            <div style={{ ...styles.sectionLabel, textAlign: 'center' }}>// GET STARTED</div>
            <h2 style={{ ...styles.sectionTitle, textAlign: 'center', maxWidth: 600, margin: '0 auto 16px' }}>
              开启您的应用安全之旅
            </h2>
            <p style={{ ...styles.sectionDesc, textAlign: 'center', maxWidth: 480, margin: '0 auto 48px' }}>
              立即入驻，免费体验 CipherGate 全部能力，让授权管理更安全、更高效。
            </p>
            <div style={{ textAlign: 'center' }}>
              <Button
                type="primary"
                className="home-cta-btn"
                style={{
                  ...styles.btnPrimary,
                  height: 56,
                  padding: '0 48px',
                  fontSize: 16,
                }}
                onClick={() => setLoginModalVisible(true)}
              >
                立即入驻 →
              </Button>
            </div>
          </div>
        </section>
      </Content>

      {/* ════════════════════════════════════════════════════ FOOTER */}
      <Footer style={styles.footer}>
        <div style={styles.footerInner}>
          <div className="home-footer-grid" style={styles.footerGrid}>
            <div>
              <div style={{ ...styles.logo, fontSize: 18, marginBottom: 16 }}>
                <img src="/favicon.svg" alt="CipherGate" style={{ width: 24, height: 24 }} />
                CipherGate
              </div>
              <p style={{ color: C.textDim, fontSize: 14, lineHeight: 1.7, maxWidth: 280 }}>
                专业的网络安全解决方案提供商，致力于为企业构建安全可信的数字化环境。
              </p>
            </div>
            <div>
              <div style={styles.footerColTitle}>快速入口</div>
              <a href="#features" className="home-footer-link" style={styles.footerLink}>产品能力</a>
              <a href="#about" className="home-footer-link" style={styles.footerLink}>关于我们</a>
              <a href="#contact" className="home-footer-link" style={styles.footerLink}>商务咨询</a>
              <a href="#login" className="home-footer-link" style={styles.footerLink}>登录平台</a>
            </div>
            <div>
              <div style={styles.footerColTitle}>资源中心</div>
              <a href={import.meta.env.VITE_DOCS_URL || '/docs/'} target="_blank" rel="noopener noreferrer" className="home-footer-link" style={styles.footerLink}>使用文档</a>
              <a href="#" className="home-footer-link" style={styles.footerLink}>更新日志</a>
              <a href="#" className="home-footer-link" style={styles.footerLink}>常见问题</a>
              <a href="#" className="home-footer-link" style={styles.footerLink}>服务条款</a>
            </div>
            <div>
              <div style={styles.footerColTitle}>获取支持</div>
              <p style={{ color: C.textDim, fontSize: 14, lineHeight: 1.7 }}>
                建议通过页面上的「登录」入口提交工单。企业合作与售前咨询请联系管理员配置的官方渠道。
              </p>
            </div>
          </div>

          <div style={styles.footerBottom}>
            <span style={{ color: C.textDim, fontSize: 13 }}>
              ©{new Date().getFullYear()} CipherGate — Created by AYssu
            </span>
            <span style={{ color: C.textDim, fontSize: 12, fontFamily: T.mono }}>
              {siteInfo.publicSecurityRecordNo && <span>{siteInfo.publicSecurityRecordNo}</span>}
              {siteInfo.icpLicenseNo && <span> · {siteInfo.icpLicenseNo}</span>}
              {siteInfo.icpRecordNo && <span> · {siteInfo.icpRecordNo}</span>}
            </span>
          </div>
        </div>
      </Footer>

      {/* ════════════════════════════════════════════════════ LOGIN MODAL */}
      <Modal
        open={loginModalVisible}
        onCancel={() => setLoginModalVisible(false)}
        footer={null}
        width={420}
        centered
        closable={false}
        maskClosable
        className="home-modal"
        styles={{
          content: {
            background: C.navy,
            borderRadius: 16,
            border: `1px solid ${C.border}`,
            boxShadow: '0 24px 48px rgba(0,0,0,0.4)',
          },
          body: { padding: 'clamp(24px, 4vw, 40px)' },
        }}
      >
          {/* Close button */}
          <Button
            type="text"
            onClick={() => setLoginModalVisible(false)}
            style={{
              position: 'absolute', top: 12, right: 12,
              color: C.textDim, fontSize: 18, width: 32, height: 32,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              border: 'none', background: 'transparent', zIndex: 1,
            }}
          >
            ×
          </Button>

          {/* Logo */}
          <div style={styles.modalLogo}>
            <LockOutlined style={{ fontSize: 20, color: C.emerald }} />
          </div>
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <Title level={4} style={{ color: C.text, margin: 0, fontWeight: 600, fontFamily: T.display }}>
              登录到 CipherGate
            </Title>
            <Text style={{ color: C.textDim, fontSize: 13, marginTop: 4, display: 'block' }}>
              选择登录方式
            </Text>
          </div>

          <Tabs
            activeKey={loginMode}
            onChange={(key) => setLoginMode(key as 'github' | 'password')}
            centered
            items={[
              {
                key: 'github',
                label: <span><GithubOutlined style={{ marginRight: 6 }} />GitHub</span>,
                children: (
                  <Button
                    type="primary"
                    size="large"
                    icon={<GithubOutlined />}
                    onClick={handleGithubLogin}
                    style={{
                      width: '100%', height: 44, fontSize: 15, fontWeight: 500,
                      borderRadius: 10, background: '#24292e', borderColor: '#24292e',
                    }}
                  >
                    Continue with GitHub
                  </Button>
                ),
              },
              {
                key: 'password',
                label: <span><KeyOutlined style={{ marginRight: 6 }} />密码登录</span>,
                children: (
                  <Form
                    form={passwordLoginForm}
                    layout="vertical"
                    onFinish={handlePasswordLogin}
                    style={{ marginBottom: 0 }}
                  >
                    <Form.Item name="login" rules={[{ required: true, message: '请输入 GitHub 账号名' }]} style={{ marginBottom: 12 }}>
                      <Input
                        prefix={<UserOutlined style={{ color: C.textDim }} />}
                        placeholder="GitHub 账号名"
                        size="large"
                        autoComplete="username"
                        style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }}
                      />
                    </Form.Item>
                    <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]} style={{ marginBottom: 16 }}>
                      <Input.Password
                        prefix={<KeyOutlined style={{ color: C.textDim }} />}
                        placeholder="密码"
                        size="large"
                        autoComplete="current-password"
                        style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }}
                      />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0 }}>
                      <Button
                        type="primary"
                        htmlType="submit"
                        loading={passwordLoginLoading}
                        block
                        size="large"
                        style={{ height: 44, fontWeight: 600, borderRadius: 8, background: C.emerald, border: 'none', color: C.midnight }}
                      >
                        登录
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />

          <div style={{ textAlign: 'center', marginTop: 20 }}>
            {loginMode === 'password' && (
              <Text style={{ display: 'block', marginBottom: 4, color: C.amber, fontSize: 12 }}>
                提示：密码登录需要先通过 GitHub 登录并设置密码
              </Text>
            )}
            <Text style={{ color: C.textDim, fontSize: 12 }}>
              点击登录即表示您同意我们的{' '}
              <a href="#" style={{ color: C.emerald, textDecoration: 'none' }}>服务条款</a>
              {' '}和{' '}
              <a href="#" style={{ color: C.emerald, textDecoration: 'none' }}>隐私政策</a>
            </Text>
          </div>
        </Modal>

      {/* ════════════════════════════════════════════════════ INIT MODAL */}
      <Modal
        open={initModalVisible}
        onOk={handleInitSubmit}
        onCancel={() => {}}
        closable={false}
        maskClosable={false}
        width={500}
        centered
        className="home-modal"
        okText="完成初始化"
        okButtonProps={{ size: 'large', style: { background: C.emerald, border: 'none', color: C.midnight, fontWeight: 600 } }}
        cancelButtonProps={{ style: { display: 'none' } }}
        confirmLoading={loading}
        styles={{
          content: {
            background: C.navy,
            borderRadius: 16,
            border: `1px solid ${C.border}`,
            boxShadow: '0 24px 48px rgba(0,0,0,0.4)',
          },
          body: { padding: 'clamp(24px, 4vw, 40px)' },
        }}
      >
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={styles.modalLogo}>
              <SettingOutlined style={{ fontSize: 20, color: C.emerald }} />
            </div>
            <Title level={4} style={{ color: C.text, margin: '0 0 8px', fontWeight: 600, fontFamily: T.display }}>
              系统初始化配置
            </Title>
            <Text style={{ color: C.textDim, fontSize: 14 }}>
              首次使用需要配置 GitHub OAuth2 认证信息
            </Text>
          </div>

          <Form
            form={initForm}
            layout="vertical"
            initialValues={{
              redirectUri: 'http://localhost:8080/login/oauth2/code/github',
              frontendUrl: 'http://localhost:5173/dashboard',
            }}
          >
            <Form.Item label={<span style={{ color: C.textMuted }}>GitHub OAuth2 Client ID</span>} name="clientId" rules={[{ required: true, message: '请输入 Client ID' }]}>
              <Input placeholder="请输入 GitHub OAuth2 Client ID" size="large" style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }} />
            </Form.Item>
            <Form.Item label={<span style={{ color: C.textMuted }}>GitHub OAuth2 Client Secret</span>} name="clientSecret" rules={[{ required: true, message: '请输入 Client Secret' }]}>
              <Input.Password placeholder="请输入 GitHub OAuth2 Client Secret" size="large" style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }} />
            </Form.Item>
            <Form.Item label={<span style={{ color: C.textMuted }}>Redirect URI</span>} name="redirectUri" rules={[{ required: true, message: '请输入 Redirect URI' }]} tooltip="GitHub 回调地址，必须是后端地址">
              <Input placeholder="请输入 Redirect URI" size="large" style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }} />
            </Form.Item>
            <Form.Item label={<span style={{ color: C.textMuted }}>前端地址</span>} name="frontendUrl" rules={[{ required: true, message: '请输入前端地址' }]} tooltip="登录成功后重定向的前端地址">
              <Input placeholder="请输入前端地址" size="large" style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }} />
            </Form.Item>
          </Form>

          <Text style={{ color: C.textDim, fontSize: 12 }}>
            提示：请确保在 GitHub OAuth App 设置中配置了正确的 Redirect URI
          </Text>
        </Modal>

      {/* ════════════════════════════════════════════════════ APP USER LOGIN MODAL */}
      <Modal
        open={appUserModalVisible}
        onCancel={() => { setAppUserModalVisible(false); appUserLoginForm.resetFields(); }}
        footer={null}
        width={420}
        centered
        closable
        maskClosable
        className="home-modal"
        styles={{
          content: { background: C.navy, borderRadius: 16, border: `1px solid ${C.border}`, boxShadow: '0 24px 48px rgba(0,0,0,0.4)' },
          body: { padding: 'clamp(24px, 4vw, 40px)' },
        }}
      >
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={styles.modalLogo}>
              <UserOutlined style={{ fontSize: 20, color: C.emerald }} />
            </div>
            <Title level={4} style={{ color: C.text, margin: '0 0 8px', fontWeight: 600, fontFamily: T.display }}>
              应用用户登录
            </Title>
          </div>

          <Form form={appUserLoginForm} layout="vertical" onFinish={handleAppUserLoginSubmit} autoComplete="off">
            <Form.Item
              name="email"
              rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input
                prefix={<MailOutlined style={{ color: C.textDim }} />}
                placeholder="邮箱地址"
                size="large"
                style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }}
              />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password
                prefix={<LockOutlined style={{ color: C.textDim }} />}
                placeholder="密码"
                size="large"
                autoComplete="current-password"
                style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }}
              />
            </Form.Item>
            <Form.Item name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
              <div style={{ position: 'relative' }}>
                <Input
                  prefix={<KeyOutlined style={{ color: C.textDim }} />}
                  placeholder="验证码"
                  size="large"
                  className="captcha-input"
                  style={{ background: C.slate, border: `1px solid ${C.border}`, color: C.text, borderRadius: 8 }}
                />
                <div
                  onClick={loadCaptcha}
                  style={{
                    position: 'absolute',
                    right: 6,
                    top: 1,
                    bottom: 1,
                    width: 100,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: C.slate,
                    borderRadius: '0 6px 6px 0',
                    overflow: 'hidden',
                    zIndex: 1,
                  }}
                >
                  {captchaUrl && <img src={captchaUrl} alt="验证码" style={{ height: 32, width: '100%', display: 'block', borderRadius: '0 6px 6px 0' }} />}
                </div>
              </div>
            </Form.Item>
            <Form.Item style={{ marginBottom: 12 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={appUserLoading}
                block
                size="large"
                style={{ height: 44, fontWeight: 600, borderRadius: 8, background: C.emerald, border: 'none', color: C.midnight }}
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <a href="/portal/recovery" style={{ color: C.emerald, fontSize: 13 }}>忘记密码？</a>
          </div>
        </Modal>

      {/* ════════════════════════════════════════════════════ APP SELECT MODAL */}
      <Modal
        open={selectAppVisible}
        onCancel={() => setSelectAppVisible(false)}
        footer={null}
        width={420}
        centered
        closable
        maskClosable
        className="home-modal"
        styles={{
          content: { background: C.navy, borderRadius: 16, border: `1px solid ${C.border}`, boxShadow: '0 24px 48px rgba(0,0,0,0.4)' },
          body: { padding: 'clamp(24px, 4vw, 40px)' },
        }}
      >
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={styles.modalLogo}>
              <SafetyCertificateOutlined style={{ fontSize: 20, color: C.emerald }} />
            </div>
            <Title level={4} style={{ color: C.text, margin: '0 0 8px', fontWeight: 600, fontFamily: T.display }}>
              选择应用
            </Title>
            <Text style={{ color: C.textDim, fontSize: 14 }}>
              您的账号绑定了多个应用，请选择要登录的应用
            </Text>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {appList.map((app: any) => (
              <Button
                key={app.appId}
                size="large"
                icon={<SafetyCertificateOutlined />}
                onClick={() => handleSelectApp(app.appId)}
                style={{
                  height: 52, textAlign: 'left', borderRadius: 10,
                  border: `1px solid ${C.border}`, fontSize: 15,
                  background: C.slate, color: C.text,
                }}
              >
                {app.appName || `应用 #${app.appId}`}
              </Button>
            ))}
          </div>
        </Modal>
    </Layout>
  );
};

export default Home;
