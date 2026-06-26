import React from 'react';
import { Layout, Card, List, Typography, Avatar, Tag, Space } from 'antd';
import { AppstoreOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { portalAuthApi } from '../services/portalAuthService';

const { Content } = Layout;
const { Title, Text } = Typography;

interface AppInfo {
  appId: number;
  appName: string;
  memberActive?: boolean;
  memberExpiresAt?: string;
}

const PortalAppSelectPage: React.FC = () => {
  const navigate = useNavigate();
  const apps: AppInfo[] = JSON.parse(localStorage.getItem('portal_apps') || '[]');
  const tempToken = localStorage.getItem('portal_temp_token');

  const handleSelect = async (appId: number) => {
    try {
      localStorage.setItem('portal_token', tempToken || '');
      const res: any = await portalAuthApi.selectApp(appId);
      if (res?.data) {
        localStorage.setItem('portal_token', res.data);
        localStorage.setItem('portal_app_id', String(appId));
        localStorage.removeItem('portal_temp_token');
        localStorage.removeItem('portal_apps');
        navigate('/portal/dashboard');
      }
    } catch {
      // error handled by interceptor
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Content style={{ width: '100%', maxWidth: 500, padding: '24px' }}>
        <Card style={{ borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.08)' }}>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <AppstoreOutlined style={{ fontSize: 40, color: '#1890ff' }} />
            <Title level={3} style={{ margin: '12px 0 4px' }}>选择应用</Title>
            <Text type="secondary">该邮箱绑定了多个应用，请选择要进入的应用</Text>
          </div>

          <List
            dataSource={apps}
            renderItem={(item) => (
              <List.Item
                style={{ padding: '12px 16px', cursor: 'pointer', borderRadius: 8, border: '1px solid #f0f0f0', marginBottom: 8 }}
                onClick={() => handleSelect(item.appId)}
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
              </List.Item>
            )}
          />
        </Card>
      </Content>
    </Layout>
  );
};

export default PortalAppSelectPage;
