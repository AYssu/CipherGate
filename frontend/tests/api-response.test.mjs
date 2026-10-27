import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const source = readFileSync(new URL('../src/services/apiResponse.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 } });
const { normalizeResponse, shouldExpireSession, isCurrentPortalSession, getErrorMessage, ApiError, toApiError, responseRequestId } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('both clients use one envelope and reject business failures including HTTP200', () => {
  assert.deepEqual(normalizeResponse({ success: true, code: 200, data: { id: 1 } }), { success: true, code: 200, message: undefined, data: { id: 1 } });
  assert.deepEqual(normalizeResponse([1]), { success: true, code: 200, data: [1] });
  for (const body of [{ success: false, message: 'denied' }, { code: 429, message: 'limited' }])
    assert.throws(() => normalizeResponse(body), error => error.name === 'ApiError' && error.code >= 400);
});
test('failed authentication and public queries cannot expire another session', () => {
  assert.equal(shouldExpireSession('/auth/login', 'portal'), false);
  assert.equal(shouldExpireSession('/auth/recovery/apps', 'portal'), false);
  assert.equal(shouldExpireSession('/public/app-user/self/query-expire', 'admin'), false);
  assert.equal(shouldExpireSession('/open/trial', 'admin'), false);
  assert.equal(shouldExpireSession('/settings/profile', 'portal'), true);
  assert.equal(shouldExpireSession('/user/info', 'admin'), true);
});
test('a stale failed request cannot erase a newly established portal session', () => {
  assert.equal(isCurrentPortalSession('Bearer old-token', 'new-token'), false);
  assert.equal(isCurrentPortalSession(undefined, 'new-token'), false);
  assert.equal(isCurrentPortalSession('Bearer same-token', 'same-token'), true);
  assert.equal(isCurrentPortalSession('Bearer token', null), false);
});
test('unknown errors use a typed safe fallback', () => {
  assert.equal(getErrorMessage(new Error('failed'), 'fallback'), 'failed');
  assert.equal(getErrorMessage(null, 'fallback'), 'fallback');
  assert.equal(getErrorMessage({ code: 400, message: '导出额度不足' }, 'fallback'), '导出额度不足');
  assert.equal(getErrorMessage({ message: 123 }, 'fallback'), 'fallback');
});


test('both clients preserve a bounded support request ID on HTTP and business failures', () => {
  const failure = toApiError({ response: { status: 403, data: { code: 403, message: '验证码已过期，请重新获取' }, headers: { 'x-request-id': 'support_123' } } });
  assert.equal(failure.code, 403);
  assert.equal(failure.requestId, 'support_123');
  assert.equal(getErrorMessage(failure), '验证码已过期，请重新获取（请求编号：support_123）');
  assert.equal(toApiError(failure), failure);
  assert.throws(() => normalizeResponse({ code: 429, success: false, message: '请稍后重试' }, 'business-456'), error =>
    error.requestId === 'business-456' && getErrorMessage(error).includes('business-456'));
  for (const id of ['x'.repeat(65), 'unsafe\r\nheader', '<script>', '', undefined]) {
    assert.equal(responseRequestId({ 'x-request-id': id }), undefined);
    assert.equal(new ApiError('失败', 400, id).requestId, undefined);
  }
  assert.equal(responseRequestId({ 'X-Request-ID': 'case-safe' }), 'case-safe');
});

test('transport ambiguity is distinguished from HTTP rejection and safe server errors', () => {
  assert.match(getErrorMessage(toApiError({ code: 'ECONNABORTED', request: {} })), /请求超时.*先查询状态/);
  assert.match(getErrorMessage(toApiError({ code: 'ETIMEDOUT' })), /请求超时/);
  assert.match(getErrorMessage(toApiError({ request: {} })), /连接中断.*先查询状态/);
  assert.match(getErrorMessage(toApiError({ response: { status: 429 } })), /过于频繁/);
  assert.match(getErrorMessage(toApiError({ response: { status: 413 } })), /文件过大/);
  assert.match(getErrorMessage(toApiError({ response: { status: 503, data: { message: 'jdbc:mysql://private-secret' } } })), /服务暂时不可用/);
  assert.doesNotMatch(getErrorMessage(toApiError({ response: { status: 503, data: { message: 'private-secret' } } })), /private-secret/);
  assert.doesNotThrow(() => getErrorMessage({ code: 'ERR_CANCELED' }));
  assert.equal(toApiError(null).code, 0);
});
