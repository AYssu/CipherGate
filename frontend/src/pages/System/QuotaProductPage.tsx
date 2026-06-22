import React from 'react';
import { Card, Table, Button, Modal, Form, Input, InputNumber, Select, message, Typography, Grid } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

const { Title } = Typography;

const QuotaProductPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [products, setProducts] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [editVisible, setEditVisible] = React.useState(false);
  const [editing, setEditing] = React.useState<any>(null);
  const [form] = Form.useForm();

  React.useEffect(() => { fetchProducts(); }, []);

  const fetchProducts = () => {
    fetch('/api/quota-products', { credentials: 'include' })
      .then(res => res.json())
      .then(data => { if (data.success) setProducts(data.data || []); setLoading(false); })
      .catch(() => setLoading(false));
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    values.price = values.price * 100;
    const url = editing ? `/api/quota-products/${editing.id}` : '/api/quota-products';
    const method = editing ? 'PUT' : 'POST';
    const res = await fetch(url, { method, credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values) });
    const data = await res.json();
    if (data.success) { message.success('保存成功'); setEditVisible(false); fetchProducts(); }
    else message.error(data.message);
  };

  const columns = [
    { title: '编码', dataIndex: 'productCode', key: 'code' },
    { title: '名称', dataIndex: 'productName', key: 'name' },
    { title: '类型', dataIndex: 'productType', key: 'type' },
    { title: '额度', dataIndex: 'quotaValue', key: 'quota' },
    { title: '价格', dataIndex: 'price', key: 'price', render: (v: number) => `¥${(v / 100).toFixed(2)}` },
    { title: '操作', key: 'action', render: (_: any, record: any) => <Button type="link" onClick={() => { setEditing(record); form.setFieldsValue({ ...record, price: record.price / 100 }); setEditVisible(true); }}>编辑</Button> },
  ];

  return (
    <div style={{ padding: isMobile ? 12 : 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: isMobile ? 12 : 16 }}>
          <Title level={isMobile ? 5 : 4} style={{ margin: 0 }}>额度商品管理</Title>
          <Button type="primary" size={isMobile ? 'small' : 'middle'} icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setEditVisible(true); }}>新增商品</Button>
        </div>
        <Table
          columns={columns}
          dataSource={products}
          rowKey="id"
          loading={loading}
          size={isMobile ? 'small' : 'middle'}
          scroll={{ x: isMobile ? 420 : undefined, y: isMobile ? 450 : undefined }}
          pagination={false}
        />
        <Modal
          title={editing ? '编辑商品' : '新增商品'}
          open={editVisible}
          onOk={handleSave}
          onCancel={() => setEditVisible(false)}
          width={isMobile ? '100%' : 520}
          className={isMobile ? 'mobile-modal' : undefined}
        >
          <Form form={form} layout="vertical">
            <Form.Item name="productCode" label="商品编码" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="productName" label="商品名称" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="productType" label="商品类型" rules={[{ required: true }]}>
              <Select options={[{ value: 'APP_QUOTA', label: '应用额度' }, { value: 'LICENSE_QUOTA', label: '卡密额度' }, { value: 'USER_REGISTER_QUOTA', label: '用户注册额度' }, { value: 'TRAFFIC_QUOTA', label: '流量额度' }, { value: 'MEMBERSHIP', label: '会员升级' }]} />
            </Form.Item>
            <Form.Item name="quotaValue" label="额度值" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="price" label="价格(元)" rules={[{ required: true }]}><InputNumber min={0} step={0.01} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="description" label="描述"><Input.TextArea rows={2} /></Form.Item>
          </Form>
        </Modal>
      </Card>
    </div>
  );
};

export default QuotaProductPage;
