import { useState, useEffect } from 'react';
import { ProLayout, PageContainer } from '@ant-design/pro-components';
import { Button, Dropdown } from 'antd';
import { DashboardOutlined, CrownOutlined, WalletOutlined, OrderedListOutlined, SettingOutlined, LogoutOutlined } from '@ant-design/icons';
import { Link, Navigate, Outlet, useLocation } from '@umijs/max';
import { sessionFetch } from '../../utils/csrf';
import { useTheme } from '../../theme/context';
import ThemeSwitcher from '../../components/ThemeSwitcher';
import NavigationToggle from '../../components/NavigationToggle';
import settings from '../../../config/defaultSettings';
import renderHeader from '../../layouts/renderHeader';
import iconSvg from '../../assets/icons/icon.svg';

const routes = [
  { path: '/portal/dashboard', icon: <DashboardOutlined />, name: '控制台' },
  { path: '/portal/membership', icon: <CrownOutlined />, name: '会员信息' },
  { path: '/portal/recharge', icon: <WalletOutlined />, name: '充值续费' },
  { path: '/portal/orders', icon: <OrderedListOutlined />, name: '订单记录' },
  { path: '/portal/settings', icon: <SettingOutlined />, name: '账号设置' },
];

export default function PortalLayout() {
  const location = useLocation();
  const { themePreset } = useTheme();
  const token = localStorage.getItem('portal_token');
  const email = localStorage.getItem('portal_email') || '';
  const [displayName, setDisplayName] = useState(email.split('@')[0] || '用户');
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => {
    if (!token) return;
    let active = true;
    sessionFetch('/api/portal/settings/profile', { headers: { Authorization: `Bearer ${token}` } })
      .then(response => response.json()).then(response => {
        if (active && response?.data) setDisplayName(response.data.username || response.data.nickname || response.data.email?.split('@')[0] || '用户');
      }).catch(() => {});
    return () => { active = false; };
  }, [token]);
  if (!token) return <Navigate to="/portal/login" replace />;
  const logout = () => {
    for (const key of ['portal_token', 'portal_app_id', 'portal_email']) localStorage.removeItem(key);
    window.location.assign('/portal/login');
  };
  return <ProLayout {...settings} headerRender={renderHeader} title="CipherGate 应用用户" logo={iconSvg}
    colorPrimary={themePreset.color} location={location} route={{ path: '/portal', routes }}
    collapsedButtonRender={value => <NavigationToggle collapsed={Boolean(value)} />}
    collapsed={collapsed} onCollapse={setCollapsed} menu={{ locale: false }}
    menuItemRender={(item, dom, menuProps) => item.path
      ? <Link to={item.path} onClick={() => { if (menuProps.isMobile) item.onClick(); }}>{dom}</Link> : dom}
    avatarProps={{ title: displayName, children: displayName.charAt(0).toUpperCase(),
      src: /^\d+@qq\.com$/.test(email) ? `https://q.qlogo.cn/headimg_dl?dst_uin=${email.split('@')[0]}&spec=640&img_type=jpg` : undefined,
      render: (_, avatar) => <Dropdown trigger={['click']} menu={{ items: [{ key: 'logout', label: '退出登录', icon: <LogoutOutlined />, onClick: logout }] }}>
        <Button type="text" aria-label="用户菜单">{avatar}</Button>
      </Dropdown> }}
    actionsRender={() => [<ThemeSwitcher key="theme" />]}>
    <PageContainer className="ciphergate-portal-page" title={false} breadcrumb={{}}><Outlet /></PageContainer>
  </ProLayout>;
}
