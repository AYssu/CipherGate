import React, { useState, useEffect } from 'react';
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
  List,
  Avatar,
  Dropdown
} from 'antd';
import { 
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  StopOutlined,
  CheckCircleOutlined,
  MoreOutlined,
  UserOutlined
} from '@ant-design/icons';
import { userApi } from '../services/userService';
import { roleApi } from '../services/roleService';
import type { User, Role } from '../services/userService';

const { Title, Text } = Typography;

const UserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [isMobile, setIsMobile] = useState(false);
  const [form] = Form.useForm();

  // 检测屏幕尺寸
  useEffect(() => {
    const checkScreenSize = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkScreenSize();
    window.addEventListener('resize', checkScreenSize);
    return () => window.removeEventListener('resize', checkScreenSize);
  }, []);

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

  // 用户表格列定义
  const userColumns = [
    {
      title: '用户',
      key: 'user',
      render: (record: User) => (
        <Space>
          <img 
            src={record.avatarUrl} 
            alt={record.name}
            style={{ width: 32, height: 32, borderRadius: '50%' }}
          />
          <div>
            <div>{record.name || record.login}</div>
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
        <Tag color={record.status === 1 ? 'green' : 'red'} icon={record.status === 1 ? <CheckCircleOutlined /> : <StopOutlined />}>
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

  // 移动端卡片列表渲染
  const renderMobileList = () => (
    <List
      loading={loading}
      dataSource={users}
      renderItem={(user) => (
        <List.Item style={{ padding: 0, marginBottom: 12 }}>
          <Card size="small" style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                <Avatar
                  src={user.avatarUrl}
                  icon={<UserOutlined />}
                  size={40}
                  style={{ marginRight: 12 }}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 500, marginBottom: 4 }}>
                    {user.name || user.login}
                  </div>
                  <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>
                    @{user.login}
                  </div>
                  {user.email && (
                    <div style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>
                      {user.email}
                    </div>
                  )}
                  <Space wrap size={[4, 4]}>
                    <Tag 
                      color={user.status === 1 ? 'green' : 'red'} 
                      icon={user.status === 1 ? <CheckCircleOutlined /> : <StopOutlined />}
                      style={{ fontSize: 11 }}
                    >
                      {user.status === 1 ? '正常' : '禁用'}
                    </Tag>
                    {user.roles?.map(role => (
                      <Tag 
                        key={role.id}
                        color={role.roleCode === 'SUPER_ADMIN' ? 'red' : role.roleCode === 'ADMIN' ? 'blue' : 'green'}
                        style={{ fontSize: 11 }}
                      >
                        {role.roleName}
                      </Tag>
                    ))}
                  </Space>
                  {user.lastLoginAt && (
                    <div style={{ fontSize: 11, color: '#999', marginTop: 4 }}>
                      最后登录: {new Date(user.lastLoginAt).toLocaleString('zh-CN')}
                    </div>
                  )}
                </div>
              </div>
              <Dropdown menu={getUserActionMenu(user)} trigger={['click']}>
                <Button type="text" icon={<MoreOutlined />} size="small" />
              </Dropdown>
            </div>
          </Card>
        </List.Item>
      )}
      pagination={{
        pageSize: 10,
        showSizeChanger: false,
        showQuickJumper: false,
        showTotal: (total) => `共 ${total} 条记录`,
        simple: true,
      }}
    />
  );

  return (
    <>
      <Card>
        <div style={{ 
          marginBottom: 16, 
          display: 'flex', 
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 8
        }}>
          <Title level={4} style={{ margin: 0 }}>用户列表</Title>
          <Button 
            type="primary" 
            icon={<ReloadOutlined />}
            onClick={fetchUsers}
            loading={loading}
            size={isMobile ? 'middle' : 'middle'}
          >
            刷新
          </Button>
        </div>
        
        {isMobile ? renderMobileList() : (
          <Table
            columns={userColumns}
            dataSource={users}
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
    </>
  );
};

export default UserManagementContent;