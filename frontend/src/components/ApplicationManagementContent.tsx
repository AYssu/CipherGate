import React, { useState, useEffect } from 'react';
import { useOutletContext } from 'react-router-dom';
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
  Divider,
  Badge,
  Dropdown,
  Upload,
  Switch,
  Grid,
  Pagination,
  type MenuProps,
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
  MoreOutlined,
  PoweroffOutlined,
  CheckCircleOutlined,
  ApiOutlined,
  RocketOutlined,
  UserAddOutlined,
  CloudOutlined,
  DatabaseOutlined,
  SafetyOutlined,
  UploadOutlined,
  SlidersOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import {
  getApplicationList,
  createApplication,
  updateApplication,
  deleteApplication,
  resetAppKeys,
  updateApplicationStatus,
  getEncryptionConfig,
  updateEncryptionConfig,
  uploadApplicationUpdatePackage,
  type Application,
  type ApplicationDTO,
} from '../services/applicationService';
import {
  listAppAgents,
  createAppAgent,
  updateAppAgent,
  updateAppAgentPermissions,
  updateAppAgentQuotas,
  lookupAppAgentBindUser,
  type AppAgentDTO,
  type AgentBindUserDTO,
} from '../services/appAgentService';
import { applicationEpayConfigApi } from '../services/applicationEpayConfigService';
import { pricingPlanApi } from '../services/pricingPlanService';

const { Title, Text } = Typography;
const { TextArea } = Input;

const ApplicationManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const { userInfo } = useOutletContext<{ userInfo: any }>();
  const isSuperAdmin = userInfo?.roles?.some((r: any) => r.roleCode === 'SUPER_ADMIN');
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
  const [encryptionModalVisible, setEncryptionModalVisible] = useState(false);
  const [encryptionApp, setEncryptionApp] = useState<Application | null>(null);
  const [encryptionConfigJson, setEncryptionConfigJson] = useState('{}');
  const [encryptionLoading, setEncryptionLoading] = useState(false);
  const [encryptionSaving, setEncryptionSaving] = useState(false);
  const [updatePackageUploading, setUpdatePackageUploading] = useState(false);
  const [agentModalVisible, setAgentModalVisible] = useState(false);
  const [agentLoading, setAgentLoading] = useState(false);
  const [agentSaving, setAgentSaving] = useState(false);
  const [agentApp, setAgentApp] = useState<Application | null>(null);
  const [agentList, setAgentList] = useState<AppAgentDTO[]>([]);
  const [editingAgent, setEditingAgent] = useState<AppAgentDTO | null>(null);
  const [agentForm] = Form.useForm();
  const [bindUser, setBindUser] = useState<AgentBindUserDTO | null>(null);
  const [bindGithubId, setBindGithubId] = useState('');
  const [bindLookupLoading, setBindLookupLoading] = useState(false);
  const [paymentModalVisible, setPaymentModalVisible] = useState(false);
  const [paymentApp, setPaymentApp] = useState<Application | null>(null);
  const [paymentConfig, setPaymentConfig] = useState<any>(null);
  const [paymentSaving, setPaymentSaving] = useState(false);
  const [pricingModalVisible, setPricingModalVisible] = useState(false);
  const [pricingApp, setPricingApp] = useState<Application | null>(null);
  const [pricingList, setPricingList] = useState<any[]>([]);
  const [pricingLoading, setPricingLoading] = useState(false);
  const [pricingForm] = Form.useForm();
  const [editingPricing, setEditingPricing] = useState<any>(null);
  const [showPricingForm, setShowPricingForm] = useState(false);

  const getEncryptionJsonError = (raw: string): string | null => {
    const trimmed = (raw || '').trim();
    if (!trimmed) {
      return null;
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return '加密配置须为 JSON 对象，例如 {}';
      }
      return null;
    } catch {
      return 'JSON 格式不正确，请检查括号与引号';
    }
  };
  const encryptionJsonError = getEncryptionJsonError(encryptionConfigJson);

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

  const loadPricingPlans = async (appId: number) => {
    setPricingLoading(true);
    try {
      const res: any = await pricingPlanApi.list(appId);
      setPricingList(res?.data || []);
    } catch {
      // handled
    } finally {
      setPricingLoading(false);
    }
  };

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
        unbindTimeDeductMode: app.unbindTimeDeductMode ?? 'NONE',
        unbindTimeDeductValue:
          app.unbindTimeDeductValue != null ? Number(app.unbindTimeDeductValue) : undefined,
        unbindCooldownHours:
          app.unbindCooldownHours != null ? Number(app.unbindCooldownHours) : 0,
      });
    } else {
      form.resetFields();
    }
    setModalVisible(true);
  };

  const handleUploadUpdatePackage = async (file: File) => {
    if (!editingApp) return;
    setUpdatePackageUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const res: any = await uploadApplicationUpdatePackage(editingApp.id, fd);
      if (res.code === 200 && res.data) {
        message.success('更新包已上传到 MinIO');
        setEditingApp({ ...editingApp, ...res.data });
        fetchApplications(pagination.current, pagination.pageSize);
      } else {
        message.error(res.message || '上传失败');
      }
    } catch (error) {
      console.error(error);
    } finally {
      setUpdatePackageUploading(false);
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (values.unbindTimeDeductMode === 'NONE') {
        values.unbindTimeDeductValue = undefined;
      }
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

  const openEncryptionConfigModal = async (record: Application) => {
    setEncryptionApp(record);
    setEncryptionModalVisible(true);
    setEncryptionLoading(true);
    setEncryptionConfigJson('{}');
    try {
      const res: any = await getEncryptionConfig(record.id);
      if (res.code === 200 && res.data && typeof res.data === 'object') {
        setEncryptionConfigJson(JSON.stringify(res.data, null, 2));
      } else {
        setEncryptionConfigJson('{}');
      }
    } catch (e) {
      console.error(e);
      message.error('加载加密配置失败');
      setEncryptionConfigJson('{}');
    } finally {
      setEncryptionLoading(false);
    }
  };

  const handleSaveEncryptionConfig = async () => {
    if (!encryptionApp) return;
    let parsed: Record<string, unknown>;
    try {
      const raw = (encryptionConfigJson || '').trim();
      parsed = raw === '' ? {} : JSON.parse(raw);
    } catch {
      message.error('JSON 格式不正确，请检查括号与引号');
      return;
    }
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      message.error('加密配置须为 JSON 对象，例如 {}');
      return;
    }
    setEncryptionSaving(true);
    try {
      const res: any = await updateEncryptionConfig(encryptionApp.id, parsed as Record<string, any>);
      if (res.code === 200) {
        message.success('加密配置已保存');
        setEncryptionModalVisible(false);
        setEncryptionApp(null);
        fetchApplications(pagination.current, pagination.pageSize);
      } else {
        message.error(res.message || '保存失败');
      }
    } catch (e) {
      console.error(e);
      message.error('保存加密配置失败');
    } finally {
      setEncryptionSaving(false);
    }
  };

  // 切换密钥显示
  const toggleShowSecret = (id: number) => {
    setShowSecret(prev => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  const AGENT_PERMISSION_OPTIONS = [
    { label: '卡密查看', value: 'LICENSE_LIST' },
    { label: '卡密创建', value: 'LICENSE_CREATE' },
    { label: '卡密修改', value: 'LICENSE_UPDATE' },
    { label: '卡密删除', value: 'LICENSE_DELETE' },
    { label: '查看应用全部卡密', value: 'LICENSE_VIEW_ALL' },
    { label: '终端用户查看', value: 'APP_USER_LIST' },
    { label: '终端用户创建', value: 'APP_USER_CREATE' },
    { label: '终端用户修改', value: 'APP_USER_UPDATE' },
    { label: '终端用户删除', value: 'APP_USER_DELETE' },
    { label: '查看应用全部终端用户', value: 'APP_USER_VIEW_ALL' },
  ];

  const loadAppAgents = async (appId: number) => {
    setAgentLoading(true);
    try {
      const res: any = await listAppAgents(appId);
      if (res.code === 200) {
        setAgentList(res.data || []);
      } else {
        message.error(res.message || '加载代理配置失败');
      }
    } finally {
      setAgentLoading(false);
    }
  };

  const openAgentModal = async (app: Application) => {
    setAgentApp(app);
    setEditingAgent(null);
    agentForm.resetFields();
    setBindGithubId('');
    setBindUser(null);
    setAgentModalVisible(true);
    try {
      await loadAppAgents(app.id);
    } catch (e) {
      console.error(e);
      message.error('加载代理配置失败');
    }
  };

  const parseQuotaText = (text: string | undefined): Record<string, number> => {
    const out: Record<string, number> = {};
    const raw = (text || '').trim();
    if (!raw) {
      return out;
    }
    raw.split('\n').forEach((line) => {
      const s = line.trim();
      if (!s) return;
      const parts = s.split(':');
      if (parts.length !== 2) return;
      const keyType = parts[0].trim().toUpperCase();
      const total = Number(parts[1].trim());
      if (keyType && Number.isFinite(total) && total >= 0) {
        out[keyType] = total;
      }
    });
    return out;
  };

  const toQuotaText = (quotas?: Record<string, number>) => {
    if (!quotas) return '';
    return Object.entries(quotas)
      .map(([k, v]) => `${k}:${v}`)
      .join('\n');
  };

  const onEditAgent = (agent: AppAgentDTO) => {
    setEditingAgent(agent);
    setBindUser(agent.userId ? { id: agent.userId, githubId: '', name: `#${agent.userId}` } : null);
    setBindGithubId('');
    agentForm.setFieldsValue({
      agentCode: agent.agentCode,
      userId: agent.userId,
      scopeMode: agent.scopeMode || 'OWN_ONLY',
      enabled: agent.enabled ?? true,
      permissions: agent.permissions || [],
      quotaText: toQuotaText(agent.quotas),
      remark: agent.remark,
    });
  };

  const handleLookupBindUser = async () => {
    if (!agentApp) return;
    const githubId = bindGithubId.trim();
    if (!githubId) {
      message.warning('请先输入 GitHub ID');
      return;
    }
    setBindLookupLoading(true);
    setBindUser(null);
    try {
      const res: any = await lookupAppAgentBindUser(agentApp.id, githubId);
      if (res.code === 200 && res.data) {
        setBindUser(res.data);
        agentForm.setFieldValue('userId', res.data.id);
        message.success('已找到用户，可进行绑定');
      } else {
        agentForm.setFieldValue('userId', undefined);
        message.error(res.message || '用户不存在');
      }
    } finally {
      setBindLookupLoading(false);
    }
  };

  const submitAgentForm = async () => {
    if (!agentApp) return;
    const values = await agentForm.validateFields();
    const dto: AppAgentDTO = {
      agentCode: values.agentCode,
      userId: values.userId,
      scopeMode: values.scopeMode,
      enabled: values.enabled,
      permissions: values.permissions || [],
      remark: values.remark,
    };
    const quotas = parseQuotaText(values.quotaText);
    setAgentSaving(true);
    try {
      let targetId = editingAgent?.id;
      if (editingAgent?.id) {
        const res: any = await updateAppAgent(agentApp.id, editingAgent.id, dto);
        if (res.code !== 200) {
          message.error(res.message || '更新代理失败');
          return;
        }
      } else {
        const res: any = await createAppAgent(agentApp.id, dto);
        if (res.code !== 200 || !res.data?.id) {
          message.error(res.message || '创建代理失败');
          return;
        }
        targetId = res.data.id;
      }
      if (!targetId) return;
      const permRes: any = await updateAppAgentPermissions(agentApp.id, targetId, dto.permissions || []);
      if (permRes.code !== 200) {
        message.error(permRes.message || '保存代理权限失败');
        return;
      }
      const quotaRes: any = await updateAppAgentQuotas(agentApp.id, targetId, quotas);
      if (quotaRes.code !== 200) {
        message.error(quotaRes.message || '保存代理额度失败');
        return;
      }
      message.success(editingAgent ? '代理已更新' : '代理已创建');
      setEditingAgent(null);
      agentForm.resetFields();
      await loadAppAgents(agentApp.id);
    } finally {
      setAgentSaving(false);
    }
  };

  const getAppMenuItems = (record: Application): MenuProps['items'] => [
    {
      key: 'edit',
      icon: <EditOutlined />,
      label: '编辑',
      onClick: () => handleOpenModal(record),
    },
    {
      key: 'encryptionConfig',
      icon: <SlidersOutlined />,
      label: '加密配置',
      onClick: () => void openEncryptionConfigModal(record),
    },
    {
      key: 'agentConfig',
      icon: <TeamOutlined />,
      label: '代理配置',
      onClick: () => void openAgentModal(record),
    },
    {
      key: 'appRegister',
      icon: <UserAddOutlined />,
      label: '应用注册页',
      onClick: () => {
        const base = import.meta.env.BASE_URL || '/';
        const path = `${base.endsWith('/') ? base : `${base}/`}register?id=${record.id}`;
        window.open(`${window.location.origin}${path}`, '_blank', 'noopener,noreferrer');
      },
    },
    {
      key: 'licenseSelf',
      icon: <KeyOutlined />,
      label: '卡密自助页',
      onClick: () => {
        const base = import.meta.env.BASE_URL || '/';
        const path = `${base.endsWith('/') ? base : `${base}/`}license?id=${record.id}`;
        window.open(`${window.location.origin}${path}`, '_blank', 'noopener,noreferrer');
      },
    },
    {
      key: 'appUserSelf',
      icon: <UserAddOutlined />,
      label: '用户自助页',
      onClick: () => {
        const base = import.meta.env.BASE_URL || '/';
        const path = `${base.endsWith('/') ? base : `${base}/`}app-user?id=${record.id}`;
        window.open(`${window.location.origin}${path}`, '_blank', 'noopener,noreferrer');
      },
    },
    {
      key: 'paymentConfig',
      icon: <SafetyOutlined />,
      label: '支付配置',
      onClick: () => {
        setPaymentApp(record);
        setPaymentModalVisible(true);
        applicationEpayConfigApi.getConfig(record.id).then((res: any) => {
          setPaymentConfig(res?.data || { epayUrl: '', epayPid: '', epayKey: '' });
        });
      },
    },
    {
      key: 'pricingPlan',
      icon: <SafetyOutlined />,
      label: '价格方案',
      onClick: () => {
        setPricingApp(record);
        setPricingModalVisible(true);
        loadPricingPlans(record.id);
      },
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
    { type: 'divider' },
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

  const renderMobileCard = (app: Application) => {
    const statusMap: Record<number, { text: string; color: string }> = {
      1: { text: '正常', color: 'success' },
      2: { text: '维护', color: 'warning' },
      3: { text: '停用', color: 'error' },
    };
    const statusInfo = statusMap[app.status] || { text: '未知', color: 'default' };

    const modelMap: Record<number, { text: string; color: string }> = {
      1: { text: '付费', color: 'blue' },
      2: { text: '免费', color: 'green' },
      3: { text: '试用+付费', color: 'orange' },
    };
    const modelInfo = modelMap[app.businessModel] || { text: '未知', color: 'default' };

    const used = app.trafficUsed || 0;
    const limit = app.trafficLimit || 0;

    const category = app.category || '';
    let categoryIcon = <CloudOutlined style={{ fontSize: 20, color: '#13c2c2' }} />;
    if (category.includes('工具')) categoryIcon = <ApiOutlined style={{ fontSize: 20, color: '#1890ff' }} />;
    else if (category.includes('游戏')) categoryIcon = <RocketOutlined style={{ fontSize: 20, color: '#52c41a' }} />;
    else if (category.includes('办公')) categoryIcon = <DatabaseOutlined style={{ fontSize: 20, color: '#722ed1' }} />;
    else if (category.includes('安全')) categoryIcon = <SafetyOutlined style={{ fontSize: 20, color: '#fa8c16' }} />;

    return (
      <Card
        key={app.id}
        size="small"
        style={{ marginBottom: 12 }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40,
            height: 40,
            borderRadius: 8,
            background: '#f5f5f5',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            {app.iconUrl && app.iconUrl !== '/default-app-icon.png' ? (
              <img src={app.iconUrl} alt="" style={{ width: 24, height: 24, objectFit: 'contain' }} />
            ) : categoryIcon}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Text strong style={{ fontSize: 15, flexShrink: 0 }}>{app.appName}</Text>
              <Tag color={statusInfo.color} style={{ margin: 0, flexShrink: 0 }}>{statusInfo.text}</Tag>
              <Tag color={modelInfo.color} style={{ margin: 0, flexShrink: 0 }}>{modelInfo.text}</Tag>
            </div>
          </div>
          <Dropdown menu={{ items: getAppMenuItems(app) }} trigger={['click']}>
            <Button type="text" size="small" icon={<MoreOutlined />} style={{ flexShrink: 0 }} />
          </Dropdown>
        </div>

        <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span>AppKey</span>
            <Text copyable={{ text: app.appKey, tooltips: ['复制', '已复制'] }} style={{ fontFamily: 'monospace', fontSize: 12 }}>
              {app.appKey ? `${app.appKey.substring(0, 12)}...` : '-'}
            </Text>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span>流量</span>
            <span>{formatBytes(used)} / {limit > 0 ? formatBytes(limit) : '不限'}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#bfbfbf' }}>
            <span>{app.ownerName || '-'}</span>
            <span>{app.createdAt ? new Date(app.createdAt).toLocaleDateString('zh-CN') : '-'}</span>
          </div>
        </div>
      </Card>
    );
  };

  // 业务模式标签
  const getBusinessModelTag = (model: number) => {
    const map: Record<number, { text: string; color: string }> = {
      1: { text: '付费', color: 'blue' },
      2: { text: '免费', color: 'green' },
      3: { text: '试用+付费', color: 'orange' },
    };
    const item = map[model] || { text: '未知', color: 'default' };
    return <Tag color={item.color} style={isMobile ? { fontSize: 10, margin: 0, padding: '0 2px', lineHeight: '16px' } : undefined}>{item.text}</Tag>;
  };

  // 状态标签
  const getStatusBadge = (status: number) => {
    const map: Record<number, { text: string; status: 'success' | 'warning' | 'error' | 'default' }> = {
      1: { text: '正常', status: 'success' },
      2: { text: '维护', status: 'warning' },
      3: { text: '停用', status: 'error' },
    };
    const item = map[status] || { text: '未知', status: 'default' };
    return <Badge status={item.status} text={<span style={isMobile ? { fontSize: 11 } : undefined}>{item.text}</span>} />;
  };

  // 表格列定义
  const allColumns = [
    {
      title: '应用名称',
      dataIndex: 'appName',
      key: 'appName',
      width: isMobile ? 140 : 200,
      ...(isMobile ? {} : { fixed: 'left' as const }),
      render: (text: string, record: Application) => {
        const getIcon = () => {
          const category = record.category?.toLowerCase();
          if (category?.includes('工具')) return <ApiOutlined style={{ fontSize: isMobile ? 16 : 20, color: '#1890ff' }} />;
          if (category?.includes('游戏')) return <RocketOutlined style={{ fontSize: isMobile ? 16 : 20, color: '#52c41a' }} />;
          if (category?.includes('办公')) return <DatabaseOutlined style={{ fontSize: isMobile ? 16 : 20, color: '#722ed1' }} />;
          if (category?.includes('安全')) return <SafetyOutlined style={{ fontSize: isMobile ? 16 : 20, color: '#fa8c16' }} />;
          return <CloudOutlined style={{ fontSize: isMobile ? 16 : 20, color: '#13c2c2' }} />;
        };

        return (
          <Space size={isMobile ? 4 : 8}>
            {getIcon()}
            <div>
              <div><Text strong style={{ fontSize: isMobile ? 13 : 14 }}>{text}</Text></div>
              {!isMobile && <div><Text type="secondary" style={{ fontSize: 12 }}>ID: {record.id}</Text></div>}
            </div>
          </Space>
        );
      },
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 80,
      render: (text: string) => <Tag style={{ fontSize: isMobile ? 10 : 12, margin: 0, padding: isMobile ? '0 2px' : undefined, lineHeight: isMobile ? '16px' : undefined }}>{text || '未分类'}</Tag>,
    },
    {
      title: 'AppKey',
      dataIndex: 'appKey',
      key: 'appKey',
      width: isMobile ? 120 : 180,
      render: (text: string) => {
        if (!text) {
          return <Text type="secondary">-</Text>;
        }
        return (
          <Text
            copyable={{ text, tooltips: ['复制', '已复制'] }}
            style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 11, color: '#666' }}
          >
            {text.substring(0, isMobile ? 8 : 12)}...
          </Text>
        );
      },
    },
    {
      title: 'AppSecret',
      dataIndex: 'appSecret',
      key: 'appSecret',
      width: isMobile ? 100 : 180,
      render: (text: string, record: Application) => (
        <Space size="small">
          <Text style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 11, color: '#666' }}>
            {!text ? '-' : (showSecret[record.id] ? text.substring(0, isMobile ? 6 : 12) + '...' : '••••••')}
          </Text>
          {text && (
            <Button
              type="text"
              size="small"
              icon={showSecret[record.id] ? <EyeInvisibleOutlined /> : <EyeOutlined />}
              onClick={() => toggleShowSecret(record.id)}
              style={{ padding: isMobile ? '0 2px' : undefined }}
            />
          )}
          {!isMobile && text && showSecret[record.id] && (
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
      width: 80,
      align: 'center' as const,
      render: (model: number) => getBusinessModelTag(model),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 70,
      align: 'center' as const,
      render: (status: number) => getStatusBadge(status),
    },
    {
      title: '流量使用',
      key: 'traffic',
      width: isMobile ? 80 : 120,
      align: 'right' as const,
      render: (_: any, record: Application) => {
        const used = record.trafficUsed || 0;
        const limit = record.trafficLimit || 0;
        return (
          <div style={{ textAlign: 'right' }}>
            <div><Text style={{ fontSize: 11 }}>{formatBytes(used)}</Text></div>
            <div><Text type="secondary" style={{ fontSize: 10 }}>/ {formatBytes(limit)}</Text></div>
          </div>
        );
      },
    },
    {
      title: '创建者',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: isMobile ? 80 : 100,
      render: (text: string) => <Text style={{ fontSize: isMobile ? 12 : 14 }}>{text || '-'}</Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: isMobile ? 100 : 160,
      render: (text: string) => (
        <Text style={{ fontSize: 11 }}>
          {isMobile
            ? new Date(text).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
            : new Date(text).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
          }
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      ...(isMobile ? {} : { fixed: 'right' as const }),
      render: (_: any, record: Application) => {
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
            <Dropdown menu={{ items: getAppMenuItems(record) }} trigger={['click']}>
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
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>应用管理</Title>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
            <Button
              size={isMobile ? 'small' : 'middle'}
              icon={<ReloadOutlined />}
              onClick={() => fetchApplications(pagination.current, pagination.pageSize)}
             
            >
              刷新
            </Button>
            <Button
              type="primary"
              size={isMobile ? 'small' : 'middle'}
              icon={<PlusOutlined />}
              onClick={() => handleOpenModal()}
             
            >
              创建应用
            </Button>
          </div>
        </div>

        {/* 应用列表 */}
        {isMobile ? (
          <div className="application-mobile-card-list">
            {loading ? (
              <div style={{ padding: '40px 0', textAlign: 'center' }}>
                <Text type="secondary">加载中...</Text>
              </div>
            ) : applications.length === 0 ? (
              <div style={{ padding: '40px 0', textAlign: 'center' }}>
                <Text type="secondary">暂无数据</Text>
              </div>
            ) : (
              applications.map(app => renderMobileCard(app))
            )}
            {pagination.total > pagination.pageSize && (
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                <Pagination
                  current={pagination.current}
                  pageSize={pagination.pageSize}
                  total={pagination.total}
                  size="small"
                  simple
                  onChange={(page, pageSize) => fetchApplications(page, pageSize)}
                />
              </div>
            )}
          </div>
        ) : (
          <Table
            columns={allColumns}
            dataSource={applications}
            rowKey="id"
            loading={loading}
            size="middle"
            pagination={{
              ...pagination,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, pageSize) => {
                fetchApplications(page, pageSize);
              },
            }}
            scroll={{ x: 1000 }}
          />
        )}
      </Space>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={editingApp ? '编辑应用' : '创建应用'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={isMobile ? '100%' : 700}
        okText="确定"
        cancelText="取消"
        className={`app-edit-modal${isMobile ? ' mobile-modal' : ''}`}
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
            unbindTimeDeductMode: 'NONE',
            unbindCooldownHours: 0,
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

          <Row gutter={isMobile ? [8, 0] : 16}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="应用分类"
                name="category"
                rules={[{ max: 50, message: '分类不能超过50个字符' }]}
              >
                <Input placeholder="如：游戏、工具等" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="标签"
                name="tags"
                rules={[{ max: 255, message: '标签不能超过255个字符' }]}
              >
                <Input placeholder="多个标签用逗号分隔" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? [8, 0] : 16}>
            <Col xs={24} sm={12}>
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
            <Col xs={24} sm={12}>
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

          <Divider>卡密换绑扣时</Divider>
          <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            仅当终端通过三方接口 <strong>POST /api/v1/card/rebind</strong> 换绑设备时，若该卡密<strong>原先已有设备绑定</strong>，才按此处规则从<strong>到期时间</strong>扣减；管理员在后台「解绑设备 / 解绑 IP」<strong>不扣时</strong>。无到期时间（永久）的卡密不扣时。默认不扣。
          </Text>
          <Row gutter={isMobile ? [8, 0] : 16}>
            <Col xs={24} sm={12}>
              <Form.Item label="扣时模式" name="unbindTimeDeductMode">
                <Select
                  options={[
                    { value: 'NONE', label: '不扣' },
                    { value: 'PERCENT', label: '按剩余时长百分比' },
                    { value: 'HOURS', label: '固定扣除小时' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.unbindTimeDeductMode !== cur.unbindTimeDeductMode}>
                {() => {
                  const mode = form.getFieldValue('unbindTimeDeductMode') as string | undefined;
                  if (!mode || mode === 'NONE') {
                    return null;
                  }
                  return (
                    <Form.Item
                      label={mode === 'PERCENT' ? '扣除比例 (%)' : '扣除小时数'}
                      name="unbindTimeDeductValue"
                      rules={[
                        { required: true, message: '请填写数值' },
                        {
                          type: 'number',
                          min: mode === 'PERCENT' ? 0 : 0,
                          max: mode === 'PERCENT' ? 100 : undefined,
                          message:
                            mode === 'PERCENT' ? '百分比需在 0～100 之间' : '须为非负数',
                        },
                      ]}
                      extra={
                        mode === 'PERCENT'
                          ? '例如 10 表示每次三方换绑扣掉「当前剩余有效期」的 10%'
                          : '支持小数，例如 2.5 表示每次三方换绑扣 2.5 小时'
                      }
                    >
                      <InputNumber
                        style={{ width: '100%' }}
                        min={0}
                        max={mode === 'PERCENT' ? 100 : undefined}
                        step={mode === 'PERCENT' ? 1 : 0.5}
                      />
                    </Form.Item>
                  );
                }}
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={isMobile ? [8, 0] : 16}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="解绑冷却时间(小时)"
                name="unbindCooldownHours"
                tooltip="0 表示不限制；仅影响三方卡密换绑接口"
                rules={[{ type: 'number', min: 0, message: '不能小于 0' }]}
              >
                <InputNumber style={{ width: '100%' }} min={0} step={1} placeholder="0 表示不限制" />
              </Form.Item>
            </Col>
          </Row>

          <Divider>版本信息（可选）</Divider>

          <Row gutter={isMobile ? [8, 0] : 16}>
            <Col xs={24} sm={12}>
              <Form.Item label="当前版本" name="currentVersion">
                <Input placeholder="如：1.0.0" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
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

          <Form.Item
            label="更新包"
            tooltip="仅编辑已保存的应用时可上传至 MinIO（与插件同一桶）；单文件最大 512MB，新上传会替换旧对象。"
          >
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <Upload
                maxCount={1}
                showUploadList={false}
                disabled={!editingApp || updatePackageUploading}
                beforeUpload={(file) => {
                  if (!editingApp) {
                    message.warning('请先创建并保存应用后，再通过「编辑」上传更新包');
                    return Upload.LIST_IGNORE;
                  }
                  void handleUploadUpdatePackage(file as File);
                  return false;
                }}
              >
                <Button icon={<UploadOutlined />} loading={updatePackageUploading} disabled={!editingApp}>
                  上传到 MinIO
                </Button>
              </Upload>
              {editingApp?.updateFileStorageKey ? (
                <Text type="secondary" copyable style={{ fontSize: 12, wordBreak: 'break-all' as const }}>
                  当前对象键：{editingApp.updateFileStorageKey}
                </Text>
              ) : editingApp ? (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  尚未上传更新包
                </Text>
              ) : null}
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          encryptionApp ? (
            <Space>
              <SlidersOutlined />
              <span>加密配置</span>
              <Text type="secondary" style={{ fontSize: 14, fontWeight: 'normal' }}>
                {encryptionApp.appName} (ID: {encryptionApp.id})
              </Text>
            </Space>
          ) : (
            '加密配置'
          )
        }
        open={encryptionModalVisible}
        onCancel={() => {
          setEncryptionModalVisible(false);
          setEncryptionApp(null);
        }}
        width={isMobile ? '100%' : 720}
        okText="保存"
        cancelText="取消"
        confirmLoading={encryptionSaving}
        okButtonProps={{ disabled: !!encryptionJsonError || encryptionLoading }}
        onOk={() => void handleSaveEncryptionConfig()}
        destroyOnHidden
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          此处为当前应用的 <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>encryptionConfig</Tag>
          （JSON 对象）。默认三方卡密接口插件为 AES（
          <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>aes-default</Tag>
          ），请至少配置{' '}
          <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>aesKey</Tag>
          （或 <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>secretKey</Tag>
          ）作为 AES 密钥（UTF-8 长度须为 16 / 24 / 32 字节）。报文体{' '}
          <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>data</Tag>
          为 Hutool AES 的十六进制密文，明文为按 key 排序的 canonical 字符串（与 EncryptionModule / aes-data-v1 一致）。解密时还会合并插件管理中的
          <Tag style={{ marginInline: '0 6px', fontFamily: 'Consolas, Monaco, monospace' }}>pluginConfig</Tag>
          一并传入插件。密钥类参数建议只放在此处，勿写入全局插件配置。
        </Text>
        <div
          style={{
            border: `1px solid ${encryptionJsonError ? '#ff4d4f' : '#d9d9d9'}`,
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          <CodeMirror
            value={encryptionConfigJson}
            height="340px"
            extensions={[json()]}
            editable={!encryptionLoading}
            basicSetup={{
              lineNumbers: true,
              highlightActiveLine: true,
              foldGutter: true,
            }}
            onChange={(value: string) => setEncryptionConfigJson(value)}
          />
        </div>
        {encryptionJsonError ? (
          <Text type="danger" style={{ display: 'block', marginTop: 8 }}>
            {encryptionJsonError}
          </Text>
        ) : (
          <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
            JSON 校验通过
          </Text>
        )}
      </Modal>

      <Modal
        title={agentApp ? `代理配置 - ${agentApp.appName} (ID: ${agentApp.id})` : '代理配置'}
        open={agentModalVisible}
        onCancel={() => {
          setAgentModalVisible(false);
          setAgentApp(null);
          setEditingAgent(null);
          setBindGithubId('');
          setBindUser(null);
          agentForm.resetFields();
        }}
        width={isMobile ? '100%' : 960}
        footer={null}
        destroyOnHidden
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Row gutter={isMobile ? [0, 16] : 16}>
          <Col xs={24} sm={14}>
            <Table
              size="small"
              rowKey="id"
              loading={agentLoading}
              dataSource={agentList}
              pagination={false}
              scroll={isMobile ? { x: 400 } : undefined}
              columns={[
                { title: '代理名', dataIndex: 'agentCode', key: 'agentCode' },
                {
                  title: '绑定用户',
                  key: 'userId',
                  render: (_: any, r: AppAgentDTO) => `#${r.userId}`,
                },
                { title: '范围', dataIndex: 'scopeMode', key: 'scopeMode', render: (v: string) => <Tag>{v}</Tag> },
                { title: '启用', dataIndex: 'enabled', key: 'enabled', render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? '启用' : '禁用'}</Tag> },
                {
                  title: '操作',
                  key: 'action',
                  render: (_: any, r: AppAgentDTO) => (
                    <Button type="link" size="small" onClick={() => onEditAgent(r)}>
                      编辑
                    </Button>
                  ),
                },
              ]}
            />
          </Col>
          <Col xs={24} sm={10}>
            <Card size="small" title={editingAgent ? '编辑代理' : '新建代理'}>
              <Form
                form={agentForm}
                layout="vertical"
                initialValues={{ scopeMode: 'OWN_ONLY', enabled: true, permissions: [] }}
              >
                <Form.Item name="agentCode" label="代理名称" rules={[{ required: true, message: '请输入代理名称' }]}>
                  <Input placeholder="例如 华东渠道A" />
                </Form.Item>
                <Form.Item label="绑定后台用户" required>
                  <Space.Compact style={{ width: '100%' }}>
                    <Input
                      value={bindGithubId}
                      onChange={(e) => setBindGithubId(e.target.value)}
                      placeholder="输入对方 GitHub ID"
                    />
                    <Button loading={bindLookupLoading} onClick={() => void handleLookupBindUser()}>
                      查询
                    </Button>
                  </Space.Compact>
                  <Form.Item name="userId" hidden rules={[{ required: true, message: '请先查询并选择用户' }]}>
                    <Input />
                  </Form.Item>
                  <div style={{ marginTop: 8 }}>
                    {bindUser ? (
                      <Text type="success">
                        已匹配用户：{bindUser.name || bindUser.login || '-'}（ID: {bindUser.id}）
                      </Text>
                    ) : (
                      <Text type="secondary">未选择用户</Text>
                    )}
                  </div>
                </Form.Item>
                <Form.Item name="scopeMode" label="数据范围">
                  <Select
                    options={[
                      { label: '仅代理自己创建数据', value: 'OWN_ONLY' },
                      { label: '应用内全部数据', value: 'ALL_IN_APP' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="enabled" label="启用状态" valuePropName="checked">
                  <Switch />
                </Form.Item>
                <Form.Item name="permissions" label="代理权限">
                  <Select mode="multiple" options={AGENT_PERMISSION_OPTIONS} />
                </Form.Item>
                <Form.Item
                  name="quotaText"
                  label="额度配置"
                  extra="每行一个：KEY_TYPE:数量，如 MONTH:100"
                >
                  <TextArea placeholder={'DAY:50\nMONTH:100\nYEAR:10'} autoSize={{ minRows: 4, maxRows: 8 }} />
                </Form.Item>
                <Form.Item name="remark" label="备注">
                  <TextArea autoSize={{ minRows: 2, maxRows: 4 }} />
                </Form.Item>
                <Space>
                  <Button type="primary" loading={agentSaving} onClick={() => void submitAgentForm()}>
                    保存
                  </Button>
                  <Button
                    onClick={() => {
                      setEditingAgent(null);
                      setBindGithubId('');
                      setBindUser(null);
                      agentForm.resetFields();
                    }}
                  >
                    重置
                  </Button>
                </Space>
              </Form>
            </Card>
          </Col>
        </Row>
      </Modal>

      {/* 价格方案弹窗 */}
      <Modal
        title={`价格方案 - ${pricingApp?.appName || ''}`}
        open={pricingModalVisible}
        onCancel={() => { setPricingModalVisible(false); setEditingPricing(null); pricingForm.resetFields(); }}
        footer={null}
        width={isMobile ? '100%' : 700}
      >
        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingPricing(null); setShowPricingForm(true); pricingForm.resetFields(); }}>
            新增方案
          </Button>
        </div>
        <Table
          dataSource={pricingList}
          rowKey="id"
          loading={pricingLoading}
          pagination={false}
          size="small"
          columns={[
            { title: '方案名称', dataIndex: 'planName', key: 'planName' },
            { title: '类型', dataIndex: 'planType', key: 'planType', render: (v: string) => v === 'MEMBER' ? '会员' : v === 'TRIAL' ? '试用' : v },
            { title: '时长(天)', dataIndex: 'durationDays', key: 'durationDays' },
            { title: '价格(元)', dataIndex: 'priceFen', key: 'priceFen', render: (v: number) => v != null ? `¥${(v / 100).toFixed(2)}` : '-' },
            { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
            { title: '状态', dataIndex: 'enabled', key: 'enabled', render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? '启用' : '禁用'}</Tag> },
            {
              title: '操作', key: 'action',
              render: (_: any, record: any) => (
                <Space>
                  <Button size="small" onClick={() => { setEditingPricing(record); setShowPricingForm(true); pricingForm.setFieldsValue(record); }}>编辑</Button>
                  <Button size="small" danger onClick={() => {
                    Modal.confirm({
                      title: '确认删除', content: `确定删除方案「${record.planName}」？`,
                      onOk: async () => {
                        await pricingPlanApi.delete(pricingApp!.id, record.id);
                        message.success('删除成功');
                        loadPricingPlans(pricingApp!.id);
                      },
                    });
                  }}>删除</Button>
                </Space>
              ),
            },
          ]}
        />
        {showPricingForm ? (
          <Card title={editingPricing ? '编辑方案' : '新增方案'} style={{ marginTop: 16 }}>
            <Form form={pricingForm} layout="vertical" onFinish={async (values: any) => {
              try {
                if (editingPricing) {
                  await pricingPlanApi.update(pricingApp!.id, editingPricing.id, values);
                  message.success('更新成功');
                } else {
                  await pricingPlanApi.create(pricingApp!.id, values);
                  message.success('创建成功');
                }
                setEditingPricing(null);
                setShowPricingForm(false);
                pricingForm.resetFields();
                loadPricingPlans(pricingApp!.id);
              } catch {
                // handled
              }
            }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="planName" label="方案名称" rules={[{ required: true, message: '请输入方案名称' }]}>
                    <Input placeholder="如：月度会员" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="planType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
                    <Select placeholder="选择类型" options={[{ value: 'MEMBER', label: '会员' }, { value: 'TRIAL', label: '试用' }]} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="durationDays" label="时长(天)" rules={[{ required: true, message: '请输入天数' }]}>
                    <InputNumber min={1} style={{ width: '100%' }} placeholder="30" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="priceFen" label="价格(分)" rules={[{ required: true, message: '请输入价格' }]}>
                    <InputNumber min={0} style={{ width: '100%' }} placeholder="如：1990 = ¥19.90" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="sortOrder" label="排序" initialValue={0}>
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="enabled" label="状态" initialValue={true}>
                    <Select options={[{ value: true, label: '启用' }, { value: false, label: '禁用' }]} />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit">{editingPricing ? '保存' : '创建'}</Button>
                  <Button onClick={() => { setEditingPricing(null); setShowPricingForm(false); pricingForm.resetFields(); }}>取消</Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        ) : null}
      </Modal>

      {/* 支付配置弹窗 */}
      <Modal
        title={`支付配置 - ${paymentApp?.appName || ''}`}
        open={paymentModalVisible}
        onCancel={() => { setPaymentModalVisible(false); setPaymentConfig(null); }}
        footer={null}
        width={isMobile ? '100%' : 600}
      >
        {paymentConfig && (
          <div>
            <div style={{ marginBottom: 16, padding: 12, background: '#fff7e6', borderRadius: 8, border: '1px solid #ffd591' }}>
              <Text type="warning">
                配置易支付参数后，超级管理员可以在应用管理中开启终端用户购买功能。
                回调地址使用系统统一回调，无需手动填写。
              </Text>
            </div>
            <Form layout="vertical">
              <Form.Item label="易支付域名" required>
                <Input
                  value={paymentConfig.epayUrl}
                  onChange={(e) => setPaymentConfig({ ...paymentConfig, epayUrl: e.target.value })}
                  placeholder="https://pay.example.com"
                />
              </Form.Item>
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item label="商户ID" required>
                    <Input
                      value={paymentConfig.epayPid}
                      onChange={(e) => setPaymentConfig({ ...paymentConfig, epayPid: e.target.value })}
                      placeholder="商户ID"
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="密钥" required>
                    <Input.Password
                      value={paymentConfig.epayKey}
                      onChange={(e) => setPaymentConfig({ ...paymentConfig, epayKey: e.target.value })}
                      placeholder="商户密钥"
                    />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item>
                <Space>
                  <Button
                    type="primary"
                    loading={paymentSaving}
                    onClick={async () => {
                      if (!paymentConfig.epayUrl || !paymentConfig.epayPid || !paymentConfig.epayKey) {
                        message.error('请填写必填项');
                        return;
                      }
                      setPaymentSaving(true);
                      try {
                        await applicationEpayConfigApi.saveConfig(paymentApp!.id, paymentConfig);
                        message.success('保存成功');
                        setPaymentModalVisible(false);
                      } catch {
                        // handled by interceptor
                      } finally {
                        setPaymentSaving(false);
                      }
                    }}
                  >
                    保存配置
                  </Button>
                  {paymentApp && (
                    isSuperAdmin ? (
                      <Switch
                        checkedChildren="开启购买"
                        unCheckedChildren="关闭购买"
                        checked={paymentApp.portalPaymentEnabled}
                        onChange={async (checked) => {
                          try {
                            await applicationEpayConfigApi.togglePayment(paymentApp.id, checked);
                            message.success(checked ? '已开启购买功能' : '已关闭购买功能');
                            setPaymentApp({ ...paymentApp, portalPaymentEnabled: checked });
                            fetchApplications();
                          } catch {
                            // handled by interceptor
                          }
                        }}
                      />
                    ) : (
                      <Tag color={paymentApp.portalPaymentEnabled ? 'success' : 'default'}>
                        {paymentApp.portalPaymentEnabled ? '购买已开启' : '购买未开启'}
                      </Tag>
                    )
                  )}
                </Space>
              </Form.Item>
            </Form>
          </div>
        )}
      </Modal>
    </Card>
  );
};

export default ApplicationManagementContent;
