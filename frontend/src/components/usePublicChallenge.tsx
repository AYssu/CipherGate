import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Form, Input, message } from 'antd';
import CaptchaImage from './CaptchaImage';
import request from '../utils/request';
import type { ApiResponse } from '../services/apiResponse';
export type Challenge = { captchaId: string; captchaCode: string };
type ImageChallenge = { captchaId: string; image: string };
export function usePublicChallenge(purpose: string, label = '图形验证码') {
  const [challenge, setChallenge] = useState<ImageChallenge | null>(null);
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const id = useId();
  const sequence = useRef(0);
  const refresh = useCallback(async () => {
    const requestSequence = ++sequence.current;
    setChallenge(null); setCode(''); setLoading(true); setFailed(false);
    try { const response = await request.get<unknown, ApiResponse<ImageChallenge>>('/public/challenge', { params: { purpose, _t: Date.now() } }); if (!response.data?.captchaId || !response.data.image) throw new Error('验证码图片为空'); if (sequence.current === requestSequence) setChallenge(response.data); }
    catch { if (sequence.current === requestSequence) setFailed(true); }
    finally { if (sequence.current === requestSequence) setLoading(false); }
  }, [purpose]);
  useEffect(() => { void refresh(); }, [refresh]);
  function get(): Challenge | null {
    if (!challenge || failed || !code.trim()) { message.warning(`请先填写${label}`); return null; }
    return { captchaId: challenge.captchaId, captchaCode: code.trim() };
  }
  const fields = <Form.Item label={label} htmlFor={id} extra={failed ? <span role="status" className="public-service-error">验证码加载失败，请点击重试或检查网络。</span> : "输入图片中的数字，看不清可点击图片换一张。"}>
    <div className="public-service-row">
      <Input id={id} aria-label={label} placeholder="请输入图片中的数字" value={code} onChange={event => setCode(event.target.value)} maxLength={5} inputMode="numeric" autoComplete="off" style={{ flex: 1, minWidth: 0 }} />
      <CaptchaImage image={challenge?.image} loading={loading} failed={failed} label={label}
        onRefresh={() => void refresh()} onError={() => { setFailed(true); setChallenge(null); setCode(''); }} />
    </div>
  </Form.Item>;
  return { fields, get, refresh };
}
