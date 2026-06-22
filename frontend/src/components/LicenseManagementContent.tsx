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
  DatePicker,
  TimePicker,
  Switch,
  Tooltip,
  Popover,
  Checkbox,
  Pagination,
  Grid,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  MoreOutlined,
  PoweroffOutlined,
  CheckCircleOutlined,
  ExportOutlined,
  KeyOutlined,
  ClockCircleOutlined,
  DisconnectOutlined,
  FilterOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getLicenseList,
  createLicense,
  batchCreateLicenses,
  batchAddLicenseTime,
  batchSubtractLicenseTime,
  batchUpdateLicenseStatus,
  batchUnbindLicenses,
  batchSetLicenseUseLimit,
  batchSetLicenseUnbindLimit,
  batchSetLicenseUseTime,
  batchDeleteLicenses,
  updateLicense,
  deleteLicense,
  updateLicenseStatus,
  unbindLicenseDevice,
  unbindLicenseIp,
  exportLicenses,
  type LicenseKey,
  type LicenseKeyDTO,
  type LicenseBatchCreateDTO,
  type LicenseBatchOperateResult,
} from '../services/licenseService';
import { getApplicationList, type Application } from '../services/applicationService';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;
const BATCH_FORM_PRESET_KEY = 'license.batchFormPreset.v1';
const CREATE_FORM_PRESET_KEY = 'license.createFormPreset.v1';

const LicenseManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [licenses, setLicenses] = useState<LicenseKey[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [batchModalVisible, setBatchModalVisible] = useState(false);
  const [batchAddTimeVisible, setBatchAddTimeVisible] = useState(false);
  const [batchSubtractTimeVisible, setBatchSubtractTimeVisible] = useState(false);
  const [batchUnbindVisible, setBatchUnbindVisible] = useState(false);
  const [batchUseLimitVisible, setBatchUseLimitVisible] = useState(false);
  const [batchUnbindLimitVisible, setBatchUnbindLimitVisible] = useState(false);
  const [batchUseTimeVisible, setBatchUseTimeVisible] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [selectedBatchAction, setSelectedBatchAction] = useState<string>();
  const [editingLicense, setEditingLicense] = useState<LicenseKey | null>(null);
  const [form] = Form.useForm();
  const [batchForm] = Form.useForm();
  const [addTimeForm] = Form.useForm();
  const [subtractTimeForm] = Form.useForm();
  const [batchUnbindForm] = Form.useForm();
  const [batchUseLimitForm] = Form.useForm();
  const [batchUnbindLimitForm] = Form.useForm();
  const [batchUseTimeForm] = Form.useForm();
  const [listFilterForm] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [filters, setFilters] = useState<any>({});
  const [keyCodeInput, setKeyCodeInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);
  const [batchFormPreset, setBatchFormPreset] = useState<Record<string, any>>({});
  const [createFormPreset, setCreateFormPreset] = useState<Record<string, any>>({});
  const selectedLicenseIds = selectedRowKeys.map((k) => Number(k));

  const showGeneratedKeysModal = (title: string, keys: string[]) => {
    const list = (keys || []).map((k) => (k ?? '').trim()).filter(Boolean);
    if (!list.length) {
      return;
    }
    const text = list.join('\n');
    const doCopyAll = async () => {
      try {
        await navigator.clipboard.writeText(text);
        message.success('已复制');
      } catch {
        message.warning('复制失败，请手动复制');
      }
    };
    Modal.info({
      title,
      width: 720,
      icon: null,
      closable: true,
      maskClosable: true,
      footer: (
        <div style={{ textAlign: 'right' }}>
          <Button type="primary" onClick={() => void doCopyAll()}>
            一键复制
          </Button>
        </div>
      ),
      content: (
        <div>
          <Space style={{ marginBottom: 12 }} wrap>
            <Text type="secondary">共 {list.length} 条</Text>
          </Space>
          <TextArea
            value={text}
            readOnly
            autoSize={{ minRows: 8, maxRows: 16 }}
            style={{ fontFamily: 'Consolas, Monaco, monospace' }}
            onFocus={(e) => e.target.select()}
          />
        </div>
      ),
    });
  };

  const activeAdvancedFilterCount = [
    filters.appId,
    filters.keyType,
    filters.status,
    filters.isOnline,
    filters.remark,
    filters.batchName,
  ].filter((v) => v !== undefined && v !== null && v !== '').length;

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

  const batchActionOptions = [
    { label: '批量加时', value: 'addTime' },
    { label: '批量扣时', value: 'subtractTime' },
    { label: '批量解绑', value: 'unbind' },
    { label: '批量封禁', value: 'disable' },
    { label: '批量删除', value: 'delete' },
    { label: '批量设置使用次数限制', value: 'setUseLimit' },
    { label: '批量设置解绑次数限制', value: 'setUnbindLimit' },
    { label: '批量设置使用时间段限制', value: 'setUseTime' },
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

  useEffect(() => {
    setKeyCodeInput(filters.keyword ?? '');
  }, [filters.keyword]);

  useEffect(() => {
    try {
      const batchRaw = localStorage.getItem(BATCH_FORM_PRESET_KEY);
      if (batchRaw) {
        const batchParsed = JSON.parse(batchRaw);
        if (batchParsed && typeof batchParsed === 'object') {
          setBatchFormPreset(batchParsed);
        }
      }
      const createRaw = localStorage.getItem(CREATE_FORM_PRESET_KEY);
      if (createRaw) {
        const createParsed = JSON.parse(createRaw);
        if (createParsed && typeof createParsed === 'object') {
          setCreateFormPreset(createParsed);
        }
      }
    } catch {
      /* ignore */
    }
  }, []);

  const syncListFilterFormFromFilters = () => {
    listFilterForm.setFieldsValue({
      appId: filters.appId,
      keyType: filters.keyType,
      status: filters.status,
      isOnline: filters.isOnline,
      remark: filters.remark,
      batchName: filters.batchName,
    });
  };

  const handleAdvancedFilterQuery = async () => {
    const v = await listFilterForm.validateFields();
    const next = { ...filters };
    if (v.appId != null && v.appId !== '') {
      next.appId = v.appId;
    } else {
      delete next.appId;
    }
    if (v.keyType) {
      next.keyType = v.keyType;
    } else {
      delete next.keyType;
    }
    if (v.status != null && v.status !== '') {
      next.status = v.status;
    } else {
      delete next.status;
    }
    if (v.isOnline != null && v.isOnline !== '') {
      next.isOnline = v.isOnline;
    } else {
      delete next.isOnline;
    }
    if (v.remark) {
      next.remark = v.remark.trim();
    } else {
      delete next.remark;
    }
    if (v.batchName) {
      next.batchName = v.batchName.trim();
    } else {
      delete next.batchName;
    }
    setFilters(next);
    setSelectedRowKeys([]);
    setSelectedBatchAction(undefined);
    fetchLicenses(1, pagination.pageSize, next);
    setFilterPopoverOpen(false);
  };

  const handleAdvancedFilterReset = () => {
    listFilterForm.resetFields();
    const next = { ...filters };
    delete next.appId;
    delete next.keyType;
    delete next.status;
    delete next.isOnline;
    delete next.remark;
    delete next.batchName;
    setFilters(next);
    setSelectedRowKeys([]);
    setSelectedBatchAction(undefined);
    fetchLicenses(1, pagination.pageSize, next);
  };

  const applyKeyCodeSearch = (raw?: string) => {
    const trimmed = (raw ?? keyCodeInput).trim();
    const next = { ...filters };
    if (trimmed) {
      next.keyword = trimmed;
    } else {
      delete next.keyword;
    }
    setFilters(next);
    setSelectedRowKeys([]);
    setSelectedBatchAction(undefined);
    fetchLicenses(1, pagination.pageSize, next);
  };

  // 打开创建/编辑弹窗
  const handleOpenModal = (license?: LicenseKey) => {
    setEditingLicense(license || null);
    if (license) {
      form.setFieldsValue({
        appId: license.appId,
        keyType: license.keyType,
        durationValue: license.durationValue,
        expiresAt: license.expiresAt ? dayjs(license.expiresAt) : null,
        useLimit: license.useLimit,
        unbindLimit: license.unbindLimit,
        bindDeviceId: license.bindDeviceId,
        bindIp: license.bindIp,
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
        ...createFormPreset,
      });
      form.setFieldsValue({
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
      ...batchFormPreset,
    });
    batchForm.setFieldsValue({
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
        expiresAt: values.expiresAt ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        useTimeStart: values.useTimeStart ? values.useTimeStart.format('HH:mm:ss') : undefined,
        useTimeEnd: values.useTimeEnd ? values.useTimeEnd.format('HH:mm:ss') : undefined,
        // 编辑时：始终传绑定字段，空值传空字符串以触发后端清空
        bindDeviceId: editingLicense
          ? (values.bindDeviceId == null ? '' : String(values.bindDeviceId).trim())
          : undefined,
        bindIp: editingLicense
          ? (values.bindIp == null ? '' : String(values.bindIp).trim())
          : undefined,
      };

      if (editingLicense) {
        // 类型与时长生成后固定，不参与更新请求
        delete (dto as any).keyType;
        delete (dto as any).durationValue;
        delete (dto as any).durationUnit;
        delete (dto as any).appId;
        await updateLicense(editingLicense.id, dto);
        message.success('卡密更新成功');
      } else {
        const nextCreatePreset = {
          appId: values.appId,
          keyType: values.keyType,
          durationValue: values.durationValue,
          durationUnit: values.durationUnit,
          useLimit: values.useLimit,
          unbindLimit: values.unbindLimit,
          remark: values.remark,
        };
        setCreateFormPreset(nextCreatePreset);
        try {
          localStorage.setItem(CREATE_FORM_PRESET_KEY, JSON.stringify(nextCreatePreset));
        } catch {
          /* ignore */
        }
        const result: any = await createLicense(dto);
        message.success(result?.message || '卡密创建成功');
        const keyCode = result?.data?.keyCode;
        if (keyCode) {
          showGeneratedKeysModal('已生成卡密', [String(keyCode)]);
        }
      }

      setModalVisible(false);
      fetchLicenses(pagination.current, pagination.pageSize, filters);
    } catch (error: any) {
      console.error('操作失败:', error);
      message.error(error.response?.data?.message || '操作失败');
    }
  };

  const openBatchAddTimeModal = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要加时的卡密');
      return;
    }
    addTimeForm.setFieldsValue({ durationValue: 1, durationUnit: 'DAY' });
    setBatchAddTimeVisible(true);
  };

  const openBatchSubtractTimeModal = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要扣时的卡密');
      return;
    }
    subtractTimeForm.setFieldsValue({ durationValue: 1, durationUnit: 'DAY' });
    setBatchSubtractTimeVisible(true);
  };

  const showBatchResult = (title: string, result: LicenseBatchOperateResult | { successCount: number; failCount: number; failures?: Array<{ keyCode?: string; id: number; reason: string }> }) => {
    message.success(`${title}完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`);
    if (result.failures?.length) {
      Modal.warning({
        title: `以下卡密未${title}`,
        width: 600,
        content: (
          <ul style={{ maxHeight: 280, overflow: 'auto', margin: '8px 0 0', paddingLeft: 20 }}>
            {result.failures.map((f, i) => (
              <li key={i} style={{ marginBottom: 4 }}>
                <Tag>{f.keyCode || `#${f.id}`}</Tag>：{f.reason}
              </li>
            ))}
          </ul>
        ),
      });
    }
  };

  const handleBatchActionConfirm = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要操作的卡密');
      return;
    }
    switch (selectedBatchAction) {
      case 'addTime':
        openBatchAddTimeModal();
        break;
      case 'subtractTime':
        openBatchSubtractTimeModal();
        break;
      case 'unbind':
        batchUnbindForm.setFieldsValue({ unbindDevice: true, unbindIp: false });
        setBatchUnbindVisible(true);
        break;
      case 'disable':
        Modal.confirm({
          title: '批量封禁卡密',
          content: `确定封禁当前已勾选的 ${selectedRowKeys.length} 条卡密吗？`,
          okText: '确定封禁',
          cancelText: '取消',
          okType: 'danger',
          onOk: () => void handleBatchDisableSubmit(),
        });
        break;
      case 'setUseLimit':
        batchUseLimitForm.setFieldsValue({ useLimit: 0 });
        setBatchUseLimitVisible(true);
        break;
      case 'setUnbindLimit':
        batchUnbindLimitForm.setFieldsValue({ unbindLimit: 0 });
        setBatchUnbindLimitVisible(true);
        break;
      case 'setUseTime':
        batchUseTimeForm.setFieldsValue({
          clearTimeRange: false,
          useTimeStart: null,
          useTimeEnd: null,
        });
        setBatchUseTimeVisible(true);
        break;
      case 'delete':
        Modal.confirm({
          title: '批量删除卡密',
          content: `确定删除当前已勾选的 ${selectedRowKeys.length} 条卡密吗？此操作不可恢复。`,
          okText: '确定删除',
          cancelText: '取消',
          okType: 'danger',
          onOk: () => void handleBatchDeleteSubmit(),
        });
        break;
      default:
        message.warning('请先选择批量操作');
    }
  };

  const handleBatchDeleteSubmit = async () => {
    try {
      const result: any = await batchDeleteLicenses({ ids: selectedLicenseIds });
      if (result.code === 200 && result.data) {
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult('删除', result.data);
      }
    } catch (error: any) {
      console.error('批量删除失败:', error);
      message.error(error.response?.data?.message || '批量删除失败');
    }
  };

  const handleBatchAddTimeSubmit = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要加时的卡密');
      return;
    }
    try {
      const values = await addTimeForm.validateFields();
      const result: any = await batchAddLicenseTime({
        ids: selectedRowKeys.map((k) => Number(k)),
        durationValue: values.durationValue,
        durationUnit: values.durationUnit,
      });
      if (result.code === 200 && result.data) {
        const r = result.data;
        setBatchAddTimeVisible(false);
        setSelectedRowKeys([]);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        setSelectedBatchAction(undefined);
        showBatchResult('加时', r);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量加时失败:', error);
      message.error(error.response?.data?.message || '批量加时失败');
    }
  };

  const handleBatchSubtractTimeSubmit = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要扣时的卡密');
      return;
    }
    try {
      const values = await subtractTimeForm.validateFields();
      const result: any = await batchSubtractLicenseTime({
        ids: selectedRowKeys.map((k) => Number(k)),
        durationValue: values.durationValue,
        durationUnit: values.durationUnit,
      });
      if (result.code === 200 && result.data) {
        const r = result.data;
        setBatchSubtractTimeVisible(false);
        setSelectedRowKeys([]);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        setSelectedBatchAction(undefined);
        showBatchResult('扣时', r);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量扣时失败:', error);
      message.error(error.response?.data?.message || '批量扣时失败');
    }
  };

  const handleBatchDisableSubmit = async () => {
    try {
      const result: any = await batchUpdateLicenseStatus({
        ids: selectedLicenseIds,
        status: 4,
      });
      if (result.code === 200 && result.data) {
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult('封禁', result.data);
      }
    } catch (error: any) {
      console.error('批量封禁失败:', error);
      message.error(error.response?.data?.message || '批量封禁失败');
    }
  };

  const handleBatchUnbindSubmit = async () => {
    try {
      const values = await batchUnbindForm.validateFields();
      if (!values.unbindDevice && !values.unbindIp) {
        message.warning('请至少选择一种解绑类型');
        return;
      }
      const result: any = await batchUnbindLicenses({
        ids: selectedLicenseIds,
        unbindDevice: values.unbindDevice,
        unbindIp: values.unbindIp,
      });
      if (result.code === 200 && result.data) {
        setBatchUnbindVisible(false);
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult('解绑', result.data);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量解绑失败:', error);
      message.error(error.response?.data?.message || '批量解绑失败');
    }
  };

  const handleBatchUseLimitSubmit = async () => {
    try {
      const values = await batchUseLimitForm.validateFields();
      const result: any = await batchSetLicenseUseLimit({
        ids: selectedLicenseIds,
        useLimit: values.useLimit,
      });
      if (result.code === 200 && result.data) {
        setBatchUseLimitVisible(false);
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult('设置使用次数限制', result.data);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量设置使用次数限制失败:', error);
      message.error(error.response?.data?.message || '批量设置使用次数限制失败');
    }
  };

  const handleBatchUnbindLimitSubmit = async () => {
    try {
      const values = await batchUnbindLimitForm.validateFields();
      const result: any = await batchSetLicenseUnbindLimit({
        ids: selectedLicenseIds,
        unbindLimit: values.unbindLimit,
      });
      if (result.code === 200 && result.data) {
        setBatchUnbindLimitVisible(false);
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult('设置解绑次数限制', result.data);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量设置解绑次数限制失败:', error);
      message.error(error.response?.data?.message || '批量设置解绑次数限制失败');
    }
  };

  const handleBatchUseTimeSubmit = async () => {
    try {
      const values = await batchUseTimeForm.validateFields();
      const clearTimeRange = !!values.clearTimeRange;
      const result: any = await batchSetLicenseUseTime({
        ids: selectedLicenseIds,
        clearTimeRange,
        useTimeStart: clearTimeRange || !values.useTimeStart ? undefined : values.useTimeStart.format('HH:mm:ss'),
        useTimeEnd: clearTimeRange || !values.useTimeEnd ? undefined : values.useTimeEnd.format('HH:mm:ss'),
      });
      if (result.code === 200 && result.data) {
        setBatchUseTimeVisible(false);
        setSelectedRowKeys([]);
        setSelectedBatchAction(undefined);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        showBatchResult(clearTimeRange ? '清空使用时间段限制' : '设置使用时间段限制', result.data);
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      console.error('批量设置使用时间段限制失败:', error);
      message.error(error.response?.data?.message || '批量设置使用时间段限制失败');
    }
  };

  // 提交批量生成
  const handleBatchSubmit = async () => {
    try {
      const values = await batchForm.validateFields();
      const nextPreset = {
        appId: values.appId,
        keyPrefix: values.keyPrefix,
        keyType: values.keyType,
        durationValue: values.durationValue,
        durationUnit: values.durationUnit,
        totalCount: values.totalCount,
        useLimit: values.useLimit,
        unbindLimit: values.unbindLimit,
        remark: values.remark,
      };
      setBatchFormPreset(nextPreset);
      try {
        localStorage.setItem(BATCH_FORM_PRESET_KEY, JSON.stringify(nextPreset));
      } catch {
        /* ignore */
      }
      
      const dto: LicenseBatchCreateDTO = values;

      const result: any = await batchCreateLicenses(dto);
      
      if (result.code === 200) {
        message.success(result?.message || `成功生成 ${values.totalCount} 个卡密`);
        setBatchModalVisible(false);
        fetchLicenses(pagination.current, pagination.pageSize, filters);
        const keys = Array.isArray(result?.data) ? result.data.map((k: any) => k?.keyCode).filter(Boolean) : [];
        if (keys.length) {
          showGeneratedKeysModal('批量生成结果', keys);
        }
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

  const unbindQuotaHint = (record: LicenseKey) => {
    const limit = record.unbindLimit ?? 0;
    const count = record.unbindCount ?? 0;
    if (limit <= 0) {
      return '解绑次数未设上限。每次解绑设备或解绑 IP 各计 1 次解绑次数。';
    }
    return `解绑次数：已用 ${count} / 上限 ${limit}。本次操作将占用 1 次。`;
  };

  const handleUnbindDevice = async (id: number) => {
    try {
      const result: any = await unbindLicenseDevice(id);
      if (result.code === 200) {
        message.success(result.message || '已解绑设备');
        fetchLicenses(pagination.current, pagination.pageSize, filters);
      } else {
        message.error(result.message || '解绑设备失败');
      }
    } catch (error: any) {
      console.error('解绑设备失败:', error);
      message.error(error.response?.data?.message || '解绑设备失败');
    }
  };

  const handleUnbindIp = async (id: number) => {
    try {
      const result: any = await unbindLicenseIp(id);
      if (result.code === 200) {
        message.success(result.message || '已解绑IP');
        fetchLicenses(pagination.current, pagination.pageSize, filters);
      } else {
        message.error(result.message || '解绑IP失败');
      }
    } catch (error: any) {
      console.error('解绑IP失败:', error);
      message.error(error.response?.data?.message || '解绑IP失败');
    }
  };

  const parseExportFilename = (contentDisposition: string | undefined): string | null => {
    if (!contentDisposition) return null;
    const star = contentDisposition.match(/filename\*=UTF-8''([^;\s]+)/i);
    if (star) {
      try {
        return decodeURIComponent(star[1].trim());
      } catch {
        return star[1];
      }
    }
    const quoted = contentDisposition.match(/filename="([^"]+)"/i);
    return quoted ? quoted[1] : null;
  };

  // 导出卡密（后端 Hutool 生成 .xlsx）
  const handleExport = async () => {
    if (selectedLicenseIds.length === 0) {
      message.warning('请先勾选需要导出的卡密');
      return;
    }
    try {
      const res = await exportLicenses({ ...filters, ids: selectedLicenseIds });
      const blob = res.data;
      if (!(blob instanceof Blob)) {
        message.error('导出失败');
        return;
      }
      if (blob.type && blob.type.includes('application/json')) {
        const text = await blob.text();
        try {
          const j = JSON.parse(text);
          message.error(j.message || '导出失败');
        } catch {
          message.error('导出失败');
        }
        return;
      }
      const name =
        parseExportFilename(res.headers['content-disposition']) ||
        `卡密导出_${Date.now()}.xlsx`;
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = name;
      link.click();
      URL.revokeObjectURL(url);
      message.success('导出成功');
      setSelectedRowKeys([]);
      setSelectedBatchAction(undefined);
    } catch (error: any) {
      console.error('导出失败:', error);
      let msg = '导出失败';
      const d = error.response?.data;
      if (d instanceof Blob) {
        try {
          const t = await d.text();
          const j = JSON.parse(t);
          msg = j.message || msg;
        } catch {
          /* ignore */
        }
      }
      message.error(msg);
    }
  };

  // 获取卡密类型标签
  const getKeyTypeLabel = (type: string) => {
    const option = keyTypeOptions.find(opt => opt.value === type);
    return option ? option.label : type;
  };

  // 获取状态Badge
  const getStatusBadge = (status: number) => {
    const map: Record<number, { text: string; status: 'success' | 'processing' | 'error' | 'default' }> = {
      1: { text: '未使用', status: 'default' },
      2: { text: '使用中', status: 'processing' },
      3: { text: '已到期', status: 'error' },
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
      title: '创建来源',
      key: 'creatorType',
      width: 130,
      render: (_: unknown, record: LicenseKey) => {
        if (record.creatorType === 'AGENT') {
          return <Text>{record.agentDisplayName || '-'}</Text>;
        }
        return <Tag color="blue">自己创建</Tag>;
      },
    },
    {
      title: '类型',
      dataIndex: 'keyType',
      key: 'keyType',
      width: 120,
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
      title: '绑定设备',
      dataIndex: 'bindDeviceId',
      key: 'bindDeviceId',
      width: 200,
      ellipsis: true,
      render: (_: string, record: LicenseKey) => {
        const dev = record.bindDeviceId?.trim();
        if (!dev) {
          return <Text type="secondary">-</Text>;
        }
        const ipHint = record.bindIp?.trim()
          ? `绑定 IP：${record.bindIp}`
          : undefined;
        const inner = (
          <Text
            copyable={{ text: dev, tooltips: ['复制设备码', '已复制'] }}
            style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12 }}
          >
            {dev}
          </Text>
        );
        return ipHint ? <Tooltip title={ipHint}>{inner}</Tooltip> : inner;
      },
    },
    {
      title: 'IP区域',
      dataIndex: 'bindIpRegion',
      key: 'bindIpRegion',
      width: 170,
      ellipsis: true as const,
      render: (text: string) => <Text>{text || '-'}</Text>,
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
      title: '解绑次数',
      key: 'unbindCount',
      width: 110,
      align: 'center' as const,
      render: (_: any, record: LicenseKey) => (
        <Text style={{ fontSize: 12 }}>
          {record.unbindCount ?? 0} / {(record.unbindLimit ?? 0) === 0 ? '∞' : record.unbindLimit}
        </Text>
      ),
    },
    {
      title: '批次',
      dataIndex: 'batchName',
      key: 'batchName',
      width: 140,
      ellipsis: true as const,
      render: (text: string) => <Text>{text || '-'}</Text>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      width: 180,
      ellipsis: true as const,
      render: (text: string) => <Text>{text || '-'}</Text>,
    },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 130,
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
      title: '激活时间',
      dataIndex: 'firstUsedAt',
      key: 'firstUsedAt',
      width: 130,
      render: (text: string) => (
        text ? (
          <Text style={{ fontSize: 12 }}>
            {new Date(text).toLocaleString('zh-CN', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
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
        ];
        if (record.bindDeviceId?.trim()) {
          menuItems.push({
            key: 'unbindDevice',
            icon: <DisconnectOutlined />,
            label: '解绑设备',
            onClick: () => {
              Modal.confirm({
                title: '解绑设备',
                content: (
                  <div>
                    <p>
                      确定解绑卡密「{record.keyCode}」的当前设备吗？解绑后用户可使用新设备再次通过卡密登录完成绑定（需该卡密开启设备校验）。
                    </p>
                    <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>{unbindQuotaHint(record)}</p>
                    <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>
                      管理员解绑不会缩短卡密到期时间；若应用开启换绑扣时，仅三方接口换绑设备时会扣时。
                    </p>
                  </div>
                ),
                okText: '解绑',
                cancelText: '取消',
                onOk: () => handleUnbindDevice(record.id),
              });
            },
          });
        }
        if (record.bindIp?.trim()) {
          menuItems.push({
            key: 'unbindIp',
            icon: <DisconnectOutlined />,
            label: '解绑IP',
            onClick: () => {
              Modal.confirm({
                title: '解绑IP',
                content: (
                  <div>
                    <p>
                      确定解绑卡密「{record.keyCode}」的当前绑定 IP 吗？解绑后用户可在新 IP 下再次登录绑定（需该卡密开启 IP 校验）。
                    </p>
                    <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>{unbindQuotaHint(record)}</p>
                    <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>
                      管理员解绑不会缩短卡密到期时间；若应用开启换绑扣时，仅三方接口换绑设备时会扣时。
                    </p>
                  </div>
                ),
                okText: '解绑',
                cancelText: '取消',
                onOk: () => handleUnbindIp(record.id),
              });
            },
          });
        }
        menuItems.push(
          { type: 'divider' },
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
          }
        );

        return (
          <Space size="small">
            {!isMobile && (
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleOpenModal(record)}
              >
                编辑
              </Button>
            )}
            <Dropdown menu={{ items: menuItems }} trigger={['click']}>
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  const MOBILE_VISIBLE_KEYS = ['keyCode', 'keyType', 'status', 'isOnline', 'action'];
  const displayColumns = isMobile
    ? columns.filter((c) => MOBILE_VISIBLE_KEYS.includes(c.key as string))
    : columns;

  const getLicenseMenuItems = (record: LicenseKey): MenuProps['items'] => {
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
    ];
    if (record.bindDeviceId?.trim()) {
      menuItems.push({
        key: 'unbindDevice',
        icon: <DisconnectOutlined />,
        label: '解绑设备',
        onClick: () => {
          Modal.confirm({
            title: '解绑设备',
            content: (
              <div>
                <p>
                  确定解绑卡密「{record.keyCode}」的当前设备吗？
                </p>
                <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>{unbindQuotaHint(record)}</p>
              </div>
            ),
            okText: '解绑',
            cancelText: '取消',
            onOk: () => handleUnbindDevice(record.id),
          });
        },
      });
    }
    if (record.bindIp?.trim()) {
      menuItems.push({
        key: 'unbindIp',
        icon: <DisconnectOutlined />,
        label: '解绑IP',
        onClick: () => {
          Modal.confirm({
            title: '解绑IP',
            content: (
              <div>
                <p>
                  确定解绑卡密「{record.keyCode}」的当前绑定 IP 吗？
                </p>
                <p style={{ marginTop: 8, color: '#666', fontSize: 12 }}>{unbindQuotaHint(record)}</p>
              </div>
            ),
            okText: '解绑',
            cancelText: '取消',
            onOk: () => handleUnbindIp(record.id),
          });
        },
      });
    }
    menuItems.push(
      { type: 'divider' },
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
      }
    );
    return menuItems;
  };

  const renderMobileLicenseCard = (record: LicenseKey) => {
    const statusMap: Record<number, { text: string; color: string }> = {
      1: { text: '未使用', color: 'default' },
      2: { text: '使用中', color: 'processing' },
      3: { text: '已到期', color: 'error' },
      4: { text: '已禁用', color: 'error' },
    };
    const statusInfo = statusMap[record.status] || { text: '未知', color: 'default' };

    let typeLabel = getKeyTypeLabel(record.keyType);
    if (record.keyType === 'CUSTOM' && record.durationValue && record.durationUnit) {
      const unitMap: Record<string, string> = { 'HOUR': '小时', 'DAY': '天', 'MONTH': '月', 'YEAR': '年' };
      typeLabel = `${record.durationValue}${unitMap[record.durationUnit] || record.durationUnit}`;
    } else if (record.keyType !== 'PERMANENT' && record.durationValue) {
      typeLabel = `${record.durationValue}x${typeLabel}`;
    }

    const isExpired = record.expiresAt && new Date(record.expiresAt) < new Date();

    return (
      <div
        key={record.id}
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid #f0f0f0',
          background: '#fff',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
          <div style={{ flex: 1, minWidth: 0, marginRight: 8 }}>
            <Text
              copyable={{ text: record.keyCode, tooltips: ['复制', '已复制'] }}
              style={{
                fontFamily: 'Consolas, Monaco, monospace',
                fontSize: 14,
                fontWeight: 500,
                color: '#1a1a1a',
              }}
            >
              {record.keyCode}
            </Text>
          </div>
          <Dropdown menu={{ items: getLicenseMenuItems(record) }} trigger={['click']}>
            <Button type="text" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
          <Badge status={statusInfo.color as any} text={<span style={{ fontSize: 12 }}>{statusInfo.text}</span>} />
          {record.isOnline && <Tag color="success" style={{ margin: 0, fontSize: 11, lineHeight: '18px', padding: '0 6px' }}>在线</Tag>}
          <Tag color="blue" style={{ margin: 0, fontSize: 11, lineHeight: '18px', padding: '0 6px' }}>{typeLabel}</Tag>
          {record.appName && <Tag style={{ margin: 0, fontSize: 11, lineHeight: '18px', padding: '0 6px' }}>{record.appName}</Tag>}
        </div>

        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 12, color: '#8c8c8c' }}>
          {record.expiresAt && (
            <span style={{ color: isExpired ? '#ff4d4f' : undefined }}>
              {isExpired ? '已过期' : '到期'} {dayjs(record.expiresAt).format('MM/DD HH:mm')}
            </span>
          )}
          <span>使用 {record.useCount}/{record.useLimit === 0 ? '∞' : record.useLimit}</span>
          {(record.unbindLimit ?? 0) > 0 && (
            <span>解绑 {record.unbindCount ?? 0}/{record.unbindLimit}</span>
          )}
          {record.bindDeviceId?.trim() && <span style={{ color: '#fa8c16' }}>已绑设备</span>}
          {record.bindIp?.trim() && <span style={{ color: '#1890ff' }}>已绑IP</span>}
        </div>
      </div>
    );
  };

  return (
    <Card>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 标题和操作栏 */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>卡密管理</Title>
          {isMobile ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
              <Dropdown
                menu={{
                  items: [
                    { key: 'batch', icon: <KeyOutlined />, label: '批量生成', onClick: handleOpenBatchModal },
                    { key: 'export', icon: <ExportOutlined />, label: '导出', onClick: handleExport },
                  ],
                }}
                trigger={['click']}
              >
                <Button size="small" icon={<MoreOutlined />}>更多</Button>
              </Dropdown>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => fetchLicenses(pagination.current, pagination.pageSize, filters)}
               
              >
                刷新
              </Button>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => handleOpenModal()}
               
              >
                创建
              </Button>
            </div>
          ) : (
            <Space>
              <Button icon={<ExportOutlined />} onClick={handleExport}>导出</Button>
              <Button icon={<ReloadOutlined />} onClick={() => fetchLicenses(pagination.current, pagination.pageSize, filters)}>刷新</Button>
              <Button icon={<KeyOutlined />} onClick={handleOpenBatchModal}>批量生成</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenModal()}>创建卡密</Button>
            </Space>
          )}
        </div>

        {/* 主搜索 + 高级筛选 */}
        <div style={isMobile ? { display: 'flex', gap: 8, alignItems: 'center' } : undefined}>
          {isMobile ? (
            <>
              <Input
                placeholder="搜索卡密"
                allowClear
                value={keyCodeInput}
                onChange={(e) => setKeyCodeInput(e.target.value)}
                onPressEnter={() => applyKeyCodeSearch()}
                style={{ flex: 1, minWidth: 0 }}
                size="small"
              />
              <Button type="primary" onClick={() => applyKeyCodeSearch()} style={{ flexShrink: 0 }}>搜索</Button>
              <Popover
                trigger="click"
                placement="bottomRight"
                open={filterPopoverOpen}
                onOpenChange={(open) => { setFilterPopoverOpen(open); if (open) syncListFilterFormFromFilters(); }}
                content={
                  <div style={{ width: 320, maxWidth: '90vw' }}>
                    <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                      <Row gutter={12}>
                        <Col span={12}>
                          <Form.Item label="应用" name="appId">
                            <Select allowClear placeholder="选择应用" options={applications.map((app) => ({ label: app.appName, value: app.id }))} />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="卡密类型" name="keyType">
                            <Select allowClear placeholder="卡密类型" options={keyTypeOptions.map((opt) => ({ label: opt.label, value: opt.value }))} />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="批次名称" name="batchName">
                            <Input allowClear placeholder="模糊匹配" />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="备注" name="remark">
                            <Input allowClear placeholder="模糊匹配" />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="状态" name="status">
                            <Select allowClear placeholder="状态" options={[{ label: '未使用', value: 1 }, { label: '使用中', value: 2 }, { label: '已到期', value: 3 }, { label: '已禁用', value: 4 }]} />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="在线状态" name="isOnline">
                            <Select allowClear placeholder="在线状态" options={[{ label: '在线', value: true }, { label: '离线', value: false }]} />
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
                  <Button icon={<FilterOutlined />} style={{ flexShrink: 0 }}>筛选</Button>
                </Badge>
              </Popover>
            </>
          ) : (
            <Space size={12}>
              <Space.Compact style={{ width: 360, maxWidth: 'calc(100vw - 120px)' }}>
                <Input placeholder="搜索卡密" allowClear value={keyCodeInput} onChange={(e) => setKeyCodeInput(e.target.value)} onPressEnter={() => applyKeyCodeSearch()} style={{ minWidth: 0 }} />
                <Button type="primary" onClick={() => applyKeyCodeSearch()}>搜索</Button>
              </Space.Compact>
              <Popover
                trigger="click"
                placement="bottomLeft"
                open={filterPopoverOpen}
                onOpenChange={(open) => {
                  setFilterPopoverOpen(open);
                  if (open) {
                    syncListFilterFormFromFilters();
                  }
                }}
                content={
                  <div style={{ width: 420, maxWidth: '90vw' }}>
                    <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                      <Row gutter={16}>
                        <Col span={12}>
                          <Form.Item label="应用" name="appId">
                            <Select
                              allowClear
                              placeholder="选择应用"
                              options={applications.map((app) => ({
                                label: app.appName,
                                value: app.id,
                              }))}
                            />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="卡密类型" name="keyType">
                            <Select
                              allowClear
                              placeholder="卡密类型"
                              options={keyTypeOptions.map((opt) => ({
                                label: opt.label,
                                value: opt.value,
                              }))}
                            />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="批次名称" name="batchName">
                            <Input allowClear placeholder="模糊匹配批次名称" />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="备注" name="remark">
                            <Input allowClear placeholder="模糊匹配备注" />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="状态" name="status">
                            <Select
                              allowClear
                              placeholder="状态"
                              options={[
                                { label: '未使用', value: 1 },
                                { label: '使用中', value: 2 },
                                { label: '已到期', value: 3 },
                                { label: '已禁用', value: 4 },
                              ]}
                            />
                          </Form.Item>
                        </Col>
                        <Col span={12}>
                          <Form.Item label="在线状态" name="isOnline">
                            <Select
                              allowClear
                              placeholder="在线状态"
                              options={[
                                { label: '在线', value: true },
                                { label: '离线', value: false },
                              ]}
                            />
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
            </Space>
          )}
        </div>

        {/* 卡密列表 */}
        {isMobile ? (
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, overflow: 'hidden', background: '#fff' }}>
            {loading ? (
              <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>加载中...</div>
            ) : licenses.length === 0 ? (
              <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>暂无数据</div>
            ) : (
              licenses.map(renderMobileLicenseCard)
            )}
          </div>
        ) : (
          <Table
            columns={displayColumns}
            dataSource={licenses}
            rowKey="id"
            loading={loading}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
            pagination={false}
            scroll={{ x: 2000 }}
            size="middle"
          />
        )}
        <Row justify="space-between" align="middle" wrap gutter={[12, 12]}>
          <Col flex="none">
            <Space wrap size={isMobile ? 'small' : 'middle'}>
              <Select
                placeholder="批量操作"
                allowClear
                style={{ width: isMobile ? 140 : 220 }}
                size={isMobile ? 'small' : 'middle'}
                value={selectedBatchAction}
                onChange={(value) => setSelectedBatchAction(value)}
                options={batchActionOptions}
              />
              <Button
                size={isMobile ? 'small' : 'middle'}
                icon={<ClockCircleOutlined />}
                disabled={selectedRowKeys.length === 0 || !selectedBatchAction}
                onClick={handleBatchActionConfirm}
              >
                确定
              </Button>
              <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>
                已选 {selectedRowKeys.length} 条
              </Text>
            </Space>
          </Col>
          <Col flex="none">
            <Pagination
              {...pagination}
              size={isMobile ? 'small' : 'default'}
              showSizeChanger={!isMobile}
              pageSizeOptions={['10', '20', '50', '100', '200', '400']}
              showQuickJumper={!isMobile}
              simple={isMobile}
              showTotal={isMobile ? undefined : (total) => `共 ${total} 条`}
              onChange={(page, pageSize) => {
                fetchLicenses(page, pageSize, filters);
              }}
            />
          </Col>
        </Row>
      </Space>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editingLicense ? '编辑卡密' : '创建卡密'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={isMobile ? '100%' : 700}
        okText="确定"
        cancelText="取消"
        className={isMobile ? 'mobile-modal' : undefined}
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
              const durationLocked = !!editingLicense;
              
              // 如果是自定义类型，显示自定义时长配置
              if (keyType === 'CUSTOM') {
                return (
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        label="时长数值"
                        name="durationValue"
                        rules={[{ required: !durationLocked, message: '请输入时长数值' }]}
                      >
                        <InputNumber 
                          min={1} 
                          style={{ width: '100%' }} 
                          placeholder="例如：3、100、365"
                          disabled={durationLocked}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        label="时长单位"
                        name="durationUnit"
                        rules={[{ required: !durationLocked, message: '请选择时长单位' }]}
                      >
                        <Select placeholder="选择单位" disabled={durationLocked}>
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
                    rules={[{ required: !durationLocked, message: '请输入倍数' }]}
                  >
                    <InputNumber 
                      min={1} 
                      max={999}
                      style={{ width: '100%' }} 
                      placeholder="输入倍数，例如：1、3、5、10"
                      disabled={durationLocked}
                    />
                  </Form.Item>
                );
              }
              
              return null;
            }}
          </Form.Item>

          {editingLicense && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="到期时间"
                  name="expiresAt"
                  tooltip="直接修改该卡密的实际到期时间"
                >
                  <DatePicker
                    showTime
                    format="YYYY-MM-DD HH:mm:ss"
                    style={{ width: '100%' }}
                    placeholder="选择到期日期和时间"
                  />
                </Form.Item>
              </Col>
            </Row>
          )}

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

          {editingLicense && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="绑定设备"
                  name="bindDeviceId"
                  tooltip="可修改或清空。清空后该卡密下次登录可重新绑定设备（开启设备校验时生效）。"
                >
                  <Input allowClear placeholder="输入设备ID，留空表示清空" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="绑定IP"
                  name="bindIp"
                  tooltip="可修改或清空。清空后该卡密下次登录可重新绑定IP（开启IP校验时生效）。"
                >
                  <Input allowClear placeholder="输入IP，留空表示清空" />
                </Form.Item>
              </Col>
            </Row>
          )}

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
        width={isMobile ? '100%' : 700}
        okText="生成"
        cancelText="取消"
        className={isMobile ? 'mobile-modal' : undefined}
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
                label={
                  <Space size={6}>
                    <span>卡密前缀</span>
                    <Tooltip title="可选。仅作为前缀拼接到自动生成的随机后缀前。只能包含字母/数字，且长度必须小于16位（不支持指定整条卡密）。">
                      <KeyOutlined />
                    </Tooltip>
                  </Space>
                }
                name="keyPrefix"
                rules={[
                  { pattern: /^[A-Za-z0-9]*$/, message: '前缀只能包含字母和数字' },
                  { max: 15, message: '前缀最长15位（总长度固定16位）' },
                ]}
                getValueFromEvent={(e) => (e?.target?.value ?? '').toUpperCase()}
              >
                <Input placeholder="例如：VIP（可留空）" maxLength={15} />
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
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="生成数量"
                name="totalCount"
                rules={[{ required: true, message: '请输入生成数量' }]}
              >
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12} />
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

      <Modal
        title="批量解绑"
        open={batchUnbindVisible}
        onOk={handleBatchUnbindSubmit}
        onCancel={() => setBatchUnbindVisible(false)}
        okText="确定解绑"
        cancelText="取消"
        width={isMobile ? '100%' : 520}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密执行解绑操作。
        </Paragraph>
        <Form form={batchUnbindForm} layout="vertical">
          <Form.Item label="解绑类型">
            <Space direction="vertical">
              <Form.Item name="unbindDevice" valuePropName="checked" noStyle>
                <Checkbox>解绑设备</Checkbox>
              </Form.Item>
              <Form.Item name="unbindIp" valuePropName="checked" noStyle>
                <Checkbox>解绑 IP</Checkbox>
              </Form.Item>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量设置使用次数限制"
        open={batchUseLimitVisible}
        onOk={handleBatchUseLimitSubmit}
        onCancel={() => setBatchUseLimitVisible(false)}
        okText="确定设置"
        cancelText="取消"
        width={isMobile ? '100%' : 520}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密统一设置使用次数限制。
          输入 <Text code>0</Text> 表示不限制。
        </Paragraph>
        <Form form={batchUseLimitForm} layout="vertical">
          <Form.Item
            label="使用次数限制"
            name="useLimit"
            rules={[{ required: true, message: '请输入使用次数限制' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量设置解绑次数限制"
        open={batchUnbindLimitVisible}
        onOk={handleBatchUnbindLimitSubmit}
        onCancel={() => setBatchUnbindLimitVisible(false)}
        okText="确定设置"
        cancelText="取消"
        width={isMobile ? '100%' : 520}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密统一设置解绑次数限制。
          输入 <Text code>0</Text> 表示不限制。
        </Paragraph>
        <Form form={batchUnbindLimitForm} layout="vertical">
          <Form.Item
            label="解绑次数限制"
            name="unbindLimit"
            rules={[{ required: true, message: '请输入解绑次数限制' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="0=不限" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量设置使用时间段限制"
        open={batchUseTimeVisible}
        onOk={handleBatchUseTimeSubmit}
        onCancel={() => setBatchUseTimeVisible(false)}
        okText="确定设置"
        cancelText="取消"
        width={isMobile ? '100%' : 560}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密统一设置可使用时间段。
        </Paragraph>
        <Form form={batchUseTimeForm} layout="vertical">
          <Form.Item name="clearTimeRange" valuePropName="checked">
            <Checkbox>清空时间段限制</Checkbox>
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) => {
              const clearTimeRange = !!getFieldValue('clearTimeRange');
              return (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      label="开始时间"
                      name="useTimeStart"
                      rules={clearTimeRange ? [] : [{ required: true, message: '请选择开始时间' }]}
                    >
                      <TimePicker
                        format="HH:mm:ss"
                        style={{ width: '100%' }}
                        disabled={clearTimeRange}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      label="结束时间"
                      name="useTimeEnd"
                      rules={clearTimeRange ? [] : [{ required: true, message: '请选择结束时间' }]}
                    >
                      <TimePicker
                        format="HH:mm:ss"
                        style={{ width: '100%' }}
                        disabled={clearTimeRange}
                      />
                    </Form.Item>
                  </Col>
                </Row>
              );
            }}
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量加时"
        open={batchAddTimeVisible}
        onOk={handleBatchAddTimeSubmit}
        onCancel={() => setBatchAddTimeVisible(false)}
        okText="确定加时"
        cancelText="取消"
        width={isMobile ? '100%' : 520}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密延长到期时间。
          仅<strong>已激活</strong>（已首次使用）且有到期时间的卡密会生效；未激活的会在结果中提示「该卡密未激活」。
        </Paragraph>
        <Form form={addTimeForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="延长数值"
                name="durationValue"
                rules={[{ required: true, message: '请输入延长数值' }]}
              >
                <InputNumber min={1} max={99999} style={{ width: '100%' }} placeholder="正整数" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="单位"
                name="durationUnit"
                rules={[{ required: true, message: '请选择单位' }]}
              >
                <Select placeholder="选择单位">
                  <Option value="MINUTE">分钟</Option>
                  <Option value="HOUR">小时</Option>
                  <Option value="DAY">天</Option>
                  <Option value="WEEK">周</Option>
                  <Option value="MONTH">月</Option>
                  <Option value="YEAR">年</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
      <Modal
        title="批量扣时"
        open={batchSubtractTimeVisible}
        onOk={handleBatchSubtractTimeSubmit}
        onCancel={() => setBatchSubtractTimeVisible(false)}
        okText="确定扣时"
        cancelText="取消"
        width={isMobile ? '100%' : 520}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 条卡密扣减到期时间。
          仅<strong>已激活</strong>（已首次使用）且有到期时间的卡密会生效；未激活的会在结果中提示「该卡密未激活」。
        </Paragraph>
        <Form form={subtractTimeForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="扣减数值"
                name="durationValue"
                rules={[{ required: true, message: '请输入扣减数值' }]}
              >
                <InputNumber min={1} max={99999} style={{ width: '100%' }} placeholder="正整数" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="单位"
                name="durationUnit"
                rules={[{ required: true, message: '请选择单位' }]}
              >
                <Select placeholder="选择单位">
                  <Option value="MINUTE">分钟</Option>
                  <Option value="HOUR">小时</Option>
                  <Option value="DAY">天</Option>
                  <Option value="WEEK">周</Option>
                  <Option value="MONTH">月</Option>
                  <Option value="YEAR">年</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Card>
  );
};

export default LicenseManagementContent;
