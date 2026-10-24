import { createHash } from 'node:crypto';
import { relative } from 'node:path';

export const legacyRule = '@typescript-eslint/no-explicit-any';

// Match the exact source line and occurrence within that line. Moving unchanged
// code does not create debt, but changing or adding an `any` does.
export function fingerprint(file, message, source, root) {
  const lines = source.split(/\r?\n/);
  const line = lines[message.line - 1] ?? '';
  const occurrencesBefore = (line.slice(0, message.column - 1).match(/\bany\b/g) ?? []).length;
  return [relative(root, file).replaceAll('\\', '/'), message.ruleId,
    createHash('sha256').update(`${line.trim()}#${occurrencesBefore}`).digest('hex')].join(':');
}

export function classify(findings, baseline) {
  const remaining = new Map(Object.entries(baseline));
  const failures = [];
  for (const finding of findings) {
    const count = remaining.get(finding.key) ?? 0;
    if (finding.message.ruleId === legacyRule && count > 0) {
      remaining.set(finding.key, count - 1);
    } else {
      failures.push(finding);
    }
  }
  return { failures, stale: [...remaining].filter(([, count]) => count > 0) };
}
