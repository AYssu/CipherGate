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
  message,
  Popconfirm,
  Tag,
  Row,
  Col,
  Grid,
  Dropdown,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  EyeOutlined,
  MoreOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { docApi } from '../services/docService';
import type { DocCategory, DocItem } from '../services/docService';

const { TextArea } = Input;

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

  const getStatusTag = (status: number) => {
    return status === 1
      ? <Tag color="success">启用</Tag>
      : <Tag color="error">禁用</Tag>;
  };

  const categoryColumns = [
    {
      title: '分类名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: DocCategory) => (
        <a onClick={() => handleCategorySelect(record)}>{text}</a>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (text: string) => text || '-',
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => getStatusTag(status),
    },
    {
      title: '操作',
      key: 'action',
      width: isMobile ? 80 : 150,
      render: (_: any, record: DocCategory) => {
        if (isMobile) {
          return (
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
        }
        return (
          <Space size="small">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleCategorySelect(record)}>
              查看
            </Button>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditCategory(record)}>
              编辑
            </Button>
            <Popconfirm title="确定要删除这个分类吗？" onConfirm={() => handleDeleteCategory(record.id)} okText="确定" cancelText="取消">
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const itemColumns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
    },
    {
      title: '作者',
      dataIndex: 'authorName',
      key: 'authorName',
      render: (text: string) => text || '-',
    },
    {
      title: '浏览',
      dataIndex: 'viewCount',
      key: 'viewCount',
      width: 80,
      render: (count: number) => count || 0,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => getStatusTag(status),
    },
    {
      title: '操作',
      key: 'action',
      width: isMobile ? 80 : 150,
      render: (_: any, record: DocItem) => {
        if (isMobile) {
          return (
            <Dropdown
              menu={{
                items: [
                  { key: 'view', label: '查看', icon: <EyeOutlined />, onClick: () => navigate(`/docs/view/${record.id}`) },
                  { key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEditItem(record) },
                  { type: 'divider' },
                  { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDeleteItem(record.id) },
                ],
              }}
              trigger={['click']}
            >
              <Button type="text" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          );
        }
        return (
          <Space size="small">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/docs/view/${record.id}`)}>
              查看
            </Button>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditItem(record)}>
              编辑
            </Button>
            <Popconfirm title="确定要删除这个文档吗？" onConfirm={() => handleDeleteItem(record.id)} okText="确定" cancelText="取消">
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const selectedCategory = categories.find(c => c.id === selectedCategoryId);

  return (
    <Row gutter={isMobile ? 0 : 16} style={{ height: '100%' }}>
      <Col xs={24} sm={24} md={8} lg={7} style={{ marginBottom: isMobile ? 16 : 0 }}>
        <Card
          title={
            <Space>
              <FolderOutlined />
              <span>文档分类</span>
            </Space>
          }
          extra={
            <Button type="primary" icon={<PlusOutlined />} size={isMobile ? 'small' : 'middle'} onClick={handleAddCategory}>
              新增
            </Button>
          }
          bodyStyle={{ padding: isMobile ? 12 : 16 }}
        >
          <Table
            columns={categoryColumns}
            dataSource={categories}
            loading={categoriesLoading}
            rowKey="id"
            pagination={false}
            size="small"
            onRow={(record) => ({
              onClick: () => handleCategorySelect(record),
              style: {
                cursor: 'pointer',
                backgroundColor: selectedCategoryId === record.id ? '#e6f4ff' : undefined,
              },
            })}
          />
        </Card>
      </Col>

      <Col xs={24} sm={24} md={16} lg={17}>
        <Card
          title={
            <Space>
              <FileTextOutlined />
              <span>{selectedCategory ? `${selectedCategory.name} - 文档列表` : '请选择分类'}</span>
            </Space>
          }
          extra={
            selectedCategoryId !== null ? (
              <Button type="primary" icon={<PlusOutlined />} size={isMobile ? 'small' : 'middle'} onClick={handleAddItem}>
                新增文档
              </Button>
            ) : null
          }
          bodyStyle={{ padding: isMobile ? 12 : 16 }}
        >
          {selectedCategoryId === null ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              请在左侧选择一个分类查看文档
            </div>
          ) : (
            <Table
              columns={itemColumns}
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
            />
          )}
        </Card>
      </Col>

      {/* Category Modal */}
      <Modal
        title={editingCategory ? '编辑分类' : '新增分类'}
        open={categoryModalVisible}
        onOk={handleCategorySubmit}
        onCancel={() => setCategoryModalVisible(false)}
        width={isMobile ? '100%' : 500}
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

      {/* Item Modal */}
      <Modal
        title={editingItem ? '编辑文档' : '新增文档'}
        open={itemModalVisible}
        onOk={handleItemSubmit}
        onCancel={() => setItemModalVisible(false)}
        width={isMobile ? '100%' : 700}
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入文档标题' }]}>
            <Input placeholder="请输入文档标题" />
          </Form.Item>
          <Form.Item label="内容 (Markdown)" name="content" rules={[{ required: true, message: '请输入文档内容' }]}>
            <TextArea rows={10} placeholder="请输入文档内容，支持 Markdown 格式" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item label="作者名称" name="authorName">
                <Input placeholder="请输入作者名称" />
              </Form.Item>
            </Col>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item label="GitHub" name="authorGithub">
                <Input placeholder="GitHub 用户名" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item label="QQ" name="authorQq">
                <Input placeholder="QQ 号码" />
              </Form.Item>
            </Col>
            <Col span={isMobile ? 24 : 12}>
              <Form.Item label="Bilibili" name="authorBilibili">
                <Input placeholder="Bilibili 用户名" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Row>
  );
};

export default DocManagementContent;
