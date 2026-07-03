import React from 'react';
import { Card, Table, Button, InputNumber, Space, message, Typography, Row, Col, Statistic, Tag, Modal, Grid } from 'antd';
import { WalletOutlined, CrownOutlined } from '@ant-design/icons';
import M5BottomSheet from '../../components/M5BottomSheet';

const { Text } = Typography;

const BalancePage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [balance, setBalance] = React.useState(0);
  const [transactions, setTransactions] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [rechargeVisible, setRechargeVisible] = React.useState(false);
  const [rechargeAmount, setRechargeAmount] = React.useState<number>(10);
  const [products, setProducts] = React.useState<any[]>([]);
  const [selectedProduct, setSelectedProduct] = React.useState<any>(null);
  const [purchaseVisible, setPurchaseVisible] = React.useState(false);

  React.useEffect(() => {
    fetchBalance();
    fetchTransactions();
    fetchProducts();
  }, []);

  const fetchBalance = () => {
    fetch('/api/user/membership/balance', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setBalance(data.data || 0); });
  };

  const fetchTransactions = () => {
    setLoading(true);
    fetch('/api/user/membership/transactions?page=1&size=50', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setTransactions(data.data?.records || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const fetchProducts = () => {
    fetch('/api/quota-products/public', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setProducts(data.data || []); });
  };

  const handleRecharge = async () => {
    if (!rechargeAmount || rechargeAmount <= 0) {
      message.error('请输入有效金额');
      return;
    }
    try {
      const res = await fetch(`/api/payment/recharge?amount=${rechargeAmount * 100}`, {
        method: 'POST', credentials: 'include'
      });
      const data = await res.json();
      if (data.success && data.data?.payUrl) {
        window.location.href = data.data.payUrl;
      } else {
        message.error(data.message || '创建充值订单失败');
      }
    } catch {
      message.error('请求失败，请检查网络');
    }
    setRechargeVisible(false);
  };

  const handlePurchase = async () => {
    if (!selectedProduct) return;
    const res = await fetch(`/api/payment/create?productId=${selectedProduct.id}&quantity=1`, {
      method: 'POST', credentials: 'include'
    });
    const data = await res.json();
    if (data.success) {
      message.success('购买成功');
      setPurchaseVisible(false);
      fetchBalance();
      fetchTransactions();
    } else {
      message.error(data.message);
    }
  };

  const typeMap: Record<string, { color: string; text: string }> = {
    RECHARGE: { color: 'green', text: '充值' },
    PURCHASE: { color: 'blue', text: '消费' },
    INVITE_REWARD: { color: 'gold', text: '邀请奖励' },
    CHECKIN_REWARD: { color: 'cyan', text: '签到奖励' },
    ADMIN_GRANT: { color: 'purple', text: '管理员充值' },
    REFUND: { color: 'orange', text: '退款' },
  };

  const productTypeMap: Record<string, { color: string; text: string }> = {
    APP_QUOTA: { color: 'blue', text: '应用额度' },
    LICENSE_QUOTA: { color: 'cyan', text: '卡密额度' },
    USER_REGISTER_QUOTA: { color: 'green', text: '用户注册额度' },
    TRAFFIC_QUOTA: { color: 'orange', text: '流量额度' },
    MEMBERSHIP: { color: 'gold', text: '会员升级' },
  };

  const columns = [
    { title: '类型', dataIndex: 'transactionType', key: 'type', render: (v: string) => <Tag color={typeMap[v]?.color}>{typeMap[v]?.text || v}</Tag> },
    { title: '金额', dataIndex: 'amount', key: 'amount', render: (v: number) => <span style={{ color: v > 0 ? '#52c41a' : '#ff4d4f', fontWeight: 500 }}>{v > 0 ? '+' : ''}{(v / 100).toFixed(2)}元</span> },
    ...(!isMobile ? [{ title: '余额', dataIndex: 'balanceAfter', key: 'balance', render: (v: number) => `${(v / 100).toFixed(2)}元` }] : []),
    { title: '描述', dataIndex: 'description', key: 'desc', ellipsis: true },
    { title: '时间', dataIndex: 'createdAt', key: 'time', width: isMobile ? 120 : 170, render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={isMobile ? 12 : 24} align="middle">
          <Col flex="auto">
            <Statistic title="当前余额" value={(balance / 100).toFixed(2)} prefix="¥" valueStyle={{ fontSize: isMobile ? 24 : 32, color: '#1890ff' }} />
          </Col>
          <Col>
            <Space direction={isMobile ? 'vertical' : 'horizontal'} size={8}>
              <Button type="primary" size={isMobile ? 'small' : 'large'} icon={<WalletOutlined />} onClick={() => setRechargeVisible(true)}>充值余额</Button>
              <Button size={isMobile ? 'small' : 'large'} icon={<CrownOutlined />} onClick={() => setPurchaseVisible(true)}>购买额度/会员</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card title="余额流水">
        <Table
          columns={columns}
          dataSource={transactions}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 15,
            simple: isMobile,
            showTotal: isMobile ? undefined : (total) => `共 ${total} 条`
          }}
          scroll={{ x: isMobile ? 460 : undefined, y: isMobile ? 450 : undefined }}
          size={isMobile ? 'small' : 'middle'}
        />
      </Card>

      {isMobile ? (
        <M5BottomSheet
          open={rechargeVisible}
          onClose={() => setRechargeVisible(false)}
          title="充值余额"
          footer={
            <>
              <Button onClick={() => setRechargeVisible(false)} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" onClick={handleRecharge} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button>
            </>
          }
        >
          <div style={{ padding: '8px 0' }}>
            <Text style={{ fontSize: 15, fontWeight: 500 }}>请输入充值金额：</Text>
            <div style={{ margin: '16px 0' }}>
              <InputNumber
                min={1}
                max={10000}
                value={rechargeAmount}
                onChange={(v) => setRechargeAmount(v || 10)}
                addonBefore="¥"
                style={{ width: '100%' }}
                size="large"
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 16 }}>
              {[10, 20, 50, 100, 200, 500].map(amount => (
                <Button
                  key={amount}
                  type={rechargeAmount === amount ? 'primary' : 'default'}
                  size="large"
                  style={{ height: 48, borderRadius: 10, fontSize: 16, fontWeight: 600 }}
                  onClick={() => setRechargeAmount(amount)}
                >
                  ¥{amount}
                </Button>
              ))}
            </div>
            <div style={{ padding: '10px 12px', background: '#f0f5ff', borderRadius: 8, border: '1px solid #91d5ff' }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                充值将跳转到支付页面，支持支付宝扫码支付。
              </Text>
            </div>
          </div>
        </M5BottomSheet>
      ) : (
        <Modal title="充值余额" open={rechargeVisible} onOk={handleRecharge} onCancel={() => setRechargeVisible(false)} width={520}>
          <div style={{ padding: '16px 0' }}>
            <Text>请输入充值金额：</Text>
            <div style={{ margin: '16px 0' }}>
              <Space>
                <InputNumber
                  min={1}
                  max={10000}
                  value={rechargeAmount}
                  onChange={(v) => setRechargeAmount(v || 10)}
                  addonBefore="¥"
                  style={{ width: 200 }}
                  size="large"
                />
              </Space>
            </div>
            <Space wrap size="middle">
              {[10, 20, 50, 100, 200, 500].map(amount => (
                <Button key={amount} type={rechargeAmount === amount ? 'primary' : 'default'} size="middle" onClick={() => setRechargeAmount(amount)}>
                  ¥{amount}
                </Button>
              ))}
            </Space>
            <div style={{ marginTop: 16, padding: '8px 12px', background: '#f0f5ff', borderRadius: 6, border: '1px solid #91d5ff' }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                充值将跳转到支付页面，支持支付宝扫码支付。
              </Text>
            </div>
          </div>
        </Modal>
      )}

      {isMobile ? (
        <M5BottomSheet
          open={purchaseVisible}
          onClose={() => setPurchaseVisible(false)}
          title="购买额度/会员"
          footer={
            <>
              <Button onClick={() => setPurchaseVisible(false)} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" onClick={handlePurchase} disabled={!selectedProduct} style={{ flex: 1, height: 44, borderRadius: 10 }}>确认购买</Button>
            </>
          }
        >
          <div style={{ padding: '8px 0' }}>
            {['APP_QUOTA', 'LICENSE_QUOTA', 'USER_REGISTER_QUOTA', 'TRAFFIC_QUOTA', 'MEMBERSHIP'].map(type => {
              const items = products.filter(p => p.productType === type);
              if (items.length === 0) return null;
              return (
                <div key={type} style={{ marginBottom: 16 }}>
                  <Text strong style={{ fontSize: 14 }}>
                    <Tag color={productTypeMap[type]?.color}>{productTypeMap[type]?.text}</Tag>
                  </Text>
                  <div style={{ marginTop: 8 }}>
                    {items.map(p => (
                      <div
                        key={p.id}
                        onClick={() => setSelectedProduct(p)}
                        style={{
                          display: 'block',
                          padding: '6px 10px',
                          margin: '4px 0',
                          border: selectedProduct?.id === p.id ? '2px solid #1890ff' : '1px solid #d9d9d9',
                          borderRadius: 8,
                          cursor: 'pointer',
                          background: selectedProduct?.id === p.id ? '#e6f7ff' : '#fff',
                          transition: 'all 0.2s',
                        }}
                      >
                        <div style={{ fontWeight: 500 }}>{p.productName}</div>
                        <div style={{ color: '#1890ff', fontSize: 16, fontWeight: 600 }}>¥{(p.price / 100).toFixed(2)}</div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
            {selectedProduct && (
              <div style={{ marginTop: 16, padding: '12px', background: '#f6ffed', borderRadius: 6, border: '1px solid #b7eb8f' }}>
                <Text>将使用余额购买：<Text strong>{selectedProduct.productName}</Text>，扣款：<Text strong style={{ color: '#ff4d4f' }}>¥{(selectedProduct.price / 100).toFixed(2)}</Text></Text>
              </div>
            )}
          </div>
        </M5BottomSheet>
      ) : (
        <Modal title="购买额度/会员" open={purchaseVisible} onOk={handlePurchase} onCancel={() => setPurchaseVisible(false)} okText="确认购买" okButtonProps={{ disabled: !selectedProduct }} width={520}>
          <div style={{ padding: '8px 0' }}>
            {['APP_QUOTA', 'LICENSE_QUOTA', 'USER_REGISTER_QUOTA', 'TRAFFIC_QUOTA', 'MEMBERSHIP'].map(type => {
              const items = products.filter(p => p.productType === type);
              if (items.length === 0) return null;
              return (
                <div key={type} style={{ marginBottom: 16 }}>
                  <Text strong style={{ fontSize: 14 }}>
                    <Tag color={productTypeMap[type]?.color}>{productTypeMap[type]?.text}</Tag>
                  </Text>
                  <div style={{ marginTop: 8 }}>
                    {items.map(p => (
                      <div
                        key={p.id}
                        onClick={() => setSelectedProduct(p)}
                        style={{
                          display: 'inline-block',
                          padding: '8px 16px',
                          margin: '4px 8px 4px 0',
                          border: selectedProduct?.id === p.id ? '2px solid #1890ff' : '1px solid #d9d9d9',
                          borderRadius: 8,
                          cursor: 'pointer',
                          background: selectedProduct?.id === p.id ? '#e6f7ff' : '#fff',
                          transition: 'all 0.2s',
                        }}
                      >
                        <div style={{ fontWeight: 500 }}>{p.productName}</div>
                        <div style={{ color: '#1890ff', fontSize: 16, fontWeight: 600 }}>¥{(p.price / 100).toFixed(2)}</div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
            {selectedProduct && (
              <div style={{ marginTop: 16, padding: '12px', background: '#f6ffed', borderRadius: 6, border: '1px solid #b7eb8f' }}>
                <Text>将使用余额购买：<Text strong>{selectedProduct.productName}</Text>，扣款：<Text strong style={{ color: '#ff4d4f' }}>¥{(selectedProduct.price / 100).toFixed(2)}</Text></Text>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
};

export default BalancePage;
