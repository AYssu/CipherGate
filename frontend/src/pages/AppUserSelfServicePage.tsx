import { usePublicChallenge } from '../components/usePublicChallenge';
import PublicServiceLayout from '../components/PublicServiceLayout';
import React, { useMemo, useState } from 'react';
import AppUserLicenseRechargeForm from '../components/AppUserLicenseRechargeForm';
import { useSearchParams } from 'react-router-dom';
import {
  Typography,
  Form,
  Input,
  Button,
  message,
  Alert,
  Grid,
  Descriptions,
  Tag,
} from 'antd';
import { MailOutlined, SearchOutlined } from '@ant-design/icons';
import {
  queryAppUserExpire,
  type PublicAppUserExpireQueryResponse,
} from '../services/appUserSelfPublicService';

const { Text } = Typography;

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
  const [searchParams] = useSearchParams();
  const appIdRaw = searchParams.get('id');
  const appId = useMemo(() => {
    const n = Number(appIdRaw);
    return Number.isSafeInteger(n) && n > 0 ? n : null;
  }, [appIdRaw]);

  const [form] = Form.useForm<{email: string; password: string}>();
  const challenge = usePublicChallenge('app-user-query');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<PublicAppUserExpireQueryResponse | null>(null);

  const doQuery = async () => {
    if (!appId) {
      message.error('当前链接无效或已过期');
      return;
    }
    try {
      await form.validateFields(['email', 'password']);
    } catch {
      return;
    }
    const verification = challenge.get(); if (!verification) return;
    const email = form.getFieldValue('email').trim().toLowerCase();
    setData(null);
    setLoading(true);
    try {
      const res = await queryAppUserExpire(appId, email, form.getFieldValue('password'), verification);
      if (res.code === 200 && res.data && email === (form.getFieldValue('email') as string)?.trim().toLowerCase()) {
        setData(res.data);
      }
    } catch {
      // The request interceptor displays the error.
    } finally {
      setLoading(false); void challenge.refresh();
    }
  };

  return (
    <PublicServiceLayout title="用户自助管理" description="查询会员到期时间，或使用卡密为账号充值会员时长。" help="请使用应用方提供的专用链接，并填写该应用的注册邮箱。">
            {!appId && (
              <Alert
                type="warning"
                showIcon
                message="链接无效或已过期"
                description="请通过应用方提供的专用入口访问本页面。"
              />
            )}

          <Form name="membership-query" form={form} layout="vertical" requiredMark={false} onFinish={() => void doQuery()} onValuesChange={() => setData(null)}>
            <Form.Item label="邮箱">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Form.Item name="email" noStyle rules={[
                  { required: true, message: '请输入邮箱' },
                  { type: 'email', message: '邮箱格式不正确' },
                ]}>
                <Input
                  size="middle"
                  aria-label="邮箱"
                  prefix={<MailOutlined style={{ color: 'rgba(0,0,0,0.35)' }} />}
                  placeholder="请输入注册邮箱"
                  disabled={!appId}
                  style={{ flex: 1, minWidth: 0 }}
                />
                </Form.Item>
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
            <Form.Item name="password" label="账号密码" rules={[{required: true, message: '请输入该应用账号密码'}]}>
              <Input.Password autoComplete="current-password" maxLength={100} />
            </Form.Item>
            {challenge.fields}
          </Form>

          <AppUserLicenseRechargeForm appId={appId} onSuccess={setData} />

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
    </PublicServiceLayout>
  );
};

export default AppUserSelfServicePage;

