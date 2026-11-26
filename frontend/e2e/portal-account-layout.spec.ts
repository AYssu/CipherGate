import { test, expect, type Page } from '@playwright/test';
const ok = (data: unknown) => ({ code: 200, success: true, data });
const profile = { id: 7, username: '测试用户', nickname: '测试昵称', email: 'portal@example.test', avatarUrl: null, createdAt: '2026-11-01T10:00:00' };
const stats = {
  totalLoginCount: 128, appLoginCount: 96, portalLoginCount: 32, boundDeviceCount: 2,
  lastLoginIp: '203.0.113.28', lastLoginIpRegion: '广东省 广州市', lastLoginAt: '2026-11-25T10:30:00',
  loginTrend: [{ date: '2026-11-24', count: 3 }, { date: '2026-11-25', count: 5 }],
  ipDistribution: [{ name: '广东省', value: 5 }],
};
async function setup(page: Page) {
  await page.addInitScript(() => localStorage.setItem('portal_token', 'layout-test-session'));
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/csrf') { await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'layout-csrf' } }); return; }
    const data: Record<string, unknown> = {
      '/api/portal/settings/profile': profile,
      '/api/portal/dashboard/stats': stats,
      '/api/portal/dashboard/login-history': [{ id: 1, loginIp: '203.0.113.28', ipRegion: '广东省 广州市', status: 'SUCCESS', createdAt: '2026-11-25T10:30:00' }],
      '/api/portal/membership/info': { appId: 40, memberStatus: 'ACTIVE', memberActive: true, memberExpiresAt: '2026-12-25T10:00:00', loginCount: 96, trialApplied: false },
      '/api/portal/payment/orders': [],
    };
    await route.fulfill({ json: ok(data[path] ?? []) });
  });
}

test('account pages have a consistent background and remain usable at narrow widths', async ({ page }, info) => {
  await setup(page);
  for (const [route, title] of [['dashboard', '账号概览'], ['membership', '会员信息'], ['orders', '订单记录'], ['settings', '账号设置']]) {
    await page.goto(`/portal/${route}`);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.portal-content')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath(`${route}-overview.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 320, height: 740 });
  for (const route of ['dashboard', 'membership', 'orders', 'settings']) {
    await page.goto(`/portal/${route}`); await page.waitForLoadState('networkidle');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.getByRole('button', { name: '打开导航菜单', exact: true }).click();
  await page.getByRole('menuitem', { name: /控制台$/ }).click();
  await expect(page).toHaveURL(/\/portal\/dashboard$/);
});

test('dashboard shows real activity and keeps its account and purchase links', async ({ page }) => {
  await setup(page); await page.goto('/portal/dashboard');
  await expect(page.locator('.dashboard-metric__value').first()).toHaveText('128');
  await expect(page.locator('.dashboard-last-login__ip')).toHaveText('203.0.113.28');
  await expect(page.locator('.dashboard-history').getByText('203.0.113.28', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: '查看会员信息', exact: true }).click();
  await expect(page.getByText('会员有效', { exact: true })).toBeVisible();
  await page.goto('/portal/dashboard');
  await page.getByRole('button', { name: /充值续费$/ }).click();
  await expect(page).toHaveURL(/\/portal\/recharge$/);
});

test('failed dashboard data is not presented as zero usage and can be retried', async ({ page }) => {
  await setup(page);
  let attempts = 0;
  await page.route('**/api/portal/dashboard/stats', async route => {
    attempts++;
    await route.fulfill(attempts === 1 ? { status: 503, json: { message: 'unavailable' } } : { json: ok(stats) });
  });
  await page.goto('/portal/dashboard');
  await expect(page.getByText('账号概览加载失败', { exact: true })).toBeVisible();
  await expect(page.locator('.dashboard-metric__value').first()).toHaveText('—');
  await page.getByRole('button', { name: '重新加载', exact: true }).click();
  await expect(page.locator('.dashboard-metric__value').first()).toHaveText('128');
  await expect(page.getByText('账号概览加载失败', { exact: true })).not.toBeVisible();
});

test('membership failure does not claim the user has no membership', async ({ page }) => {
  await setup(page);
  await page.route('**/api/portal/membership/info', route => route.fulfill({ status: 503, json: { message: 'unavailable' } }));
  await page.goto('/portal/membership');
  await expect(page.getByText('会员信息加载失败', { exact: true })).toBeVisible();
  await expect(page.getByText('未开通', { exact: true })).not.toBeVisible();
});

test('settings keep nickname saving and password change session invalidation', async ({ page }) => {
  await setup(page);
  let nickname: unknown;
  let password: unknown;
  await page.route('**/api/portal/settings/nickname', async route => { nickname = route.request().postDataJSON(); await route.fulfill({ json: ok(null) }); });
  await page.route('**/api/portal/settings/password', async route => { password = route.request().postDataJSON(); await route.fulfill({ json: ok(null) }); });
  await page.goto('/portal/settings');
  await expect(page.getByLabel('昵称', { exact: true })).toHaveValue('测试昵称');
  await page.getByLabel('昵称', { exact: true }).fill('新昵称');
  await page.getByRole('button', { name: '保存昵称', exact: true }).click();
  await expect.poll(() => nickname).toEqual({ nickname: '新昵称' });
  await expect(page.getByText('昵称更新成功', { exact: true })).toBeVisible();
  await page.getByLabel('当前密码', { exact: true }).first().fill('old-password');
  await page.getByLabel('新密码', { exact: true }).fill('new-password');
  await page.getByLabel('确认新密码', { exact: true }).fill('new-password');
  await page.getByRole('button', { name: /修改密码$/ }).click();
  await expect.poll(() => password).toEqual({ oldPassword: 'old-password', newPassword: 'new-password' });
  await expect(page).toHaveURL(/\/portal\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('portal_token'))).toBeNull();
});
