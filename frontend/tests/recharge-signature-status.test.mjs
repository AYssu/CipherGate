import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/utils/rechargeSignatureStatus.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { getRechargeSignatureStatus } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

for (const code of ['TIMESTAMP_EXPIRED', 'BAD_API_KEY', 'CREDENTIAL_EXPIRED', 'IP_DENIED', 'BAD_PROJECT_KEY', 'APP_DENIED', 'APP_NOT_FOUND']) {
  test(`${code}: historical zero means not verified, not bad signature`, () => {
    const status = getRechargeSignatureStatus({ signValid: 0, errorCode: code });
    assert.equal(status.text, '未校验');
    assert.equal(status.color, 'default');
  });
}
test('BAD_SIGN explicitly marks a signature mismatch', () => {
  assert.equal(getRechargeSignatureStatus({ signValid: 0, errorCode: 'BAD_SIGN' }).text, '未通过');
});
test('successful verification remains passed even when recharge later fails', () => {
  for (const code of [undefined, 'APP_USER_NOT_FOUND', 'TOTAL_CALL_LIMIT_EXCEEDED', 'ORDER_ALREADY_PROCESSED']) {
    assert.equal(getRechargeSignatureStatus({ signValid: 1, errorCode: code }).text, '通过');
  }
});
test('ambiguous legacy/internal-error records are unknown, not a mismatch', () => {
  for (const log of [{}, { signValid: 0 }, { signValid: 0, errorCode: 'INTERNAL_ERROR' }]) {
    assert.equal(getRechargeSignatureStatus(log).text, '未知');
  }
});
