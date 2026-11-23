import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import ts from 'typescript';
const load = async path => import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText).toString('base64')}`);
const { default: routes } = await load('../config/routes.ts');
const { isAdminPath } = await load('../src/navigation/routePolicy.ts');
const expected = [
  '/', '/register', '/license', '/app-user', '/login', '/dashboard', '/profile',
  '/applications/list', '/applications/licenses', '/applications/users', '/applications/user-transfer', '/applications/variables', '/applications/credentials', '/applications/call-logs',
  '/user/membership', '/user/balance', '/user/checkin', '/user/invite', '/user/orders', '/user/tickets',
  '/system/users', '/system/roles', '/system/menus', '/system/permissions', '/system/info', '/system/config', '/system/announcements',
  '/membership/levels', '/membership/users', '/membership/products', '/operation/orders', '/operation/tickets',
  '/plugins/list', '/plugins/function', '/admin/docs/categories', '/admin/docs/view/:id',
  '/portal/login', '/portal/recovery', '/portal/select-app', '/portal/dashboard', '/portal/membership', '/portal/recharge', '/portal/orders', '/portal/settings', '*',
];
function leaves(items, parent = '', inheritedLayout) {
  return items.flatMap(route => {
    const path = route.path.startsWith('/') || route.path === '*' ? route.path : `${parent}/${route.path}`;
    return route.routes ? leaves(route.routes, path, route.layout ?? inheritedLayout) : [{ ...route, layout: route.layout ?? inheritedLayout, path }];
  });
}
test('Umi preserves every previously published business URL and resolves its page module', () => {
  const all = leaves(routes);
  assert.deepEqual(all.map(route => route.path).sort(), expected.toSorted());
  for (const route of all) if (route.component) {
    assert.ok(existsSync(new URL(`../src/${route.component.replace('@/', '')}.tsx`, import.meta.url)), route.path);
  }
});
test('public and portal routes opt out of administrator layout and session loading', () => {
  for (const route of leaves(routes)) if (!isAdminPath(route.path)) assert.equal(route.layout, false, route.path);
  for (const path of ['/user', '/user/orders', '/system/config', '/admin/docs/view/25']) assert.equal(isAdminPath(path), true, path);
  for (const path of ['/', '/register', '/license', '/app-user', '/portal/login', '/portal/dashboard', '/users-public', '/dashboard-other']) assert.equal(isAdminPath(path), false, path);
});
