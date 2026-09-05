import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  Popconfirm,
  Dropdown,
  message,
  Grid,
  Tooltip,
  Divider,
  Spin,
} from 'antd';
import {
  ReloadOutlined,
  UploadOutlined,
  MoreOutlined,
  CodeOutlined,
  PlayCircleOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import M5BottomSheet from '../../components/M5BottomSheet';
import type { ColumnsType } from 'antd/es/table';
import {
  disableFunctionPlugin,
  deleteFunctionPlugin,
  enableFunctionPlugin,
  listFunctionPlugins,
  getFunctionPluginFunctions,
  testFunction,
  type FunctionPluginModule,
  type FunctionInfo,
  type TestFunctionResponse,
  uploadFunctionPlugin,
} from '../../services/functionPluginService';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '已上传', color: 'default' },
  1: { text: '已启用', color: 'green' },
  2: { text: '已停用', color: 'orange' },
  3: { text: '加载失败', color: 'red' },
};

const FunctionPluginManagementPage = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [plugins, setPlugins] = useState<FunctionPluginModule[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [form] = Form.useForm();

  // 详情/测试相关状态
  const [detailOpen, setDetailOpen] = useState(false);
  const [activePlugin, setActivePlugin] = useState<FunctionPluginModule | null>(null);
  const [functions, setFunctions] = useState<FunctionInfo[]>([]);
  const [loadingFunctions, setLoadingFunctions] = useState(false);

  // 测试控制台状态
  const [selectedFunc, setSelectedFunc] = useState<string>('');
  const [testInput, setTestInput] = useState<string>('{}');
  const [testOutput, setTestOutput] = useState<string>('');
  const [testing, setTesting] = useState(false);

  const loadPlugins = async () => {
    setLoading(true);
    try {
      const res: any = await listFunctionPlugins();
      setPlugins(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPlugins();
  }, []);

  const openDetail = async (plugin: FunctionPluginModule) => {
    setActivePlugin(plugin);
    setDetailOpen(true);
    setSelectedFunc('');
    setTestInput('{}');
    setTestOutput('');

    // 加载函数列表
    setLoadingFunctions(true);
    try {
      const res: any = await getFunctionPluginFunctions(plugin.id);
      setFunctions(res.data || []);
    } catch (e) {
      message.error('加载函数列表失败');
    } finally {
      setLoadingFunctions(false);
    }
  };

  const handleTestFunction = async () => {
    if (!activePlugin || !selectedFunc) {
      message.warning('请选择要测试的函数');
      return;
    }

    let params: Record<string, any>;
    try {
      params = JSON.parse(testInput || '{}');
    } catch (e) {
      message.error('输入参数不是合法的 JSON');
      return;
    }

    setTesting(true);
    setTestOutput('');
    try {
      const res: any = await testFunction({
        pluginId: activePlugin.pluginId,
        func: selectedFunc,
        params,
      });
      const result: TestFunctionResponse = res.data;
      setTestOutput(JSON.stringify(result, null, 2));
      if (result.success) {
        message.success('函数执行成功');
      } else {
        message.error(`函数执行失败: ${result.message}`);
      }
    } catch (e: any) {
      setTestOutput(JSON.stringify({ error: e.message }, null, 2));
      message.error('测试请求失败');
    } finally {
      setTesting(false);
    }
  };

  const loadExampleInput = (funcName: string) => {
    const func = functions.find(f => f.name === funcName);
    if (func) {
      setTestInput(JSON.stringify(func.exampleInput || func.inputExample, null, 2));
    }
  };

  const parseFunctions = (functionsStr?: string): string[] => {
    if (!functionsStr) return [];
    try {
      const parsed = JSON.parse(functionsStr);
      // 兼容两种格式：
      // 1. 简单数组: ["echo", "add"]
      // 2. 对象格式: {"functions": [{"name": "echo"}, ...]}
      if (Array.isArray(parsed)) {
        if (parsed.length > 0 && typeof parsed[0] === 'object' && parsed[0].name) {
          return parsed.map((f: any) => f.name);
        }
        return parsed;
      }
      if (parsed.functions && Array.isArray(parsed.functions)) {
        return parsed.functions.map((f: any) => f.name);
      }
      return [];
    } catch {
      return [];
    }
  };

  const allColumns: ColumnsType<FunctionPluginModule> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 80 },
      { title: '插件ID', dataIndex: 'pluginId' },
      { title: '名称', dataIndex: 'pluginName', render: (v) => v || '-' },
      { title: '版本', dataIndex: 'pluginVersion', width: 120 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        render: (status: number) => {
          const item = statusMap[status] || { text: `未知(${status})`, color: 'default' };
          return <Tag color={item.color}>{item.text}</Tag>;
        },
      },
      {
        title: '提供的函数',
        dataIndex: 'functions',
        render: (v: string) => {
          const funcs = parseFunctions(v);
          if (funcs.length === 0) return '-';
          return (
            <Space size={[4, 4]} wrap>
              {funcs.map((f) => (
                <Tag key={f} icon={<CodeOutlined />} color="blue">
                  {f}
                </Tag>
              ))}
            </Space>
          );
        },
      },
      {
        title: '操作',
        key: 'actions',
        width: isMobile ? 80 : 280,
        render: (_, record) => {
          if (isMobile) {
            return (
              <Dropdown
                menu={{
                  items: [
                    { key: 'detail', label: '详情/测试', onClick: () => openDetail(record) },
                    { key: 'enable', label: '启用', disabled: record.status === 1, onClick: async () => { await enableFunctionPlugin(record.id); message.success('插件已启用'); await loadPlugins(); } },
                    { key: 'disable', label: '停用', disabled: record.status !== 1, onClick: async () => { await disableFunctionPlugin(record.id); message.success('插件已停用'); await loadPlugins(); } },
                    { type: 'divider' },
                    { key: 'delete', label: '删除', danger: true, onClick: async () => { await deleteFunctionPlugin(record.id); message.success('插件已删除'); await loadPlugins(); } },
                  ],
                }}
                trigger={['click']}
              >
                <Button type="text" size="small" icon={<MoreOutlined />} />
              </Dropdown>
            );
          }
          return (
            <Space>
              <Button size="small" icon={<CodeOutlined />} onClick={() => openDetail(record)}>详情/测试</Button>
              <Tooltip title={record.status === 1 ? '已启用' : ''}>
                <Button type="primary" size="small" disabled={record.status === 1} onClick={async () => { await enableFunctionPlugin(record.id); message.success('插件已启用'); await loadPlugins(); }}>启用</Button>
              </Tooltip>
              <Tooltip title={record.status !== 1 ? '未启用' : ''}>
                <Button danger size="small" disabled={record.status !== 1} onClick={async () => { await disableFunctionPlugin(record.id); message.success('插件已停用'); await loadPlugins(); }}>停用</Button>
              </Tooltip>
              <Popconfirm title="确认删除该插件？" description="删除后不可恢复，如插件已加载会先卸载。" okText="删除" cancelText="取消" onConfirm={async () => { await deleteFunctionPlugin(record.id); message.success('插件已删除'); await loadPlugins(); }}>
                <Button danger size="small">删除</Button>
              </Popconfirm>
            </Space>
          );
        },
      },
    ],
    [isMobile]
  );

  const MOBILE_VISIBLE_KEYS = ['pluginName', 'status', 'functions', 'actions'];
  const displayColumns = isMobile
    ? allColumns.filter((c) => MOBILE_VISIBLE_KEYS.includes(c.key as string) || MOBILE_VISIBLE_KEYS.includes((c as any).dataIndex as string))
    : allColumns;

  const handleSubmitUpload = async () => {
    const values = await form.validateFields();
    if (!selectedFile) {
      message.error('请先选择 Jar 文件');
      return;
    }
    const formData = new FormData();
    formData.append('file', selectedFile);
    if (values.pluginName) {
      formData.append('pluginName', values.pluginName);
    }
    if (values.remark) {
      formData.append('remark', values.remark);
    }

    setUploading(true);
    try {
      await uploadFunctionPlugin(formData);
      message.success('函数插件上传成功');
      setUploadOpen(false);
      setSelectedFile(null);
      form.resetFields();
      await loadPlugins();
    } finally {
      setUploading(false);
    }
  };

  // 渲染详情/测试弹窗内容
  const renderDetailContent = () => (
    <Tabs
      defaultActiveKey="functions"
      items={[
        {
          key: 'functions',
          label: '函数列表',
          children: (
            <Spin spinning={loadingFunctions}>
              {functions.length === 0 ? (
                <Alert message="该插件暂无已注册的函数" type="info" showIcon />
              ) : (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {functions.map((func) => (
                    <Card
                      key={func.name}
                      size="small"
                      title={
                        <Space>
                          <Tag icon={<CodeOutlined />} color="blue" style={{ fontSize: 14 }}>{func.name}</Tag>
                          <Typography.Text type="secondary">插件: {func.pluginId}</Typography.Text>
                        </Space>
                      }
                    >
                      <Row gutter={16}>
                        <Col span={12}>
                          <Typography.Text strong>入参示例:</Typography.Text>
                          <Input.TextArea
                            value={JSON.stringify(func.exampleInput || func.inputExample, null, 2)}
                            readOnly
                            autoSize={{ minRows: 3, maxRows: 6 }}
                            style={{ fontFamily: 'monospace', marginTop: 4 }}
                          />
                        </Col>
                        <Col span={12}>
                          <Typography.Text strong>出参示例:</Typography.Text>
                          <Input.TextArea
                            value={JSON.stringify(func.exampleOutput || func.outputExample, null, 2)}
                            readOnly
                            autoSize={{ minRows: 3, maxRows: 6 }}
                            style={{ fontFamily: 'monospace', marginTop: 4 }}
                          />
                        </Col>
                      </Row>
                    </Card>
                  ))}
                </Space>
              )}
            </Spin>
          ),
        },
        {
          key: 'test',
          label: '在线测试',
          children: (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Alert
                message="函数测试控制台"
                description="选择函数，输入参数，点击执行按钮测试函数。"
                type="info"
                showIcon
              />

              <div>
                <Typography.Text strong>选择函数:</Typography.Text>
                <Space wrap style={{ marginTop: 8 }}>
                  {functions.map((func) => (
                    <Button
                      key={func.name}
                      type={selectedFunc === func.name ? 'primary' : 'default'}
                      icon={<CodeOutlined />}
                      onClick={() => {
                        setSelectedFunc(func.name);
                        loadExampleInput(func.name);
                      }}
                    >
                      {func.name}
                    </Button>
                  ))}
                </Space>
              </div>

              {selectedFunc && (
                <>
                  <Divider style={{ margin: '12px 0' }} />

                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <Typography.Text strong>输入参数 (JSON):</Typography.Text>
                      <Button
                        size="small"
                        onClick={() => loadExampleInput(selectedFunc)}
                      >
                        加载示例
                      </Button>
                    </div>
                    <Input.TextArea
                      value={testInput}
                      onChange={(e) => setTestInput(e.target.value)}
                      autoSize={{ minRows: 6, maxRows: 12 }}
                      style={{ fontFamily: 'monospace' }}
                      placeholder='{"key": "value"}'
                    />
                  </div>

                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    loading={testing}
                    onClick={handleTestFunction}
                    block
                  >
                    执行测试
                  </Button>

                  {testOutput && (
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                        <Typography.Text strong>执行结果:</Typography.Text>
                        <Button
                          size="small"
                          icon={<CopyOutlined />}
                          onClick={() => {
                            navigator.clipboard.writeText(testOutput);
                            message.success('已复制到剪贴板');
                          }}
                        >
                          复制
                        </Button>
                      </div>
                      <Input.TextArea
                        value={testOutput}
                        readOnly
                        autoSize={{ minRows: 8, maxRows: 16 }}
                        style={{
                          fontFamily: 'monospace',
                          backgroundColor: testOutput.includes('"success":true') ? '#f6ffed' : '#fff2f0',
                        }}
                      />
                    </div>
                  )}
                </>
              )}
            </Space>
          ),
        },
      ]}
    />
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="函数插件管理"
        description="管理 WebSocket 函数执行插件。上传 JAR 包后启用，客户端可通过 FUNC_CALL 消息调用插件中的函数。支持在线测试函数执行。"
      />
      <Card
        title={
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <Typography.Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>函数插件列表</Typography.Title>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
              <Button
                size={isMobile ? 'small' : 'middle'}
                icon={<ReloadOutlined />}
                onClick={loadPlugins}
              >
                {!isMobile && '刷新'}
              </Button>
              <Button
                type="primary"
                size={isMobile ? 'small' : 'middle'}
                icon={<UploadOutlined />}
                onClick={() => setUploadOpen(true)}
              >
                上传插件
              </Button>
            </div>
          </div>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={displayColumns}
          dataSource={plugins}
          pagination={{ pageSize: 10, simple: isMobile, showTotal: isMobile ? undefined : (total) => `共 ${total} 条` }}
          scroll={{ x: isMobile ? 300 : undefined }}
          size={isMobile ? 'small' : 'middle'}
        />
      </Card>

      {/* 上传弹窗 */}
      {isMobile ? (
        <M5BottomSheet
          open={uploadOpen}
          onClose={() => setUploadOpen(false)}
          title="上传函数插件"
          footer={<><Button onClick={() => setUploadOpen(false)} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button><Button type="primary" onClick={handleSubmitUpload} loading={uploading} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button></>}
        >
          <Form form={form} layout="vertical">
            <Form.Item label="插件名称" name="pluginName">
              <Input placeholder="可选，不填则使用 plugin.id" />
            </Form.Item>
            <Form.Item label="备注" name="remark">
              <Input.TextArea rows={3} placeholder="可选" />
            </Form.Item>
            <Form.Item label="Jar 文件" required>
              <Upload
                beforeUpload={(file) => {
                  const isJar = file.name.toLowerCase().endsWith('.jar');
                  if (!isJar) {
                    message.error('只支持 .jar 文件');
                    return Upload.LIST_IGNORE;
                  }
                  setSelectedFile(file as unknown as File);
                  return false;
                }}
                maxCount={1}
                onRemove={() => setSelectedFile(null)}
              >
                <Button icon={<UploadOutlined />}>选择 Jar 文件</Button>
              </Upload>
            </Form.Item>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title="上传函数插件"
          open={uploadOpen}
          onCancel={() => setUploadOpen(false)}
          onOk={handleSubmitUpload}
          confirmLoading={uploading}
          width={520}
        >
          <Form form={form} layout="vertical">
            <Form.Item label="插件名称" name="pluginName">
              <Input placeholder="可选，不填则使用 plugin.id" />
            </Form.Item>
            <Form.Item label="备注" name="remark">
              <Input.TextArea rows={3} placeholder="可选" />
            </Form.Item>
            <Form.Item label="Jar 文件" required>
              <Upload
                beforeUpload={(file) => {
                  const isJar = file.name.toLowerCase().endsWith('.jar');
                  if (!isJar) {
                    message.error('只支持 .jar 文件');
                    return Upload.LIST_IGNORE;
                  }
                  setSelectedFile(file as unknown as File);
                  return false;
                }}
                maxCount={1}
                onRemove={() => setSelectedFile(null)}
              >
                <Button icon={<UploadOutlined />}>选择 Jar 文件</Button>
              </Upload>
            </Form.Item>
          </Form>
        </Modal>
      )}

      {/* 详情/测试弹窗 */}
      {isMobile ? (
        <M5BottomSheet
          open={detailOpen}
          onClose={() => { setDetailOpen(false); setActivePlugin(null); setFunctions([]); }}
          title={`函数插件详情${activePlugin ? ` - ${activePlugin.pluginId}` : ''}`}
          maxHeight="90vh"
          footer={<Button onClick={() => { setDetailOpen(false); setActivePlugin(null); setFunctions([]); }} style={{ width: '100%', height: 44, borderRadius: 10 }}>关闭</Button>}
        >
          {renderDetailContent()}
        </M5BottomSheet>
      ) : (
        <Modal
          title={`函数插件详情${activePlugin ? ` - ${activePlugin.pluginId}@${activePlugin.pluginVersion}` : ''}`}
          open={detailOpen}
          onCancel={() => { setDetailOpen(false); setActivePlugin(null); setFunctions([]); }}
          footer={null}
          width={900}
        >
          {renderDetailContent()}
        </Modal>
      )}
    </Space>
  );
};

export default FunctionPluginManagementPage;
