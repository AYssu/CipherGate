import React from 'react';
import { Card, Table, Button, Modal, Form, InputNumber, Input, message, Typography, Tag, Space, Descriptions, Tabs, Grid, Dropdown, Select } from 'antd';
import { MoreOutlined, ReloadOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const formatBytes = (bytes: number | null | undefined) => {
  if (bytes == null || bytes < 0) return '0 B';
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatMoney = (fen: number | null | undefined) => {
  if (fen == null) return '¥0.00';
  return `¥${(fen / 100).toFixed(2)}`;
};

const UserMembershipPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [users, setUsers] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [levels, setLevels] = React.useState<any[]>([]);
  const [detailVisible, setDetailVisible] = React.useState(false);
  const [editingUser, setEditingUser] = React.useState<any>(null);
  const [editType, setEditType] = React.useState<string>('');
  const [selectedRowKeys, setSelectedRowKeys] = React.useState<number[]>([]);
  const [form] = Form.useForm();
  const [inviteEnabled, setInviteEnabled] = React.useState(true);

  React.useEffect(() => {
    fetchUsers();
    fetchLevels();
    fetch('/api/config/public/invite-status', { credentials: 'include' })
      .then(r => r.json())
      .then(res => setInviteEnabled(res?.data?.enabled !== false))
      .catch(() => {});
  }, []);

  const fetchUsers = () => {
    setLoading(true);
    fetch('/api/membership/users', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setUsers(data.data || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const fetchLevels = () => {
    fetch('/api/membership/levels', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setLevels(data.data || []); })
      .catch(() => {});
  };

  const openDetail = (record: any) => {
    setEditingUser(record);
    setDetailVisible(true);
  };

  const openEditLevel = (record: any) => {
    setEditingUser(record);
    setEditType('level');
    form.setFieldsValue({ levelId: record.levelId });
  };

  const openEditExtraQuota = (record: any) => {
    setEditingUser(record);
    setEditType('extraQuota');
    form.setFieldsValue({
      extraAppQuota: record.extraAppQuota || 0,
      extraLicenseQuota: record.extraLicenseQuota || 0,
      extraUserRegisterQuota: record.extraUserRegisterQuota || 0,
      extraTrafficQuota: record.extraTrafficQuota || 0,
    });
  };

  const openEditBalance = (record: any) => {
    setEditingUser(record);
    setEditType('balance');
    form.resetFields();
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (!editingUser) return;

    let res: Response;
    if (editType === 'level') {
      res = await fetch(`/api/membership/users/${editingUser.userId}`, {
        method: 'PUT', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ levelId: values.levelId, remark: '管理员手动调整' })
      });
    } else if (editType === 'extraQuota') {
      res = await fetch(`/api/membership/users/${editingUser.userId}/extra-quota`, {
        method: 'PUT', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
    } else if (editType === 'balance') {
      res = await fetch(`/api/membership/users/${editingUser.userId}/grant-balance`, {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: values.amount * 100, description: values.description || '管理员充值' })
      });
    } else if (editType === 'expires') {
      res = await fetch(`/api/membership/users/${editingUser.userId}/expires`, {
        method: 'PUT', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memberExpiresAt: values.memberExpiresAt || null })
      });
    } else {
      return;
    }

    const data = await res!.json();
    if (data.success) {
      message.success('更新成功');
      setDetailVisible(false);
      setEditType('');
      fetchUsers();
    } else {
      message.error(data.message);
    }
  };

  const allColumns = [
    { title: 'ID', dataIndex: 'userId', key: 'userId', width: 70 },
    { title: '会员等级', dataIndex: 'levelName', key: 'levelName', render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '余额', dataIndex: 'balance', key: 'balance', render: (v: number) => formatMoney(v) },
    { title: '应用', key: 'app', render: (_: any, r: any) => <span>{r.appUsed || 0} / {r.appTotal}</span> },
    { title: '卡密', key: 'license', render: (_: any, r: any) => <span>{r.licenseUsed || 0} / {r.licenseTotal}</span> },
    { title: '终端用户', key: 'userReg', render: (_: any, r: any) => <span>{r.userRegisterUsed || 0} / {r.userRegisterTotal}</span> },
    { title: '流量', key: 'traffic', render: (_: any, r: any) => <span>{formatBytes(r.trafficUsed)} / {formatBytes(r.trafficTotal)}</span> },
    ...(inviteEnabled ? [{ title: '邀请人数', dataIndex: 'inviteCount', key: 'inviteCount', render: (v: number) => v || 0 }] : []),
    { title: '签到天数', dataIndex: 'totalCheckinDays', key: 'totalCheckinDays', render: (v: number) => v || 0 },
    { title: '到期时间', dataIndex: 'memberExpiresAt', key: 'memberExpiresAt', render: (v: string) => v || '永久' },
    {
      title: '操作', key: 'action', width: 200,
      render: (_: any, record: any) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'detail', label: '详情', onClick: () => openDetail(record) },
                  { key: 'level', label: '改等级', onClick: () => openEditLevel(record) },
                  { key: 'quota', label: '改额度', onClick: () => openEditExtraQuota(record) },
                  { key: 'balance', label: '充值', onClick: () => openEditBalance(record) },
                ],
              }}
              trigger={['click']}
            >
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          );
        }
        return (
          <Space size="small">
            <Button type="link" size="small" onClick={() => openDetail(record)}>详情</Button>
            <Button type="link" size="small" onClick={() => openEditLevel(record)}>改等级</Button>
            <Button type="link" size="small" onClick={() => openEditExtraQuota(record)}>改额度</Button>
            <Button type="link" size="small" onClick={() => openEditBalance(record)}>充值</Button>
          </Space>
        );
      }
    },
  ];

  const MOBILE_VISIBLE_KEYS = ['levelName', 'app', 'traffic', 'action'];
  const displayColumns = isMobile
    ? allColumns
        .filter((c) => MOBILE_VISIBLE_KEYS.includes(c.key as string) || MOBILE_VISIBLE_KEYS.includes((c as any).dataIndex as string))
        .map((c) => c.key === 'action' ? { ...c, width: 60 } : c)
    : allColumns;

  const renderEditContent = () => {
    if (editType === 'level') {
      return (
        <Form form={form} layout="vertical">
          <Form.Item name="levelId" label="会员等级" rules={[{ required: true }]}>
            <Select placeholder="选择会员等级">
              {levels.map((l: any) => <Select.Option key={l.id} value={l.id}>{l.levelName} (¥{l.price})</Select.Option>)}
            </Select>
          </Form.Item>
        </Form>
      );
    }
    if (editType === 'extraQuota') {
      return (
        <Form form={form} layout="vertical">
          <Form.Item name="extraAppQuota" label="额外应用额度"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="extraLicenseQuota" label="额外卡密额度"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="extraUserRegisterQuota" label="额外终端用户额度"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="extraTrafficQuota" label="额外流量额度(字节)"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
        </Form>
      );
    }
    if (editType === 'balance') {
      return (
        <Form form={form} layout="vertical">
          <Form.Item name="amount" label="充值金额(元)" rules={[{ required: true }]}><InputNumber min={0.01} step={0.01} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="description" label="备注"><Input /></Form.Item>
        </Form>
      );
    }
    if (editType === 'expires') {
      return (
        <Form form={form} layout="vertical">
          <Form.Item name="memberExpiresAt" label="到期时间（留空=永久）">
            <Input placeholder="格式: 2026-12-31 23:59:59" />
          </Form.Item>
        </Form>
      );
    }
    return null;
  };

  const getEditTitle = () => {
    const name = editingUser?.userId || '';
    if (editType === 'level') return `修改用户 ${name} 的会员等级`;
    if (editType === 'extraQuota') return `修改用户 ${name} 的额外额度`;
    if (editType === 'balance') return `为用户 ${name} 充值余额`;
    if (editType === 'expires') return `设置用户 ${name} 的到期时间`;
    return '';
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: isMobile ? 12 : 16 }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>用户会员管理</Title>
          <Space size="small">
            {selectedRowKeys.length > 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>已选 {selectedRowKeys.length} 项</Text>
            )}
            <Button icon={<ReloadOutlined />} size="small" onClick={fetchUsers} loading={loading}>刷新</Button>
          </Space>
        </div>
        <Table
          columns={displayColumns}
          dataSource={users}
          rowKey="userId"
          loading={loading}
          size={isMobile ? 'small' : 'middle'}
          scroll={{ x: isMobile ? 420 : undefined, y: isMobile ? 450 : undefined }}
          pagination={{ pageSize: 20, simple: isMobile, showSizeChanger: !isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 个用户` }}
          rowSelection={{ selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys as number[]) }}
        />
      </Card>

      <Modal
        title={getEditTitle()}
        open={editType !== ''}
        onOk={handleSave}
        onCancel={() => { setEditType(''); setEditingUser(null); }}
        width={isMobile ? '100%' : 500}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        {renderEditContent()}
      </Modal>

      <Modal
        title={`用户 ${editingUser?.userId} 会员详情`}
        open={detailVisible}
        onCancel={() => { setDetailVisible(false); setEditingUser(null); }}
        footer={null}
        width={isMobile ? '100%' : 700}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        {editingUser && (
          <Tabs items={[
            {
              key: 'basic',
              label: '基本信息',
              children: (
                <Descriptions column={isMobile ? 1 : 2} bordered size="small">
                  <Descriptions.Item label="用户ID">{editingUser.userId}</Descriptions.Item>
                  <Descriptions.Item label="会员等级"><Tag color="blue">{editingUser.levelName}</Tag></Descriptions.Item>
                  <Descriptions.Item label="余额">{formatMoney(editingUser.balance)}</Descriptions.Item>
                  {inviteEnabled && <Descriptions.Item label="邀请码">{editingUser.inviteCode || '-'}</Descriptions.Item>}
                  {inviteEnabled && <Descriptions.Item label="邀请人数">{editingUser.inviteCount || 0}</Descriptions.Item>}
                  {inviteEnabled && <Descriptions.Item label="邀请人">{editingUser.invitedBy || '-'}</Descriptions.Item>}
                  <Descriptions.Item label="到期时间">{editingUser.memberExpiresAt || '永久'}</Descriptions.Item>
                  <Descriptions.Item label="创建时间">{editingUser.createdAt}</Descriptions.Item>
                </Descriptions>
              )
            },
            {
              key: 'quota',
              label: '额度使用',
              children: (
                <Descriptions column={isMobile ? 1 : 2} bordered size="small">
                  <Descriptions.Item label="应用额度">{editingUser.appUsed || 0} / {editingUser.appTotal}</Descriptions.Item>
                  <Descriptions.Item label="额外应用额度">{editingUser.extraAppQuota || 0}</Descriptions.Item>
                  <Descriptions.Item label="卡密额度">{editingUser.licenseUsed || 0} / {editingUser.licenseTotal}</Descriptions.Item>
                  <Descriptions.Item label="额外卡密额度">{editingUser.extraLicenseQuota || 0}</Descriptions.Item>
                  <Descriptions.Item label="终端用户额度">{editingUser.userRegisterUsed || 0} / {editingUser.userRegisterTotal}</Descriptions.Item>
                  <Descriptions.Item label="额外终端用户额度">{editingUser.extraUserRegisterQuota || 0}</Descriptions.Item>
                  <Descriptions.Item label="流量额度">{formatBytes(editingUser.trafficUsed)} / {formatBytes(editingUser.trafficTotal)}</Descriptions.Item>
                  <Descriptions.Item label="额外流量额度">{formatBytes(editingUser.extraTrafficQuota)}</Descriptions.Item>
                </Descriptions>
              )
            },
            {
              key: 'checkin',
              label: '签到信息',
              children: (
                <Descriptions column={isMobile ? 1 : 2} bordered size="small">
                  <Descriptions.Item label="连续签到天数">{editingUser.consecutiveCheckinDays || 0}</Descriptions.Item>
                  <Descriptions.Item label="累计签到天数">{editingUser.totalCheckinDays || 0}</Descriptions.Item>
                  <Descriptions.Item label="最后签到日期">{editingUser.lastCheckinDate || '-'}</Descriptions.Item>
                </Descriptions>
              )
            }
          ]} />
        )}
      </Modal>
    </div>
  );
};

export default UserMembershipPage;
