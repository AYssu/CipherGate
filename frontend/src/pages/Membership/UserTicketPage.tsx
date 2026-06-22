import React from 'react';
import { Card, Table, Tag, Button, Modal, Form, Input, Select, message, Typography, List, Avatar, Space, Tabs, Badge, Empty, Grid, Dropdown } from 'antd';
import { UserOutlined, RobotOutlined, PlusOutlined, MoreOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;
const { TextArea } = Input;

const UserTicketPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [tickets, setTickets] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [createVisible, setCreateVisible] = React.useState(false);
  const [detailVisible, setDetailVisible] = React.useState(false);
  const [selectedTicket, setSelectedTicket] = React.useState<any>(null);
  const [messages, setMessages] = React.useState<any[]>([]);
  const [newMessage, setNewMessage] = React.useState('');
  const [activeTab, setActiveTab] = React.useState('all');
  const [form] = Form.useForm();

  React.useEffect(() => { fetchTickets(); }, []);

  const fetchTickets = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/tickets?page=1&size=100', { credentials: 'include' });
      const data = await res.json();
      if (data.success) setTickets(data.data?.records || []);
    } finally { setLoading(false); }
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    const res = await fetch('/api/tickets', {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values)
    });
    const data = await res.json();
    if (data.success) { message.success('工单已创建'); setCreateVisible(false); form.resetFields(); fetchTickets(); }
    else message.error(data.message);
  };

  const viewDetail = async (ticket: any) => {
    setSelectedTicket(ticket);
    const res = await fetch(`/api/tickets/${ticket.ticketNo}`, { credentials: 'include' });
    const data = await res.json();
    if (data.success) { setMessages(data.data.messages || []); setDetailVisible(true); }
  };

  const sendMessage = async () => {
    if (!newMessage.trim()) return;
    await fetch(`/api/tickets/${selectedTicket.ticketNo}/messages`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: newMessage })
    });
    setNewMessage('');
    viewDetail(selectedTicket);
    fetchTickets();
  };

  const handleClose = async () => {
    if (!selectedTicket) return;
    await fetch(`/api/tickets/${selectedTicket.ticketNo}/close`, { method: 'POST', credentials: 'include' });
    message.success('工单已关闭');
    setDetailVisible(false);
    fetchTickets();
  };

  const handleUrge = async (ticketNo: string) => {
    const res = await fetch(`/api/tickets/${ticketNo}/urge`, { method: 'POST', credentials: 'include' });
    const data = await res.json();
    if (data.success) message.success('催办成功');
    else message.error(data.message);
  };

  const priorityMap: Record<number, { color: string; text: string }> = { 1: { color: 'default', text: '普通' }, 2: { color: 'orange', text: '重要' }, 3: { color: 'red', text: '紧急' } };
  const statusMap: Record<number, { color: string; text: string }> = { 0: { color: 'processing', text: '待处理' }, 1: { color: 'blue', text: '处理中' }, 2: { color: 'orange', text: '等待回复' }, 3: { color: 'success', text: '已解决' }, 4: { color: 'default', text: '已关闭' } };

  const getFilteredTickets = () => {
    if (activeTab === 'all') return tickets;
    const status = parseInt(activeTab);
    return tickets.filter(t => t.status === status);
  };

  const getTabCounts = () => {
    const c: Record<string, number> = { all: tickets.length, '0': 0, '1': 0, '2': 0, '3': 0, '4': 0 };
    tickets.forEach(t => { if (c[String(t.status)] !== undefined) c[String(t.status)]++; });
    return c;
  };

  const tabCounts = getTabCounts();

  const columns = [
    { title: '工单号', dataIndex: 'ticketNo', key: 'no', width: 160 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80, render: (v: number) => <Tag color={priorityMap[v]?.color}>{priorityMap[v]?.text}</Tag> },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'time', width: 160, render: (v: string) => new Date(v).toLocaleString('zh-CN') },
    {
      title: '操作', key: 'action', width: isMobile ? 80 : 200,
      render: (_: any, record: any) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'view', label: '查看', onClick: () => viewDetail(record) },
                  ...(record.status === 0 || record.status === 1
                    ? [{ key: 'urge', label: '催办', onClick: () => handleUrge(record.ticketNo) }]
                    : []),
                ],
              }}
              trigger={['click']}
            >
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          );
        }
        return (
          <Space>
            <Button type="link" size="small" onClick={() => viewDetail(record)}>查看</Button>
            {(record.status === 0 || record.status === 1) && (
              <Button type="link" size="small" onClick={() => handleUrge(record.ticketNo)}>催办</Button>
            )}
          </Space>
        );
      },
    },
  ];

  const tabItems = [
    { key: 'all', label: `全部 (${tabCounts.all})` },
    { key: '0', label: <Badge count={tabCounts['0']} offset={[6, 0]}>待处理</Badge> },
    { key: '1', label: <Badge count={tabCounts['1']} offset={[6, 0]} color="blue">处理中</Badge> },
    { key: '2', label: <Badge count={tabCounts['2']} offset={[6, 0]} color="orange">等待回复</Badge> },
    { key: '3', label: `已解决 (${tabCounts['3']})` },
    { key: '4', label: `已关闭 (${tabCounts['4']})` },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>我的工单</Title>
          <Button type="primary" size={isMobile ? 'small' : 'middle'} icon={<PlusOutlined />} onClick={() => setCreateVisible(true)}>创建工单</Button>
        </div>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
        <Table columns={columns} dataSource={getFilteredTickets()} rowKey="id" loading={loading} pagination={{ pageSize: 15, simple: isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 条` }} scroll={{ x: isMobile ? 420 : undefined, y: isMobile ? 450 : undefined }} size={isMobile ? 'small' : 'middle'} />

        <Modal title="创建工单" open={createVisible} onOk={handleCreate} onCancel={() => setCreateVisible(false)} width={isMobile ? '100%' : 520} className={isMobile ? 'mobile-modal' : undefined}>
          <Form form={form} layout="vertical">
            <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入工单标题' }]}>
              <Input placeholder="简要描述您的问题" />
            </Form.Item>
            <Form.Item name="category" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
              <Select placeholder="选择问题分类" options={[
                { value: 'TECHNICAL', label: '技术问题' },
                { value: 'BILLING', label: '账单问题' },
                { value: 'FEATURE', label: '功能建议' },
                { value: 'OTHER', label: '其他' }
              ]} />
            </Form.Item>
            <Form.Item name="priority" label="优先级" initialValue={1}>
              <Select options={[
                { value: 1, label: '普通' },
                { value: 2, label: '重要' },
                { value: 3, label: '紧急' }
              ]} />
            </Form.Item>
            <Form.Item name="content" label="问题描述" rules={[{ required: true, message: '请描述您的问题' }]}>
              <TextArea rows={4} placeholder="请详细描述您遇到的问题..." />
            </Form.Item>
          </Form>
        </Modal>

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
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div>
                  <Button onClick={handleClose}>关闭工单</Button>
                </div>
                <div>
                  <Button type="primary" onClick={sendMessage} disabled={!newMessage.trim()}>发送回复</Button>
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
          {messages.length === 0 ? (
            <Empty description="暂无消息" />
          ) : (
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
                        <Text strong>{msg.senderType === 'ADMIN' ? '管理员' : '我'}</Text>
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
          )}
          {selectedTicket && selectedTicket.status !== 3 && selectedTicket.status !== 4 && (
            <div style={{ marginTop: 12 }}>
              <TextArea
                value={newMessage}
                onChange={e => setNewMessage(e.target.value)}
                rows={3}
                placeholder={selectedTicket.status === 2 ? '管理员需要您补充信息，请输入...' : '输入回复内容...'}
              />
            </div>
          )}
        </Modal>
      </Card>
    </div>
  );
};

export default UserTicketPage;
