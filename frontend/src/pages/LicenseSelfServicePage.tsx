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
  Modal,
} from 'antd';
import {
  ArrowLeftOutlined,
  KeyOutlined,
  DisconnectOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  queryLicenseRemaining,
  unbindLicense,
  type PublicLicenseQueryResponse,
} from '../services/licenseSelfServicePublicService';

const { Header, Content, Footer } = Layout;
const { Title, Text, Paragraph } = Typography;

const formatRemaining = (sec: number) => {
  if (sec < 0) return '永久';
  const s = Math.max(0, Math.floor(sec));
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = s % 60;
  if (d > 0) return `${d}天${h}小时${m}分`;
  if (h > 0) return `${h}小时${m}分`;
  if (m > 0) return `${m}分${ss}秒`;
  return `${ss}秒`;
};

const statusTag = (status?: number) => {
  const map: Record<number, { label: string; color: string }> = {
    1: { label: '未使用', color: 'default' },
    2: { label: '使用中', color: 'processing' },
    3: { label: '已到期', color: 'error' },
    4: { label: '已禁用', color: 'error' },
  };
  const v = status != null ? map[status] : undefined;
  return v ? <Tag color={v.color}>{v.label}</Tag> : <Tag>未知</Tag>;
};

/**
 * 卡密自助页（与首页同级路由）。
 * 通过 URL 查询参数 {@code ?id=} 指定所属应用。
 */
const LicenseSelfServicePage: React.FC = () => {
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
  const [unbindLoading, setUnbindLoading] = useState(false);
  const [data, setData] = useState<PublicLicenseQueryResponse | null>(null);

  const keyCode = (form.getFieldValue('keyCode') as string | undefined)?.trim()?.toUpperCase();

  const doQuery = async () => {
    if (!appId) {
      message.error('当前链接无效或已过期');
      return;
    }
    try {
      await form.validateFields(['keyCode']);
    } catch {
      return;
    }
    const keyCode = (form.getFieldValue('keyCode') as string).trim().toUpperCase();
    setLoading(true);
    try {
      const res: any = await queryLicenseRemaining(appId, keyCode);
      if (res.code === 200 && res.data) {
        setData(res.data);
      }
    } catch {
      /* interceptor already shows */
    } finally {
      setLoading(false);
    }
  };

  const doUnbind = async () => {
    if (!appId) {
      message.error('当前链接无效或已过期');
      return;
    }
    if (!keyCode) {
      message.warning('请先输入卡密');
      return;
    }
    Modal.confirm({
      title: '解绑卡密',
      content: (
        <div>
          <p>
            确定要解绑当前卡密的设备与 IP 吗？
          </p>
          <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>
            解绑成功后，可在新设备/新 IP 下重新登录绑定（需卡密开启对应校验）。
          </p>
        </div>
      ),
      okText: '确定解绑',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        setUnbindLoading(true);
        try {
          const res: any = await unbindLicense({
            appId,
            keyCode,
          });
          if (res.code === 200) {
            message.success('解绑成功');
            await doQuery();
          }
        } finally {
          setUnbindLoading(false);
        }
      },
    });
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
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            maxWidth: 1400,
            margin: '0 auto',
            padding: isNarrow ? '0 14px' : '0 24px',
            height: '100%',
            boxSizing: 'border-box',
          }}
        >
          <Link
            to="/"
            style={{
              display: 'flex',
              alignItems: 'center',
              textDecoration: 'none',
              color: 'inherit',
              minWidth: 0,
              flex: 1,
            }}
          >
            <img
              src="/favicon.svg"
              alt="CipherGate"
              style={{ marginRight: 10, width: 32, height: 32, flexShrink: 0 }}
            />
            <span
              style={{
                fontSize: 18,
                fontWeight: 700,
                background: 'linear-gradient(135deg, #00d4aa, #1890ff)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                letterSpacing: '-0.5px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              CipherGate
            </span>
            {!isNarrow && (
              <span
                style={{
                  marginLeft: 16,
                  paddingLeft: 16,
                  borderLeft: '1px solid #e8e8e8',
                  fontSize: 15,
                  fontWeight: 600,
                  color: '#262626',
                  WebkitTextFillColor: '#262626',
                }}
              >
                卡密自助管理
              </span>
            )}
          </Link>
          <Link
            to="/"
            style={{
              color: '#595959',
              fontWeight: 500,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 13,
              flexShrink: 0,
              marginLeft: 8,
            }}
          >
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
                卡密自助管理
              </Title>
              <Paragraph style={{ marginTop: 8, marginBottom: 0, color: '#6b7280', fontSize: 13 }}>
                输入卡密查询剩余到期时间。如需更换设备或网络环境，可执行解绑（默认解绑设备与 IP）。
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
              name="keyCode"
              label="卡密"
              rules={[
                { required: true, message: '请输入卡密' },
                { max: 64, message: '长度不能超过64位' },
                { pattern: /^[A-Z0-9]+$/, message: '只能包含大写字母和数字' },
              ]}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Input
                  size="middle"
                  prefix={<KeyOutlined style={{ color: 'rgba(0,0,0,0.35)' }} />}
                  placeholder="请输入卡密（大写字母与数字）"
                  maxLength={64}
                  disabled={!appId}
                  onPressEnter={() => void doQuery()}
                  style={{ flex: 1, minWidth: 0 }}
                  onChange={(e) => {
                    const v = e.target.value.toUpperCase();
                    form.setFieldValue('keyCode', v);
                  }}
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

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: -10, marginBottom: 8 }}>
              <Button
                type="link"
                danger
                size="small"
                icon={<DisconnectOutlined />}
                onClick={() => void doUnbind()}
                loading={unbindLoading}
                disabled={!appId}
                style={{ padding: 0 }}
              >
                解绑设备与 IP
              </Button>
            </div>

            <div style={{ marginTop: -8, marginBottom: 10 }}>
              <Space size={12} wrap>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  提示：解绑会占用一次解绑次数（若应用设置了上限）
                </Text>
              </Space>
            </div>
          </Form>

          {data && (
            <div style={{ marginTop: 18 }}>
              <Descriptions
                bordered
                size="middle"
                column={1}
                styles={{
                  label: { width: isNarrow ? 120 : 160, background: '#fafafa' },
                  content: { background: '#fff' },
                }}
              >
                <Descriptions.Item label="卡密">
                  <Text style={{ fontFamily: 'Consolas, Monaco, monospace' }}>{data.keyCodeMasked}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="状态">{statusTag(data.status)}</Descriptions.Item>
                <Descriptions.Item label="到期时间">
                  {data.remainingSeconds < 0 ? (
                    <Tag color="green">永久</Tag>
                  ) : data.expiresAt ? (
                    <Text>{new Date(data.expiresAt).toLocaleString('zh-CN')}</Text>
                  ) : (
                    <Text type="secondary">-</Text>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="剩余时间">
                  <Text strong>{formatRemaining(data.remainingSeconds)}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="绑定信息">
                  <Space wrap>
                    <Tag color={data.boundDevice ? 'blue' : 'default'}>{data.boundDevice ? '已绑定设备' : '未绑定设备'}</Tag>
                    <Tag color={data.boundIp ? 'blue' : 'default'}>{data.boundIp ? '已绑定IP' : '未绑定IP'}</Tag>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="解绑次数">
                  <Text>
                    {data.unbindCount} / {data.unbindLimit <= 0 ? '不限' : data.unbindLimit}
                    {data.unbindRemaining < 0 ? '（剩余不限）' : `（剩余 ${data.unbindRemaining} 次）`}
                  </Text>
                </Descriptions.Item>
              </Descriptions>
            </div>
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

export default LicenseSelfServicePage;

