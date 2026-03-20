import React from 'react';
import { Card, Typography, Empty } from 'antd';

const { Title } = Typography;

const PermissionManagementContent: React.FC = () => {
  return (
    <Card>
      <div style={{ marginBottom: 16 }}>
        <Title level={4}>权限管理</Title>
      </div>
      <Empty 
        description="权限管理功能开发中..."
        style={{ padding: '60px 0' }}
      />
    </Card>
  );
};

export default PermissionManagementContent;