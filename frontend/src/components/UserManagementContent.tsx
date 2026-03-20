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
  Tooltip
} from 'antd';
import { 
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  StopOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface User {
  id: number;
  name: string;
  login: string;
  email: string;
  avatarUrl: string;
  roles: Role[];
  status: number;
  createdAt: string;
  lastLoginAt: string;
}

interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
}

const UserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [form] = Form.useForm();

  // 获取用户列表
  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/users', {
        credentials: 'include'
      });
      if (response.ok) {
        const result = await response.json();
        if (result.success) {
          setUsers(result.data || []);
        } else {
          message.error(result.message || '获取用户列表失败');
        }
      } else {
        message.error('获取用户列表失败');
      }
    } catch (error) {
      console.error('获取用户列表失败:', error);
      message.error('网络错误，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  // 获取角色列表
  const fetchRoles = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/roles', {
        credentials: 'include'
      });
      if (response.ok) {
        const result = await response.json();
        if (result.success) {
          setRoles(result.data || []);
        }
      }
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
      const response = await fetch(`http://localhost:8080/api/users/${user.id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      
      const result = await response.json();
      if (result.success) {
        message.success('用户删除成功');
        fetchUsers();
      } else {
        message.error(result.message || '用户删除失败');
      }
    } catch (error) {
      console.error('删除用户失败:', error);
      message.error('删除用户失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      const response = await fetch(`http://localhost:8080/api/users/${editingUser?.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify(values),
      });

      const result = await response.json();
      if (result.success) {
        message.success('用户更新成功');
        setModalVisible(false);
        setEditingUser(null);
        form.resetFields();
        fetchUsers();
      } else {
        message.error(result.message || '用户更新失败');
      }
    } catch (error) {
      console.error('更新用户失败:', error);
      message.error('用户更新失败');
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingUser(null);
    form.resetFields();
  };

  return (
    <>
      <Card>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
          <Title level={4}>用户列表</Title>
          <Button 
            type="primary" 
            icon={<ReloadOutlined />}
            onClick={fetchUsers}
            loading={loading}
          >
            刷新
          </Button>
        </div>
        
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
      </Card>

      {/* 编辑用户模态框 */}
      <Modal
        title="编辑用户"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={600}
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
            <Select mode="multiple" placeholder="请选择角色">
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