import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  Popconfirm,
  Dropdown,
  message,
  Grid,
} from 'antd';
import { ReloadOutlined, UploadOutlined, MoreOutlined } from '@ant-design/icons';
import M5BottomSheet from '../../components/M5BottomSheet';
import type { ColumnsType } from 'antd/es/table';
import {
  disablePlugin,
  deletePlugin,
  enablePlugin,
  getPluginConfig,
  getPluginConfigSchema,
  listPlugins,
  type PluginModule,
  updatePluginConfig,
  uploadPlugin,
} from '../../services/pluginService';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '已上传', color: 'default' },
  1: { text: '已启用', color: 'green' },
  2: { text: '已停用', color: 'orange' },
  3: { text: '加载失败', color: 'red' },
};

const PluginManagementPage = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [plugins, setPlugins] = useState<PluginModule[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [form] = Form.useForm();

  const [configOpen, setConfigOpen] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [activePlugin, setActivePlugin] = useState<PluginModule | null>(null);
  const [pluginSchema, setPluginSchema] = useState<string>('');
  const [pluginDefaults, setPluginDefaults] = useState<string>('');
  const [configJson, setConfigJson] = useState<string>('{}');

  const loadPlugins = async () => {
    setLoading(true);
    try {
      const res: any = await listPlugins();
      setPlugins(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPlugins();
  }, []);

  const openConfig = async (plugin: PluginModule) => {
    setActivePlugin(plugin);
    setConfigOpen(true);
    try {
      const [schemaRes, cfgRes]: any = await Promise.all([
        getPluginConfigSchema(plugin.id),
        getPluginConfig(plugin.id),
      ]);
      const schemaPayload = schemaRes.data || {};
      const cfgPayload = cfgRes.data || {};
      setPluginSchema(schemaPayload.configSchema || '');
      setPluginDefaults(schemaPayload.configDefaults || '');
      if (cfgPayload.configValues) {
        setConfigJson(JSON.stringify(JSON.parse(cfgPayload.configValues), null, 2));
      } else if (schemaPayload.configDefaults) {
        setConfigJson(schemaPayload.configDefaults);
      } else {
        setConfigJson('{}');
      }
    } finally {
    }
  };

  const savePluginConfig = async () => {
    if (!activePlugin) {
      message.warning('未选择插件');
      return;
    }
    let obj: any;
    try {
      obj = JSON.parse(configJson || '{}');
    } catch (e) {
      message.error('配置不是合法JSON');
      return;
    }
    setConfigSaving(true);
    try {
      await updatePluginConfig(activePlugin.id, obj);
      message.success('已保存插件配置');
    } finally {
      setConfigSaving(false);
    }
  };

  const allColumns: ColumnsType<PluginModule> = useMemo(
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
        title: '对象路径',
        dataIndex: 'objectKey',
        render: (v: string) => (
          <Typography.Text copyable style={{ maxWidth: 380 }} ellipsis>
            {v}
          </Typography.Text>
        ),
      },
      {
        title: '操作',
        key: 'actions',
        width: isMobile ? 80 : 300,
        render: (_, record) => {
          if (isMobile) {
            return (
              <Dropdown
                menu={{
                  items: [
                    { key: 'config', label: '配置', onClick: () => openConfig(record) },
                    { key: 'enable', label: '启用', disabled: record.status === 1, onClick: async () => { await enablePlugin(record.id); message.success('插件已启用'); await loadPlugins(); } },
                    { key: 'disable', label: '停用', disabled: record.status !== 1, onClick: async () => { await disablePlugin(record.id); message.success('插件已停用'); await loadPlugins(); } },
                    { type: 'divider' },
                    { key: 'delete', label: '删除', danger: true, onClick: async () => { await deletePlugin(record.id); message.success('插件已删除'); await loadPlugins(); } },
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
              <Button size="small" onClick={() => openConfig(record)}>配置</Button>
              <Button type="primary" size="small" disabled={record.status === 1} onClick={async () => { await enablePlugin(record.id); message.success('插件已启用'); await loadPlugins(); }}>启用</Button>
              <Button danger size="small" disabled={record.status !== 1} onClick={async () => { await disablePlugin(record.id); message.success('插件已停用'); await loadPlugins(); }}>停用</Button>
              <Popconfirm title="确认删除该插件？" description="删除后不可恢复，如插件已加载会先卸载。" okText="删除" cancelText="取消" onConfirm={async () => { await deletePlugin(record.id); message.success('插件已删除'); await loadPlugins(); }}>
                <Button danger size="small">删除</Button>
              </Popconfirm>
            </Space>
          );
        },
      },
    ],
    [isMobile]
  );

  const MOBILE_VISIBLE_KEYS = ['pluginName', 'status', 'actions'];
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
      await uploadPlugin(formData);
      message.success('插件上传成功');
      setUploadOpen(false);
      setSelectedFile(null);
      form.resetFields();
      await loadPlugins();
    } finally {
      setUploading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="插件管理"
        description="插件 Jar 会上传到 MinIO，启用时下载到后端临时目录并加载。"
      />
      <Card
        title={
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <Typography.Title level={isMobile ? 5 : 4} style={{ margin: 0, whiteSpace: 'nowrap' }}>插件列表</Typography.Title>
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

      {isMobile ? (
        <M5BottomSheet
          open={uploadOpen}
          onClose={() => setUploadOpen(false)}
          title="上传插件"
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
                onRemove={() => {
                  setSelectedFile(null);
                }}
              >
                <Button icon={<UploadOutlined />}>选择 Jar 文件</Button>
              </Upload>
            </Form.Item>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title="上传插件"
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
                onRemove={() => {
                  setSelectedFile(null);
                }}
              >
                <Button icon={<UploadOutlined />}>选择 Jar 文件</Button>
              </Upload>
            </Form.Item>
          </Form>
        </Modal>
      )}

      {isMobile ? (
        <M5BottomSheet
          open={configOpen}
          onClose={() => { setConfigOpen(false); setActivePlugin(null); }}
          title={`插件配置${activePlugin ? ` - ${activePlugin.pluginId}@${activePlugin.pluginVersion}` : ''}`}
          maxHeight="90vh"
          footer={<>
            <Button onClick={() => { setConfigOpen(false); setActivePlugin(null); }} style={{ flex: 1, height: 44, borderRadius: 10 }}>关闭</Button>
            <Button type="primary" loading={configSaving} onClick={savePluginConfig} style={{ flex: 1, height: 44, borderRadius: 10 }}>保存配置</Button>
          </>}
        >
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert type="info" showIcon message="说明" description="这里展示插件的配置Schema/默认值，并保存插件配置值(JSON)。" />
            <Card size="small" title="插件配置 Schema (只读)">
              <Input.TextArea value={pluginSchema} readOnly autoSize={{ minRows: 6, maxRows: 12 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} />
            </Card>
            <Card size="small" title="插件默认配置 (只读)">
              <Input.TextArea value={pluginDefaults} readOnly autoSize={{ minRows: 4, maxRows: 8 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} />
            </Card>
            <Card size="small" title="插件 configValues (可编辑JSON)">
              <Input.TextArea value={configJson} onChange={(e) => setConfigJson(e.target.value)} autoSize={{ minRows: 8, maxRows: 16 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} placeholder='例如：{"aesKey":"cg_demo_key_1234"}' />
            </Card>
          </Space>
        </M5BottomSheet>
      ) : (
        <Modal
          title={`插件配置${activePlugin ? ` - ${activePlugin.pluginId}@${activePlugin.pluginVersion}` : ''}`}
          open={configOpen}
          onCancel={() => { setConfigOpen(false); setActivePlugin(null); }}
          footer={null}
          width={900}
        >
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert type="info" showIcon message="说明" description="这里展示插件的配置Schema/默认值，并保存插件配置值(JSON)。" />
            <Card size="small" title="保存插件配置">
              <Button type="primary" loading={configSaving} onClick={savePluginConfig}>保存配置</Button>
            </Card>
            <Card size="small" title="插件配置 Schema (只读)">
              <Input.TextArea value={pluginSchema} readOnly autoSize={{ minRows: 10, maxRows: 18 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} />
            </Card>
            <Card size="small" title="插件默认配置 (只读)">
              <Input.TextArea value={pluginDefaults} readOnly autoSize={{ minRows: 6, maxRows: 12 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} />
            </Card>
            <Card size="small" title="插件 configValues (可编辑JSON)">
              <Input.TextArea value={configJson} onChange={(e) => setConfigJson(e.target.value)} autoSize={{ minRows: 12, maxRows: 22 }} style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} placeholder='例如：{"aesKey":"cg_demo_key_1234"}' />
            </Card>
          </Space>
        </Modal>
      )}
    </Space>
  );
};

export default PluginManagementPage;
