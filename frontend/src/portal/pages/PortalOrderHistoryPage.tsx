import type { PortalOrder } from '../services/types';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Typography, Table, Tag, Alert, Button, Empty, Space } from 'antd';
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
  const [orders, setOrders] = useState<PortalOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [requestedPage, setRequestedPage] = useState(1);
  const [returnedFromPayment, setReturnedFromPayment] = useState(false);
  const pending = useRef(false);
  const [searchParams, setSearchParams] = useSearchParams();

  const loadOrders = useCallback(async (targetPage: number) => {
    if (pending.current) return;
    pending.current = true;
    setRequestedPage(targetPage);
    setLoading(true);
    setLoadFailed(false);
    try {
      const res = await portalPaymentApi.getOrders(targetPage, 20);
      setOrders(res?.data || []);
      setPage(targetPage);
      setLoaded(true);
    } catch {
      // Keep the last successful page visible and label it as stale.
      setLoadFailed(true);
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadOrders(1); }, [loadOrders]);

  useEffect(() => {
    if (searchParams.has('trade_status') || searchParams.has('out_trade_no')) {
      // A return URL is editable; only the server's order state confirms payment.
      setReturnedFromPayment(true);
      const clean = new URLSearchParams(searchParams);
      clean.delete('trade_status');
      clean.delete('out_trade_no');
      setSearchParams(clean, { replace: true });
    }
  }, [searchParams, setSearchParams]);

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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>订单记录</Title>
        <Button onClick={() => void loadOrders(page)} disabled={loading}>刷新订单</Button>
      </div>
      {returnedFromPayment && <Alert style={{ marginBottom: 16 }} type="info" showIcon
        message="已返回订单页面" description="支付结果以订单状态为准。如仍显示待支付，请稍后刷新订单。" />}
      {loadFailed && <Alert style={{ marginBottom: 16 }} type="error" showIcon message="订单加载失败"
        description={loaded ? `当前保留第 ${page} 页的上次加载结果，请重试以获取最新状态。` : '暂时无法获取订单记录，请重试。'}
        action={<Button onClick={() => void loadOrders(requestedPage)}>重新加载</Button>} />}
      <Card>
        <Table
          dataSource={orders}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: loadFailed ? '订单尚未加载成功' : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无订单记录" /> }}
          scroll={{ x: 700 }}
        />
        {loaded && <Space wrap style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <Typography.Text type="secondary">第 {page} 页 · 本页 {orders.length} 条</Typography.Text>
          <Button disabled={loading || page <= 1} onClick={() => void loadOrders(page - 1)}>上一页</Button>
          <Button disabled={loading || orders.length < 20} onClick={() => void loadOrders(page + 1)}>下一页</Button>
        </Space>}
      </Card>
    </div>
  );
};

export default PortalOrderHistoryPage;
