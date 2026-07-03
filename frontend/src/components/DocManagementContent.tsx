import React, { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Upload,
  message,
  Popconfirm,
  Tag,
  Row,
  Col,
  Grid,
  Dropdown,
  Empty,
  Tooltip,
  List,
  Progress,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  EyeOutlined,
  MoreOutlined,
  FileTextOutlined,
  PaperClipOutlined,
  InboxOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import MDEditor from '@uiw/react-md-editor';
import { useNavigate } from 'react-router-dom';
import { docApi } from '../services/docService';
import { chunkedUploadService } from '../services/chunkedUploadService';
import type { DocCategory, DocItem, DocAttachment } from '../services/docService';
import M5BottomSheet from './M5BottomSheet';

const { TextArea } = Input;

// MDEditor wrapper
const MdEditorWrapper: React.FC<{
  value?: string;
  onChange?: (value: string) => void;
  height?: number;
}> = ({ value, onChange, height = 400 }) => {
  return (
      <div data-color-mode="light">
        <MDEditor
            value={value || ''}
            onChange={(val) => onChange?.(val || '')}
            height={height}
            preview="live"
            data-color-mode="light"
        />
      </div>
  );
};

const DocManagementContent: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const navigate = useNavigate();

  const [categories, setCategories] = useState<DocCategory[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [items, setItems] = useState<DocItem[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);

  const [categoryModalVisible, setCategoryModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<DocCategory | null>(null);
  const [categoryForm] = Form.useForm();

  const [itemModalVisible, setItemModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<DocItem | null>(null);
  const [itemForm] = Form.useForm();

  const [attachmentModalVisible, setAttachmentModalVisible] = useState(false);
  const [attachmentDocId, setAttachmentDocId] = useState<number | null>(null);
  const [attachments, setAttachments] = useState<DocAttachment[]>([]);
  const [attachmentsLoading, setAttachmentsLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const fetchCategories = useCallback(async () => {
    setCategoriesLoading(true);
    try {
      const result = await docApi.getCategories();
      setCategories((result as any).data || []);
    } catch (error) {
      console.error('获取分类列表失败:', error);
    } finally {
      setCategoriesLoading(false);
    }
  }, []);

  const fetchItems = useCallback(async (categoryId: number) => {
    setItemsLoading(true);
    try {
      const result = await docApi.getDocsByCategory(categoryId);
      setItems((result as any).data || []);
    } catch (error) {
      console.error('获取文档列表失败:', error);
    } finally {
      setItemsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  useEffect(() => {
    if (selectedCategoryId !== null) {
      fetchItems(selectedCategoryId);
    }
  }, [selectedCategoryId, fetchItems]);

  const handleCategorySelect = (record: DocCategory) => {
    setSelectedCategoryId(record.id);
  };

  const handleAddCategory = () => {
    setEditingCategory(null);
    setCategoryModalVisible(true);
    categoryForm.resetFields();
    categoryForm.setFieldsValue({ sortOrder: 0, status: 1 });
  };

  const handleEditCategory = (record: DocCategory) => {
    setEditingCategory(record);
    setCategoryModalVisible(true);
    categoryForm.setFieldsValue(record);
  };

  const handleDeleteCategory = async (id: number) => {
    try {
      await docApi.deleteCategory(id);
      message.success('删除成功');
      if (selectedCategoryId === id) {
        setSelectedCategoryId(null);
        setItems([]);
      }
      fetchCategories();
    } catch (error) {
      console.error('删除分类失败:', error);
    }
  };

  const handleCategorySubmit = async () => {
    try {
      const values = await categoryForm.validateFields();
      if (editingCategory) {
        await docApi.updateCategory(editingCategory.id, values);
        message.success('更新成功');
      } else {
        await docApi.createCategory(values);
        message.success('创建成功');
      }
      setCategoryModalVisible(false);
      fetchCategories();
    } catch (error) {
      console.error('提交分类失败:', error);
    }
  };

  const handleAddItem = () => {
    if (selectedCategoryId === null) {
      message.warning('请先选择一个分类');
      return;
    }
    setEditingItem(null);
    setItemModalVisible(true);
    itemForm.resetFields();
    itemForm.setFieldsValue({ categoryId: selectedCategoryId, status: 1 });
  };

  const handleEditItem = (record: DocItem) => {
    setEditingItem(record);
    setItemModalVisible(true);
    itemForm.setFieldsValue(record);
  };

  const handleDeleteItem = async (id: number) => {
    try {
      await docApi.deleteDoc(id);
      message.success('删除成功');
      if (selectedCategoryId !== null) {
        fetchItems(selectedCategoryId);
      }
    } catch (error) {
      console.error('删除文档失败:', error);
    }
  };

  const handleItemSubmit = async () => {
    try {
      const values = await itemForm.validateFields();
      if (editingItem) {
        await docApi.updateDoc(editingItem.id, values);
        message.success('更新成功');
      } else {
        await docApi.createDoc({ ...values, categoryId: selectedCategoryId! });
        message.success('创建成功');
      }
      setItemModalVisible(false);
      if (selectedCategoryId !== null) {
        fetchItems(selectedCategoryId);
      }
    } catch (error) {
      console.error('提交文档失败:', error);
    }
  };

  const handleManageAttachments = async (docId: number) => {
    setAttachmentDocId(docId);
    setAttachmentModalVisible(true);
    setAttachmentsLoading(true);
    try {
      const result = await docApi.getDocDetail(docId);
      const data = (result as any).data;
      setAttachments(data?.attachments || []);
    } catch (error) {
      console.error('获取附件失败:', error);
    } finally {
      setAttachmentsLoading(false);
    }
  };

  const handleUploadAttachment = async (file: File) => {
    if (!attachmentDocId) return;
    setUploading(true);
    try {
      const objectKey = `doc-attachments/${attachmentDocId}/${Date.now()}_${file.name}`;
      await chunkedUploadService.uploadFile({ file, objectKey });
      await docApi.addAttachment(attachmentDocId, file.name, objectKey, file.size, file.type);
      message.success('上传成功');
      const result = await docApi.getDocDetail(attachmentDocId);
      const data = (result as any).data;
      setAttachments(data?.attachments || []);
    } catch (error) {
      message.error('上传失败');
      console.error('上传附件失败:', error);
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAttachment = async (id: number) => {
    try {
      await docApi.deleteAttachment(id);
      message.success('删除成功');
      if (attachmentDocId) {
        const result = await docApi.getDocDetail(attachmentDocId);
        const data = (result as any).data;
        setAttachments(data?.attachments || []);
      }
    } catch (error) {
      console.error('删除附件失败:', error);
    }
  };

  const getStatusTag = (status: number) => {
    return status === 1
      ? <Tag color="success">启用</Tag>
      : <Tag color="error">禁用</Tag>;
  };

  const selectedCategory = categories.find(c => c.id === selectedCategoryId);

  // 移动端分类操作菜单
  const getCategoryActions = (record: DocCategory) => (
    <Dropdown
      menu={{
        items: [
          { key: 'view', label: '查看文档', icon: <EyeOutlined />, onClick: () => handleCategorySelect(record) },
          { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditCategory(record) },
          { type: 'divider' },
          { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDeleteCategory(record.id) },
        ],
      }}
      trigger={['click']}
    >
      <Button type="text" size="small" icon={<MoreOutlined />} />
    </Dropdown>
  );

  // 移动端文档操作菜单
  const getItemActions = (record: DocItem) => (
    <Dropdown
      menu={{
        items: [
          { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: () => navigate(`/docs/view/${record.id}`) },
          { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditItem(record) },
          { key: 'attach', label: '附件', icon: <PaperClipOutlined />, onClick: () => handleManageAttachments(record.id) },
          { type: 'divider' },
          { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDeleteItem(record.id) },
        ],
      }}
      trigger={['click']}
    >
      <Button type="text" size="small" icon={<MoreOutlined />} />
    </Dropdown>
  );

  const selectedCategoryName = selectedCategory?.name || '请选择分类';

  return (
    <Row gutter={isMobile ? 0 : 20} style={{ height: '100%' }}>
      {/* 左侧分类面板 */}
      <Col xs={24} sm={24} md={7} lg={6}>
        <Card
          title={
            <Space>
              <FolderOutlined />
              <span>文档分类</span>
            </Space>
          }
          extra={
            <Tooltip title={isMobile ? undefined : "新增分类"}>
              <Button type="primary" icon={<PlusOutlined />} size={isMobile ? 'small' : 'middle'} onClick={handleAddCategory}>
                {isMobile ? '新增' : '新增分类'}
              </Button>
            </Tooltip>
          }
          styles={{ body: { padding: 0 } }}
        >
          {categoriesLoading ? (
            <div style={{ padding: 24, textAlign: 'center' }}>加载中...</div>
          ) : categories.length === 0 ? (
            <Empty description="暂无分类" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
          ) : (
            <div style={{ maxHeight: isMobile ? 300 : 'calc(100vh - 200px)', overflowY: 'auto' }}>
              {categories.map((cat) => (
                <div
                  key={cat.id}
                  onClick={() => handleCategorySelect(cat)}
                  style={{
                    padding: '12px 16px',
                    cursor: 'pointer',
                    borderLeft: selectedCategoryId === cat.id ? '3px solid #1677ff' : '3px solid transparent',
                    backgroundColor: selectedCategoryId === cat.id ? '#e6f4ff' : 'transparent',
                    transition: 'all 0.2s',
                    borderBottom: '1px solid #f0f0f0',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: selectedCategoryId === cat.id ? 600 : 400, fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {cat.name}
                      </div>
                      {cat.description && (
                        <div style={{ fontSize: 12, color: '#999', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {cat.description}
                        </div>
                      )}
                    </div>
                    <div style={{ marginLeft: 8, flexShrink: 0 }}>
                      {isMobile ? (
                        getCategoryActions(cat)
                      ) : (
                        <Space size={0}>
                          <Tooltip title="编辑">
                            <Button type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); handleEditCategory(cat); }} />
                          </Tooltip>
                          <Popconfirm title="确定要删除这个分类吗？" onConfirm={() => handleDeleteCategory(cat.id)} okText="确定" cancelText="取消">
                            <Tooltip title="删除">
                              <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                            </Tooltip>
                          </Popconfirm>
                        </Space>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </Col>

      {/* 右侧文档列表 */}
      <Col xs={24} sm={24} md={17} lg={18}>
        <Card
          title={
            <Space>
              <FileTextOutlined />
              <span>{selectedCategoryName} - 文档列表</span>
            </Space>
          }
          extra={
            selectedCategoryId !== null ? (
              <Tooltip title={isMobile ? undefined : "新增文档"}>
                <Button type="primary" icon={<PlusOutlined />} size={isMobile ? 'small' : 'middle'} onClick={handleAddItem}>
                  {isMobile ? '新增' : '新增文档'}
                </Button>
              </Tooltip>
            ) : null
          }
        >
          {selectedCategoryId === null ? (
            <Empty description="请在左侧选择一个分类查看文档" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '60px 0' }} />
          ) : (
            <Table
              columns={[
                {
                  title: '标题',
                  dataIndex: 'title',
                  key: 'title',
                  ellipsis: true,
                },
                ...(!isMobile ? [{
                  title: '作者',
                  dataIndex: 'authorName',
                  key: 'authorName',
                  width: 100,
                  render: (text: string) => text || '-',
                }] : []),
                ...(!isMobile ? [{
                  title: '浏览',
                  dataIndex: 'viewCount',
                  key: 'viewCount',
                  width: 60,
                  render: (count: number) => count || 0,
                }] : []),
                {
                  title: '状态',
                  dataIndex: 'status',
                  key: 'status',
                  width: 70,
                  render: (status: number) => getStatusTag(status),
                },
                {
                  title: '操作',
                  key: 'action',
                  width: 90,
                  render: (_: any, record: DocItem) => {
                    if (isMobile) {
                      return getItemActions(record);
                    }
                    return (
                      <Dropdown
                        menu={{
                          items: [
                            { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: () => navigate(`/docs/view/${record.id}`) },
                            { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditItem(record) },
                            { key: 'attach', label: '附件', icon: <PaperClipOutlined />, onClick: () => handleManageAttachments(record.id) },
                            { type: 'divider' },
                            { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDeleteItem(record.id) },
                          ],
                        }}
                        trigger={['click']}
                      >
                        <Button type="link" size="small" icon={<MoreOutlined />}>
                          操作
                        </Button>
                      </Dropdown>
                    );
                  },
                },
              ]}
              dataSource={items}
              loading={itemsLoading}
              rowKey="id"
              pagination={{
                simple: isMobile,
                showSizeChanger: !isMobile,
                showQuickJumper: !isMobile,
                showTotal: isMobile ? undefined : (total) => `共 ${total} 条记录`,
              }}
              size={isMobile ? 'small' : 'middle'}
              scroll={isMobile ? { x: 300 } : undefined}
            />
          )}
        </Card>
      </Col>

      {/* 分类 Modal */}
      {isMobile ? (
        <M5BottomSheet
          open={categoryModalVisible}
          onClose={() => setCategoryModalVisible(false)}
          title={editingCategory ? '编辑分类' : '新增分类'}
          footer={
            <>
              <Button onClick={() => setCategoryModalVisible(false)} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" onClick={handleCategorySubmit} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button>
            </>
          }
        >
          <Form form={categoryForm} layout="vertical">
            <Form.Item label="分类名称" name="name" rules={[{ required: true, message: '请输入分类名称' }]}>
              <Input placeholder="请输入分类名称" />
            </Form.Item>
            <Form.Item label="描述" name="description">
              <TextArea rows={3} placeholder="请输入分类描述" />
            </Form.Item>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="排序" name="sortOrder">
                  <InputNumber min={0} placeholder="排序值" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="状态" name="status" valuePropName="value">
                  <Switch
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                    checked={categoryForm.getFieldValue('status') === 1}
                    onChange={(checked) => categoryForm.setFieldsValue({ status: checked ? 1 : 0 })}
                  />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title={editingCategory ? '编辑分类' : '新增分类'}
          open={categoryModalVisible}
          onOk={handleCategorySubmit}
          onCancel={() => setCategoryModalVisible(false)}
          width={500}
        >
          <Form form={categoryForm} layout="vertical">
            <Form.Item label="分类名称" name="name" rules={[{ required: true, message: '请输入分类名称' }]}>
              <Input placeholder="请输入分类名称" />
            </Form.Item>
            <Form.Item label="描述" name="description">
              <TextArea rows={3} placeholder="请输入分类描述" />
            </Form.Item>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="排序" name="sortOrder">
                  <InputNumber min={0} placeholder="排序值" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="状态" name="status" valuePropName="value">
                  <Switch
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                    checked={categoryForm.getFieldValue('status') === 1}
                    onChange={(checked) => categoryForm.setFieldsValue({ status: checked ? 1 : 0 })}
                  />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </Modal>
      )}

      {/* 文档 Modal / BottomSheet */}
      {isMobile ? (
        <M5BottomSheet
          open={itemModalVisible}
          onClose={() => setItemModalVisible(false)}
          title={editingItem ? '编辑文档' : '新增文档'}
          maxHeight="95vh"
          footer={
            <>
              <Button onClick={() => setItemModalVisible(false)} style={{ flex: 1, height: 44, borderRadius: 10 }}>取消</Button>
              <Button type="primary" onClick={handleItemSubmit} style={{ flex: 1, height: 44, borderRadius: 10 }}>确定</Button>
            </>
          }
        >
          <Form form={itemForm} layout="vertical">
            <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入文档标题' }]}>
              <Input placeholder="请输入文档标题" />
            </Form.Item>
            <Form.Item label="内容 (Markdown)" name="content" rules={[{ required: true, message: '请输入文档内容' }]}>
              <MdEditorWrapper height={300} />
            </Form.Item>
            <Form.Item label="作者名称" name="authorName">
              <Input placeholder="请输入作者名称" />
            </Form.Item>
            <Form.Item label="GitHub" name="authorGithub">
              <Input placeholder="GitHub 用户名" />
            </Form.Item>
            <Form.Item label="QQ" name="authorQq">
              <Input placeholder="QQ 号码" />
            </Form.Item>
            <Form.Item label="Bilibili" name="authorBilibili">
              <Input placeholder="Bilibili 用户名" />
            </Form.Item>
          </Form>
        </M5BottomSheet>
      ) : (
        <Modal
          title={editingItem ? '编辑文档' : '新增文档'}
          open={itemModalVisible}
          onOk={handleItemSubmit}
          onCancel={() => setItemModalVisible(false)}
          width={700}
        >
          <Form form={itemForm} layout="vertical">
            <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入文档标题' }]}>
              <Input placeholder="请输入文档标题" />
            </Form.Item>
            <Form.Item label="内容 (Markdown)" name="content" rules={[{ required: true, message: '请输入文档内容' }]}>
              <MdEditorWrapper height={400} />
            </Form.Item>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="作者名称" name="authorName">
                  <Input placeholder="请输入作者名称" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="GitHub" name="authorGithub">
                  <Input placeholder="GitHub 用户名" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="QQ" name="authorQq">
                  <Input placeholder="QQ 号码" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="Bilibili" name="authorBilibili">
                  <Input placeholder="Bilibili 用户名" />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </Modal>
      )}

      {/* 附件管理 Modal */}
      <Modal
        title="文档附件管理"
        open={attachmentModalVisible}
        onCancel={() => setAttachmentModalVisible(false)}
        footer={null}
        width={isMobile ? '100%' : 600}
      >
        <Upload.Dragger
          multiple={false}
          showUploadList={false}
          disabled={uploading}
          beforeUpload={(file) => {
            handleUploadAttachment(file as unknown as File);
            return false;
          }}
          style={{ marginBottom: 16 }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">支持任意格式文件</p>
        </Upload.Dragger>
        {uploading && <Progress percent={100} status="active" style={{ marginBottom: 16 }} />}
        <List
          loading={attachmentsLoading}
          dataSource={attachments}
          locale={{ emptyText: '暂无附件' }}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Tooltip title="下载" key="download">
                  <Button
                    type="link"
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={async () => {
                      try {
                        const response = await fetch(`/api/doc/attachments/${item.id}/file`, {
                          credentials: 'include',
                        });
                        if (!response.ok) {
                          if (response.status === 404) {
                            const error = await response.json();
                            message.error(error.message || '附件文件不存在');
                          } else if (response.status === 429) {
                            message.warning('下载过于频繁，请1分钟后再试');
                          } else {
                            message.error('下载失败');
                          }
                          return;
                        }
                        const blob = await response.blob();
                        const url = window.URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = item.fileName;
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        window.URL.revokeObjectURL(url);
                      } catch (err) {
                        message.error('下载失败');
                      }
                    }}
                  />
                </Tooltip>,
                <Popconfirm title="确定删除此附件？" onConfirm={() => handleDeleteAttachment(item.id)} key="delete">
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                avatar={<PaperClipOutlined style={{ fontSize: 20, color: '#1677ff' }} />}
                title={item.fileName}
                description={item.fileSize ? `${(item.fileSize / 1024).toFixed(1)} KB` : ''}
              />
            </List.Item>
          )}
        />
      </Modal>
    </Row>
  );
};

export default DocManagementContent;
