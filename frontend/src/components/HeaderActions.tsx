import { useState, useEffect } from 'react';
import { Badge, Button, Drawer, Dropdown, Empty, Grid, List, Modal, Space, Tag, Typography } from 'antd';
import { BellOutlined, ClockCircleOutlined, LogoutOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { useModel, useNavigate } from '@umijs/max';
import { userApi } from '../services/userService';
import { activityApi } from '../services/activityService';
import { messageApi, type SystemMessage } from '../services/messageService';
import { announcementApi, type SystemAnnouncement } from '../services/announcementService';
import ThemeSwitcher from './ThemeSwitcher';
import { useTheme } from '../theme/context';
const { Text } = Typography;

export default function HeaderActions() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md; // < 768px
  const [unreadCount, setUnreadCount] = useState(0);
  const [showBadge, setShowBadge] = useState(false);
  const [notificationVisible, setNotificationVisible] = useState(false);
  const [messages, setMessages] = useState<SystemMessage[]>([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [markingAllMessagesRead, setMarkingAllMessagesRead] = useState(false);
  const [selectedMessage, setSelectedMessage] = useState<SystemMessage | null>(null);
  const [messageDetailVisible, setMessageDetailVisible] = useState(false);
  const [announcementVisible, setAnnouncementVisible] = useState(false);
  const [currentAnnouncement, setCurrentAnnouncement] = useState<SystemAnnouncement | null>(null);
  const navigate = useNavigate();

  const { initialState, setInitialState } = useModel('@@initialState');
  const { themePreset } = useTheme();
  useEffect(() => {
    void setInitialState(current => current?.colorPrimary === themePreset.color ? current
      : { ...current, documents: current?.documents || [], adminSessionLoaded: true, colorPrimary: themePreset.color });
  }, [themePreset.color, setInitialState]);
  const userInfo = initialState?.currentUser;
  useEffect(() => {
    const fetchUnreadCount = async () => {
      try {
        const result = await activityApi.getUnreadCount();
        const data = result.data;
        setUnreadCount(data.total);
        setShowBadge(data.showBadge);
      } catch (error) {
        console.error('获取未读消息数失败:', error);
      }
    };

    fetchUnreadCount();

    // 每30秒刷新一次未读消息数
    const interval = setInterval(fetchUnreadCount, 30000);
    const handleUnreadCountRefresh = () => {
      void fetchUnreadCount();
    };

    // 其他页面批量标记已读后立即刷新全局未读徽标
    window.addEventListener('ciphergate-unread-refresh', handleUnreadCountRefresh);

    return () => {
      clearInterval(interval);
      window.removeEventListener('ciphergate-unread-refresh', handleUnreadCountRefresh);
    };
  }, []);

  // 检查公告弹窗
  useEffect(() => {
    const checkAnnouncements = async () => {
      try {
        const result = await announcementApi.getActiveAnnouncements();
        const activeAnnouncements = result.data || [];
        if (activeAnnouncements.length === 0) return;

        const shownKey = 'shown_announcements';
        const shownIds: number[] = JSON.parse(localStorage.getItem(shownKey) || '[]');
        const unshown = activeAnnouncements.find((a: SystemAnnouncement) => !shownIds.includes(a.id));

        if (unshown) {
          setCurrentAnnouncement(unshown);
          setAnnouncementVisible(true);
        }
      } catch (error) {
        console.error('获取公告失败:', error);
      }
    };

    checkAnnouncements();

  }, []);

  const handleLogout = async () => {
    try {
      await userApi.logout();
      // 退出成功后跳转到首页
      window.location.href = '/';
    } catch (error) {
      console.error('退出登录失败:', error);
      // 即使退出失败，也跳转到首页（可能是网络问题）
      window.location.href = '/';
    }
  };

  // 打开通知面板
  const handleOpenNotification = async () => {
    setNotificationVisible(true);
    setLoadingMessages(true);

    try {
      const result = await messageApi.getMyMessages(20);
      setMessages(result.data || []);
    } catch (error) {
      console.error('获取消息失败:', error);
    } finally {
      setLoadingMessages(false);
    }
  };

  // 关闭通知面板
  const handleCloseNotification = () => {
    setNotificationVisible(false);
  };

  // 打开消息详情
  const handleOpenMessageDetail = (message: SystemMessage) => {
    setSelectedMessage(message);
    setMessageDetailVisible(true);
    // 如果是未读消息，标记为已读
    if (!message.isRead) {
      handleMarkMessageAsRead(message.id);
    }
  };

  // 关闭消息详情
  const handleCloseMessageDetail = () => {
    setMessageDetailVisible(false);
    setSelectedMessage(null);
  };

  // 关闭公告弹窗
  const handleAnnouncementClose = () => {
    if (currentAnnouncement) {
      const shownKey = 'shown_announcements';
      const shownIds: number[] = JSON.parse(localStorage.getItem(shownKey) || '[]');
      if (!shownIds.includes(currentAnnouncement.id)) {
        shownIds.push(currentAnnouncement.id);
        localStorage.setItem(shownKey, JSON.stringify(shownIds));
      }
    }
    setAnnouncementVisible(false);
    setCurrentAnnouncement(null);
  };

  // 标记消息为已读
  const handleMarkMessageAsRead = async (id: number) => {
    try {
      await messageApi.markAsRead(id);
      // 刷新消息列表
      const result = await messageApi.getMyMessages(20);
      setMessages(result.data || []);
      // 刷新未读数
      const countResult = await activityApi.getUnreadCount();
      setUnreadCount(countResult.data.total);
      setShowBadge(countResult.data.showBadge);
    } catch (error) {
      console.error('标记已读失败:', error);
    }
  };

  // 一键标记所有系统消息为已读
  const handleMarkAllMessagesAsRead = async () => {
    if (markingAllMessagesRead) return;

    setMarkingAllMessagesRead(true);
    try {
      await messageApi.markAllAsRead();
      setMessages((current) => current.map((item) => ({
        ...item,
        isRead: true,
      })));

      const countResult = await activityApi.getUnreadCount();
      const data = countResult.data;
      setUnreadCount(data.total);
      setShowBadge(data.showBadge);
    } catch (error) {
      console.error('全部标记已读失败:', error);
    } finally {
      setMarkingAllMessagesRead(false);
    }
  };

  // 格式化时间
  const formatTime = (dateString: string) => {
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
    if (days < 30) return `${days}天前`;
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  };

  // 获取重要程度标签
  const getImportanceTag = (level: string) => {
    const levelMap: Record<string, { color: string; text: string }> = {
      'LOW': { color: 'default', text: '低' },
      'MEDIUM': { color: 'blue', text: '中' },
      'HIGH': { color: 'orange', text: '高' },
      'URGENT': { color: 'red', text: '紧急' }
    };
    return levelMap[level] || levelMap['LOW'];
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => navigate('/profile'),
    },
    // 只有超级管理员才显示 API 文档入口
    ...(userInfo?.roles?.some(role => role.roleCode === 'SUPER_ADMIN') ? [{
      key: 'api-docs',
      icon: <SafetyOutlined />,
      label: 'API 文档',
      onClick: () => {
        // 在新标签页打开 API 文档
        const backendUrl = process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : '';
        window.open(`${backendUrl}/doc.html`, '_blank');
      },
    }] : []),
    {
      key: 'divider',
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];


  return <>
    <ThemeSwitcher />
    <Button type="text" aria-label="系统消息" onClick={handleOpenNotification} icon={
      <Badge count={unreadCount} dot={showBadge && unreadCount === 0} size="small"><BellOutlined /></Badge>
    } />
    <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
      <Button type="text" aria-label="用户菜单" icon={<UserOutlined />}>
        {!isMobile && (userInfo?.name || userInfo?.login || '账户')}
      </Button>
    </Dropdown>
      {/* 消息通知抽屉 */}
      <Drawer rootClassName="ciphergate-overlay"
        title={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <span>系统消息</span>
            <Button
              type="link"
              size="small"
              loading={markingAllMessagesRead}
              disabled={!messages.some((item) => !item.isRead)}
              onClick={handleMarkAllMessagesAsRead}
            >
              一键已读
            </Button>
          </div>
        }
        placement={isMobile ? 'bottom' : 'right'}
        onClose={handleCloseNotification}
        open={notificationVisible}
        width={isMobile ? undefined : 380}
        height={isMobile ? '70vh' : undefined}
        styles={{
          body: { padding: 0, overflow: 'hidden', overflowY: 'auto' },
          header: isMobile ? { borderBottom: '1px solid #f0f0f0' } : undefined,
          content: isMobile ? { borderRadius: '16px 16px 0 0', overflow: 'hidden' } : undefined,
        }}
      >
        {isMobile && (
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            padding: '8px 0 4px',
            position: 'sticky',
            top: 0,
            background: '#fff',
            zIndex: 1
          }}>
            <div style={{
              width: 36,
              height: 4,
              borderRadius: 2,
              background: '#d9d9d9'
            }} />
          </div>
        )}
        {messages.length === 0 ? (
          <div style={{ padding: '80px 24px', textAlign: 'center' }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无消息"
            />
          </div>
        ) : (
          <List
            loading={loadingMessages}
            dataSource={messages}
            renderItem={(message) => {
              const importanceTag = getImportanceTag(message.importanceLevel);
              const isUnread = !message.isRead;

              return (
                <List.Item
                  style={{
                    padding: isMobile ? '12px 16px' : '12px 16px',
                    backgroundColor: isUnread ? '#f0f7ff' : '#fff',
                    cursor: 'pointer',
                    transition: 'background-color 0.2s',
                    borderBottom: '1px solid #f0f0f0',
                    position: 'relative'
                  }}
                  onClick={() => handleOpenMessageDetail(message)}
                  className={isUnread ? 'msg-item-unread' : 'msg-item-read'}
                >
                  <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                    {/* 未读标记 */}
                    {isUnread && (
                      <div style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: '#1890ff',
                        marginTop: 6,
                        flexShrink: 0
                      }} />
                    )}
                    {!isUnread && <div style={{ width: 8, flexShrink: 0 }} />}

                    <div style={{ flex: 1, minWidth: 0 }}>
                      {/* 标题行 */}
                      <div style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: 4,
                        gap: 8
                      }}>
                        <Text
                          strong
                          style={{
                            fontSize: 14,
                            color: '#1a1a1a',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            flex: 1,
                            minWidth: 0
                          }}
                        >
                          {message.title}
                        </Text>

                        <Tag
                          color={importanceTag.color}
                          style={{
                            margin: 0,
                            fontSize: 11,
                            lineHeight: '18px',
                            padding: '0 6px'
                          }}
                        >
                          {importanceTag.text}
                        </Tag>
                      </div>

                      {/* 消息内容预览 */}
                      <Text
                        style={{
                          fontSize: 13,
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          marginBottom: 6,
                          color: '#666',
                          lineHeight: '1.5',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          wordBreak: 'break-all'
                        }}
                      >
                        {message.content}
                      </Text>

                      {/* 时间 */}
                      <Text type="secondary" style={{ fontSize: 12, color: '#999' }}>
                        {formatTime(message.createdTime)}
                      </Text>
                    </div>
                  </div>
                </List.Item>
              );
            }}
          />
        )}
      </Drawer>

      {/* 消息详情弹窗 */}
      <Modal
        title={
          <Space>
            <Text strong style={{ fontSize: isMobile ? 15 : 16 }}>消息详情</Text>
            {selectedMessage && !selectedMessage.isRead && (
              <Badge status="processing" text="未读" />
            )}
          </Space>
        }
        open={messageDetailVisible}
        onCancel={handleCloseMessageDetail}
        footer={[
          <Button key="close" type="primary" onClick={handleCloseMessageDetail} block={isMobile}>
            关闭
          </Button>
        ]}
        width={isMobile ? '92vw' : 600}
        styles={isMobile ? { content: { padding: '12px' } } : undefined}
      >
        {selectedMessage && (
          <div>
            <div style={{ marginBottom: isMobile ? 12 : 16 }}>
              <Space>
                <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>重要程度：</Text>
                <Tag color={getImportanceTag(selectedMessage.importanceLevel).color} style={{ fontSize: isMobile ? 11 : 12 }}>
                  {getImportanceTag(selectedMessage.importanceLevel).text}
                </Tag>
              </Space>
            </div>

            <div style={{ marginBottom: isMobile ? 12 : 16 }}>
              <Text strong style={{ fontSize: isMobile ? 15 : 16, display: 'block', marginBottom: isMobile ? 6 : 8 }}>
                {selectedMessage.title}
              </Text>
            </div>

            <div style={{
              marginBottom: isMobile ? 12 : 16,
              padding: isMobile ? 12 : 16,
              backgroundColor: '#fafafa',
              borderRadius: 8,
              lineHeight: '1.7'
            }}>
              <Text style={{ fontSize: isMobile ? 13 : 14, whiteSpace: 'pre-wrap' }}>
                {selectedMessage.content}
              </Text>
            </div>

            <div>
              <Space size={4}>
                <ClockCircleOutlined style={{ fontSize: 11, color: '#8c8c8c' }} />
                <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>
                  {new Date(selectedMessage.createdTime).toLocaleString('zh-CN')}
                </Text>
              </Space>
            </div>
          </div>
        )}
      </Modal>

      {/* 公告弹窗 */}
      <Modal
        title="系统公告"
        open={announcementVisible}
        onCancel={handleAnnouncementClose}
        footer={[
          <Button key="close" type="primary" onClick={handleAnnouncementClose} block={isMobile}>
            我知道了
          </Button>,
        ]}
        width={isMobile ? '92vw' : 600}
        styles={isMobile ? { content: { padding: '12px' } } : undefined}
      >
        {currentAnnouncement && (
          <div>
            <Text strong style={{ fontSize: isMobile ? 15 : 16, display: 'block', marginBottom: isMobile ? 8 : 12 }}>
              {currentAnnouncement.title}
            </Text>
            <div style={{
              padding: isMobile ? 12 : 16,
              backgroundColor: '#fafafa',
              borderRadius: 8,
              lineHeight: 1.8,
              whiteSpace: 'pre-wrap',
            }}>
              {currentAnnouncement.content}
            </div>
            <div style={{ marginTop: isMobile ? 8 : 12 }}>
              <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>
                {new Date(currentAnnouncement.createdAt).toLocaleString('zh-CN')}
              </Text>
            </div>
          </div>
        )}
      </Modal>
  </>;
}
