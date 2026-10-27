/** Both HTTP clients return the same envelope; authentication remains scoped to each client. */
export interface ApiResponse<T> { success: boolean; code: number; message?: string; data: T }
export interface PageResult<T> { records: T[]; current: number; size: number; total: number }
export class ApiError extends Error {
  readonly code: number;
  readonly requestId?: string;
  constructor(message: string, code: number, requestId?: string) {
    super(message); this.name = 'ApiError'; this.code = code; this.requestId = safeRequestId(requestId);
  }
}
export function normalizeResponse(value: unknown, requestId?: string): ApiResponse<unknown> {
  if (value && typeof value === 'object' && ('success' in value || 'code' in value)) {
    const data = value as { success?: boolean; code?: number; message?: string; data?: unknown };
    const code = data.code ?? (data.success === false ? 400 : 200);
    if (data.success === false || (code !== 200 && code !== 0)) throw new ApiError(data.message || '请求失败', code, requestId);
    return { success: true, code, message: data.message, data: data.data };
  }
  return { success: true, code: 200, data: value };
}
export function shouldExpireSession(url = '', scope: 'admin' | 'portal'): boolean {
  if (scope === 'portal') return !['/auth/login', '/auth/captcha', '/auth/recovery/', '/auth/select-app'].some(path => url.startsWith(path));
  return !url.startsWith('/public/') && !url.startsWith('/open/') && !url.startsWith('/auth/');
}

/** Only bounded IDs are safe to display or include in support tickets. */
export function safeRequestId(value: unknown): string | undefined {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value) ? value : undefined;
}
export function responseRequestId(headers: unknown): string | undefined {
  if (!headers || typeof headers !== 'object') return undefined;
  const values = headers as Record<string, unknown>;
  return safeRequestId(values['x-request-id'] ?? values['X-Request-ID']);
}

/** Keep HTTP/business failures distinct from transport errors across both clients. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  const value = error && typeof error === 'object' ? error as {
    response?: { status?: number; data?: { code?: number; message?: string }; headers?: unknown };
    code?: string; request?: unknown;
  } : undefined;
  if (value?.response) {
    const { status = 500, data, headers } = value.response;
    const fallback: Record<number, string> = {
      400: '请求参数无效，请检查输入', 401: '登录信息无效或已过期，请重新登录',
      403: '操作未获授权，请刷新页面或联系管理员确认权限', 404: '请求的资源不存在',
      408: '请求超时，请稍后重试', 413: '文件过大，请缩小后重试',
      429: '操作过于频繁，请稍后重试',
    };
    const serverMessage = status < 500 && typeof data?.message === 'string' && data.message.trim()
      ? data.message : undefined;
    return new ApiError(serverMessage || fallback[status] || (status >= 500
      ? '服务暂时不可用，请稍后重试' : `请求失败（${status}）`),
      typeof data?.code === 'number' ? data.code : status, responseRequestId(headers));
  }
  if (value?.code === 'ECONNABORTED' || value?.code === 'ETIMEDOUT')
    return new ApiError('请求超时，操作结果尚未确认，请先查询状态再重试', 408);
  if (value?.request) return new ApiError('连接中断，操作结果尚未确认，请检查网络并先查询状态', 0);
  const detail = error instanceof Error && error.message ? error.message : '请求未能发出，请刷新页面后重试';
  return new ApiError(detail, 0);
}

export function getErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error instanceof ApiError) return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message;
  if (error && typeof error === 'object' && ('response' in error || 'request' in error || ('code' in error && typeof error.code === 'string')))
    return getErrorMessage(toApiError(error), fallback);
  if (error instanceof Error && error.message) return error.message;
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string' && error.message) return error.message;
  return fallback;
}
export function isCurrentPortalSession(authorization: unknown, token: string | null): boolean {
  return !!token && authorization === `Bearer ${token}`;
}
