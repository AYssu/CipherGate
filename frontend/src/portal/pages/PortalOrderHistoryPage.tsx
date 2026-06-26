import React, { useEffect, useState } from 'react';
import { Card, Typography, Table, Tag, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { portalPaymentApi } from '../services/portalPaymentService';

const { Title } = Typography;

const statusMap: Record<number, { color: string; text: string }> = {
  0: { color: 'processing', text: '待支付' },
  1: { color: 'success', text: '已支付' },
  2: { color: 'default', text: '已关闭' },
  3: { color: 'error', text: '已退款' },
  4: { color: 'warning', text: '已超时' },
};

const PortalOrderHistoryPage: React.FC = () => {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [searchParams] = useSearchParams();

  useEffect(() => {
    // 处理同步跳转参数（支付网关带回的）
    const tradeStatus = searchParams.get('trade_status');
    const orderNo = searchParams.get('out_trade_no');

    if (tradeStatus && orderNo) {
      if (tradeStatus === 'TRADE_SUCCESS' || tradeStatus === 'TRADE_FINISHED') {
        message.success('支付成功！订单号：' + orderNo);
      } else {
        message.warning('支付状态：' + tradeStatus);
      }
      // 清除URL参数，跳转到干净地址
      const successUrl = localStorage.getItem('portal_success_url') || '/portal/orders';
      window.history.replaceState({}, '', successUrl);
    }

    loadOrders();
  }, [page]);

  const loadOrders = async () => {
    setLoading(true);
    try {
      const res: any = await portalPaymentApi.getOrders(page, 20);
      setOrders(res?.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', ellipsis: true },
    { title: '方案', dataIndex: 'planName', key: 'planName' },
    { title: '金额', dataIndex: 'amountFen', key: 'amountFen', render: (v: number) => `¥${(v / 100).toFixed(2)}` },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: number) => { const s = statusMap[v]; return s ? <Tag color={s.color}>{s.text}</Tag> : '-'; } },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', render: (v: string) => v?.replace('T', ' ').slice(0, 19) },
    { title: '支付时间', dataIndex: 'paidAt', key: 'paidAt', render: (v: string) => v ? v.replace('T', ' ').slice(0, 19) : '-' },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>订单记录</Title>
      <Card>
        <Table
          dataSource={orders}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ current: page, onChange: setPage, pageSize: 20 }}
          scroll={{ x: 700 }}
        />
      </Card>
    </div>
  );
};

export default PortalOrderHistoryPage;
