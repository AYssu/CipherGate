import React from 'react';
import { Card, Table, Tag, Button, Modal, Input, message, Typography, List, Avatar, Space, Tabs, Popconfirm, Badge } from 'antd';
import { UserOutlined, RobotOutlined, CheckCircleOutlined, CloseCircleOutlined, MessageOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;
const { TextArea } = Input;

const AdminTicketPage: React.FC = () => {
  const [tickets, setTickets] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [detailVisible, setDetailVisible] = React.useState(false);
  const [selectedTicket, setSelectedTicket] = React.useState<any>(null);
  const [messages, setMessages] = React.useState<any[]>([]);
  const [newMessage, setNewMessage] = React.useState('');
  const [activeTab, setActiveTab] = React.useState('all');
  const [counts, setCounts] = React.useState<Record<number, number>>({});

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

  const handleStatusChange = async (ticketNo: string, status: number) => {
    await fetch(`/api/admin/tickets/${ticketNo}/status`, {
      method: 'PUT', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
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

  const handleResolve = async () => {
    if (!selectedTicket) return;
    await handleStatusChange(selectedTicket.ticketNo, 3);
    setDetailVisible(false);
    fetchTickets();
  };

  const handleClose = async () => {
    if (!selectedTicket) return;
    await handleStatusChange(selectedTicket.ticketNo, 4);
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

  const columns = [
    { title: '工单号', dataIndex: 'ticketNo', key: 'no', width: 160 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80, render: (v: number) => <Tag color={priorityMap[v]?.color}>{priorityMap[v]?.text}</Tag> },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
    { title: '最后回复', dataIndex: 'lastReplyAt', key: 'lastReply', width: 160, render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
    {
      title: '操作', key: 'action', width: 180,
      render: (_: any, record: any) => (
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
            <Popconfirm title="确认标记为已解决？" onConfirm={() => handleStatusChange(record.ticketNo, 3)}>
              <Button type="link" size="small" style={{ color: '#52c41a' }} icon={<CheckCircleOutlined />}>解决</Button>
            </Popconfirm>
          )}
        </Space>
      )
    },
  ];

  const tabItems = [
    { key: 'all', label: `全部 (${tickets.length})` },
    { key: '0', label: <Badge count={counts[0] || 0} offset={[6, 0]}>待处理</Badge> },
    { key: '1', label: <Badge count={counts[1] || 0} offset={[6, 0]} color="blue">处理中</Badge> },
    { key: '2', label: <Badge count={counts[2] || 0} offset={[6, 0]} color="orange">等待回复</Badge> },
    { key: '3', label: `已解决 (${counts[3] || 0})` },
    { key: '4', label: `已关闭 (${counts[4] || 0})` },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={4} style={{ margin: 0 }}>工单管理</Title>
        </div>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
        <Table columns={columns} dataSource={getFilteredTickets()} rowKey="id" loading={loading} pagination={{ pageSize: 15 }} />

        <Modal
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>工单 {selectedTicket?.ticketNo}</span>
              {selectedTicket && <Tag color={statusMap[selectedTicket.status]?.color}>{statusMap[selectedTicket.status]?.text}</Tag>}
            </div>
          }
          open={detailVisible}
          onCancel={() => setDetailVisible(false)}
          width={700}
          footer={
            selectedTicket && selectedTicket.status !== 3 && selectedTicket.status !== 4 ? (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div>
                  {selectedTicket.status === 0 && (
                    <Button type="primary" onClick={handleAcceptAndReply} style={{ marginRight: 8 }}>
                      接单并回复
                    </Button>
                  )}
                  {selectedTicket.status === 1 && (
                    <Button onClick={() => { sendReply(); }} style={{ marginRight: 8 }}>
                      发送回复
                    </Button>
                  )}
                  {selectedTicket.status === 2 && (
                    <Button type="primary" onClick={sendReply} style={{ marginRight: 8 }}>
                      发送回复
                    </Button>
                  )}
                </div>
                <div>
                  <Popconfirm title="确认标记为已解决？" onConfirm={handleResolve}>
                    <Button type="primary" ghost style={{ color: '#52c41a', borderColor: '#52c41a', marginRight: 8 }}>
                      <CheckCircleOutlined /> 标记已解决
                    </Button>
                  </Popconfirm>
                  <Popconfirm title="确认关闭此工单？" onConfirm={handleClose}>
                    <Button danger>
                      <CloseCircleOutlined /> 关闭工单
                    </Button>
                  </Popconfirm>
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
    </div>
  );
};

export default AdminTicketPage;
