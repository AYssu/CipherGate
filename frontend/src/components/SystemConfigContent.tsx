import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Form, Input, Row, Space, Spin, Switch, Tabs, Typography, Upload, message, Grid } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { systemApi } from '../services';
import type { SystemSettings } from '../services/systemService';

const { Title, Text } = Typography;

const SystemConfigContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [geoIpSaving, setGeoIpSaving] = useState(false);
  const [ip2RegionSaving, setIp2RegionSaving] = useState(false);
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [githubForm] = Form.useForm();
  const [siteForm] = Form.useForm();
  const [emailForm] = Form.useForm();
  const [paymentForm] = Form.useForm();
  const [paymentSaving, setPaymentSaving] = useState(false);
  const [inviteForm] = Form.useForm();
  const [inviteSaving, setInviteSaving] = useState(false);

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
        fromDisplayName: data.emailFromDisplayName || '',
        enabled: !!data.emailEnabled
      });
      // 加载支付配置
      try {
        const payRes = await systemApi.getPaymentConfig();
        const payData = payRes.data;
        paymentForm.setFieldsValue({
          epayUrl: payData?.epayUrl || '',
          epayPid: payData?.epayPid || '',
          epayKey: '',
          epayNotifyUrl: payData?.epayNotifyUrl || '',
          epayReturnUrl: payData?.epayReturnUrl || '',
          successRedirectUrl: payData?.successRedirectUrl || '/user/balance',
        });
      } catch {}
      // 加载邀请配置
      try {
        const inviteRes = await systemApi.getInviteConfig();
        const inviteData = inviteRes.data;
        inviteForm.setFieldsValue({
          enabled: inviteData?.enabled !== false,
          maxCount: inviteData?.maxCount ?? 20,
          rewardAmount: inviteData?.rewardAmount ? inviteData.rewardAmount / 100 : 3,
        });
      } catch {}
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

  const submitPayment = async () => {
    try {
      const values = await paymentForm.validateFields();
      setPaymentSaving(true);
      const data: any = {};
      if (values.epayUrl) data.epayUrl = values.epayUrl;
      if (values.epayPid) data.epayPid = values.epayPid;
      if (values.epayKey) data.epayKey = values.epayKey;
      if (values.epayNotifyUrl) data.epayNotifyUrl = values.epayNotifyUrl;
      if (values.epayReturnUrl) data.epayReturnUrl = values.epayReturnUrl;
      if (values.successRedirectUrl) data.successRedirectUrl = values.successRedirectUrl;
      await systemApi.updatePaymentConfig(data);
      message.success('支付配置已保存');
      paymentForm.setFieldValue('epayKey', '');
      await loadSettings();
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存支付配置失败');
      }
    } finally {
      setPaymentSaving(false);
    }
  };

  const submitInvite = async () => {
    try {
      const values = await inviteForm.validateFields();
      setInviteSaving(true);
      await systemApi.updateInviteConfig({
        enabled: values.enabled,
        maxCount: values.maxCount,
        rewardAmount: Math.round(values.rewardAmount * 100),
      });
      message.success('邀请配置已保存');
      await loadSettings();
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error?.message || '保存邀请配置失败');
      }
    } finally {
      setInviteSaving(false);
    }
  };

  const toggleGeoIp = async (enabled: boolean) => {
    try {
      setGeoIpSaving(true);
      await systemApi.updateGeoIpSettings({ enabled });
      message.success(enabled ? '已开启 IP 解析' : '已关闭 IP 解析');
      await loadSettings();
    } catch (error: any) {
      message.error(error?.message || '更新 IP 解析开关失败');
    } finally {
      setGeoIpSaving(false);
    }
  };

  const uploadGeoDb = async (dbType: 'country' | 'city', file: File) => {
    try {
      setGeoIpSaving(true);
      await systemApi.uploadGeoIpDb(dbType, file);
      message.success(`${dbType === 'country' ? '国家库' : '城市库'}上传成功`);
      await loadSettings();
    } catch (error: any) {
      message.error(error?.message || '上传失败');
    } finally {
      setGeoIpSaving(false);
    }
    return false;
  };

  const toggleIp2Region = async (enabled: boolean) => {
    try {
      setIp2RegionSaving(true);
      await systemApi.updateIp2RegionSettings({ enabled });
      message.success(enabled ? '已开启 ip2region' : '已关闭 ip2region');
      await loadSettings();
    } catch (error: any) {
      message.error(error?.message || '更新 ip2region 开关失败');
    } finally {
      setIp2RegionSaving(false);
    }
  };

  const uploadIp2RegionDb = async (file: File) => {
    try {
      setIp2RegionSaving(true);
      await systemApi.uploadIp2RegionDb(file);
      message.success('ip2region 数据库上传成功');
      await loadSettings();
    } catch (error: any) {
      message.error(error?.message || '上传失败');
    } finally {
      setIp2RegionSaving(false);
    }
    return false;
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
      <Title level={isMobile ? 5 : 4} style={{ marginTop: 0 }}>系统配置</Title>
      <Tabs
        tabPosition={isMobile ? 'top' : 'top'}
        size={isMobile ? 'small' : 'middle'}
        items={[
          {
            key: 'github',
            label: 'GitHub 配置',
            children: (
              <Form form={githubForm} layout="vertical">
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="clientId" label="Client ID" rules={[{ required: true, message: '请输入 Client ID' }]}>
                      <Input placeholder="GitHub OAuth2 Client ID" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="clientSecret" label="Client Secret（不填则保持不变）">
                      <Input.Password placeholder="GitHub OAuth2 Client Secret" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="redirectUri" label="Redirect URI" rules={[{ required: true, message: '请输入 Redirect URI' }]}>
                      <Input placeholder="http://localhost:8080/login/oauth2/code/github" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
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
            label: '备案信息',
            children: (
              <Form form={siteForm} layout="vertical">
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="publicSecurityRecordNo" label="公网安备号">
                      <Input placeholder="京公网安备11000002000001号" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="icpLicenseNo" label="ICP证号">
                      <Input placeholder="京ICP证030173号" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
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
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="smtpHost" label="SMTP Host">
                      <Input placeholder="smtp.example.com" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 4}>
                    <Form.Item name="smtpPort" label="SMTP Port">
                      <Input placeholder="465/587" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="smtpUsername" label="SMTP 用户名">
                      <Input placeholder="username@example.com" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="smtpPassword" label="SMTP 密码（不填则保持不变）">
                      <Input.Password placeholder="******" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="fromEmail" label="发件人邮箱">
                      <Input placeholder="noreply@example.com" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="enabled" label="启用邮箱通知" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={24}>
                    <Form.Item
                      name="fromDisplayName"
                      label="发件人显示名称（可选）"
                      tooltip="收件人列表里看到的名称，可中文；真实发信地址仍为上方「发件人邮箱」"
                    >
                      <Input placeholder="例如 CipherGate 或 公司名称" maxLength={100} />
                    </Form.Item>
                  </Col>
                </Row>
                <Space align="center" direction={isMobile ? 'vertical' : 'horizontal'} style={{ width: isMobile ? '100%' : undefined }}>
                  <Button type="primary" loading={saving} onClick={submitEmail} block={isMobile}>保存邮箱配置</Button>
                  <Text type="secondary">
                    当前密码状态：{settings?.emailPasswordSet ? '已设置' : '未设置'}
                  </Text>
                </Space>
              </Form>
            )
          },
          {
            key: 'payment',
            label: '支付配置',
            children: (
              <Form form={paymentForm} layout="vertical">
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="epayUrl" label="易支付接口地址" rules={[{ required: true, message: '请输入接口地址' }]}>
                      <Input placeholder="https://pay.ayssu.com" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="epayPid" label="商户ID (PID)" rules={[{ required: true, message: '请输入商户ID' }]}>
                      <Input placeholder="10001" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="epayKey" label="商户密钥（不填则保持不变）">
                      <Input.Password placeholder="输入密钥" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="epayNotifyUrl" label="异步回调地址" rules={[{ required: true, message: '请输入回调地址' }]}>
                      <Input placeholder="https://你的域名/api/payment/notify" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="epayReturnUrl" label="同步跳转地址" rules={[{ required: true, message: '请输入跳转地址' }]}>
                      <Input placeholder="https://你的域名/api/payment/return" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Form.Item name="successRedirectUrl" label="支付成功跳转地址" tooltip="支付成功后前端跳转的页面地址">
                      <Input placeholder="/user/balance" />
                    </Form.Item>
                  </Col>
                </Row>
                <Space align="center" direction={isMobile ? 'vertical' : 'horizontal'} style={{ width: isMobile ? '100%' : undefined }}>
                  <Button type="primary" loading={paymentSaving} onClick={submitPayment} block={isMobile}>保存支付配置</Button>
                  <Text type="secondary">支持易支付协议（支付宝/微信/QQ钱包）</Text>
                </Space>
              </Form>
            )
          },
          {
            key: 'geoip',
            label: 'IP解析',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Title level={5}>ip2region（推荐）</Title>
                <Text type="secondary">
                  ip2region 是一个离线IP地址定位库，支持中国IP精准定位，数据库文件约7MB，查询速度极快。
                </Text>
                <div style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #d0d7de' }}>
                  <Text strong>下载地址：</Text>
                  <br />
                  <Text>
                    <a href="https://github.com/lionsoul2014/ip2region/tree/master/data" target="_blank" rel="noopener noreferrer">
                      https://github.com/lionsoul2014/ip2region/tree/master/data
                    </a>
                  </Text>
                  <br />
                  <Text type="secondary">下载 ip2region.xdb 文件，上传到此处即可。</Text>
                </div>
                <Row gutter={isMobile ? [0, 8] : 16} align="middle">
                  <Col span={isMobile ? 16 : 8}>
                    <Text>启用 ip2region</Text>
                  </Col>
                  <Col span={isMobile ? 8 : 16}>
                    <Switch
                      checked={!!settings?.ip2RegionEnabled}
                      loading={ip2RegionSaving}
                      onChange={toggleIp2Region}
                    />
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 16] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Space>
                      <Text>ip2region 数据库：</Text>
                      <Text type={settings?.ip2RegionUploaded ? 'success' : 'secondary'}>
                        {settings?.ip2RegionUploaded ? '已上传' : '未上传'}
                      </Text>
                    </Space>
                    <div style={{ marginTop: 8 }}>
                      <Upload
                        accept=".xdb"
                        showUploadList={false}
                        beforeUpload={(file) => uploadIp2RegionDb(file)}
                      >
                        <Button icon={<UploadOutlined />} loading={ip2RegionSaving} block={isMobile}>上传 ip2region.xdb</Button>
                      </Upload>
                    </div>
                  </Col>
                </Row>
                <Text type={settings?.ip2RegionReady ? 'success' : 'warning'}>
                  当前状态：{settings?.ip2RegionReady ? '就绪' : '未就绪'}
                  {settings?.ip2RegionLastError ? `（${settings.ip2RegionLastError}）` : ''}
                </Text>

                <Title level={5}>MaxMind GeoIP（备用）</Title>
                <Text type="secondary">
                  MaxMind GeoIP2 是国际通用的IP地理定位库，需要同时上传 Country 和 City 两个数据库文件。
                </Text>
                <div style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #d0d7de' }}>
                  <Text strong>下载地址：</Text>
                  <br />
                  <Text>
                    <a href="https://dev.maxmind.com/geoip/geolite2-free-geolocation-data" target="_blank" rel="noopener noreferrer">
                      https://dev.maxmind.com/geoip/geolite2-free-geolocation-data
                    </a>
                  </Text>
                  <br />
                  <Text type="secondary">
                    需要注册 MaxMind 账号（免费），下载 GeoLite2 Country 和 GeoLite2 City 的 .mmdb 格式文件。
                  </Text>
                </div>
                <Row gutter={isMobile ? [0, 8] : 16} align="middle">
                  <Col span={isMobile ? 16 : 8}>
                    <Text>启用 MaxMind GeoIP</Text>
                  </Col>
                  <Col span={isMobile ? 8 : 16}>
                    <Switch
                      checked={!!settings?.geoIpEnabled}
                      loading={geoIpSaving}
                      onChange={toggleGeoIp}
                    />
                  </Col>
                </Row>
                <Row gutter={isMobile ? [0, 16] : 16}>
                  <Col span={isMobile ? 24 : 12}>
                    <Space>
                      <Text>Country 库：</Text>
                      <Text type={settings?.geoIpCountryUploaded ? 'success' : 'secondary'}>
                        {settings?.geoIpCountryUploaded ? '已上传' : '未上传'}
                      </Text>
                    </Space>
                    <div style={{ marginTop: 8 }}>
                      <Upload
                        accept=".mmdb"
                        showUploadList={false}
                        beforeUpload={(file) => uploadGeoDb('country', file)}
                      >
                        <Button icon={<UploadOutlined />} loading={geoIpSaving} block={isMobile}>上传 Country.mmdb</Button>
                      </Upload>
                    </div>
                  </Col>
                  <Col span={isMobile ? 24 : 12}>
                    <Space>
                      <Text>City 库：</Text>
                      <Text type={settings?.geoIpCityUploaded ? 'success' : 'secondary'}>
                        {settings?.geoIpCityUploaded ? '已上传' : '未上传'}
                      </Text>
                    </Space>
                    <div style={{ marginTop: 8 }}>
                      <Upload
                        accept=".mmdb"
                        showUploadList={false}
                        beforeUpload={(file) => uploadGeoDb('city', file)}
                      >
                        <Button icon={<UploadOutlined />} loading={geoIpSaving} block={isMobile}>上传 City.mmdb</Button>
                      </Upload>
                    </div>
                  </Col>
                </Row>
                <Text type={settings?.geoIpReady ? 'success' : 'warning'}>
                  当前状态：{settings?.geoIpReady ? '就绪' : '未就绪'}
                  {settings?.geoIpLastError ? `（${settings.geoIpLastError}）` : ''}
                </Text>
              </Space>
            ),
          },
          {
            key: 'invite',
            label: '邀请有奖',
            children: (
              <Form form={inviteForm} layout="vertical">
                <Row gutter={isMobile ? [0, 0] : 16}>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="enabled" label="启用邀请功能" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="maxCount" label="最大邀请人数" rules={[{ required: true, message: '请输入最大邀请人数' }]}>
                      <Input type="number" min={1} placeholder="20" />
                    </Form.Item>
                  </Col>
                  <Col span={isMobile ? 24 : 8}>
                    <Form.Item name="rewardAmount" label="每人奖励金额（元）" rules={[{ required: true, message: '请输入奖励金额' }]}>
                      <Input type="number" min={0} step={0.1} placeholder="3" />
                    </Form.Item>
                  </Col>
                </Row>
                <Space>
                  <Button type="primary" loading={inviteSaving} onClick={submitInvite}>保存邀请配置</Button>
                </Space>
              </Form>
            ),
          },
        ]}
      />
    </Card>
  );
};

export default SystemConfigContent;