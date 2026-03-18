import React from 'react';
import { Button, Card, Typography, Space } from 'antd';
import { GithubOutlined, SafetyOutlined } from '@ant-design/icons';

const { Title, Paragraph } = Typography;

const Login: React.FC = () => {
  const handleGithubLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/github';
  };

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px'
    }}>
      <Card style={{
        width: '100%',
        maxWidth: 400,
        borderRadius: 16,
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
        border: 'none'
      }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <SafetyOutlined style={{ 
            fontSize: 48, 
            color: '#1890ff', 
            marginBottom: 16 
          }} />
          <Title level={2} style={{ marginBottom: 8, color: '#1a1a2e' }}>
            CipherGate
          </Title>
          <Paragraph style={{ color: '#666', margin: 0 }}>
            企业级网络安全智能防护平台
          </Paragraph>
        </div>

        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Title level={4} style={{ textAlign: 'center', marginBottom: 16 }}>
              欢迎登录
            </Title>
            <Paragraph style={{ textAlign: 'center', color: '#666', marginBottom: 24 }}>
              使用您的 GitHub 账号快速登录
            </Paragraph>
          </div>

          <Button
            type="primary"
            size="large"
            icon={<GithubOutlined />}
            onClick={handleGithubLogin}
            style={{
              width: '100%',
              height: 48,
              fontSize: 16,
              fontWeight: 600,
              borderRadius: 8,
              background: '#24292e',
              borderColor: '#24292e'
            }}
          >
            使用 GitHub 登录
          </Button>

          <div style={{ textAlign: 'center', marginTop: 24 }}>
            <Paragraph style={{ color: '#999', fontSize: 14 }}>
              登录即表示您同意我们的
              <a href="#" style={{ color: '#1890ff' }}> 服务条款 </a>
              和
              <a href="#" style={{ color: '#1890ff' }}> 隐私政策</a>
            </Paragraph>
          </div>
        </Space>
      </Card>
    </div>
  );
};

export default Login;