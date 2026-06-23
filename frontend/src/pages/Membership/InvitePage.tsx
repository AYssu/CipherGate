import React from 'react';
import { Card, Typography, Table, Button, Input, message, Space, Statistic, Row, Col, Grid, Tag, Alert, Progress } from 'antd';
import { CopyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { inviteApi } from '../../services/inviteService';

const { Text } = Typography;

const InvitePage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [stats, setStats] = React.useState<any>({});
  const [records, setRecords] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [bindCode, setBindCode] = React.useState('');
  const [binding, setBinding] = React.useState(false);

  React.useEffect(() => {
    fetchStats();
    fetchRecords();
  }, []);

  const fetchStats = async () => {
    try {
      const res: any = await inviteApi.getInviteStats();
      if (res.success) setStats(res.data);
    } catch {}
  };

  const fetchRecords = async () => {
    setLoading(true);
    try {
      const res: any = await inviteApi.getInviteRecords(1, 20);
      if (res.success) setRecords(res.data?.records || []);
    } catch {}
    setLoading(false);
  };

  const copyInviteCode = () => {
    if (stats.inviteCode) {
      const text = `邀请码：${stats.inviteCode}\n注册链接：${window.location.origin}?invite=${stats.inviteCode}`;
      navigator.clipboard.writeText(text);
      message.success('邀请信息已复制到剪贴板');
    }
  };

  const handleBind = async () => {
    if (!bindCode.trim()) {
      message.warning('请输入邀请码');
      return;
    }
    setBinding(true);
    try {
      const res: any = await inviteApi.bindInviteCode(bindCode.trim());
      if (res.success) {
        message.success('邀请码绑定成功！邀请人已获得奖励');
        setBindCode('');
        fetchStats();
      }
    } catch {}
    setBinding(false);
  };

  const inviteProgress = stats.maxInviteCount ? ((stats.inviteCount || 0) / stats.maxInviteCount) * 100 : 0;

  const columns = [
    { 
      title: '被邀请人', 
      dataIndex: 'inviteeId', 
      key: 'inviteeId',
      render: (v: number) => <Text>用户#{v}</Text>
    },
    { 
      title: '奖励', 
      dataIndex: 'rewardAmount', 
      key: 'rewardAmount', 
      render: (v: number) => <Text type="success" strong>+¥{(v / 100).toFixed(2)}</Text>
    },
    { 
      title: '状态', 
      dataIndex: 'rewardGranted', 
      key: 'granted', 
      render: (v: boolean) => v ? <Tag color="success">已发放</Tag> : <Tag color="processing">待发放</Tag>
    },
    { 
      title: '时间', 
      dataIndex: 'createdAt', 
      key: 'time',
      render: (v: string) => v ? new Date(v).toLocaleDateString('zh-CN') : '-'
    },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {stats.enabled === false ? (
          <Card>
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Text type="secondary" style={{ fontSize: 16 }}>邀请有奖功能暂未开启</Text>
              <div style={{ marginTop: 8 }}>
                <Text type="secondary">请联系管理员开启邀请功能</Text>
              </div>
            </div>
          </Card>
        ) : (
          <>
            <Card title="邀请有奖" extra={<Text type="secondary">每邀请1位新用户，获得 ¥{(stats.rewardAmount / 100 || 3).toFixed(2)} 余额奖励</Text>}>
          <div style={{ marginBottom: 16, padding: '12px 16px', background: '#fafafa', borderRadius: 6 }}>
            <Text type="secondary" style={{ fontSize: 13 }}>
              使用方式：将下方邀请链接分享给好友，好友注册后即视为成功邀请。
            </Text>
          </div>
          <Row gutter={isMobile ? 12 : 24} align="middle">
            <Col flex={isMobile ? 'none' : 'auto'}>
              <div style={{ marginBottom: 12 }}>
                <Text type="secondary">我的邀请码</Text>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                  <Input 
                    value={stats.inviteCode || ''} 
                    readOnly 
                    style={{ flex: 1, fontWeight: 600, fontSize: 16, maxWidth: 240 }}
                  />
                  <Button icon={<CopyOutlined />} onClick={copyInviteCode}>复制</Button>
                </div>
              </div>
            </Col>
            <Col>
              <Row gutter={24}>
                <Col>
                  <Statistic title="已邀请" value={stats.inviteCount || 0} suffix={`/ ${stats.maxInviteCount || 0}`} />
                </Col>
                <Col>
                  <Statistic title="累计奖励" value={(stats.totalReward || 0) / 100} prefix="¥" valueStyle={{ color: '#52c41a' }} />
                </Col>
                <Col>
                  <Statistic title="剩余名额" value={(stats.maxInviteCount || 0) - (stats.inviteCount || 0)} />
                </Col>
              </Row>
            </Col>
          </Row>
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>邀请进度</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>{stats.inviteCount || 0} / {stats.maxInviteCount || 0}</Text>
            </div>
            <Progress percent={inviteProgress} size="small" />
          </div>
        </Card>

        {!stats.invitedBy ? (
          <Card title="填写邀请码" size="small">
            <Alert type="info" showIcon message="填写他人的邀请码，对方将获得奖励" style={{ marginBottom: 12 }} />
            <Space.Compact style={{ width: '100%', maxWidth: 400 }}>
              <Input 
                placeholder="请输入邀请码" 
                value={bindCode}
                onChange={(e) => setBindCode(e.target.value)}
                onPressEnter={handleBind}
              />
              <Button type="primary" onClick={handleBind} loading={binding}>绑定</Button>
            </Space.Compact>
          </Card>
        ) : (
          <Card size="small">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <CheckCircleOutlined style={{ fontSize: 16, color: '#52c41a' }} />
              <Text>您已通过邀请注册</Text>
            </div>
          </Card>
        )}

        <Card title="邀请记录" size="small">
          <Table 
            columns={columns} 
            dataSource={records} 
            rowKey="id" 
            loading={loading}
            pagination={{ 
              pageSize: 10, 
              simple: isMobile, 
              showTotal: isMobile ? undefined : (total) => `共 ${total} 条` 
            }}
            scroll={{ x: isMobile ? 400 : undefined }}
            size={isMobile ? 'small' : 'middle'}
            locale={{ emptyText: '暂无邀请记录' }}
          />
        </Card>
          </>
        )}
      </Space>
    </div>
  );
};

export default InvitePage;
