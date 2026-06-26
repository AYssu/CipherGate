import React, { useEffect, useState } from 'react';
import { Card, Typography, Row, Col, Button, Tag, message, Modal } from 'antd';
import { CrownOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { portalMembershipApi } from '../services/portalMembershipService';
import { portalPaymentApi } from '../services/portalPaymentService';

const { Title, Text } = Typography;

interface PricingPlan {
  id: number;
  planName: string;
  planType: string;
  durationDays: number | null;
  priceFen: number;
  sortOrder: number;
}

const PortalRechargePage: React.FC = () => {
  const [plans, setPlans] = useState<PricingPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [ordering, setOrdering] = useState<number | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    loadPlans();
  }, []);

  const loadPlans = async () => {
    try {
      const res: any = await portalMembershipApi.getPlans();
      setPlans(res?.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const handleOrder = async (plan: PricingPlan) => {
    setOrdering(plan.id);
    try {
      const res: any = await portalPaymentApi.createOrder(plan.id);
      if (res?.data) {
        Modal.confirm({
          title: '确认支付',
          icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
          content: (
            <div>
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
          cancelText: '取消',
          onOk: () => {
            if (res.data.payUrl) {
              window.location.href = res.data.payUrl;
            } else {
              message.info('订单已创建，请前往订单记录查看');
              navigate('/portal/orders');
            }
          },
        });
      }
    } catch {
      // error handled by interceptor
    } finally {
      setOrdering(null);
    }
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>充值续费</Title>

      {loading ? (
        <Card loading />
      ) : plans.length === 0 ? (
        <Card><div style={{ textAlign: 'center', padding: 40, color: '#999' }}>该应用暂无可购买的方案</div></Card>
      ) : (
        <Row gutter={[16, 16]}>
          {plans.map((plan) => (
            <Col xs={24} sm={12} lg={8} key={plan.id}>
              <Card
                hoverable
                style={{ textAlign: 'center' }}
                actions={[
                  <Button type="primary" loading={ordering === plan.id} onClick={() => handleOrder(plan)}>
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
