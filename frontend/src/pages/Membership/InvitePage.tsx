import React from 'react';
import { Card, Typography, Table, Button, Input, message, Space, Statistic, Row, Col, Grid } from 'antd';
import { CopyOutlined, ShareAltOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const InvitePage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [stats, setStats] = React.useState<any>({});
  const [records, setRecords] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    fetch('/api/user/invite/stats', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setStats(data.data); });
    fetch('/api/user/invite/records?page=1&size=20', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setRecords(data.data?.records || []); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const copyInviteCode = () => {
    if (stats.inviteCode) {
      navigator.clipboard.writeText(stats.inviteCode);
      message.success('邀请码已复制');
    }
  };

  const columns = [
    { title: '被邀请人ID', dataIndex: 'inviteeId', key: 'inviteeId' },
    { title: '奖励金额(分)', dataIndex: 'rewardAmount', key: 'rewardAmount' },
    { title: '状态', dataIndex: 'rewardGranted', key: 'granted', render: (v: boolean) => v ? '已发放' : '待发放' },
    { title: '时间', dataIndex: 'createdAt', key: 'time' },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <Title level={isMobile ? 5 : 4}>邀请有奖</Title>
            <Text type="secondary">每邀请1位新用户，获得3元余额奖励</Text>
          </div>
          <Row gutter={isMobile ? 8 : 16} style={{ textAlign: 'center' }}>
            <Col xs={8}><Statistic title="已邀请" value={stats.inviteCount || 0} suffix={`/ ${stats.maxInviteCount || 20}`} /></Col>
            <Col xs={8}><Statistic title="累计奖励" value={(stats.totalReward || 0) / 100} prefix="¥" /></Col>
            <Col xs={8}><Statistic title="剩余可邀请" value={(stats.maxInviteCount || 20) - (stats.inviteCount || 0)} /></Col>
          </Row>
          <Card type="inner" title="我的邀请码">
            <Space wrap>
              <Input value={stats.inviteCode || ''} readOnly style={{ width: isMobile ? 140 : 200 }} />
              <Button size={isMobile ? 'small' : 'middle'} icon={<CopyOutlined />} onClick={copyInviteCode}>复制</Button>
            </Space>
          </Card>
          <Table columns={columns} dataSource={records} rowKey="id" loading={loading} pagination={{ pageSize: 10, simple: isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 条` }} scroll={{ x: isMobile ? 300 : undefined }} size={isMobile ? 'small' : 'middle'} />
        </Space>
      </Card>
    </div>
  );
};

export default InvitePage;
