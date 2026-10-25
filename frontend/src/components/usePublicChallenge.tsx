import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Button, Form, Input, message } from 'antd';
import request from '../utils/request';
import type { ApiResponse } from '../services/apiResponse';
export type Challenge = { captchaId: string; captchaCode: string };
type ImageChallenge = { captchaId: string; image: string };
export function usePublicChallenge(purpose: string, label = '图形验证码') {
  const [challenge, setChallenge] = useState<ImageChallenge | null>(null);
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const id = useId();
  const sequence = useRef(0);
  const refresh = useCallback(async () => {
    const requestSequence = ++sequence.current;
    setChallenge(null); setCode(''); setLoading(true);
    try { const response = await request.get<unknown, ApiResponse<ImageChallenge>>('/public/challenge', { params: { purpose } }); if (sequence.current === requestSequence) setChallenge(response.data); }
    catch { /* interceptor displays a recoverable error */ }
    finally { if (sequence.current === requestSequence) setLoading(false); }
  }, [purpose]);
  useEffect(() => { void refresh(); }, [refresh]);
  function get(): Challenge | null {
    if (!challenge || !code.trim()) { message.warning(`请先填写${label}`); return null; }
    return { captchaId: challenge.captchaId, captchaCode: code.trim() };
  }
  const fields = <Form.Item label={label} htmlFor={id} extra="每次操作后需重新验证。看不清可点击图片刷新。">
    <div className="public-service-row">
      <Input id={id} aria-label={label} value={code} onChange={event => setCode(event.target.value)} maxLength={5} inputMode="numeric" autoComplete="off" style={{ flex: 1, minWidth: 0 }} />
      <Button htmlType="button" type="text" loading={loading} aria-label={`刷新${label}`} onClick={() => void refresh()} style={{ padding: 0, width: 140, height: 44, flexShrink: 0 }}>
        {challenge?.image ? <img src={challenge.image} alt={`${label}图片`} width={140} height={44} /> : '点击获取验证码'}
      </Button>
    </div>
  </Form.Item>;
  return { fields, get, refresh };
}
