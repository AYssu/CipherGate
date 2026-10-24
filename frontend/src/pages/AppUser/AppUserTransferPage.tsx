import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { Alert, Badge, Button, Card, Checkbox, Collapse, Col, Empty, Form, Grid, Input, Modal, Pagination, Popover, Row,
  Segmented, Select, Space, Spin, Switch, Table, Tag, Typography, message, theme } from 'antd';
import { CopyOutlined, FilterOutlined, ReloadOutlined, SearchOutlined, SwapOutlined } from '@ant-design/icons';
import { executeTransfer, getTransferApplications, getTransferUsers, previewTransfer } from '../../services/appUserTransferService';
import M5BottomSheet from '../../components/M5BottomSheet';
import './AppUserTransferPage.css';
import type { TransferApplication, TransferItem, TransferRequest, TransferResult, TransferUser, TransferUserFilters } from '../../services/appUserTransferService';

const { Title, Text, Paragraph } = Typography;
const actionLabels: Record<TransferItem['action'], string> = { CREATE: '新建', OVERWRITE: '覆盖', RESTORE: '恢复', SKIP: '跳过', BLOCK: '阻止' };
const filterLabels: Record<keyof TransferUserFilters, string> = { username: '用户名', nickname: '昵称', email: '邮箱', phone: '手机号', memberStatus: '会员状态' };
const memberLabels = { ACTIVE: '未到期', EXPIRED: '已到期', NONE: '未开通' };
const actionColors: Record<TransferItem['action'], string> = { CREATE: 'success', OVERWRITE: 'warning', RESTORE: 'processing', SKIP: 'default', BLOCK: 'error' };

/** 与其他管理页一致：桌面表格、移动卡片；用户跨页勾选，不隐式选中整个应用。 */
export default function AppUserTransferPage() {
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [apps, setApps] = useState<TransferApplication[]>([]);
  const appCache = useRef(new Map<number, TransferApplication>());
  const [appLoading, setAppLoading] = useState(false);
  const [appError, setAppError] = useState(false);
  const appQuery = useRef({ keyword: '', current: 1, total: 0 });
  const appSequence = useRef(0);
  const appSearchTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [sourceId, setSourceId] = useState<number>();
  const [targetId, setTargetId] = useState<number>();
  const [mode, setMode] = useState<TransferRequest['mode']>('COPY');
  const [force, setForce] = useState(false);
  const [rows, setRows] = useState<TransferUser[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [filters, setFilters] = useState<TransferUserFilters>({});
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterForm] = Form.useForm<TransferUserFilters>();
  const [selectedOnly, setSelectedOnly] = useState(false);
  const selectedIds = selectedOnly ? selected.join(',') : undefined;
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState(false);
  const userSequence = useRef(0);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<TransferResult>();
  const [previewRequest, setPreviewRequest] = useState<TransferRequest>();
  const [modalOpen, setModalOpen] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  const loadApps = useCallback(async (search = '', page = 1) => {
    const sequence = ++appSequence.current;
    setAppLoading(true);
    setAppError(false);
    try {
      const { data } = await getTransferApplications({ keyword: search, current: page, size: 50 });
      if (sequence !== appSequence.current) return;
      data.records.forEach(a => appCache.current.set(a.id, a));
      appQuery.current = { keyword: search, current: page, total: data.total };
      setApps(previous => page === 1 ? data.records : [...previous, ...data.records]);
    } catch {
      if (sequence === appSequence.current) setAppError(true);
    } finally {
      if (sequence === appSequence.current) setAppLoading(false);
    }
  }, []);

  const loadUsers = useCallback(async () => {
    const sequence = ++userSequence.current;
    if (!sourceId || (selectedOnly && !selectedIds)) { setRows([]); setTotal(0); setListLoading(false); setListError(false); return; }
    setListLoading(true);
    setListError(false);
    try {
      const { data } = await getTransferUsers({ appId: sourceId, keyword, ...filters, userIds: selectedIds, current, size });
      if (sequence !== userSequence.current) return;
      setRows(data.records);
      setTotal(data.total);
    } catch {
      if (sequence === userSequence.current) { setListError(true); setRows([]); setTotal(0); }
    } finally {
      if (sequence === userSequence.current) setListLoading(false);
    }
  }, [sourceId, keyword, filters, selectedOnly, selectedIds, current, size]);

  useEffect(() => {
    const sequence = appSequence;
    void loadApps();
    return () => { sequence.current++; clearTimeout(appSearchTimer.current); };
  }, [loadApps]);
  useEffect(() => {
    const sequence = userSequence;
    void loadUsers();
    return () => { sequence.current++; };
  }, [loadUsers]);

  const invalidate = () => { setPreview(undefined); setPreviewRequest(undefined); setConfirmed(false); };
  const changeSelection = (ids: number[]) => {
    if (ids.length > 100) { message.warning('每批最多选择 100 位用户'); return; }
    setSelected(ids); if (selectedOnly) setCurrent(1); invalidate();
  };
  const appOptions = [...apps];
  [sourceId, targetId].forEach(id => {
    if (id && !appOptions.some(a => a.id === id) && appCache.current.has(id)) appOptions.push(appCache.current.get(id)!);
  });
  const options = appOptions.map(a => ({ value: a.id, label: `${a.appName} (#${a.id})` }));
  const selectProps = {
    showSearch: true, filterOption: false, loading: appLoading, disabled: busy, style: { width: '100%' },
    onOpenChange: (open: boolean) => { if (open && appQuery.current.keyword) void loadApps(); },
    onSearch: (value: string) => {
      clearTimeout(appSearchTimer.current);
      appSearchTimer.current = setTimeout(() => { void loadApps(value); }, 300);
    },
    onPopupScroll: (event: React.UIEvent<HTMLDivElement>) => {
      const element = event.currentTarget;
      if (!appLoading && element.scrollTop + element.clientHeight >= element.scrollHeight - 24
          && appQuery.current.current * 50 < appQuery.current.total) {
        void loadApps(appQuery.current.keyword, appQuery.current.current + 1);
      }
    },
    notFoundContent: appLoading ? <Spin size="small" /> : '无可操作应用（需应用所有者或管理员）',
  };

  const check = async () => {
    if (!sourceId || !targetId || sourceId === targetId || !selected.length) return;
    const request: TransferRequest = { sourceAppId: sourceId, targetAppId: targetId, mode, forceOverwrite: force, userIds: [...selected] };
    setBusy(true);
    try {
      const { data } = await previewTransfer(request);
      setPreview(data); setPreviewRequest(request); setConfirmed(false); setModalOpen(true);
    } catch { /* request 拦截器统一显示错误 */ }
    finally { setBusy(false); }
  };

  const execute = async () => {
    if (!preview || !previewRequest || !confirmed || busy || preview.executed || preview.blockCount) return;
    setBusy(true);
    try {
      const { data } = await executeTransfer({ ...previewRequest, previewToken: preview.previewToken });
      setPreview(data); setSelected([]); setConfirmed(false);
      message.success(`处理完成：新建 ${data.createCount}、覆盖 ${data.overwriteCount}、恢复 ${data.restoreCount ?? 0}、跳过 ${data.skipCount}`);
      await loadUsers();
    } catch {
      // 未知网络结果也要求重新预检查；后端快照验证防止原请求重复执行。
      setPreview(undefined); setPreviewRequest(undefined); setModalOpen(false);
    } finally { setBusy(false); }
  };

  const summary = preview && <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
    <Tag color="success">新建 {preview.createCount}</Tag><Tag color="warning">覆盖 {preview.overwriteCount}</Tag>
    <Tag color="processing">恢复 {preview.restoreCount ?? 0}</Tag><Tag>跳过 {preview.skipCount}</Tag><Tag color={preview.blockCount ? 'error' : 'default'}>阻止 {preview.blockCount}</Tag>
  </div>;
  const previewColumns = [
    { title: '源用户', dataIndex: 'username', render: (value: string, r: TransferItem) => <span>{value || '不存在'} <Text type="secondary">#{r.sourceUserId}</Text></span> },
    { title: '目标用户', render: (_: unknown, r: TransferItem) => r.targetUserId ? `${r.targetUsername} (#${r.targetUserId})` : '新账号' },
    { title: '处理', dataIndex: 'action', render: (value: TransferItem['action']) => <Tag color={actionColors[value]}>{actionLabels[value]}</Tag> },
    { title: '说明 / 安全限制', render: (_: unknown, r: TransferItem) => <Space direction="vertical" size={0}>
      <Text>{r.reason}</Text><Text type="secondary">设备 {r.deviceCount} · 源封禁 {r.bannedDeviceCount}{r.trialRecorded ? ' · 保留已领取试用' : ''}</Text>
    </Space> },
  ];

  const activeFilters = Object.entries(filters).filter(([, value]) => Boolean(value)) as [keyof TransferUserFilters, string][];
  const openFilters = (open: boolean) => {
    if (open) filterForm.setFieldsValue({ username: undefined, nickname: undefined, email: undefined, phone: undefined, memberStatus: undefined, ...filters });
    setFilterOpen(open);
  };
  const applyFilters = async () => {
    const values = await filterForm.validateFields();
    setFilters(Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === 'string' ? value.trim() || undefined : value])));
    setCurrent(1); setFilterOpen(false);
  };
  const resetFilters = () => {
    filterForm.resetFields(); setFilters({}); setKeyword(''); setKeywordInput(''); setCurrent(1);
  };
  const searchUsers = () => { setKeyword(keywordInput.trim()); setCurrent(1); };
  const filterFields = <Form form={filterForm} layout="vertical" onFinish={() => void applyFilters()}>
    <Row gutter={12}>
      {(['username', 'nickname', 'email', 'phone'] as const).map(field => <Col span={12} key={field}>
        <Form.Item label={filterLabels[field]} name={field}><Input allowClear placeholder={`筛选${filterLabels[field]}`} disabled={busy} /></Form.Item>
      </Col>)}
      <Col span={24}><Form.Item label="会员状态" name="memberStatus"><Select allowClear placeholder="全部会员状态" disabled={busy}
        options={Object.entries(memberLabels).map(([value, label]) => ({ value, label }))} /></Form.Item></Col>
    </Row>
    {!isMobile && <div className="transfer-filter-actions"><Button disabled={busy} onClick={resetFilters}>重置</Button><Button type="primary" disabled={busy} htmlType="submit">查询</Button></div>}
  </Form>;
  const filterButton = <Badge count={activeFilters.length} size="small"><Button icon={<FilterOutlined />} disabled={!sourceId || busy}
    onClick={isMobile ? () => openFilters(true) : undefined}>筛选</Button></Badge>;

  return <Card className="app-user-transfer" style={{ '--transfer-border': token.colorBorderSecondary, '--transfer-selected-bg': token.colorPrimaryBg,
    '--transfer-selected-border': token.colorPrimaryBorder, '--transfer-primary': token.colorPrimary } as CSSProperties}
    styles={{ body: { padding: isMobile ? 12 : 24 } }}>
    <div className="transfer-page-content">
      <div className="transfer-heading">
        <div><Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>用户迁移</Title>
          <Text type="secondary">选择源用户，复制或迁移到指定应用。每批最多 100 位。</Text></div>
        <Button size={isMobile ? 'small' : 'middle'} icon={<ReloadOutlined />} disabled={!sourceId || busy} loading={listLoading}
          aria-label="刷新用户列表" onClick={() => void loadUsers()}>刷新</Button>
      </div>
      {appError && <Alert type="error" showIcon message="应用列表加载失败" action={<Button size="small" onClick={() => void loadApps()}>重试</Button>} />}
      <div className="transfer-config">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={12}><Text strong>源应用</Text><div style={{ marginTop: 8 }}><Select {...selectProps} aria-label="源应用" placeholder="选择需要处理用户的应用" value={sourceId}
            options={options} onChange={(id: number) => { setSourceId(id); setSelected([]); setSelectedOnly(false); setCurrent(1); resetFilters(); if (targetId === id) setTargetId(undefined); invalidate(); }} /></div></Col>
          <Col xs={24} md={12}><Text strong>目标应用</Text><div style={{ marginTop: 8 }}><Select {...selectProps} aria-label="目标应用" placeholder="选择接收用户的应用" value={targetId}
            options={options.map(o => ({ ...o, disabled: o.value === sourceId }))} onChange={(id: number) => { setTargetId(id); invalidate(); }} /></div></Col>
        </Row>
        <div className="transfer-options">
          <div className="transfer-mode"><Text strong>操作方式</Text>
            <Segmented aria-label="操作类型" value={mode} disabled={busy} block
              onChange={value => { setMode(value as TransferRequest['mode']); invalidate(); }}
              options={[{ label: <span><CopyOutlined /> 复制</span>, value: 'COPY' }, { label: <span><SwapOutlined /> 迁移</span>, value: 'MOVE' }]} />
            <Text type="secondary">{mode === 'COPY' ? '保留源账号，在目标应用创建副本' : '成功后停用源账号，并断开其在线会话'}</Text>
          </div>
          <div className="transfer-overwrite"><div><Text strong>强制覆盖</Text><Switch aria-label="强制覆盖" checked={force} disabled={busy}
            onChange={value => { setForce(value); invalidate(); }} /></div><Text type="secondary">{force ? '覆盖唯一匹配的目标账号' : '默认跳过有效账号冲突，已删除匹配账号自动恢复'}</Text></div>
        </div>
        {force && <Alert type="warning" showIcon message="覆盖将替换目标账号的密码、资料和会员到期时间，不会累加会员时长" />}
        <Collapse ghost size="small" className="transfer-rules" items={[{ key: 'rules', label: '查看迁移范围与安全规则', children:
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>迁移账号资料、原密码、会员到期时间及设备限制；目标使用自己的 App Key 登录。
            订单、卡密权益、代理归属、登录历史和在线会话不转移。封禁只合并不解除，已领取试用不会重新发放。
            强制覆盖仅处理唯一匹配的账号，保留目标 ID、代理归属和历史记录；已删除的唯一匹配账号自动恢复并按源账号更新，无需强制覆盖；歧义或不同身份冲突仍会阻止。</Paragraph> }]} />
      </div>
      <div className="transfer-user-section">
        <div className="transfer-list-heading"><Text strong>选择用户</Text><Text type="secondary">共 {total} 位{selectedOnly ? '已选匹配' : '匹配'}用户</Text></div>
        <div className="transfer-search-row">
          <Input placeholder="搜索用户名 / 邮箱" aria-label="搜索源用户" allowClear disabled={!sourceId || busy} value={keywordInput}
            onChange={e => { setKeywordInput(e.target.value); if (!e.target.value) { setKeyword(''); setCurrent(1); } }} onPressEnter={searchUsers} />
          <Button type="primary" icon={isMobile ? undefined : <SearchOutlined />} disabled={!sourceId || busy} onClick={searchUsers}>搜索</Button>
          {isMobile ? filterButton : <Popover trigger="click" placement="bottomRight" open={filterOpen} onOpenChange={openFilters}
            content={<div style={{ width: 360 }}>{filterFields}</div>} title="筛选条件">{filterButton}</Popover>}
        </div>
        {isMobile && <M5BottomSheet open={filterOpen} onClose={() => setFilterOpen(false)} title="筛选条件"
          footer={<><Button disabled={busy} onClick={resetFilters} style={{ flex: 1 }}>重置</Button><Button type="primary" disabled={busy} onClick={() => void applyFilters()} style={{ flex: 2 }}>查询</Button></>}>{filterFields}</M5BottomSheet>}
        {(keyword || activeFilters.length > 0) && <div className="transfer-active-filters">
          {keyword && <Tag closable onClose={() => { setKeyword(''); setKeywordInput(''); setCurrent(1); }}>搜索：{keyword}</Tag>}
          {activeFilters.map(([key, value]) => <Tag key={key} closable onClose={() => { setFilters(previous => ({ ...previous, [key]: undefined })); setCurrent(1); }}>
            {filterLabels[key]}：{key === 'memberStatus' ? memberLabels[value as keyof typeof memberLabels] : value}</Tag>)}
          <Button type="link" size="small" disabled={busy} onClick={resetFilters}>重置筛选</Button>
        </div>}
        <div className="transfer-selection-bar">
          <div><Checkbox checked={selectedOnly} disabled={!sourceId || busy} onChange={e => { setSelectedOnly(e.target.checked); setCurrent(1); }}>仅看已选</Checkbox>
            <Text>已选 {selected.length} / 100</Text></div>
          <Button type="link" size="small" disabled={!selected.length || busy} onClick={() => changeSelection([])}>清空选择</Button>
        </div>
      {listError ? <Alert type="error" showIcon message="源用户加载失败" action={<Button size="small" onClick={() => void loadUsers()}>重试</Button>} /> :
        isMobile ? <Spin spinning={listLoading}><div className="mgmt-mobile-list">
          {!rows.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={sourceId ? selectedOnly && !selected.length ? '尚未选择用户，取消「仅看已选」后勾选' : '暂无匹配用户，请调整筛选条件' : '先选择源应用'} />}
          {rows.map(user => <div className="mgmt-mobile-card" key={user.id} style={{ overflowWrap: 'anywhere' }}>
            <Checkbox checked={selected.includes(user.id)} disabled={busy || (!selected.includes(user.id) && selected.length >= 100)}
              onChange={e => changeSelection(e.target.checked ? [...selected, user.id] : selected.filter(id => id !== user.id))}>
              <Text strong>{user.username}</Text> <Text type="secondary">#{user.id}</Text>
            </Checkbox><div style={{ marginTop: 8, overflowWrap: 'anywhere' }}><Text type="secondary">{user.email || '未设置邮箱'}</Text><br />
              {user.nickname && <><Text type="secondary">昵称：{user.nickname}</Text><br /></>}
              <Text>会员到期：{user.memberExpiresAt?.replace('T', ' ') || '未开通'}</Text></div>
          </div>)}
          {sourceId && <Pagination simple current={current} pageSize={size} total={total} disabled={busy} onChange={page => setCurrent(page)} style={{ marginTop: 16, textAlign: 'center' }} />}
        </div></Spin> : <Table<TransferUser> rowKey="id" dataSource={rows} loading={listLoading} size="middle" scroll={{ x: 800 }}
          locale={{ emptyText: sourceId ? selectedOnly && !selected.length ? '尚未选择用户，取消「仅看已选」后勾选' : '暂无匹配用户，请调整筛选条件' : '先选择源应用' }}
          rowSelection={{ selectedRowKeys: selected, preserveSelectedRowKeys: true, onChange: keys => changeSelection(keys.map(Number)),
            getCheckboxProps: user => ({ disabled: busy || (!selected.includes(user.id) && selected.length >= 100) }) }}
          columns={[{ title: 'ID', dataIndex: 'id', width: 90 }, { title: '用户名', dataIndex: 'username' },
            { title: '邮箱', dataIndex: 'email', render: value => value || '-' }, { title: '昵称', dataIndex: 'nickname', render: value => value || '-' },
            { title: '会员到期时间', dataIndex: 'memberExpiresAt', render: (value?: string) => value?.replace('T', ' ') || '未开通' }]}
          pagination={{ current, pageSize: size, total, showSizeChanger: true, pageSizeOptions: [20, 50, 100],
            showTotal: count => `共 ${count} 位用户`, onChange: (page, pageSize) => { setCurrent(pageSize === size ? page : 1); setSize(pageSize); } }} />}
      </div>
      <div className="transfer-footer">
        <Text type="secondary">筛选和翻页会保留勾选，切换源应用会清空。实际处理范围以已选用户为准。</Text>
        <Button type="primary" icon={mode === 'MOVE' ? <SwapOutlined /> : <CopyOutlined />} loading={busy}
          disabled={!sourceId || !targetId || sourceId === targetId || !selected.length || listError}
          onClick={() => void check()}>预检查{mode === 'MOVE' ? '迁移' : '复制'}</Button>
      </div>
    </div>
    <Modal open={modalOpen} title={preview?.executed ? '处理结果' : '确认预检查结果'} width={isMobile ? 'calc(100vw - 24px)' : 960}
      closable={!busy} maskClosable={!busy} keyboard={!busy} onCancel={() => { if (!busy) setModalOpen(false); }}
      footer={preview?.executed ? <Button type="primary" onClick={() => setModalOpen(false)}>完成</Button> : <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, flexWrap: 'wrap' }}>
        <Button disabled={busy} onClick={() => setModalOpen(false)}>返回调整</Button>
        <Button type="primary" danger={previewRequest?.mode === 'MOVE' || previewRequest?.forceOverwrite || Boolean(preview?.restoreCount)} loading={busy}
          disabled={!confirmed || !preview || preview.blockCount > 0 || preview.createCount + preview.overwriteCount + (preview.restoreCount ?? 0) === 0}
          onClick={() => void execute()}>确认{previewRequest?.mode === 'MOVE' ? '迁移' : '复制'}</Button>
      </div>}>
      {preview && <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Paragraph style={{ margin: 0 }}>{preview.sourceAppName} → {preview.targetAppName} · {previewRequest?.mode === 'MOVE' ? '迁移' : '复制'}{previewRequest?.forceOverwrite ? ' · 强制覆盖' : ''}</Paragraph>
        {summary}
        {preview.restoreCount > 0 && <Alert type="warning" showIcon message="将恢复已删除的目标账号，并按源账号替换密码、资料和会员到期时间" description="保留目标原 ID、历史记录、设备封禁和已领取试用限制，不累加会员时长。" />}
        {preview.blockCount > 0 && <Alert type="error" showIcon message="有不可处理用户，本批次不会执行" description="返回取消勾选阻止项或先修复冲突，再重新预检查。" />}
        {isMobile ? <div style={{ maxHeight: '45vh', overflowY: 'auto' }}>{preview.items.map(item => <div className="mgmt-mobile-card" key={item.sourceUserId}>
          <Space wrap><Text strong>{item.username || '不存在'} #{item.sourceUserId}</Text><Tag color={actionColors[item.action]}>{actionLabels[item.action]}</Tag></Space>
          {item.targetUserId && <div>目标：{item.targetUsername} #{item.targetUserId}</div>}
          <Paragraph style={{ margin: '8px 0', overflowWrap: 'anywhere' }}>{item.reason}</Paragraph>
          <Text type="secondary">设备 {item.deviceCount} · 源封禁 {item.bannedDeviceCount}{item.trialRecorded ? ' · 已领取试用' : ''}</Text>
        </div>)}</div> : <Table<TransferItem> rowKey="sourceUserId" size="small" dataSource={preview.items} columns={previewColumns} pagination={false} scroll={{ y: 360 }} />}
        {!preview.executed && <Checkbox checked={confirmed} disabled={busy || preview.blockCount > 0} onChange={e => setConfirmed(e.target.checked)}>
          我已确认上述范围{preview.restoreCount > 0 ? '，允许恢复已删除目标账号并替换其密码及会员配置' : ''}{previewRequest?.mode === 'MOVE' ? '，迁移成功的源账号将被停用并断开 WS' : ''}{previewRequest?.forceOverwrite ? '，允许替换冲突目标账号的密码及会员配置并断开其 WS' : ''}。
        </Checkbox>}
        {preview.executed && <Alert type="success" showIcon message="已完成，账号使用目标应用的登录配置；请重新登录验证" />}
      </Space>}
    </Modal>
  </Card>;
}
