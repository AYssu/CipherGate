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
  message,
  Popconfirm,
  Tooltip,
  Avatar
} from 'antd';
import { 
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  StopOutlined,
  CheckCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import { roleApi } from '../services/roleService';
import type { User, Role } from '../services/userService';

const { Text } = Typography;

const UserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [form] = Form.useForm();

  const [keyword] = useState('');
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

  return (
    <div style={{ padding: 0 }}>
      <Card>
        <div style={{ marginBottom: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              marginBottom: 16
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                flex: 1,
              }}
            >
              <UserOutlined style={{ color: '#1677ff', fontSize: 20 }} />
              <Text strong style={{ fontSize: 20 }}>
                用户管理
              </Text>
            </div>

            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={fetchUsers}
              loading={loading}
            >
              刷新
            </Button>
          </div>
        </div>
        
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
      </Card>

      {/* 编辑用户模态框 */}
      <Modal
        title="编辑用户"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={600}
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
    </div>
  );
};

export default UserManagementContent;
