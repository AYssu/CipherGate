import React, { useMemo, useState, useEffect } from 'react';
import { 
  Card, 
  Typography, 
  Space, 
  Button, 
  Table, 
  Tag, 
  Modal, 
  Form, 
  Select, 
  Input,
  message,
  Popconfirm,
  Tooltip,
  List,
  Avatar,
  Dropdown,
  Grid
} from 'antd';
import { 
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  StopOutlined,
  CheckCircleOutlined,
  MoreOutlined,
  UserOutlined,
  SearchOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import { roleApi } from '../services/roleService';
import type { User, Role } from '../services/userService';

const { Text } = Typography;
const { useBreakpoint } = Grid;

const UserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [form] = Form.useForm();

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | 'all'>('all');
  const [roleFilter, setRoleFilter] = useState<number | 'all'>('all');

  // 获取用户列表
  const fetchUsers = async () => {
    setLoading(true);
    try {
      const result = await userApi.getUsers();
      setUsers((result as any).data || []);
    } catch (error) {
      console.error('获取用户列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 获取角色列表
  const fetchRoles = async () => {
    try {
      const result = await roleApi.getRoles();
      setRoles((result as any).data || []);
    } catch (error) {
      console.error('获取角色列表失败:', error);
    }
  };

  useEffect(() => {
    fetchUsers();
    fetchRoles();
  }, []);

  const filteredUsers = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return users.filter((u) => {
      const matchKeyword = !kw
        ? true
        : [u.name, u.login, u.email]
            .filter(Boolean)
            .some((v) => String(v).toLowerCase().includes(kw));
      const matchStatus = statusFilter === 'all' ? true : u.status === statusFilter;
      const matchRole =
        roleFilter === 'all'
          ? true
          : (u.roles || []).some((r) => r.id === roleFilter);
      return matchKeyword && matchStatus && matchRole;
    });
  }, [keyword, roleFilter, statusFilter, users]);

  // 用户表格列定义
  const userColumns = [
    {
      title: '用户',
      key: 'user',
      render: (record: User) => (
        <Space>
          <Avatar 
            src={record.avatarUrl} 
            icon={<UserOutlined />}
            size={32}
          />
          <div>
            <div style={{ fontWeight: 500 }}>{record.name || record.login}</div>
            <Text type="secondary" style={{ fontSize: 12 }}>@{record.login}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      render: (email: string) => email || '-',
    },
    {
      title: '角色',
      key: 'roles',
      render: (record: User) => (
        <Space wrap>
          {record.roles?.map(role => (
            <Tag 
              key={role.id}
              color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
            >
              {role.roleName}
            </Tag>
          )) || <Text type="secondary">无角色</Text>}
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      render: (record: User) => (
        <Tag 
          color={record.status === 1 ? 'green' : 'red'} 
          icon={record.status === 1 ? <CheckCircleOutlined /> : <StopOutlined />}
        >
          {record.status === 1 ? '正常' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (date: string) => date ? new Date(date).toLocaleString('zh-CN') : '-',
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      render: (record: User) => (
        <Space>
          <Tooltip title="编辑用户">
            <Button 
              type="link" 
              icon={<EditOutlined />}
              onClick={() => handleEditUser(record)}
              size="small"
            >
              编辑
            </Button>
          </Tooltip>
          <Popconfirm
            title="确认删除"
            description={`确定要删除用户 ${record.name || record.login} 吗？`}
            onConfirm={() => handleDeleteUser(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button 
              type="link" 
              danger
              icon={<DeleteOutlined />}
              disabled={record.roles?.some(role => role.roleCode === 'SUPER_ADMIN')}
              size="small"
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleEditUser = (user: User) => {
    setEditingUser(user);
    form.setFieldsValue({
      status: user.status,
      roleIds: user.roles?.map(role => role.id) || [],
    });
    setModalVisible(true);
  };

  const handleDeleteUser = async (user: User) => {
    try {
      await userApi.deleteUser(user.id);
      message.success('用户删除成功');
      fetchUsers();
    } catch (error) {
      console.error('删除用户失败:', error);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      await userApi.updateUser(editingUser!.id, values);
      message.success('用户更新成功');
      setModalVisible(false);
      setEditingUser(null);
      form.resetFields();
      fetchUsers();
    } catch (error) {
      console.error('更新用户失败:', error);
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingUser(null);
    form.resetFields();
  };

  // 移动端用户操作菜单
  const getUserActionMenu = (user: User) => ({
    items: [
      {
        key: 'edit',
        label: '编辑用户',
        icon: <EditOutlined />,
        onClick: () => handleEditUser(user),
      },
      {
        key: 'delete',
        label: '删除用户',
        icon: <DeleteOutlined />,
        danger: true,
        disabled: user.roles?.some(role => role.roleCode === 'SUPER_ADMIN'),
        onClick: () => {
          Modal.confirm({
            title: '确认删除',
            content: `确定要删除用户 ${user.name || user.login} 吗？`,
            onOk: () => handleDeleteUser(user),
            okText: '确定',
            cancelText: '取消',
          });
        },
      },
    ],
  });

  // 移动端紧凑列表渲染 - 企业风格
  const renderMobileList = () => (
    <List
      loading={loading}
      dataSource={filteredUsers}
      split={false}
      style={{ background: 'transparent' }}
      locale={{ emptyText: '暂无用户' }}
      renderItem={(user) => (
        <div style={{ padding: '8px 0' }}>
          <Card
            size="small"
            styles={{ body: { padding: 12 } }}
            style={{ borderRadius: 10 }}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <Avatar
                src={user.avatarUrl}
                icon={<UserOutlined />}
                size={40}
                style={{ flexShrink: 0 }}
              />

              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 8,
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <Text
                      strong
                      style={{
                        fontSize: 15,
                        display: 'block',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {user.name || user.login}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      @{user.login}
                    </Text>
                  </div>

                  <Dropdown menu={getUserActionMenu(user)} trigger={['click']} placement="bottomRight">
                    <Button type="text" icon={<MoreOutlined />} />
                  </Dropdown>
                </div>

                <div style={{ marginTop: 8, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <Tag
                    color={user.status === 1 ? 'success' : 'error'}
                    icon={user.status === 1 ? <CheckCircleOutlined /> : <StopOutlined />}
                    style={{ margin: 0 }}
                  >
                    {user.status === 1 ? '正常' : '禁用'}
                  </Tag>

                  {(user.roles || []).slice(0, 2).map((role) => (
                    <Tag
                      key={role.id}
                      color={
                        role.roleCode === 'SUPER_ADMIN'
                          ? 'red'
                          : role.roleCode === 'ADMIN'
                            ? 'blue'
                            : 'default'
                      }
                      style={{ margin: 0 }}
                    >
                      {role.roleName}
                    </Tag>
                  ))}
                </div>

                {!!user.email && (
                  <div style={{ marginTop: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {user.email}
                    </Text>
                  </div>
                )}

                {!!user.lastLoginAt && (
                  <div style={{ marginTop: 6 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      最后登录: {new Date(user.lastLoginAt).toLocaleString('zh-CN')}
                    </Text>
                  </div>
                )}
              </div>
            </div>
          </Card>
        </div>
      )}
      pagination={{
        pageSize: 20,
        size: 'small',
        showSizeChanger: false,
        showTotal: (total) => `共 ${total} 条`,
        simple: true,
        style: { textAlign: 'center', padding: '12px 0' }
      }}
    />
  );

  return (
    <div style={{ 
      padding: 0,
      background: isMobile ? '#f5f5f5' : 'transparent'
    }}>
      {/* 用户列表 */}
      <Card 
        bordered={!isMobile}
        style={isMobile ? { 
          borderRadius: 0,
          boxShadow: 'none',
          marginBottom: 0
        } : undefined}
        bodyStyle={isMobile ? { padding: 12 } : undefined}
      >
        <div style={{ marginBottom: isMobile ? 12 : 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                flex: 1,
                minWidth: 0,
                whiteSpace: 'nowrap',
              }}
            >
              <UserOutlined style={{ color: '#1677ff', flexShrink: 0 }} />
              <Text
                strong
                style={{
                  fontSize: isMobile ? 16 : 20,
                  whiteSpace: 'nowrap',
                }}
              >
                用户管理
              </Text>
            </div>

            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={fetchUsers}
              loading={loading}
            >
              {isMobile ? '' : '刷新'}
            </Button>
          </div>

          {isMobile && (
            <div style={{ marginTop: 12 }}>
              <Space direction="vertical" style={{ width: '100%' }} size={10}>
                <Input
                  placeholder="搜索姓名 / 账号 / 邮箱"
                  allowClear
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  prefix={<SearchOutlined style={{ color: 'rgba(0,0,0,0.45)' }} />}
                />

                <div style={{ display: 'flex', gap: 10 }}>
                  <Select
                    value={statusFilter}
                    onChange={setStatusFilter}
                    style={{ flex: 1 }}
                    options={[
                      { label: '全部状态', value: 'all' },
                      { label: '正常', value: 1 },
                      { label: '禁用', value: 0 },
                    ]}
                  />

                  <Select
                    value={roleFilter}
                    onChange={setRoleFilter}
                    style={{ flex: 1 }}
                    options={[
                      { label: '全部角色', value: 'all' },
                      ...roles.map((r) => ({ label: r.roleName, value: r.id })),
                    ]}
                  />
                </div>
              </Space>
            </div>
          )}
        </div>
        
        {isMobile ? renderMobileList() : (
          <Table
            columns={userColumns}
            dataSource={filteredUsers}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            scroll={{ x: 1200 }}
          />
        )}
      </Card>

      {/* 编辑用户模态框 */}
      <Modal
        title="编辑用户"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={isMobile ? '90%' : 600}
        style={isMobile ? { top: 20 } : undefined}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="用户状态"
            name="status"
            rules={[{ required: true, message: '请选择用户状态' }]}
          >
            <Select size={isMobile ? 'large' : 'middle'}>
              <Select.Option value={1}>正常</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            label="用户角色"
            name="roleIds"
            rules={[{ required: true, message: '请选择用户角色' }]}
          >
            <Select 
              mode="multiple" 
              placeholder="请选择角色"
              size={isMobile ? 'large' : 'middle'}
            >
              {roles.map(role => (
                <Select.Option key={role.id} value={role.id}>
                  {role.roleName}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementContent;
