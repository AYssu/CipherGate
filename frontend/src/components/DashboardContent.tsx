import React, { useState, useEffect } from 'react';
import {
  Card,
  Row,
  Col,
  Avatar,
  Typography,
  Space,
  Statistic,
  Button,
  List,
  Tag,
  message,
  Modal,
  Pagination,
  Grid
} from 'antd';
import {
  UserOutlined,
  GithubOutlined,
  SettingOutlined,
  TeamOutlined,
  LockOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  BarChartOutlined,
  KeyOutlined,
  LoginOutlined,
  UserAddOutlined,
  ApiOutlined,
  LineChartOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { User } from '../services';
import { activityApi, dashboardApi, type ActivityLog, type DashboardOnlineStats, type DashboardOverview, type DashboardTodayStats, type DashboardTrendPoint } from '../services';

const { Title, Text } = Typography;

interface DashboardContentProps {
  userInfo?: User | null;
  isAdmin: () => boolean;
  setSelectedMenu: (menu: string) => void;
}

const AnimatedNumber: React.FC<{ value: number; durationMs?: number }> = ({ value, durationMs = 900 }) => {
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    const target = Number.isFinite(value) ? value : 0;
    const start = performance.now();
    let raf = 0;

    const tick = (now: number) => {
      const progress = Math.min((now - start) / durationMs, 1);
      const next = Math.round(target * progress);
      setDisplay(next);
      if (progress < 1) {
        raf = requestAnimationFrame(tick);
      }
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, durationMs]);

  return <>{display.toLocaleString()}</>;
};

const DashboardContent: React.FC<DashboardContentProps> = ({
  userInfo,
  isAdmin,
  setSelectedMenu
}) => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md; // < 768px
  const [currentTime, setCurrentTime] = useState(new Date());
  const [recentActivities, setRecentActivities] = useState<ActivityLog[]>([]);
  const [loadingActivities, setLoadingActivities] = useState(false);
  const [allActivitiesVisible, setAllActivitiesVisible] = useState(false);
  const [allActivities, setAllActivities] = useState<ActivityLog[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [loadingAllActivities, setLoadingAllActivities] = useState(false);
  const [todayStats, setTodayStats] = useState<DashboardTodayStats | null>(null);
  const [loadingTodayStats, setLoadingTodayStats] = useState(false);
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [onlineStats, setOnlineStats] = useState<DashboardOnlineStats | null>(null);
  const [trend7d, setTrend7d] = useState<DashboardTrendPoint[]>([]);
  const [loadingOverview, setLoadingOverview] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const loadOverview = async () => {
      setLoadingOverview(true);
      try {
        const [overviewRes, onlineRes, trendRes] = await Promise.all([
          dashboardApi.getOverview(),
          dashboardApi.getOnline(),
          dashboardApi.getTrend7d(),
        ]);
        setOverview((overviewRes as any).data || null);
        setOnlineStats((onlineRes as any).data || null);
        setTrend7d((trendRes as any).data || []);
      } catch {
        /* 拦截器已提示 */
      } finally {
        setLoadingOverview(false);
      }
    };
    void loadOverview();
    const t = window.setInterval(() => void loadOverview(), 60_000);
    return () => window.clearInterval(t);
  }, []);

  // 获取最近活动（默认5条）
  useEffect(() => {
    const fetchActivities = async () => {
      setLoadingActivities(true);
      try {
        const result = await activityApi.getRecentActivities(5);
        setRecentActivities((result as any).data || []);
      } catch (error) {
        console.error('获取活动日志失败:', error);
        message.error('获取活动日志失败');
      } finally {
        setLoadingActivities(false);
      }
    };

    fetchActivities();
    // 每30秒刷新一次
    const interval = setInterval(fetchActivities, 30000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const loadToday = async () => {
      setLoadingTodayStats(true);
      try {
        const res = (await dashboardApi.getTodayStats()) as unknown as { data: DashboardTodayStats };
        setTodayStats(res.data);
      } catch {
        /* 拦截器已提示 */
      } finally {
        setLoadingTodayStats(false);
      }
    };
    void loadToday();
    const t = window.setInterval(() => void loadToday(), 60_000);
    return () => window.clearInterval(t);
  }, []);

  // 格式化时间为相对时间
  const formatRelativeTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return date.toLocaleDateString();
  };

  // 获取重要程度的显示样式
  const getImportanceBadge = (level: string) => {
    const levelMap: Record<string, { color: string; text: string }> = {
      'LOW': { color: 'default', text: '低' },
      'MEDIUM': { color: 'blue', text: '中' },
      'HIGH': { color: 'orange', text: '高' },
      'URGENT': { color: 'red', text: '紧急' }
    };
    return levelMap[level] || levelMap['LOW'];
  };

  // 判断是否需要显示已读状态（只有 MEDIUM、HIGH、URGENT 需要）
  const needReadStatus = (level: string) => {
    return ['MEDIUM', 'HIGH', 'URGENT'].includes(level);
  };

  // 判断是否显示红点（只有 HIGH、URGENT 显示）
  const showImportantBadge = (level: string, isRead: boolean) => {
    return !isRead && ['HIGH', 'URGENT'].includes(level);
  };

  // 标记活动为已读
  const handleMarkAsRead = async (id: number) => {
    try {
      await activityApi.markAsRead(id);
      // 刷新活动列表
      const result = await activityApi.getRecentActivities(5);
      setRecentActivities((result as any).data || []);
      // 如果弹窗打开，也刷新弹窗列表
      if (allActivitiesVisible) {
        fetchAllActivities(currentPage, pageSize);
      }
      message.success('已标记为已读');
    } catch (error) {
      console.error('标记已读失败:', error);
      message.error('标记已读失败');
    }
  };

  // 打开全部活动弹窗
  const handleShowAllActivities = () => {
    setAllActivitiesVisible(true);
    fetchAllActivities(1, pageSize);
  };

  // 获取全部活动（分页）
  const fetchAllActivities = async (page: number, size: number) => {
    setLoadingAllActivities(true);
    try {
      const result = await activityApi.getRecentActivitiesPage(page, size);
      const data = (result as any).data;
      setAllActivities(data.records || []);
      setTotal(data.total || 0);
      setCurrentPage(page);
    } catch (error) {
      console.error('获取活动日志失败:', error);
      message.error('获取活动日志失败');
    } finally {
      setLoadingAllActivities(false);
    }
  };

  // 处理分页变化
  const handlePageChange = (page: number, size: number) => {
    setPageSize(size);
    fetchAllActivities(page, size);
  };

  // 获取操作类型的显示文本和颜色
  const getActionDisplay = (activity: ActivityLog) => {
    const typeMap: Record<string, { text: string; color: string }> = {
      'LOGIN': { text: '登录操作', color: 'green' },
      'LOGOUT': { text: '登出操作', color: 'default' },
      'CREATE': { text: '创建操作', color: 'blue' },
      'UPDATE': { text: '更新操作', color: 'orange' },
      'DELETE': { text: '删除操作', color: 'red' },
      'VIEW': { text: '查看操作', color: 'cyan' }
    };
    
    return typeMap[activity.actionType] || { text: activity.actionDescription, color: 'default' };
  };

  const quickActions = [
    { 
      title: '用户管理', 
      icon: <TeamOutlined />, 
      description: '管理系统用户和权限',
      action: () => setSelectedMenu('user_management'),
      adminOnly: true
    },
    { 
      title: '角色管理', 
      icon: <LockOutlined />, 
      description: '配置用户角色和权限',
      action: () => setSelectedMenu('role_management'),
      adminOnly: true
    },
    { 
      title: '个人信息', 
      icon: <UserOutlined />, 
      description: '查看和编辑个人资料',
      action: () => setSelectedMenu('profile'),
      adminOnly: false
    },
    { 
      title: '系统配置', 
      icon: <SettingOutlined />, 
      description: '系统参数和安全设置',
      action: () => setSelectedMenu('system_config'),
      adminOnly: true
    }
  ];

  const getGreeting = () => {
    const hour = currentTime.getHours();
    if (hour < 12) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  };

  const trendOption = {
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['注册', '卡密登录', '终端登录'],
      top: 8,
      textStyle: { fontSize: isMobile ? 11 : 13 },
      itemWidth: isMobile ? 14 : 20,
      itemHeight: isMobile ? 8 : 12,
    },
    grid: {
      left: isMobile ? 8 : 24,
      right: isMobile ? 32 : 48,
      top: isMobile ? 40 : 56,
      bottom: isMobile ? 28 : 40,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend7d.map((p) => p.date.slice(5)),
      axisLabel: { fontSize: isMobile ? 10 : 12 },
    },
    yAxis: [
      {
        type: 'value',
        name: '注册',
        minInterval: 1,
        axisLabel: { fontSize: isMobile ? 10 : 12 },
        nameTextStyle: { fontSize: isMobile ? 10 : 12 },
        splitLine: { show: false },
      },
      {
        type: 'value',
        name: '登录',
        minInterval: 1,
        axisLabel: { fontSize: isMobile ? 10 : 12 },
        nameTextStyle: { fontSize: isMobile ? 10 : 12 },
      },
    ],
    series: [
      {
        name: '注册',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        symbol: isMobile ? 'none' : 'circle',
        symbolSize: 6,
        data: trend7d.map((p) => p.appUserRegistered),
      },
      {
        name: '卡密登录',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: isMobile ? 'none' : 'circle',
        symbolSize: 6,
        data: trend7d.map((p) => p.cardLogin),
      },
      {
        name: '终端登录',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: isMobile ? 'none' : 'circle',
        symbolSize: 6,
        data: trend7d.map((p) => p.appUserWsLogin),
      },
    ],
  };

  const statFormatter = (value: number | string) => (
    <AnimatedNumber value={Number(value) || 0} durationMs={1000} />
  );

  return (
    <div style={{ padding: 0 }}>
      {/* 简洁的欢迎区域 */}
      <Card style={{ marginBottom: isMobile ? 12 : 24 }} className="dashboard-welcome-card">
        <Row align="middle" gutter={isMobile ? [12, 0] : [24, 0]}>
          <Col xs={6} sm="auto" style={{ textAlign: isMobile ? 'center' : undefined }}>
            <Avatar
              src={userInfo?.avatarUrl}
              size={isMobile ? 44 : 72}
              icon={<UserOutlined />}
            />
          </Col>
          <Col xs={18} sm={12}>
            <Title level={isMobile ? 5 : 3} style={{ margin: '0 0 4px 0', color: '#1a1a2e', fontSize: isMobile ? 15 : undefined }}>
              {getGreeting()}, {userInfo?.name || userInfo?.login}
            </Title>
            <Space size={isMobile ? 6 : 16} direction="horizontal" style={{ flexWrap: 'wrap' }}>
              <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>
                <GithubOutlined /> @{userInfo?.login}
              </Text>
              {!isMobile && (
                <Text type="secondary" style={{ fontSize: 14 }}>
                  <ClockCircleOutlined /> {currentTime.toLocaleString()}
                </Text>
              )}
            </Space>
            <div style={{ marginTop: 4 }}>
              <Space wrap size={2}>
                {userInfo?.roles?.map(role => (
                  <Tag
                    key={role.id}
                    color={role.roleCode === 'SUPER_ADMIN' ? 'red' :
                           role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                    style={isMobile ? { fontSize: 10, padding: '0 4px', lineHeight: '18px', margin: 0 } : undefined}
                  >
                    {role.roleName}
                  </Tag>
                ))}
              </Space>
            </div>
          </Col>
          {!isMobile && (
            <Col>
              <div style={{ textAlign: 'right' }}>
                <Text type="secondary" style={{ fontSize: 12 }}>系统状态</Text>
                <div style={{ fontSize: 16, fontWeight: 500, color: '#52c41a' }}>
                  <CheckCircleOutlined /> 正常运行
                </div>
              </div>
            </Col>
          )}
        </Row>
      </Card>

      {/* 今日业务统计 */}
      {!isMobile && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 13 }}>
          卡密与终端用户指标仅统计您作为创建者的应用；「今日后台登录」仅管理员可见且为全平台 GitHub 登录次数。
        </Text>
      )}
      <Row gutter={[isMobile ? 6 : 16, isMobile ? 6 : 16]} style={{ marginBottom: isMobile ? 12 : 24 }} wrap>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingTodayStats} className={isMobile ? 'dashboard-stat-tile' : ''}>
            <Statistic
              title="卡密激活"
              value={todayStats?.cardFirstActivatedToday ?? 0}
              formatter={statFormatter}
              valueStyle={{ color: '#1890ff' }}
              prefix={<KeyOutlined />}
              suffix="张"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingTodayStats} className={isMobile ? 'dashboard-stat-tile' : ''}>
            <Statistic
              title="卡密登录"
              value={todayStats?.cardLoginToday ?? 0}
              formatter={statFormatter}
              valueStyle={{ color: '#13c2c2' }}
              prefix={<LoginOutlined />}
              suffix="次"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingTodayStats} className={isMobile ? 'dashboard-stat-tile' : ''}>
            <Statistic
              title="终端注册"
              value={todayStats?.appUserRegisteredToday ?? 0}
              formatter={statFormatter}
              valueStyle={{ color: '#722ed1' }}
              prefix={<UserAddOutlined />}
              suffix="人"
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingTodayStats} className={isMobile ? 'dashboard-stat-tile' : ''}>
            <Statistic
              title="终端登录"
              value={todayStats?.appUserWsLoginToday ?? 0}
              formatter={statFormatter}
              valueStyle={{ color: '#fa8c16' }}
              prefix={<ApiOutlined />}
              suffix="次"
            />
          </Card>
        </Col>
        {typeof todayStats?.platformLoginToday === 'number' && (
          <Col xs={12} sm={12} lg={6}>
            <Card loading={loadingTodayStats} className={isMobile ? 'dashboard-stat-tile' : ''}>
              <Statistic
                title="后台登录"
                value={todayStats.platformLoginToday}
                formatter={statFormatter}
                valueStyle={{ color: '#52c41a' }}
                prefix={<GithubOutlined />}
                suffix="次"
              />
            </Card>
          </Col>
        )}
      </Row>

      {!isMobile && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 13 }}>
          总览与在线指标按当前登录用户拥有的应用统计，在线口径：卡密 5 分钟内有使用记录，用户为 WS 在线会话。
        </Text>
      )}
      <Row gutter={[isMobile ? 6 : 16, isMobile ? 6 : 16]} style={{ marginBottom: isMobile ? 12 : 24 }} wrap>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="应用" value={overview?.appCount ?? 0} formatter={statFormatter} valueStyle={{ color: '#1890ff' }} suffix="个" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="终端用户" value={overview?.appUserTotal ?? 0} formatter={statFormatter} valueStyle={{ color: '#722ed1' }} suffix="人" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="卡密" value={overview?.licenseTotal ?? 0} formatter={statFormatter} valueStyle={{ color: '#13c2c2' }} suffix="张" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="在线卡密" value={onlineStats?.cardOnlineCount ?? 0} formatter={statFormatter} valueStyle={{ color: '#52c41a' }} suffix="张" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="在线用户" value={onlineStats?.appUserOnlineCount ?? 0} formatter={statFormatter} valueStyle={{ color: '#fa8c16' }} suffix="人" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="7日卡密" value={overview?.cardLogin7d ?? 0} formatter={statFormatter} valueStyle={{ color: '#1677ff' }} suffix="次" />
          </Card>
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Card loading={loadingOverview} className={isMobile ? 'dashboard-stat-tile-sm' : ''}>
            <Statistic title="7日终端" value={overview?.appUserWsLogin7d ?? 0} formatter={statFormatter} valueStyle={{ color: '#eb2f96' }} suffix="次" />
          </Card>
        </Col>
      </Row>

      <Card
        style={{ marginBottom: isMobile ? 12 : 24 }}
        title={<Space><LineChartOutlined style={{ color: '#1890ff' }} /><span style={{ fontSize: isMobile ? 14 : 16 }}>近7天趋势</span></Space>}
        loading={loadingOverview}
        className={isMobile ? 'dashboard-chart-card' : ''}
      >
        <ReactECharts option={trendOption} style={{ height: isMobile ? 200 : 340, width: '100%' }} notMerge />
      </Card>

      <Row gutter={[isMobile ? 8 : 16, isMobile ? 8 : 16]}>
        {/* 快速操作 */}
        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <BarChartOutlined style={{ color: '#1890ff' }} />
                <span>快速操作</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <Row gutter={[isMobile ? 8 : 12, isMobile ? 8 : 12]}>
              {quickActions
                .filter(action => !action.adminOnly || isAdmin())
                .map((action, index) => (
                <Col xs={12} key={index}>
                  <Card
                    size="small"
                    hoverable
                    onClick={action.action}
                    style={{
                      textAlign: 'center',
                      cursor: 'pointer',
                      transition: 'all 0.3s ease'
                    }}
                    styles={{ body: { padding: isMobile ? '12px 4px' : '16px 8px' } }}
                  >
                    <div style={{ fontSize: isMobile ? 20 : 24, color: '#1890ff', marginBottom: isMobile ? 4 : 8 }}>
                      {action.icon}
                    </div>
                    <div style={{ fontWeight: 500, marginBottom: 4, fontSize: isMobile ? 13 : 14 }}>
                      {action.title}
                    </div>
                    {!isMobile && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {action.description}
                      </Text>
                    )}
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>

        {/* 最近活动 */}
        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <EyeOutlined style={{ color: '#1890ff' }} />
                <span>最近活动</span>
              </Space>
            }
            extra={<Button type="link" size="small" onClick={handleShowAllActivities}>查看全部</Button>}
            style={{ height: '100%' }}
            loading={loadingActivities}
          >
            <List
              size="small"
              dataSource={recentActivities}
              locale={{ emptyText: '暂无活动记录' }}
              renderItem={(activity) => {
                const actionDisplay = getActionDisplay(activity);
                const importanceBadge = getImportanceBadge(activity.importanceLevel);
                const needRead = needReadStatus(activity.importanceLevel);
                const showBadge = showImportantBadge(activity.importanceLevel, activity.isRead);
                
                return (
                  <List.Item 
                    style={{ 
                      padding: '12px 0', 
                      borderBottom: '1px solid #f0f0f0',
                      backgroundColor: showBadge ? '#fff7e6' : 'transparent',
                      borderLeft: showBadge ? '3px solid #ff4d4f' : 'none',
                      paddingLeft: showBadge ? '12px' : '0',
                      transition: 'all 0.3s ease'
                    }}
                  >
                    <List.Item.Meta
                      avatar={
                        <div style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          backgroundColor: 
                            activity.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f',
                          marginTop: 6,
                          position: 'relative'
                        }}>
                          {showBadge && (
                            <div style={{
                              position: 'absolute',
                              top: -2,
                              right: -2,
                              width: 6,
                              height: 6,
                              borderRadius: '50%',
                              backgroundColor: '#ff4d4f',
                              border: '1px solid #fff'
                            }} />
                          )}
                        </div>
                      }
                      title={
                        <div style={{ fontSize: 13, lineHeight: 1.4 }}>
                          <Text strong style={{ color: '#1a1a2e' }}>{activity.username}</Text>
                          <Text type="secondary"> 执行了 </Text>
                          <Text strong>{activity.actionDescription}</Text>
                          {needRead && !activity.isRead && (
                            <Tag color="red" style={{ fontSize: 10, marginLeft: 8 }}>未读</Tag>
                          )}
                        </div>
                      }
                      description={
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          marginTop: 4,
                          flexWrap: 'wrap',
                          gap: isMobile ? '2px 6px' : '0 8px'
                        }}>
                          <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>
                            {formatRelativeTime(activity.createdTime)}
                          </Text>
                          <Tag
                            color={importanceBadge.color}
                            style={{ fontSize: 10, margin: 0 }}
                          >
                            {importanceBadge.text}
                          </Tag>
                          <Tag
                            color={actionDisplay.color}
                            style={{ fontSize: 10, margin: 0 }}
                          >
                            {activity.status === 'SUCCESS' ? '成功' : '失败'}
                          </Tag>
                          {needRead && !activity.isRead && (
                            <Button
                              type="link"
                              size="small"
                              style={{ fontSize: isMobile ? 11 : 12, padding: 0, height: 'auto' }}
                              onClick={() => handleMarkAsRead(activity.id)}
                            >
                              标记已读
                            </Button>
                          )}
                        </div>
                      }
                    />
                  </List.Item>
                );
              }}
            />
          </Card>
        </Col>
      </Row>

      {/* 全部活动弹窗 */}
      <Modal
        title="全部活动"
        open={allActivitiesVisible}
        onCancel={() => setAllActivitiesVisible(false)}
        footer={null}
        width={isMobile ? '95vw' : 700}
        styles={{
          body: {
            maxHeight: isMobile ? '70vh' : '60vh',
            overflowY: 'auto',
            padding: isMobile ? '12px 16px' : '16px 24px',
            scrollbarWidth: 'thin',
            scrollbarColor: '#d9d9d9 transparent'
          }
        }}
        className="activity-modal"
      >
        <style>{`
          .activity-modal .ant-modal-body::-webkit-scrollbar {
            width: 6px;
          }
          .activity-modal .ant-modal-body::-webkit-scrollbar-track {
            background: transparent;
          }
          .activity-modal .ant-modal-body::-webkit-scrollbar-thumb {
            background-color: #d9d9d9;
            border-radius: 3px;
          }
          .activity-modal .ant-modal-body::-webkit-scrollbar-thumb:hover {
            background-color: #bfbfbf;
          }
        `}</style>
        <List
          loading={loadingAllActivities}
          dataSource={allActivities}
          locale={{ emptyText: '暂无活动记录' }}
          split={false}
          renderItem={(activity, index) => {
            const actionDisplay = getActionDisplay(activity);
            const importanceBadge = getImportanceBadge(activity.importanceLevel);
            const needRead = needReadStatus(activity.importanceLevel);
            const showBadge = showImportantBadge(activity.importanceLevel, activity.isRead);
            const isLast = index === allActivities.length - 1;
            
            return (
              <List.Item 
                style={{ 
                  padding: '8px 0', 
                  borderBottom: isLast ? 'none' : '1px solid #f0f0f0',
                  backgroundColor: showBadge ? '#fff7e6' : 'transparent',
                  borderLeft: showBadge ? '3px solid #ff4d4f' : 'none',
                  paddingLeft: showBadge ? '8px' : '0',
                  transition: 'all 0.3s ease'
                }}
              >
                <List.Item.Meta
                  avatar={
                    <div style={{
                      width: 6,
                      height: 6,
                      borderRadius: '50%',
                      backgroundColor: 
                        activity.status === 'SUCCESS' ? '#52c41a' : '#ff4d4f',
                      marginTop: 4,
                      position: 'relative'
                    }}>
                      {showBadge && (
                        <div style={{
                          position: 'absolute',
                          top: -2,
                          right: -2,
                          width: 5,
                          height: 5,
                          borderRadius: '50%',
                          backgroundColor: '#ff4d4f',
                          border: '1px solid #fff'
                        }} />
                      )}
                    </div>
                  }
                  title={
                    <div style={{ fontSize: 13, lineHeight: 1.3 }}>
                      <Text strong style={{ color: '#1a1a2e' }}>{activity.username}</Text>
                      <Text type="secondary"> 执行了 </Text>
                      <Text strong>{activity.actionDescription}</Text>
                      {needRead && !activity.isRead && (
                        <Tag color="red" style={{ fontSize: 10, marginLeft: 6, padding: '0 4px' }}>未读</Tag>
                      )}
                    </div>
                  }
                  description={
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      marginTop: 2,
                      flexWrap: 'wrap',
                      gap: isMobile ? '2px 6px' : '0 6px'
                    }}>
                      <Text type="secondary" style={{ fontSize: isMobile ? 10 : 11 }}>
                        {formatRelativeTime(activity.createdTime)}
                      </Text>
                      <Tag
                        color={importanceBadge.color}
                        style={{ fontSize: 10, margin: 0, padding: '0 4px', lineHeight: '16px' }}
                      >
                        {importanceBadge.text}
                      </Tag>
                      <Tag
                        color={actionDisplay.color}
                        style={{ fontSize: 10, margin: 0, padding: '0 4px', lineHeight: '16px' }}
                      >
                        {activity.status === 'SUCCESS' ? '成功' : '失败'}
                      </Tag>
                      {needRead && !activity.isRead && (
                        <Button
                          type="link"
                          size="small"
                          style={{ fontSize: isMobile ? 10 : 11, padding: 0, height: 'auto' }}
                          onClick={() => handleMarkAsRead(activity.id)}
                        >
                          标记已读
                        </Button>
                      )}
                    </div>
                  }
                />
              </List.Item>
            );
          }}
        />
        <div style={{ marginTop: 12, textAlign: isMobile ? 'center' : 'right', paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
          <Pagination
            current={currentPage}
            pageSize={pageSize}
            total={total}
            onChange={handlePageChange}
            showSizeChanger={!isMobile}
            showTotal={isMobile ? undefined : (total) => `共 ${total} 条`}
            pageSizeOptions={['10', '20', '50', '100']}
            size="small"
            simple={isMobile}
          />
        </div>
      </Modal>
    </div>
  );
};

export default DashboardContent;