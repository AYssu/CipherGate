import React, { useState } from 'react';
import { Layout, Card, Form, Input, Button, Typography, message, Steps, Select } from 'antd';
import { SafetyOutlined, MailOutlined, LockOutlined, SendOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Content } = Layout;
const { Title } = Typography;

const PortalPasswordRecoveryPage: React.FC = () => {
  const [form] = Form.useForm();
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState('');
  const [apps, setApps] = useState<any[]>([]);
  const navigate = useNavigate();

  const handleSendCode = async () => {
    try {
      const emailVal = await form.validateFields(['email']);
      setEmail(emailVal.email);
      setLoading(true);
      await portalAuthApi.sendRecoveryCode(emailVal.email);
      const appsRes = await portalAuthApi.getRecoveryApps(emailVal.email);
      setApps(appsRes.data || []);
      message.success('验证码已发送');
      setCurrentStep(1);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async (values: any) => {
    setLoading(true);
    try {
      await portalAuthApi.resetPassword({
        email,
        verifyCode: values.verifyCode,
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
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Content style={{ width: '100%', maxWidth: 480, padding: '24px' }}>
        <Card style={{ borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.08)' }}>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <SafetyOutlined style={{ fontSize: 40, color: '#1890ff' }} />
            <Title level={3} style={{ margin: '12px 0 4px' }}>找回密码</Title>
          </div>

          <Steps current={currentStep} size="small" style={{ marginBottom: 24 }}
            items={[{ title: '验证邮箱' }, { title: '重置密码' }]}
          />

          {currentStep === 0 && (
            <Form form={form} layout="vertical" onFinish={handleSendCode}>
              <Form.Item name="email" rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}>
                <Input prefix={<MailOutlined />} placeholder="注册邮箱" size="large" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block size="large">发送验证码</Button>
              </Form.Item>
            </Form>
          )}

          {currentStep === 1 && (
            <Form form={form} layout="vertical" onFinish={handleReset}>
              <Form.Item name="verifyCode" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input prefix={<SendOutlined />} placeholder="验证码" size="large" />
              </Form.Item>
              <Form.Item name="appId" rules={[{ required: true, message: '请选择应用' }]}>
                <Select placeholder="选择要重置密码的应用" size="large">
                  {apps.map((app: any) => (
                    <Select.Option key={app.appId} value={app.appId}>{app.appName}</Select.Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="newPassword" rules={[{ required: true, min: 6, message: '密码至少6位' }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="新密码" size="large" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block size="large">重置密码</Button>
              </Form.Item>
            </Form>
          )}

          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/portal/login"><Button type="link">返回登录</Button></Link>
          </div>
        </Card>
      </Content>
    </Layout>
  );
};

export default PortalPasswordRecoveryPage;
