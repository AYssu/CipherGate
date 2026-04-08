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
  message,
} from 'antd';
import { ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  disablePlugin,
  deletePlugin,
  enablePlugin,
  listPlugins,
  type PluginModule,
  uploadPlugin,
} from '../../services/pluginService';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '已上传', color: 'default' },
  1: { text: '已启用', color: 'green' },
  2: { text: '已停用', color: 'orange' },
  3: { text: '加载失败', color: 'red' },
};

const PluginManagementPage = () => {
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [plugins, setPlugins] = useState<PluginModule[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [form] = Form.useForm();

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

  const columns: ColumnsType<PluginModule> = useMemo(
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
        width: 220,
        render: (_, record) => (
          <Space>
            <Button
              type="primary"
              size="small"
              disabled={record.status === 1}
              onClick={async () => {
                await enablePlugin(record.id);
                message.success('插件已启用');
                await loadPlugins();
              }}
            >
              启用
            </Button>
            <Button
              danger
              size="small"
              disabled={record.status !== 1}
              onClick={async () => {
                await disablePlugin(record.id);
                message.success('插件已停用');
                await loadPlugins();
              }}
            >
              停用
            </Button>
            <Popconfirm
              title="确认删除该插件？"
              description="删除后不可恢复，如插件已加载会先卸载。"
              okText="删除"
              cancelText="取消"
              onConfirm={async () => {
                await deletePlugin(record.id);
                message.success('插件已删除');
                await loadPlugins();
              }}
            >
              <Button danger size="small">
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    []
  );

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
        title="插件列表"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadPlugins}>
              刷新
            </Button>
            <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>
              上传插件
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={plugins}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title="上传插件"
        open={uploadOpen}
        onCancel={() => setUploadOpen(false)}
        onOk={handleSubmitUpload}
        confirmLoading={uploading}
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
    </Space>
  );
};

export default PluginManagementPage;
