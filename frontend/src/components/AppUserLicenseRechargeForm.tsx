import { getErrorMessage } from '../services/apiResponse';
import { useState } from 'react';
import { Alert, Button, Form, Input, Modal, Typography } from 'antd';
import { CreditCardOutlined } from '@ant-design/icons';
import { redeemAppUserLicense, type AppUserLicenseRechargeResponse } from '../services/appUserSelfPublicService';

interface Props {
  appId: number | null;
  onSuccess: (data: AppUserLicenseRechargeResponse) => void;
}

export default function AppUserLicenseRechargeForm({ appId, onSuccess }: Props) {
  const [form] = Form.useForm<{ email: string; keyCode: string }>();
  const [pending, setPending] = useState<{ email: string; keyCode: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<AppUserLicenseRechargeResponse | null>(null);
  const [error, setError] = useState('');

  async function redeem() {
    if (!appId || !pending || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const response = await redeemAppUserLicense(appId, pending.email, pending.keyCode);
      if (response.code !== 200 || !response.data) throw new Error('充值未完成，请查询会员状态后重试');
      setSuccess(response.data);
      onSuccess(response.data);
      form.resetFields(['keyCode']);
    } catch (reason) {
      setSuccess(null);
      setError(getErrorMessage(reason, '充值失败，请稍后重试'));
    } finally {
      setSubmitting(false);
      setPending(null);
    }
  }

  return (
    <section aria-label="卡密充值">
      <Form name="license-recharge" form={form} layout="vertical" requiredMark={false} onFinish={values => {
        setSuccess(null);
        setError('');
        setPending({ email: values.email.trim().toLowerCase(), keyCode: values.keyCode.trim() });
      }}>
        <Form.Item name="email" label="充值邮箱" rules={[
          { required: true, message: '请输入要充值的注册邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
          { max: 100, message: '邮箱过长' },
        ]}>
          <Input type="email" autoComplete="email" placeholder="请输入本应用的注册邮箱" disabled={!appId || submitting} />
        </Form.Item>
        <Form.Item name="keyCode" label="充值卡密" rules={[{ required: true, whitespace: true, message: '请输入卡密' }, { max: 100, message: '卡密过长' }]}>
          <Input.Password autoComplete="off" placeholder="请输入充值卡密" disabled={!appId || submitting} />
        </Form.Item>
        <Button type="primary" htmlType="submit" block icon={<CreditCardOutlined />} loading={submitting} disabled={!appId || pending !== null}>
          兑换充值
        </Button>
      </Form>
      {success && <Alert showIcon type="success" message="卡密充值成功" description={
        `${success.emailMasked} 已到账，到期时间：${new Date(success.memberExpiresAt!).toLocaleString('zh-CN')}。卡密 ${success.keyCodeMasked} 已兑换。`
      } />}
      {error && <Alert showIcon type="error" message="卡密充值失败" description={`${error}。如遇网络中断，请先查询会员状态，确认是否到账。`} />}
      <Modal title="确认卡密充值" open={pending !== null} okText="确认充值" cancelText="取消"
        confirmLoading={submitting} closable={!submitting} maskClosable={!submitting}
        cancelButtonProps={{ disabled: submitting }} onCancel={() => { if (!submitting) setPending(null); }}
        onOk={() => void redeem()}>
        <Typography.Paragraph>将为本应用的 <Typography.Text strong>{pending?.email}</Typography.Text> 充值会员时长。</Typography.Paragraph>
        <Typography.Paragraph>请确认邮箱无误，充值成功后无法撤销。</Typography.Paragraph>
      </Modal>
    </section>
  );
}
