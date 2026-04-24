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
  InputNumber,
  DatePicker,
  Select,
  message,
  Row,
  Col,
  Dropdown,
  Badge,
  Popover,
  type MenuProps,
} from 'antd';
import dayjs from 'dayjs';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  MoreOutlined,
  LockOutlined,
  StopOutlined,
  UserOutlined,
  MobileOutlined,
  CrownOutlined,
  FilterOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import {
  getAppUserList,
  createAppUser,
  updateAppUser,
  deleteAppUser,
  resetPassword,
  banUser,
  getUserBindings,
  unbindDevice,
  extendMemberDays,
  batchExtendMemberDays,
  setMemberExpiresAt,
  type AppUser,
  type AppUserBinding,
} from '../services/appUserService';
import { getApplicationList, type Application } from '../services/applicationService';

const { Title, Text } = Typography;
const { Option } = Select;

function formatOnlineSeconds(sec?: number | null): string {
  if (sec == null || sec < 0) return '-';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  if (h > 0) return `${h}时${m}分`;
  if (m > 0) return `${m}分${s}秒`;
  return `${s}秒`;
}

const AppUserManagementContent: React.FC = () => {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [bindingsModalVisible, setBindingsModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<AppUser | null>(null);
  const [resetUserId, setResetUserId] = useState<number | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [selectedUsername, setSelectedUsername] = useState<string>('');
  const [userBindings, setUserBindings] = useState<AppUserBinding[]>([]);
  const [bindingsLoading, setBindingsLoading] = useState(false);
  const [extendModalVisible, setExtendModalVisible] = useState(false);
  const [extendUserId, setExtendUserId] = useState<number | null>(null);
  const [batchExtendVisible, setBatchExtendVisible] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [memberExpModalVisible, setMemberExpModalVisible] = useState(false);
  const [memberExpUser, setMemberExpUser] = useState<AppUser | null>(null);
  const [form] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [extendForm] = Form.useForm();
  const [batchExtendForm] = Form.useForm();
  const [memberExpForm] = Form.useForm();
  const [listFilterForm] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [filters, setFilters] = useState<any>({});
  const [usernameInput, setUsernameInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);

  const activeAdvancedFilterCount = [
    filters.appId,
    filters.email,
    filters.phone,
    filters.banned,
    filters.memberStatus,
    filters.wsOnline,
  ].filter((v) => v !== undefined && v !== null && v !== '').length;

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

  useEffect(() => {
    setUsernameInput(filters.username ?? '');
  }, [filters.username]);

  const syncListFilterFormFromFilters = () => {
    listFilterForm.setFieldsValue({
      appId: filters.appId,
      username: filters.username,
      email: filters.email,
      phone: filters.phone,
      banned: filters.banned,
      memberStatus: filters.memberStatus,
      wsOnline: filters.wsOnline,
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
    const usernameTrim = (v.username ?? '').trim();
    if (usernameTrim) {
      next.username = usernameTrim;
    } else {
      delete next.username;
    }
    const emailTrim = (v.email ?? '').trim();
    if (emailTrim) {
      next.email = emailTrim;
    } else {
      delete next.email;
    }
    const phoneTrim = (v.phone ?? '').trim();
    if (phoneTrim) {
      next.phone = phoneTrim;
    } else {
      delete next.phone;
    }

    if (v.banned === true || v.banned === false) {
      next.banned = v.banned;
    } else {
      delete next.banned;
    }

    if (v.memberStatus) {
      next.memberStatus = v.memberStatus;
    } else {
      delete next.memberStatus;
    }

    if (v.wsOnline === true || v.wsOnline === false) {
      next.wsOnline = v.wsOnline;
    } else {
      delete next.wsOnline;
    }

    setFilters(next);
    fetchUsers(1, pagination.pageSize, next);
    setFilterPopoverOpen(false);
  };

  const handleAdvancedFilterReset = () => {
    listFilterForm.resetFields();
    const next = { ...filters };
    delete next.appId;
    delete next.username;
    delete next.email;
    delete next.phone;
    delete next.banned;
    delete next.memberStatus;
    delete next.wsOnline;
    setFilters(next);
    fetchUsers(1, pagination.pageSize, next);
  };

  const applyUsernameSearch = (raw?: string) => {
    const trimmed = (raw ?? usernameInput).trim();
    const next = { ...filters };
    if (trimmed) {
      next.username = trimmed;
    } else {
      delete next.username;
    }
    setFilters(next);
    fetchUsers(1, pagination.pageSize, next);
  };

  // 定时刷新列表，使「在线 / 在线时长」接近实时（依赖管理端轮询）
  useEffect(() => {
    const timer = window.setInterval(() => {
      fetchUsers(pagination.current, pagination.pageSize, filters);
    }, 10000);
    return () => window.clearInterval(timer);
  }, [pagination.current, pagination.pageSize, filters]);

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
        const createValues: any = { ...values };
        if (createValues.memberExpiresAt && dayjs.isDayjs(createValues.memberExpiresAt)) {
          createValues.memberExpiresAt = createValues.memberExpiresAt.format('YYYY-MM-DDTHH:mm:ss');
        }
        // 创建
        const result: any = await createAppUser(createValues);
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

  // 封禁/解封用户（不传 bindingId 时对该用户全部绑定生效；传 bindingId 时仅该条绑定）
  const handleBanUser = (
    id: number,
    username: string,
    currentBanned: boolean,
    bindingId?: number,
    deviceId?: string,
  ) => {
    Modal.confirm({
      title: currentBanned ? '确认解封' : '确认封禁',
      content: currentBanned
        ? bindingId && deviceId
          ? `确定要解封用户 "${username}" 的设备 "${deviceId}" 吗？`
          : `确定要解封用户 "${username}" 吗？其下所有已封禁的设备绑定将恢复为正常。`
        : `确定要封禁用户 "${username}" 吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: currentBanned ? 'primary' : 'danger',
      onOk: async () => {
        try {
          const result: any = await banUser(
            id,
            !currentBanned,
            currentBanned ? undefined : '管理员封禁',
            bindingId,
          );
          if (result.code === 200) {
            message.success(currentBanned ? '解封成功' : '封禁成功');
            fetchUsers(pagination.current, pagination.pageSize, filters);
            if (bindingsModalVisible && selectedUserId === id) {
              fetchUserBindings(id);
            }
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

  // 获取用户绑定设备列表
  const fetchUserBindings = async (userId: number) => {
    setBindingsLoading(true);
    try {
      const result: any = await getUserBindings(userId, 1, 100);
      if (result.code === 200 && result.data) {
        setUserBindings(result.data.records || []);
      }
    } catch (error) {
      message.error('获取设备列表失败');
      console.error('获取设备列表失败:', error);
    } finally {
      setBindingsLoading(false);
    }
  };

  const handleExtendMemberOk = async () => {
    try {
      const { days } = await extendForm.validateFields();
      if (!extendUserId) return;
      const result: any = await extendMemberDays(extendUserId, days);
      if (result.code === 200) {
        message.success('会员已延长');
        setExtendModalVisible(false);
        setExtendUserId(null);
        fetchUsers(pagination.current, pagination.pageSize, filters);
      } else {
        message.error(result.message || '操作失败');
      }
    } catch (e) {
      console.error(e);
    }
  };

  const openBatchExtendModal = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要加时的用户');
      return;
    }
    batchExtendForm.setFieldsValue({ days: 30 });
    setBatchExtendVisible(true);
  };

  const handleBatchExtendOk = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选需要加时的用户');
      return;
    }
    try {
      const { days } = await batchExtendForm.validateFields();
      const result: any = await batchExtendMemberDays({
        ids: selectedRowKeys.map((k) => Number(k)),
        days,
      });
      if (result.code === 200 && result.data) {
        const r = result.data;
        setBatchExtendVisible(false);
        setSelectedRowKeys([]);
        fetchUsers(pagination.current, pagination.pageSize, filters);
        message.success(`批量加时完成：成功 ${r.successCount} 条，失败 ${r.failCount} 条`);
        if (r.failures?.length) {
          Modal.warning({
            title: '以下用户未加时',
            width: 600,
            content: (
              <ul style={{ maxHeight: 280, overflow: 'auto', margin: '8px 0 0', paddingLeft: 20 }}>
                {r.failures.map((f: { username?: string; id: number; reason: string }, i: number) => (
                  <li key={i} style={{ marginBottom: 4 }}>
                    <Tag>{f.username || `#${f.id}`}</Tag>：{f.reason}
                  </li>
                ))}
              </ul>
            ),
          });
        }
      } else {
        message.error(result.message || '批量加时失败');
      }
    } catch (e: any) {
      if (e?.errorFields) {
        return;
      }
      console.error(e);
      message.error('批量加时失败');
    }
  };

  const handleMemberExpiresOk = async () => {
    try {
      const { expires } = await memberExpForm.validateFields();
      if (!memberExpUser) return;
      if (!expires) {
        message.warning('请选择到期时间，或使用下方「清空会员」');
        return;
      }
      const iso = dayjs(expires).format('YYYY-MM-DDTHH:mm:ss');
      const result: any = await setMemberExpiresAt(memberExpUser.id, iso);
      if (result.code === 200) {
        message.success('已保存');
        setMemberExpModalVisible(false);
        setMemberExpUser(null);
        fetchUsers(pagination.current, pagination.pageSize, filters);
      } else {
        message.error(result.message || '操作失败');
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleClearMemberExpires = async () => {
    if (!memberExpUser) return;
    Modal.confirm({
      title: '清空会员',
      content: '确定取消该用户的会员到期时间？',
      onOk: async () => {
        const result: any = await setMemberExpiresAt(memberExpUser.id, null);
        if (result.code === 200) {
          message.success('已清空');
          setMemberExpModalVisible(false);
          setMemberExpUser(null);
          memberExpForm.resetFields();
          fetchUsers(pagination.current, pagination.pageSize, filters);
        } else {
          message.error(result.message || '操作失败');
        }
      },
    });
  };

  // 打开设备列表弹窗
  const handleOpenBindingsModal = (userId: number, username: string) => {
    setSelectedUserId(userId);
    setSelectedUsername(username);
    setBindingsModalVisible(true);
    fetchUserBindings(userId);
  };

  // 解绑设备
  const handleUnbindDevice = (userId: number, bindingId: number, deviceId: string) => {
    Modal.confirm({
      title: '确认解绑',
      content: `确定要解绑设备 "${deviceId}" 吗？此操作不可恢复。`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        try {
          const result: any = await unbindDevice(userId, bindingId, '管理员解绑');
          if (result.code === 200) {
            message.success('解绑成功');
            fetchUserBindings(userId); // 刷新设备列表
            fetchUsers(pagination.current, pagination.pageSize, filters); // 刷新用户列表
          } else {
            message.error(result.message || '解绑失败');
          }
        } catch (error) {
          message.error('解绑失败');
          console.error('解绑失败:', error);
        }
      },
    });
  };

  // 操作菜单
  const getActionMenu = (record: AppUser): MenuProps => ({
    items: [
      {
        key: 'view-devices',
        icon: <MobileOutlined />,
        label: '查看设备',
        onClick: () => handleOpenBindingsModal(record.id, record.username),
      },
      {
        key: 'extend-member',
        icon: <CrownOutlined />,
        label: '延长会员',
        onClick: () => {
          setExtendUserId(record.id);
          extendForm.setFieldsValue({ days: 30 });
          setExtendModalVisible(true);
        },
      },
      {
        key: 'set-member-expires',
        icon: <CrownOutlined />,
        label: '设置会员到期',
        onClick: () => {
          setMemberExpUser(record);
          memberExpForm.setFieldsValue({
            expires: record.memberExpiresAt ? dayjs(record.memberExpiresAt) : undefined,
          });
          setMemberExpModalVisible(true);
        },
      },
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
        label: record.isBanned ? '解封' : '封禁',
        danger: !record.isBanned,
        onClick: () => handleBanUser(record.id, record.username, !!record.isBanned),
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
          <Text
            strong
            copyable={{ text, tooltips: ['复制用户名', '已复制'] }}
            ellipsis={{ tooltip: text }}
            style={{ display: 'block', maxWidth: 110 }}
          >
            {text}
          </Text>
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
      title: '创建来源',
      key: 'creatorType',
      width: 200,
      render: (_: unknown, record: AppUser) => {
        if (record.creatorType === 'AGENT') {
          return <Text>{record.agentDisplayName || '-'}</Text>;
        }
        return <Tag color="blue">自己创建</Tag>;
      },
    },
    {
      title: '绑定设备',
      dataIndex: 'bindingCount',
      key: 'bindingCount',
      width: 100,
      align: 'center' as const,
      render: (count: number, record: AppUser) => (
        <Button
          type="link"
          size="small"
          onClick={() => handleOpenBindingsModal(record.id, record.username)}
          style={{ padding: 0 }}
        >
          <Tag color={count > 0 ? 'green' : 'default'}>{count || 0}</Tag>
        </Button>
      ),
    },
    {
      title: '封禁',
      key: 'banStatus',
      width: 88,
      align: 'center' as const,
      render: (_: unknown, record: AppUser) =>
        record.isBanned ? (
          <Tag color="red">已封禁</Tag>
        ) : (
          <Tag color="default">正常</Tag>
        ),
    },
    {
      title: '会员',
      key: 'member',
      width: 156,
      render: (_: unknown, record: AppUser) => {
        const active = record.memberActive;
        const exp = record.memberExpiresAt;
        return (
          <div style={{ minWidth: 0, lineHeight: 1.45 }}>
            <Tag color={active ? 'gold' : exp ? 'default' : 'blue'} style={{ marginInlineEnd: 0 }}>
              {active ? '会员有效' : exp ? '已过期' : '未开通'}
            </Tag>
            {exp ? (
              <div style={{ marginTop: 6 }}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', whiteSpace: 'nowrap' }}>
                  到期 {dayjs(exp).format('YYYY-MM-DD HH:mm')}
                </Text>
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      title: '试用',
      key: 'trial',
      width: 156,
      render: (_: unknown, record: AppUser) => {
        const applied = !!record.trialApplied;
        const active = !!record.trialActive;
        const exp = record.trialExpiresAt;
        return (
          <div style={{ minWidth: 0, lineHeight: 1.45 }}>
            <Tag color={!applied ? 'default' : active ? 'success' : 'warning'} style={{ marginInlineEnd: 0 }}>
              {!applied ? '未申请' : active ? '试用有效' : '已过期'}
            </Tag>
            {exp ? (
              <div style={{ marginTop: 6 }}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', whiteSpace: 'nowrap' }}>
                  到期 {dayjs(exp).format('YYYY-MM-DD HH:mm')}
                </Text>
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      title: 'WS在线',
      dataIndex: 'wsOnline',
      key: 'wsOnline',
      width: 88,
      align: 'center' as const,
      render: (online: boolean | undefined, record: AppUser) => (
        <Space direction="vertical" size={0} style={{ fontSize: 11 }}>
          <Tag color={online ? 'success' : 'default'}>{online ? '在线' : '离线'}</Tag>
          {online && record.wsSessionCount != null && record.wsSessionCount > 1 ? (
            <Text type="secondary">{record.wsSessionCount} 会话</Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '在线时长',
      dataIndex: 'wsOnlineSeconds',
      key: 'wsOnlineSeconds',
      width: 140,
      align: 'center' as const,
      render: (_: unknown, record: AppUser) => (
        <Space direction="vertical" size={0} style={{ fontSize: 11 }}>
          <Text style={{ fontSize: 12 }}>
            {record.wsOnline ? formatOnlineSeconds(record.wsOnlineSeconds) : '-'}
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            今日 {formatOnlineSeconds(record.wsTodayOnlineSeconds)}
          </Text>
        </Space>
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
      title: '最后登录IP',
      dataIndex: 'lastLoginIp',
      key: 'lastLoginIp',
      width: 200,
      render: (ip: string) =>
        ip ? (
          <Popover
            trigger="click"
            placement="topLeft"
            content={
              <div style={{ maxWidth: 420 }}>
                <Text
                  copyable={{ text: ip, tooltips: ['复制IP', '已复制'] }}
                  style={{ fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 12 }}
                >
                  {ip}
                </Text>
              </div>
            }
          >
            <Text
              copyable={{ text: ip, tooltips: ['复制IP', '已复制'] }}
              ellipsis={{ tooltip: '点击查看完整IP' }}
              style={{
                display: 'block',
                maxWidth: '100%',
                fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              {ip}
            </Text>
          </Popover>
        ) : (
          '-'
        ),
    },
    {
      title: '最后登录设备',
      dataIndex: 'lastDeviceId',
      key: 'lastDeviceId',
      width: 200,
      ellipsis: true,
      render: (id: string) =>
        id ? (
          <Popover
            trigger="click"
            placement="topLeft"
            content={
              <div style={{ maxWidth: 520 }}>
                <Text
                  copyable={{ text: id, tooltips: ['复制设备 ID', '已复制'] }}
                  style={{ fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 12 }}
                >
                  {id}
                </Text>
              </div>
            }
          >
            <Text
              copyable={{ text: id, tooltips: ['复制设备 ID', '已复制'] }}
              ellipsis={{ tooltip: '点击查看完整设备 ID' }}
              style={{
                display: 'block',
                maxWidth: '100%',
                fontSize: 12,
                fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace',
                lineHeight: 1.45,
                cursor: 'pointer',
              }}
            >
              {id}
            </Text>
          </Popover>
        ) : (
          '-'
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
                icon={<ClockCircleOutlined />}
                disabled={selectedRowKeys.length === 0}
                onClick={openBatchExtendModal}
              >
                批量加时
              </Button>
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

        {/* 主搜索（用户名）+ 高级筛选 */}
        <Row gutter={12} align="middle" wrap>
          <Col flex="none">
            <Space.Compact
              style={{
                width: 360,
                maxWidth: 'calc(100vw - 120px)',
              }}
            >
              <Input
                placeholder="搜索用户名"
                allowClear
                value={usernameInput}
                onChange={(e) => setUsernameInput(e.target.value)}
                onPressEnter={() => applyUsernameSearch()}
                style={{ minWidth: 0 }}
              />
              <Button type="primary" onClick={() => applyUsernameSearch()}>
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
                  syncListFilterFormFromFilters();
                }
              }}
              content={
                <div style={{ width: 560, maxWidth: '90vw' }}>
                  <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                    <Row gutter={16}>
                      <Col xs={24} md={12}>
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
                      <Col xs={24} md={12}>
                        <Form.Item label="用户名" name="username">
                          <Input allowClear placeholder="搜索用户名" />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="邮箱" name="email">
                          <Input allowClear placeholder="搜索邮箱" />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="手机号" name="phone">
                          <Input allowClear placeholder="搜索手机号" />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="封禁状态" name="banned">
                          <Select
                            allowClear
                            placeholder="全部"
                            options={[
                              { label: '正常', value: false },
                              { label: '已封禁', value: true },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="会员状态" name="memberStatus">
                          <Select
                            allowClear
                            placeholder="全部"
                            options={[
                              { label: '未到期（会员有效）', value: 'ACTIVE' },
                              { label: '已到期', value: 'EXPIRED' },
                              { label: '未开通', value: 'NONE' },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="WS 在线" name="wsOnline">
                          <Select
                            allowClear
                            placeholder="全部"
                            options={[
                              { label: '离线', value: false },
                              { label: '在线', value: true },
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
          </Col>
        </Row>

        {/* 用户列表表格 */}
        <Table
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
            preserveSelectedRowKeys: true,
          }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              fetchUsers(page, pageSize, filters);
            },
          }}
          scroll={{ x: 1920 }}
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

          {!editingUser && (
            <Form.Item label="会员到期（可选）" name="memberExpiresAt">
              <DatePicker
                showTime
                style={{ width: '100%' }}
                format="YYYY-MM-DD HH:mm:ss"
                placeholder="不选表示暂不开通会员"
              />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title="延长会员"
        open={extendModalVisible}
        onOk={handleExtendMemberOk}
        onCancel={() => {
          setExtendModalVisible(false);
          setExtendUserId(null);
        }}
        okText="确定"
        cancelText="取消"
      >
        <Form form={extendForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            label="增加天数"
            name="days"
            rules={[{ required: true, message: '请输入天数' }]}
            initialValue={30}
          >
            <InputNumber min={1} max={36500} style={{ width: '100%' }} placeholder="如 30" />
          </Form.Item>
          <Text type="secondary" style={{ fontSize: 12 }}>
            在「当前时间」与「原到期时间」中较晚的时间点上累加天数；未开通则从当前时间起算。
          </Text>
        </Form>
      </Modal>

      <Modal
        title="批量加时"
        open={batchExtendVisible}
        onOk={handleBatchExtendOk}
        onCancel={() => setBatchExtendVisible(false)}
        okText="确定加时"
        cancelText="取消"
        width={520}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          将对当前已勾选的 <Text strong>{selectedRowKeys.length}</Text> 位用户延长会员到期时间（按天累加）。
        </Text>
        <Form form={batchExtendForm} layout="vertical">
          <Form.Item
            label="增加天数"
            name="days"
            rules={[{ required: true, message: '请输入天数' }]}
            initialValue={30}
          >
            <InputNumber min={1} max={36500} style={{ width: '100%' }} placeholder="如 30" />
          </Form.Item>
          <Text type="secondary" style={{ fontSize: 12 }}>
            在「当前时间」与「原到期时间」中较晚的时间点上累加天数；未开通则从当前时间起算。
          </Text>
        </Form>
      </Modal>

      <Modal
        title="设置会员到期"
        open={memberExpModalVisible}
        onOk={handleMemberExpiresOk}
        onCancel={() => {
          setMemberExpModalVisible(false);
          setMemberExpUser(null);
        }}
        footer={[
          <Button key="clear" danger onClick={handleClearMemberExpires}>
            清空会员
          </Button>,
          <Button key="cancel" onClick={() => { setMemberExpModalVisible(false); setMemberExpUser(null); }}>
            取消
          </Button>,
          <Button key="ok" type="primary" onClick={handleMemberExpiresOk}>
            保存
          </Button>,
        ]}
      >
        <Form form={memberExpForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="到期时间" name="expires">
            <DatePicker
              showTime
              style={{ width: '100%' }}
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择日期时间"
            />
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

      {/* 用户绑定设备列表弹窗 */}
      <Modal
        title="用户绑定设备列表"
        open={bindingsModalVisible}
        onCancel={() => {
          setBindingsModalVisible(false);
          setSelectedUserId(null);
          setSelectedUsername('');
          setUserBindings([]);
        }}
        footer={[
          <Button key="close" onClick={() => {
            setBindingsModalVisible(false);
            setSelectedUserId(null);
            setSelectedUsername('');
            setUserBindings([]);
          }}>
            关闭
          </Button>
        ]}
        width={1000}
      >
        <Table
          columns={[
            {
              title: '设备ID',
              dataIndex: 'deviceId',
              key: 'deviceId',
              width: 260,
              ellipsis: true,
              render: (text: string) =>
                text ? (
                  <Popover
                    trigger="click"
                    placement="topLeft"
                    content={
                      <div style={{ maxWidth: 520 }}>
                        <Text
                          copyable={{ text, tooltips: ['复制设备 ID', '已复制'] }}
                          style={{ fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 12 }}
                        >
                          {text}
                        </Text>
                      </div>
                    }
                  >
                    <Text
                      copyable={{ text, tooltips: ['复制设备 ID', '已复制'] }}
                      ellipsis={{ tooltip: '点击查看完整设备 ID' }}
                      style={{
                        display: 'block',
                        maxWidth: '100%',
                        fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace',
                        fontSize: 12,
                        cursor: 'pointer',
                      }}
                    >
                      {text}
                    </Text>
                  </Popover>
                ) : (
                  '-'
                ),
            },
            {
              title: '设备名称',
              dataIndex: 'deviceName',
              key: 'deviceName',
              width: 120,
              render: (text: string) => text || '-',
            },
            {
              title: '系统',
              dataIndex: 'deviceOs',
              key: 'deviceOs',
              width: 80,
              render: (text: string) => (
                <Tag color="blue">{text || '-'}</Tag>
              ),
            },
            {
              title: '绑定类型',
              dataIndex: 'bindType',
              key: 'bindType',
              width: 100,
              render: (text: string) => {
                const colorMap: { [key: string]: string } = {
                  'LICENSE': 'green',
                  'TRIAL': 'orange',
                  'VIP': 'purple',
                  'ACCOUNT': 'cyan',
                };
                return <Tag color={colorMap[text] || 'default'}>{text}</Tag>;
              },
            },
            {
              title: '使用次数',
              dataIndex: 'useCount',
              key: 'useCount',
              width: 80,
              align: 'center' as const,
              render: (count: number) => <Text>{count || 0}</Text>,
            },
            {
              title: '解绑次数',
              dataIndex: 'unbindCount',
              key: 'unbindCount',
              width: 80,
              align: 'center' as const,
              render: (count: number) => <Text>{count || 0}</Text>,
            },
            {
              title: '状态',
              dataIndex: 'status',
              key: 'status',
              width: 80,
              render: (status: number, record: AppUserBinding) => {
                if (record.isBanned) {
                  return <Tag color="red">已封禁</Tag>;
                }
                const statusMap: { [key: number]: { text: string; color: string } } = {
                  1: { text: '正常', color: 'green' },
                  2: { text: '已过期', color: 'orange' },
                  3: { text: '已封禁', color: 'red' },
                  4: { text: '已解绑', color: 'default' }
                };
                const statusInfo = statusMap[status] || { text: '未知', color: 'default' };
                return <Tag color={statusInfo.color}>{statusInfo.text}</Tag>;
              },
            },
            {
              title: '最后活跃',
              dataIndex: 'lastActiveAt',
              key: 'lastActiveAt',
              width: 140,
              render: (text: string) => (
                text ? (
                  <Text style={{ fontSize: 12 }}>
                    {new Date(text).toLocaleString('zh-CN', {
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
              title: '绑定时间',
              dataIndex: 'firstBindAt',
              key: 'firstBindAt',
              width: 140,
              render: (text: string) => (
                text ? (
                  <Text style={{ fontSize: 12 }}>
                    {new Date(text).toLocaleString('zh-CN', {
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
              title: '操作',
              key: 'action',
              width: 140,
              fixed: 'right' as const,
              render: (_: any, record: AppUserBinding) => (
                <Space size="small" wrap>
                  {record.isBanned && record.status !== 4 && selectedUserId != null && (
                    <Button
                      type="link"
                      size="small"
                      onClick={() =>
                        handleBanUser(
                          selectedUserId,
                          selectedUsername || '该用户',
                          true,
                          record.id,
                          record.deviceId,
                        )
                      }
                    >
                      解封
                    </Button>
                  )}
                  {record.status !== 4 && !record.isBanned && (
                    <Button
                      type="link"
                      size="small"
                      danger
                      onClick={() => selectedUserId && handleUnbindDevice(selectedUserId, record.id, record.deviceId)}
                    >
                      解绑
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
          dataSource={userBindings}
          rowKey="id"
          loading={bindingsLoading}
          pagination={false}
          scroll={{ x: 900 }}
          size="small"
        />
      </Modal>
    </Card>
  );
};

export default AppUserManagementContent;
