import type { ThirdPartyRechargeLog } from '../services/callLogService';

const rejectedBeforeSignature = new Set([
  'TIMESTAMP_EXPIRED',
  'BAD_API_KEY',
  'CREDENTIAL_EXPIRED',
  'IP_DENIED',
  'BAD_PROJECT_KEY',
  'APP_DENIED',
  'APP_NOT_FOUND',
  'OPERATION_DENIED',
  'INVALID_REQUEST',
  'RATE_LIMITED',
]);

/** Legacy logs use signValid=0 both for a mismatch and for requests rejected before verification. */
export function getRechargeSignatureStatus(log: Pick<ThirdPartyRechargeLog, 'signValid' | 'errorCode'>) {
  if (log.errorCode === 'BAD_SIGN') {
    return { text: '未通过', color: 'error', explanation: '已执行签名校验，签名不匹配。' };
  }
  if (log.signValid === 1) {
    return { text: '通过', color: 'blue', explanation: '签名校验通过；调用结果请查看状态和错误信息。' };
  }
  if (log.errorCode && rejectedBeforeSignature.has(log.errorCode)) {
    return { text: '未校验', color: 'default', explanation: '请求在签名校验前被拒绝，请查看错误信息。' };
  }
  return { text: '未知', color: 'default', explanation: '现有记录不足以确定是否执行了签名校验。' };
}
