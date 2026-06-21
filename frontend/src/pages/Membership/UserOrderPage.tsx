import React from 'react';
import { Card, Table, Tag, Typography } from 'antd';

const { Title } = Typography;

const UserOrderPage: React.FC = () => {
  const [orders, setOrders] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    fetch('/api/payment/orders?page=1&size=20', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setOrders(data.data?.records || []); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'processing', text: '待支付' },
    1: { color: 'success', text: '已支付' },
    2: { color: 'default', text: '已取消' },
    3: { color: 'warning', text: '已退款' },
    4: { color: 'error', text: '支付失败' },
  };

  const columns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo' },
    { title: '商品', dataIndex: 'productName', key: 'product' },
    { title: '金额', dataIndex: 'totalAmount', key: 'amount', render: (v: number) => `¥${(v / 100).toFixed(2)}` },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
    { title: '时间', dataIndex: 'createdAt', key: 'time' },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <Title level={4}>我的订单</Title>
        <Table columns={columns} dataSource={orders} rowKey="id" loading={loading} pagination={{ pageSize: 10 }} />
      </Card>
    </div>
  );
};

export default UserOrderPage;
