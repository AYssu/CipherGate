import React, { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Space, Avatar, Dropdown, Button, Badge, Drawer, List, Tag, Empty, Modal } from 'antd';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import {
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  FolderOutlined,
  LockOutlined,
  LogoutOutlined,
  MenuOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import { activityApi } from '../services/activityService';
import { messageApi, type SystemMessage } from '../services/messageService';
import type { User, Menu as UserMenu } from '../services/userService';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const MainLayout: React.FC = () => {
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [showBadge, setShowBadge] = useState(false);
  const [notificationVisible, setNotificationVisible] = useState(false);
  const [messages, setMessages] = useState<SystemMessage[]>([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [selectedMessage, setSelectedMessage] = useState<SystemMessage | null>(null);
  const [messageDetailVisible, setMessageDetailVisible] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  // 根据当前路由自动计算 selectedMenu
  const getSelectedMenuFromPath = (pathname: string) => {
    if (pathname === '/dashboard') return 'dashboard';
    if (pathname === '/profile') return 'profile';
    if (pathname.startsWith('/system/')) {
      const systemPath = pathname.replace('/system/', '');
      switch (systemPath) {
        case 'users': return 'user_management';
        case 'roles': return 'role_management';
        case 'menus': return 'menu_management';
        case 'permissions': return 'permission_management';
        case 'info': return 'system_config';
        case 'config': return 'system_setting';
        default: return 'dashboard';
      }
    }
    if (pathname.startsWith('/applications/')) {
      const appPath = pathname.replace('/applications/', '');
      switch (appPath) {
        case 'list': return 'app_list_page';
        case 'licenses': return 'license_management';
        case 'users': return 'app_user_management';
        case 'variables': return 'app_variable_management';
        default: return 'dashboard';
      }
    }
    return 'dashboard';
  };

  const selectedMenu = getSelectedMenuFromPath(location.pathname);

  // 根据当前选中的菜单自动设置展开的父菜单
  useEffect(() => {
    if (selectedMenu?.includes('_management') && !selectedMenu?.startsWith('app_') && selectedMenu !== 'license_management' && selectedMenu !== 'app_user_management') {
      setOpenKeys(['system_management']);
    } else if (selectedMenu?.includes('_config')) {
      setOpenKeys(['system_management']);
    } else if (selectedMenu?.startsWith('app_') || selectedMenu === 'license_management' || selectedMenu === 'app_user_management') {
      setOpenKeys(['app_management']);
    }
  }, [selectedMenu]);

  // 获取页面标题
  const getPageTitle = (pathname: string) => {
    if (pathname === '/dashboard') return '控制台';
    if (pathname === '/profile') return '个人信息';
    if (pathname.startsWith('/system/')) {
      const systemPath = pathname.replace('/system/', '');
      switch (systemPath) {
        case 'users': return '用户管理';
        case 'roles': return '角色管理';
        case 'menus': return '菜单管理';
        case 'permissions': return '权限管理';
        case 'info': return '系统信息';
        case 'config': return '系统配置';
        default: return '控制台';
      }
    }
    if (pathname.startsWith('/applications/')) {
      const appPath = pathname.replace('/applications/', '');
      switch (appPath) {
        case 'list': return '应用列表';
        case 'licenses': return '卡密管理';
        case 'users': return '终端用户';
        case 'variables': return '变量管理';
        default: return '应用管理';
      }
    }
    return '控制台';
  };

  // 处理子菜单展开/收起
  const handleOpenChange = (keys: string[]) => {
    setOpenKeys(keys);
  };

  const pageTitle = getPageTitle(location.pathname);

  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        const result = await userApi.getCurrentUserInfo();
        setUserInfo((result as any).data);
      } catch (error) {
        console.error('获取用户信息失败:', error);
      } finally {
        setLoading(false);
      }
    };
    
    const fetchUnreadCount = async () => {
      try {
        const result = await activityApi.getUnreadCount();
        const data = (result as any).data;
        setUnreadCount(data.total);
        setShowBadge(data.showBadge);
      } catch (error) {
        console.error('获取未读消息数失败:', error);
      }
    };
    
    fetchUserInfo();
    fetchUnreadCount();
    
    // 每30秒刷新一次未读消息数
    const interval = setInterval(fetchUnreadCount, 30000);
    
    return () => clearInterval(interval);
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
      setMessages((result as any).data || []);
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

  // 标记消息为已读
  const handleMarkMessageAsRead = async (id: number) => {
    try {
      await messageApi.markAsRead(id);
      // 刷新消息列表
      const result = await messageApi.getMyMessages(20);
      setMessages((result as any).data || []);
      // 刷新未读数
      const countResult = await activityApi.getUnreadCount();
      const data = (countResult as any).data;
      setUnreadCount(data.total);
      setShowBadge(data.showBadge);
    } catch (error) {
      console.error('标记已读失败:', error);
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
    return date.toLocaleDateString();
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
        const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
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

  /**
   * 与后台 menus.icon 对应（存库多为 Ant Icon 组件名或小写别名）。统一小写匹配；未识别则用中性图标。
   */
  const getMenuIcon = (iconName?: string | null) => {
    const key = (iconName || '').trim().toLowerCase();
    const iconMap: Record<string, React.ReactNode> = {
      dashboard: <DashboardOutlined />,
      user: <UserOutlined />,
      setting: <SettingOutlined />,
      safety: <AuditOutlined />,
      security: <LockOutlined />,
      team: <TeamOutlined />,
      menu: <MenuOutlined />,
      lock: <LockOutlined />,
      tool: <ToolOutlined />,
      appstoreoutlined: <AppstoreOutlined />,
      appstore: <AppstoreOutlined />,
    };
    return iconMap[key] || <FolderOutlined />;
  };

  const generateSidebarMenus = (menus: UserMenu[]) => {
    return menus.map(menu => {
      const menuKey = menu.menuCode.toLowerCase();
      
      if (menu.children && menu.children.length > 0) {
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          children: menu.children.map((child: any) => {
            const childKey = child.menuCode.toLowerCase();
            
            return {
              key: childKey,
              label: child.menuName,
              onClick: () => {
                if (menuKey === 'system_management') {
                  let routePath = '';
                  switch (childKey) {
                    case 'user_management':
                      routePath = '/system/users';
                      break;
                    case 'role_management':
                      routePath = '/system/roles';
                      break;
                    case 'menu_management':
                      routePath = '/system/menus';
                      break;
                    case 'permission_management':
                      routePath = '/system/permissions';
                      break;
                    case 'system_config':
                      routePath = '/system/info';
                      break;
                    case 'system_setting':
                    case 'system_settings':
                      routePath = '/system/config';
                      break;
                    default:
                      const cleanKey = childKey.replace('_management', '');
                      routePath = `/system/${cleanKey}`;
                  }
                  navigate(routePath);
                } else if (menuKey === 'app_management') {
                  // 处理应用管理菜单
                  let routePath = '';
                  switch (childKey) {
                    case 'app_list_page':
                      routePath = '/applications/list';
                      break;
                    case 'license_management':
                      routePath = '/applications/licenses';
                      break;
                    case 'app_user_management':
                      routePath = '/applications/users';
                      break;
                    case 'app_variable_management':
                      routePath = '/applications/variables';
                      break;
                    default:
                      routePath = `/applications/${childKey}`;
                  }
                  navigate(routePath);
                } else if (menuKey === 'plugin_management') {
                  let routePath = '';
                  switch (childKey) {
                    case 'plugin_list_page':
                      routePath = '/plugins/list';
                      break;
                    default:
                      routePath = `/plugins/${childKey}`;
                  }
                  navigate(routePath);
                } else {
                  navigate(`/${childKey}`);
                }
              },
            };
          }),
        };
      } else {
        return {
          key: menuKey,
          icon: getMenuIcon(menu.icon),
          label: menu.menuName,
          onClick: () => {
            if (menuKey === 'dashboard') {
              navigate('/dashboard');
            } else {
              navigate(`/${menuKey}`);
            }
          },
        };
      }
    });
  };

  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh' 
      }}>
        <SafetyOutlined spin style={{ fontSize: 48, color: '#1890ff' }} />
      </div>
    );
  }

  const sidebarMenuItems = userInfo?.menus ? generateSidebarMenus(userInfo.menus) : [];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        theme="light"
        width={250}
        collapsed={collapsed}
        style={{
          boxShadow: '2px 0 8px rgba(0,0,0,0.1)',
          borderRight: '1px solid #f0f0f0'
        }}
      >
        <div className="sider-logo" style={{
          padding: collapsed ? '24px 8px' : '24px 16px',
          borderBottom: '1px solid #f0f0f0',
          textAlign: 'center',
          transition: 'all 0.2s'
        }}>
          <SafetyOutlined style={{ 
            fontSize: 32, 
            color: '#1890ff', 
            marginBottom: collapsed ? 0 : 8
          }} />
          {!collapsed && (
            <Title level={4} style={{ margin: 0, color: '#1a1a2e' }}>
              CipherGate
            </Title>
          )}
        </div>
        
        <Menu
          mode="inline"
          selectedKeys={[selectedMenu]}
          openKeys={collapsed ? [] : openKeys}
          onOpenChange={handleOpenChange}
          inlineCollapsed={collapsed}
          items={sidebarMenuItems}
          style={{ 
            border: 'none', 
            padding: '16px 0',
            fontSize: '14px'
          }}
          className="dashboard-sidebar-menu"
        />
      </Sider>

      <Layout>
        <Header style={{
          background: '#fff',
          padding: '0 24px',
          height: '64px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          position: 'relative'
        }}>
          {/* 左侧区域 */}
          <div style={{ 
            display: 'flex', 
            alignItems: 'center',
            position: 'absolute',
            left: '24px'
          }}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{
                fontSize: '16px',
                width: 32,
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            />
            <Title 
              level={4}
              style={{ 
                margin: '0 0 0 12px', 
                color: '#1a1a2e',
                fontSize: '18px',
                fontWeight: 500,
                lineHeight: 1
              }}
            >
              {pageTitle}
            </Title>
          </div>
          
          {/* 右侧区域 */}
          <div style={{
            position: 'absolute',
            right: '24px',
            top: '50%',
            transform: 'translateY(-50%)'
          }}>
            <Space size={12} align="center">
              <Badge 
                count={unreadCount} 
                size="small"
                dot={showBadge && unreadCount === 0}
                offset={[-2, 2]}
              >
                <BellOutlined style={{ 
                  fontSize: 16, 
                  cursor: 'pointer',
                  color: showBadge ? '#ff4d4f' : undefined
                }} 
                onClick={handleOpenNotification}
                />
              </Badge>
              
              <Dropdown
                menu={{ items: userMenuItems }}
                placement="bottomRight"
              >
                <Space style={{ cursor: 'pointer' }} size={8} align="center">
                  <Avatar 
                    src={userInfo?.avatarUrl} 
                    icon={<UserOutlined />}
                    size={32}
                  />
                  <Text strong style={{ fontSize: '14px' }}>
                    {userInfo?.name || userInfo?.login}
                  </Text>
                </Space>
              </Dropdown>
            </Space>
          </div>
        </Header>

        <Content style={{ 
          padding: '24px', 
          background: '#f5f5f5' 
        }}>
          <Outlet context={{ userInfo }} />
        </Content>
      </Layout>

      {/* 消息通知抽屉 */}
      <Drawer
        title="系统消息"
        placement="right"
        onClose={handleCloseNotification}
        open={notificationVisible}
        width={380}
        styles={{
          body: { padding: 0 }
        }}
      >
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
              const isImportant = message.importanceLevel === 'HIGH' || message.importanceLevel === 'URGENT';
              
              return (
                <List.Item
                  style={{
                    padding: '14px 20px',
                    backgroundColor: isUnread ? '#e6f7ff' : '#fff',
                    borderLeft: isImportant ? `3px solid ${message.importanceLevel === 'URGENT' ? '#ff4d4f' : '#faad14'}` : isUnread ? '3px solid #1890ff' : 'none',
                    cursor: 'pointer',
                    transition: 'background-color 0.2s',
                    borderBottom: '1px solid #f0f0f0'
                  }}
                  onClick={() => handleOpenMessageDetail(message)}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = isUnread ? '#bae7ff' : '#fafafa';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = isUnread ? '#e6f7ff' : '#fff';
                  }}
                >
                  <div style={{ width: '100%' }}>
                    {/* 标题行 */}
                    <div style={{ 
                      display: 'flex', 
                      justifyContent: 'space-between', 
                      alignItems: 'center',
                      marginBottom: 6
                    }}>
                      <Space size={6}>
                        {isUnread && (
                          <Badge 
                            status="processing" 
                            text=""
                          />
                        )}
                        <Text 
                          strong 
                          style={{ 
                            fontSize: 14,
                            color: isUnread ? '#1890ff' : '#262626'
                          }}
                        >
                          {message.title}
                        </Text>
                      </Space>
                      
                      <Tag 
                        color={importanceTag.color}
                        style={{ 
                          margin: 0,
                          fontSize: 12
                        }}
                      >
                        {importanceTag.text}
                      </Tag>
                    </div>
                    
                    {/* 消息内容预览 */}
                    <Text 
                      style={{ 
                        fontSize: 13, 
                        display: 'block', 
                        marginBottom: 8,
                        color: '#595959',
                        lineHeight: '1.6',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                      }}
                    >
                      {message.content}
                    </Text>
                    
                    {/* 时间 */}
                    <Space size={4}>
                      <ClockCircleOutlined style={{ fontSize: 12, color: '#8c8c8c' }} />
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {formatTime(message.createdTime)}
                      </Text>
                    </Space>
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
            <Text strong style={{ fontSize: 16 }}>消息详情</Text>
            {selectedMessage && !selectedMessage.isRead && (
              <Badge status="processing" text="未读" />
            )}
          </Space>
        }
        open={messageDetailVisible}
        onCancel={handleCloseMessageDetail}
        footer={[
          <Button key="close" type="primary" onClick={handleCloseMessageDetail}>
            关闭
          </Button>
        ]}
        width={600}
      >
        {selectedMessage && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Space>
                <Text type="secondary">重要程度：</Text>
                <Tag color={getImportanceTag(selectedMessage.importanceLevel).color}>
                  {getImportanceTag(selectedMessage.importanceLevel).text}
                </Tag>
              </Space>
            </div>
            
            <div style={{ marginBottom: 16 }}>
              <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 8 }}>
                {selectedMessage.title}
              </Text>
            </div>
            
            <div style={{ 
              marginBottom: 16,
              padding: 16,
              backgroundColor: '#fafafa',
              borderRadius: 4,
              lineHeight: '1.8'
            }}>
              <Text style={{ fontSize: 14, whiteSpace: 'pre-wrap' }}>
                {selectedMessage.content}
              </Text>
            </div>
            
            <div>
              <Space size={4}>
                <ClockCircleOutlined style={{ fontSize: 12, color: '#8c8c8c' }} />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {new Date(selectedMessage.createdTime).toLocaleString('zh-CN')}
                </Text>
              </Space>
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  );
};



export default MainLayout;
