import PublicServiceLayout from '../components/PublicServiceLayout';
import React, { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Typography,
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
  KeyOutlined,
  DisconnectOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  queryLicenseRemaining,
  unbindLicense,
  type PublicLicenseQueryResponse,
} from '../services/licenseSelfServicePublicService';

const { Text } = Typography;

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
  const [searchParams] = useSearchParams();
  const appIdRaw = searchParams.get('id');
  const appId = useMemo(() => {
    const n = Number(appIdRaw);
    return Number.isSafeInteger(n) && n > 0 ? n : null;
  }, [appIdRaw]);

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [unbindLoading, setUnbindLoading] = useState(false);
  const [data, setData] = useState<PublicLicenseQueryResponse | null>(null);

  const keyCode = Form.useWatch('keyCode', form)?.trim()?.toUpperCase();
  const [queriedKey, setQueriedKey] = useState('');

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
    setData(null);
    setQueriedKey('');
    setLoading(true);
    try {
      const res: any = await queryLicenseRemaining(appId, keyCode);
      if (res.code === 200 && res.data && keyCode === (form.getFieldValue('keyCode') as string)?.trim().toUpperCase()) {
        setData(res.data);
        setQueriedKey(keyCode);
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
    if (!keyCode || !data || keyCode !== queriedKey) {
      message.warning('请先查询当前卡密');
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
    <PublicServiceLayout title="卡密自助管理" description="查询卡密有效期与绑定状态，需要更换设备时可在这里解绑。" help="卡密是您的使用凭证，请勿分享给他人。解绑可能消耗次数，确认后再操作。">
            {!appId && (
              <Alert
                type="warning"
                showIcon
                message="链接无效或已过期"
                description="请通过应用方提供的专用入口访问本页面。"
              />
            )}

          <Form form={form} layout="vertical" requiredMark={false} onFinish={() => void doQuery()} onValuesChange={() => setData(null)}>
            <Form.Item label="卡密">
              <div className="public-service-row">
                <Form.Item name="keyCode" noStyle normalize={(value: string) => value.toUpperCase()}
                  rules={[{ required: true, message: '请输入卡密' }, { max: 64, message: '长度不能超过64位' }, { pattern: /^[A-Z0-9]+$/, message: '只能包含字母和数字' }]}>
                  <Input.Password aria-label="卡密" prefix={<KeyOutlined />} placeholder="请输入卡密" maxLength={64}
                    autoComplete="off" disabled={!appId} style={{ flex: 1, minWidth: 0 }} />
                </Form.Item>
                <Button type="primary" icon={<SearchOutlined />} htmlType="submit" loading={loading} disabled={!appId}>查询</Button>
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
                disabled={!appId || !data || keyCode !== queriedKey || !!data.redeemedAt || !(data.boundDevice || data.boundIp) || data.unbindRemaining === 0}
                style={{ padding: 0, minHeight: 44 }}
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
                <Descriptions.Item label="状态">{data.redeemedAt ? <Tag color="purple">已兑换为用户会员</Tag> : statusTag(data.status)}</Descriptions.Item>
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
    </PublicServiceLayout>
  );
};

export default LicenseSelfServicePage;

