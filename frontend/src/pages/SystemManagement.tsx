import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography } from 'antd';
import {
  TeamOutlined,
  SafetyOutlined,
  MenuOutlined,
  LockOutlined,
  SettingOutlined
} from '@ant-design/icons';

const { Sider, Content } = Layout;
const { Title } = Typography;

// 导入组件内容
import {
  UserManagementContent,
  RoleManagementContent,
  MenuManagementContent,
  PermissionManagementContent,
  SystemConfigContent
} from '../components';

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  component: React.ReactNode;
}

const SystemManagement: React.FC = () => {
  const [selectedKey, setSelectedKey] = useState('users');

  // 从URL参数获取当前选中的菜单
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const tab = urlParams.get('tab');
    if (tab && ['users', 'roles', 'menus', 'permissions', 'config'].includes(tab)) {
      setSelectedKey(tab);
    }
  }, []);

  // 更新URL参数
  const handleMenuSelect = (key: string) => {
    setSelectedKey(key);
    const newUrl = `${window.location.pathname}?tab=${key}`;
    window.history.pushState({}, '', newUrl);
  };

  const menuItems: MenuItem[] = [
    {
      key: 'users',
      icon: <TeamOutlined />,
      label: '用户管理',
      component: <UserManagementContent />
    },
    {
      key: 'roles',
      icon: <SafetyOutlined />,
      label: '角色管理',
      component: <RoleManagementContent />
    },
    {
      key: 'menus',
      icon: <MenuOutlined />,
      label: '菜单管理',
      component: <MenuManagementContent />
    },
    {
      key: 'permissions',
      icon: <LockOutlined />,
      label: '权限管理',
      component: <PermissionManagementContent />
    },
    {
      key: 'config',
      icon: <SettingOutlined />,
      label: '系统配置',
      component: <SystemConfigContent />
    }
  ];

  const getCurrentComponent = () => {
    const currentItem = menuItems.find(item => item.key === selectedKey);
    return currentItem?.component || <UserManagementContent />;
  };

  const getCurrentTitle = () => {
    const currentItem = menuItems.find(item => item.key === selectedKey);
    return currentItem?.label || '用户管理';
  };

  return (
    <div style={{ padding: 24, background: '#f5f5f5', minHeight: '100vh' }}>
      <div style={{ maxWidth: 1600, margin: '0 auto' }}>
        <Title level={2} style={{ marginBottom: 24 }}>
          <SettingOutlined /> 系统管理
        </Title>

        <Layout style={{ background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
          <Sider
            width={250}
            style={{
              background: '#fff',
              borderRight: '1px solid #f0f0f0'
            }}
          >
            <Menu
              mode="inline"
              selectedKeys={[selectedKey]}
              style={{ border: 'none', padding: '16px 0' }}
              items={menuItems.map(item => ({
                key: item.key,
                icon: item.icon,
                label: item.label,
                onClick: () => handleMenuSelect(item.key)
              }))}
            />
          </Sider>

          <Layout>
            <Content style={{ padding: 24, minHeight: 600 }}>
              <div style={{ marginBottom: 16 }}>
                <Title level={3} style={{ margin: 0 }}>
                  {getCurrentTitle()}
                </Title>
              </div>
              {getCurrentComponent()}
            </Content>
          </Layout>
        </Layout>
      </div>
    </div>
  );
};

export default SystemManagement;