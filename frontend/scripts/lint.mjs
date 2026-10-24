import { ESLint } from 'eslint';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { classify, fingerprint } from './lint-baseline.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const eslint = new ESLint({ cwd: root });
const results = await eslint.lintFiles(['.']);
const findings = [];
for (const result of results) {
  const source = result.source ?? await readFile(result.filePath, 'utf8');
  for (const message of result.messages) findings.push({
    filePath: result.filePath, message, key: fingerprint(result.filePath, message, source, root),
  });
}
const baseline = JSON.parse(await readFile(resolve(root, 'eslint-legacy-any.json'), 'utf8'));
const { failures, stale } = classify(findings, baseline);
for (const { filePath, message } of failures) {
  console.error(`${filePath}:${message.line}:${message.column} ${message.ruleId}: ${message.message}`);
}
if (stale.length) {
  console.error('Remove resolved entries from eslint-legacy-any.json (the baseline must only shrink):');
  for (const [key, count] of stale) console.error(`${key} (${count})`);
}
console.log(`Lint: ${findings.length - failures.length} recorded legacy any findings; ${failures.length} new violations.`);
process.exitCode = failures.length || stale.length ? 1 : 0;
