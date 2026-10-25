import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/services/chunkedUploadService.ts', import.meta.url), 'utf8')
  .replace("import request from '../utils/request';", 'const request = globalThis.__chunkUploadTestRequest;');
const calls = [];
let failChunk = false;
globalThis.__chunkUploadTestRequest = {
  async post(path, body, config) {
    calls.push({ path, body, config });
    if (path === '/upload/init') return { data: { uploadId: 'session', objectKey: 'doc-attachments/10/server-generated-key', chunkSize: 3, totalChunks: 2 } };
    if (path.startsWith('/upload/chunk') && failChunk) throw new Error('network');
    return { data: {} };
  },
  async delete(path) { calls.push({ path }); },
};
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { chunkedUploadService } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('uses server negotiated chunk size and returns server generated key', async () => {
  calls.length = 0; failChunk = false;
  const file = new File([new Uint8Array([1,2,3,4,5])], 'package.zip');
  const key = await chunkedUploadService.uploadFile({ file, objectKey: 'doc-attachments/10/requested-key', chunkSize: 5 });
  assert.equal(key, 'doc-attachments/10/server-generated-key');
  const chunks = calls.filter(c => c.path.startsWith('/upload/chunk'));
  assert.deepEqual(chunks.map(c => c.body.get('file').size), [3, 2]);
  assert.deepEqual([...new Uint8Array(await chunks[1].body.get('file').arrayBuffer())], [4,5]);
  assert.equal(calls.at(-1).path, '/upload/complete');
  assert.equal(calls.at(-1).config.timeout, 30 * 60 * 1000);
  assert.ok(chunks.every(c => c.config.timeout === 5 * 60 * 1000));
});

test('failed upload cancels session and never completes it', async () => {
  calls.length = 0; failChunk = true;
  await assert.rejects(chunkedUploadService.uploadFile({ file: new File(['abcde'], 'a.zip'), objectKey: 'doc-attachments/10/a.zip' }), /network/);
  assert.equal(calls.at(-1).path, '/upload/session');
  assert.equal(calls.filter(c => c.path === '/upload/complete').length, 0);
});


test('upload proxy preserves request routes and headers with dedicated matching timeout budgets', () => {
  const nginx = readFileSync(new URL('../nginx.conf', import.meta.url), 'utf8');
  for (const [endpoint, seconds] of [['chunk', 300], ['complete', 1800]]) {
    const start = nginx.indexOf(`location = /api/upload/${endpoint} {`);
    assert.ok(start >= 0);
    const block = nginx.slice(start, nginx.indexOf('\n    }', start));
    assert.ok(block.includes('proxy_pass http://backend:${BACKEND_CONTAINER_PORT};'));
    assert.ok(block.includes(`proxy_read_timeout ${seconds}s;`));
    assert.ok(block.includes(`proxy_send_timeout ${seconds}s;`));
    for (const header of ['Host $host', 'X-Real-IP $remote_addr', 'X-Forwarded-For $remote_addr', 'X-Forwarded-Proto $scheme']) {
      assert.ok(block.includes(`proxy_set_header ${header};`));
    }
  }
  const ordinaryStart = nginx.indexOf('location /api/ {');
  const ordinary = nginx.slice(ordinaryStart, nginx.indexOf('\n    }', ordinaryStart));
  assert.ok(ordinary.includes('proxy_read_timeout 60s;'));
});
