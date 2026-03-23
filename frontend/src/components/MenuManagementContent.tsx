import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  Switch,
  message,
  Popconfirm,
  Tag,
  Row,
  Col,
  Typography,
  Dropdown
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  FileOutlined,
  SettingOutlined,
  MoreOutlined
} from '@ant-design/icons';
import { MenuService } from '../services';
import type { Menu } from '../services/menuService';

const { Title } = Typography;
const { Option } = Select;

const MenuManagementContent: React.FC = () => {
  const [menus, setMenus] = useState<Menu[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingMenu, setEditingMenu] = useState<Menu | null>(null);
  const [parentOptions, setParentOptions] = useState<Menu[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [isMobile, setIsMobile] = useState(false);
  const [form] = Form.useForm();

  // 检测屏幕尺寸
  useEffect(() => {
    const checkScreenSize = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkScreenSize();
    window.addEventListener('resize', checkScreenSize);
    return () => window.removeEventListener('resize', checkScreenSize);
  }, []);

  // 菜单类型选项
  const menuTypeOptions = [
    { label: '目录', value: 1 },
    { label: '菜单', value: 2 },
    { label: '按钮', value: 3 }
  ];

  // 图标选项
  const iconOptions = [
    { label: '仪表板', value: 'dashboard' },
    { label: '用户', value: 'user' },
    { label: '设置', value: 'setting' },
    { label: '安全', value: 'safety' },
    { label: '扫描', value: 'security' },
    { label: '团队', value: 'team' },
    { label: '菜单', value: 'menu' },
    { label: '锁定', value: 'lock' },
    { label: '工具', value: 'tool' }
  ];

  useEffect(() => {
    fetchMenus();
    fetchParentOptions();
  }, []);

  const fetchMenus = async () => {
    setLoading(true);
    try {
      const result = await MenuService.getAllMenus();
      setMenus((result as any).data || []);
    } catch (error) {
      console.error('获取菜单列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchParentOptions = async () => {
    try {
      const result = await MenuService.getParentMenuOptions();
      setParentOptions((result as any).data || []);
    } catch (error) {
      console.error('获取父菜单选项失败:', error);
    }
  };

  const handleAdd = () => {
    setEditingMenu(null);
    setModalVisible(true);
    form.resetFields();
    form.setFieldsValue({
      parentId: 0,
      menuType: 1,
      visible: true,
      status: true,
      sortOrder: 0
    });
  };

  const handleEdit = (record: Menu) => {
    setEditingMenu(record);
    setModalVisible(true);
    form.setFieldsValue({
      ...record,
      visible: record.visible === 1,
      status: record.status === 1
    });
  };

  const handleDelete = async (id: number) => {
    try {
      await MenuService.deleteMenu(id);
      message.success('删除成功');
      fetchMenus();
    } catch (error) {
      console.error('删除菜单失败:', error);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要删除的菜单');
      return;
    }

    try {
      await MenuService.batchDeleteMenus(selectedRowKeys as number[]);
      message.success('批量删除成功');
      setSelectedRowKeys([]);
      fetchMenus();
    } catch (error) {
      console.error('批量删除菜单失败:', error);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const menuData = {
        ...values,
        visible: values.visible ? 1 : 0,
        status: values.status ? 1 : 0
      };

      if (editingMenu) {
        await MenuService.updateMenu(editingMenu.id, menuData);
        message.success('更新成功');
      } else {
        await MenuService.createMenu(menuData);
        message.success('创建成功');
      }
      
      setModalVisible(false);
      fetchMenus();
      fetchParentOptions();
    } catch (error) {
      console.error('提交菜单失败:', error);
    }
  };

  const getMenuTypeTag = (type: number) => {
    switch (type) {
      case 1:
        return <Tag color="blue" icon={<FolderOutlined />}>目录</Tag>;
      case 2:
        return <Tag color="green" icon={<FileOutlined />}>菜单</Tag>;
      case 3:
        return <Tag color="orange" icon={<SettingOutlined />}>按钮</Tag>;
      default:
        return <Tag>未知</Tag>;
    }
  };

  const getStatusTag = (status: number) => {
    return status === 1 
      ? <Tag color="success">启用</Tag>
      : <Tag color="error">禁用</Tag>;
  };

  const getVisibleTag = (visible: number) => {
    return visible === 1 
      ? <Tag color="success">显示</Tag>
      : <Tag color="default">隐藏</Tag>;
  };

  // 将树形数据转换为表格数据，保持层级结构
  const convertMenusToTableData = (menus: Menu[]): any[] => {
    return menus.map(menu => ({
      ...menu,
      key: menu.id,
      children: menu.children && menu.children.length > 0 
        ? convertMenusToTableData(menu.children) 
        : undefined
    }));
  };

  const columns = [
    {
      title: '菜单名称',
      dataIndex: 'menuName',
      key: 'menuName',
      width: 200,
    },
    {
      title: '菜单编码',
      dataIndex: 'menuCode',
      key: 'menuCode',
      width: 180,
    },
    {
      title: '菜单类型',
      dataIndex: 'menuType',
      key: 'menuType',
      width: 100,
      render: (type: number) => getMenuTypeTag(type),
    },
    {
      title: '路径',
      dataIndex: 'path',
      key: 'path',
      width: 200,
      render: (path: string) => path || '-',
    },
    {
      title: '图标',
      dataIndex: 'icon',
      key: 'icon',
      width: 80,
      render: (icon: string) => icon || '-',
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
    },
    {
      title: '可见性',
      dataIndex: 'visible',
      key: 'visible',
      width: 80,
      render: (visible: number) => getVisibleTag(visible),
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
      width: 150,
      render: (_: any, record: Menu) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个菜单吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const rowSelection = {
    selectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setSelectedRowKeys(newSelectedRowKeys);
    },
  };

  // 移动端菜单操作
  const getMenuActionMenu = (menu: Menu) => ({
    items: [
      {
        key: 'edit',
        label: '编辑菜单',
        icon: <EditOutlined />,
        onClick: () => handleEdit(menu),
      },
      {
        key: 'delete',
        label: '删除菜单',
        icon: <DeleteOutlined />,
        danger: true,
        onClick: () => {
          Modal.confirm({
            title: '确认删除',
            content: `确定要删除菜单 ${menu.menuName} 吗？`,
            onOk: () => handleDelete(menu.id),
            okText: '确定',
            cancelText: '取消',
          });
        },
      },
    ],
  });

  // 移动端菜单项渲染
  const renderMobileMenuItem = (menu: Menu, level: number = 0) => (
    <div key={menu.id} style={{ marginLeft: level * 16 }}>
      <Card 
        size="small" 
        style={{ 
          marginBottom: 8,
          backgroundColor: level > 0 ? '#fafafa' : '#fff'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 4 }}>
              <span style={{ fontWeight: 500, marginRight: 8 }}>
                {menu.menuName}
              </span>
              {getMenuTypeTag(menu.menuType)}
            </div>
            <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>
              编码: {menu.menuCode}
            </div>
            {menu.path && (
              <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>
                路径: {menu.path}
              </div>
            )}
            <Space size={[4, 4]} wrap>
              {getVisibleTag(menu.visible)}
              {getStatusTag(menu.status)}
              <Tag color="default">排序: {menu.sortOrder}</Tag>
            </Space>
          </div>
          <Dropdown menu={getMenuActionMenu(menu)} trigger={['click']}>
            <Button type="text" icon={<MoreOutlined />} size="small" />
          </Dropdown>
        </div>
      </Card>
      {menu.children && menu.children.map(child => renderMobileMenuItem(child, level + 1))}
    </div>
  );

  // 移动端列表渲染
  const renderMobileList = () => (
    <div>
      {menus.map(menu => renderMobileMenuItem(menu))}
    </div>
  );

  return (
    <Card>
      <div style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle" gutter={[8, 8]}>
          <Col xs={24} sm={12}>
            <Title level={4} style={{ margin: 0 }}>菜单管理</Title>
          </Col>
          <Col xs={24} sm={12} style={{ textAlign: isMobile ? 'center' : 'right' }}>
            <Space wrap>
              {!isMobile && selectedRowKeys.length > 0 && (
                <Popconfirm
                  title={`确定要删除选中的 ${selectedRowKeys.length} 个菜单吗？`}
                  onConfirm={handleBatchDelete}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button danger>
                    批量删除 ({selectedRowKeys.length})
                  </Button>
                </Popconfirm>
              )}
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleAdd}
                size={isMobile ? 'middle' : 'middle'}
              >
                新增菜单
              </Button>
            </Space>
          </Col>
        </Row>
      </div>

      {isMobile ? renderMobileList() : (
        <Table
          columns={columns}
          dataSource={convertMenusToTableData(menus)}
          loading={loading}
          rowSelection={rowSelection}
          pagination={{
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          expandable={{
            defaultExpandAllRows: true,
          }}
        />
      )}

      <Modal
        title={editingMenu ? '编辑菜单' : '新增菜单'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={isMobile ? '95%' : 600}
        style={isMobile ? { top: 20 } : undefined}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            parentId: 0,
            menuType: 1,
            visible: true,
            status: true,
            sortOrder: 0
          }}
        >
          <Row gutter={isMobile ? [0, 0] : [16, 0]}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="菜单名称"
                name="menuName"
                rules={[{ required: true, message: '请输入菜单名称' }]}
              >
                <Input 
                  placeholder="请输入菜单名称" 
                  size={isMobile ? 'large' : 'middle'}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="菜单编码"
                name="menuCode"
                rules={[{ required: true, message: '请输入菜单编码' }]}
              >
                <Input 
                  placeholder="请输入菜单编码" 
                  size={isMobile ? 'large' : 'middle'}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? [0, 0] : [16, 0]}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="父菜单"
                name="parentId"
              >
                <Select 
                  placeholder="请选择父菜单"
                  size={isMobile ? 'large' : 'middle'}
                >
                  <Option value={0}>根菜单</Option>
                  {parentOptions.map(menu => (
                    <Option key={menu.id} value={menu.id}>
                      {menu.menuName}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="菜单类型"
                name="menuType"
                rules={[{ required: true, message: '请选择菜单类型' }]}
              >
                <Select 
                  placeholder="请选择菜单类型"
                  size={isMobile ? 'large' : 'middle'}
                >
                  {menuTypeOptions.map(option => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? [0, 0] : [16, 0]}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="路由路径"
                name="path"
              >
                <Input 
                  placeholder="请输入路由路径" 
                  size={isMobile ? 'large' : 'middle'}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="组件路径"
                name="component"
              >
                <Input 
                  placeholder="请输入组件路径" 
                  size={isMobile ? 'large' : 'middle'}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? [0, 0] : [16, 0]}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="菜单图标"
                name="icon"
              >
                <Select 
                  placeholder="请选择菜单图标" 
                  allowClear
                  size={isMobile ? 'large' : 'middle'}
                >
                  {iconOptions.map(option => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="排序"
                name="sortOrder"
              >
                <InputNumber
                  min={0}
                  placeholder="请输入排序值"
                  style={{ width: '100%' }}
                  size={isMobile ? 'large' : 'middle'}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={isMobile ? [0, 0] : [16, 0]}>
            <Col xs={24} sm={12}>
              <Form.Item
                label="是否可见"
                name="visible"
                valuePropName="checked"
              >
                <Switch 
                  checkedChildren="显示" 
                  unCheckedChildren="隐藏"
                  size={isMobile ? 'default' : 'default'}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                label="菜单状态"
                name="status"
                valuePropName="checked"
              >
                <Switch 
                  checkedChildren="启用" 
                  unCheckedChildren="禁用"
                  size={isMobile ? 'default' : 'default'}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Card>
  );
};

export default MenuManagementContent;