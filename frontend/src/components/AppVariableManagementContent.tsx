import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Row,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Divider,
  Badge,
  Popover,
  Pagination,
  Grid,
  type MenuProps,
} from 'antd';
import M5BottomSheet from './M5BottomSheet';
import {
  DownOutlined,
  RightOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  HistoryOutlined,
  ImportOutlined,
  EyeOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  FilterOutlined,
} from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
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

type VariableTemplateItem = {
  token: string;
  label: string;
  description: string;
};

const VARIABLE_TEMPLATE_GROUPS: Array<{ title: string; items: VariableTemplateItem[] }> = [
  {
    title: '时间',
    items: [
      { token: '${time}', label: '当前秒时间戳', description: '返回当前 Unix 时间戳（秒）' },
      { token: '${time_ms}', label: '当前毫秒时间戳', description: '返回当前 Unix 时间戳（毫秒）' },
      { token: '${now}', label: '当前 ISO 时间', description: '返回 UTC ISO 时间，如 2026-04-22T10:15:30Z' },
      { token: '${date(yyyy-MM-dd)}', label: '日期格式化', description: '按指定格式输出当前日期' },
      { token: '${datetime(yyyy-MM-dd HH:mm:ss)}', label: '日期时间格式化', description: '按指定格式输出当前日期时间' },
      { token: '${unix(+3600)}', label: '偏移秒时间戳', description: '在当前秒时间戳基础上加减偏移秒数' },
    ],
  },
  {
    title: '会话/用户',
    items: [
      { token: '${app.id}', label: '应用 ID', description: '当前应用 ID' },
      { token: '${app.key}', label: '应用 Key', description: '当前应用 appKey' },
      { token: '${user.id}', label: '用户 ID', description: '当前终端用户 ID' },
      { token: '${user.username}', label: '用户名', description: '当前终端用户名' },
      { token: '${user.member_expires_at}', label: '会员到期时间', description: '当前用户会员到期时间（ISO）' },
      { token: '${ws.conn_id}', label: '连接 ID', description: '当前 WebSocket 连接 ID' },
      { token: '${ws.connected_at}', label: '连接时间戳', description: 'WebSocket 建连时间戳（毫秒）' },
      { token: '${ws.online_seconds}', label: '在线秒数', description: '当前连接在线时长（秒）' },
      { token: '${client.ip}', label: '客户端 IP', description: '请求来源 IP' },
      { token: '${device.id}', label: '设备 ID', description: '设备唯一标识' },
      { token: '${device.name}', label: '设备名称', description: '设备展示名称' },
      { token: '${device.os}', label: '设备系统', description: '设备操作系统信息' },
    ],
  },
  {
    title: '登录统计',
    items: [
      { token: '${user.login_count}', label: '登录次数', description: '当前用户累计登录次数' },
      { token: '${user.last_login_at}', label: '上次登录时间', description: '当前用户上次登录时间（ISO）' },
      { token: '${user.last_login_ip}', label: '上次登录 IP', description: '当前用户上次登录 IP' },
      { token: '${user.login_count+1}', label: '登录次数 +1', description: '在登录次数基础上加 1（展示视角）' },
    ],
  },
  {
    title: '随机/编码',
    items: [
      { token: '${rand.int(1000,9999)}', label: '随机整数', description: '生成指定范围内随机整数' },
      { token: '${rand.str(16)}', label: '随机字符串', description: '生成指定长度随机字母数字串' },
      { token: '${uuid}', label: 'UUID', description: '生成随机 UUID' },
      { token: '${nonce(16)}', label: '随机 nonce', description: '生成指定长度随机 nonce' },
      { token: '${sha256(text)}', label: 'SHA-256', description: '对参数文本做 SHA-256 哈希（十六进制）' },
      { token: '${base64(text)}', label: 'Base64', description: '对参数文本做 Base64 编码' },
      { token: '${urlencode(text)}', label: 'URL 编码', description: '对参数文本做 URL 编码' },
    ],
  },
  {
    title: '字符串/条件',
    items: [
      { token: '${upper(x)}', label: '转大写', description: '将参数转为大写' },
      { token: '${lower(x)}', label: '转小写', description: '将参数转为小写' },
      { token: '${trim(x)}', label: '去空格', description: '去除参数首尾空白' },
      { token: '${substr(x,0,8)}', label: '截取子串', description: '按起止下标截取字符串' },
      { token: '${replace(x,a,b)}', label: '替换文本', description: '将文本中的 a 替换为 b' },
      { token: '${user.member_expires_at|2099-12-31 23:59:59}', label: '默认值', description: '左值为空时使用默认值' },
      { token: '${if(user.login_count>10,VIP,NORMAL)}', label: '条件判断', description: '满足条件返回第一个值，否则返回第二个值' },
    ],
  },
];

const AppVariableManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
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
  const [exportText] = useState('');
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewText, setPreviewText] = useState('');
  const [previewAppId, setPreviewAppId] = useState<number | null>(null);
  const [variableNameInput, setVariableNameInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);
  const [templateGroupTitle, setTemplateGroupTitle] = useState<string>(VARIABLE_TEMPLATE_GROUPS[0].title);
  const [templatePanelOpen, setTemplatePanelOpen] = useState(false);
  const currentVariableValue = Form.useWatch('variableValue', editForm) as string | undefined;

  const activeAdvancedFilterCount = [listFilters.appId, listFilters.variableType].filter(
    (v) => v !== undefined && v !== null
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
    if (v.appId != null) {
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

  const insertTemplateToVariableValue = (template: string) => {
    const current = (editForm.getFieldValue('variableValue') ?? '') as string;
    const next = current ? `${current}${current.endsWith(' ') ? '' : ' '}${template}` : template;
    editForm.setFieldsValue({ variableValue: next });
  };

  const activeTemplateGroup = useMemo(
    () => VARIABLE_TEMPLATE_GROUPS.find((group) => group.title === templateGroupTitle) ?? VARIABLE_TEMPLATE_GROUPS[0],
    [templateGroupTitle]
  );

  const variableValuePreviewParts = useMemo(() => {
    const text = currentVariableValue ?? '';
    const regex = /\$\{[^{}]+\}/g;
    const parts: Array<{ type: 'text' | 'token'; value: string }> = [];
    let last = 0;
    for (const match of text.matchAll(regex)) {
      const idx = match.index ?? 0;
      if (idx > last) {
        parts.push({ type: 'text', value: text.slice(last, idx) });
      }
      parts.push({ type: 'token', value: match[0] });
      last = idx + match[0].length;
    }
    if (last < text.length) {
      parts.push({ type: 'text', value: text.slice(last) });
    }
    return parts;
  }, [currentVariableValue]);

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

  const handlePreviewVariables = async () => {
    const appId = listFilters.appId;
    if (!appId) {
      message.warning('请先在筛选条件中选择应用');
      return;
    }
    setPreviewLoading(true);
    setPreviewVisible(true);
    setPreviewAppId(appId);
    try {
      const res: any = await exportAppVariables(appId, { format: 'json' });
      if (res.code === 200) {
        const raw = res.data || '{}';
        try {
          const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
          setPreviewText(JSON.stringify(parsed ?? {}, null, 2));
        } catch {
          setPreviewText(String(raw));
        }
      } else {
        message.error(res.message || '预览加载失败');
        setPreviewText('');
      }
    } catch (e) {
      console.error(e);
      message.error('预览加载失败');
      setPreviewText('');
    } finally {
      setPreviewLoading(false);
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
    <Card styles={{ body: { padding: isMobile ? 12 : 24 } }}>
      <Space direction="vertical" size={isMobile ? 12 : 'large'} style={{ width: '100%' }}>
        {/* 标题栏 */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>变量管理</Title>
          {isMobile ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
              <Dropdown
                menu={{
                  items: [
                    { key: 'import', icon: <ImportOutlined />, label: '导入', onClick: () => setImportVisible(true) },
                    { key: 'preview', icon: <EyeOutlined />, label: '变量预览', onClick: () => void handlePreviewVariables() },
                    { type: 'divider' },
                    { key: 'batchDelete', icon: <DeleteOutlined />, label: '批量删除', danger: true, disabled: selectedRowKeys.length === 0, onClick: handleBatchDelete },
                  ],
                }}
                trigger={['click']}
              >
                <Button size="small" icon={<MoreOutlined />}>更多</Button>
              </Dropdown>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => fetchVariables(pagination.current, pagination.pageSize)}
               
              >
                刷新
              </Button>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => handleOpenEdit()}
               
              >
                新建
              </Button>
            </div>
          ) : (
            <Space>
              <Tooltip title="预览当前筛选应用的变量 JSON">
                <Button icon={<EyeOutlined />} onClick={() => void handlePreviewVariables()}>变量预览</Button>
              </Tooltip>
              <Tooltip title="导入配置会执行批量更新（JSON）">
                <Button icon={<ImportOutlined />} onClick={() => setImportVisible(true)}>导入</Button>
              </Tooltip>
              <Button icon={<ReloadOutlined />} onClick={() => fetchVariables(pagination.current, pagination.pageSize)}>刷新</Button>
              <Button danger disabled={selectedRowKeys.length === 0} onClick={handleBatchDelete}>批量删除</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenEdit()}>新建变量</Button>
            </Space>
          )}
        </div>

        {/* 搜索和筛选 */}
        <div style={isMobile ? { display: 'flex', gap: 8, alignItems: 'center' } : undefined}>
          {isMobile ? (
            <>
              <Input
                size="small"
                placeholder="变量名模糊匹配"
                allowClear
                value={variableNameInput}
                onChange={(e) => setVariableNameInput(e.target.value)}
                onPressEnter={() => applyVariableNameSearch()}
                style={{ flex: 1, minWidth: 0 }}
              />
              <Button type="primary" onClick={() => applyVariableNameSearch()} style={{ flexShrink: 0 }}>搜索</Button>
              <M5BottomSheet open={filterPopoverOpen} onClose={() => setFilterPopoverOpen(false)} title="筛选条件" footer={<><Button onClick={handleAdvancedFilterReset} style={{ flex: 1, height: 44, borderRadius: 10 }}>重置</Button><Button type="primary" onClick={() => { void handleAdvancedFilterQuery(); setFilterPopoverOpen(false); }} style={{ flex: 2, height: 44, borderRadius: 10 }}>查询</Button></>}>
                <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                  <Row gutter={12}>
                    <Col span={24}>
                      <Form.Item label="应用" name="appId">
                        <Select allowClear showSearch optionFilterProp="label" placeholder="选择应用" options={appOptions} />
                      </Form.Item>
                    </Col>
                    <Col span={24}>
                      <Form.Item label="类型" name="variableType">
                        <Select allowClear placeholder="变量类型" options={VARIABLE_TYPE_OPTIONS} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Form>
              </M5BottomSheet>
            </>
          ) : (
            <Space size={12}>
              <Space.Compact style={{ width: 360, maxWidth: 'calc(100vw - 120px)' }}>
                <Input placeholder="变量名模糊匹配" allowClear value={variableNameInput} onChange={(e) => setVariableNameInput(e.target.value)} onPressEnter={() => applyVariableNameSearch()} style={{ minWidth: 0 }} />
                <Button type="primary" onClick={() => applyVariableNameSearch()}>搜索</Button>
              </Space.Compact>
              <Popover
                trigger="click"
                placement="bottomLeft"
                open={filterPopoverOpen}
                onOpenChange={(open) => { setFilterPopoverOpen(open); if (open) syncListFilterFormFromListFilters(); }}
                content={
                  <div style={{ width: 420, maxWidth: '90vw' }}>
                    <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                      <Row gutter={16}>
                        <Col span={24}>
                          <Form.Item label="应用" name="appId">
                            <Select allowClear showSearch optionFilterProp="label" placeholder="选择应用" options={appOptions} />
                          </Form.Item>
                        </Col>
                        <Col span={24}>
                          <Form.Item label="类型" name="variableType">
                            <Select allowClear placeholder="变量类型" options={VARIABLE_TYPE_OPTIONS} />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row justify="end" gutter={8} style={{ marginTop: 8 }}>
                        <Col><Button onClick={handleAdvancedFilterReset}>重置</Button></Col>
                        <Col><Button type="primary" onClick={() => void handleAdvancedFilterQuery()}>查询</Button></Col>
                      </Row>
                    </Form>
                  </div>
                }
              >
                <Badge count={activeAdvancedFilterCount} size="small" offset={[-2, 2]}>
                  <Button icon={<FilterOutlined />}>筛选</Button>
                </Badge>
              </Popover>
            </Space>
          )}
        </div>

        {/* 列表 */}
        {isMobile ? (
          <div className="mgmt-mobile-list">
            {loading && <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>加载中...</div>}
            {!loading && variables.length === 0 && <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无数据</div>}
            {variables.map((record) => (
              <div key={record.id} className="mgmt-mobile-card">
                <div className="mgmt-mobile-card-header">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <Text strong style={{ fontSize: 14 }} ellipsis={{ tooltip: record.variableName }}>{record.variableName}</Text>
                      {getTypeTag(record.variableType)}
                      <Tag color={record.enabled ? 'success' : 'default'} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>{record.enabled ? '启用' : '停用'}</Tag>
                    </div>
                    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 2 }}>{record.appName || `AppID:${record.appId}`}</Text>
                    <Text type="secondary" style={{ fontSize: 11, display: 'block', marginTop: 2 }}>{formatTime(record.updatedAt)}</Text>
                  </div>
                  <Space size={0}>
                    <Button type="link" size="small" icon={<EditOutlined />} style={{ fontSize: 12 }}>编辑</Button>
                    <Dropdown menu={{ items: [
                      { key: 'copy', icon: <CopyOutlined />, label: '复制', onClick: () => handleCopyVariable(record) },
                      { key: 'history', icon: <HistoryOutlined />, label: '历史记录', onClick: () => openHistory(record) },
                      { type: 'divider' },
                      { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => { Modal.confirm({ title: '删除变量', content: `确定要删除变量 "${record.variableName}" 吗？`, okText: '确定', okType: 'danger', cancelText: '取消', onOk: () => handleDelete(record.id) }); } },
                    ] }} trigger={['click']}>
                      <Button type="text" size="small" icon={<MoreOutlined />} style={{ width: 32, height: 32 }} />
                    </Dropdown>
                  </Space>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Table
            columns={columns as any}
            dataSource={variables}
            rowKey="id"
            loading={loading}
            size="middle"
            rowSelection={{ selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }}
            pagination={{
              ...pagination,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, pageSize) => fetchVariables(page, pageSize),
            }}
            scroll={{ x: 1400 }}
          />
        )}

        {/* 移动端分页 */}
        {isMobile && pagination.total > 0 && (
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <Pagination current={pagination.current} pageSize={pagination.pageSize} total={pagination.total} onChange={(page, pageSize) => fetchVariables(page, pageSize)} size="small" simple />
          </div>
        )}
      </Space>

      {isMobile ? (
        <M5BottomSheet
          open={editModalVisible}
          onClose={() => setEditModalVisible(false)}
          title={editingVariable ? '编辑变量' : '新建变量'}
          footer={
            <>
              <Button style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => setEditModalVisible(false)}>取消</Button>
              <Button type="primary" style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={handleSubmitEdit}>确定</Button>
            </>
          }
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
                  <InputNumber min={0} placeholder="0" style={{ width: '100%' }} />
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
            <div
              style={{
                marginTop: -6,
                marginBottom: 12,
                border: '1px solid #e5e7eb',
                borderRadius: 8,
                background: '#fafbfc',
                padding: 12,
              }}
            >
              <Row justify="space-between" align="middle" style={{ marginBottom: 10 }}>
                <Button
                  type="text"
                  size="small"
                  icon={templatePanelOpen ? <DownOutlined /> : <RightOutlined />}
                  onClick={() => setTemplatePanelOpen((v) => !v)}
                  style={{ paddingInline: 0, fontWeight: 600 }}
                >
                  快捷插入
                </Button>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {templatePanelOpen ? '点击项可追加到变量值' : '点击左侧展开'}
                </Text>
              </Row>
              {templatePanelOpen ? (
                <>
                  <Segmented
                    size="small"
                    block
                    value={templateGroupTitle}
                    options={VARIABLE_TEMPLATE_GROUPS.map((group) => ({ label: group.title, value: group.title }))}
                    onChange={(value) => setTemplateGroupTitle(String(value))}
                  />
                  <div
                    style={{
                      marginTop: 10,
                      maxHeight: 130,
                      overflowY: 'auto',
                      paddingRight: 4,
                    }}
                  >
                    <Space size={[8, 8]} wrap>
                      {activeTemplateGroup.items.map((item) => (
                        <Tooltip key={item.token} title={`${item.description} | ${item.token}`}>
                          <Tag
                            color="blue"
                            style={{
                              cursor: 'pointer',
                              userSelect: 'none',
                              borderRadius: 4,
                              paddingInline: 8,
                            }}
                            onClick={() => insertTemplateToVariableValue(item.token)}
                          >
                            {item.label}
                          </Tag>
                        </Tooltip>
                      ))}
                    </Space>
                  </div>
                </>
              ) : null}
            </div>
            <div
              style={{
                marginTop: -6,
                marginBottom: 12,
                border: '1px dashed #d0d7de',
                borderRadius: 8,
                padding: 10,
                background: '#fff',
              }}
            >
              <Row justify="space-between" align="middle" style={{ marginBottom: 6 }}>
                <Text strong style={{ fontSize: 13 }}>
                  变量值渲染预览
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  原始值仍为 `${'{...}'}` 模板
                </Text>
              </Row>
              <div style={{ minHeight: 26, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {variableValuePreviewParts.length === 0 ? (
                  <Text type="secondary">暂无内容</Text>
                ) : (
                  variableValuePreviewParts.map((part, idx) =>
                    part.type === 'token' ? (
                      <Tag key={`${part.value}-${idx}`} color="geekblue" style={{ marginInlineEnd: 4 }}>
                        {part.value}
                      </Tag>
                    ) : (
                      <span key={`${part.value}-${idx}`}>{part.value}</span>
                    )
                  )
                )}
              </div>
            </div>

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
        </M5BottomSheet>
      ) : (
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
                  <InputNumber min={0} placeholder="0" style={{ width: '100%' }} />
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
            <div
              style={{
                marginTop: -6,
                marginBottom: 12,
                border: '1px solid #e5e7eb',
                borderRadius: 8,
                background: '#fafbfc',
                padding: 12,
              }}
            >
              <Row justify="space-between" align="middle" style={{ marginBottom: 10 }}>
                <Button
                  type="text"
                  size="small"
                  icon={templatePanelOpen ? <DownOutlined /> : <RightOutlined />}
                  onClick={() => setTemplatePanelOpen((v) => !v)}
                  style={{ paddingInline: 0, fontWeight: 600 }}
                >
                  快捷插入
                </Button>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {templatePanelOpen ? '点击项可追加到变量值' : '点击左侧展开'}
                </Text>
              </Row>
              {templatePanelOpen ? (
                <>
                  <Segmented
                    size="small"
                    block
                    value={templateGroupTitle}
                    options={VARIABLE_TEMPLATE_GROUPS.map((group) => ({ label: group.title, value: group.title }))}
                    onChange={(value) => setTemplateGroupTitle(String(value))}
                  />
                  <div
                    style={{
                      marginTop: 10,
                      maxHeight: 130,
                      overflowY: 'auto',
                      paddingRight: 4,
                    }}
                  >
                    <Space size={[8, 8]} wrap>
                      {activeTemplateGroup.items.map((item) => (
                        <Tooltip key={item.token} title={`${item.description} | ${item.token}`}>
                          <Tag
                            color="blue"
                            style={{
                              cursor: 'pointer',
                              userSelect: 'none',
                              borderRadius: 4,
                              paddingInline: 8,
                            }}
                            onClick={() => insertTemplateToVariableValue(item.token)}
                          >
                            {item.label}
                          </Tag>
                        </Tooltip>
                      ))}
                    </Space>
                  </div>
                </>
              ) : null}
            </div>
            <div
              style={{
                marginTop: -6,
                marginBottom: 12,
                border: '1px dashed #d0d7de',
                borderRadius: 8,
                padding: 10,
                background: '#fff',
              }}
            >
              <Row justify="space-between" align="middle" style={{ marginBottom: 6 }}>
                <Text strong style={{ fontSize: 13 }}>
                  变量值渲染预览
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  原始值仍为 `${'{...}'}` 模板
                </Text>
              </Row>
              <div style={{ minHeight: 26, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {variableValuePreviewParts.length === 0 ? (
                  <Text type="secondary">暂无内容</Text>
                ) : (
                  variableValuePreviewParts.map((part, idx) =>
                    part.type === 'token' ? (
                      <Tag key={`${part.value}-${idx}`} color="geekblue" style={{ marginInlineEnd: 4 }}>
                        {part.value}
                      </Tag>
                    ) : (
                      <span key={`${part.value}-${idx}`}>{part.value}</span>
                    )
                  )
                )}
              </div>
            </div>

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
      )}

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
        width={isMobile ? '100%' : 720}
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

      {isMobile ? (
        <M5BottomSheet
          open={importVisible}
          onClose={() => setImportVisible(false)}
          title="导入变量配置（JSON）"
          footer={
            <>
              <Button style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => setImportVisible(false)}>取消</Button>
              <Button type="primary" style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={handleImport}>导入</Button>
            </>
          }
        >
          <Text type="secondary">
            导入会按变量名执行 upsert：已存在则更新，不存在则自动新增。
          </Text>
          <div style={{ height: 12 }} />
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
            <CodeMirror
              value={importText}
              height="320px"
              extensions={[json()]}
              theme="light"
              style={{
                fontSize: 14,
                fontFamily: 'Consolas, Monaco, monospace',
                fontWeight: 500,
              }}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLine: true,
                foldGutter: true,
              }}
              onChange={(value: string) => setImportText(value)}
            />
          </div>
        </M5BottomSheet>
      ) : (
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
            导入会按变量名执行 upsert：已存在则更新，不存在则自动新增。
          </Text>
          <div style={{ height: 12 }} />
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
            <CodeMirror
              value={importText}
              height="320px"
              extensions={[json()]}
              theme="light"
              style={{
                fontSize: 14,
                fontFamily: 'Consolas, Monaco, monospace',
                fontWeight: 500,
              }}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLine: true,
                foldGutter: true,
              }}
              onChange={(value: string) => setImportText(value)}
            />
          </div>
        </Modal>
      )}

      {isMobile ? (
        <M5BottomSheet
          open={previewVisible}
          onClose={() => setPreviewVisible(false)}
          title={previewAppId ? `变量预览（AppID: ${previewAppId}）` : '变量预览'}
          footer={
            <>
              <Button style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => setPreviewVisible(false)}>关闭</Button>
              <Button type="primary" style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => { navigator.clipboard.writeText(previewText || '').then(() => message.success('已复制到剪贴板')); }}>复制</Button>
            </>
          }
        >
          <Text type="secondary">以下为当前应用变量转换后的 JSON 预览。</Text>
          <div style={{ height: 12 }} />
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
            <CodeMirror
              value={previewText || (previewLoading ? '// 加载中...' : '{}')}
              height="360px"
              extensions={[json()]}
              theme="light"
              editable={false}
              style={{
                fontSize: 14,
                fontFamily: 'Consolas, Monaco, monospace',
                fontWeight: 500,
              }}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLine: true,
                foldGutter: true,
              }}
            />
          </div>
        </M5BottomSheet>
      ) : (
        <Modal
          title={previewAppId ? `变量预览（AppID: ${previewAppId}）` : '变量预览'}
          open={previewVisible}
          onOk={() => {
            navigator.clipboard.writeText(previewText || '').then(() => message.success('已复制到剪贴板'));
          }}
          onCancel={() => setPreviewVisible(false)}
          okText="复制"
          cancelText="关闭"
          width={860}
        >
          <Text type="secondary">以下为当前应用变量转换后的 JSON 预览。</Text>
          <div style={{ height: 12 }} />
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
            <CodeMirror
              value={previewText || (previewLoading ? '// 加载中...' : '{}')}
              height="360px"
              extensions={[json()]}
              theme="light"
              editable={false}
              style={{
                fontSize: 14,
                fontFamily: 'Consolas, Monaco, monospace',
                fontWeight: 500,
              }}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLine: true,
                foldGutter: true,
              }}
            />
          </div>
        </Modal>
      )}

      {isMobile ? (
        <M5BottomSheet
          open={exportVisible}
          onClose={() => setExportVisible(false)}
          title="导出变量配置（JSON）"
          footer={
            <>
              <Button style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => setExportVisible(false)}>关闭</Button>
              <Button type="primary" style={{ flex: 1, height: 44, borderRadius: 10 }} onClick={() => { navigator.clipboard.writeText(exportText || '').then(() => message.success('已复制到剪贴板')); }}>复制</Button>
            </>
          }
        >
          <Text type="secondary">你可以复制后保存到本地，或作为导入模板。</Text>
          <div style={{ height: 12 }} />
          <TextArea
            value={exportText}
            readOnly
            autoSize={{ minRows: 12, maxRows: 18 }}
            style={{ fontFamily: 'Consolas, Monaco, monospace' }}
          />
        </M5BottomSheet>
      ) : (
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
      )}
    </Card>
  );
};

export default AppVariableManagementContent;

