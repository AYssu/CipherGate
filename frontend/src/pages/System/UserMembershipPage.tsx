import React from 'react';
import { Card, Table, Button, Modal, Form, InputNumber, Input, message, Typography, Tag } from 'antd';

const { Title } = Typography;

const UserMembershipPage: React.FC = () => {
  const [users, setUsers] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [editVisible, setEditVisible] = React.useState(false);
  const [editingUser, setEditingUser] = React.useState<any>(null);
  const [form] = Form.useForm();

  React.useEffect(() => { fetchUsers(); }, []);

  const fetchUsers = () => {
    fetch('/api/users', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setUsers(data.data || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const handleGrantBalance = async () => {
    const values = await form.validateFields();
    const res = await fetch(`/api/membership/users/${editingUser.id}/grant-balance`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: values.amount * 100, description: values.description })
    });
    const data = await res.json();
    if (data.success) { message.success('充值成功'); setEditVisible(false); }
    else message.error(data.message);
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id' },
    { title: '用户名', dataIndex: 'login', key: 'login' },
    { title: '姓名', dataIndex: 'name', key: 'name' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: number) => <Tag color={v === 1 ? 'success' : 'error'}>{v === 1 ? '正常' : '禁用'}</Tag> },
    { title: '操作', key: 'action', render: (_: any, record: any) => <Button type="link" onClick={() => { setEditingUser(record); form.resetFields(); setEditVisible(true); }}>充值余额</Button> },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <Title level={4}>用户会员管理</Title>
        <Table columns={columns} dataSource={users} rowKey="id" loading={loading} />
        <Modal title={`为 ${editingUser?.login} 充值余额`} open={editVisible} onOk={handleGrantBalance} onCancel={() => setEditVisible(false)}>
          <Form form={form} layout="vertical">
            <Form.Item name="amount" label="充值金额(元)" rules={[{ required: true }]}><InputNumber min={0.01} step={0.01} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="description" label="备注"><Input /></Form.Item>
          </Form>
        </Modal>
      </Card>
    </div>
  );
};

export default UserMembershipPage;
