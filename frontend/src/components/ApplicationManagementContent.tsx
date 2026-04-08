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
  Popconfirm,
  Tooltip,
  Row,
  Col,
  InputNumber,
  Divider,
  Badge,
  Dropdown,
  Upload,
  type MenuProps,
  type UploadProps,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  KeyOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  CopyOutlined,
  AppstoreOutlined,
  MoreOutlined,
  PoweroffOutlined,
  CheckCircleOutlined,
  ApiOutlined,
  RocketOutlined,
  CloudOutlined,
  DatabaseOutlined,
  SafetyOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  getApplicationList,
  createApplication,
  updateApplication,
  deleteApplication,
  generateAppKeys,
  resetAppKeys,
  updateApplicationStatus,
  type Application,
  type ApplicationDTO,
} from '../services/applicationService';

const { Title, Text } = Typography;
const { TextArea } = Input;

const ApplicationManagementContent: React.FC = () => {
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingApp, setEditingApp] = useState<Application | null>(null);
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [showSecret, setShowSecret] = useState<Record<number, boolean>>({});

  // 获取应用列表
  const fetchApplications = async (page = 1, size = 10) => {
    setLoading(true);
    try {
      const result: any = await getApplicationList({
        current: page,
        size: size,
      });
      
      if (result.code === 200 && result.data) {
        setApplications(result.data.records || []);
        setPagination({
          current: result.data.current || page,
          pageSize: result.data.size || size,
          total: result.data.total || 0,
        });
      }
    } catch (error) {
      console.error('获取应用列表失败:', error);
      message.error('获取应用列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplications();
  }, []);

  // 打开创建/编辑弹窗
  const handleOpenModal = (app?: Application) => {
    setEditingApp(app || null);
    if (app) {
      form.setFieldsValue({
        appName: app.appName,
        description: app.description,
        notice: app.notice,
        updateNotice: app.updateNotice,
        category: app.category,
        tags: app.tags,
        businessModel: app.businessModel,
        status: app.status,
        trafficLimit: app.trafficLimit,
        currentVersion: app.currentVersion,
        minVersion: app.minVersion,
      });
    } else {
      form.resetFields();
    }
    setModalVisible(true);
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const dto: ApplicationDTO = {
        ...values,
      };

      if (editingApp) {
        const result: any = await updateApplication(editingApp.id, dto);
        if (result.code === 200) {
          message.success('应用更新成功');
          setModalVisible(false);
          fetchApplications(pagination.current, pagination.pageSize);
        } else {
          message.error(result.message || '应用更新失败');
        }
      } else {
        const result: any = await createApplication(dto);
        if (result.code === 200) {
          message.success('应用创建成功');
          setModalVisible(false);
          fetchApplications(1, pagination.pageSize);
        } else {
          message.error(result.message || '应用创建失败');
        }
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 删除应用
  const handleDelete = async (id: number) => {
    try {
      const result: any = await deleteApplication(id);
      if (result.code === 200) {
        message.success('应用删除成功');
        fetchApplications(pagination.current, pagination.pageSize);
      } else {
        message.error(result.message || '应用删除失败');
      }
    } catch (error) {
      console.error('删除失败:', error);
      message.error('应用删除失败');
    }
  };

  // 重置密钥
  const handleResetKeys = async (id: number) => {
    try {
      const result: any = await resetAppKeys(id);
      if (result.code === 200) {
        message.success('密钥重置成功');
        fetchApplications(pagination.current, pagination.pageSize);
      } else {
        message.error(result.message || '密钥重置失败');
      }
    } catch (error) {
      console.error('重置密钥失败:', error);
      message.error('密钥重置失败');
    }
  };

  // 更新状态
  const handleUpdateStatus = async (id: number, status: number) => {
    try {
      const result: any = await updateApplicationStatus(id, status);
      if (result.code === 200) {
        message.success('状态更新成功');
        fetchApplications(pagination.current, pagination.pageSize);
      } else {
        message.error(result.message || '状态更新失败');
      }
    } catch (error) {
      console.error('更新状态失败:', error);
      message.error('状态更新失败');
    }
  };

  // 复制到剪贴板
  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => {
      message.success(`${label}已复制到剪贴板`);
    });
  };

  // 切换密钥显示
  const toggleShowSecret = (id: number) => {
    setShowSecret(prev => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  // 业务模式标签
  const getBusinessModelTag = (model: number) => {
    const map: Record<number, { text: string; color: string }> = {
      1: { text: '付费', color: 'blue' },
      2: { text: '免费', color: 'green' },
      3: { text: '试用+付费', color: 'orange' },
    };
    const item = map[model] || { text: '未知', color: 'default' };
    return <Tag color={item.color}>{item.text}</Tag>;
  };

  // 状态标签
  const getStatusBadge = (status: number) => {
    const map: Record<number, { text: string; status: 'success' | 'warning' | 'error' | 'default' }> = {
      1: { text: '正常', status: 'success' },
      2: { text: '维护', status: 'warning' },
      3: { text: '停用', status: 'error' },
    };
    const item = map[status] || { text: '未知', status: 'default' };
    return <Badge status={item.status} text={item.text} />;
  };

  // 表格列定义
  const columns = [
    {
      title: '应用名称',
      dataIndex: 'appName',
      key: 'appName',
      width: 200,
      render: (text: string, record: Application) => {
        // 根据分类选择不同的图标
        const getIcon = () => {
          const category = record.category?.toLowerCase();
          if (category?.includes('工具')) return <ApiOutlined style={{ fontSize: 20, color: '#1890ff' }} />;
          if (category?.includes('游戏')) return <RocketOutlined style={{ fontSize: 20, color: '#52c41a' }} />;
          if (category?.includes('办公')) return <DatabaseOutlined style={{ fontSize: 20, color: '#722ed1' }} />;
          if (category?.includes('安全')) return <SafetyOutlined style={{ fontSize: 20, color: '#fa8c16' }} />;
          return <CloudOutlined style={{ fontSize: 20, color: '#13c2c2' }} />;
        };

        return (
          <Space>
            {getIcon()}
            <div>
              <div><Text strong>{text}</Text></div>
              <div><Text type="secondary" style={{ fontSize: 12 }}>ID: {record.id}</Text></div>
            </div>
          </Space>
        );
      },
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (text: string) => <Tag>{text || '未分类'}</Tag>,
    },
    {
      title: 'AppKey',
      dataIndex: 'appKey',
      key: 'appKey',
      width: 180,
      render: (text: string) => (
        <Text 
          copyable={{ text, tooltips: ['复制', '已复制'] }} 
          style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, color: '#666' }}
        >
          {text.substring(0, 12)}...
        </Text>
      ),
    },
    {
      title: 'AppSecret',
      dataIndex: 'appSecret',
      key: 'appSecret',
      width: 180,
      render: (text: string, record: Application) => (
        <Space size="small">
          <Text style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, color: '#666' }}>
            {showSecret[record.id] ? text?.substring(0, 12) + '...' : '••••••••••••'}
          </Text>
          <Button
            type="text"
            size="small"
            icon={showSecret[record.id] ? <EyeInvisibleOutlined /> : <EyeOutlined />}
            onClick={() => toggleShowSecret(record.id)}
          />
          {showSecret[record.id] && (
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => handleCopy(text, 'AppSecret')}
            />
          )}
        </Space>
      ),
    },
    {
      title: '业务模式',
      dataIndex: 'businessModel',
      key: 'businessModel',
      width: 100,
      align: 'center' as const,
      render: (model: number) => getBusinessModelTag(model),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      align: 'center' as const,
      render: (status: number) => getStatusBadge(status),
    },
    {
      title: '流量使用',
      key: 'traffic',
      width: 120,
      align: 'right' as const,
      render: (_: any, record: Application) => {
        const used = record.trafficUsed || 0;
        const limit = record.trafficLimit || 0;
        const percent = limit > 0 ? ((used / limit) * 100).toFixed(1) : 0;
        return (
          <div style={{ textAlign: 'right' }}>
            <div><Text style={{ fontSize: 12 }}>{formatBytes(used)}</Text></div>
            <div><Text type="secondary" style={{ fontSize: 11 }}>/ {formatBytes(limit)}</Text></div>
          </div>
        );
      },
    },
    {
      title: '创建者',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 100,
      render: (text: string) => <Text>{text || '-'}</Text>,
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
      width: 150,
      fixed: 'right' as const,
      render: (_: any, record: Application) => {
        const menuItems: MenuProps['items'] = [
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: '编辑',
            onClick: () => handleOpenModal(record),
          },
          {
            key: 'resetKeys',
            icon: <KeyOutlined />,
            label: '重置密钥',
            onClick: () => {
              Modal.confirm({
                title: '重置密钥',
                content: '确定要重置密钥吗？重置后旧密钥将立即失效。',
                okText: '确定',
                cancelText: '取消',
                onOk: () => handleResetKeys(record.id),
              });
            },
          },
          {
            key: 'status',
            icon: record.status === 1 ? <PoweroffOutlined /> : <CheckCircleOutlined />,
            label: record.status === 1 ? '停用' : '启用',
            onClick: () => {
              const newStatus = record.status === 1 ? 3 : 1;
              const action = record.status === 1 ? '停用' : '启用';
              Modal.confirm({
                title: `${action}应用`,
                content: `确定要${action}应用"${record.appName}"吗？`,
                okText: '确定',
                cancelText: '取消',
                onOk: () => handleUpdateStatus(record.id, newStatus),
              });
            },
          },
          {
            type: 'divider',
          },
          {
            key: 'delete',
            icon: <DeleteOutlined />,
            label: '删除',
            danger: true,
            onClick: () => {
              Modal.confirm({
                title: '删除应用',
                content: `确定要删除应用"${record.appName}"吗？删除后无法恢复。`,
                okText: '确定',
                okType: 'danger',
                cancelText: '取消',
                onOk: () => handleDelete(record.id),
              });
            },
          },
        ];

        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenModal(record)}
            >
              编辑
            </Button>
            <Dropdown menu={{ items: menuItems }} trigger={['click']}>
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  // 格式化字节
  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  return (
    <Card>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 标题和操作栏 */}
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>应用管理</Title>
          </Col>
          <Col>
            <Space>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => fetchApplications(pagination.current, pagination.pageSize)}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => handleOpenModal()}
              >
                创建应用
              </Button>
            </Space>
          </Col>
        </Row>

        {/* 应用列表表格 */}
        <Table
          columns={columns}
          dataSource={applications}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              fetchApplications(page, pageSize);
            },
          }}
          scroll={{ x: 1500 }}
        />
      </Space>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editingApp ? '编辑应用' : '创建应用'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={700}
        okText="确定"
        cancelText="取消"
        className="app-edit-modal"
      >
        <style>{`
          /* Modal 滚动条样式 */
          .app-edit-modal .ant-modal-body::-webkit-scrollbar {
            width: 6px;
          }
          .app-edit-modal .ant-modal-body::-webkit-scrollbar-track {
            background: transparent;
          }
          .app-edit-modal .ant-modal-body::-webkit-scrollbar-thumb {
            background-color: #d9d9d9;
            border-radius: 3px;
          }
          .app-edit-modal .ant-modal-body::-webkit-scrollbar-thumb:hover {
            background-color: #bfbfbf;
          }
          
          /* TextArea 滚动条样式 */
          .app-edit-modal textarea::-webkit-scrollbar {
            width: 6px;
          }
          .app-edit-modal textarea::-webkit-scrollbar-track {
            background: transparent;
          }
          .app-edit-modal textarea::-webkit-scrollbar-thumb {
            background-color: #d9d9d9;
            border-radius: 3px;
          }
          .app-edit-modal textarea::-webkit-scrollbar-thumb:hover {
            background-color: #bfbfbf;
          }
        `}</style>
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            businessModel: 1,
            status: 1,
            trafficLimit: 0,
          }}
        >
          <Form.Item
            label="应用名称"
            name="appName"
            rules={[
              { required: true, message: '请输入应用名称' },
              { max: 100, message: '应用名称不能超过100个字符' },
            ]}
          >
            <Input placeholder="请输入应用名称" />
          </Form.Item>

          <Form.Item
            label="应用描述"
            name="description"
            rules={[{ max: 500, message: '描述不能超过500个字符' }]}
          >
            <TextArea 
              placeholder="请输入应用描述"
              autoSize={{ minRows: 3, maxRows: 10 }}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="应用分类"
                name="category"
                rules={[{ max: 50, message: '分类不能超过50个字符' }]}
              >
                <Input placeholder="如：游戏、工具等" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="标签"
                name="tags"
                rules={[{ max: 255, message: '标签不能超过255个字符' }]}
              >
                <Input placeholder="多个标签用逗号分隔" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="业务模式"
                name="businessModel"
                rules={[{ required: true, message: '请选择业务模式' }]}
              >
                <Select>
                  <Select.Option value={1}>付费</Select.Option>
                  <Select.Option value={2}>免费</Select.Option>
                  <Select.Option value={3}>试用+付费</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="状态" name="status">
                <Select>
                  <Select.Option value={1}>正常</Select.Option>
                  <Select.Option value={2}>维护</Select.Option>
                  <Select.Option value={3}>停用</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="流量限制 (字节)"
            name="trafficLimit"
            tooltip="0 表示不限制"
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              placeholder="0 表示不限制"
            />
          </Form.Item>

          <Divider>版本信息（可选）</Divider>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="当前版本" name="currentVersion">
                <Input placeholder="如：1.0.0" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="最低支持版本" name="minVersion">
                <Input placeholder="如：1.0.0" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="应用公告" name="notice">
            <TextArea 
              placeholder="请输入应用公告"
              autoSize={{ minRows: 4, maxRows: 15 }}
            />
          </Form.Item>

          <Form.Item label="更新公告" name="updateNotice">
            <TextArea 
              placeholder="请输入更新公告，描述最新版本的更新内容"
              autoSize={{ minRows: 4, maxRows: 15 }}
            />
          </Form.Item>

          <Form.Item label="更新文件" name="updateFile" tooltip="上传应用更新文件（暂未启用MinIO，功能预留）">
            <Upload
              maxCount={1}
              beforeUpload={() => {
                message.info('MinIO文件上传功能暂未启用，敬请期待');
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} disabled>上传更新文件（功能开发中）</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default ApplicationManagementContent;
