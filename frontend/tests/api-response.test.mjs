import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const source = readFileSync(new URL('../src/services/apiResponse.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 } });
const { normalizeResponse, shouldExpireSession, isCurrentPortalSession, getErrorMessage } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

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
  assert.equal(getErrorMessage({ message: 123 }, 'fallback'), 'fallback');
});
