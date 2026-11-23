import type { ProLayoutProps } from '@ant-design/pro-components';
import { Button } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';

type HeaderRender = Exclude<NonNullable<ProLayoutProps['headerRender']>, boolean>;

/** Pro's mobile trigger is a span; supply a keyboard-operable button without replacing its header. */
const renderHeader: HeaderRender = (props, defaultDom) => {
  if (!props.isMobile) return defaultDom;
  return <div className="ciphergate-mobile-header">
    {defaultDom}
    <Button className="ciphergate-mobile-nav-toggle" type="text"
      aria-label={props.collapsed ? '展开导航' : '收起导航'} aria-expanded={!props.collapsed}
      onClick={() => props.onCollapse?.(!props.collapsed)}
      icon={props.collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} />
  </div>;
};
export default renderHeader;
