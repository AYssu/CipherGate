import type { PortalAppInfo } from '../services/types';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import PublicServiceLayout from '../../components/PublicServiceLayout';
import React from 'react';
import { Alert, Button, List, Typography, Avatar, Tag, Space } from 'antd';
import { AppstoreOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Text } = Typography;

const PortalAppSelectPage: React.FC = () => {
  const navigate = useNavigate();
  const [pending, setPending] = useState<number | null>(null);
  let apps: PortalAppInfo[] = [];
  try {
    const saved: unknown = JSON.parse(localStorage.getItem('portal_apps') || '[]');
    if (Array.isArray(saved)) apps = saved.filter((item): item is PortalAppInfo => !!item && typeof item === 'object' && Number.isSafeInteger(item.appId) && item.appId > 0 && typeof item.appName === 'string');
  } catch { /* An expired or damaged local session can be recovered by logging in. */ }
  const tempToken = localStorage.getItem('portal_temp_token');

  const handleSelect = async (appId: number) => {
    if (!tempToken || pending !== null) return;
    setPending(appId);
    try {
      localStorage.setItem('portal_token', tempToken || '');
      const res = await portalAuthApi.selectApp(appId);
      if (res?.data) {
        localStorage.setItem('portal_token', res.data);
        localStorage.setItem('portal_app_id', String(appId));
        localStorage.removeItem('portal_temp_token');
        localStorage.removeItem('portal_apps');
        navigate('/portal/dashboard');
      }
    } catch {
      // error handled by interceptor
    } finally { setPending(null); }
  };

  return (
    <PublicServiceLayout title="选择应用" description="您的邮箱关联了多个应用，请选择本次需要管理的应用。">
      {!tempToken || !apps.length ? <Alert type="info" message="登录信息已失效" description={<Link to="/portal/login">请重新登录后选择应用</Link>} /> : <>
          <List
            dataSource={apps}
            renderItem={(item) => (
              <List.Item
                style={{ padding: '16px 0', gap: 12, flexWrap: 'wrap' }}
              >
                <List.Item.Meta
                  avatar={<Avatar icon={<AppstoreOutlined />} style={{ backgroundColor: '#1890ff' }} />}
                  title={
                    <Space>
                      <span>{item.appName}</span>
                      {item.memberActive && <Tag color="success" icon={<CheckCircleOutlined />}>会员有效</Tag>}
                    </Space>
                  }
                  description={item.memberExpiresAt ? `到期: ${item.memberExpiresAt}` : '未开通会员'}
                />
                <Button aria-label={`进入${item.appName}`} loading={pending === item.appId} disabled={pending !== null} onClick={() => void handleSelect(item.appId)}>进入应用</Button>
              </List.Item>
            )}
          />
      <Text type="secondary">不同应用的会员和账号信息独立管理。</Text></>}
    </PublicServiceLayout>
  );
};

export default PortalAppSelectPage;
