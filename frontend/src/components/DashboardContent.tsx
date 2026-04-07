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
  Pagination
} from 'antd';
import { 
  UserOutlined, 
  GithubOutlined, 
  SecurityScanOutlined, 
  SafetyOutlined, 
  SettingOutlined, 
  TeamOutlined,
  LockOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  BarChartOutlined
} from '@ant-design/icons';
import type { User } from '../services';
import { activityApi, type ActivityLog } from '../services';

const { Title, Text } = Typography;

interface DashboardContentProps {
  userInfo?: User | null;
  isAdmin: () => boolean;
  setSelectedMenu: (menu: string) => void;
}

const DashboardContent: React.FC<DashboardContentProps> = ({ 
  userInfo, 
  isAdmin, 
  setSelectedMenu 
}) => {
  const [currentTime, setCurrentTime] = useState(new Date());
  const [recentActivities, setRecentActivities] = useState<ActivityLog[]>([]);
  const [loadingActivities, setLoadingActivities] = useState(false);
  const [allActivitiesVisible, setAllActivitiesVisible] = useState(false);
  const [allActivities, setAllActivities] = useState<ActivityLog[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [loadingAllActivities, setLoadingAllActivities] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);
    return () => clearInterval(timer);
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

  // 模拟数据
  const securityStats = {
    totalThreats: 156,
    blockedToday: 23,
    systemHealth: 99.9,
    activeUsers: 12
  };

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

  return (
    <div style={{ padding: 0 }}>
      {/* 简洁的欢迎区域 */}
      <Card style={{ marginBottom: 24 }}>
        <Row align="middle" gutter={24}>
          <Col>
            <Avatar
              src={userInfo?.avatarUrl}
              size={72}
              icon={<UserOutlined />}
            />
          </Col>
          <Col flex={1}>
            <Title level={3} style={{ margin: '0 0 8px 0', color: '#1a1a2e' }}>
              {getGreeting()}, {userInfo?.name || userInfo?.login}
            </Title>
            <Space size={16}>
              <Text type="secondary" style={{ fontSize: 14 }}>
                <GithubOutlined /> @{userInfo?.login}
              </Text>
              <Text type="secondary" style={{ fontSize: 14 }}>
                <ClockCircleOutlined /> {currentTime.toLocaleString()}
              </Text>
            </Space>
            <div style={{ marginTop: 8 }}>
              <Space wrap>
                {userInfo?.roles?.map(role => (
                  <Tag
                    key={role.id}
                    color={role.roleCode === 'SUPER_ADMIN' ? 'red' : 
                           role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                  >
                    {role.roleName}
                  </Tag>
                ))}
              </Space>
            </div>
          </Col>
          <Col>
            <div style={{ textAlign: 'right' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>系统状态</Text>
              <div style={{ fontSize: 16, fontWeight: 500, color: '#52c41a' }}>
                <CheckCircleOutlined /> 正常运行
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 统计数据 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="安全事件"
              value={securityStats.totalThreats}
              valueStyle={{ color: '#52c41a' }}
              prefix={<SecurityScanOutlined />}
              suffix="个"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日拦截"
              value={securityStats.blockedToday}
              valueStyle={{ color: '#1890ff' }}
              prefix={<SafetyOutlined />}
              suffix="次"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="系统健康度"
              value={securityStats.systemHealth}
              precision={1}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />}
              suffix="%"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="在线用户"
              value={securityStats.activeUsers}
              valueStyle={{ color: '#722ed1' }}
              prefix={<UserOutlined />}
              suffix="人"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* 快速操作 */}
        <Col span={12}>
          <Card 
            title={
              <Space>
                <BarChartOutlined style={{ color: '#1890ff' }} />
                <span>快速操作</span>
              </Space>
            }
            style={{ height: '100%' }}
          >
            <Row gutter={[12, 12]}>
              {quickActions
                .filter(action => !action.adminOnly || isAdmin())
                .map((action, index) => (
                <Col span={12} key={index}>
                  <Card 
                    size="small"
                    hoverable
                    onClick={action.action}
                    style={{ 
                      textAlign: 'center',
                      cursor: 'pointer',
                      transition: 'all 0.3s ease'
                    }}
                    styles={{ body: { padding: '16px 8px' } }}
                  >
                    <div style={{ fontSize: 24, color: '#1890ff', marginBottom: 8 }}>
                      {action.icon}
                    </div>
                    <div style={{ fontWeight: 500, marginBottom: 4 }}>
                      {action.title}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {action.description}
                    </Text>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>

        {/* 最近活动 */}
        <Col span={12}>
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
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
                          <Space size={8}>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatRelativeTime(activity.createdTime)}
                            </Text>
                            <Tag 
                              color={importanceBadge.color}
                              style={{ fontSize: 10, margin: 0 }}
                            >
                              {importanceBadge.text}
                            </Tag>
                          </Space>
                          <Space size={4}>
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
                                style={{ fontSize: 12, padding: 0, height: 'auto' }}
                                onClick={() => handleMarkAsRead(activity.id)}
                              >
                                标记已读
                              </Button>
                            )}
                          </Space>
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
        width={700}
        styles={{
          body: { 
            maxHeight: '60vh', 
            overflowY: 'auto', 
            padding: '16px 24px',
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
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 2 }}>
                      <Space size={6}>
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          {formatRelativeTime(activity.createdTime)}
                        </Text>
                        <Tag 
                          color={importanceBadge.color}
                          style={{ fontSize: 10, margin: 0, padding: '0 4px', lineHeight: '16px' }}
                        >
                          {importanceBadge.text}
                        </Tag>
                      </Space>
                      <Space size={4}>
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
                            style={{ fontSize: 11, padding: 0, height: 'auto' }}
                            onClick={() => handleMarkAsRead(activity.id)}
                          >
                            标记已读
                          </Button>
                        )}
                      </Space>
                    </div>
                  }
                />
              </List.Item>
            );
          }}
        />
        <div style={{ marginTop: 12, textAlign: 'right', paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
          <Pagination
            current={currentPage}
            pageSize={pageSize}
            total={total}
            onChange={handlePageChange}
            showSizeChanger
            showTotal={(total) => `共 ${total} 条`}
            pageSizeOptions={['10', '20', '50', '100']}
            size="small"
          />
        </div>
      </Modal>
    </div>
  );
};

export default DashboardContent;