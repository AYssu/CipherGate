import type { ReactNode } from 'react';
import { App, ConfigProvider, Empty } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ThemeProvider } from '../theme';
import { useTheme } from '../theme/context';

function ThemedApp({ children }: { children: ReactNode }) {
  const { themeConfig } = useTheme();
  return <ConfigProvider locale={zhCN} theme={themeConfig} modal={{ classNames: { wrapper: 'ciphergate-overlay' } }}
    renderEmpty={() => <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />}>
    <App>{children}</App>
  </ConfigProvider>;
}
export default function AppProviders({ children }: { children: ReactNode }) {
  return <ThemeProvider><ThemedApp>{children}</ThemedApp></ThemeProvider>;
}
