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
  message,
  Popconfirm,
  Tree,
  Checkbox
} from 'antd';
import { 
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ApiOutlined,
  MenuOutlined,
  SafetyOutlined
} from '@ant-design/icons';
import { roleApi } from '../services';
import { MenuService } from '../services';
import { permissionApi } from '../services';
import type { Role, Permission } from '../services';
import type { Menu } from '../services/menuService';

const { Title, Text } = Typography;

const RoleManagementContent: React.FC = () => {
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [menuModalVisible, setMenuModalVisible] = useState(false);
  const [permissionModalVisible, setPermissionModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [currentRole, setCurrentRole] = useState<Role | null>(null);
  const [allMenus, setAllMenus] = useState<Menu[]>([]);
  const [allPermissions, setAllPermissions] = useState<Permission[]>([]);
  const [roleMenuIds, setRoleMenuIds] = useState<number[]>([]);
  const [rolePermissionIds, setRolePermissionIds] = useState<number[]>([]);
  const [form] = Form.useForm();

  // 获取角色列表
  const fetchRoles = async () => {
    setLoading(true);
    try {
      const result = await roleApi.getRoles();
      setRoles((result as any).data || []);
    } catch (error) {
      console.error('获取角色列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
    fetchAllMenus();
    fetchAllPermissions();
  }, []);

  // 获取所有菜单
  const fetchAllMenus = async () => {
    try {
      const result = await MenuService.getAllMenus();
      setAllMenus((result as any).data || []);
    } catch (error) {
      console.error('获取菜单列表失败:', error);
    }
  };

  // 获取所有权限
  const fetchAllPermissions = async () => {
    try {
      const result = await permissionApi.getPermissions();
      setAllPermissions((result as any).data || []);
    } catch (error) {
      console.error('获取权限列表失败:', error);
    }
  };

  // 获取角色的菜单权限
  const fetchRoleMenus = async (roleId: number) => {
    try {
      const result = await roleApi.getRoleMenus(roleId);
      setRoleMenuIds((result as any).data || []);
    } catch (error) {
      console.error('获取角色菜单权限失败:', error);
    }
  };

  // 获取角色的API权限
  const fetchRolePermissions = async (roleId: number) => {
    try {
      const result = await roleApi.getRolePermissions(roleId);
      setRolePermissionIds((result as any).data || []);
    } catch (error) {
      console.error('获取角色API权限失败:', error);
    }
  };

  // 角色表格列定义
  const roleColumns = [
    {
      title: '角色名称',
      dataIndex: 'roleName',
      key: 'roleName',
    },
    {
      title: '角色编码',
      dataIndex: 'roleCode',
      key: 'roleCode',
      render: (code: string) => (
        <Tag color={code === 'SUPER_ADMIN' ? 'red' : code === 'ADMIN' ? 'blue' : 'green'}>
          {code}
        </Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (desc: string) => desc || '-',
    },
    {
      title: '权限数量',
      key: 'permissionCount',
      render: (record: Role) => (
        <Text>{record.permissions?.length || 0} 个权限</Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (record: Role) => (
        <Space>
          <Button 
            type="link" 
            icon={<MenuOutlined />}
            onClick={() => handleManageMenus(record)}
          >
            菜单权限
          </Button>
          <Button 
            type="link" 
            icon={<ApiOutlined />}
            onClick={() => handleManagePermissions(record)}
          >
            API权限
          </Button>
          <Button 
            type="link" 
            icon={<EditOutlined />}
            onClick={() => handleEditRole(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除角色 ${record.roleName} 吗？`}
            onConfirm={() => handleDeleteRole(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button 
              type="link" 
              danger
              icon={<DeleteOutlined />}
              disabled={record.roleCode === 'SUPER_ADMIN'}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleEditRole = (role: Role) => {
    setEditingRole(role);
    form.setFieldsValue({
      roleName: role.roleName,
      roleCode: role.roleCode,
      description: role.description,
    });
    setModalVisible(true);
  };

  const handleDeleteRole = async (role: Role) => {
    try {
      await roleApi.deleteRole(role.id);
      message.success('角色删除成功');
      fetchRoles();
    } catch (error) {
      console.error('删除角色失败:', error);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      
      if (editingRole) {
        await roleApi.updateRole(editingRole.id, values);
        message.success('角色更新成功');
      } else {
        await roleApi.createRole(values);
        message.success('角色创建成功');
      }
      
      setModalVisible(false);
      setEditingRole(null);
      form.resetFields();
      fetchRoles();
    } catch (error) {
      console.error('操作角色失败:', error);
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingRole(null);
    form.resetFields();
  };

  const handleCreateRole = () => {
    setEditingRole(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleManageMenus = async (role: Role) => {
    setCurrentRole(role);
    await fetchRoleMenus(role.id);
    setMenuModalVisible(true);
  };

  const handleManagePermissions = async (role: Role) => {
    setCurrentRole(role);
    await fetchRolePermissions(role.id);
    setPermissionModalVisible(true);
  };

  // 将菜单树转换为Tree组件需要的格式
  const convertMenusToTreeData = (menus: Menu[]): any[] => {
    return menus.map(menu => ({
      title: menu.menuName,
      key: menu.id,
      children: menu.children && menu.children.length > 0 
        ? convertMenusToTreeData(menu.children) 
        : undefined
    }));
  };

  // 保存角色菜单权限
  const handleSaveMenuPermissions = async () => {
    if (!currentRole) return;

    try {
      await roleApi.assignMenusToRole(currentRole.id, roleMenuIds);
      message.success('菜单权限保存成功');
      setMenuModalVisible(false);
      setCurrentRole(null);
      setRoleMenuIds([]);
    } catch (error) {
      console.error('保存菜单权限失败:', error);
    }
  };

  // 保存角色API权限
  const handleSaveApiPermissions = async () => {
    if (!currentRole) return;

    try {
      await roleApi.assignPermissionsToRole(currentRole.id, rolePermissionIds);
      message.success('API权限保存成功');
      setPermissionModalVisible(false);
      setCurrentRole(null);
      setRolePermissionIds([]);
      fetchRoles(); // 刷新角色列表以更新权限显示
    } catch (error) {
      console.error('保存API权限失败:', error);
    }
  };

  const handleMenuModalCancel = () => {
    setMenuModalVisible(false);
    setCurrentRole(null);
    setRoleMenuIds([]);
  };

  const handlePermissionModalCancel = () => {
    setPermissionModalVisible(false);
    setCurrentRole(null);
    setRolePermissionIds([]);
  };

  return (
    <div style={{ padding: 0 }}>
      <Card>
        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Space>
              <SafetyOutlined style={{ color: '#1677ff', fontSize: 20 }} />
              <Title level={4} style={{ margin: 0 }}>角色管理</Title>
            </Space>
            <Space>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleCreateRole}
              >
                新建角色
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchRoles}
                loading={loading}
              >
                刷新
              </Button>
            </Space>
          </div>
        </div>
        
        <Table
          columns={roleColumns}
          dataSource={roles}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`,
          }}
          expandable={{
            expandedRowRender: (record: Role) => (
              <div style={{ padding: 16, background: '#fafafa' }}>
                <Title level={5}>权限列表</Title>
                <Space wrap>
                  {record.permissions?.map(permission => (
                    <Tag key={permission.id} color="blue">
                      {permission.permissionName}
                    </Tag>
                  )) || <Text type="secondary">暂无权限</Text>}
                </Space>
              </div>
            ),
            rowExpandable: (record: Role) => (record.permissions?.length || 0) > 0,
          }}
        />
      </Card>

      {/* 编辑/创建角色模态框 */}
      <Modal
        title={editingRole ? '编辑角色' : '创建角色'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={600}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="角色名称"
            name="roleName"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          
          <Form.Item
            label="角色编码"
            name="roleCode"
            rules={[{ required: true, message: '请输入角色编码' }]}
          >
            <Input 
              placeholder="请输入角色编码" 
              disabled={editingRole?.roleCode === 'SUPER_ADMIN'}
            />
          </Form.Item>
          
          <Form.Item
            label="角色描述"
            name="description"
          >
            <Input.TextArea 
              placeholder="请输入角色描述" 
              rows={3}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 菜单权限管理模态框 */}
      <Modal
        title={`管理角色菜单权限 - ${currentRole?.roleName}`}
        open={menuModalVisible}
        onOk={handleSaveMenuPermissions}
        onCancel={handleMenuModalCancel}
        width={600}
        okText="保存"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            请选择该角色可以访问的菜单项，已选择 {roleMenuIds.length} 个菜单
          </Text>
        </div>
        
        <Tree
          checkable
          checkedKeys={roleMenuIds}
          onCheck={(checkedKeys) => {
            setRoleMenuIds(checkedKeys as number[]);
          }}
          treeData={convertMenusToTreeData(allMenus)}
          height={400}
          defaultExpandAll
        />
      </Modal>

      {/* 权限管理模态框 */}
      <Modal
        title={`管理角色API权限 - ${currentRole?.roleName}`}
        open={permissionModalVisible}
        onOk={handleSaveApiPermissions}
        onCancel={handlePermissionModalCancel}
        width={800}
        okText="保存"
        cancelText="取消"
      >
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            请选择该角色可以使用的API权限，已选择 {rolePermissionIds.length} 个权限
          </Text>
        </div>
        
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            <Button 
              size="small"
              onClick={() => setRolePermissionIds(allPermissions.map(p => p.id))}
            >
              全选
            </Button>
            <Button 
              size="small"
              onClick={() => setRolePermissionIds([])}
            >
              清空
            </Button>
          </Space>
        </div>

        <div 
          className="hide-scrollbar"
          style={{ 
            maxHeight: 400, 
            overflowY: 'auto', 
            paddingRight: 8
          }}
        >
          {(() => {
            // 按资源类型分组权限
            const groupedPermissions = allPermissions.reduce((groups, permission) => {
              const category = permission.permissionCode.split('_')[0];
              if (!groups[category]) {
                groups[category] = [];
              }
              groups[category].push(permission);
              return groups;
            }, {} as Record<string, Permission[]>);

            const categoryNames: Record<string, string> = {
              'USER': '用户管理',
              'ROLE': '角色管理', 
              'MENU': '菜单管理',
              'PERMISSION': '权限管理',
              'CONFIG': '系统配置',
              'PROFILE': '个人信息'
            };

            return Object.entries(groupedPermissions).map(([category, permissions]) => (
              <div key={category} style={{ marginBottom: 20 }}>
                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  marginBottom: 12,
                  paddingBottom: 8,
                  borderBottom: '1px solid #f0f0f0'
                }}>
                  <Text strong style={{ marginRight: 8, fontSize: '13px' }}>
                    {categoryNames[category] || category}
                  </Text>
                  <Text type="secondary" style={{ fontSize: '11px' }}>
                    ({permissions.length} 个权限)
                  </Text>
                  <div style={{ marginLeft: 'auto' }}>
                    <Checkbox
                      indeterminate={
                        permissions.some(p => rolePermissionIds.includes(p.id)) && 
                        !permissions.every(p => rolePermissionIds.includes(p.id))
                      }
                      checked={permissions.every(p => rolePermissionIds.includes(p.id))}
                      onChange={(e) => {
                        const categoryPermissionIds = permissions.map(p => p.id);
                        if (e.target.checked) {
                          setRolePermissionIds([
                            ...rolePermissionIds.filter(id => !categoryPermissionIds.includes(id)),
                            ...categoryPermissionIds
                          ]);
                        } else {
                          setRolePermissionIds(
                            rolePermissionIds.filter(id => !categoryPermissionIds.includes(id))
                          );
                        }
                      }}
                    >
                      <span style={{ fontSize: '13px' }}>全选</span>
                    </Checkbox>
                  </div>
                </div>
                
                <div style={{ paddingLeft: 16 }}>
                  {permissions.map(permission => (
                    <div key={permission.id} style={{ marginBottom: 8 }}>
                      <Checkbox
                        checked={rolePermissionIds.includes(permission.id)}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setRolePermissionIds([...rolePermissionIds, permission.id]);
                          } else {
                            setRolePermissionIds(rolePermissionIds.filter(id => id !== permission.id));
                          }
                        }}
                      >
                        <div>
                          <Text strong style={{ fontSize: '13px' }}>
                            {permission.permissionName}
                          </Text>
                          <div style={{ marginTop: 2 }}>
                            <Tag color="blue" style={{ 
                              marginRight: 4, 
                              fontSize: '9px',
                              padding: '0 3px'
                            }}>
                              {permission.permissionCode}
                            </Tag>
                            {permission.httpMethod && (
                              <Tag color="green" style={{ 
                                fontSize: '9px',
                                padding: '0 3px'
                              }}>
                                {permission.httpMethod}
                              </Tag>
                            )}
                          </div>
                          <Text type="secondary" style={{ 
                            fontSize: '11px', 
                            display: 'block', 
                            marginTop: 2 
                          }}>
                            {permission.description}
                          </Text>
                        </div>
                      </Checkbox>
                    </div>
                  ))}
                </div>
              </div>
            ));
          })()}
        </div>
      </Modal>
    </div>
  );
};

export default RoleManagementContent;
