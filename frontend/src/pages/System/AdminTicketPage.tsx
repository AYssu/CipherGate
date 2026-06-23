import React from 'react';
import { Card, Table, Tag, Button, Modal, Input, message, Typography, List, Avatar, Space, Tabs, Badge, Grid, Dropdown, Checkbox } from 'antd';
import { UserOutlined, RobotOutlined, CheckCircleOutlined, CloseCircleOutlined, MessageOutlined, MoreOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';

const { Title, Text } = Typography;
const { TextArea } = Input;

const MenuPopup: React.FC<{ items: MenuProps['items'] }> = ({ items }) => (
  <Dropdown menu={{ items }} trigger={['click']}>
    <Button type="text" size="small" icon={<MoreOutlined />} />
  </Dropdown>
);

const AdminTicketPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [tickets, setTickets] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [detailVisible, setDetailVisible] = React.useState(false);
  const [selectedTicket, setSelectedTicket] = React.useState<any>(null);
  const [messages, setMessages] = React.useState<any[]>([]);
  const [newMessage, setNewMessage] = React.useState('');
  const [activeTab, setActiveTab] = React.useState('all');
  const [counts, setCounts] = React.useState<Record<number, number>>({});
  const [resolveModalVisible, setResolveModalVisible] = React.useState(false);
  const [resolveStatus, setResolveStatus] = React.useState<number>(3);
  const [resolveEmail, setResolveEmail] = React.useState(false);
  const [resolveRemark, setResolveRemark] = React.useState('');

  React.useEffect(() => { fetchTickets(); }, []);

  const fetchTickets = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/tickets?page=1&size=100', { credentials: 'include' });
      const data = await res.json();
      if (data.success) {
        const all = data.data?.records || [];
        setTickets(all);
        const c: Record<number, number> = { 0: 0, 1: 0, 2: 0, 3: 0, 4: 0 };
        all.forEach((t: any) => { if (c[t.status] !== undefined) c[t.status]++; });
        setCounts(c);
      }
    } finally { setLoading(false); }
  };

  const viewDetail = async (ticket: any) => {
    setSelectedTicket(ticket);
    const res = await fetch(`/api/admin/tickets/${ticket.ticketNo}`, { credentials: 'include' });
    const data = await res.json();
    if (data.success) { setMessages(data.data.messages || []); setDetailVisible(true); }
  };

  const sendReply = async () => {
    if (!newMessage.trim()) return;
    await fetch(`/api/admin/tickets/${selectedTicket.ticketNo}/messages`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: newMessage })
    });
    setNewMessage('');
    viewDetail(selectedTicket);
    fetchTickets();
  };

  const handleStatusChange = async (ticketNo: string, status: number, sendEmail: boolean = false, remark: string = '') => {
    await fetch(`/api/admin/tickets/${ticketNo}/status`, {
      method: 'PUT', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, sendEmail, remark })
    });
    message.success('状态已更新');
    fetchTickets();
  };

  const handleAcceptAndReply = async () => {
    if (!selectedTicket) return;
    if (selectedTicket.status === 0) {
      await handleStatusChange(selectedTicket.ticketNo, 1);
    }
    if (newMessage.trim()) {
      await sendReply();
    }
    viewDetail(selectedTicket);
  };

  const showResolveModal = (status: number) => {
    setResolveStatus(status);
    setResolveEmail(false);
    setResolveRemark('');
    setResolveModalVisible(true);
  };

  const handleResolveConfirm = async () => {
    if (!selectedTicket) return;
    await handleStatusChange(selectedTicket.ticketNo, resolveStatus, resolveEmail, resolveRemark);
    message.success(resolveStatus === 3 ? '工单已解决' : '工单已关闭');
    setResolveModalVisible(false);
    setDetailVisible(false);
    fetchTickets();
  };

  const priorityMap: Record<number, { color: string; text: string }> = {
    1: { color: 'default', text: '普通' },
    2: { color: 'orange', text: '重要' },
    3: { color: 'red', text: '紧急' }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'processing', text: '待处理' },
    1: { color: 'blue', text: '处理中' },
    2: { color: 'orange', text: '等待回复' },
    3: { color: 'success', text: '已解决' },
    4: { color: 'default', text: '已关闭' }
  };

  const getFilteredTickets = () => {
    if (activeTab === 'all') return tickets;
    const status = parseInt(activeTab);
    return tickets.filter(t => t.status === status);
  };

  const getActionMenuItems = (record: any): MenuProps['items'] => {
    const items: MenuProps['items'] = [
      { key: 'view', icon: <MessageOutlined />, label: record.status === 0 ? '接单处理' : record.status === 2 ? '查看回复' : '查看', onClick: () => viewDetail(record) },
    ];
    if (record.status === 0) {
      items.push({ key: 'accept', label: '接单', onClick: () => { setSelectedTicket(record); handleStatusChange(record.ticketNo, 1); } });
    }
    if (record.status === 1) {
      items.push({ key: 'resolve', icon: <CheckCircleOutlined />, label: '标记已解决', danger: false, onClick: () => handleStatusChange(record.ticketNo, 3) });
    }
    return items;
  };

  const columns = [
    { title: '工单号', dataIndex: 'ticketNo', key: 'no', width: isMobile ? 120 : 160 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    ...(!isMobile ? [
      { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80, render: (v: number) => <Tag color={priorityMap[v]?.color}>{priorityMap[v]?.text}</Tag> },
      { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
      { title: '最后回复', dataIndex: 'lastReplyAt', key: 'lastReply', width: 160, render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
    ] : []),
    ...(isMobile ? [
      { title: '状态', dataIndex: 'status', key: 'status', width: 70, render: (v: number) => <Tag color={statusMap[v]?.color} style={{ margin: 0 }}>{statusMap[v]?.text}</Tag> },
    ] : []),
    {
      title: '操作', key: 'action', width: isMobile ? 60 : 180,
      render: (_: any, record: any) => {
        if (isMobile) {
          return (
            <MenuPopup items={getActionMenuItems(record)} />
          );
        }
        return (
          <Space>
            <Button type="link" size="small" icon={<MessageOutlined />} onClick={() => viewDetail(record)}>
              {record.status === 0 ? '接单处理' : record.status === 2 ? '查看回复' : '查看'}
            </Button>
            {record.status === 0 && (
              <Button type="link" size="small" style={{ color: '#1890ff' }} onClick={() => { setSelectedTicket(record); handleStatusChange(record.ticketNo, 1); }}>
                接单
              </Button>
            )}
            {record.status === 1 && (
              <Button type="link" size="small" style={{ color: '#52c41a' }} icon={<CheckCircleOutlined />} onClick={() => { setSelectedTicket(record); showResolveModal(3); }}>解决</Button>
            )}
          </Space>
        );
      }
    },
  ];

  const tabItems = [
    { key: 'all', label: `全部 (${tickets.length})` },
    { key: '0', label: <Badge count={counts[0] || 0} offset={[6, 0]}>待处理</Badge> },
    ...(!isMobile ? [
      { key: '1', label: <Badge count={counts[1] || 0} offset={[6, 0]} color="blue">处理中</Badge> },
      { key: '2', label: <Badge count={counts[2] || 0} offset={[6, 0]} color="orange">等待回复</Badge> },
    ] : [
      { key: '1', label: `处理中 (${counts[1] || 0})` },
      { key: '2', label: `等待回复 (${counts[2] || 0})` },
    ]),
    { key: '3', label: `已解决 (${counts[3] || 0})` },
    { key: '4', label: `已关闭 (${counts[4] || 0})` },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>工单管理</Title>
        </div>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
        <Table columns={columns} dataSource={getFilteredTickets()} rowKey="id" loading={loading} pagination={{ pageSize: 15, simple: isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 条` }} scroll={{ x: isMobile ? 300 : undefined }} size={isMobile ? 'small' : 'middle'} />

        <Modal
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>工单 {selectedTicket?.ticketNo}</span>
              {selectedTicket && <Tag color={statusMap[selectedTicket.status]?.color}>{statusMap[selectedTicket.status]?.text}</Tag>}
            </div>
          }
          open={detailVisible}
          onCancel={() => setDetailVisible(false)}
          width={isMobile ? '100%' : 700}
          className={isMobile ? 'mobile-modal' : undefined}
          footer={
            selectedTicket && selectedTicket.status !== 3 && selectedTicket.status !== 4 ? (
              <div style={{ display: 'flex', justifyContent: 'space-between', flexDirection: isMobile ? 'column' : 'row', gap: 8 }}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {selectedTicket.status === 0 && (
                    <Button type="primary" size={isMobile ? 'small' : 'middle'} onClick={handleAcceptAndReply}>
                      接单并回复
                    </Button>
                  )}
                  {selectedTicket.status === 1 && (
                    <Button size={isMobile ? 'small' : 'middle'} onClick={() => { sendReply(); }}>
                      发送回复
                    </Button>
                  )}
                  {selectedTicket.status === 2 && (
                    <Button type="primary" size={isMobile ? 'small' : 'middle'} onClick={sendReply}>
                      发送回复
                    </Button>
                  )}
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  <Button type="primary" ghost size={isMobile ? 'small' : 'middle'} style={{ color: '#52c41a', borderColor: '#52c41a' }} onClick={() => showResolveModal(3)}>
                    <CheckCircleOutlined /> 标记已解决
                  </Button>
                  <Button danger size={isMobile ? 'small' : 'middle'} onClick={() => showResolveModal(4)}>
                    <CloseCircleOutlined /> 关闭工单
                  </Button>
                </div>
              </div>
            ) : null
          }
        >
          <div style={{ marginBottom: 12, padding: '8px 12px', background: '#fafafa', borderRadius: 6 }}>
            <Space size={16}>
              <Text type="secondary">分类: <Text strong>{selectedTicket?.category}</Text></Text>
              <Text type="secondary">优先级: <Tag color={priorityMap[selectedTicket?.priority]?.color} style={{ marginLeft: 4 }}>{priorityMap[selectedTicket?.priority]?.text}</Tag></Text>
            </Space>
          </div>
          <List
            dataSource={messages}
            style={{ maxHeight: 400, overflow: 'auto' }}
            renderItem={(msg: any) => (
              <List.Item style={{ padding: '8px 0' }}>
                <List.Item.Meta
                  avatar={
                    <Avatar
                      icon={msg.senderType === 'ADMIN' ? <RobotOutlined /> : <UserOutlined />}
                      style={{ backgroundColor: msg.senderType === 'ADMIN' ? '#1890ff' : '#87d068' }}
                    />
                  }
                  title={
                    <span>
                      <Text strong>{msg.senderType === 'ADMIN' ? '管理员' : '用户'}</Text>
                      <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>{msg.createdAt}</Text>
                    </span>
                  }
                  description={
                    <div style={{ whiteSpace: 'pre-wrap', marginTop: 4 }}>{msg.content}</div>
                  }
                />
              </List.Item>
            )}
          />
          {selectedTicket && selectedTicket.status !== 3 && selectedTicket.status !== 4 && (
            <div style={{ marginTop: 12 }}>
              <TextArea
                value={newMessage}
                onChange={e => setNewMessage(e.target.value)}
                rows={3}
                placeholder={selectedTicket.status === 0 ? '接单并输入回复内容...' : '输入回复内容...'}
              />
            </div>
          )}
        </Modal>
      </Card>

      {/* 解决/关闭工单确认弹窗 */}
      <Modal
        title={resolveStatus === 3 ? '标记工单已解决' : '关闭工单'}
        open={resolveModalVisible}
        onOk={handleResolveConfirm}
        onCancel={() => setResolveModalVisible(false)}
        width={isMobile ? '100%' : 480}
        className={isMobile ? 'mobile-modal' : undefined}
        okText={resolveStatus === 3 ? '确认解决' : '确认关闭'}
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <Text>工单号：{selectedTicket?.ticketNo}</Text>
        </div>
        <div style={{ marginBottom: 16 }}>
          <Text>标题：{selectedTicket?.title}</Text>
        </div>
        <div style={{ marginBottom: 16 }}>
          <Text strong>处理备注</Text>
          <TextArea
            value={resolveRemark}
            onChange={e => setResolveRemark(e.target.value)}
            rows={3}
            placeholder="输入处理备注（可选）"
            style={{ marginTop: 8 }}
          />
        </div>
        <div style={{ marginBottom: 16 }}>
          <Checkbox checked={resolveEmail} onChange={e => setResolveEmail(e.target.checked)}>
            同时发送邮件通知用户
          </Checkbox>
        </div>
      </Modal>
    </div>
  );
};

export default AdminTicketPage;
