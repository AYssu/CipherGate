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
  InputNumber,
  Badge,
  Dropdown,
  type MenuProps,
  TimePicker,
  Switch,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CopyOutlined,
  MoreOutlined,
  PoweroffOutlined,
  CheckCircleOutlined,
  ExportOutlined,
  AppstoreOutlined,
  KeyOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getLicenseList,
  createLicense,
  batchCreateLicenses,
  updateLicense,
  deleteLicense,
  updateLicenseStatus,
  exportLicenses,
  type LicenseKey,
  type LicenseKeyDTO,
  type LicenseBatchCreateDTO,
} from '../services/licenseService';
import { getApplicationList, type Application } from '../services/applicationService';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

const LicenseManagementContent: React.FC = () => {
  const [licenses, setLicenses] = useState<LicenseKey[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [batchModalVisible, setBatchModalVisible] = useState(false);
  const [editingLicense, setEditingLicense] = useState<LicenseKey | null>(null);
  const [form] = Form.useForm();
  const [batchForm] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [filters, setFilters] = useState<any>({});

  // 卡密类型选项
  const keyTypeOptions = [
    { label: '天卡', value: 'DAY' },
    { label: '周卡', value: 'WEEK' },
    { label: '月卡', value: 'MONTH' },
    { label: '季卡', value: 'QUARTER' },
    { label: '半年卡', value: 'HALF_YEAR' },
    { label: '年卡', value: 'YEAR' },
    { label: '永久卡', value: 'PERMANENT' },
    { label: '自定义', value: 'CUSTOM' },
  ];

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

  // 获取卡密列表
  const fetchLicenses = async (page = 1, size = 10, filterParams = {}) => {
    setLoading(true);
    try {
      const result: any = await getLicenseList({
        current: page,
        size: size,
        ...filterParams,
      });
      
      if (result.code === 200 && result.data) {
        setLicenses(result.data.records || []);
        setPagination({
          current: result.data.current || page,
          pageSize: result.data.size || size,
          total: result.data.total || 0,
        });
      }
    } catch (error) {
      console.error('获取卡密列表失败:', error);
      message.error('获取卡密列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplications();
    fetchLicenses();
  }, []);

  // 打开创建/编辑弹窗
  const handleOpenModal = (license?: LicenseKey) => {
    setEditingLicense(license || null);
    if (license) {
      form.setFieldsValue({
        appId: license.appId,
        keyType: license.keyType,
        durationValue: license.durationValue,
        useLimit: license.useLimit,
        unbindLimit: license.unbindLimit,
        useTimeStart: license.useTimeStart ? dayjs(license.useTimeStart, 'HH:mm:ss') : null,
        useTimeEnd: license.useTimeEnd ? dayjs(license.useTimeEnd, 'HH:mm:ss') : null,
        deviceCheckEnabled: license.deviceCheckEnabled,
        ipCheckEnabled: license.ipCheckEnabled,
        remark: license.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        useLimit: 0,
        unbindLimit: 0,
        deviceCheckEnabled: true,
        ipCheckEnabled: false,
      });
    }
    setModalVisible(true);
  };

  // 打开批量生成弹窗
  const handleOpenBatchModal = () => {
    batchForm.resetFields();
    batchForm.setFieldsValue({
      totalCount: 10,
      useLimit: 0,
      unbindLimit: 0,
      deviceCheckEnabled: true,
      ipCheckEnabled: false,
    });
    setBatchModalVisible(true);
  };

  // 提交创建/编辑
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      
      const dto: LicenseKeyDTO = {
        ...values,
        useTimeStart: values.useTimeStart ? values.useTimeStart.format('HH:mm:ss') : undefined,
        useTimeEnd: values.useTimeEnd ? values.useTimeEnd.format('HH:mm:ss') : undefined,
      };

      if (editingLicense) {
        await updateLicense(editingLicense.id, dto);
        message.success('卡密更新成功');
      } else {
        await createLicense(dto);
        message.success('卡密创建成功');
      }

      setModalVisible(false);
      fetchLicenses(pagination.current, pagination.pageSize, filters);
    } catch (error: any) {
      console.error('操作失败:', error);
      message.error(error.response?.data?.message || '操作失败');
    }
  };

  // 提交批量生成
  const handleBatchSubmit = async () => {
    try {
      const values = await batchForm.validateFields();
      
      const dto: LicenseBatchCreateDTO = values;

      const result: any = await batchCreateLicenses(dto);
      
      if (result.code === 200) {
        message.success(`成功生成 ${values.totalCount} 个卡密`);
        setBatchModalVisible(false);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
      }
    } catch (error: any) {
      console.error('批量生成失败:', error);
      message.error(error.response?.data?.message || '批量生成失败');
    }
  };

  // 删除卡密
  const handleDelete = async (id: number) => {
    try {
      await deleteLicense(id);
      message.success('卡密删除成功');
      fetchLicenses(pagination.current, pagination.pageSize, filters);
    } catch (error: any) {
      console.error('删除失败:', error);
      message.error(error.response?.data?.message || '删除失败');
    }
  };

  // 更新状态
  const handleUpdateStatus = async (id: number, status: number) => {
    try {
      await updateLicenseStatus(id, status);
      message.success('状态更新成功');
      fetchLicenses(pagination.current, pagination.pageSize, filters);
    } catch (error: any) {
      console.error('更新状态失败:', error);
      message.error(error.response?.data?.message || '更新状态失败');
    }
  };

  // 复制卡密
  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    message.success(`${label}已复制到剪贴板`);
  };

  // 导出卡密
  const handleExport = async () => {
    try {
      const result: any = await exportLicenses(filters);
      if (result.code === 200 && result.data) {
        // 转换为CSV格式
        const csvContent = convertToCSV(result.data);
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `licenses_${new Date().getTime()}.csv`;
        link.click();
        message.success('导出成功');
      }
    } catch (error) {
      console.error('导出失败:', error);
      message.error('导出失败');
    }
  };

  // 转换为CSV
  const convertToCSV = (data: LicenseKey[]) => {
    const headers = ['卡密码', '应用', '类型', '状态', '创建时间'];
    const rows = data.map(item => [
      item.keyCode,
      item.appName || '',
      getKeyTypeLabel(item.keyType),
      getStatusLabel(item.status),
      item.createdAt,
    ]);
    
    return [headers, ...rows].map(row => row.join(',')).join('\n');
  };

  // 获取卡密类型标签
  const getKeyTypeLabel = (type: string) => {
    const option = keyTypeOptions.find(opt => opt.value === type);
    return option ? option.label : type;
  };

  // 获取状态标签
  const getStatusLabel = (status: number) => {
    const map: Record<number, string> = {
      1: '未使用',
      2: '使用中',
      3: '已过期',
      4: '已禁用',
    };
    return map[status] || '未知';
  };

  // 获取状态Badge
  const getStatusBadge = (status: number) => {
    const map: Record<number, { text: string; status: 'success' | 'processing' | 'error' | 'default' }> = {
      1: { text: '未使用', status: 'default' },
      2: { text: '使用中', status: 'processing' },
      3: { text: '已过期', status: 'error' },
      4: { text: '已禁用', status: 'error' },
    };
    const item = map[status] || { text: '未知', status: 'default' };
    return <Badge status={item.status} text={item.text} />;
  };

  // 表格列定义
  const columns = [
    {
      title: '卡密码',
      dataIndex: 'keyCode',
      key: 'keyCode',
      width: 180,
      render: (text: string) => (
        <Space>
          <Text 
            copyable={{ text, tooltips: ['复制', '已复制'] }} 
            style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, color: '#666' }}
          >
            {text}
          </Text>
        </Space>
      ),
    },
    {
      title: '应用',
      dataIndex: 'appName',
      key: 'appName',
      width: 150,
      render: (text: string) => <Text>{text || '-'}</Text>,
    },
    {
      title: '类型',
      dataIndex: 'keyType',
      key: 'keyType',
      width: 150,
      render: (type: string, record: LicenseKey) => {
        let label = getKeyTypeLabel(type);
        
        // 显示倍数或自定义时长
        if (type === 'CUSTOM' && record.durationValue && record.durationUnit) {
          const unitMap: Record<string, string> = {
            'HOUR': '小时',
            'DAY': '天',
            'MONTH': '月',
            'YEAR': '年'
          };
          label = `${record.durationValue}${unitMap[record.durationUnit] || record.durationUnit}`;
        } else if (type !== 'PERMANENT' && record.durationValue) {
          label = `${record.durationValue}x${label}`;
        }
        
        return <Tag color="blue">{label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center' as const,
      render: (status: number) => getStatusBadge(status),
    },
    {
      title: '在线',
      dataIndex: 'isOnline',
      key: 'isOnline',
      width: 80,
      align: 'center' as const,
      render: (isOnline: boolean) => (
        isOnline ? <Badge status="success" text="在线" /> : <Badge status="default" text="离线" />
      ),
    },
    {
      title: '使用次数',
      key: 'useCount',
      width: 100,
      align: 'center' as const,
      render: (_: any, record: LicenseKey) => (
        <Text style={{ fontSize: 12 }}>
          {record.useCount} / {record.useLimit === 0 ? '∞' : record.useLimit}
        </Text>
      ),
    },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
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
      width: 150,
      fixed: 'right' as const,
      render: (_: any, record: LicenseKey) => {
        const menuItems: MenuProps['items'] = [
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: '编辑',
            onClick: () => handleOpenModal(record),
          },
          {
            key: 'status',
            icon: record.status === 4 ? <CheckCircleOutlined /> : <PoweroffOutlined />,
            label: record.status === 4 ? '启用' : '禁用',
            onClick: () => {
              const newStatus = record.status === 4 ? 1 : 4;
              const action = record.status === 4 ? '启用' : '禁用';
              Modal.confirm({
                title: `${action}卡密`,
                content: `确定要${action}卡密"${record.keyCode}"吗？`,
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
                title: '删除卡密',
                content: `确定要删除卡密"${record.keyCode}"吗？删除后无法恢复。`,
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

  return (
    <Card>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 标题和操作栏 */}
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>卡密管理</Title>
          </Col>
          <Col>
            <Space>
              <Button
                icon={<ExportOutlined />}
                onClick={handleExport}
              >
                导出
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => fetchLicenses(pagination.current, pagination.pageSize, filters)}
              >
                刷新
              </Button>
              <Button
                icon={<KeyOutlined />}
                onClick={handleOpenBatchModal}
              >
                批量生成
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => handleOpenModal()}
              >
                创建卡密
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
                fetchLicenses(1, pagination.pageSize, newFilters);
              }}
            >
              {applications.map(app => (
                <Option key={app.id} value={app.id}>{app.appName}</Option>
              ))}
            </Select>
          </Col>
          <Col span={6}>
            <Select
              placeholder="卡密类型"
              allowClear
              style={{ width: '100%' }}
              onChange={(value) => {
                const newFilters = { ...filters, keyType: value };
                setFilters(newFilters);
                fetchLicenses(1, pagination.pageSize, newFilters);
              }}
            >
              {keyTypeOptions.map(opt => (
                <Option key={opt.value} value={opt.value}>{opt.label}</Option>
              ))}
            </Select>
          </Col>
          <Col span={6}>
            <Select
              placeholder="状态"
              allowClear
              style={{ width: '100%' }}
              onChange={(value) => {
                const newFilters = { ...filters, status: value };
                setFilters(newFilters);
                fetchLicenses(1, pagination.pageSize, newFilters);
              }}
            >
              <Option value={1}>未使用</Option>
              <Option value={2}>使用中</Option>
              <Option value={3}>已过期</Option>
              <Option value={4}>已禁用</Option>
            </Select>
          </Col>
          <Col span={6}>
            <Input
              placeholder="搜索卡密码"
              allowClear
              onPressEnter={(e: any) => {
                const newFilters = { ...filters, keyCode: e.target.value };
                setFilters(newFilters);
                fetchLicenses(1, pagination.pageSize, newFilters);
              }}
            />
          </Col>
        </Row>

        {/* 卡密列表表格 */}
        <Table
          columns={columns}
          dataSource={licenses}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              fetchLicenses(page, pageSize, filters);
            },
          }}
          scroll={{ x: 1400 }}
        />
      </Space>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editingLicense ? '编辑卡密' : '创建卡密'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={700}
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
                <Select placeholder="选择应用" disabled={!!editingLicense}>
                  {applications.map(app => (
                    <Option key={app.id} value={app.id}>{app.appName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="卡密类型"
                name="keyType"
                rules={[{ required: true, message: '请选择卡密类型' }]}
              >
                <Select 
                  placeholder="选择类型" 
                  disabled={!!editingLicense}
                  onChange={(value) => {
                    // 当选择自定义类型时，显示自定义时长字段
                    if (value === 'CUSTOM') {
                      form.setFieldValue('durationUnit', 'DAY');
                    }
                  }}
                >
                  {keyTypeOptions.map(opt => (
                    <Option key={opt.value} value={opt.value}>{opt.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          {!editingLicense && (
            <Form.Item
              label="自定义卡密"
              name="keyCode"
              tooltip="留空自动生成16位；输入1-5位作为前缀自动补全；输入6-64位作为完整卡密"
              rules={[
                {
                  pattern: /^[A-Z0-9]*$/,
                  message: '只能包含大写字母和数字',
                },
                {
                  max: 64,
                  message: '长度不能超过64位',
                },
              ]}
            >
              <Input 
                placeholder="留空自动生成，或输入前缀/完整卡密（仅支持大写字母和数字）" 
                maxLength={64}
                style={{ textTransform: 'uppercase' }}
                onChange={(e) => {
                  // 自动转大写
                  const value = e.target.value.toUpperCase();
                  form.setFieldValue('keyCode', value);
                }}
              />
            </Form.Item>
          )}

          <Form.Item noStyle shouldUpdate={(prevValues, currentValues) => prevValues.keyType !== currentValues.keyType}>
            {({ getFieldValue }) => {
              const keyType = getFieldValue('keyType');
              
              // 如果是自定义类型，显示自定义时长配置
              if (keyType === 'CUSTOM') {
                return (
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        label="时长数值"
                        name="durationValue"
                        rules={[{ required: true, message: '请输入时长数值' }]}
                      >
                        <InputNumber 
                          min={1} 
                          style={{ width: '100%' }} 
                          placeholder="例如：3、100、365"
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        label="时长单位"
                        name="durationUnit"
                        rules={[{ required: true, message: '请选择时长单位' }]}
                      >
                        <Select placeholder="选择单位">
                          <Option value="HOUR">小时</Option>
                          <Option value="DAY">天</Option>
                          <Option value="MONTH">月</Option>
                          <Option value="YEAR">年</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                  </Row>
                );
              }
              
              // 如果是预设类型（非永久卡），显示倍数
              if (keyType && keyType !== 'PERMANENT' && keyType !== 'CUSTOM') {
                return (
                  <Form.Item
                    label="倍数"
                    name="durationValue"
                    tooltip="例如：3天卡输入3，5月卡输入5"
                    rules={[{ required: true, message: '请输入倍数' }]}
                  >
                    <InputNumber 
                      min={1} 
                      max={999}
                      style={{ width: '100%' }} 
                      placeholder="输入倍数，例如：1、3、5、10"
                    />
                  </Form.Item>
                );
              }
              
              return null;
            }}
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="使用次数限制"
                name="useLimit"
                tooltip="0表示不限制"
              >
                <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="解绑次数限制"
                name="unbindLimit"
                tooltip="0表示不限制"
              >
                <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="可使用时间段-开始"
                name="useTimeStart"
              >
                <TimePicker format="HH:mm:ss" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="可使用时间段-结束"
                name="useTimeEnd"
              >
                <TimePicker format="HH:mm:ss" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="验证设备"
                name="deviceCheckEnabled"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="验证IP"
                name="ipCheckEnabled"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="备注"
            name="remark"
          >
            <TextArea rows={3} placeholder="输入备注信息" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 批量生成弹窗 */}
      <Modal
        title="批量生成卡密"
        open={batchModalVisible}
        onOk={handleBatchSubmit}
        onCancel={() => setBatchModalVisible(false)}
        width={700}
        okText="生成"
        cancelText="取消"
      >
        <Form
          form={batchForm}
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
                <Select placeholder="选择应用">
                  {applications.map(app => (
                    <Option key={app.id} value={app.id}>{app.appName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="批次名称"
                name="batchName"
                rules={[{ required: true, message: '请输入批次名称' }]}
              >
                <Input placeholder="例如：2024年1月批次" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="卡密类型"
                name="keyType"
                rules={[{ required: true, message: '请选择卡密类型' }]}
              >
                <Select 
                  placeholder="选择类型"
                  onChange={(value) => {
                    if (value === 'CUSTOM') {
                      batchForm.setFieldValue('durationUnit', 'DAY');
                    }
                  }}
                >
                  {keyTypeOptions.map(opt => (
                    <Option key={opt.value} value={opt.value}>{opt.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="生成数量"
                name="totalCount"
                rules={[{ required: true, message: '请输入生成数量' }]}
              >
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item noStyle shouldUpdate={(prevValues, currentValues) => prevValues.keyType !== currentValues.keyType}>
            {({ getFieldValue }) => {
              const keyType = getFieldValue('keyType');
              
              // 如果是自定义类型，显示自定义时长配置
              if (keyType === 'CUSTOM') {
                return (
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        label="时长数值"
                        name="durationValue"
                        rules={[{ required: true, message: '请输入时长数值' }]}
                      >
                        <InputNumber 
                          min={1} 
                          style={{ width: '100%' }} 
                          placeholder="例如：3、100、365"
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        label="时长单位"
                        name="durationUnit"
                        rules={[{ required: true, message: '请选择时长单位' }]}
                      >
                        <Select placeholder="选择单位">
                          <Option value="HOUR">小时</Option>
                          <Option value="DAY">天</Option>
                          <Option value="MONTH">月</Option>
                          <Option value="YEAR">年</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                  </Row>
                );
              }
              
              // 如果是预设类型（非永久卡），显示倍数
              if (keyType && keyType !== 'PERMANENT' && keyType !== 'CUSTOM') {
                return (
                  <Form.Item
                    label="倍数"
                    name="durationValue"
                    tooltip="例如：3天卡输入3，5月卡输入5"
                    rules={[{ required: true, message: '请输入倍数' }]}
                  >
                    <InputNumber 
                      min={1} 
                      max={999}
                      style={{ width: '100%' }} 
                      placeholder="输入倍数，例如：1、3、5、10"
                    />
                  </Form.Item>
                );
              }
              
              return null;
            }}
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="使用次数限制"
                name="useLimit"
                tooltip="0表示不限制"
              >
                <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="解绑次数限制"
                name="unbindLimit"
                tooltip="0表示不限制"
              >
                <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="验证设备"
                name="deviceCheckEnabled"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="验证IP"
                name="ipCheckEnabled"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            label="备注"
            name="remark"
          >
            <TextArea rows={3} placeholder="输入批次备注信息" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default LicenseManagementContent;
