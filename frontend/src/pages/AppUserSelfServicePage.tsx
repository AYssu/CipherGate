import React, { useMemo, useState } from 'react';
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
  Descriptions,
  Tag,
} from 'antd';
import { ArrowLeftOutlined, MailOutlined, SearchOutlined } from '@ant-design/icons';
import {
  queryAppUserExpire,
  type PublicAppUserExpireQueryResponse,
} from '../services/appUserSelfPublicService';

const { Header, Content, Footer } = Layout;
const { Title, Text, Paragraph } = Typography;

const formatRemaining = (sec: number) => {
  const s = Math.max(0, Math.floor(sec));
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}天${h}小时${m}分`;
  if (h > 0) return `${h}小时${m}分`;
  return `${m}分`;
};

const AppUserSelfServicePage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isNarrow = screens.md === false;
  const cardMaxWidth = screens.md === false ? 520 : screens.lg === true ? 760 : 680;
  const [searchParams] = useSearchParams();
  const appIdRaw = searchParams.get('id');
  const appId = useMemo(() => {
    const n = Number(appIdRaw);
    return Number.isFinite(n) && n > 0 ? n : null;
  }, [appIdRaw]);

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<PublicAppUserExpireQueryResponse | null>(null);

  const doQuery = async () => {
    if (!appId) {
      message.error('当前链接无效或已过期');
      return;
    }
    try {
      await form.validateFields(['email']);
    } catch {
      return;
    }
    const email = (form.getFieldValue('email') as string).trim().toLowerCase();
    setLoading(true);
    try {
      const res: any = await queryAppUserExpire(appId, email);
      if (res.code === 200 && res.data) {
        setData(res.data);
      }
    } finally {
      setLoading(false);
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
            justifyContent: 'space-between',
            alignItems: 'center',
            maxWidth: 1400,
            margin: '0 auto',
            padding: isNarrow ? '0 14px' : '0 24px',
            height: '100%',
          }}
        >
          <Link to="/" style={{ display: 'flex', alignItems: 'center', textDecoration: 'none', color: 'inherit' }}>
            <img src="/favicon.svg" alt="CipherGate" style={{ marginRight: 10, width: 32, height: 32 }} />
            <span
              style={{
                fontSize: 18,
                fontWeight: 700,
                background: 'linear-gradient(135deg, #00d4aa, #1890ff)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              CipherGate
            </span>
          </Link>
          <Link to="/" style={{ color: '#595959', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <ArrowLeftOutlined />
            返回
          </Link>
        </div>
      </Header>

      <Content
        style={{
          marginTop: 64,
          minHeight: 'calc(100vh - 64px - 52px)',
          padding: isNarrow ? '28px 14px 28px' : '40px 16px 40px',
          background: '#f6f8fb',
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
            border: '1px solid #eef1f5',
            boxShadow: '0 10px 24px rgba(15, 23, 42, 0.06)',
            background: '#fff',
          }}
          styles={{ body: { padding: isNarrow ? '22px 18px 18px' : '28px 30px 22px' } }}
        >
          <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 18 }}>
            <div>
              <Title level={4} style={{ margin: 0, color: '#111827', fontWeight: 650 }}>
                用户自助管理
              </Title>
              <Paragraph style={{ marginTop: 8, marginBottom: 0, color: '#6b7280', fontSize: 13 }}>
                输入注册邮箱，查询当前账号会员到期时间。
              </Paragraph>
            </div>
            {!appId && (
              <Alert
                type="warning"
                showIcon
                message="链接无效或已过期"
                description="请通过应用方提供的专用入口访问本页面。"
              />
            )}
          </Space>

          <Form form={form} layout="vertical" requiredMark="optional" onFinish={() => void doQuery()}>
            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Input
                  size="middle"
                  prefix={<MailOutlined style={{ color: 'rgba(0,0,0,0.35)' }} />}
                  placeholder="请输入注册邮箱"
                  disabled={!appId}
                  style={{ flex: 1, minWidth: 0 }}
                />
                <Button
                  type="primary"
                  size="middle"
                  icon={<SearchOutlined />}
                  htmlType="submit"
                  loading={loading}
                  disabled={!appId}
                  style={{ flexShrink: 0, minWidth: isNarrow ? 86 : 96 }}
                >
                  查询
                </Button>
              </div>
            </Form.Item>
          </Form>

          {data && (
            <Descriptions
              bordered
              size="middle"
              column={1}
              styles={{
                label: { width: isNarrow ? 120 : 160, background: '#fafafa' },
                content: { background: '#fff' },
              }}
            >
              <Descriptions.Item label="邮箱">
                <Text>{data.emailMasked}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="会员状态">
                {data.memberActive ? <Tag color="processing">有效</Tag> : <Tag>未生效/已到期</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="到期时间">
                {data.memberExpiresAt ? new Date(data.memberExpiresAt).toLocaleString('zh-CN') : <Text type="secondary">未开通会员</Text>}
              </Descriptions.Item>
              <Descriptions.Item label="剩余时长">
                <Text strong>{data.remainingSeconds > 0 ? formatRemaining(data.remainingSeconds) : '0分'}</Text>
              </Descriptions.Item>
            </Descriptions>
          )}
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

export default AppUserSelfServicePage;

