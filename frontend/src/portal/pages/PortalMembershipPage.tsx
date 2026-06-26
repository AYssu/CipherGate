import React, { useEffect, useState } from 'react';
import { Card, Typography, Descriptions, Tag, Button, Space, Row, Col, Statistic } from 'antd';
import { CrownOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, ExperimentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { portalMembershipApi } from '../services/portalMembershipService';

const { Title } = Typography;

const PortalMembershipPage: React.FC = () => {
  const [info, setInfo] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const res: any = await portalMembershipApi.getInfo();
      setInfo(res?.data);
    } catch {
      // ignore
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
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>会员信息</Title>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card loading={loading} style={{ textAlign: 'center' }}>
            <Statistic
              title="会员状态"
              valueRender={() => getStatusTag()}
              style={{ marginTop: 8 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card loading={loading}>
            <Statistic
              title={getExpiryLabel()}
              value={getExpiryDisplay()}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ fontSize: 16 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card loading={loading}>
            <Statistic
              title="登录次数"
              value={info?.loginCount || 0}
              prefix={<CrownOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card loading={loading} style={{ marginTop: 16 }}>
        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="middle">
          <Descriptions.Item label="应用ID">{info?.appId}</Descriptions.Item>
          <Descriptions.Item label="最近登录">
            {info?.lastLoginAt ? info.lastLoginAt.replace('T', ' ').slice(0, 19) : '-'}
          </Descriptions.Item>
          {info?.trialApplied && (
            <Descriptions.Item label="试用状态">
              {info?.trialActive ? (
                <Tag color="processing">试用中</Tag>
              ) : (
                <Tag color="default">试用已过期</Tag>
              )}
            </Descriptions.Item>
          )}
          {info?.trialExpiresAt && (
            <Descriptions.Item label="试用到期">
              {info.trialExpiresAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
        </Descriptions>

        <Space style={{ marginTop: 24 }}>
          <Button type="primary" icon={<CrownOutlined />} onClick={() => navigate('/portal/recharge')}>
            充值续费
          </Button>
        </Space>
      </Card>
    </div>
  );
};

export default PortalMembershipPage;
