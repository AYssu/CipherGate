import type { PortalPricingPlan } from '../services/types';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Typography, Button, message, Modal, Alert, Empty } from 'antd';
import { CheckCircleOutlined, ClockCircleOutlined, GiftOutlined, ShoppingOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { portalMembershipApi } from '../services/portalMembershipService';
import { portalPaymentApi } from '../services/portalPaymentService';
import './PortalRechargePage.css';

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
    if (orderPending.current || plan.stockRemaining === 0) return;
    orderPending.current = true;
    setOrdering(plan.id);
    let dialogOpened = false;
    const releaseOrder = () => { orderPending.current = false; setOrdering(null); };
    try {
      const res = await portalPaymentApi.createOrder(plan.id);
      if (res?.data) {
        void loadPlans();
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
      void loadPlans(); // Refresh stock after a concurrent purchase or an uncertain gateway result.
    } finally {
      if (!dialogOpened) releaseOrder();
    }
  };

  return (
    <div className="portal-recharge">
      <header className="portal-recharge__header">
        <div>
          <Title level={4} className="portal-recharge__title">充值续费</Title>
          <p>选择会员方案，支付成功后自动生效。</p>
        </div>
        <Link to="/portal/orders" className="portal-recharge__orders">查看订单记录</Link>
      </header>

      {loading ? (
        <Card loading />
      ) : loadFailed ? (
        <Alert type="error" showIcon message="购买方案加载失败"
          description="暂时无法获取购买方案，请重试。"
          action={<Button onClick={() => void loadPlans()}>重新加载</Button>} />
      ) : plans.length === 0 ? (
        <Card><Empty description="该应用暂无可购买的方案" image={Empty.PRESENTED_IMAGE_SIMPLE} /></Card>
      ) : (
        <div className="portal-recharge__plans">
          {plans.map((plan) => {
            const discounted = plan.originalPriceFen != null && plan.originalPriceFen > plan.priceFen;
            const soldOut = plan.stockRemaining === 0;
            const limited = plan.stockRemaining != null;
            const saving = discounted ? ((plan.originalPriceFen! - plan.priceFen) / 100).toFixed(2).replace(/\.00$/, '') : '';
            return (
              <article className={`recharge-plan${limited ? ' recharge-plan--limited' : ''}${soldOut ? ' recharge-plan--sold-out' : ''}`} key={plan.id}>
                <div className="recharge-plan__topline">
                  <span className="recharge-plan__kind">
                    {limited ? <ShoppingOutlined /> : <ClockCircleOutlined />}
                    {limited ? '限量方案' : '会员方案'}
                  </span>
                  {discounted && <span className="recharge-plan__saving"><GiftOutlined />省 ¥{saving}</span>}
                </div>
                <h2 className="recharge-plan__name">{plan.planName}</h2>
                <p className="recharge-plan__duration">
                  {plan.planType === 'PERMANENT' ? '永久有效' : `有效期 ${plan.durationDays} 天`}
                </p>
                <div className="recharge-plan__pricing">
                  <div className="recharge-plan__current">
                    <span className="recharge-plan__currency">¥</span>
                    <span>{(plan.priceFen / 100).toFixed(2)}</span>
                  </div>
                  <div className="recharge-plan__reference">
                    {discounted ? <><span>原价</span><del>¥{(plan.originalPriceFen! / 100).toFixed(2)}</del><span className="recharge-plan__offer">优惠价</span></> : <span>按此价格一次支付</span>}
                  </div>
                </div>
                <div className="recharge-plan__footer">
                  <div className="recharge-plan__availability">
                    {limited ? <span className="recharge-plan__stock"><span aria-hidden="true" className="recharge-plan__stock-dot" />{soldOut ? '已售罄' : <>剩余 <strong>{plan.stockRemaining}</strong> 份</>}</span> : <span>支付成功后开通</span>}
                  </div>
                  <Button type="primary" block className="recharge-plan__buy" loading={ordering === plan.id} disabled={ordering !== null || soldOut} onClick={() => handleOrder(plan)}>
                    {soldOut ? '已售罄' : '购买'}
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default PortalRechargePage;
