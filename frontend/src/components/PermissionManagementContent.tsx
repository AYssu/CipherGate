import React, { useState, useEffect, useMemo } from 'react';
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
  Typography,
  Grid,
  Dropdown,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { permissionApi } from '../services/permissionService';
import type { Permission } from '../services/permissionService';

const { Title } = Typography;
const { Option } = Select;

const PermissionManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
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
      const result = await permissionApi.getPermissions();
      setPermissions((result as any).data || []);
    } catch (error) {
      console.error('获取权限列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchResourceTypes = async () => {
    try {
      const result = await permissionApi.getResourceTypes();
      setResourceTypes((result as any).data || []);
    } catch (error) {
      console.error('获取资源类型失败:', error);
    }
  };

  const fetchHttpMethods = async () => {
    try {
      const result = await permissionApi.getHttpMethods();
      setHttpMethods((result as any).data || []);
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
      await permissionApi.deletePermission(id);
      message.success('删除成功');
      fetchPermissions();
    } catch (error) {
      console.error('删除权限失败:', error);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要删除的权限');
      return;
    }

    try {
      await permissionApi.batchDeletePermissions(selectedRowKeys as number[]);
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      fetchPermissions();
    } catch (error) {
      console.error('批量删除权限失败:', error);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const permissionData = {
        ...values,
        status: values.status ? 1 : 0
      };

      if (editingPermission) {
        await permissionApi.updatePermission(editingPermission.id, permissionData);
        message.success('更新成功');
      } else {
        await permissionApi.createPermission(permissionData);
        message.success('创建成功');
      }
      
      setModalVisible(false);
      fetchPermissions();
    } catch (error) {
      console.error('提交权限失败:', error);
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

  const allColumns = useMemo(() => [
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
      width: isMobile ? 80 : 150,
      render: (_: any, record: Permission) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEdit(record) },
                  { type: 'divider' },
                  { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDelete(record.id) },
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
        );
      },
    },
  ], [isMobile]);

  const MOBILE_VISIBLE_KEYS = ['permissionName', 'resourceType', 'status', 'action'];
  const columns = isMobile
    ? allColumns.filter((c) => MOBILE_VISIBLE_KEYS.includes(c.key as string) || MOBILE_VISIBLE_KEYS.includes(c.dataIndex as string))
    : allColumns;

  const rowSelection = {
    selectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setSelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <Card>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
      }}>
        <Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>权限管理</Title>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
          {selectedRowKeys.length > 0 && (
            <Popconfirm
              title={`确定要删除选中的 ${selectedRowKeys.length} 个权限吗？`}
              onConfirm={handleBatchDelete}
              okText="确定"
              cancelText="取消"
            >
              <Button
                danger
                size={isMobile ? 'small' : 'middle'}
                style={isMobile ? { fontSize: 12, padding: '0 6px', height: 24 } : undefined}
              >
                批量删除 ({selectedRowKeys.length})
              </Button>
            </Popconfirm>
          )}
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchPermissions}
            loading={loading}
            size={isMobile ? 'small' : 'middle'}
            style={isMobile ? { fontSize: 12, padding: '0 6px', height: 24 } : undefined}
          >
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
            size={isMobile ? 'small' : 'middle'}
            style={isMobile ? { fontSize: 12, padding: '0 6px', height: 24 } : undefined}
          >
            新增权限
          </Button>
        </div>
      </div>

      <Table
        columns={columns}
        dataSource={permissions}
        rowKey="id"
        loading={loading}
        rowSelection={isMobile ? undefined : rowSelection}
        pagination={{
          simple: isMobile,
          showSizeChanger: !isMobile,
          showQuickJumper: !isMobile,
          showTotal: isMobile ? undefined : (total) => `共 ${total} 条记录`,
        }}
        scroll={{ x: isMobile ? 300 : 1200 }}
        size={isMobile ? 'small' : 'middle'}
      />

      <Modal
        title={editingPermission ? '编辑权限' : '新增权限'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={isMobile ? '100%' : 600}
        className={isMobile ? 'mobile-modal' : undefined}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            resourceType: 'API',
            status: true
          }}
        >
          <Row gutter={isMobile ? 0 : 16}>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item
                label="权限名称"
                name="permissionName"
                rules={[{ required: true, message: '请输入权限名称' }]}
              >
                <Input placeholder="请输入权限名称" />
              </Form.Item>
            </Col>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item
                label="权限编码"
                name="permissionCode"
                rules={[{ required: true, message: '请输入权限编码' }]}
              >
                <Input placeholder="请输入权限编码" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? 0 : 16}>
            <Col span={isMobile ? 24 : 12}>
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
            <Col span={isMobile ? 24 : 12}>
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