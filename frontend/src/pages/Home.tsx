import React, { useState, useEffect, useCallback } from 'react';
import { Layout, Menu, Button, Card, Row, Col, Typography, Space, Statistic, Divider, Modal, Form, Input, message, Tabs } from 'antd';
import {
  SecurityScanOutlined,
  SafetyOutlined,
  CloudServerOutlined,
  SafetyCertificateOutlined,
  LockOutlined,
  MonitorOutlined,
  RightOutlined,
  CheckCircleOutlined,
  TeamOutlined,
  MenuOutlined,
  GithubOutlined,
  SettingOutlined,
  UserOutlined,
  KeyOutlined,
  MailOutlined
} from '@ant-design/icons';
import { systemApi } from '../services';
import safeIcon from '../assets/icons/safe.svg';
import portalRequest from '../portal/services/portalRequest';

const { Header, Content, Footer } = Layout;
const { Title, Paragraph, Text } = Typography;

const Home: React.FC = () => {
  const [loginModalVisible, setLoginModalVisible] = useState(false);
  const [initModalVisible, setInitModalVisible] = useState(false);
  const [initForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [siteInfo, setSiteInfo] = useState({
    icpRecordNo: '',
    publicSecurityRecordNo: '',
    icpLicenseNo: ''
  });
  const [loginMode, setLoginMode] = useState<'github' | 'password'>('github');
  const [passwordLoginForm] = Form.useForm();
  const [passwordLoginLoading, setPasswordLoginLoading] = useState(false);

  // 应用用户登录相关状态
  const [appUserModalVisible, setAppUserModalVisible] = useState(false);
  const [appUserLoginForm] = Form.useForm();
  const [appUserLoading, setAppUserLoading] = useState(false);
  const [captchaUrl, setCaptchaUrl] = useState('');
  const [captchaId, setCaptchaId] = useState('');
  const [tempToken, setTempToken] = useState('');
  const [appList, setAppList] = useState<any[]>([]);
  const [selectAppVisible, setSelectAppVisible] = useState(false);

  // 检查系统是否已初始化
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
        // 系统未初始化，显示配置弹窗
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
      setSiteInfo({
        icpRecordNo: '',
        publicSecurityRecordNo: '',
        icpLicenseNo: ''
      });
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
        console.log('[OAuth2 Redirect URL]', url);
        window.location.href = url;
      } else {
        message.error('无法获取登录地址，请检查后端 OAuth 配置');
      }
    } catch {
      // 错误提示由 request 拦截器处理
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
        // 跳转到仪表板
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

  const showLoginModal = () => {
    setLoginModalVisible(true);
  };

  const handleCancel = () => {
    setLoginModalVisible(false);
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
  const features = [
    {
      icon: <SecurityScanOutlined style={{ fontSize: 48, color: '#1890ff' }} />,
      title: 'OAuth2 登录接入',
      description: '支持 GitHub OAuth2 登录与 Session 会话管理，初始化后即可完成统一身份接入。',
      highlight: 'GitHub OAuth2'
    },
    {
      icon: <SafetyOutlined style={{ fontSize: 48, color: '#52c41a' }} />,
      title: 'RBAC 权限模型',
      description: '内置用户、角色、菜单、权限四层管理，并支持细粒度接口权限校验。',
      highlight: '细粒度权限'
    },
    {
      icon: <CloudServerOutlined style={{ fontSize: 48, color: '#722ed1' }} />,
      title: '应用与卡密管理',
      description: '支持应用、卡密、批次、状态等全链路管理，覆盖常见授权运营场景。',
      highlight: '授权运营'
    },
    {
      icon: <SafetyCertificateOutlined style={{ fontSize: 48, color: '#fa8c16' }} />,
      title: '终端用户管理',
      description: '支持终端用户创建、封禁、密码重置、设备解绑与绑定记录管理。',
      highlight: '用户生命周期'
    },
    {
      icon: <LockOutlined style={{ fontSize: 48, color: '#eb2f96' }} />,
      title: '应用变量管理',
      description: '支持变量增删改查、批量更新、导入导出与历史记录追踪，便于配置治理。',
      highlight: '配置治理'
    },
    {
      icon: <MonitorOutlined style={{ fontSize: 48, color: '#13c2c2' }} />,
      title: '审计与系统消息',
      description: '关键操作支持活动日志审计，并可向目标用户推送站内通知消息。',
      highlight: '可追踪可通知'
    },
  ];
  const stats = [
    { title: '核心管理模块', value: 8, suffix: '+', prefix: <TeamOutlined /> },
    { title: '权限控制接口', value: 30, suffix: '+', prefix: <SafetyOutlined /> },
    { title: '已覆盖审计操作', value: 20, suffix: '+', prefix: <SecurityScanOutlined /> },
    { title: '统一返回契约', value: 100, suffix: '%', prefix: <CheckCircleOutlined /> },
  ];

  return (
    <Layout style={{ minHeight: '100vh', background: '#fff' }}>
      {/* Fixed Header */}
      <Header style={{ 
        background: 'rgba(255, 255, 255, 0.95)', 
        boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
        borderBottom: '1px solid #f0f0f0',
        position: 'fixed',
        width: '100%',
        zIndex: 1000,
        backdropFilter: 'blur(10px)',
        padding: '0'
      }}>
        <div style={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center',
          maxWidth: 1400,
          margin: '0 auto',
          padding: '0 16px'
        }}>
          <div style={{ 
            fontSize: window.innerWidth < 768 ? 20 : 28, 
            fontWeight: 700, 
            background: 'linear-gradient(135deg, #00d4aa, #1890ff)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            display: 'flex',
            alignItems: 'center',
            letterSpacing: '-0.5px'
          }}>
            <img 
              src="/favicon.svg" 
              alt="CipherGate Logo" 
              style={{ 
                marginRight: window.innerWidth < 768 ? 8 : 12, 
                width: window.innerWidth < 768 ? 32 : 40,
                height: window.innerWidth < 768 ? 32 : 40
              }} 
            />
            CipherGate
          </div>
          
          {/* 桌面端菜单 */}
          <div style={{ display: window.innerWidth < 768 ? 'none' : 'block' }}>
            <Menu 
              mode="horizontal" 
              style={{ 
                border: 'none', 
                background: 'transparent',
                fontSize: 16,
                fontWeight: 500
              }}
              items={[
                { key: 'home', label: '首页' },
                { key: 'products', label: '产品与服务' },
                { key: 'solutions', label: '解决方案' },
                { key: 'cases', label: '成功案例' },
                { key: 'about', label: '关于我们' }
              ]}
            />
          </div>
          
          {/* 移动端菜单按钮 */}
          <div style={{ display: window.innerWidth < 768 ? 'block' : 'none' }}>
            <Button type="text" icon={<MenuOutlined />} size="large" />
          </div>
          
          {/* 桌面端按钮 */}
          <div style={{ display: window.innerWidth < 768 ? 'none' : 'block' }}>
            <Space size="middle">
              <Button
                type="text"
                size="large"
                style={{ fontWeight: 500 }}
                onClick={showLoginModal}
              >
                开发者登录
              </Button>
              <Button type="primary" size="large" style={{
                fontWeight: 600,
                height: 44,
                padding: '0 24px',
                borderRadius: 8
              }} onClick={showLoginModal}>
                入驻开发者
              </Button>
            </Space>
          </div>
        </div>
      </Header>
      <Content style={{ marginTop: 64, padding: 0 }}>
        {/* Hero Section */}
        <div style={{ 
          background: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',
          color: 'white',
          minHeight: window.innerWidth < 768 ? 'calc(100vh - 64px)' : 'calc(100vh - 64px)',
          padding: window.innerWidth < 768 ? '96px 0 48px' : '120px 0 64px',
          position: 'relative',
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center'
        }}>
          {/* 动态网格背景 */}
          <div style={{ 
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'url("data:image/svg+xml,%3Csvg width="60" height="60" viewBox="0 0 60 60" xmlns="http://www.w3.org/2000/svg"%3E%3Cg fill="none" fill-rule="evenodd"%3E%3Cg fill="%2300d4aa" fill-opacity="0.05"%3E%3Cpath d="M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z"/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")',
            opacity: 0.3
          }} />
          
          {/* 渐变光晕效果 */}
          <div style={{ 
            position: 'absolute',
            top: '-50%',
            right: '-10%',
            width: '600px',
            height: '600px',
            background: 'radial-gradient(circle, rgba(0, 212, 170, 0.15) 0%, transparent 70%)',
            borderRadius: '50%',
            filter: 'blur(60px)',
            animation: 'float 8s ease-in-out infinite'
          }} />
          
          <div style={{ 
            position: 'absolute',
            bottom: '-30%',
            left: '-5%',
            width: '500px',
            height: '500px',
            background: 'radial-gradient(circle, rgba(24, 144, 255, 0.15) 0%, transparent 70%)',
            borderRadius: '50%',
            filter: 'blur(60px)',
            animation: 'float 10s ease-in-out infinite reverse'
          }} />
          
          {/* 装饰性3D魔方 - 仅桌面端显示 */}
          {window.innerWidth >= 768 && (
            <div style={{ 
              position: 'absolute',
              top: '20%',
              left: '20%',
              perspective: '1000px',
              zIndex: 1
            }}>
              <div style={{
                width: 100,
                height: 100,
                position: 'relative',
                transformStyle: 'preserve-3d',
                animation: 'rotateCube 20s infinite linear'
              }}>
                {/* 魔方的6个面 */}
                {[
                  { transform: 'rotateY(0deg) translateZ(50px)', bg: 'linear-gradient(135deg, #00d4aa, #0ba360)' },
                  { transform: 'rotateY(90deg) translateZ(50px)', bg: 'linear-gradient(135deg, #1890ff, #0066cc)' },
                  { transform: 'rotateY(180deg) translateZ(50px)', bg: 'linear-gradient(135deg, #00d4aa, #1890ff)' },
                  { transform: 'rotateY(-90deg) translateZ(50px)', bg: 'linear-gradient(135deg, #0ba360, #00d4aa)' },
                  { transform: 'rotateX(90deg) translateZ(50px)', bg: 'linear-gradient(135deg, #1890ff, #00d4aa)' },
                  { transform: 'rotateX(-90deg) translateZ(50px)', bg: 'linear-gradient(135deg, #0066cc, #0ba360)' }
                ].map((face, index) => (
                  <div
                    key={index}
                    style={{
                      position: 'absolute',
                      width: 100,
                      height: 100,
                      background: face.bg,
                      border: '1px solid rgba(255,255,255,0.2)',
                      borderRadius: 8,
                      transform: face.transform,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      boxShadow: '0 0 15px rgba(0,212,170,0.3)',
                      backdropFilter: 'blur(10px)'
                    }}
                  >
                    {/* 每个面上的小方块网格 */}
                    <div style={{
                      display: 'grid',
                      gridTemplate: 'repeat(3, 1fr) / repeat(3, 1fr)',
                      gap: 3,
                      width: '70%',
                      height: '70%'
                    }}>
                      {Array.from({ length: 9 }).map((_, i) => (
                        <div
                          key={i}
                          style={{
                            background: 'rgba(255,255,255,0.3)',
                            borderRadius: 1,
                            border: '1px solid rgba(255,255,255,0.2)',
                            animation: `pulse ${2 + (i * 0.1)}s ease-in-out infinite alternate`
                          }}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          <div style={{ 
            position: 'absolute',
            bottom: '15%',
            right: '15%',
            width: '80px',
            height: '80px',
            border: '2px solid rgba(24, 144, 255, 0.2)',
            borderRadius: '50%',
            animation: 'pulse 4s ease-in-out infinite'
          }} />
          
          <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 16px', position: 'relative' }}>
            <Row align="middle" gutter={[24, 48]}>
              <Col xs={24} lg={14}>
                <div className="hero-content">
                  <Title level={1} style={{ 
                    color: 'white', 
                    fontSize: window.innerWidth < 768 ? 32 : window.innerWidth < 1024 ? 42 : 56, 
                    marginBottom: window.innerWidth < 768 ? 16 : 24,
                    fontWeight: 700,
                    lineHeight: 1.2,
                    letterSpacing: '-1px',
                    textAlign: window.innerWidth < 768 ? 'center' : 'left'
                  }}>
                    企业级授权与配置
                    <br />
                    <span style={{ color: '#00d4aa' }}>统一管理平台</span>
                  </Title>
                  
                  <Paragraph style={{
                    color: 'rgba(255,255,255,0.9)',
                    fontSize: window.innerWidth < 768 ? 16 : 20,
                    marginBottom: window.innerWidth < 768 ? 32 : 48,
                    lineHeight: 1.6,
                    maxWidth: 600,
                    textAlign: window.innerWidth < 768 ? 'center' : 'left',
                    margin: window.innerWidth < 768 ? '0 auto 32px' : '0 0 48px 0'
                  }}>
                    入驻成为开发者，开发和管理自己的应用。<br />
                    一站式解决授权分发、用户管理、卡密运营与安全审计。
                  </Paragraph>

                  <div style={{
                    display: 'flex',
                    flexDirection: window.innerWidth < 768 ? 'column' : 'row',
                    gap: window.innerWidth < 768 ? 16 : 24,
                    alignItems: 'center',
                    justifyContent: window.innerWidth < 768 ? 'center' : 'flex-start'
                  }}>
                    <Button
                      type="primary"
                      size="large"
                      style={{
                        height: window.innerWidth < 768 ? 48 : 56,
                        padding: window.innerWidth < 768 ? '0 24px' : '0 32px',
                        fontSize: window.innerWidth < 768 ? 16 : 18,
                        fontWeight: 600,
                        borderRadius: 8,
                        background: '#00d4aa',
                        borderColor: '#00d4aa',
                        color: 'white',
                        width: window.innerWidth < 768 ? '200px' : 'auto'
                      }}
                      onClick={showLoginModal}
                    >
                      入驻开发者 <RightOutlined />
                    </Button>
                    <Button
                      size="large"
                      style={{
                        height: window.innerWidth < 768 ? 48 : 56,
                        padding: window.innerWidth < 768 ? '0 24px' : '0 32px',
                        fontSize: window.innerWidth < 768 ? 16 : 18,
                        fontWeight: 600,
                        borderRadius: 8,
                        background: 'transparent',
                        borderColor: 'rgba(0, 212, 170, 0.6)',
                        color: '#00d4aa',
                        width: window.innerWidth < 768 ? '200px' : 'auto'
                      }}
                      icon={<UserOutlined />}
                      onClick={handleAppUserLogin}
                    >
                      应用用户
                    </Button>
                    <Button
                      size="large"
                      style={{
                        height: window.innerWidth < 768 ? 48 : 56,
                        padding: window.innerWidth < 768 ? '0 24px' : '0 32px',
                        fontSize: window.innerWidth < 768 ? 16 : 18,
                        fontWeight: 600,
                        borderRadius: 8,
                        background: 'transparent',
                        borderColor: 'rgba(255,255,255,0.4)',
                        color: 'white',
                        width: window.innerWidth < 768 ? '200px' : 'auto',
                      }}
                      icon={<GithubOutlined />}
                      onClick={() =>
                        window.open('https://github.com/AYssu/CipherGate', '_blank', 'noopener,noreferrer')
                      }
                    >
                      GitHub 
                    </Button>
                  </div>
                </div>
              </Col>
              
              <Col xs={24} lg={10}>
                <div style={{ 
                  textAlign: 'center',
                  marginTop: window.innerWidth < 768 ? 32 : 0
                }}>
                  <div style={{
                    background: 'rgba(0, 212, 170, 0.1)',
                    borderRadius: 20,
                    padding: window.innerWidth < 768 ? 24 : 40,
                    backdropFilter: 'blur(10px)',
                    border: '1px solid rgba(0, 212, 170, 0.2)'
                  }}>
                    <img 
                      src={safeIcon}
                      alt="CipherGate Security" 
                      style={{ 
                        width: window.innerWidth < 768 ? 80 : 120,
                        height: window.innerWidth < 768 ? 80 : 120,
                        marginBottom: window.innerWidth < 768 ? 12 : 20,
                        filter: 'drop-shadow(0 4px 20px rgba(0, 212, 170, 0.3))'
                      }} 
                    />
                    <Title level={window.innerWidth < 768 ? 4 : 3} style={{ color: 'white', margin: 0 }}>
                      可信赖的安全伙伴
                    </Title>
                  </div>
                </div>
              </Col>
            </Row>
          </div>

        </div>
        {/* Stats Section */}
        <div style={{ padding: window.innerWidth < 768 ? '60px 0' : '80px 0', background: '#fafafa' }}>
          <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 16px' }}>
            <div style={{ textAlign: 'center', marginBottom: window.innerWidth < 768 ? 40 : 60 }}>
              <Title level={2} style={{ 
                marginBottom: 16, 
                fontSize: window.innerWidth < 768 ? 24 : 36, 
                fontWeight: 700 
              }}>
                值得信赖的数据表现
              </Title>
              <Paragraph style={{ 
                fontSize: window.innerWidth < 768 ? 16 : 18, 
                color: '#666', 
                maxWidth: 600, 
                margin: '0 auto',
                padding: '0 16px'
              }}>
                以下能力均可在当前版本中直接使用与验证
              </Paragraph>
            </div>
            
            <Row gutter={[16, 24]}>
              {stats.map((stat, index) => (
                <Col xs={12} sm={12} md={6} key={index}>
                  <Card style={{ 
                    textAlign: 'center', 
                    border: 'none',
                    boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
                    borderRadius: 16,
                    transition: 'all 0.3s ease'
                  }}
                  className="stats-card"
                  >
                    <Statistic
                      title={stat.title}
                      value={stat.value}
                      suffix={stat.suffix}
                      prefix={stat.prefix}
                      valueStyle={{ 
                        color: '#1890ff', 
                        fontSize: window.innerWidth < 768 ? 20 : 32, 
                        fontWeight: 700 
                      }}
                    />
                  </Card>
                </Col>
              ))}
            </Row>
          </div>
        </div>

        {/* Features Section */}
        <div style={{ padding: window.innerWidth < 768 ? '80px 0' : '120px 0', background: '#fff' }}>
          <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 16px' }}>
            <div style={{ textAlign: 'center', marginBottom: window.innerWidth < 768 ? 60 : 80 }}>
              <Title level={2} style={{ 
                marginBottom: 16, 
                fontSize: window.innerWidth < 768 ? 24 : 36, 
                fontWeight: 700,
                background: 'linear-gradient(135deg, #1890ff, #00d4aa)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}>
                核心产品能力
              </Title>
              <Paragraph style={{ 
                fontSize: window.innerWidth < 768 ? 16 : 18, 
                color: '#666', 
                maxWidth: 700, 
                margin: '0 auto',
                padding: '0 16px',
                lineHeight: 1.6
              }}>
                聚焦“可落地”的授权与管理能力，覆盖鉴权、权限、配置、审计与消息通知等核心场景
              </Paragraph>
            </div>
            
            <Row gutter={[16, 24]}>
              {features.map((feature, index) => (
                <Col xs={24} sm={12} lg={8} key={index}>
                  <Card 
                    hoverable
                    style={{ 
                      height: '100%',
                      border: 'none',
                      borderRadius: 16,
                      boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
                      transition: 'all 0.3s ease'
                    }}
                    styles={{ body: { padding: window.innerWidth < 768 ? 24 : 32 } }}
                    className="feature-card"
                  >
                    <div style={{ textAlign: 'center', marginBottom: window.innerWidth < 768 ? 16 : 24 }}>
                      <div style={{ fontSize: window.innerWidth < 768 ? 36 : 48 }}>
                        {React.cloneElement(feature.icon, {
                          style: { 
                            ...feature.icon.props.style,
                            fontSize: window.innerWidth < 768 ? 36 : 48
                          }
                        })}
                      </div>
                    </div>
                    
                    <Title level={4} style={{ 
                      marginBottom: 16, 
                      textAlign: 'center',
                      fontSize: window.innerWidth < 768 ? 18 : 20,
                      fontWeight: 600
                    }}>
                      {feature.title}
                    </Title>
                    
                    <Paragraph style={{ 
                      color: '#666', 
                      lineHeight: 1.7,
                      textAlign: 'center',
                      marginBottom: 16,
                      fontSize: window.innerWidth < 768 ? 14 : 16
                    }}>
                      {feature.description}
                    </Paragraph>
                    
                    <div style={{ textAlign: 'center' }}>
                      <Text strong style={{ 
                        color: '#1890ff',
                        background: '#f0f8ff',
                        padding: '4px 12px',
                        borderRadius: 20,
                        fontSize: window.innerWidth < 768 ? 12 : 14
                      }}>
                        {feature.highlight}
                      </Text>
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </div>
        </div>
        {/* CTA Section */}
        <div style={{ 
          background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
          color: 'white',
          padding: window.innerWidth < 768 ? '60px 0' : '100px 0',
          position: 'relative'
        }}>
          <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 16px', textAlign: 'center' }}>
            <Title level={2} style={{
              color: 'white',
              marginBottom: window.innerWidth < 768 ? 16 : 24,
              fontSize: window.innerWidth < 768 ? 24 : 36,
              fontWeight: 700
            }}>
              开启您的应用安全之旅
            </Title>
            <Paragraph style={{
              color: 'rgba(255,255,255,0.9)',
              fontSize: window.innerWidth < 768 ? 16 : 18,
              marginBottom: window.innerWidth < 768 ? 32 : 48,
              maxWidth: 600,
              margin: window.innerWidth < 768 ? '0 auto 32px' : '0 auto 48px',
              padding: '0 16px'
            }}>
              立即入驻，免费体验 CipherGate 全部能力，
              让授权管理更安全、更高效
            </Paragraph>
            
            <div style={{ 
              display: 'flex',
              flexDirection: window.innerWidth < 768 ? 'column' : 'row',
              gap: window.innerWidth < 768 ? 16 : 24,
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Button
                type="primary"
                size="large"
                style={{
                  height: window.innerWidth < 768 ? 48 : 56,
                  padding: window.innerWidth < 768 ? '0 32px' : '0 40px',
                  fontSize: window.innerWidth < 768 ? 16 : 18,
                  fontWeight: 600,
                  borderRadius: 8,
                  background: '#00d4aa',
                  borderColor: '#00d4aa',
                  color: 'white',
                  width: window.innerWidth < 768 ? '240px' : 'auto'
                }}
                onClick={showLoginModal}
              >
                立即入驻 <RightOutlined />
              </Button>
            </div>
          </div>
        </div>
      </Content>
      <Footer style={{ 
        background: '#001529',
        color: 'rgba(255,255,255,0.65)',
        padding: '60px 0 40px'
      }}>
        <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 24px' }}>
          <Row gutter={[48, 32]}>
            <Col xs={24} md={8}>
              <div style={{ marginBottom: 24 }}>
                <div style={{ 
                  fontSize: 24, 
                  fontWeight: 700, 
                  color: '#1890ff',
                  marginBottom: 16,
                  display: 'flex',
                  alignItems: 'center'
                }}>
                  <img 
                    src="/favicon.svg" 
                    alt="CipherGate Logo" 
                    style={{ marginRight: 8, width: 28, height: 28 }} 
                  />
                  CipherGate
                </div>
                <Paragraph style={{ color: 'rgba(255,255,255,0.65)', lineHeight: 1.6 }}>
                  专业的网络安全解决方案提供商，致力于为企业构建安全可信的数字化环境。
                </Paragraph>
              </div>
            </Col>
            
            <Col xs={12} md={4}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>快速入口</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <a href="#features" style={{ color: 'rgba(255,255,255,0.65)' }}>产品能力</a>
                <a href="#about" style={{ color: 'rgba(255,255,255,0.65)' }}>关于我们</a>
                <a href="#contact" style={{ color: 'rgba(255,255,255,0.65)' }}>商务咨询</a>
                <a href="#login" style={{ color: 'rgba(255,255,255,0.65)' }}>登录平台</a>
              </div>
            </Col>
            
            <Col xs={12} md={4}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>资源中心</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>使用文档</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>更新日志</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>常见问题</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>服务条款</a>
              </div>
            </Col>
            
            <Col xs={24} md={8}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>获取支持</Title>
              <Space direction="vertical" size="small">
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  建议通过页面上的「立即登录」入口提交工单
                </Text>
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  企业合作与售前咨询请联系管理员配置的官方渠道
                </Text>
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  工作时间内通常在 1 个工作日内响应
                </Text>
              </Space>
            </Col>
          </Row>
          
          <Divider style={{ borderColor: 'rgba(255,255,255,0.2)', margin: '40px 0 20px' }} />
          
          <div style={{ textAlign: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.45)' }}>
              CipherGate ©{new Date().getFullYear()} Created by AYssu.专业的网络安全解决方案提供商
              {siteInfo.publicSecurityRecordNo ? ` | ${siteInfo.publicSecurityRecordNo}` : ''}
              {siteInfo.icpLicenseNo ? ` | ${siteInfo.icpLicenseNo}` : ''}
              {siteInfo.icpRecordNo ? ` | ${siteInfo.icpRecordNo}` : ''}
            </Text>
          </div>
        </div>
      </Footer>

      {/* 登录弹窗 */}
      <Modal
        title={null}
        open={loginModalVisible}
        onCancel={handleCancel}
        footer={null}
        width={window.innerWidth < 768 ? '90%' : 400}
        centered
        closable={false}
        maskClosable={true}
        styles={{
          content: {
            background: '#ffffff',
            borderRadius: 8,
            border: 'none',
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
            margin: window.innerWidth < 768 ? '16px' : 'auto'
          },
          body: {
            padding: window.innerWidth < 768 ? '24px 20px 20px' : '32px'
          }
        }}
      >
        <div>
          {/* 关闭按钮 */}
          <Button
            type="text"
            onClick={handleCancel}
            style={{
              position: 'absolute',
              top: window.innerWidth < 768 ? 8 : 12,
              right: window.innerWidth < 768 ? 8 : 12,
              color: '#ccc',
              fontSize: window.innerWidth < 768 ? 14 : 16,
              width: window.innerWidth < 768 ? 24 : 28,
              height: window.innerWidth < 768 ? 24 : 28,
              minWidth: window.innerWidth < 768 ? 24 : 28,
              maxWidth: window.innerWidth < 768 ? 24 : 28,
              padding: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              lineHeight: 1,
              flexShrink: 0,
              border: 'none',
              background: 'transparent'
            }}
          >
            ×
          </Button>

          {/* Logo */}
          <div style={{ textAlign: 'center', marginBottom: window.innerWidth < 768 ? 20 : 24 }}>
            <div style={{
              width: window.innerWidth < 768 ? 40 : 48,
              height: window.innerWidth < 768 ? 40 : 48,
              margin: '0 auto 16px',
              background: '#f8f9fa',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid #e9ecef'
            }}>
              <LockOutlined style={{
                fontSize: window.innerWidth < 768 ? 16 : 20,
                color: '#1890ff'
              }} />
            </div>

            <Typography.Title level={4} style={{
              marginBottom: 4,
              color: '#1a1a1a',
              fontWeight: 600,
              fontSize: window.innerWidth < 768 ? 18 : 20
            }}>
              登录到 CipherGate
            </Typography.Title>

            <Typography.Paragraph style={{
              color: '#666',
              marginBottom: 0,
              fontSize: window.innerWidth < 768 ? 13 : 14
            }}>
              选择登录方式
            </Typography.Paragraph>
          </div>

          {/* 登录方式 Tabs */}
          <Tabs
            activeKey={loginMode}
            onChange={(key) => setLoginMode(key as 'github' | 'password')}
            centered
            items={[
              {
                key: 'github',
                label: (
                  <span><GithubOutlined style={{ marginRight: 4 }} />GitHub</span>
                ),
                children: (
                  <Button
                    type="primary"
                    size="large"
                    icon={<GithubOutlined />}
                    onClick={handleGithubLogin}
                    style={{
                      width: '100%',
                      height: window.innerWidth < 768 ? 40 : 44,
                      fontSize: window.innerWidth < 768 ? 14 : 15,
                      fontWeight: 500,
                      borderRadius: 6,
                      background: '#24292e',
                      borderColor: '#24292e',
                      boxShadow: 'none'
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.background = '#2f363d';
                      e.currentTarget.style.borderColor = '#2f363d';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.background = '#24292e';
                      e.currentTarget.style.borderColor = '#24292e';
                    }}
                  >
                    Continue with GitHub
                  </Button>
                )
              },
              {
                key: 'password',
                label: (
                  <span><KeyOutlined style={{ marginRight: 4 }} />密码登录</span>
                ),
                children: (
                  <Form
                    form={passwordLoginForm}
                    layout="vertical"
                    onFinish={handlePasswordLogin}
                    style={{ marginBottom: 0 }}
                  >
                    <Form.Item
                      name="login"
                      rules={[{ required: true, message: '请输入 GitHub 账号名' }]}
                      style={{ marginBottom: 12 }}
                    >
                      <Input
                        prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
                        placeholder="GitHub 账号名"
                        size={window.innerWidth < 768 ? 'middle' : 'large'}
                        autoComplete="username"
                      />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      rules={[{ required: true, message: '请输入密码' }]}
                      style={{ marginBottom: 16 }}
                    >
                      <Input.Password
                        prefix={<KeyOutlined style={{ color: '#bfbfbf' }} />}
                        placeholder="密码"
                        size={window.innerWidth < 768 ? 'middle' : 'large'}
                        autoComplete="current-password"
                      />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0 }}>
                      <Button
                        type="primary"
                        htmlType="submit"
                        loading={passwordLoginLoading}
                        block
                        size={window.innerWidth < 768 ? 'middle' : 'large'}
                        style={{
                          height: window.innerWidth < 768 ? 36 : 44,
                          fontWeight: 500,
                          borderRadius: 6
                        }}
                      >
                        登录
                      </Button>
                    </Form.Item>
                  </Form>
                )
              }
            ]}
          />

          {/* 底部说明 */}
          <Typography.Paragraph style={{
            color: '#999',
            fontSize: window.innerWidth < 768 ? 11 : 12,
            margin: window.innerWidth < 768 ? '16px 0 0' : '20px 0 0',
            lineHeight: 1.4,
            textAlign: 'center'
          }}>
            {loginMode === 'password' && (
              <span style={{ display: 'block', marginBottom: 4, color: '#faad14' }}>
                提示：密码登录需要先通过 GitHub 登录并设置密码
              </span>
            )}
            点击登录即表示您同意我们的
            <a href="#" style={{ color: '#1890ff', textDecoration: 'none' }}> 服务条款 </a>
            和
            <a href="#" style={{ color: '#1890ff', textDecoration: 'none' }}> 隐私政策</a>
          </Typography.Paragraph>
        </div>
      </Modal>

      {/* 系统初始化配置弹窗 */}
      <Modal
        title={
          <div style={{ textAlign: 'center', paddingTop: 8 }}>
            <SettingOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 12 }} />
            <div style={{ fontSize: 20, fontWeight: 600 }}>系统初始化配置</div>
          </div>
        }
        open={initModalVisible}
        onOk={handleInitSubmit}
        onCancel={() => {}}
        closable={false}
        maskClosable={false}
        width={500}
        centered
        okText="完成初始化"
        cancelText="稍后配置"
        confirmLoading={loading}
        okButtonProps={{ size: 'large' }}
        cancelButtonProps={{ size: 'large', style: { display: 'none' } }}
      >
        <div style={{ padding: '20px 0' }}>
          <Typography.Paragraph style={{ color: '#666', marginBottom: 24, textAlign: 'center' }}>
            首次使用需要配置 GitHub OAuth2 认证信息，请填写以下配置项
          </Typography.Paragraph>

          <Form
            form={initForm}
            layout="vertical"
            initialValues={{
              redirectUri: 'http://localhost:8080/login/oauth2/code/github',
              frontendUrl: 'http://localhost:5173/dashboard'
            }}
          >
            <Form.Item
              label="GitHub OAuth2 Client ID"
              name="clientId"
              rules={[{ required: true, message: '请输入 Client ID' }]}
            >
              <Input 
                placeholder="请输入 GitHub OAuth2 Client ID" 
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="GitHub OAuth2 Client Secret"
              name="clientSecret"
              rules={[{ required: true, message: '请输入 Client Secret' }]}
            >
              <Input.Password 
                placeholder="请输入 GitHub OAuth2 Client Secret" 
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="Redirect URI"
              name="redirectUri"
              rules={[{ required: true, message: '请输入 Redirect URI' }]}
              tooltip="GitHub 回调地址，必须是后端地址"
            >
              <Input 
                placeholder="请输入 Redirect URI" 
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="前端地址"
              name="frontendUrl"
              rules={[{ required: true, message: '请输入前端地址' }]}
              tooltip="登录成功后重定向的前端地址"
            >
              <Input 
                placeholder="请输入前端地址" 
                size="large"
              />
            </Form.Item>
          </Form>

          <Typography.Paragraph style={{ color: '#999', fontSize: 12, marginTop: 16, marginBottom: 0 }}>
            提示：请确保在 GitHub OAuth App 设置中配置了正确的 Redirect URI
          </Typography.Paragraph>
        </div>
      </Modal>
      {/* 应用用户登录弹窗 */}
      <Modal
        title={
          <div style={{ textAlign: 'center', paddingTop: 8 }}>
            <UserOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 12 }} />
            <div style={{ fontSize: 20, fontWeight: 600 }}>应用用户登录</div>
          </div>
        }
        open={appUserModalVisible}
        onCancel={() => {
          setAppUserModalVisible(false);
          appUserLoginForm.resetFields();
        }}
        footer={null}
        width={420}
        centered
        closable
        maskClosable
        styles={{
          content: {
            borderRadius: 12,
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)'
          },
          body: {
            padding: window.innerWidth < 768 ? '24px 20px 20px' : '24px 32px 32px'
          }
        }}
      >
        <Form
          form={appUserLoginForm}
          layout="vertical"
          onFinish={handleAppUserLoginSubmit}
          autoComplete="off"
        >
          <Form.Item
            name="email"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '邮箱格式不正确' }
            ]}
          >
            <Input
              prefix={<MailOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="邮箱地址"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="密码"
              size="large"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item
            name="captchaCode"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <Input
                prefix={<KeyOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="验证码"
                size="large"
                style={{ flex: 1 }}
              />
              <div
                onClick={loadCaptcha}
                style={{
                  cursor: 'pointer',
                  height: 40,
                  borderRadius: 6,
                  overflow: 'hidden',
                  border: '1px solid #d9d9d9',
                  flexShrink: 0
                }}
              >
                {captchaUrl && <img src={captchaUrl} alt="验证码" style={{ height: 40, display: 'block' }} />}
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
              style={{ height: 44, fontWeight: 500, borderRadius: 6 }}
            >
              登录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <a
            href="/portal/recovery"
            style={{ color: '#1890ff', fontSize: 13 }}
          >
            忘记密码？
          </a>
        </div>
      </Modal>

      {/* 应用选择弹窗 */}
      <Modal
        title={
          <div style={{ textAlign: 'center', paddingTop: 8 }}>
            <SafetyCertificateOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 12 }} />
            <div style={{ fontSize: 20, fontWeight: 600 }}>选择应用</div>
          </div>
        }
        open={selectAppVisible}
        onCancel={() => setSelectAppVisible(false)}
        footer={null}
        width={420}
        centered
        closable
        maskClosable
        styles={{
          content: {
            borderRadius: 12,
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)'
          },
          body: {
            padding: window.innerWidth < 768 ? '24px 20px 20px' : '24px 32px 32px'
          }
        }}
      >
        <Typography.Paragraph style={{ textAlign: 'center', color: '#666', marginBottom: 20 }}>
          您的账号绑定了多个应用，请选择要登录的应用
        </Typography.Paragraph>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {appList.map((app: any) => (
            <Button
              key={app.appId}
              size="large"
              icon={<SafetyCertificateOutlined />}
              onClick={() => handleSelectApp(app.appId)}
              style={{
                height: 52,
                textAlign: 'left',
                borderRadius: 8,
                border: '1px solid #d9d9d9',
                fontSize: 15
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