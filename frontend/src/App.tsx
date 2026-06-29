import React from 'react';
import { ConfigProvider, Empty } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
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

const App: React.FC = () => {
  return (
    <ConfigProvider
      locale={antdZhCN}
      renderEmpty={() => <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  );
};

export default App;