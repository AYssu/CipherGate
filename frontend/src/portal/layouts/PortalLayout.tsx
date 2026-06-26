import React, { useState, useEffect } from 'react';
import { Layout, Menu, Button, Drawer, Grid, Typography, Avatar, Dropdown } from 'antd';
import {
  DashboardOutlined,
  CrownOutlined,
  WalletOutlined,
  OrderedListOutlined,
  SettingOutlined,
  MenuOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const menuItems = [
  { key: '/portal/dashboard', icon: <DashboardOutlined />, label: '控制台' },
  { key: '/portal/membership', icon: <CrownOutlined />, label: '会员信息' },
  { key: '/portal/recharge', icon: <WalletOutlined />, label: '充值续费' },
  { key: '/portal/orders', icon: <OrderedListOutlined />, label: '订单记录' },
  { key: '/portal/settings', icon: <SettingOutlined />, label: '账号设置' },
];

function getAvatarUrl(email: string): string | undefined {
  if (email && email.endsWith('@qq.com')) {
    const qqNumber = email.split('@')[0];
    return `http://q.qlogo.cn/headimg_dl?dst_uin=${qqNumber}&spec=640&img_type=jpg`;
  }
  return undefined;
}

const PortalLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [displayName, setDisplayName] = useState('');

  useEffect(() => {
    const token = localStorage.getItem('portal_token');
    if (!token) {
      navigate('/portal/login');
      return;
    }
    const email = localStorage.getItem('portal_email') || '';
    setDisplayName(email.split('@')[0] || email);
    // 获取用户名
    fetch('/api/portal/settings/profile', {
      headers: { Authorization: `Bearer ${token}` }
    }).then(r => r.json()).then(res => {
      if (res?.data?.username) {
        setDisplayName(res.data.username);
      } else if (res?.data?.nickname) {
        setDisplayName(res.data.nickname);
      } else if (res?.data?.email) {
        setDisplayName(res.data.email.split('@')[0]);
      }
    }).catch(() => {});
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('portal_token');
    localStorage.removeItem('portal_app_id');
    localStorage.removeItem('portal_email');
    navigate('/portal/login');
  };

  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key || '/portal/dashboard';
  const email = localStorage.getItem('portal_email') || '用户';
  const avatarUrl = getAvatarUrl(email);

  const siderContent = (
    <>
      <div style={{ padding: '16px', textAlign: 'center', borderBottom: '1px solid #f0f0f0' }}>
        {avatarUrl ? (
          <Avatar src={avatarUrl} size={48} style={{ marginBottom: 8 }} />
        ) : (
          <Avatar
            size={48}
            style={{ backgroundColor: '#1890ff', marginBottom: 8, fontSize: 20, fontWeight: 600 }}
          >
            {displayName ? displayName.charAt(0).toUpperCase() : '?'}
          </Avatar>
        )}
        <div>
          <Text strong style={{ display: 'block', fontSize: 14 }}>{displayName || '用户'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>应用用户</Text>
        </div>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={menuItems}
        onClick={({ key }) => {
          navigate(key);
          if (isMobile) setDrawerOpen(false);
        }}
        style={{ borderRight: 0 }}
      />
    </>
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {isMobile ? (
        <Drawer placement="left" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={240} bodyStyle={{ padding: 0 }}>
          {siderContent}
        </Drawer>
      ) : (
        <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={220} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
          {siderContent}
        </Sider>
      )}

      <Layout>
        {isMobile ? (
          <Header style={{ background: '#fff', padding: '0 8px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0', height: 48, lineHeight: '48px', overflow: 'hidden', flexShrink: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, minWidth: 0, overflow: 'hidden' }}>
              <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} style={{ flexShrink: 0 }} />
              <Text strong style={{ fontSize: 14 }}>CipherGate</Text>
            </div>
            <Dropdown menu={{ items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout }] }}>
              <div style={{ flexShrink: 0, cursor: 'pointer' }}>
                {avatarUrl ? (
                  <Avatar src={avatarUrl} size={28} />
                ) : (
                  <Avatar size={28} style={{ backgroundColor: '#1890ff', fontSize: 12 }}>
                    {displayName ? displayName.charAt(0).toUpperCase() : '?'}
                  </Avatar>
                )}
              </div>
            </Dropdown>
          </Header>
        ) : (
          <Header style={{ background: '#fff', padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0' }}>
            <Text strong style={{ fontSize: 16 }}>CipherGate 应用用户</Text>
            <Dropdown menu={{ items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout }] }}>
              <Button type="text" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {avatarUrl ? (
                  <Avatar src={avatarUrl} size={24} />
                ) : (
                  <Avatar size={24} style={{ backgroundColor: '#1890ff', fontSize: 12 }}>
                    {displayName ? displayName.charAt(0).toUpperCase() : '?'}
                  </Avatar>
                )}
                <span>{displayName || '用户'}</span>
              </Button>
            </Dropdown>
          </Header>
        )}

        <Content style={{ margin: isMobile ? 12 : 24, padding: isMobile ? 12 : 24, background: '#f0f2f5', minHeight: 280 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default PortalLayout;
