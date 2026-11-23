import { useEffect } from 'react';
import { Outlet, useModel } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Result } from 'antd';

/** Session data stays available to all existing management screens through the Umi outlet. */
export default function MainLayout() {
  const { initialState, refresh, loading } = useModel('@@initialState');
  useEffect(() => {
    if (!loading && !initialState?.adminSessionLoaded) void refresh();
  }, [initialState?.adminSessionLoaded, loading, refresh]);
  if (!initialState?.currentUser) return <Result status="warning" title="暂时无法加载账户信息"
    subTitle="请检查网络后重试；登录失效时请重新登录。"
    extra={<Button type="primary" loading={loading} onClick={() => void refresh()}>重新加载</Button>} />;
  return <PageContainer className="ciphergate-page" title={false} breadcrumb={{}}>
    <Outlet context={{ userInfo: initialState.currentUser }} />
  </PageContainer>;
}
