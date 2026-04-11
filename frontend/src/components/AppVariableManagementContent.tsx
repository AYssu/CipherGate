import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Drawer,
  Dropdown,
  Form,
  Input,
  message,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Divider,
  Badge,
  Popover,
  type MenuProps,
} from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  HistoryOutlined,
  ImportOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  FilterOutlined,
} from '@ant-design/icons';
import { getApplicationList, type Application } from '../services/applicationService';
import {
  batchDeleteVariables,
  copyVariable,
  createVariable,
  deleteVariable,
  exportAppVariables,
  getVariableHistory,
  getVariableList,
  importAppVariables,
  updateVariable,
  type AppVariable,
  type AppVariableDTO,
  type AppVariableHistory,
  type AppVariableQuery,
  type VariableType,
} from '../services/appVariableService';

const { Title, Text } = Typography;
const { TextArea } = Input;

const VARIABLE_TYPE_OPTIONS: Array<{ value: VariableType; label: string }> = [
  { value: 'STRING', label: '字符串' },
  { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'JSON', label: 'JSON' },
  { value: 'ARRAY', label: '数组' },
];

const AppVariableManagementContent: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [variables, setVariables] = useState<AppVariable[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const [listFilterForm] = Form.useForm<Pick<AppVariableQuery, 'appId' | 'variableType'>>();
  const [editForm] = Form.useForm<AppVariableDTO>();
  const [listFilters, setListFilters] = useState<Pick<AppVariableQuery, 'appId' | 'variableType'>>({});

  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingVariable, setEditingVariable] = useState<AppVariable | null>(null);

  const [historyVisible, setHistoryVisible] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRecords, setHistoryRecords] = useState<AppVariableHistory[]>([]);
  const [historyPagination, setHistoryPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [historyVariable, setHistoryVariable] = useState<AppVariable | null>(null);

  const [importVisible, setImportVisible] = useState(false);
  const [exportVisible, setExportVisible] = useState(false);
  const [importText, setImportText] = useState('');
  const [exportText, setExportText] = useState('');
  const [variableNameInput, setVariableNameInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);

  const activeAdvancedFilterCount = [listFilters.appId, listFilters.variableType].filter(
    (v) => v !== undefined && v !== null && v !== ''
  ).length;

  const fetchApplications = async () => {
    try {
      const res: any = await getApplicationList({ current: 1, size: 200 });
      if (res.code === 200) {
        setApplications(res.data?.records || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  type FetchVariablesOpts = {
    listFiltersOverride?: Pick<AppVariableQuery, 'appId' | 'variableType'>;
    variableNameOverride?: string;
  };

  const fetchVariables = async (
    page = pagination.current,
    size = pagination.pageSize,
    opts?: FetchVariablesOpts
  ) => {
    setLoading(true);
    try {
      const lf = opts?.listFiltersOverride ?? listFilters;
      const nameTrim =
        opts?.variableNameOverride !== undefined
          ? opts.variableNameOverride.trim()
          : variableNameInput.trim();
      const params: AppVariableQuery = {
        appId: lf.appId,
        variableType: lf.variableType,
        ...(nameTrim ? { variableName: nameTrim } : {}),
        current: page,
        size,
      };

      const res: any = await getVariableList(params);
      if (res.code === 200 && res.data) {
        setVariables(res.data.records || []);
        setPagination({
          current: res.data.current || page,
          pageSize: res.data.size || size,
          total: res.data.total || 0,
        });
      }
    } catch (e) {
      console.error(e);
      message.error('获取变量列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplications();
    fetchVariables(1, pagination.pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const syncListFilterFormFromListFilters = () => {
    listFilterForm.setFieldsValue({
      appId: listFilters.appId,
      variableType: listFilters.variableType,
    });
  };

  const handleAdvancedFilterQuery = async () => {
    const v = await listFilterForm.validateFields();
    const next: Pick<AppVariableQuery, 'appId' | 'variableType'> = {};
    if (v.appId != null && v.appId !== '') {
      next.appId = v.appId;
    }
    if (v.variableType) {
      next.variableType = v.variableType;
    }
    setListFilters(next);
    setSelectedRowKeys([]);
    fetchVariables(1, pagination.pageSize, { listFiltersOverride: next });
    setFilterPopoverOpen(false);
  };

  const handleAdvancedFilterReset = () => {
    listFilterForm.resetFields();
    setListFilters({});
    setSelectedRowKeys([]);
    fetchVariables(1, pagination.pageSize, { listFiltersOverride: {} });
  };

  const applyVariableNameSearch = () => {
    setSelectedRowKeys([]);
    fetchVariables(1, pagination.pageSize, {
      variableNameOverride: variableNameInput,
    });
  };

  const appOptions = useMemo(
    () =>
      applications.map((a) => ({
        label: `${a.appName} (ID: ${a.id})`,
        value: a.id,
      })),
    [applications]
  );

  const getTypeTag = (type: VariableType) => {
    const map: Record<VariableType, { color: string; text: string }> = {
      STRING: { color: 'blue', text: 'STRING' },
      NUMBER: { color: 'purple', text: 'NUMBER' },
      BOOLEAN: { color: 'green', text: 'BOOLEAN' },
      JSON: { color: 'orange', text: 'JSON' },
      ARRAY: { color: 'magenta', text: 'ARRAY' },
    };
    const item = map[type] || { color: 'default', text: type };
    return <Tag color={item.color}>{item.text}</Tag>;
  };

  const formatTime = (text?: string) => {
    if (!text) return '-';
    return new Date(text).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const handleOpenEdit = (record?: AppVariable) => {
    setEditingVariable(record || null);
    if (record) {
      editForm.setFieldsValue({
        id: record.id,
        appId: record.appId,
        variableName: record.variableName,
        displayName: record.displayName,
        description: record.description,
        variableType: record.variableType,
        variableValue: record.variableValue,
        required: record.required,
        sortOrder: record.sortOrder,
        validationRules: record.validationRules,
        options: record.options,
        enabled: record.enabled,
        version: record.version,
        tags: record.tags,
        metadata: record.metadata,
        changeReason: '',
      });
    } else {
      editForm.resetFields();
      editForm.setFieldsValue({
        enabled: true,
        required: false,
        sortOrder: 0,
      } as any);
    }
    setEditModalVisible(true);
  };

  const handleSubmitEdit = async () => {
    try {
      const values = await editForm.validateFields();
      const dto: AppVariableDTO = { ...values };

      if (editingVariable) {
        const res: any = await updateVariable(editingVariable.id, dto);
        if (res.code === 200) {
          message.success('变量更新成功');
          setEditModalVisible(false);
          fetchVariables(pagination.current, pagination.pageSize);
        } else {
          message.error(res.message || '变量更新失败');
        }
      } else {
        const res: any = await createVariable(dto);
        if (res.code === 200) {
          message.success('变量创建成功');
          setEditModalVisible(false);
          fetchVariables(1, pagination.pageSize);
        } else {
          message.error(res.message || '变量创建失败');
        }
      }
    } catch (e) {
      // validateFields 会抛错，这里不需要 message.error
      console.error(e);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res: any = await deleteVariable(id);
      if (res.code === 200) {
        message.success('删除成功');
        fetchVariables(pagination.current, pagination.pageSize);
      } else {
        message.error(res.message || '删除失败');
      }
    } catch (e) {
      console.error(e);
      message.error('删除失败');
    }
  };

  const handleBatchDelete = async () => {
    const ids = selectedRowKeys.map((k) => Number(k)).filter((n) => !Number.isNaN(n));
    if (ids.length === 0) return;
    Modal.confirm({
      title: '批量删除',
      content: `确定要删除选中的 ${ids.length} 个变量吗？`,
      okText: '确定',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res: any = await batchDeleteVariables(ids);
          if (res.code === 200) {
            message.success('批量删除成功');
            setSelectedRowKeys([]);
            fetchVariables(pagination.current, pagination.pageSize);
          } else {
            message.error(res.message || '批量删除失败');
          }
        } catch (e) {
          console.error(e);
          message.error('批量删除失败');
        }
      },
    });
  };

  const handleCopyVariable = async (record: AppVariable) => {
    let newName = `${record.variableName}_copy`;
    Modal.confirm({
      title: '复制变量',
      content: (
        <div>
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary">源变量：</Text> <Text strong>{record.variableName}</Text>
          </div>
          <Input defaultValue={newName} onChange={(e) => (newName = e.target.value)} placeholder="请输入新变量名" />
        </div>
      ),
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        if (!newName || !newName.trim()) {
          message.error('新变量名不能为空');
          return Promise.reject();
        }
        const res: any = await copyVariable(record.id, newName.trim());
        if (res.code === 200) {
          message.success('复制成功');
          fetchVariables(pagination.current, pagination.pageSize);
          return;
        }
        message.error(res.message || '复制失败');
        return Promise.reject();
      },
    });
  };

  const openHistory = async (record: AppVariable) => {
    setHistoryVariable(record);
    setHistoryVisible(true);
    await fetchHistory(record, 1, historyPagination.pageSize);
  };

  const fetchHistory = async (record: AppVariable, current = 1, size = 10) => {
    setHistoryLoading(true);
    try {
      const res: any = await getVariableHistory(record.id, current, size);
      if (res.code === 200 && res.data) {
        setHistoryRecords(res.data.records || []);
        setHistoryPagination({
          current: res.data.current || current,
          pageSize: res.data.size || size,
          total: res.data.total || 0,
        });
      }
    } catch (e) {
      console.error(e);
      message.error('获取历史记录失败');
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleExport = async () => {
    const appId = listFilters.appId;
    if (!appId) {
      message.warning('请先在筛选条件中选择应用');
      return;
    }
    try {
      const res: any = await exportAppVariables(appId, { format: 'json' });
      if (res.code === 200) {
        setExportText(res.data || '');
        setExportVisible(true);
      } else {
        message.error(res.message || '导出失败');
      }
    } catch (e) {
      console.error(e);
      message.error('导出失败');
    }
  };

  const handleImport = async () => {
    const appId = listFilters.appId;
    if (!appId) {
      message.warning('请先在筛选条件中选择应用');
      return;
    }
    if (!importText.trim()) {
      message.warning('请输入要导入的配置内容');
      return;
    }
    try {
      const res: any = await importAppVariables(appId, importText, { format: 'json' });
      if (res.code === 200) {
        message.success('导入成功（已执行批量更新）');
        setImportVisible(false);
        setImportText('');
        fetchVariables(pagination.current, pagination.pageSize);
      } else {
        message.error(res.message || '导入失败');
      }
    } catch (e) {
      console.error(e);
      message.error('导入失败');
    }
  };

  const columns = [
    {
      title: '应用',
      dataIndex: 'appName',
      key: 'appName',
      width: 220,
      render: (_: any, record: AppVariable) => (
        <div>
          <div>
            <Text strong>{record.appName || `AppID:${record.appId}`}</Text>
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            AppID: {record.appId}
          </Text>
        </div>
      ),
    },
    {
      title: '变量名',
      dataIndex: 'variableName',
      key: 'variableName',
      width: 220,
      render: (text: string) => (
        <Text copyable={{ text, tooltips: ['复制', '已复制'] }} style={{ fontFamily: 'Consolas, Monaco, monospace' }}>
          {text}
        </Text>
      ),
    },
    {
      title: '显示名',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 220,
      ellipsis: true as const,
    },
    {
      title: '类型',
      dataIndex: 'variableType',
      key: 'variableType',
      width: 110,
      align: 'center' as const,
      render: (t: VariableType) => getTypeTag(t),
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      align: 'center' as const,
      render: (_: any, record: AppVariable) => (
        <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 160,
      render: (t: string) => <Text style={{ fontSize: 12 }}>{formatTime(t)}</Text>,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right' as const,
      render: (_: any, record: AppVariable) => {
        const items: MenuProps['items'] = [
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: '编辑',
            onClick: () => handleOpenEdit(record),
          },
          {
            key: 'copy',
            icon: <CopyOutlined />,
            label: '复制',
            onClick: () => handleCopyVariable(record),
          },
          {
            key: 'history',
            icon: <HistoryOutlined />,
            label: '历史记录',
            onClick: () => openHistory(record),
          },
          { type: 'divider' },
          {
            key: 'delete',
            icon: <DeleteOutlined />,
            label: '删除',
            danger: true,
            onClick: () => {
              Modal.confirm({
                title: '删除变量',
                content: `确定要删除变量 "${record.variableName}" 吗？`,
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
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleOpenEdit(record)}>
              编辑
            </Button>
            <Dropdown menu={{ items }} trigger={['click']}>
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  const historyColumns = [
    {
      title: '操作',
      dataIndex: 'operationType',
      key: 'operationType',
      width: 90,
      render: (t: AppVariableHistory['operationType']) => {
        const map: Record<string, { color: string; text: string }> = {
          CREATE: { color: 'green', text: 'CREATE' },
          UPDATE: { color: 'blue', text: 'UPDATE' },
          DELETE: { color: 'red', text: 'DELETE' },
        };
        const item = map[t] || { color: 'default', text: t };
        return <Tag color={item.color}>{item.text}</Tag>;
      },
    },
    {
      title: '原因',
      dataIndex: 'changeReason',
      key: 'changeReason',
      ellipsis: true as const,
      render: (t: string) => <Text>{t || '-'}</Text>,
    },
    {
      title: '时间',
      dataIndex: 'operatedAt',
      key: 'operatedAt',
      width: 160,
      render: (t: string) => <Text style={{ fontSize: 12 }}>{formatTime(t)}</Text>,
    },
  ];

  return (
    <Card>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>
              变量管理
            </Title>
          </Col>
          <Col>
            <Space>
              <Tooltip title="导出当前筛选应用的变量配置（JSON）">
                <Button icon={<ExportOutlined />} onClick={handleExport}>
                  导出
                </Button>
              </Tooltip>
              <Tooltip title="导入配置会执行批量更新（JSON）">
                <Button icon={<ImportOutlined />} onClick={() => setImportVisible(true)}>
                  导入
                </Button>
              </Tooltip>
              <Button icon={<ReloadOutlined />} onClick={() => fetchVariables(pagination.current, pagination.pageSize)}>
                刷新
              </Button>
              <Button danger disabled={selectedRowKeys.length === 0} onClick={handleBatchDelete}>
                批量删除
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenEdit()}>
                新建变量
              </Button>
            </Space>
          </Col>
        </Row>

        {/* 主搜索（变量名）+ 高级筛选 */}
        <Row gutter={12} align="middle" wrap>
          <Col flex="none">
            <Space.Compact
              style={{
                width: 360,
                maxWidth: 'calc(100vw - 120px)',
              }}
            >
              <Input
                placeholder="变量名模糊匹配"
                allowClear
                value={variableNameInput}
                onChange={(e) => setVariableNameInput(e.target.value)}
                onPressEnter={() => applyVariableNameSearch()}
                style={{ minWidth: 0 }}
              />
              <Button type="primary" onClick={() => applyVariableNameSearch()}>
                搜索
              </Button>
            </Space.Compact>
          </Col>
          <Col flex="none">
            <Popover
              trigger="click"
              placement="bottomLeft"
              open={filterPopoverOpen}
              onOpenChange={(open) => {
                setFilterPopoverOpen(open);
                if (open) {
                  syncListFilterFormFromListFilters();
                }
              }}
              content={
                <div style={{ width: 420, maxWidth: '90vw' }}>
                  <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                    <Row gutter={16}>
                      <Col span={24}>
                        <Form.Item label="应用" name="appId">
                          <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            placeholder="选择应用"
                            options={appOptions}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={24}>
                        <Form.Item label="类型" name="variableType">
                          <Select allowClear placeholder="变量类型" options={VARIABLE_TYPE_OPTIONS} />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row justify="end" gutter={8} style={{ marginTop: 8 }}>
                      <Col>
                        <Button onClick={handleAdvancedFilterReset}>重置</Button>
                      </Col>
                      <Col>
                        <Button type="primary" onClick={() => void handleAdvancedFilterQuery()}>
                          查询
                        </Button>
                      </Col>
                    </Row>
                  </Form>
                </div>
              }
            >
              <Badge count={activeAdvancedFilterCount} size="small" offset={[-2, 2]}>
                <Button icon={<FilterOutlined />}>筛选</Button>
              </Badge>
            </Popover>
          </Col>
        </Row>

        <Table
          columns={columns as any}
          dataSource={variables}
          rowKey="id"
          loading={loading}
          size="middle"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => fetchVariables(page, pageSize),
          }}
          scroll={{ x: 1400 }}
        />
      </Space>

      <Modal
        title={editingVariable ? '编辑变量' : '新建变量'}
        open={editModalVisible}
        onOk={handleSubmitEdit}
        onCancel={() => setEditModalVisible(false)}
        okText="确定"
        cancelText="取消"
        width={780}
        styles={{ body: { maxHeight: '70vh', overflowY: 'auto', paddingRight: 8 } }}
      >
        <Form
          form={editForm}
          layout="vertical"
          colon={false}
          initialValues={{
            enabled: true,
            required: false,
            sortOrder: 0,
            variableType: 'STRING',
          }}
        >
          <Divider orientation="left" style={{ marginTop: 0 }}>基本信息</Divider>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="应用"
                name="appId"
                rules={[{ required: true, message: '请选择应用' }]}
                tooltip="建议先在筛选中选好应用，再创建变量"
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择应用"
                  options={appOptions}
                  disabled={!!editingVariable}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="变量名"
                name="variableName"
                rules={[
                  { required: true, message: '请输入变量名' },
                  { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '以字母开头，仅允许字母/数字/下划线' },
                ]}
              >
                <Input placeholder="如：API_URL" disabled={!!editingVariable} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="显示名" name="displayName" rules={[{ required: true, message: '请输入显示名' }]}>
                <Input placeholder="用于界面展示" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">配置</Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="类型" name="variableType" rules={[{ required: true, message: '请选择类型' }]}>
                <Select options={VARIABLE_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="排序" name="sortOrder">
                <Input type="number" placeholder="0" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="启用" name="enabled">
                <Select
                  options={[
                    { value: true, label: '启用' },
                    { value: false, label: '停用' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="变量值" name="variableValue">
            <TextArea
              placeholder="STRING/NUMBER/BOOLEAN/JSON/ARRAY"
              autoSize={{ minRows: 3, maxRows: 10 }}
              style={{ fontFamily: 'Consolas, Monaco, monospace', backgroundColor: '#fafafa' }}
            />
          </Form.Item>

          <Divider orientation="left">附加信息</Divider>
          <Form.Item label="描述" name="description">
            <TextArea placeholder="可选" autoSize={{ minRows: 2, maxRows: 6 }} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="标签" name="tags" tooltip="后端按字符串 like 查询">
                <Input placeholder="如：core,release（可选）" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="版本号" name="version">
                <Input placeholder="如：v1.0.0（可选）" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="变更原因" name="changeReason" tooltip="用于历史记录备注（建议填写）">
            <Input placeholder="如：上线配置/修复问题/调参等" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <HistoryOutlined />
            <span>历史记录</span>
            {historyVariable ? (
              <Tag color="blue" style={{ marginLeft: 8 }}>
                {historyVariable.variableName}
              </Tag>
            ) : null}
          </Space>
        }
        open={historyVisible}
        onClose={() => setHistoryVisible(false)}
        width={720}
      >
        <Table
          columns={historyColumns as any}
          dataSource={historyRecords}
          rowKey="id"
          loading={historyLoading}
          pagination={{
            ...historyPagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              if (!historyVariable) return;
              fetchHistory(historyVariable, page, pageSize);
            },
          }}
        />
      </Drawer>

      <Modal
        title="导入变量配置（JSON）"
        open={importVisible}
        onOk={handleImport}
        onCancel={() => setImportVisible(false)}
        okText="导入"
        cancelText="取消"
        width={800}
      >
        <Text type="secondary">
          导入会调用后端“批量更新”逻辑：仅更新已存在的变量（按变量名匹配），不会自动创建新变量。
        </Text>
        <div style={{ height: 12 }} />
        <TextArea
          value={importText}
          onChange={(e) => setImportText(e.target.value)}
          placeholder='粘贴 JSON，例如：{"FEATURE_X": true, "API_URL": "https://..."}'
          autoSize={{ minRows: 12, maxRows: 18 }}
          style={{ fontFamily: 'Consolas, Monaco, monospace' }}
        />
      </Modal>

      <Modal
        title="导出变量配置（JSON）"
        open={exportVisible}
        onOk={() => {
          navigator.clipboard.writeText(exportText || '').then(() => message.success('已复制到剪贴板'));
        }}
        onCancel={() => setExportVisible(false)}
        okText="复制"
        cancelText="关闭"
        width={800}
      >
        <Text type="secondary">你可以复制后保存到本地，或作为导入模板。</Text>
        <div style={{ height: 12 }} />
        <TextArea
          value={exportText}
          readOnly
          autoSize={{ minRows: 12, maxRows: 18 }}
          style={{ fontFamily: 'Consolas, Monaco, monospace' }}
        />
      </Modal>
    </Card>
  );
};

export default AppVariableManagementContent;

