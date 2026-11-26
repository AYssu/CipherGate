import type { PortalDashboardStats, PortalLoginLog } from '../services/types';
import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Grid, Skeleton, Table, Tag, Typography } from 'antd';
import { EnvironmentOutlined, ClockCircleOutlined, WalletOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { portalDashboardApi } from '../services/portalDashboardService';
import './PortalDashboardPage.css';

const { Title } = Typography;
const formatTime = (value: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '暂无记录';
const loginStatus = (status: string) => <Tag color={status === 'SUCCESS' ? 'success' : 'error'}>{status === 'SUCCESS' ? '成功' : '失败'}</Tag>;

const PortalDashboardPage: React.FC = () => {
  const [stats, setStats] = useState<PortalDashboardStats | null>(null);
  const [loginHistory, setLoginHistory] = useState<PortalLoginLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const screens = Grid.useBreakpoint();
  const navigate = useNavigate();

  const loadData = async () => {
    setLoading(true);
    setLoadFailed(false);
    try {
      const [statsRes, historyRes] = await Promise.all([
        portalDashboardApi.getStats(), portalDashboardApi.getLoginHistory(1, 10),
      ]);
      setStats(statsRes?.data);
      setLoginHistory(historyRes?.data || []);
    } catch { setLoadFailed(true); }
    finally { setLoading(false); }
  };
  useEffect(() => { void loadData(); }, []);

  const trend = stats?.loginTrend || [];
  const regions = stats?.ipDistribution || [];
  const regionTotal = regions.reduce((sum, region) => sum + region.value, 0);
  const metrics = [
    { label: '总登录次数', value: stats?.totalLoginCount },
    { label: '应用登录', value: stats?.appLoginCount },
    { label: '用户中心登录', value: stats?.portalLoginCount },
    { label: '绑定设备', value: stats?.boundDeviceCount },
  ];
  const loginTrendOption = {
    animation: false,
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: trend.map(t => t.date?.slice(5)), boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8ef' } }, axisTick: { show: false }, axisLabel: { color: '#667589', hideOverlap: true } },
    yAxis: { type: 'value' as const, minInterval: 1, axisLabel: { color: '#667589' }, splitLine: { lineStyle: { color: '#edf0f4', type: 'dashed' } } },
    series: [{ data: trend.map(t => t.count), type: 'line', smooth: false, symbolSize: 6,
      lineStyle: { width: 2 }, areaStyle: { opacity: 0.04 }, color: '#1765d1' }],
    grid: { left: 32, right: 14, top: 18, bottom: 28 },
  };
  const historyColumns = [
    { title: '登录时间', dataIndex: 'createdAt', key: 'createdAt', render: (value: string) => formatTime(value) },
    { title: 'IP 地址', dataIndex: 'loginIp', key: 'loginIp', render: (value: string) => value || '暂无记录' },
    { title: '归属地', dataIndex: 'ipRegion', key: 'ipRegion', render: (value: string) => value || '未知' },
    { title: '状态', dataIndex: 'status', key: 'status', render: loginStatus },
  ];

  return (
    <div className="portal-dashboard">
      <header className="portal-page__header">
        <div><Title level={4} className="portal-page__title">账号概览</Title><p className="portal-page__subtitle">查看账号使用情况和最近登录动态。</p></div>
        <div className="portal-page__actions">
          <Link to="/portal/membership">查看会员信息</Link>
          <Button type="primary" icon={<WalletOutlined />} onClick={() => navigate('/portal/recharge')}>充值续费</Button>
        </div>
      </header>
      {loadFailed && <Alert type="error" showIcon className="dashboard-error" message="账号概览加载失败"
        description={stats ? '刷新失败，当前显示的信息可能不是最新的。' : '暂时无法获取账号使用情况，请重试。'}
        action={<Button onClick={() => void loadData()} disabled={loading}>重新加载</Button>} />}
      <section className="dashboard-summary" aria-label="登录统计">
        {metrics.map(metric => <div className="dashboard-metric" key={metric.label}>
          <span className="dashboard-metric__label">{metric.label}</span>
          <strong className="dashboard-metric__value">{loading && !stats ? '—' : metric.value ?? '—'}</strong>
        </div>)}
      </section>
      <div className="dashboard-activity-grid">
        <Card className="portal-panel dashboard-trend" title="登录趋势" extra={<span className="dashboard-caption">近 30 天</span>} loading={loading && !stats}>
          {trend.length ? <ReactECharts option={loginTrendOption} style={{ height: 250 }} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loadFailed ? '登录趋势尚未加载成功' : '暂无登录记录'} />}
        </Card>
        <Card className="portal-panel dashboard-last-login" title="最近登录" loading={loading && !stats}>
          <span className="portal-icon"><EnvironmentOutlined /></span>
          <strong className="dashboard-last-login__ip">{stats?.lastLoginIp || '暂无记录'}</strong>
          <p className="dashboard-last-login__region">{stats?.lastLoginIpRegion || '暂无归属地信息'}</p>
          <div className="dashboard-last-login__time"><ClockCircleOutlined /><span>{formatTime(stats?.lastLoginAt ?? null)}</span></div>
          <div className="dashboard-last-login__hint">如发现陌生登录，请及时修改密码。</div>
          <Link to="/portal/settings">管理账号安全</Link>
        </Card>
      </div>
      <div className="dashboard-records-grid">
        <Card className="portal-panel dashboard-history" title="最近登录记录" extra={<span className="dashboard-caption">最近 10 条</span>}>
          {loading && !stats ? <Skeleton active /> : screens.md ? <Table dataSource={loginHistory} columns={historyColumns} rowKey="id" pagination={false} size="middle"
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loadFailed ? '登录记录尚未加载成功' : '暂无登录记录'} /> }} /> :
            loginHistory.length ? <ul className="dashboard-login-list">{loginHistory.map(log => <li key={log.id}>
              <div><strong>{log.loginIp || '暂无记录'}</strong>{loginStatus(log.status)}</div>
              <span>{log.ipRegion || '未知归属地'}</span><time>{formatTime(log.createdAt)}</time>
            </li>)}</ul> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loadFailed ? '登录记录尚未加载成功' : '暂无登录记录'} />}
        </Card>
        <Card className="portal-panel dashboard-regions" title="登录地区" loading={loading && !stats}>
          {regionTotal > 0 ? <ul className="dashboard-region-list">{regions.map((region, index) => <li key={`${region.name}-${index}`}>
            <div><span>{region.name || '未知'}</span><strong>{region.value} 次</strong></div>
            <div className="dashboard-region-track"><span style={{ width: `${region.value / regionTotal * 100}%` }} /></div>
          </li>)}</ul> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loadFailed ? '地区信息尚未加载成功' : '暂无地区数据'} />}
        </Card>
      </div>
    </div>
  );
};
export default PortalDashboardPage;
