import React, { useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Form, Input, Popover, Row, Select, Space, Table, Tag, Typography, Grid, Pagination, message } from 'antd';
import { FilterOutlined, ReloadOutlined } from '@ant-design/icons';
import { getThirdPartyRechargeLogList, type ThirdPartyRechargeLog } from '../services/callLogService';
import { getApplicationList, type Application } from '../services/applicationService';
import M5BottomSheet from './M5BottomSheet';

const { Text, Title } = Typography;

const CallLogManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<ThirdPartyRechargeLog[]>([]);
  const [apps, setApps] = useState<Application[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState<{ appId?: number; userEmail?: string; status?: number; outTradeNo?: string }>({});
  const [emailInput, setEmailInput] = useState('');
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
      const res: any = await getThirdPartyRechargeLogList({
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
    setEmailInput(filters.userEmail ?? '');
  }, [filters.userEmail]);

  const activeAdvancedFilterCount = [
    filters.appId,
    filters.status,
    filters.outTradeNo,
  ].filter((v) => v !== undefined && v !== null && v !== '').length;

  const syncListFilterFormFromFilters = () => {
    listFilterForm.setFieldsValue({
      appId: filters.appId,
      status: filters.status,
      outTradeNo: filters.outTradeNo,
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
    if (v.status != null && v.status !== '') {
      next.status = v.status;
    } else {
      delete next.status;
    }
    if (v.outTradeNo && v.outTradeNo.trim()) {
      next.outTradeNo = v.outTradeNo.trim();
    } else {
      delete next.outTradeNo;
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
    delete next.outTradeNo;
    setFilters(next);
    fetchData(1, pagination.pageSize, next);
  };

  const applyEmailSearch = (raw?: string) => {
    const trimmed = (raw ?? emailInput).trim();
    const next = { ...filters };
    if (trimmed) {
      next.userEmail = trimmed;
    } else {
      delete next.userEmail;
    }
    setFilters(next);
    fetchData(1, pagination.pageSize, next);
  };

  const formatDateTime = (value?: string) => {
    if (!value) return '-';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value;
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    const hh = `${d.getHours()}`.padStart(2, '0');
    const mm = `${d.getMinutes()}`.padStart(2, '0');
    return `${y}/${m}/${day} ${hh}:${mm}`;
  };

  return (
    <Card styles={{ body: { padding: isMobile ? 12 : 24 } }}>
      {/* 标题栏 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: isMobile ? 12 : 16,
      }}>
        <Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>
          三方调用日志
        </Title>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => fetchData()}
          size={isMobile ? 'small' : 'middle'}
        >
          刷新
        </Button>
      </div>

      {/* 搜索和筛选 */}
      <div style={isMobile ? { display: 'flex', gap: 8, marginBottom: 12, alignItems: 'center' } : { marginBottom: 16 }}>
        {isMobile ? (
          <>
            <Input
              size="small"
              placeholder="搜索用户邮箱"
              allowClear
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              onPressEnter={() => applyEmailSearch()}
              style={{ flex: 1, minWidth: 0 }}
            />
            <Button type="primary" onClick={() => applyEmailSearch()} style={{ flexShrink: 0 }}>
              搜索
            </Button>
            <Badge count={activeAdvancedFilterCount} size="small" offset={[-2, 2]}>
              <Button icon={<FilterOutlined />} style={{ flexShrink: 0 }} onClick={() => { setFilterPopoverOpen(true); syncListFilterFormFromFilters(); }}>筛选</Button>
            </Badge>
            <M5BottomSheet open={filterPopoverOpen} onClose={() => setFilterPopoverOpen(false)} title="筛选条件" footer={<><Button onClick={handleAdvancedFilterReset} style={{ flex: 1, height: 44, borderRadius: 10 }}>重置</Button><Button type="primary" onClick={() => { void handleAdvancedFilterQuery(); setFilterPopoverOpen(false); }} style={{ flex: 2, height: 44, borderRadius: 10 }}>查询</Button></>}>
              <Form form={listFilterForm} layout="vertical" style={{ marginBottom: 0 }}>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item label="应用" name="appId">
                      <Select
                        allowClear
                        placeholder="选择应用"
                        options={apps.map((app) => ({
                          label: app.appName,
                          value: app.id,
                        }))}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item label="状态" name="status">
                      <Select
                        allowClear
                        placeholder="状态"
                        options={[
                          { label: '成功', value: 1 },
                          { label: '失败', value: 2 },
                        ]}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Form.Item label="订单号" name="outTradeNo">
                      <Input allowClear placeholder="输入订单号" />
                    </Form.Item>
                  </Col>
                </Row>
              </Form>
            </M5BottomSheet>
          </>
        ) : (
          <Space size={12}>
            <Space.Compact style={{ width: 360, maxWidth: 'calc(100vw - 120px)' }}>
              <Input
                placeholder="搜索用户邮箱"
                allowClear
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                onPressEnter={() => applyEmailSearch()}
                style={{ minWidth: 0 }}
              />
              <Button type="primary" onClick={() => applyEmailSearch()}>
                搜索
              </Button>
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
                            options={apps.map((app) => ({
                              label: app.appName,
                              value: app.id,
                            }))}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item label="状态" name="status">
                          <Select
                            allowClear
                            placeholder="状态"
                            options={[
                              { label: '成功', value: 1 },
                              { label: '失败', value: 2 },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={24}>
                        <Form.Item label="订单号" name="outTradeNo">
                          <Input allowClear placeholder="输入订单号" />
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

      {/* 用户列表 */}
      {isMobile ? (
        <div className="mgmt-mobile-list">
          {loading && (
            <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>加载中...</div>
          )}
          {!loading && list.length === 0 && (
            <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无数据</div>
          )}
          {list.map((item) => (
            <div
              key={item.id}
              className="mgmt-mobile-card"
              style={{ borderLeft: `3px solid ${item.status === 1 ? '#52c41a' : '#ff4d4f'}` }}
            >
              <div className="mgmt-mobile-card-header">
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Tag color={item.status === 1 ? 'success' : 'error'} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>
                      {item.status === 1 ? '成功' : '失败'}
                    </Tag>
                    <Tag color={item.signValid === 1 ? 'blue' : 'default'} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>
                      签名{item.signValid === 1 ? '通过' : '未通过'}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {formatDateTime(item.createdAt)}
                    </Text>
                  </div>
                  <Text
                    style={{ fontSize: 13, display: 'block', marginTop: 4 }}
                    ellipsis={{ tooltip: item.userEmail }}
                  >
                    {item.userEmail || '-'}
                  </Text>
                </div>
                <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap', marginLeft: 8 }}>
                  +{item.days}天
                </Text>
              </div>
              <div className="mgmt-mobile-card-meta">
                <Text type="secondary">ID: {item.id}</Text>
                <Text type="secondary">|</Text>
                <Text type="secondary">IP: {item.requestIp || '-'}</Text>
              </div>
              {item.outTradeNo && (
                <div style={{ marginTop: 4 }}>
                  <Text
                    type="secondary"
                    style={{ fontSize: 11, fontFamily: 'monospace' }}
                    ellipsis={{ tooltip: item.outTradeNo }}
                  >
                    订单: {item.outTradeNo}
                  </Text>
                </div>
              )}
              {item.errorMessage && (
                <div style={{ marginTop: 4 }}>
                  <Text type="danger" style={{ fontSize: 11 }} ellipsis={{ tooltip: item.errorMessage }}>
                    {item.errorMessage}
                  </Text>
                </div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <Table<ThirdPartyRechargeLog>
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
            { title: '凭证ID', dataIndex: 'credentialId', width: 100 },
            { title: '用户邮箱', dataIndex: 'userEmail', width: 220 },
            { title: '加时天数', dataIndex: 'days', width: 90 },
            { title: '订单号', dataIndex: 'outTradeNo', width: 180, ellipsis: true },
            { title: 'IP', dataIndex: 'requestIp', width: 140 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v?: number) => <Tag color={v === 1 ? 'success' : 'error'}>{v === 1 ? '成功' : '失败'}</Tag>,
            },
            {
              title: '签名',
              dataIndex: 'signValid',
              width: 90,
              render: (v?: number) => <Tag color={v === 1 ? 'blue' : 'default'}>{v === 1 ? '通过' : '未通过'}</Tag>,
            },
            {
              title: '前后到期',
              width: 280,
              render: (_, r) => (
                <Space direction="vertical" size={0}>
                  <Text type="secondary">前: {formatDateTime(r.beforeExpiresAt)}</Text>
                  <Text>后: {formatDateTime(r.afterExpiresAt)}</Text>
                </Space>
              ),
            },
            { title: '错误信息', dataIndex: 'errorMessage', width: 260, ellipsis: true },
            {
              title: '时间',
              dataIndex: 'createdAt',
              width: 170,
              render: (v?: string) => <Text style={{ fontSize: 12 }}>{formatDateTime(v)}</Text>,
            },
          ]}
          scroll={{ x: 1800 }}
        />
      )}

      {/* 移动端分页 */}
      {isMobile && pagination.total > 0 && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
          <Pagination
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            onChange={(page, size) => fetchData(page, size, filters)}
            size="small"
            simple
          />
        </div>
      )}
    </Card>
  );
};

export default CallLogManagementContent;
