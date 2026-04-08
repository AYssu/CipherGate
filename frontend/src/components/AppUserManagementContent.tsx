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
  Input,
  Select,
  message,
  Row,
  Col,
  Dropdown,
  type MenuProps,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  MoreOutlined,
  LockOutlined,
  StopOutlined,
  CheckCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  getAppUserList,
  createAppUser,
  updateAppUser,
  deleteAppUser,
  resetPassword,
  banUser,
  type AppUser,
  type AppUserDTO,
} from '../services/appUserService';
import { getApplicationList, type Application } from '../services/applicationService';

const { Title, Text } = Typography;
const { Option } = Select;

const AppUserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<AppUser | null>(null);
  const [resetUserId, setResetUserId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [filters, setFilters] = useState<any>({});

  // 获取应用列表
  const fetchApplications = async () => {
    try {
      const result: any = await getApplicationList({ current: 1, size: 1000 });
      if (result.code === 200 && result.data) {
        setApplications(result.data.records || []);
      }
    } catch (error) {
      console.error('获取应用列表失败:', error);
    }
  };

  // 获取用户列表
  const fetchUsers = async (page = 1, size = 10, filterParams = {}) => {
    setLoading(true);
    try {
      const result: any = await getAppUserList({
        current: page,
        size,
        ...filterParams,
      });

      if (result.code === 200 && result.data) {
        setUsers(result.data.records || []);
        setPagination({
          current: result.data.current || page,
          pageSize: result.data.size || size,
          total: result.data.total || 0,
        });
      }
    } catch (error) {
      message.error('获取用户列表失败');
      console.error('获取用户列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplications();
    fetchUsers();
  }, []);

  // 打开创建/编辑弹窗
  const handleOpenModal = (user?: AppUser) => {
    setEditingUser(user || null);
    setModalVisible(true);

    if (user) {
      form.setFieldsValue({
        appId: user.appId,
        username: user.username,
        email: user.email,
        phone: user.phone,
        nickname: user.nickname,
        signature: user.signature,
      });
    } else {
      form.resetFields();
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (editingUser) {
        // 更新
        const result: any = await updateAppUser(editingUser.id, values);
        if (result.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          fetchUsers(pagination.current, pagination.pageSize, filters);
        } else {
          message.error(result.message || '更新失败');
        }
      } else {
        // 创建
        const result: any = await createAppUser(values);
        if (result.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          fetchUsers(1, pagination.pageSize, filters);
        } else {
          message.error(result.message || '创建失败');
        }
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 删除用户
  const handleDelete = (id: number, username: string) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除用户 "${username}" 吗？此操作不可恢复。`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        try {
          const result: any = await deleteAppUser(id);
          if (result.code === 200) {
            message.success('删除成功');
            fetchUsers(pagination.current, pagination.pageSize, filters);
          } else {
            message.error(result.message || '删除失败');
          }
        } catch (error) {
          message.error('删除失败');
          console.error('删除失败:', error);
        }
      },
    });
  };

  // 打开重置密码弹窗
  const handleOpenPasswordModal = (id: number) => {
    setResetUserId(id);
    setPasswordModalVisible(true);
    passwordForm.resetFields();
  };

  // 重置密码
  const handleResetPassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      if (!resetUserId) return;

      const result: any = await resetPassword(resetUserId, values.newPassword);
      if (result.code === 200) {
        message.success('密码重置成功');
        setPasswordModalVisible(false);
        setResetUserId(null);
      } else {
        message.error(result.message || '重置失败');
      }
    } catch (error) {
      console.error('重置密码失败:', error);
    }
  };

  // 封禁/解封用户
  const handleBanUser = (id: number, username: string, currentBanned: boolean) => {
    Modal.confirm({
      title: currentBanned ? '确认解封' : '确认封禁',
      content: currentBanned 
        ? `确定要解封用户 "${username}" 吗？` 
        : `确定要封禁用户 "${username}" 吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: currentBanned ? 'primary' : 'danger',
      onOk: async () => {
        try {
          const result: any = await banUser(id, !currentBanned, currentBanned ? undefined : '管理员封禁');
          if (result.code === 200) {
            message.success(currentBanned ? '解封成功' : '封禁成功');
            fetchUsers(pagination.current, pagination.pageSize, filters);
          } else {
            message.error(result.message || '操作失败');
          }
        } catch (error) {
          message.error('操作失败');
          console.error('封禁/解封失败:', error);
        }
      },
    });
  };

  // 操作菜单
  const getActionMenu = (record: AppUser): MenuProps => ({
    items: [
      {
        key: 'edit',
        icon: <EditOutlined />,
        label: '编辑',
        onClick: () => handleOpenModal(record),
      },
      {
        key: 'reset-password',
        icon: <LockOutlined />,
        label: '重置密码',
        onClick: () => handleOpenPasswordModal(record.id),
      },
      {
        key: 'ban',
        icon: <StopOutlined />,
        label: '封禁',
        onClick: () => handleBanUser(record.id, record.username, false),
      },
      {
        type: 'divider',
      },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        onClick: () => handleDelete(record.id, record.username),
      },
    ],
  });

  // 表格列定义
  const columns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 150,
      render: (text: string) => (
        <Space>
          <UserOutlined style={{ color: '#1890ff' }} />
          <Text strong>{text}</Text>
        </Space>
      ),
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
      render: (text: string) => text || '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 200,
      render: (text: string) => text || '-',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
      render: (text: string) => text || '-',
    },
    {
      title: '所属应用',
      dataIndex: 'appName',
      key: 'appName',
      width: 150,
      render: (text: string) => <Tag color="blue">{text || '-'}</Tag>,
    },
    {
      title: '绑定设备',
      dataIndex: 'bindingCount',
      key: 'bindingCount',
      width: 100,
      align: 'center' as const,
      render: (count: number) => (
        <Tag color={count > 0 ? 'green' : 'default'}>{count || 0}</Tag>
      ),
    },
    {
      title: '登录次数',
      dataIndex: 'loginCount',
      key: 'loginCount',
      width: 100,
      align: 'center' as const,
      render: (count: number) => <Text>{count || 0}</Text>,
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      width: 160,
      render: (text: string) => (
        text ? (
          <Text style={{ fontSize: 12 }}>
            {new Date(text).toLocaleString('zh-CN', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit'
            })}
          </Text>
        ) : '-'
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (text: string) => (
        <Text style={{ fontSize: 12 }}>
          {new Date(text).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          })}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right' as const,
      render: (_: any, record: AppUser) => (
        <Dropdown menu={getActionMenu(record)} trigger={['click']}>
          <Button type="text" icon={<MoreOutlined />} />
        </Dropdown>
      ),
    },
  ];

  return (
    <Card>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 标题和操作栏 */}
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>终端用户管理</Title>
          </Col>
          <Col>
            <Space>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => fetchUsers(pagination.current, pagination.pageSize, filters)}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => handleOpenModal()}
              >
                创建用户
              </Button>
            </Space>
          </Col>
        </Row>

        {/* 筛选栏 */}
        <Row gutter={16}>
          <Col span={6}>
            <Select
              placeholder="选择应用"
              allowClear
              style={{ width: '100%' }}
              onChange={(value) => {
                const newFilters = { ...filters, appId: value };
                setFilters(newFilters);
                fetchUsers(1, pagination.pageSize, newFilters);
              }}
            >
              {applications.map(app => (
                <Option key={app.id} value={app.id}>{app.appName}</Option>
              ))}
            </Select>
          </Col>
          <Col span={6}>
            <Input
              placeholder="搜索用户名"
              allowClear
              onPressEnter={(e: any) => {
                const newFilters = { ...filters, username: e.target.value };
                setFilters(newFilters);
                fetchUsers(1, pagination.pageSize, newFilters);
              }}
            />
          </Col>
          <Col span={6}>
            <Input
              placeholder="搜索邮箱"
              allowClear
              onPressEnter={(e: any) => {
                const newFilters = { ...filters, email: e.target.value };
                setFilters(newFilters);
                fetchUsers(1, pagination.pageSize, newFilters);
              }}
            />
          </Col>
          <Col span={6}>
            <Input
              placeholder="搜索手机号"
              allowClear
              onPressEnter={(e: any) => {
                const newFilters = { ...filters, phone: e.target.value };
                setFilters(newFilters);
                fetchUsers(1, pagination.pageSize, newFilters);
              }}
            />
          </Col>
        </Row>

        {/* 用户列表表格 */}
        <Table
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              fetchUsers(page, pageSize, filters);
            },
          }}
          scroll={{ x: 1400 }}
        />
      </Space>


      {/* 创建/编辑弹窗 */}
      <Modal
        title={editingUser ? '编辑用户' : '创建用户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 20 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="所属应用"
                name="appId"
                rules={[{ required: true, message: '请选择应用' }]}
              >
                <Select placeholder="选择应用" disabled={!!editingUser}>
                  {applications.map(app => (
                    <Option key={app.id} value={app.id}>{app.appName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="用户名"
                name="username"
                rules={[
                  { required: true, message: '请输入用户名' },
                  { min: 3, max: 20, message: '用户名长度为3-20位' },
                  { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线' }
                ]}
              >
                <Input placeholder="输入用户名" disabled={!!editingUser} />
              </Form.Item>
            </Col>
          </Row>

          {!editingUser && (
            <Form.Item
              label="密码"
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6位' }
              ]}
            >
              <Input.Password placeholder="输入密码" />
            </Form.Item>
          )}

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="邮箱"
                name="email"
                rules={[
                  { type: 'email', message: '请输入有效的邮箱地址' }
                ]}
              >
                <Input placeholder="输入邮箱" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="手机号"
                name="phone"
                rules={[
                  { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' }
                ]}
              >
                <Input placeholder="输入手机号" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="昵称"
            name="nickname"
          >
            <Input placeholder="输入昵称" />
          </Form.Item>

          <Form.Item
            label="个性签名"
            name="signature"
          >
            <Input.TextArea rows={3} placeholder="输入个性签名" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码弹窗 */}
      <Modal
        title="重置密码"
        open={passwordModalVisible}
        onOk={handleResetPassword}
        onCancel={() => {
          setPasswordModalVisible(false);
          setResetUserId(null);
        }}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={passwordForm}
          layout="vertical"
          style={{ marginTop: 20 }}
        >
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码至少6位' }
            ]}
          >
            <Input.Password placeholder="输入新密码" />
          </Form.Item>

          <Form.Item
            label="确认密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AppUserManagementContent;
