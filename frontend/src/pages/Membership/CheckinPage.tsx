import React from 'react';
import { Card, Button, Typography, Statistic, Row, Col, Tag, message, Grid, Descriptions } from 'antd';
import { CheckCircleOutlined, GiftOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const rewardTypeMap: Record<string, { color: string; text: string; icon: string }> = {
  LICENSE: { color: 'cyan', text: '卡密额度', icon: ' ' },
  USER_REGISTER: { color: 'green', text: '用户额度', icon: ' ' },
  TRAFFIC: { color: 'orange', text: '流量额度', icon: ' ️' },
};

const CheckinPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [status, setStatus] = React.useState<any>(null);
  const [_loading, setLoading] = React.useState(true);
  const [checkinLoading, setCheckinLoading] = React.useState(false);
  const [rewardResult, setRewardResult] = React.useState<any>(null);

  React.useEffect(() => {
    fetchStatus();
  }, []);

  const fetchStatus = () => {
    fetch('/api/user/checkin/status', { credentials: 'include' })
      .then(res => res.json())
      .then(data => {
        if (data.success) setStatus(data.data);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  const handleCheckin = async () => {
    setCheckinLoading(true);
    setRewardResult(null);
    try {
      const res = await fetch('/api/user/checkin', { method: 'POST', credentials: 'include' });
      const data = await res.json();
      if (data.success) {
        message.success('签到成功！');
        setRewardResult(data.data);
        fetchStatus();
      } else {
        message.error(data.message);
      }
    } catch {
      message.error('签到失败');
    }
    setCheckinLoading(false);
  };

  const formatReward = (reward: any) => {
    if (!reward) return '-';
    if (reward.type === 'TRAFFIC') {
      return `${reward.amountMB}MB`;
    }
    return `${reward.amount}${reward.unit}`;
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ textAlign: 'center' }}>
          <Title level={isMobile ? 4 : 3}>每日签到</Title>
          <CheckCircleOutlined
            style={{
              fontSize: isMobile ? 48 : 64,
              color: status?.checkedIn ? '#52c41a' : '#d9d9d9',
              margin: isMobile ? '16px 0' : '24px 0'
            }}
          />
          <div>
            {status?.checkedIn ? (
              <Tag color="success" style={{ fontSize: isMobile ? 14 : 16, padding: isMobile ? '2px 12px' : '4px 16px' }}>
                今日已签到
              </Tag>
            ) : (
              <Button
                type="primary"
                size={isMobile ? 'middle' : 'large'}
                loading={checkinLoading}
                onClick={handleCheckin}
                icon={<GiftOutlined />}
              >
                签到领奖励
              </Button>
            )}
          </div>

          <Row gutter={isMobile ? 8 : 16} style={{ marginTop: isMobile ? 16 : 24 }}>
            <Col span={isMobile ? 12 : 8}>
              <Statistic
                title="连续签到"
                value={status?.consecutiveDays || 0}
                suffix="天"
                valueStyle={{ fontSize: isMobile ? 20 : 24 }}
              />
            </Col>
            <Col span={isMobile ? 12 : 8}>
              <Statistic
                title="累计签到"
                value={status?.totalDays || 0}
                suffix="天"
                valueStyle={{ fontSize: isMobile ? 20 : 24 }}
              />
            </Col>
          </Row>
        </div>
      </Card>

      {/* 显示签到奖励结果 */}
      {rewardResult && rewardResult.reward && (
        <Card
          title={
            <span>
              <GiftOutlined style={{ color: '#52c41a', marginRight: 8 }} />
              签到奖励
            </span>
          }
          style={{ marginTop: 16 }}
        >
          <Descriptions column={isMobile ? 1 : 2} bordered size="small">
            <Descriptions.Item label="奖励类型">
              <Tag color={rewardTypeMap[rewardResult.rewardType]?.color}>
                {rewardTypeMap[rewardResult.rewardType]?.icon}{' '}
                {rewardTypeMap[rewardResult.rewardType]?.text}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="奖励数量">
              <Text strong style={{ color: '#52c41a', fontSize: 16 }}>
                {formatReward(rewardResult.reward)}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="连续签到">
              {rewardResult.consecutiveDays} 天
            </Descriptions.Item>
            <Descriptions.Item label="累计签到">
              {rewardResult.totalDays} 天
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* 签到规则说明 */}
      <Card title="签到规则" style={{ marginTop: 16 }} size="small">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="奖励类型">每日随机获得一种：卡密额度 / 用户额度 / 流量额度</Descriptions.Item>
          <Descriptions.Item label="卡密额度">基础 3-15 张，连续签到可提升至最多 55 张</Descriptions.Item>
          <Descriptions.Item label="用户额度">基础 1-3 个，连续签到可提升至最多 10 个</Descriptions.Item>
          <Descriptions.Item label="流量额度">基础 5-15 MB，连续签到可提升至最多 100 MB</Descriptions.Item>
          <Descriptions.Item label="连续签到">每日连续签到可获得更高额度奖励</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
};

export default CheckinPage;
