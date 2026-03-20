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
  message,
  Popconfirm
} from 'antd';
import { 
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  description: string;
  permissions?: Permission[];
}

interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  description: string;
}

const RoleManagementContent: React.FC = () => {
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [form] = Form.useForm();

  // 获取角色列表
  const fetchRoles = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/roles', {
        credentials: 'include'
      });
      if (response.ok) {
        const result = await response.json();
        if (result.success) {
          setRoles(result.data || []);
        } else {
          message.error(result.message || '获取角色列表失败');
        }
      } else {
        message.error('获取角色列表失败');
      }
    } catch (error) {
      console.error('获取角色列表失败:', error);
      message.error('网络错误，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
  }, []);

  // 角色表格列定义
  const roleColumns = [
    {
      title: '角色名称',
      dataIndex: 'roleName',
      key: 'roleName',
    },
    {
      title: '角色编码',
      dataIndex: 'roleCode',
      key: 'roleCode',
      render: (code: string) => (
        <Tag color={code === 'SUPER_ADMIN' ? 'red' : code === 'ADMIN' ? 'blue' : 'green'}>
          {code}
        </Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (desc: string) => desc || '-',
    },
    {
      title: '权限数量',
      key: 'permissionCount',
      render: (record: Role) => (
        <Text>{record.permissions?.length || 0} 个权限</Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (record: Role) => (
        <Space>
          <Button 
            type="link" 
            icon={<EditOutlined />}
            onClick={() => handleEditRole(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除角色 ${record.roleName} 吗？`}
            onConfirm={() => handleDeleteRole(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button 
              type="link" 
              danger
              icon={<DeleteOutlined />}
              disabled={record.roleCode === 'SUPER_ADMIN'}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleEditRole = (role: Role) => {
    setEditingRole(role);
    form.setFieldsValue({
      roleName: role.roleName,
      roleCode: role.roleCode,
      description: role.description,
    });
    setModalVisible(true);
  };

  const handleDeleteRole = async (role: Role) => {
    try {
      const response = await fetch(`http://localhost:8080/api/roles/${role.id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      
      const result = await response.json();
      if (result.success) {
        message.success('角色删除成功');
        fetchRoles();
      } else {
        message.error(result.message || '角色删除失败');
      }
    } catch (error) {
      console.error('删除角色失败:', error);
      message.error('删除角色失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      const url = editingRole 
        ? `http://localhost:8080/api/roles/${editingRole.id}`
        : 'http://localhost:8080/api/roles';
      const method = editingRole ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify(values),
      });

      const result = await response.json();
      if (result.success) {
        message.success(editingRole ? '角色更新成功' : '角色创建成功');
        setModalVisible(false);
        setEditingRole(null);
        form.resetFields();
        fetchRoles();
      } else {
        message.error(result.message || (editingRole ? '角色更新失败' : '角色创建失败'));
      }
    } catch (error) {
      console.error('操作角色失败:', error);
      message.error('操作角色失败');
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingRole(null);
    form.resetFields();
  };

  const handleCreateRole = () => {
    setEditingRole(null);
    form.resetFields();
    setModalVisible(true);
  };

  return (
    <>
      <Card>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
          <Title level={4}>角色列表</Title>
          <Space>
            <Button 
              type="primary" 
              icon={<PlusOutlined />}
              onClick={handleCreateRole}
            >
              新建角色
            </Button>
            <Button 
              icon={<ReloadOutlined />}
              onClick={fetchRoles}
              loading={loading}
            >
              刷新
            </Button>
          </Space>
        </div>
        
        <Table
          columns={roleColumns}
          dataSource={roles}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          expandable={{
            expandedRowRender: (record: Role) => (
              <div style={{ padding: 16, background: '#fafafa' }}>
                <Title level={5}>权限列表</Title>
                <Space wrap>
                  {record.permissions?.map(permission => (
                    <Tag key={permission.id} color="blue">
                      {permission.permissionName}
                    </Tag>
                  )) || <Text type="secondary">暂无权限</Text>}
                </Space>
              </div>
            ),
            rowExpandable: (record: Role) => (record.permissions?.length || 0) > 0,
          }}
        />
      </Card>

      {/* 编辑/创建角色模态框 */}
      <Modal
        title={editingRole ? '编辑角色' : '创建角色'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="角色名称"
            name="roleName"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          
          <Form.Item
            label="角色编码"
            name="roleCode"
            rules={[{ required: true, message: '请输入角色编码' }]}
          >
            <Input 
              placeholder="请输入角色编码" 
              disabled={editingRole?.roleCode === 'SUPER_ADMIN'}
            />
          </Form.Item>
          
          <Form.Item
            label="角色描述"
            name="description"
          >
            <Input.TextArea 
              placeholder="请输入角色描述" 
              rows={3}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default RoleManagementContent;