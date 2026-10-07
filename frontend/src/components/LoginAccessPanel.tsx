import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { LoginOutlined, ReloadOutlined } from '@ant-design/icons';
import { dashboardApi, type DashboardAccessStats, type DashboardAccessRecord, type DashboardAccessPage } from '../services';

const { Text } = Typography;

function shortIdentity(value: string): string {
  return value.length > 24 ? `${value.slice(0, 18)}…${value.slice(-6)}` : value;
}

const columns: ColumnsType<DashboardAccessRecord> = [
  {
    title: '应用', dataIndex: 'appName', width: 160,
    render: (name: string | undefined, record) => <Space direction="vertical" size={0}>
      <Text>{name || '应用'}</Text><Text type="secondary">ID: {record.appId}</Text>
    </Space>,
  },
  {
    title: '登录类型', dataIndex: 'identityType', width: 110,
    render: (type: DashboardAccessRecord['identityType']) =>
      <Tag color={type === 'CARD' ? 'blue' : 'green'}>{type === 'CARD' ? '卡密' : '免费访客'}</Tag>,
  },
  {
    title: '统计身份', dataIndex: 'identityId', width: 265,
    render: (id: string | undefined) => id
      ? <Tooltip title={id}><Text copyable={{ text: id }}>{shortIdentity(id)}</Text></Tooltip>
      : <Text type="secondary">历史记录未标识</Text>,
  },
  {
    title: '设备摘要', dataIndex: 'deviceHash', width: 155,
    render: (hash: string | undefined) => hash
      ? <Tooltip title={hash}><Text>{hash.slice(0, 12)}…</Text></Tooltip>
      : <Text type="secondary">未记录</Text>,
  },
  { title: '来源 IP', dataIndex: 'clientIp', width: 150, render: (ip?: string) => ip || '-' },
  { title: '登录时间', dataIndex: 'createdAt', width: 180, render: (time: string) => time.replace('T', ' ') },
];

/** 只登录也能记录访问；不把登录后的静默时间猜测成在线时长。 */
export default function LoginAccessPanel({ isMobile }: { isMobile: boolean }) {
  const [stats, setStats] = useState<DashboardAccessStats | null>(null);
  const [records, setRecords] = useState<DashboardAccessRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let disposed = false;
    let inFlight = false;
    const load = async () => {
      if (inFlight) return;
      inFlight = true;
      setLoading(true);
      try {
        const [statsResponse, recordsResponse] = await Promise.all([
          dashboardApi.getAccessStats(), dashboardApi.getRecentAccess(page, size),
        ]);
        if (disposed) return;
        // request 拦截器返回完整的 Result，而不是原始 AxiosResponse。
        const statsResult = statsResponse as unknown as { data: DashboardAccessStats };
        const recordsResult = recordsResponse as unknown as { data: DashboardAccessPage };
        setStats(statsResult.data);
        setRecords(recordsResult.data.records);
        setTotal(recordsResult.data.total);
        setFailed(false);
      } catch {
        if (!disposed) setFailed(true);
      } finally {
        inFlight = false;
        if (!disposed) setLoading(false);
      }
    };
    void load();
    const timer = window.setInterval(() => void load(), 60_000);
    return () => { disposed = true; window.clearInterval(timer); };
  }, [page, size, refreshKey]);

  const tiles = [
    { title: '今日活跃卡密', value: stats?.activeCardToday, suffix: '张' },
    { title: '今日免费访客', value: stats?.activeVisitorToday, suffix: '个' },
    { title: '今日活跃设备', value: stats?.activeDeviceToday, suffix: '台' },
    { title: '今日免费登录', value: stats?.freeLoginToday, suffix: '次' },
  ];

  return <Card
    title={<Space><LoginOutlined /><span>登录与访客统计</span></Space>}
    extra={<Button size="small" icon={<ReloadOutlined />} loading={loading}
      onClick={() => setRefreshKey(value => value + 1)}>刷新</Button>}
    style={{ marginBottom: isMobile ? 12 : 24 }}
  >
    {failed && <Alert type="error" showIcon message="访问统计加载失败，请稍后刷新；以下已加载的数据可能不是最新。" style={{ marginBottom: 12 }} />}
    <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
      成功登录即记录，无需心跳。访客和设备按应用去重；近期活跃不代表实时在线，设备数也不等于真实人数。
    </Text>
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      {tiles.map(tile => <Col key={tile.title} xs={12} lg={6}>
        <Statistic title={tile.title} value={tile.value ?? '-'} suffix={tile.suffix} />
      </Col>)}
    </Row>
    {stats && <Space size={[16, 8]} wrap style={{ marginBottom: 16 }}>
      <Text>今日卡密登录：{stats.paidCardLoginToday.toLocaleString()} 次</Text>
      <Text>7 日活跃：{stats.activeCard7d.toLocaleString()} 张卡密 / {stats.activeVisitor7d.toLocaleString()} 个免费访客 / {stats.activeDevice7d.toLocaleString()} 台设备</Text>
      <Text type="secondary">最近 5 分钟登录：{stats.recentCardCount} 张卡密 / {stats.recentVisitorCount} 个免费访客</Text>
    </Space>}
    {!!stats?.unidentifiedFreeLogin7d && <Alert type="info" showIcon
      message={`近 7 天有 ${stats.unidentifiedFreeLogin7d} 次历史免费登录未记录设备，保留登录次数，但不能补算独立访客。`}
      style={{ marginBottom: 16 }} />}
    <Text strong style={{ display: 'block', marginBottom: 12 }}>近 7 天成功登录记录</Text>
    <Table<DashboardAccessRecord>
      rowKey="id" columns={columns} dataSource={records} loading={loading}
      size={isMobile ? 'small' : 'middle'} scroll={{ x: 1020 }}
      locale={{ emptyText: '近 7 天暂无成功登录记录' }}
      pagination={{
        current: page, pageSize: size, total, showSizeChanger: !isMobile,
        pageSizeOptions: [10, 20, 50], showTotal: count => `共 ${count} 次成功登录`,
        onChange: (nextPage, nextSize) => {
          setPage(nextSize !== size ? 1 : nextPage); setSize(nextSize);
        },
      }}
    />
  </Card>;
}
