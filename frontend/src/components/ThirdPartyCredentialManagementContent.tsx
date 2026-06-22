import React, { useEffect, useState } from 'react';
import {
  Badge,
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
} from '../services/thirdPartyCredentialService';
import { getApplicationList, type Application } from '../services/applicationService';

const { Text, Title } = Typography;

const ThirdPartyCredentialManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<ThirdPartyCredential[]>([]);
  const [apps, setApps] = useState<Application[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCredential, setEditingCredential] = useState<ThirdPartyCredential | null>(null);
  const [secretVisible, setSecretVisible] = useState(false);
  const [latestSecret, setLatestSecret] = useState<{ apiKey: string; apiSecret: string } | null>(null);
  const [showSecret, setShowSecret] = useState<Record<number, boolean>>({});
  const [rotatedSecretMap, setRotatedSecretMap] = useState<Record<number, string>>({});
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState<{ appId?: number; status?: number; name?: string }>({});
  const [nameInput, setNameInput] = useState('');
  const [filterPopoverOpen, setFilterPopoverOpen] = useState(false);
  const [listFilterForm] = Form.useForm();

  const fetchApps = async () => {
    try {
      const res: any = await getApplicationList({ current: 1, size: 1000 });
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
      const res: any = await getThirdPartyCredentialList({
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
        appId: v.appId,
        name: v.name,
        allowedIps: v.allowedIps,
        dailyLimit: v.dailyLimit,
        totalCallLimit: v.totalCallLimit,
        totalDaysLimit: v.totalDaysLimit,
        status: v.status,
        remark: v.remark,
      };
      const res: any = editingCredential
        ? await updateThirdPartyCredential(editingCredential.id, payload)
        : await createThirdPartyCredential(payload);
      if (res.code === 200) {
        message.success(editingCredential ? '更新成功' : '创建成功');
        setModalVisible(false);
        setEditingCredential(null);
        form.resetFields();
        fetchData();
      } else {
        message.error(res.message || (editingCredential ? '更新失败' : '创建失败'));
      }
    } catch {
      // validation
    }
  };

  const onEdit = (row: ThirdPartyCredential) => {
    setEditingCredential(row);
    form.setFieldsValue({
      appId: row.appId,
      name: row.name,
      allowedIps: row.allowedIps,
      dailyLimit: row.dailyLimit,
      totalCallLimit: row.totalCallLimit,
      totalDaysLimit: row.totalDaysLimit,
      status: row.status,
      remark: row.remark,
    });
    setModalVisible(true);
  };

  const onDelete = async (id: number) => {
    const res: any = await deleteThirdPartyCredential(id);
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
    const res: any = await rotateThirdPartyCredentialSecret(id);
    if (res.code === 200) {
      message.success('Secret已重置');
      const row = res.data as ThirdPartyCredential;
      if (row?.apiSecret) {
        setRotatedSecretMap((prev) => ({ ...prev, [id]: row.apiSecret as string }));
        setShowSecret((prev) => ({ ...prev, [id]: true }));
      }
      setLatestSecret({
        apiKey: row.apiKey,
        apiSecret: row.apiSecret,
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
      message.info('密钥默认不可见，请先点击“重置密钥”后查看');
      return;
    }
    setShowSecret((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const getCredentialMenuItems = (r: ThirdPartyCredential): MenuProps['items'] => [
    { key: 'edit', icon: <EditOutlined />, label: '编辑', onClick: () => onEdit(r) },
    { key: 'rotate', icon: <KeyOutlined />, label: '重置密钥', onClick: () => onRotate(r.id) },
    { type: 'divider' },
    { key: 'delete', icon: <DeleteOutlined />, danger: true, label: '删除', onClick: () => confirmDelete(r.id) },
  ];

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
              onClick={() => { setEditingCredential(null); form.resetFields(); form.setFieldsValue({ status: 1 }); setModalVisible(true); }}
             
            >
              新增
            </Button>
          </div>
        ) : (
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => fetchData()}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingCredential(null); form.resetFields(); form.setFieldsValue({ status: 1 }); setModalVisible(true); }}>新增凭证</Button>
          </Space>
        )}
      </div>

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
                <Button icon={<FilterOutlined />} style={{ flexShrink: 0 }}>筛选</Button>
              </Badge>
            </Popover>
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
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {loading && <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>加载中...</div>}
          {!loading && list.length === 0 && <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无数据</div>}
          {list.map((r) => (
            <div key={r.id} style={{ background: '#fafafa', borderRadius: 8, padding: '10px 12px', border: '1px solid #f0f0f0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Text strong style={{ fontSize: 14 }}>{r.name || '-'}</Text>
                    <Tag color={r.status === 1 ? 'success' : 'default'} style={{ margin: 0 }}>{r.status === 1 ? '启用' : '禁用'}</Tag>
                  </div>
                  {r.apiKey && (
                    <Text type="secondary" style={{ fontSize: 11, fontFamily: 'Consolas, Monaco, monospace', display: 'block', marginTop: 2 }} ellipsis={{ tooltip: r.apiKey }}>
                      Key: {r.apiKey.substring(0, 16)}...
                    </Text>
                  )}
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 4, fontSize: 12, color: '#666' }}>
                    <Text type="secondary">日限: {r.dailyLimit ?? '-'}</Text>
                    <Text type="secondary">|</Text>
                    <Text type="secondary">已用: {r.usedCallCount || 0}次 / {r.usedDaysCount || 0}天</Text>
                  </div>
                </div>
                <Dropdown menu={{ items: getCredentialMenuItems(r) }} trigger={['click']}>
                  <Button type="text" size="small" icon={<MoreOutlined />} />
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
            { title: '应用ID', dataIndex: 'appId', width: 90 },
            { title: '凭证名称', dataIndex: 'name', width: 180 },
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
                  <Button type="text" size="small" icon={<MoreOutlined />} />
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

      <Modal
        title={editingCredential ? '编辑三方凭证' : '新增三方凭证'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditingCredential(null);
        }}
        onOk={onCreate}
        width={isMobile ? '100%' : 720}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ status: 1 }}
        >
          <Form.Item name="appId" label="绑定应用" rules={[{ required: true, message: '请选择应用' }]}>
            <Select
              options={apps.map((a) => ({ label: `${a.appName} (#${a.id})`, value: a.id }))}
              placeholder="请选择应用"
              disabled={!!editingCredential}
            />
          </Form.Item>
          <Form.Item name="name" label="凭证名称" rules={[{ required: true, message: '请输入凭证名称' }]}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="allowedIps" label="IP白名单（多个用逗号分隔）">
            <Input placeholder="例如: 1.1.1.1,2.2.2.2" />
          </Form.Item>
          <Space style={{ width: '100%' }} align="start">
            <Form.Item name="dailyLimit" label="每日调用上限">
              <InputNumber min={1} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="totalCallLimit" label="总调用上限">
              <InputNumber min={1} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="totalDaysLimit" label="总消费天数上限">
              <InputNumber min={1} style={{ width: 180 }} />
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
          <Text type="secondary">创建后会生成 API Key。API Secret 默认不展示，请通过“重置密钥”查看并复制。</Text>
        </Form>
      </Modal>
      <Modal
        title="已重置密钥（仅本次可见）"
        open={secretVisible}
        onCancel={() => setSecretVisible(false)}
        footer={[
          <Button key="ok" type="primary" onClick={() => setSecretVisible(false)}>
            我已保存
          </Button>,
        ]}
        width={isMobile ? '100%' : 640}
        className={isMobile ? 'mobile-modal' : undefined}
      >
        <Alert
          type="warning"
          showIcon
          message="请立即复制并妥善保存"
          description="关闭弹窗后将不再展示明文 API Secret。"
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
