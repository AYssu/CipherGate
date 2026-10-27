import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const { outputText } = ts.transpileModule(readFileSync(new URL('../src/utils/csrf.ts',import.meta.url),'utf8'),{ compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2023} });
const { csrfHeaders,sessionFetch } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
globalThis.window={location:{origin:'https://ciphergate.example.test'}};
test('session mutation obtains fresh session-bound token and preserves existing request headers',async()=>{
  const calls=[];let sequence=0;
  globalThis.fetch=async(input,init)=>{calls.push({input,init});if(input==='/api/csrf')return {ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:`token-${++sequence}`})};return {ok:true};};
  await sessionFetch('/api/payment/create',{method:'POST',headers:{'Content-Type':'application/json'}});
  await sessionFetch('/api/payment/create',{method:'POST'});
  assert.equal(calls[1].init.headers.get('X-CSRF-TOKEN'),'token-1');
  assert.equal(calls[1].init.headers.get('Content-Type'),'application/json');
  assert.equal(calls[3].init.headers.get('X-CSRF-TOKEN'),'token-2');
  assert.equal(calls[1].init.credentials,'same-origin');
});
test('reads and public challenge-protected writes need no CSRF bootstrap',async()=>{
  globalThis.fetch=()=>{throw new Error('unexpected csrf request');};
  assert.deepEqual(await csrfHeaders('GET','/api/user/info'),{});
  assert.deepEqual(await csrfHeaders('POST','/api/public/app-user/register/submit'),{});
});
test('failed or malformed bootstrap never sends mutation or leaks token to another origin',async()=>{
  let calls=0;
  globalThis.fetch=async()=>{calls++;return {ok:false};};
  await assert.rejects(sessionFetch('/api/tickets',{method:'POST'}));assert.equal(calls,1);
  await assert.rejects(sessionFetch('https://other.example.test/api/tickets',{method:'POST'}));assert.equal(calls,1);
  await assert.rejects(csrfHeaders('POST','https://other.example.test/api/tickets'));assert.equal(calls,1);
  globalThis.fetch=async()=>({ok:true,json:async()=>({headerName:'wrong-header',token:'wrong'})});
  await assert.rejects(sessionFetch('/api/tickets',{method:'POST'}));
});
