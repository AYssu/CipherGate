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
  Avatar,
  Grid,
  Dropdown,
} from 'antd';
import {
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  StopOutlined,
  CheckCircleOutlined,
  UserOutlined,
  MoreOutlined,
  KeyOutlined,
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import { roleApi } from '../services/roleService';
import type { User, Role } from '../services/userService';

const { Text } = Typography;

const UserManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [form] = Form.useForm();
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [passwordForm] = Form.useForm();
  const [resettingPassword, setResettingPassword] = useState(false);

  const [keyword, setKeyword] = useState('');
  const [statusFilter] = useState<number | 'all'>('all');
  const [roleFilter] = useState<number | 'all'>('all');

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
        : [
            u.name,
            u.login,
            u.email,
            u.githubId,
            u.status,
            u.createdAt,
            u.updatedAt,
            u.lastLoginAt,
            (u.roles || []).map((r) => `${r.roleName} ${r.roleCode}`).join(' '),
          ]
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
  const allUserColumns = useMemo(() => [
    {
      title: '用户',
      key: 'user',
      render: (record: User) => (
        <Space>
          <Avatar
            src={record.avatarUrl}
            icon={<UserOutlined />}
            size={isMobile ? 28 : 32}
          />
          <div>
            <div style={{ fontWeight: 500, fontSize: isMobile ? 13 : 14 }}>{record.name || record.login}</div>
            <Text type="secondary" style={{ fontSize: isMobile ? 11 : 12 }}>@{record.login}</Text>
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
      width: isMobile ? 100 : undefined,
      render: (record: User) => (
        <Space wrap size={4}>
          {record.roles?.map(role => (
            <Tag
              key={role.id}
              color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
              style={{ margin: 0, fontSize: isMobile ? 11 : 12 }}
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
      width: 70,
      render: (record: User) => (
        <Tag
          color={record.status === 1 ? 'green' : 'red'}
          icon={record.status === 1 ? <CheckCircleOutlined /> : <StopOutlined />}
          style={{ margin: 0, fontSize: isMobile ? 11 : 12 }}
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
      width: 60,
      render: (record: User) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditUser(record) },
                  { key: 'password', label: '重置密码', icon: <KeyOutlined />, onClick: () => handleResetPassword(record) },
                  { type: 'divider' },
                  { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, disabled: record.roles?.some(role => role.roleCode === 'SUPER_ADMIN'), onClick: () => handleDeleteUser(record) },
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
            <Tooltip title="重置密码">
              <Button
                type="link"
                icon={<KeyOutlined />}
                onClick={() => handleResetPassword(record)}
                size="small"
              >
                密码
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
        );
      },
    },
  ], [isMobile]);

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

  const handleResetPassword = (user: User) => {
    setEditingUser(user);
    passwordForm.resetFields();
    setPasswordModalVisible(true);
  };

  const handlePasswordModalOk = async () => {
    try {
      const values = await passwordForm.validateFields();
      setResettingPassword(true);
      const result = await userApi.resetPassword(editingUser!.id, values.password);
      if ((result as any).success) {
        message.success('密码重置成功');
        setPasswordModalVisible(false);
        setEditingUser(null);
        passwordForm.resetFields();
      } else {
        message.error((result as any).message || '密码重置失败');
      }
    } catch (error: any) {
      if (!error?.errorFields) {
        console.error('重置密码失败:', error);
      }
    } finally {
      setResettingPassword(false);
    }
  };

  return (
    <div style={{ padding: 0 }}>
      <Card
        styles={{ body: { padding: isMobile ? 12 : 24 } }}
      >
        {/* 标题栏 */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: isMobile ? 12 : 16,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <UserOutlined style={{ color: '#1677ff', fontSize: isMobile ? 18 : 20 }} />
            <Text strong style={{ fontSize: isMobile ? 16 : 20, whiteSpace: 'nowrap' }}>
              用户管理
            </Text>
          </div>
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchUsers}
            loading={loading}
            size={isMobile ? 'small' : 'middle'}
          >
            {!isMobile && '刷新'}
          </Button>
        </div>

        {/* 搜索框 */}
        <Input
          allowClear
          prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
          placeholder="搜索用户名、邮箱、角色..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: '100%', marginBottom: isMobile ? 12 : 16 }}
        />

        {/* 用户列表 */}
        {isMobile ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {loading && (
              <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>加载中...</div>
            )}
            {!loading && filteredUsers.length === 0 && (
              <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无用户</div>
            )}
            {filteredUsers.map((user) => (
              <div
                key={user.id}
                style={{
                  background: '#fafafa',
                  borderRadius: 8,
                  padding: '10px 12px',
                  border: '1px solid #f0f0f0',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <Avatar
                    src={user.avatarUrl}
                    icon={<UserOutlined />}
                    size={36}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Text strong style={{ fontSize: 14 }}>
                        {user.name || user.login}
                      </Text>
                      <Tag
                        color={user.status === 1 ? 'green' : 'red'}
                        style={{ margin: 0, fontSize: 11, lineHeight: '18px', padding: '0 4px' }}
                      >
                        {user.status === 1 ? '正常' : '禁用'}
                      </Tag>
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>@{user.login}</Text>
                    {user.roles && user.roles.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
                        {user.roles.map(role => (
                          <Tag
                            key={role.id}
                            color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                            style={{ margin: 0, fontSize: 11 }}
                          >
                            {role.roleName}
                          </Tag>
                        ))}
                      </div>
                    )}
                  </div>
                  <Dropdown
                    menu={{
                      items: [
                        { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditUser(user) },
                        { key: 'password', label: '重置密码', icon: <KeyOutlined />, onClick: () => handleResetPassword(user) },
                        { type: 'divider' },
                        { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, disabled: user.roles?.some(role => role.roleCode === 'SUPER_ADMIN'), onClick: () => handleDeleteUser(user) },
                      ],
                    }}
                    trigger={['click']}
                  >
                    <Button type="text" size="small" icon={<MoreOutlined />} />
                  </Dropdown>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Table
            columns={allUserColumns}
            dataSource={filteredUsers}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showQuickJumper: true,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            scroll={{ x: 1200 }}
            size="middle"
          />
        )}
      </Card>

      {/* 编辑用户模态框 */}
      <Modal
        title="编辑用户"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={isMobile ? '100%' : 600}
        className={isMobile ? 'mobile-modal' : undefined}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="用户状态"
            name="status"
            rules={[{ required: true, message: '请选择用户状态' }]}
          >
            <Select>
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

      {/* 重置密码模态框 */}
      <Modal
        title={`重置密码 - ${editingUser?.name || editingUser?.login || ''}`}
        open={passwordModalVisible}
        onOk={handlePasswordModalOk}
        onCancel={() => {
          setPasswordModalVisible(false);
          setEditingUser(null);
          passwordForm.resetFields();
        }}
        width={isMobile ? '100%' : 400}
        className={isMobile ? 'mobile-modal' : undefined}
        okText="确定重置"
        cancelText="取消"
        confirmLoading={resettingPassword}
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item
            label="新密码"
            name="password"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码长度不能少于6位' }
            ]}
          >
            <Input.Password placeholder="请输入新密码（至少6位）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementContent;
