import React from 'react';
import { Card, Table, Tag, Button, message, Typography, Grid, Dropdown, Space } from 'antd';
import { MoreOutlined } from '@ant-design/icons';

const { Title } = Typography;

const AdminOrderPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [orders, setOrders] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => { fetchOrders(); }, []);

  const fetchOrders = () => {
    fetch('/api/admin/payment/orders?page=1&size=50', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setOrders(data.data?.records || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const handleGrant = async (orderId: number) => {
    const res = await fetch(`/api/admin/payment/grant?orderId=${orderId}`, { method: 'POST', credentials: 'include' });
    const data = await res.json();
    if (data.success) { message.success('发放成功'); fetchOrders(); }
    else message.error(data.message);
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'processing', text: '待支付' }, 1: { color: 'success', text: '已支付' },
    2: { color: 'default', text: '已取消' }, 3: { color: 'warning', text: '已退款' }, 4: { color: 'error', text: '失败' },
  };

  const columns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'no' },
    { title: '用户ID', dataIndex: 'userId', key: 'userId' },
    { title: '商品', dataIndex: 'productName', key: 'product' },
    { title: '金额', dataIndex: 'totalAmount', key: 'amount', render: (v: number) => `¥${(v / 100).toFixed(2)}` },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
    { title: '时间', dataIndex: 'createdAt', key: 'time' },
    {
      title: '操作', key: 'action', width: isMobile ? 80 : undefined,
      render: (_: any, record: any) => {
        if (record.status !== 0) return null;
        if (isMobile) {
          return (
            <Dropdown
              menu={{ items: [{ key: 'grant', label: '手动发放', onClick: () => handleGrant(record.id) }] }}
              trigger={['click']}
            >
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          );
        }
        return <Button type="link" onClick={() => handleGrant(record.id)}>手动发放</Button>;
      },
    },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <Title level={isMobile ? 5 : 4}>订单管理</Title>
        <Table columns={columns} dataSource={orders} rowKey="id" loading={loading} pagination={{ simple: isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 条` }} scroll={{ x: isMobile ? 300 : undefined }} size={isMobile ? 'small' : 'middle'} />
      </Card>
    </div>
  );
};

export default AdminOrderPage;
