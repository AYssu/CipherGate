/** Both HTTP clients return the same envelope; authentication remains scoped to each client. */
export interface ApiResponse<T> { success: boolean; code: number; message?: string; data: T }
export interface PageResult<T> { records: T[]; current: number; size: number; total: number }
export class ApiError extends Error {
  readonly code: number;
  constructor(message: string, code: number) { super(message); this.name = 'ApiError'; this.code = code; }
}
export function normalizeResponse(value: unknown): ApiResponse<unknown> {
  if (value && typeof value === 'object' && ('success' in value || 'code' in value)) {
    const data = value as { success?: boolean; code?: number; message?: string; data?: unknown };
    const code = data.code ?? (data.success === false ? 400 : 200);
    if (data.success === false || (code !== 200 && code !== 0)) throw new ApiError(data.message || '请求失败', code);
    return { success: true, code, message: data.message, data: data.data };
  }
  return { success: true, code: 200, data: value };
}
export function shouldExpireSession(url = '', scope: 'admin' | 'portal'): boolean {
  if (scope === 'portal') return !['/auth/login', '/auth/captcha', '/auth/recovery/', '/auth/select-app'].some(path => url.startsWith(path));
  return !url.startsWith('/public/') && !url.startsWith('/open/') && !url.startsWith('/auth/');
}

export function getErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error && typeof error === 'object' && 'response' in error && error.response && typeof error.response === 'object'
      && 'data' in error.response && error.response.data && typeof error.response.data === 'object'
      && 'message' in error.response.data && typeof error.response.data.message === 'string' && error.response.data.message)
    return error.response.data.message;
  if (error instanceof Error && error.message) return error.message;
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string' && error.message) return error.message;
  return fallback;
}
export function isCurrentPortalSession(authorization: unknown, token: string | null): boolean {
  return !!token && authorization === `Bearer ${token}`;
}
