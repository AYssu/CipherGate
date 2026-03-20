import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  message,
  Popconfirm,
  Tag,
  Row,
  Col,
  Typography
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined
} from '@ant-design/icons';

const { Title } = Typography;
const { Option } = Select;

// 本地类型定义
interface Permission {
  id: number;
  permissionName: string;
  permissionCode: string;
  resourceType: string;
  resourcePath?: string;
  httpMethod?: string;
  description?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

const PermissionManagementContent: React.FC = () => {
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingPermission, setEditingPermission] = useState<Permission | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [resourceTypes, setResourceTypes] = useState<string[]>([]);
  const [httpMethods, setHttpMethods] = useState<string[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchPermissions();
    fetchResourceTypes();
    fetchHttpMethods();
  }, []);

  const fetchPermissions = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/permissions', {
        credentials: 'include'
      });
      const result = await response.json();
      if (result.success) {
        setPermissions(result.data);
      } else {
        message.error(result.message || '获取权限列表失败');
      }
    } catch (error) {
      console.error('获取权限列表失败:', error);
      message.error('网络错误，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const fetchResourceTypes = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/permissions/resource-types', {
        credentials: 'include'
      });
      const result = await response.json();
      if (result.success) {
        setResourceTypes(result.data);
      }
    } catch (error) {
      console.error('获取资源类型失败:', error);
    }
  };

  const fetchHttpMethods = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/permissions/http-methods', {
        credentials: 'include'
      });
      const result = await response.json();
      if (result.success) {
        setHttpMethods(result.data);
      }
    } catch (error) {
      console.error('获取HTTP方法失败:', error);
    }
  };

  const handleAdd = () => {
    setEditingPermission(null);
    setModalVisible(true);
    form.resetFields();
    form.setFieldsValue({
      resourceType: 'API',
      status: true
    });
  };

  const handleEdit = (record: Permission) => {
    setEditingPermission(record);
    setModalVisible(true);
    form.setFieldsValue({
      ...record,
      status: record.status === 1
    });
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await fetch(`http://localhost:8080/api/permissions/${id}`, {
        method: 'DELETE',
        credentials: 'include'
      });
      const result = await response.json();
      if (result.success) {
        message.success('删除成功');
        fetchPermissions();
      } else {
        message.error(result.message || '删除失败');
      }
    } catch (error) {
      console.error('删除权限失败:', error);
      message.error('网络错误，请稍后重试');
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要删除的权限');
      return;
    }

    try {
      const response = await fetch('http://localhost:8080/api/permissions/batch', {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(selectedRowKeys)
      });
      const result = await response.json();
      if (result.success) {
        message.success('批量删除成功');
        setSelectedRowKeys([]);
        fetchPermissions();
      } else {
        message.error(result.message || '批量删除失败');
      }
    } catch (error) {
      console.error('批量删除权限失败:', error);
      message.error('网络错误，请稍后重试');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const permissionData = {
        ...values,
        status: values.status ? 1 : 0
      };

      const url = editingPermission 
        ? `http://localhost:8080/api/permissions/${editingPermission.id}`
        : 'http://localhost:8080/api/permissions';
      
      const method = editingPermission ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(permissionData)
      });

      const result = await response.json();
      if (result.success) {
        message.success(editingPermission ? '更新成功' : '创建成功');
        setModalVisible(false);
        fetchPermissions();
      } else {
        message.error(result.message || (editingPermission ? '更新失败' : '创建失败'));
      }
    } catch (error) {
      console.error('提交权限失败:', error);
      message.error('网络错误，请稍后重试');
    }
  };

  const getResourceTypeTag = (type: string) => {
    const colors = {
      'API': 'blue',
      'MENU': 'green',
      'BUTTON': 'orange',
      'DATA': 'purple'
    };
    return <Tag color={colors[type as keyof typeof colors] || 'default'}>{type}</Tag>;
  };

  const getStatusTag = (status: number) => {
    return status === 1 
      ? <Tag color="success">启用</Tag>
      : <Tag color="error">禁用</Tag>;
  };

  const columns = [
    {
      title: '权限名称',
      dataIndex: 'permissionName',
      key: 'permissionName',
      width: 150,
    },
    {
      title: '权限编码',
      dataIndex: 'permissionCode',
      key: 'permissionCode',
      width: 150,
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
      width: 100,
      render: (type: string) => getResourceTypeTag(type),
    },
    {
      title: '资源路径',
      dataIndex: 'resourcePath',
      key: 'resourcePath',
      width: 200,
      render: (path: string) => path || '-',
    },
    {
      title: 'HTTP方法',
      dataIndex: 'httpMethod',
      key: 'httpMethod',
      width: 100,
      render: (method: string) => method ? <Tag>{method}</Tag> : '-',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (desc: string) => desc || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => getStatusTag(status),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: Permission) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个权限吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const rowSelection = {
    selectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setSelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <Card>
      <div style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4}>权限管理</Title>
          </Col>
          <Col>
            <Space>
              {selectedRowKeys.length > 0 && (
                <Popconfirm
                  title={`确定要删除选中的 ${selectedRowKeys.length} 个权限吗？`}
                  onConfirm={handleBatchDelete}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button danger>
                    批量删除 ({selectedRowKeys.length})
                  </Button>
                </Popconfirm>
              )}
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchPermissions}
                loading={loading}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleAdd}
              >
                新增权限
              </Button>
            </Space>
          </Col>
        </Row>
      </div>

      <Table
        columns={columns}
        dataSource={permissions}
        rowKey="id"
        loading={loading}
        rowSelection={rowSelection}
        pagination={{
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条记录`,
        }}
        scroll={{ x: 1200 }}
      />

      <Modal
        title={editingPermission ? '编辑权限' : '新增权限'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            resourceType: 'API',
            status: true
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="权限名称"
                name="permissionName"
                rules={[{ required: true, message: '请输入权限名称' }]}
              >
                <Input placeholder="请输入权限名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="权限编码"
                name="permissionCode"
                rules={[{ required: true, message: '请输入权限编码' }]}
              >
                <Input placeholder="请输入权限编码" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="资源类型"
                name="resourceType"
                rules={[{ required: true, message: '请选择资源类型' }]}
              >
                <Select placeholder="请选择资源类型">
                  {resourceTypes.map(type => (
                    <Option key={type} value={type}>
                      {type}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="HTTP方法"
                name="httpMethod"
              >
                <Select placeholder="请选择HTTP方法" allowClear>
                  {httpMethods.map(method => (
                    <Option key={method} value={method}>
                      {method}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="资源路径"
            name="resourcePath"
          >
            <Input placeholder="请输入资源路径，如：/api/users" />
          </Form.Item>

          <Form.Item
            label="权限描述"
            name="description"
          >
            <Input.TextArea
              placeholder="请输入权限描述"
              rows={3}
            />
          </Form.Item>

          <Form.Item
            label="权限状态"
            name="status"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default PermissionManagementContent;