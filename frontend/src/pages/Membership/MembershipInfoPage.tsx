import React from 'react';
import { Card, Typography, Tag, Button, Space, Progress, Statistic, Row, Col, message } from 'antd';
import { CrownOutlined, CheckCircleOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const MembershipInfoPage: React.FC = () => {
  const [loading, setLoading] = React.useState(true);
  const [membership, setMembership] = React.useState<any>(null);

  React.useEffect(() => {
    fetch('/api/user/membership/info', { credentials: 'include' })
      .then(res => res.json())
      .then(data => {
        if (data.success) setMembership(data.data);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  if (loading) return <Card loading />;
  if (!membership) return <Card><Text>加载失败</Text></Card>;

  const levelNames: Record<number, string> = {
    1: '初级开发者', 2: '铜牌开发者', 3: '银牌开发者', 4: '金牌开发者', 5: '永久会员'
  };

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <CrownOutlined style={{ fontSize: 48, color: '#faad14' }} />
            <Title level={3}>{levelNames[membership.levelId] || '未知等级'}</Title>
            <Tag color="gold">LV.{membership.levelId || 1}</Tag>
          </div>
          <Row gutter={16}>
            <Col span={6}><Statistic title="余额(分)" value={membership.balance || 0} /></Col>
            <Col span={6}><Statistic title="已创建应用" value={membership.appUsed || 0} /></Col>
            <Col span={6}><Statistic title="已创建卡密" value={membership.licenseUsed || 0} /></Col>
            <Col span={6}><Statistic title="已注册用户" value={membership.userRegisterUsed || 0} /></Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}><Statistic title="已邀请人数" value={membership.inviteCount || 0} /></Col>
            <Col span={8}><Statistic title="累计签到天数" value={membership.totalCheckinDays || 0} /></Col>
            <Col span={8}><Statistic title="连续签到" value={membership.consecutiveCheckinDays || 0} /></Col>
          </Row>
        </Space>
      </Card>
    </div>
  );
};

export default MembershipInfoPage;
