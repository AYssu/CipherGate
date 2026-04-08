import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Form, Input, Row, Space, Spin, Switch, Tabs, Typography, message } from 'antd';
import { systemApi } from '../services';
import type { SystemSettings } from '../services/systemService';

const { Title, Text } = Typography;

const SystemConfigContent: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [githubForm] = Form.useForm();
  const [siteForm] = Form.useForm();
  const [emailForm] = Form.useForm();

  const loadSettings = async () => {
    setLoading(true);
    try {
      const response = await systemApi.getSystemSettings();
      const data: SystemSettings = response.data;
      setSettings(data);
      githubForm.setFieldsValue({
        clientId: data.githubClientId || '',
        clientSecret: '',
        redirectUri: data.githubRedirectUri || '',
        frontendUrl: data.frontendUrl || ''
      });
      siteForm.setFieldsValue({
        publicSecurityRecordNo: data.sitePublicSecurityRecordNo || '',
        icpLicenseNo: data.siteIcpLicenseNo || '',
        icpRecordNo: data.siteIcpRecordNo || ''
      });
      emailForm.setFieldsValue({
        smtpHost: data.emailSmtpHost || '',
        smtpPort: data.emailSmtpPort || '',
        smtpUsername: data.emailSmtpUsername || '',
        smtpPassword: '',
        fromEmail: data.emailFrom || '',
        enabled: !!data.emailEnabled
      });
    } catch (error) {
      console.error('加载系统配置失败:', error);
      message.error('加载系统配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const submitGithub = async () => {
    try {
      const values = await githubForm.validateFields();
      setSaving(true);
      await systemApi.updateGithubSettings(values);
      message.success('GitHub 配置已保存');
      githubForm.setFieldValue('clientSecret', '');
      await loadSettings();
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存 GitHub 配置失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const submitSite = async () => {
    try {
      const values = await siteForm.validateFields();
      setSaving(true);
      await systemApi.updateSiteSettings(values);
      message.success('备案信息已保存');
      await loadSettings();
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存备案信息失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const submitEmail = async () => {
    try {
      const values = await emailForm.validateFields();
      setSaving(true);
      await systemApi.updateEmailSettings(values);
      message.success('邮箱配置已保存');
      emailForm.setFieldValue('smtpPassword', '');
      await loadSettings();
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存邮箱配置失败');
      }
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 320 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <Card>
      <Title level={4} style={{ marginTop: 0 }}>系统配置</Title>
      <Tabs
        items={[
          {
            key: 'github',
            label: 'GitHub 配置',
            children: (
              <Form form={githubForm} layout="vertical">
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="clientId" label="Client ID" rules={[{ required: true, message: '请输入 Client ID' }]}>
                      <Input placeholder="GitHub OAuth2 Client ID" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="clientSecret" label="Client Secret（不填则保持不变）">
                      <Input.Password placeholder="GitHub OAuth2 Client Secret" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="redirectUri" label="Redirect URI" rules={[{ required: true, message: '请输入 Redirect URI' }]}>
                      <Input placeholder="http://localhost:8080/login/oauth2/code/github" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="frontendUrl" label="前端地址" rules={[{ required: true, message: '请输入前端地址' }]}>
                      <Input placeholder="http://localhost:5173/dashboard" />
                    </Form.Item>
                  </Col>
                </Row>
                <Space>
                  <Button type="primary" loading={saving} onClick={submitGithub}>保存 GitHub 配置</Button>
                </Space>
              </Form>
            )
          },
          {
            key: 'site',
            label: '主页备案信息',
            children: (
              <Form form={siteForm} layout="vertical">
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="publicSecurityRecordNo" label="公网安备号">
                      <Input placeholder="京公网安备11000002000001号" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="icpLicenseNo" label="ICP证号">
                      <Input placeholder="京ICP证030173号" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="icpRecordNo" label="ICP备案号（可选）">
                      <Input placeholder="京ICP备xxxxxxx号" />
                    </Form.Item>
                  </Col>
                </Row>
                <Space>
                  <Button type="primary" loading={saving} onClick={submitSite}>保存备案配置</Button>
                </Space>
              </Form>
            )
          },
          {
            key: 'email',
            label: '邮箱配置',
            children: (
              <Form form={emailForm} layout="vertical">
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item name="smtpHost" label="SMTP Host">
                      <Input placeholder="smtp.example.com" />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item name="smtpPort" label="SMTP Port">
                      <Input placeholder="465/587" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="smtpUsername" label="SMTP 用户名">
                      <Input placeholder="username@example.com" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item name="smtpPassword" label="SMTP 密码（不填则保持不变）">
                      <Input.Password placeholder="******" />
                    </Form.Item>
                  </Col>
                  <Col span={10}>
                    <Form.Item name="fromEmail" label="发件人邮箱">
                      <Input placeholder="noreply@example.com" />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name="enabled" label="启用邮箱通知" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </Col>
                </Row>
                <Space align="center">
                  <Button type="primary" loading={saving} onClick={submitEmail}>保存邮箱配置</Button>
                  <Text type="secondary">
                    当前密码状态：{settings?.emailPasswordSet ? '已设置' : '未设置'}
                  </Text>
                </Space>
              </Form>
            )
          }
        ]}
      />
    </Card>
  );
};

export default SystemConfigContent;