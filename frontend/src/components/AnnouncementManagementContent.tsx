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
  Popconfirm,
  Tooltip,
  Grid,
  Dropdown,
} from 'antd';
import {
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  PlusOutlined,
  EyeOutlined,
  MoreOutlined,
  SoundOutlined,
} from '@ant-design/icons';
import { announcementApi, type SystemAnnouncement } from '../services/announcementService';
import M5BottomSheet from './M5BottomSheet';

const { Text } = Typography;
const { TextArea } = Input;

const AnnouncementManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [announcements, setAnnouncements] = useState<SystemAnnouncement[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewAnnouncement, setPreviewAnnouncement] = useState<SystemAnnouncement | null>(null);
  const [editingAnnouncement, setEditingAnnouncement] = useState<SystemAnnouncement | null>(null);
  const [form] = Form.useForm();

  const fetchAnnouncements = async () => {
    setLoading(true);
    try {
      const result = await announcementApi.getAnnouncements();
      setAnnouncements((result as any).data || []);
    } catch (error) {
      console.error('获取公告列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnnouncements();
  }, []);

  const handleCreate = () => {
    setEditingAnnouncement(null);
    form.resetFields();
    form.setFieldsValue({ status: 1 });
    setModalVisible(true);
  };

  const handleEdit = (record: SystemAnnouncement) => {
    setEditingAnnouncement(record);
    form.setFieldsValue({
      title: record.title,
      content: record.content,
      status: record.status,
    });
    setModalVisible(true);
  };

  const handlePreview = (record: SystemAnnouncement) => {
    setPreviewAnnouncement(record);
    setPreviewVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await announcementApi.deleteAnnouncement(id);
      message.success('公告删除成功');
      fetchAnnouncements();
    } catch (error) {
      console.error('删除公告失败:', error);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (editingAnnouncement) {
        await announcementApi.updateAnnouncement(editingAnnouncement.id, values);
        message.success('公告更新成功');
      } else {
        await announcementApi.createAnnouncement(values);
        message.success('公告创建成功');
      }
      setModalVisible(false);
      setEditingAnnouncement(null);
      form.resetFields();
      fetchAnnouncements();
    } catch (error) {
      console.error('保存公告失败:', error);
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingAnnouncement(null);
    form.resetFields();
  };

  const getStatusTag = (status: number) => {
    return status === 1
      ? <Tag color="green" style={{ margin: 0, fontSize: isMobile ? 11 : 12 }}>启用</Tag>
      : <Tag color="red" style={{ margin: 0, fontSize: isMobile ? 11 : 12 }}>禁用</Tag>;
  };

  const columns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      render: (text: string) => (
        <Text strong style={{ fontSize: isMobile ? 13 : 14 }}>{text}</Text>
      ),
    },
    {
      title: '内容预览',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      render: (text: string) => (
        <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>
          {text?.substring(0, 50)}{text?.length > 50 ? '...' : ''}
        </Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => getStatusTag(status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => (
        <Text type="secondary" style={{ fontSize: isMobile ? 12 : 14 }}>
          {new Date(date).toLocaleString('zh-CN')}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: isMobile ? 60 : 150,
      render: (record: SystemAnnouncement) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: () => handlePreview(record) },
                  { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEdit(record) },
                  { type: 'divider' },
                  { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDelete(record.id) },
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
            <Tooltip title="查看">
              <Button
                type="link"
                icon={<EyeOutlined />}
                onClick={() => handlePreview(record)}
                size="small"
              />
            </Tooltip>
            <Tooltip title="编辑">
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={() => handleEdit(record)}
                size="small"
              />
            </Tooltip>
            <Popconfirm
              title="确认删除"
              description="确定要删除这条公告吗？"
              onConfirm={() => handleDelete(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
                size="small"
              />
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 0 }}>
      <Card styles={{ body: { padding: isMobile ? 12 : 24 } }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: isMobile ? 12 : 16,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <SoundOutlined style={{ color: '#1677ff', fontSize: isMobile ? 18 : 20 }} />
            <Text strong style={{ fontSize: isMobile ? 16 : 20, whiteSpace: 'nowrap' }}>
              公告管理
            </Text>
          </div>
          {isMobile ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Button size="small" icon={<ReloadOutlined />} onClick={fetchAnnouncements} loading={loading}>刷新</Button>
              <Button type="primary" size="small" icon={<PlusOutlined />} onClick={handleCreate}>新增</Button>
            </div>
          ) : (
            <Space>
              <Button icon={<ReloadOutlined />} onClick={fetchAnnouncements} loading={loading}>刷新</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新增</Button>
            </Space>
          )}
        </div>

        {isMobile ? (
          <div className="mgmt-mobile-list">
            {loading && (
              <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>加载中...</div>
            )}
            {!loading && announcements.length === 0 && (
              <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无公告</div>
            )}
            {announcements.map((record) => (
              <div
                key={record.id}
                className="mgmt-mobile-card"
              >
                <div className="mgmt-mobile-card-header">
                  <Text strong style={{ fontSize: 14, flex: 1 }}>{record.title}</Text>
                  {getStatusTag(record.status)}
                </div>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                  {record.content?.substring(0, 80)}{record.content?.length > 80 ? '...' : ''}
                </Text>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {new Date(record.createdAt).toLocaleString('zh-CN')}
                  </Text>
                  <Dropdown
                    menu={{
                      items: [
                        { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: () => handlePreview(record) },
                        { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEdit(record) },
                        { type: 'divider' },
                        { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDelete(record.id) },
                      ],
                    }}
                    trigger={['click']}
                  >
                    <Button type="text" size="small" icon={<MoreOutlined />} style={{ width: 32, height: 32 }} />
                  </Dropdown>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Table
            columns={columns}
            dataSource={announcements}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50'],
              showQuickJumper: true,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            size="middle"
          />
        )}
      </Card>

      {isMobile ? (
        <M5BottomSheet
          open={modalVisible}
          onClose={handleModalCancel}
          title={editingAnnouncement ? '编辑公告' : '新增公告'}
          footer={
            <>
              <Button onClick={handleModalCancel} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" onClick={handleModalOk} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button>
            </>
          }
        >
          <Form form={form} layout="vertical">
            <Form.Item
              label="公告标题"
              name="title"
              rules={[{ required: true, message: '请输入公告标题' }]}
            >
              <Input placeholder="请输入公告标题" />
            </Form.Item>
            <Form.Item
              label="公告内容"
              name="content"
              rules={[{ required: true, message: '请输入公告内容' }]}
            >
              <TextArea
                rows={6}
                placeholder="请输入公告内容（支持 Markdown 格式）"
              />
            </Form.Item>
            <Form.Item
              label="状态"
              name="status"
              rules={[{ required: true, message: '请选择状态' }]}
            >
              <Select>
                <Select.Option value={1}>启用</Select.Option>
                <Select.Option value={0}>禁用</Select.Option>
              </Select>
            </Form.Item>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title={editingAnnouncement ? '编辑公告' : '新增公告'}
          open={modalVisible}
          onOk={handleModalOk}
          onCancel={handleModalCancel}
          width={600}
          okText="确定"
          cancelText="取消"
        >
          <Form form={form} layout="vertical">
            <Form.Item
              label="公告标题"
              name="title"
              rules={[{ required: true, message: '请输入公告标题' }]}
            >
              <Input placeholder="请输入公告标题" />
            </Form.Item>
            <Form.Item
              label="公告内容"
              name="content"
              rules={[{ required: true, message: '请输入公告内容' }]}
            >
              <TextArea
                rows={6}
                placeholder="请输入公告内容（支持 Markdown 格式）"
              />
            </Form.Item>
            <Form.Item
              label="状态"
              name="status"
              rules={[{ required: true, message: '请选择状态' }]}
            >
              <Select>
                <Select.Option value={1}>启用</Select.Option>
                <Select.Option value={0}>禁用</Select.Option>
              </Select>
            </Form.Item>
          </Form>
        </Modal>
      )}

      <Modal
        title="公告详情"
        open={previewVisible}
        onCancel={() => {
          setPreviewVisible(false);
          setPreviewAnnouncement(null);
        }}
        footer={[
          <Button key="close" onClick={() => {
            setPreviewVisible(false);
            setPreviewAnnouncement(null);
          }}>
            关闭
          </Button>,
        ]}
        width={600}
      >
        {previewAnnouncement && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Text strong style={{ fontSize: 18, display: 'block', marginBottom: 8 }}>
                {previewAnnouncement.title}
              </Text>
              <Space>
                {getStatusTag(previewAnnouncement.status)}
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {new Date(previewAnnouncement.createdAt).toLocaleString('zh-CN')}
                </Text>
              </Space>
            </div>
            <div style={{
              padding: 16,
              background: '#fafafa',
              borderRadius: 8,
              lineHeight: 1.8,
              whiteSpace: 'pre-wrap',
            }}>
              {previewAnnouncement.content}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default AnnouncementManagementContent;
