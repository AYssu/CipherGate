const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

export async function csrfHeaders(method = 'GET', path?: string): Promise<Record<string, string>> {
  if (SAFE_METHODS.has(method.toUpperCase())) return {};
  if (path) {
    const url = new URL(path, window.location.origin);
    if (url.origin !== window.location.origin) throw new Error('会话请求必须使用同源地址');
    // Public challenge-protected APIs deliberately do not depend on a browser session.
    if (/^\/api\/(?:public|v1|open)(?:\/|$)/.test(url.pathname)) return {};
  }
  // Fetch anew: login, logout and credential changes may rotate the session/token.
  const response = await fetch('/api/csrf', { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new Error('安全校验初始化失败，请刷新后重试');
  const body = await response.json();
  if (body.headerName !== 'X-CSRF-TOKEN' || typeof body.token !== 'string' || !body.token) {
    throw new Error('安全校验响应无效，请刷新后重试');
  }
  return { [body.headerName]: body.token };
}

export async function sessionFetch(input: string, init: RequestInit = {}): Promise<Response> {
  // Do not attach a session-bound token to external URLs.
  const url = new URL(input, window.location.origin);
  if (url.origin !== window.location.origin) throw new Error('会话请求必须使用同源地址');
  const headers = new Headers(init.headers);
  for (const [name, value] of Object.entries(await csrfHeaders(init.method, url.pathname))) headers.set(name, value);
  return fetch(input, { ...init, headers, credentials: 'same-origin' });
}
