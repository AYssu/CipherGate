import type { PortalMembershipInfo } from '../services/types';
import React, { useEffect, useState } from 'react';
import { Card, Typography, Descriptions, Tag, Button, Alert } from 'antd';
import { CrownOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, ExperimentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { portalMembershipApi } from '../services/portalMembershipService';

const { Title } = Typography;

const PortalMembershipPage: React.FC = () => {
  const [info, setInfo] = useState<PortalMembershipInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setLoadFailed(false);
    try {
      const res = await portalMembershipApi.getInfo();
      setInfo(res?.data);
    } catch {
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  };

  const getStatusTag = () => {
    const status = info?.memberStatus;
    if (status === 'ACTIVE') {
      return <Tag icon={<CheckCircleOutlined />} color="success" style={{ fontSize: 14, padding: '2px 12px' }}>会员有效</Tag>;
    }
    if (status === 'EXPIRED') {
      return <Tag icon={<CloseCircleOutlined />} color="error" style={{ fontSize: 14, padding: '2px 12px' }}>已到期</Tag>;
    }
    if (status === 'TRIAL') {
      return <Tag icon={<ExperimentOutlined />} color="processing" style={{ fontSize: 14, padding: '2px 12px' }}>试用期</Tag>;
    }
    return <Tag icon={<CloseCircleOutlined />} color="default" style={{ fontSize: 14, padding: '2px 12px' }}>未开通</Tag>;
  };

  const getExpiryDisplay = () => {
    const status = info?.memberStatus;
    if (status === 'TRIAL' && info?.trialExpiresAt) {
      return info.trialExpiresAt.replace('T', ' ').slice(0, 19);
    }
    if (info?.memberExpiresAt) {
      return info.memberExpiresAt.replace('T', ' ').slice(0, 19);
    }
    return '未开通';
  };

  const getExpiryLabel = () => {
    if (info?.memberStatus === 'TRIAL') return '试用到期时间';
    return '会员到期时间';
  };

  return (
    <div className="portal-membership">
      <header className="portal-page__header">
        <div><Title level={4} className="portal-page__title">会员信息</Title><p className="portal-page__subtitle">查看当前应用的会员状态和有效期。</p></div>
        <Button type="primary" onClick={() => navigate('/portal/recharge')}>充值续费</Button>
      </header>
      {loadFailed && <Alert type="error" showIcon style={{ marginBottom: 20 }} message="会员信息加载失败"
        description={info ? '刷新失败，当前显示的信息可能不是最新的。' : '暂时无法获取会员状态，请重试。'}
        action={<Button disabled={loading} onClick={() => void loadData()}>重新加载</Button>} />}
      <Card className="portal-panel membership-overview" loading={loading}>
        <div className="membership-overview__status">
          <span className="membership-overview__icon"><CrownOutlined /></span>
          <div><span className="membership-label">当前会员状态</span><div>{info ? getStatusTag() : '暂不可用'}</div></div>
        </div>
        <div className="membership-overview__expiry">
          <span className="membership-label">{getExpiryLabel()}</span>
          <strong><ClockCircleOutlined /> {info ? getExpiryDisplay() : '暂不可用'}</strong>
        </div>
      </Card>
      <Card className="portal-panel portal-section-gap" title="会员详情" loading={loading}>
        <Descriptions column={{ xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2 }} size="middle">
          <Descriptions.Item label="应用 ID">{info?.appId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="累计登录">{info?.loginCount ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="最近登录">
            {info?.lastLoginAt ? info.lastLoginAt.replace('T', ' ').slice(0, 19) : '暂无记录'}
          </Descriptions.Item>
          {info?.trialApplied && <Descriptions.Item label="试用状态">
            {info.trialActive ? <Tag color="processing">试用中</Tag> : <Tag color="default">试用已过期</Tag>}
          </Descriptions.Item>}
          {info?.trialExpiresAt && <Descriptions.Item label="试用到期">
            {info.trialExpiresAt.replace('T', ' ').slice(0, 19)}
          </Descriptions.Item>}
        </Descriptions>
      </Card>
    </div>
  );
};

export default PortalMembershipPage;
