import React from 'react';
import { Layout, Menu, Button, Card, Row, Col, Typography, Space, Statistic, Divider } from 'antd';
import {
  SecurityScanOutlined,
  SafetyOutlined,
  CloudServerOutlined,
  SafetyCertificateOutlined,
  LockOutlined,
  MonitorOutlined,
  RightOutlined,
  CheckCircleOutlined,
  TeamOutlined, MenuOutlined
} from '@ant-design/icons';
import { navigateTo } from '../utils/router';

const { Header, Content, Footer } = Layout;
const { Title, Paragraph, Text } = Typography;

const Home: React.FC = () => {
  const features = [
    {
      icon: <SecurityScanOutlined style={{ fontSize: 48, color: '#1890ff' }} />,
      title: '智能威胁检测',
      description: '基于AI的实时威胁检测系统，99.9%准确率识别恶意行为，保护企业核心资产安全。',
      highlight: '99.9% 准确率'
    },
    {
      icon: <SafetyOutlined style={{ fontSize: 48, color: '#52c41a' }} />,
      title: '零信任架构',
      description: '构建企业级零信任安全框架，动态访问控制，确保每个连接都经过验证。',
      highlight: '零信任'
    },
    {
      icon: <CloudServerOutlined style={{ fontSize: 48, color: '#722ed1' }} />,
      title: '云原生安全',
      description: '为云环境量身定制的安全解决方案，支持多云部署，弹性扩展。',
      highlight: '多云支持'
    },
    {
      icon: <SafetyCertificateOutlined style={{ fontSize: 48, color: '#fa8c16' }} />,
      title: '合规自动化',
      description: '自动化合规检查与报告生成，满足SOC2、ISO27001等国际标准要求。',
      highlight: 'SOC2 认证'
    },
    {
      icon: <LockOutlined style={{ fontSize: 48, color: '#eb2f96' }} />,
      title: '端到端加密',
      description: '军用级AES-256加密技术，保护数据在传输和存储过程中的绝对安全。',
      highlight: 'AES-256'
    },
    {
      icon: <MonitorOutlined style={{ fontSize: 48, color: '#13c2c2' }} />,
      title: '7x24监控',
      description: '全天候安全运营中心，专业团队实时监控，平均响应时间小于5分钟。',
      highlight: '<5分钟响应'
    },
  ];
  const stats = [
    { title: '企业客户', value: 2000, suffix: '+', prefix: <TeamOutlined /> },
    { title: '防护设备', value: 50000, suffix: '+', prefix: <SafetyOutlined /> },
    { title: '威胁拦截', value: 99.9, suffix: '%', prefix: <SecurityScanOutlined /> },
    { title: '服务可用性', value: 99.99, suffix: '%', prefix: <CheckCircleOutlined /> },
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
            color: '#1890ff',
            display: 'flex',
            alignItems: 'center',
            letterSpacing: '-0.5px'
          }}>
            <SafetyOutlined style={{ 
              marginRight: window.innerWidth < 768 ? 8 : 12, 
              fontSize: window.innerWidth < 768 ? 24 : 32 
            }} />
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
                onClick={() => navigateTo('/login')}
              >
                登录
              </Button>
              <Button type="primary" size="large" style={{ 
                fontWeight: 600,
                height: 44,
                padding: '0 24px',
                borderRadius: 8
              }}>
                免费咨询
              </Button>
            </Space>
          </div>
        </div>
      </Header>
      <Content style={{ marginTop: 64 }}>
        {/* Hero Section */}
        <div style={{ 
          background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
          color: 'white',
          padding: window.innerWidth < 768 ? '100px 0 80px' : '18 0px 0 120px',
          position: 'relative',
          overflow: 'hidden'
        }}>
          <div style={{ 
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'url("data:image/svg+xml,%3Csvg width="80" height="80" viewBox="0 0 80 80" xmlns="http://www.w3.org/2000/svg"%3E%3Cg fill="none" fill-rule="evenodd"%3E%3Cg fill="%23ffffff" fill-opacity="0.02"%3E%3Cpath d="M0 0h80v80H0V0zm20 20v40h40V20H20zm20 35a15 15 0 1 1 0-30 15 15 0 0 1 0 30z"/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")',
            opacity: 0.4
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
                    企业级网络安全
                    <br />
                    <span style={{ color: '#00d4aa' }}>智能防护平台</span>
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
                    CipherGate 为全球2000+个人开发者提供网络安全解决方案，
                    从传输检测到数据加密，构建个人开发者软件安全基石。
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
                      onClick={() => navigateTo('/login')}
                    >
                      立即登录 <RightOutlined />
                    </Button>
                    
                    <Button size="large" style={{ 
                      height: window.innerWidth < 768 ? 48 : 56, 
                      padding: window.innerWidth < 768 ? '0 24px' : '0 32px',
                      fontSize: window.innerWidth < 768 ? 16 : 18,
                      fontWeight: 600,
                      borderRadius: 8,
                      background: 'transparent',
                      borderColor: 'rgba(255,255,255,0.4)',
                      color: 'white',
                      width: window.innerWidth < 768 ? '200px' : 'auto'
                    }}>
                      观看演示
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
                    <SafetyOutlined style={{ 
                      fontSize: window.innerWidth < 768 ? 80 : 120, 
                      color: '#00d4aa', 
                      marginBottom: window.innerWidth < 768 ? 12 : 20 
                    }} />
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
                我们用数据说话，用实力证明专业能力
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
                我们提供完整的网络安全产品矩阵，覆盖从预防到检测、响应的全生命周期安全管理，
                为企业构建坚不可摧的安全防线
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
              准备好保护您的数字资产了吗？
            </Title>
            <Paragraph style={{ 
              color: 'rgba(255,255,255,0.9)', 
              fontSize: window.innerWidth < 768 ? 16 : 18, 
              marginBottom: window.innerWidth < 768 ? 32 : 48,
              maxWidth: 600,
              margin: window.innerWidth < 768 ? '0 auto 32px' : '0 auto 48px',
              padding: '0 16px'
            }}>
              立即联系我们的安全专家，获取个性化的安全解决方案，
              让您的企业在数字化转型中安全前行
            </Paragraph>
            
            <div style={{ 
              display: 'flex',
              flexDirection: window.innerWidth < 768 ? 'column' : 'row',
              gap: window.innerWidth < 768 ? 16 : 24,
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Button type="primary" size="large" style={{ 
                height: window.innerWidth < 768 ? 48 : 56, 
                padding: window.innerWidth < 768 ? '0 32px' : '0 40px',
                fontSize: window.innerWidth < 768 ? 16 : 18,
                fontWeight: 600,
                borderRadius: 8,
                background: '#00d4aa',
                borderColor: '#00d4aa',
                color: 'white',
                width: window.innerWidth < 768 ? '240px' : 'auto'
              }}>
                免费安全评估
              </Button>
              
              <Button size="large" style={{ 
                height: window.innerWidth < 768 ? 48 : 56, 
                padding: window.innerWidth < 768 ? '0 32px' : '0 40px',
                fontSize: window.innerWidth < 768 ? 16 : 18,
                fontWeight: 600,
                borderRadius: 8,
                background: 'transparent',
                borderColor: 'rgba(255,255,255,0.4)',
                color: 'white',
                width: window.innerWidth < 768 ? '240px' : 'auto'
              }}>
                联系销售顾问
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
                  <SafetyOutlined style={{ marginRight: 8, fontSize: 28 }} />
                  CipherGate
                </div>
                <Paragraph style={{ color: 'rgba(255,255,255,0.65)', lineHeight: 1.6 }}>
                  专业的网络安全解决方案提供商，致力于为企业构建安全可信的数字化环境。
                </Paragraph>
              </div>
            </Col>
            
            <Col xs={12} md={4}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>产品服务</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>威胁检测</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>安全防护</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>合规管理</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>安全咨询</a>
              </div>
            </Col>
            
            <Col xs={12} md={4}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>解决方案</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>金融行业</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>制造业</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>医疗健康</a>
                <a href="#" style={{ color: 'rgba(255,255,255,0.65)' }}>政府机构</a>
              </div>
            </Col>
            
            <Col xs={24} md={8}>
              <Title level={5} style={{ color: 'white', marginBottom: 16 }}>联系我们</Title>
              <Space direction="vertical" size="small">
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  📞 400-888-9999
                </Text>
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  ✉️ contact@ciphergate.com
                </Text>
                <Text style={{ color: 'rgba(255,255,255,0.65)' }}>
                  📍 北京市朝阳区建国门外大街1号
                </Text>
              </Space>
            </Col>
          </Row>
          
          <Divider style={{ borderColor: 'rgba(255,255,255,0.2)', margin: '40px 0 20px' }} />
          
          <div style={{ textAlign: 'center' }}>
            <Text style={{ color: 'rgba(255,255,255,0.45)' }}>
              CipherGate ©{new Date().getFullYear()} Created by Ayssu. 专业的网络安全解决方案提供商 | 
              京ICP备12345678号 | 网络文化经营许可证
            </Text>
          </div>
        </div>
      </Footer>
    </Layout>
  );
};

export default Home;