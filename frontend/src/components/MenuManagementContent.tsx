import React from 'react';
import { Card, Typography, Empty } from 'antd';

const { Title } = Typography;

const MenuManagementContent: React.FC = () => {
  return (
    <Card>
      <div style={{ marginBottom: 16 }}>
        <Title level={4}>菜单管理</Title>
      </div>
      <Empty 
        description="菜单管理功能开发中..."
        style={{ padding: '60px 0' }}
      />
    </Card>
  );
};

export default MenuManagementContent;