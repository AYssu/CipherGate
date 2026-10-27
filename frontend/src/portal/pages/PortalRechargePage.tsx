import type { PortalPricingPlan } from '../services/types';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Typography, Row, Col, Button, Tag, message, Modal, Alert, Empty } from 'antd';
import { CrownOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { portalMembershipApi } from '../services/portalMembershipService';
import { portalPaymentApi } from '../services/portalPaymentService';

const { Title, Text } = Typography;

const PortalRechargePage: React.FC = () => {
  const [plans, setPlans] = useState<PortalPricingPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [ordering, setOrdering] = useState<number | null>(null);
  const navigate = useNavigate();
  const [loadFailed, setLoadFailed] = useState(false);
  const orderPending = useRef(false);

  const loadPlans = useCallback(async () => {
    setLoading(true);
    setLoadFailed(false);
    try {
      const res = await portalMembershipApi.getPlans();
      setPlans(res?.data || []);
    } catch {
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadPlans(); }, [loadPlans]);

  const handleOrder = async (plan: PortalPricingPlan) => {
    if (orderPending.current) return;
    orderPending.current = true;
    setOrdering(plan.id);
    let dialogOpened = false;
    const releaseOrder = () => { orderPending.current = false; setOrdering(null); };
    try {
      const res = await portalPaymentApi.createOrder(plan.id);
      if (res?.data) {
        dialogOpened = true;
        Modal.confirm({
          title: '确认支付',
          icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
          content: (
            <div style={{ overflowWrap: 'anywhere' }}>
              <p>订单已创建。选择稍后支付仍会保留待支付订单，可在订单记录中查看。</p>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <Text type="secondary">订单号</Text>
                <Text>{res.data.orderNo}</Text>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <Text type="secondary">商品</Text>
                <Text>{res.data.planName}</Text>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
                <Text type="secondary">金额</Text>
                <Text strong style={{ color: '#ff4d4f', fontSize: 18 }}>¥{(res.data.amount / 100).toFixed(2)}</Text>
              </div>
            </div>
          ),
          okText: '去支付',
          cancelText: '稍后支付',
          afterClose: releaseOrder,
          onOk: () => {
            if (res.data.payUrl) {
              window.location.href = res.data.payUrl;
            } else {
              message.info('订单已创建，请前往订单记录查看');
              navigate('/portal/orders');
            }
          },
        });
      } else {
        message.error('订单创建结果不完整，请先查看订单记录，避免重复下单');
      }
    } catch {
      // error handled by interceptor
    } finally {
      if (!dialogOpened) releaseOrder();
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>充值续费</Title>
        <Link to="/portal/orders">查看订单记录</Link>
      </div>

      {loading ? (
        <Card loading />
      ) : loadFailed ? (
        <Alert type="error" showIcon message="购买方案加载失败"
          description="暂时无法获取购买方案，请重试。"
          action={<Button onClick={() => void loadPlans()}>重新加载</Button>} />
      ) : plans.length === 0 ? (
        <Card><Empty description="该应用暂无可购买的方案" image={Empty.PRESENTED_IMAGE_SIMPLE} /></Card>
      ) : (
        <Row gutter={[16, 16]}>
          {plans.map((plan) => (
            <Col xs={24} sm={12} lg={8} key={plan.id}>
              <Card
                hoverable
                style={{ textAlign: 'center' }}
                actions={[
                  <Button type="primary" loading={ordering === plan.id} disabled={ordering !== null} onClick={() => handleOrder(plan)}>
                    购买
                  </Button>,
                ]}
              >
                <CrownOutlined style={{ fontSize: 32, color: '#faad14', marginBottom: 12 }} />
                <Title level={4} style={{ margin: 0 }}>{plan.planName}</Title>
                <div style={{ margin: '16px 0' }}>
                  <Text style={{ fontSize: 28, fontWeight: 700, color: '#ff4d4f' }}>
                    ¥{(plan.priceFen / 100).toFixed(2)}
                  </Text>
                </div>
                <Tag color={plan.planType === 'PERMANENT' ? 'gold' : 'blue'}>
                  {plan.planType === 'PERMANENT' ? '永久' : `${plan.durationDays}天`}
                </Tag>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </div>
  );
};

export default PortalRechargePage;
