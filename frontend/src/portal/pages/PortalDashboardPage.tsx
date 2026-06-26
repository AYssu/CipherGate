import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Typography, Table, Tag } from 'antd';
import { LoginOutlined, MobileOutlined, EnvironmentOutlined, ClockCircleOutlined, SafetyOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { portalDashboardApi } from '../services/portalDashboardService';

const { Title, Text } = Typography;

const PortalDashboardPage: React.FC = () => {
  const [stats, setStats] = useState<any>(null);
  const [loginHistory, setLoginHistory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [statsRes, historyRes]: any[] = await Promise.all([
        portalDashboardApi.getStats(),
        portalDashboardApi.getLoginHistory(1, 10),
      ]);
      setStats(statsRes?.data);
      setLoginHistory(historyRes?.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (v: string) => {
    if (!v) return '-';
    return v.replace('T', ' ').slice(0, 19);
  };

  const loginTrendOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: stats?.loginTrend?.map((t: any) => t.date?.slice(5)) || [] },
    yAxis: { type: 'value' as const },
    series: [{ data: stats?.loginTrend?.map((t: any) => t.count) || [], type: 'line', smooth: true, areaStyle: {}, color: '#1890ff' }],
    grid: { left: 40, right: 16, top: 16, bottom: 30 },
  };

  const ipPieOption = {
    tooltip: { trigger: 'item' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: stats?.ipDistribution || [],
      label: { formatter: '{b}: {c}' },
    }],
  };

  const historyColumns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (v: string) => <Text style={{ fontSize: 13 }}>{formatTime(v)}</Text>
    },
    {
      title: 'IP',
      dataIndex: 'loginIp',
      key: 'loginIp',
      render: (v: string) => v || '-'
    },
    {
      title: '归属地',
      dataIndex: 'ipRegion',
      key: 'ipRegion',
      render: (v: string) => v || '-'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (v: string) => <Tag color={v === 'SUCCESS' ? 'success' : 'error'}>{v === 'SUCCESS' ? '成功' : '失败'}</Tag>
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>控制台</Title>

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="总登录次数" value={stats?.totalLoginCount || 0} prefix={<LoginOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="三方应用登录" value={stats?.appLoginCount || 0} prefix={<SafetyOutlined />} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="门户登录" value={stats?.portalLoginCount || 0} prefix={<LoginOutlined />} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="绑定设备" value={stats?.boundDeviceCount || 0} prefix={<MobileOutlined />} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24}>
          <Card>
            <Statistic
              title="最近登录 IP"
              value={stats?.lastLoginIp || '-'}
              prefix={<EnvironmentOutlined />}
              valueStyle={{ fontSize: 16 }}
              suffix={stats?.lastLoginIpRegion ? <Tag color="blue" style={{ marginLeft: 8 }}>{stats.lastLoginIpRegion}</Tag> : null}
            />
            {stats?.lastLoginAt && (
              <div style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  <ClockCircleOutlined style={{ marginRight: 4 }} />
                  {formatTime(stats.lastLoginAt)}
                </Text>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="登录趋势（近30天）" loading={loading}>
            <ReactECharts option={loginTrendOption} style={{ height: 280 }} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="IP 归属分布" loading={loading}>
            {stats?.ipDistribution?.length > 0 ? (
              <ReactECharts option={ipPieOption} style={{ height: 280 }} />
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>暂无数据</div>
            )}
          </Card>
        </Col>
      </Row>

      <Card title="最近登录记录" style={{ marginTop: 16 }} loading={loading}>
        <Table
          dataSource={loginHistory}
          columns={historyColumns}
          rowKey="id"
          pagination={false}
          size="small"
          scroll={{ x: 600 }}
        />
      </Card>
    </div>
  );
};

export default PortalDashboardPage;
