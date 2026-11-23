import { test, expect, type Page } from '@playwright/test';

const ok = (data: unknown) => ({ success: true, code: 200, data });
const menus = [
  { menuCode: 'app_management', menuName: '应用管理', icon: 'appstore', children: [
    { menuCode: 'app_list_page', menuName: '应用列表', path: '/applications/list' },
    { menuCode: 'credentials', menuName: '三方凭证', path: '/applications/credentials' },
  ] },
  { menuCode: 'system_management', menuName: '系统管理', children: [
    { menuCode: 'menus', menuName: '菜单管理', path: '/system?tab=menus' },
  ] },
  { menuCode: 'doc_management', menuName: '对接文档' },
  { menuCode: 'hidden', menuName: '不可见的菜单', visible: 0, path: '/system/users' },
];
async function mock(page: Page) {
  const calls: string[] = [];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    calls.push(path);
    let data: unknown = [];
    if (path === '/api/csrf') { await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'pro-test-csrf' } }); return; }
    if (path === '/api/user/info') data = { id: 7, name: '应用管理员', login: 'owner', roles: [{ roleCode: 'SUPER_ADMIN' }], menus };
    if (path === '/api/applications') data = { records: [], total: 0, current: 1, size: 10 };
    if (path.endsWith('/unread/count')) data = { total: 1, showBadge: true };
    if (path === '/api/doc/menu') data = [{ id: 1, name: '接入指南', items: [{ id: 25, title: '签名接入' }] }];
    if (path === '/api/doc/items/25') data = { id: 25, title: '签名接入', content: '测试文档', categoryName: '接入指南' };
    if (path === '/api/messages/my') data = [{ id: 1, title: '更新说明', content: '通知内容', importanceLevel: 'LOW', isRead: false, createdTime: '2026-11-23T00:00:00' }];
    await route.fulfill({ json: ok(data) });
  });
  return calls;
}
async function openNavigation(page: Page, mobile: boolean) {
  if (mobile) await page.getByRole('button', { name: '展开导航', exact: true }).click();
}

test('Pro navigation preserves server permissions, legacy menu aliases, nested docs and deep links', async ({ page, isMobile }, info) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await mock(page);
  await page.goto('/applications/list');
  await expect(page.locator('.ant-pro-layout')).toBeVisible();
  await openNavigation(page, isMobile);
  await expect(page.getByText('不可见的菜单', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: /用户管理/ })).toHaveCount(0);
  await page.getByRole('menuitem', { name: /系统管理$/ }).click();
  await page.getByRole('link', { name: '菜单管理', exact: true }).click();
  await expect(page).toHaveURL(/\/system\/menus$/);
  if (isMobile) await expect(page.getByRole('link', { name: '菜单管理', exact: true })).not.toBeVisible();
  await openNavigation(page, isMobile);
  await page.getByRole('menuitem', { name: /对接文档$/ }).click();
  await page.getByRole('menuitem', { name: /接入指南$/ }).click();
  await page.getByRole('link', { name: '签名接入', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/docs\/view\/25$/);
  await page.reload();
  await expect(page.locator('.ciphergate-page')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('暂时无法加载账户信息');
  await page.goto('/applications/list');
  await expect(page.getByRole('button', { name: /创建应用/ })).toBeVisible();
  const bounds = await page.getByRole('button', { name: /创建应用/ }).boundingBox();
  expect(bounds && bounds.x + bounds.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await info.attach('pro-application-layout', { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' });
  expect(errors).toEqual([]);
});

test('Pro header retains notifications, theme persistence and profile navigation', async ({ page, isMobile }) => {
  const calls = await mock(page);
  await page.goto('/applications/list');
  await page.getByRole('button', { name: '系统消息', exact: true }).click();
  const drawer = page.getByRole('dialog');
  await expect(drawer.getByText('更新说明', { exact: true })).toBeVisible();
  await drawer.getByRole('button', { name: '一键已读', exact: true }).click();
  await expect.poll(() => calls.includes('/api/messages/read/all')).toBe(true);
  await drawer.getByRole('button', { name: /Close|关闭/ }).click();
  await page.getByRole('button', { name: '切换主题', exact: true }).click();
  await page.getByRole('menuitem', { name: /优雅紫/ }).click();
  expect(await page.evaluate(() => localStorage.getItem('ciphergate_theme'))).toBe('purple');
  await page.reload();
  await expect(page.getByRole('button', { name: '用户菜单', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '用户菜单', exact: true }).click();
  await page.getByRole('menuitem', { name: /个人资料/ }).click();
  await expect(page).toHaveURL(/\/profile$/);
  if (isMobile) await expect(page.getByRole('button', { name: '展开导航', exact: true })).toBeVisible();
});

for (const path of ['/license?id=12', '/portal/login', '/portal/dashboard']) {
  test(`public/portal scope does not request an administrator session: ${path}`, async ({ page }) => {
    const calls = await mock(page);
    if (path === '/portal/dashboard') await page.addInitScript(() => localStorage.setItem('portal_token', 'pro-portal-session'));
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    expect(calls).not.toContain('/api/user/info');
    await expect(page.getByRole('button', { name: '系统消息', exact: true })).toHaveCount(0);
    await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
    await expect(page.locator('meta[name="viewport"]')).toHaveAttribute('content', 'width=device-width, initial-scale=1.0');
  });
}

test('administrator bootstrap recovers after a network failure without rendering privileged content', async ({ page }) => {
  await mock(page);
  let fail = true;
  await page.route('**/api/user/info', async route => fail ? route.abort('failed') : route.fulfill({ json: ok({ id: 7, login: 'owner', roles: [], menus: [] }) }));
  await page.goto('/applications/list');
  await expect(page.getByText('暂时无法加载账户信息', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /创建应用/ })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: '重新加载', exact: true }).click();
  await expect(page.getByRole('button', { name: /创建应用/ })).toBeVisible();
});
