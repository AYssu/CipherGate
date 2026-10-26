import React, { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { UserInfo } from '../services/userService';
import {
  Badge,
  Checkbox,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Dropdown,
  Alert,
  Divider,
  Popover,
  Row,
  message,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Pagination,
  type MenuProps,
  Grid,
} from 'antd';
import { PlusOutlined, ReloadOutlined, KeyOutlined, CopyOutlined, EyeOutlined, EyeInvisibleOutlined, MoreOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { FilterOutlined } from '@ant-design/icons';
import {
  createThirdPartyCredential,
  deleteThirdPartyCredential,
  getThirdPartyCredentialList,
  rotateThirdPartyCredentialSecret,
  updateThirdPartyCredential,
  type ThirdPartyCredential,
  type ThirdPartyCredentialDTO,
  type CredentialPage,
} from '../services/thirdPartyCredentialService';
import { getApplicationList, type Application } from '../services/applicationService';
import type { ApiResponse } from '../types';
import M5BottomSheet from './M5BottomSheet';

const { Text, Title, Paragraph } = Typography;

const ThirdPartyCredentialManagementContent: React.FC = () => {
  const { userInfo } = useOutletContext<{ userInfo?: UserInfo | null }>() ?? {};
  const isAdmin = !!userInfo?.roles?.some((role) => role.roleCode === 'SUPER_ADMIN' || role.roleCode === 'ADMIN');
  const hasRestrictedActions = (credential: ThirdPartyCredential) => !!(credential.allowTrial || credential.allowQuery);
  const canManageCredential = (credential: ThirdPartyCredential) => isAdmin || !hasRestrictedActions(credential);
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [list, setList] = useState<ThirdPartyCredential[]>([]);
  const [apps, setApps] = useState<Application[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCredential, setEditingCredential] = useState<ThirdPartyCredential | null>(null);
  const [secretVisible, setSecretVisible] = useState(false);
  const [latestSecret, setLatestSecret] = useState<{ apiKey: string; apiSecret: string } | null>(null);
  const [showSecret, setShowSecret] = useState<Record<number, boolean>>({});
  const [rotatedSecretMap, setRotatedSecretMap] = useState<Record<number, string>>({});
  const [form] = Form.useForm<ThirdPartyCredentialDTO>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState<{ appId?: number; status?: number; name?: string }>({});
  const [nameInput, setNameInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);
  const [listFilterForm] = Form.useForm();

  const fetchApps = async () => {
    try {
      const res = await getApplicationList({ current: 1, size: 1000 }) as unknown as ApiResponse<CredentialPage<Application>>;
      if (res.code === 200) {
        setApps(res.data?.records || []);
      }
    } catch {
      // ignore
    }
  };

  const fetchData = async (page = pagination.current, size = pagination.pageSize, query = filters) => {
    setLoading(true);
    try {
      const res = await getThirdPartyCredentialList({
        current: page,
        size,
        ...query,
      });
      if (res.code === 200 && res.data) {
        setList(res.data.records || []);
        setPagination({
          current: res.data.current || page,
          pageSize: res.data.size || size,
          total: res.data.total || 0,
        });
      } else {
        message.error(res.message || '查询失败');
      }
    } catch {
      message.error('查询失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApps();
    fetchData(1, pagination.pageSize, {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setNameInput(filters.name ?? '');
  }, [filters.name]);

  const activeAdvancedFilterCount = [
    filters.appId,
    filters.status,
  ].filter((v) => v !== undefined && v !== null).length;

  const syncListFilterFormFromFilters = () => {
    listFilterForm.setFieldsValue({
      appId: filters.appId,
      status: filters.status,
    });
  };

  const handleAdvancedFilterQuery = async () => {
    const v = await listFilterForm.validateFields();
    const next = { ...filters };
    if (v.appId != null) {
      next.appId = v.appId;
    } else {
      delete next.appId;
    }
    if (v.status != null) {
      next.status = v.status;
    } else {
      delete next.status;
    }
    setFilters(next);
    fetchData(1, pagination.pageSize, next);
    setFilterPopoverOpen(false);
  };

  const handleAdvancedFilterReset = () => {
    listFilterForm.resetFields();
    const next = { ...filters };
    delete next.appId;
    delete next.status;
    setFilters(next);
    fetchData(1, pagination.pageSize, next);
  };

  const applyNameSearch = (raw?: string) => {
    const trimmed = (raw ?? nameInput).trim();
    const next = { ...filters };
    if (trimmed) {
      next.name = trimmed;
    } else {
      delete next.name;
    }
    setFilters(next);
    fetchData(1, pagination.pageSize, next);
  };

  const onCreate = async () => {
    try {
      const v = await form.validateFields();
      const payload: ThirdPartyCredentialDTO = {
        appIds: v.appIds,
        name: v.name,
        allowedIps: v.allowedIps,
        dailyLimit: v.dailyLimit,
        totalCallLimit: v.totalCallLimit,
        totalDaysLimit: v.totalDaysLimit,
        status: v.status,
        remark: v.remark,
        ...(isAdmin ? { allowTrial: !!v.allowTrial, allowQuery: !!v.allowQuery } : {}),
      };
      setSaving(true);
      const res = editingCredential
        ? await updateThirdPartyCredential(editingCredential.id, payload)
        : await createThirdPartyCredential(payload);
      if (res.code === 200) {
        message.success(editingCredential ? '更新成功' : '创建成功');
        const created = res.data;
        const createdSecret = created?.apiSecret;
        if (!editingCredential && createdSecret) {
          setLatestSecret({ apiKey: created.apiKey, apiSecret: createdSecret });
          setSecretVisible(true);
          setRotatedSecretMap((prev) => ({ ...prev, [created.id]: createdSecret }));
        }
        setModalVisible(false);
        setEditingCredential(null);
        form.resetFields();
        fetchData();
      } else {
        message.error(res.message || (editingCredential ? '更新失败' : '创建失败'));
      }
    } catch (error) {
      if (!(error && typeof error === 'object' && 'errorFields' in error)) {
        message.error('保存失败，请重试');
      }
    } finally {
      setSaving(false);
    }
  };

  const onEdit = (row: ThirdPartyCredential) => {
    if (!canManageCredential(row)) return;
    setEditingCredential(row);
    form.setFieldsValue({
      appIds: row.appIds?.length ? row.appIds : [row.appId],
      name: row.name,
      allowedIps: row.allowedIps,
      allowTrial: !!row.allowTrial,
      allowQuery: !!row.allowQuery,
      dailyLimit: row.dailyLimit,
      totalCallLimit: row.totalCallLimit,
      totalDaysLimit: row.totalDaysLimit,
      status: row.status,
      remark: row.remark,
    });
    setModalVisible(true);
  };

  const onDelete = async (id: number) => {
    const res = await deleteThirdPartyCredential(id);
    if (res.code === 200) {
      message.success('删除成功');
      fetchData();
    } else {
      message.error(res.message || '删除失败');
    }
  };

  const confirmDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除该凭证？',
      content: '删除后无法恢复，请谨慎操作。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => onDelete(id),
    });
  };

  const onRotate = async (id: number) => {
    const res = await rotateThirdPartyCredentialSecret(id);
    if (res.code === 200) {
      message.success('Secret已重置');
      const row = res.data as ThirdPartyCredential;
      if (row?.apiSecret) {
        setRotatedSecretMap((prev) => ({ ...prev, [id]: row.apiSecret as string }));
        setShowSecret((prev) => ({ ...prev, [id]: true }));
      }
      setLatestSecret({
        apiKey: row.apiKey,
        apiSecret: row.apiSecret || '',
      });
      setSecretVisible(true);
      fetchData();
    } else {
      message.error(res.message || '重置失败');
    }
  };

  const copyText = async (text?: string) => {
    if (!text) {
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      message.success('复制成功');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const toggleShowSecret = (id: number) => {
    const secret = rotatedSecretMap[id];
    if (!secret) {
      message.info('密钥默认不可见，请先点击"重置密钥"后查看');
      return;
    }
    setShowSecret((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const getCredentialMenuItems = (r: ThirdPartyCredential): MenuProps['items'] => [
    { key: 'edit', disabled: !canManageCredential(r), icon: <EditOutlined />, label: '编辑', onClick: () => onEdit(r) },
    { key: 'rotate', disabled: !canManageCredential(r), icon: <KeyOutlined />, label: '重置密钥', onClick: () => onRotate(r.id) },
    { type: 'divider' },
    { key: 'delete', disabled: !canManageCredential(r), icon: <DeleteOutlined />, danger: true, label: '删除', onClick: () => confirmDelete(r.id) },
  ];

  const boundAppIds = (row: ThirdPartyCredential) => row.appIds?.length ? row.appIds : [row.appId];
  // 保留已绑定但不在当前应用列表中的项目，避免打开编辑表单后无意移除授权。
  const applicationOptions = [
    ...apps.map((app) => ({ label: `${app.appName} (#${app.id})`, value: app.id })),
    ...[...new Set(editingCredential ? boundAppIds(editingCredential) : [])]
      .filter((id) => !apps.some((app) => app.id === id))
      .map((id) => ({ label: `应用 #${id}（不可用）`, value: id })),
  ];
  const renderBoundApps = (row: ThirdPartyCredential) => (
    <Space size={[4, 4]} wrap>
      {boundAppIds(row).map((id) => (
        <Tag key={id} style={{ marginInlineEnd: 0 }}>
          {apps.find((app) => app.id === id)?.appName || '应用'} #{id}
        </Tag>
      ))}
    </Space>
  );

  const renderActions = (credential: ThirdPartyCredential) => (
    <Space size={[4, 4]} wrap>
      <Tag style={{ marginInlineEnd: 0 }}>充值</Tag>
      {credential.allowTrial && <Tag color="blue" style={{ marginInlineEnd: 0 }}>申请试用</Tag>}
      {credential.allowQuery && <Tag color="cyan" style={{ marginInlineEnd: 0 }}>查询授权</Tag>}
      {!isAdmin && hasRestrictedActions(credential) && <Text type="secondary">仅管理员可修改</Text>}
    </Space>
  );

  const interfacePermissions = (
    <>
      <Divider orientation="left">开放接口权限</Divider>
      <Space wrap size={[24, 8]}>
        <Form.Item name="allowTrial" valuePropName="checked" style={{ marginBottom: 8 }}>
          <Checkbox disabled={!isAdmin}>申请试用</Checkbox>
        </Form.Item>
        <Form.Item name="allowQuery" valuePropName="checked" style={{ marginBottom: 8 }}>
          <Checkbox disabled={!isAdmin}>查询授权</Checkbox>
        </Form.Item>
      </Space>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        {isAdmin ? '单独开启后，调用方可通过签名和 IP 白名单，凭应用标识与邮箱操作，无需用户密码或图形验证码。申请试用仍遵守应用的试用规则。' : '仅系统管理员可开启这些接口；已开启的凭证仅管理员可编辑、重置或删除。'}
      </Paragraph>
    </>
  );

  return (
    <Card styles={{ body: { padding: isMobile ? 12 : 24 } }}>
      {/* 标题栏 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: isMobile ? 12 : 16,
      }}>
        <Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>
          三方凭证管理
        </Title>
        {isMobile ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => fetchData()}
             
            >
              刷新
            </Button>
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => { setEditingCredential(null); form.resetFields(); form.setFieldsValue({ status: 1, allowTrial: false, allowQuery: false }); setModalVisible(true); }}
             
            >
              新增
            </Button>
          </div>
        ) : (
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => fetchData()}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingCredential(null); form.resetFields(); form.setFieldsValue({ status: 1, allowTrial: false, allowQuery: false }); setModalVisible(true); }}>新增凭证</Button>
          </Space>
        )}
      </div>

      <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message="同一组凭证可授权多个应用"
        description="充值权限保持原有规则；申请试用、查询授权由系统管理员单独开启，并须设置调用方出口 IP。所有请求均需凭证签名，调用额度由整组凭证共享。" />

      {/* 搜索和筛选 */}
      <div style={isMobile ? { display: 'flex', gap: 8, marginBottom: 12, alignItems: 'center' } : { marginBottom: 16 }}>
        {isMobile ? (
          <>
            <Input
              size="small"
              placeholder="搜索凭证名称"
              allowClear
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              onPressEnter={() => applyNameSearch()}
              style={{ flex: 1, minWidth: 0 }}
            />
            <Button type="primary" onClick={() => applyNameSearch()} style={{ flexShrink: 0 }}>搜索</Button>
            <Badge count={activeAdvancedFilterCount} size="small" offset={[-2, 2]}>
              <Button icon={<FilterOutlined />} style={{ flexShrink: 0 }} onClick={() => { setFilterPopoverOpen(true); syncListFilterFormFromFilters(); }}>筛选</Button>
            </Badge>
            <M5BottomSheet open={filterPopoverOpen} onClose={() => setFilterPopoverOpen(false)} title="筛选条件" footer={<><Button onClick={handleAdvancedFilterReset} style={{ flex: 1, height: 44, borderRadius: 10 }}>重置</Button><Button type="primary" onClick={() => { void handleAdvancedFilterQuery(); setFilterPopoverOpen(false); }} style={{ flex: 2, height: 44, borderRadius: 10 }}>查询</Button></>}>
              <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item label="应用" name="appId">
                      <Select allowClear placeholder="选择应用" options={apps.map((app) => ({ label: app.appName, value: app.id }))} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item label="状态" name="status">
                      <Select allowClear placeholder="状态" options={[{ label: '启用', value: 1 }, { label: '禁用', value: 0 }]} />
                    </Form.Item>
                  </Col>
                </Row>
              </Form>
            </M5BottomSheet>
          </>
        ) : (
          <Space size={12}>
            <Space.Compact style={{ width: 360, maxWidth: 'calc(100vw - 120px)' }}>
              <Input placeholder="搜索凭证名称" allowClear value={nameInput} onChange={(e) => setNameInput(e.target.value)} onPressEnter={() => applyNameSearch()} style={{ minWidth: 0 }} />
              <Button type="primary" onClick={() => applyNameSearch()}>搜索</Button>
            </Space.Compact>
            <Popover
              trigger="click"
              placement="bottomLeft"
              open={filterPopoverOpen}
              onOpenChange={(open) => { setFilterPopoverOpen(open); if (open) syncListFilterFormFromFilters(); }}
              content={
                <div style={{ width: 420, maxWidth: '90vw' }}>
                  <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item label="应用" name="appId">
                          <Select allowClear placeholder="选择应用" options={apps.map((app) => ({ label: app.appName, value: app.id }))} />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item label="状态" name="status">
                          <Select allowClear placeholder="状态" options={[{ label: '启用', value: 1 }, { label: '禁用', value: 0 }]} />
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
          {!loading && list.length === 0 && <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无数据</div>}
          {list.map((r) => (
            <div key={r.id} className="mgmt-mobile-card" style={{ borderLeft: `3px solid ${r.status === 1 ? '#52c41a' : '#d9d9d9'}` }}>
              <div className="mgmt-mobile-card-header">
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Text strong style={{ fontSize: 14 }}>{r.name || '-'}</Text>
                    <Tag color={r.status === 1 ? 'success' : 'default'} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>{r.status === 1 ? '启用' : '禁用'}</Tag>
                  </div>
                  {r.apiKey && (
                    <Text type="secondary" style={{ fontSize: 11, fontFamily: 'Consolas, Monaco, monospace', display: 'block', marginTop: 2 }} ellipsis={{ tooltip: r.apiKey }}>
                      Key: {r.apiKey.substring(0, 16)}...
                    </Text>
                  )}
                  <div style={{ marginTop: 6 }}>{renderBoundApps(r)}</div>
                  <div style={{ marginTop: 6 }}>{renderActions(r)}</div>
                  <div className="mgmt-mobile-card-meta" style={{ marginTop: 4 }}>
                    <Text type="secondary">日限: {r.dailyLimit ?? '-'}</Text>
                    <Text type="secondary">|</Text>
                    <Text type="secondary">已用: {r.usedCallCount || 0}次 / {r.usedDaysCount || 0}天</Text>
                  </div>
                </div>
                <Dropdown menu={{ items: getCredentialMenuItems(r) }} trigger={['click']}>
                  <Button aria-label="更多操作" type="text" size="small" icon={<MoreOutlined />} style={{ width: 32, height: 32 }} />
                </Dropdown>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Table<ThirdPartyCredential>
          rowKey="id"
          loading={loading}
          dataSource={list}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            onChange: (page, size) => fetchData(page, size, filters),
          }}
          columns={[
            { title: 'ID', dataIndex: 'id', width: 80 },
            { title: '绑定应用', key: 'appIds', width: 240, render: (_, row) => renderBoundApps(row) },
            { title: '凭证名称', dataIndex: 'name', width: 180 },
            { title: '开放接口', key: 'actions', width: 190, render: (_, row) => renderActions(row) },
            {
              title: 'API Key',
              dataIndex: 'apiKey',
              width: 200,
              render: (text: string) => {
                if (!text) return <Text type="secondary">-</Text>;
                return (
                  <Text
                    copyable={{ text, tooltips: ['复制', '已复制'] }}
                    style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, color: '#666' }}
                  >
                    {text.substring(0, 12)}...
                  </Text>
                );
              },
            },
            {
              title: 'API Secret',
              key: 'apiSecret',
              width: 210,
              render: (_, r) => {
                const secret = rotatedSecretMap[r.id];
                const visible = !!showSecret[r.id] && !!secret;
                return (
                  <Space size="small">
                    <Text style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, color: '#666' }}>
                      {visible ? `${secret.substring(0, 12)}...` : '••••••••••••'}
                    </Text>
                    <Button type="text" size="small" icon={visible ? <EyeInvisibleOutlined /> : <EyeOutlined />} onClick={() => toggleShowSecret(r.id)} />
                    {visible && <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => copyText(secret)} />}
                  </Space>
                );
              },
            },
            { title: '状态', dataIndex: 'status', width: 90, render: (v: number) => <Tag color={v === 1 ? 'success' : 'default'}>{v === 1 ? '启用' : '禁用'}</Tag> },
            { title: '日调用上限', dataIndex: 'dailyLimit', width: 110, render: (v?: number) => v ?? '-' },
            { title: '总调用上限', dataIndex: 'totalCallLimit', width: 110, render: (v?: number) => v ?? '-' },
            { title: '总消耗天数上限', dataIndex: 'totalDaysLimit', width: 130, render: (v?: number) => v ?? '-' },
            { title: '已用次数/天数', width: 140, render: (_, r) => `${r.usedCallCount || 0} / ${r.usedDaysCount || 0}` },
            {
              title: '操作', width: 90, fixed: 'right', align: 'center' as const,
              render: (_, r) => (
                <Dropdown menu={{ items: getCredentialMenuItems(r) }} trigger={['click']}>
                  <Button aria-label="更多操作" type="text" size="small" icon={<MoreOutlined />} />
                </Dropdown>
              ),
            },
          ]}
          scroll={{ x: 1500 }}
        />
      )}

      {/* 移动端分页 */}
      {isMobile && pagination.total > 0 && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
          <Pagination current={pagination.current} pageSize={pagination.pageSize} total={pagination.total} onChange={(page, size) => fetchData(page, size, filters)} size="small" simple />
        </div>
      )}

      {isMobile ? (
        <M5BottomSheet
          open={modalVisible}
          onClose={() => { setModalVisible(false); setEditingCredential(null); }}
          title={editingCredential ? '编辑三方凭证' : '新增三方凭证'}
          footer={
            <>
              <Button onClick={() => { setModalVisible(false); setEditingCredential(null); }} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" loading={saving} onClick={onCreate} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button>
            </>
          }
        >
          <Form
            form={form}
            layout="vertical"
            initialValues={{ status: 1, allowTrial: false, allowQuery: false }}
          >
            <Form.Item name="appIds" label="授权应用" rules={[{ required: true, type: 'array', min: 1, max: 100, message: '请选择1至100个应用' }]}
              extra="每次调用仅作用于 projectKey 指定的应用；修改绑定不会重置 Key / Secret。">
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                maxTagCount="responsive"
                options={applicationOptions}
                placeholder="可搜索并选择多个应用"
              />
            </Form.Item>
            <Form.Item name="name" label="凭证名称" rules={[{ required: true, message: '请输入凭证名称' }]}>
              <Input maxLength={120} />
            </Form.Item>
            {interfacePermissions}
            <Form.Item name="allowedIps" label="IP 白名单" dependencies={['allowTrial', 'allowQuery']}
              extra="填写调用服务器的公网出口 IP，多个用英文逗号分隔；支持精确 IPv4 / IPv6，不支持网段或通配符。"
              rules={[({ getFieldValue }) => ({
                validator(_, value: string | undefined) {
                  if ((getFieldValue('allowTrial') || getFieldValue('allowQuery')) && !value?.trim()) {
                    return Promise.reject(new Error('开启申请试用或查询授权时，必须填写 IP 白名单'));
                  }
                  if (value && /[/*]/.test(value)) {
                    return Promise.reject(new Error('请填写精确 IP，不支持网段或通配符'));
                  }
                  return Promise.resolve();
                },
              })]}>
              <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} placeholder="例如：203.0.113.10,2001:db8::10" />
            </Form.Item>
            <Space wrap style={{ width: '100%' }} align="start">
              <Form.Item name="dailyLimit" label="每日调用上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
              <Form.Item name="totalCallLimit" label="总调用上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
              <Form.Item name="totalDaysLimit" label="总消费天数上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
            </Space>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '禁用', value: 0 },
                ]}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={3} maxLength={500} />
            </Form.Item>
            <Text type="secondary">创建后会展示一次 API Key / Secret，请立即保存。编辑授权应用不会改变原密钥。</Text>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title={editingCredential ? '编辑三方凭证' : '新增三方凭证'}
          open={modalVisible}
          onCancel={() => {
            setModalVisible(false);
            setEditingCredential(null);
          }}
          onOk={onCreate}
          confirmLoading={saving}
          width={720}
        >
          <Form
            form={form}
            layout="vertical"
            initialValues={{ status: 1, allowTrial: false, allowQuery: false }}
          >
            <Form.Item name="appIds" label="授权应用" rules={[{ required: true, type: 'array', min: 1, max: 100, message: '请选择1至100个应用' }]}
              extra="每次调用仅作用于 projectKey 指定的应用；修改绑定不会重置 Key / Secret。">
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                maxTagCount="responsive"
                options={applicationOptions}
                placeholder="可搜索并选择多个应用"
              />
            </Form.Item>
            <Form.Item name="name" label="凭证名称" rules={[{ required: true, message: '请输入凭证名称' }]}>
              <Input maxLength={120} />
            </Form.Item>
            {interfacePermissions}
            <Form.Item name="allowedIps" label="IP 白名单" dependencies={['allowTrial', 'allowQuery']}
              extra="填写调用服务器的公网出口 IP，多个用英文逗号分隔；支持精确 IPv4 / IPv6，不支持网段或通配符。"
              rules={[({ getFieldValue }) => ({
                validator(_, value: string | undefined) {
                  if ((getFieldValue('allowTrial') || getFieldValue('allowQuery')) && !value?.trim()) {
                    return Promise.reject(new Error('开启申请试用或查询授权时，必须填写 IP 白名单'));
                  }
                  if (value && /[/*]/.test(value)) {
                    return Promise.reject(new Error('请填写精确 IP，不支持网段或通配符'));
                  }
                  return Promise.resolve();
                },
              })]}>
              <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} placeholder="例如：203.0.113.10,2001:db8::10" />
            </Form.Item>
            <Space wrap style={{ width: '100%' }} align="start">
              <Form.Item name="dailyLimit" label="每日调用上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
              <Form.Item name="totalCallLimit" label="总调用上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
              <Form.Item name="totalDaysLimit" label="总消费天数上限">
                <InputNumber min={1} style={{ width: isMobile ? 120 : 180 }} />
              </Form.Item>
            </Space>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '禁用', value: 0 },
                ]}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={3} maxLength={500} />
            </Form.Item>
            <Text type="secondary">创建后会展示一次 API Key / Secret，请立即保存。编辑授权应用不会改变原密钥。</Text>
          </Form>
        </Modal>
      )}
      <Modal
        title="三方凭证密钥（请立即保存）"
        open={secretVisible}
        onCancel={() => setSecretVisible(false)}
        footer={[
          <Button key="ok" type="primary" onClick={() => setSecretVisible(false)}>
            我已保存
          </Button>,
        ]}
        width={640}
      >
        <Alert
          type="warning"
          showIcon
          message="请立即复制并妥善保存"
          description="刷新页面后无法再次查看明文 Secret。若未保存，需要重置密钥并更新调用方配置。"
        />
        <Divider style={{ margin: '16px 0' }} />
        <Space direction="vertical" style={{ width: '100%' }} size={14}>
          <div>
            <Text type="secondary">API Key</Text>
            <div style={{ marginTop: 6, display: 'flex', gap: 8, alignItems: 'center' }}>
              <Input
                readOnly
                value={latestSecret?.apiKey || ''}
                style={{ fontFamily: 'Consolas, Monaco, monospace' }}
              />
              <Button icon={<CopyOutlined />} onClick={() => copyText(latestSecret?.apiKey)}>
                复制
              </Button>
            </div>
          </div>
          <div>
            <Text type="secondary">API Secret</Text>
            <div style={{ marginTop: 6, display: 'flex', gap: 8, alignItems: 'center' }}>
              <Input
                readOnly
                value={latestSecret?.apiSecret || ''}
                style={{ fontFamily: 'Consolas, Monaco, monospace' }}
              />
              <Button icon={<CopyOutlined />} onClick={() => copyText(latestSecret?.apiSecret)}>
                复制
              </Button>
            </div>
          </div>
        </Space>
      </Modal>
    </Card>
  );
};

export default ThirdPartyCredentialManagementContent;
