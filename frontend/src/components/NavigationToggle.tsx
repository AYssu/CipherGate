import { Button } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';

export default function NavigationToggle({ collapsed }: { collapsed: boolean }) {
  return <Button type="text" aria-label={collapsed ? '展开导航' : '收起导航'} aria-expanded={!collapsed}
    icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} />;
}
