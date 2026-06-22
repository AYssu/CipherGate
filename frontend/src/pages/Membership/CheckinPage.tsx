import React from 'react';
import { Card, Button, Typography, Statistic, Row, Col, Tag, message, Grid } from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';

const { Title } = Typography;

const CheckinPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [status, setStatus] = React.useState<any>(null);
  const [_loading, setLoading] = React.useState(true);
  const [checkinLoading, setCheckinLoading] = React.useState(false);

  React.useEffect(() => {
    fetchStatus();
  }, []);

  const fetchStatus = () => {
    fetch('/api/user/checkin/status', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setStatus(data.data); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const handleCheckin = async () => {
    setCheckinLoading(true);
    try {
      const res = await fetch('/api/user/checkin', { method: 'POST', credentials: 'include' });
      const data = await res.json();
      if (data.success) {
        message.success('签到成功！');
        fetchStatus();
      } else {
        message.error(data.message);
      }
    } catch { message.error('签到失败'); }
    setCheckinLoading(false);
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ textAlign: 'center' }}>
          <Title level={isMobile ? 4 : 3}>每日签到</Title>
          <CheckCircleOutlined style={{ fontSize: isMobile ? 48 : 64, color: status?.checkedIn ? '#52c41a' : '#d9d9d9', margin: isMobile ? '16px 0' : '24px 0' }} />
          <div>
            {status?.checkedIn ? (
              <Tag color="success" style={{ fontSize: isMobile ? 14 : 16, padding: isMobile ? '2px 12px' : '4px 16px' }}>今日已签到</Tag>
            ) : (
              <Button type="primary" size={isMobile ? 'middle' : 'large'} loading={checkinLoading} onClick={handleCheckin}>签到</Button>
            )}
          </div>
          <Row gutter={isMobile ? 8 : 16} style={{ marginTop: isMobile ? 16 : 24 }}>
            <Col span={isMobile ? 12 : 8}><Statistic title="连续签到" value={status?.todayRecord?.consecutiveDays || 0} suffix="天" valueStyle={{ fontSize: isMobile ? 20 : 24 }} /></Col>
            <Col span={isMobile ? 12 : 8}><Statistic title="累计签到" value={status?.todayRecord?.consecutiveDays || 0} suffix="天" valueStyle={{ fontSize: isMobile ? 20 : 24 }} /></Col>
          </Row>
        </div>
      </Card>
    </div>
  );
};

export default CheckinPage;
