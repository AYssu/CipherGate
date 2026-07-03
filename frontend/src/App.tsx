import React from 'react';
import { ConfigProvider, Empty } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { ThemeProvider, useTheme } from './theme';
import '@uiw/react-md-editor/markdown-editor.css';
import './App.css';

const antdZhCN = {
  ...zhCN,
  Pagination: {
    ...zhCN.Pagination,
    items_per_page: '条/页',
    jump_to: '跳至',
    jump_to_confirm: '确定',
    page: '页',
  },
};

const ThemedApp: React.FC = () => {
  const { themeConfig } = useTheme();

  return (
    <ConfigProvider
      locale={antdZhCN}
      theme={themeConfig}
      renderEmpty={() => <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  );
};

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  );
};

export default App;
