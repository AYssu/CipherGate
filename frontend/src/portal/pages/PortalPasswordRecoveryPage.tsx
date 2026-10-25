import PublicServiceLayout from '../../components/PublicServiceLayout';
import React, { useState } from 'react';
import { Form, Input, Button, message, Steps, Select } from 'antd';
import { MailOutlined, LockOutlined, SendOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';


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
    <PublicServiceLayout title="找回密码" description="验证注册邮箱后，为您选择的应用设置新密码。" help="验证码发送到您的注册邮箱，5 分钟内有效。请留意垃圾邮件文件夹。">
          <Steps current={currentStep} size="small" style={{ marginBottom: 24 }}
            items={[{ title: '验证邮箱' }, { title: '重置密码' }]}
          />

          {currentStep === 0 && (
            <Form form={form} layout="vertical" onFinish={handleSendCode}>
              <Form.Item name="email" label="注册邮箱" rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}>
                <Input prefix={<MailOutlined />} placeholder="注册邮箱" size="large" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block size="large">发送验证码</Button>
              </Form.Item>
            </Form>
          )}

          {currentStep === 1 && (<><p className="public-service-note">验证码已发送至 {email}</p>
            <Form form={form} layout="vertical" onFinish={handleReset}>
              <Form.Item name="verifyCode" label="邮箱验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input prefix={<SendOutlined />} placeholder="验证码" size="large" autoComplete="one-time-code" inputMode="numeric" maxLength={6} />
              </Form.Item>
              <Form.Item name="appId" label="需要重置的应用" rules={[{ required: true, message: '请选择应用' }]}>
                <Select placeholder="选择要重置密码的应用" size="large">
                  {apps.map((app: any) => (
                    <Select.Option key={app.appId} value={app.appId}>{app.appName}</Select.Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 6, message: '密码至少6位' }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="新密码" size="large" autoComplete="new-password" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block size="large">重置密码</Button>
              </Form.Item>
            </Form>
          <Button type="link" disabled={loading} onClick={() => { setCurrentStep(0); form.resetFields(['verifyCode', 'newPassword', 'appId']); }}>更换邮箱或重新获取验证码</Button></>)}

          <div style={{ textAlign: 'center', marginTop: 12 }}>
            <Link to="/portal/login"><Button type="link">返回登录</Button></Link>
          </div>
    </PublicServiceLayout>
  );
};

export default PortalPasswordRecoveryPage;
