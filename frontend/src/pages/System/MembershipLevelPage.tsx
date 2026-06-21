import React from 'react';
import { Card, Table, Button, Modal, Form, Input, InputNumber, message, Typography } from 'antd';

const { Title } = Typography;

const MembershipLevelPage: React.FC = () => {
  const [levels, setLevels] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [editVisible, setEditVisible] = React.useState(false);
  const [editing, setEditing] = React.useState<any>(null);
  const [form] = Form.useForm();

  React.useEffect(() => { fetchLevels(); }, []);

  const fetchLevels = () => {
    fetch('/api/membership/levels', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setLevels(data.data || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const url = editing ? `/api/membership/levels/${editing.id}` : '/api/membership/levels';
    const method = editing ? 'PUT' : 'POST';
    const res = await fetch(url, {
      method, credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values)
    });
    const data = await res.json();
    if (data.success) { message.success('保存成功'); setEditVisible(false); fetchLevels(); }
    else message.error(data.message);
  };

  const columns = [
    { title: '等级', dataIndex: 'level', key: 'level' },
    { title: '名称', dataIndex: 'levelName', key: 'name' },
    { title: '价格', dataIndex: 'price', key: 'price', render: (v: number) => `¥${v}` },
    { title: '应用额度', dataIndex: 'appQuota', key: 'appQuota', render: (v: number) => v === -1 ? '不限' : v },
    { title: '卡密额度', dataIndex: 'licenseQuota', key: 'licenseQuota', render: (v: number) => v === -1 ? '不限' : v },
    { title: '用户额度', dataIndex: 'userRegisterQuota', key: 'userQuota', render: (v: number) => v === -1 ? '不限' : v },
    { title: '时长(天)', dataIndex: 'durationDays', key: 'duration', render: (v: number) => v === 0 ? '永久' : v },
    { title: '操作', key: 'action', render: (_: any, record: any) => <Button type="link" onClick={() => { setEditing(record); form.setFieldsValue(record); setEditVisible(true); }}>编辑</Button> },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Title level={4} style={{ margin: 0 }}>会员等级管理</Title>
          <Button type="primary" onClick={() => { setEditing(null); form.resetFields(); setEditVisible(true); }}>新增等级</Button>
        </div>
        <Table columns={columns} dataSource={levels} rowKey="id" loading={loading} pagination={false} />
        <Modal title={editing ? '编辑等级' : '新增等级'} open={editVisible} onOk={handleSave} onCancel={() => setEditVisible(false)}>
          <Form form={form} layout="vertical">
            <Form.Item name="level" label="等级编号" rules={[{ required: true }]}><InputNumber min={1} max={99} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="levelName" label="等级名称" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="price" label="价格(元)" rules={[{ required: true }]}><InputNumber min={0} step={0.01} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="appQuota" label="应用额度(-1不限)"><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="licenseQuota" label="卡密额度(-1不限)"><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="userRegisterQuota" label="用户注册额度(-1不限)"><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="trafficQuota" label="流量额度(字节)"><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="durationDays" label="时长(天，0=永久)"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="description" label="描述"><Input.TextArea rows={2} /></Form.Item>
          </Form>
        </Modal>
      </Card>
    </div>
  );
};

export default MembershipLevelPage;
