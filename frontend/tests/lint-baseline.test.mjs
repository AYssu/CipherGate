import test from 'node:test';
import assert from 'node:assert/strict';
import { classify, fingerprint, legacyRule } from '../scripts/lint-baseline.mjs';
const message = { ruleId: legacyRule, line: 1, column: 10 };
const entry = key => ({ key, message });
test('only the recorded occurrence is exempted; duplicated debt fails', () => {
  assert.equal(classify([entry('a')], { a: 1 }).failures.length, 0);
  assert.equal(classify([entry('a'), entry('a')], { a: 1 }).failures.length, 1);
});
test('other rules never receive a legacy exemption', () => {
  assert.equal(classify([{ key: 'a', message: { ruleId: 'no-unused-vars' } }], { a: 1 }).failures.length, 1);
});
test('new source and files fail, resolved debt must be removed', () => {
  const original = fingerprint('/repo/a.ts', message, 'const a: any = 1;', '/repo');
  const changed = fingerprint('/repo/a.ts', message, 'const a: any = 2;', '/repo');
  const moved = fingerprint('/repo/b.ts', message, 'const a: any = 1;', '/repo');
  assert.notEqual(original, changed);
  assert.notEqual(original, moved);
  assert.equal(classify([entry(changed)], { [original]: 1 }).failures.length, 1);
  assert.deepEqual(classify([], { [original]: 1 }).stale, [[original, 1]]);
});
test('line shifts preserve fingerprint; separate any tokens remain distinct', () => {
  const source = 'const a: any = f<any>();';
  assert.equal(fingerprint('/repo/a', message, source, '/repo'), fingerprint('/repo/a', { ...message, line: 2 }, '\n' + source, '/repo'));
  assert.notEqual(fingerprint('/repo/a', message, source, '/repo'), fingerprint('/repo/a', { ...message, column: 18 }, source, '/repo'));
});
