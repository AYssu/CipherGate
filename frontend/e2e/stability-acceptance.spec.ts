import { test, expect, type Page, type Route } from '@playwright/test';

const ok = (data: unknown) => ({ success: true, code: 200, data });
const image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6V8AAAAASUVORK5CYII=';
const plans = [1, 2].map(id => ({ id, planName: `会员方案${id}`, planType: 'DAYS', durationDays: 30, priceFen: 1000, sortOrder: id }));
async function mock(page: Page, handler: (route: Route, url: URL) => Promise<boolean>) {
  await page.addInitScript(() => { localStorage.setItem('portal_token', 'acceptance-session'); });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (await handler(route, url)) return;
    if (url.pathname === '/api/csrf') { await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'acceptance-csrf' } }); return; }
    let data: unknown = [];
    if (url.pathname === '/api/portal/settings/profile') data = { username: '测试用户', email: 'user@example.test' };
    if (url.pathname === '/api/public/challenge') data = { captchaId: 'acceptance-captcha', image };
    await route.fulfill({ json: ok(data) });
  });
}

test('plans distinguish failure from empty results and permit a successful retry', async ({ page }) => {
  let attempts = 0;
  await mock(page, async (route, url) => {
    if (url.pathname !== '/api/portal/membership/plans') return false;
    attempts++;
    await route.fulfill(attempts === 1 ? { status: 503, headers: { 'X-Request-ID': 'plans-load-123' }, json: { message: 'internal diagnostic must stay private' } } : { json: ok([]) });
    return true;
  });
  await page.goto('/portal/recharge');
  await expect(page.getByText('购买方案加载失败', { exact: true })).toBeVisible();
  await expect(page.getByText('该应用暂无可购买的方案', { exact: true })).not.toBeVisible();
  await expect(page.getByText(/请求编号：plans-load-123/)).toBeVisible();
  await expect(page.getByText(/internal diagnostic/)).not.toBeVisible();
  await page.getByRole('button', { name: '重新加载', exact: true }).click();
  await expect(page.getByText('该应用暂无可购买的方案', { exact: true })).toBeVisible();
  expect(attempts).toBe(2);
});

test('only one purchase can be pending and postponing payment preserves its order', async ({ page }, info) => {
  let submissions = 0;
  let finish: (() => Promise<void>) | undefined;
  await mock(page, async (route, url) => {
    if (url.pathname === '/api/portal/membership/plans') { await route.fulfill({ json: ok(plans) }); return true; }
    if (url.pathname === '/api/portal/payment/create') {
      submissions++;
      finish = () => route.fulfill({ json: ok({ orderNo: 'ACCEPTANCE-ORDER', planName: '会员方案1', amount: 1000, payUrl: '' }) });
      return true;
    }
    return false;
  });
  await page.goto('/portal/recharge');
  const buy = page.getByRole('button', { name: /购\s*买/, exact: true });
  await buy.first().click();
  await expect.poll(() => Boolean(finish)).toBe(true);
  await expect(buy.first()).toBeDisabled();
  await expect(buy.last()).toBeDisabled();
  await finish!();
  const dialog = page.getByRole('dialog', { name: '确认支付', exact: true });
  await expect(dialog.getByText('ACCEPTANCE-ORDER', { exact: true })).toBeVisible();
  await expect(buy.last()).toBeDisabled();
  await expect(dialog.getByText(/仍会保留待支付订单/)).toBeVisible();
  await dialog.getByRole('button', { name: '稍后支付', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(buy.first()).toBeEnabled();
  expect(submissions).toBe(1);
  await page.setViewportSize({ width: 320, height: 740 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('recharge-mobile.png'), fullPage: true });
});

test('orders paginate without fabricated totals and preserve the last page on failure', async ({ page }, info) => {
  const requests: number[] = [];
  let pageTwoFails = true;
  await mock(page, async (route, url) => {
    if (url.pathname !== '/api/portal/payment/orders') return false;
    const number = Number(url.searchParams.get('page'));
    requests.push(number);
    if (number === 2 && pageTwoFails) {
      pageTwoFails = false;
      await route.fulfill({ status: 503, json: { message: 'unavailable' } }); return true;
    }
    const count = number === 1 ? 20 : 3;
    await route.fulfill({ json: ok(Array.from({ length: count }, (_, i) => ({ id: number * 100 + i,
      orderNo: `PAGE${number}-ORDER${i}`, planName: '月会员', amountFen: 1000, status: 0,
      createdAt: '2026-10-27T12:00:00', paidAt: null }))) });
    return true;
  });
  await page.goto('/portal/orders?trade_status=TRADE_SUCCESS&out_trade_no=FORGED');
  await expect(page.getByText('已返回订单页面', { exact: true })).toBeVisible();
  await expect(page.getByText('支付成功', { exact: true })).not.toBeVisible();
  await expect(page.getByText('PAGE1-ORDER0', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.getByText('订单加载失败', { exact: true })).toBeVisible();
  await expect(page.getByText('PAGE1-ORDER0', { exact: true })).toBeVisible();
  await expect(page.getByText(/当前保留第 1 页的上次加载结果/)).toBeVisible();
  await page.getByRole('button', { name: '重新加载', exact: true }).click();
  await expect(page.getByText('PAGE2-ORDER0', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeEnabled();
  expect(requests).toEqual([1, 2, 2]);
  expect(new URL(page.url()).searchParams.has('trade_status')).toBe(false);
  await page.setViewportSize({ width: 320, height: 740 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('orders-mobile.png'), fullPage: true });
});

test('public registration retains user input after rejection and succeeds on correction', async ({ page }) => {
  const submissions: Record<string, unknown>[] = [];
  await mock(page, async (route, url) => {
    if (url.pathname !== '/api/public/app-user/register/submit') return false;
    submissions.push(route.request().postDataJSON());
    await route.fulfill(submissions.length === 1 ? { status: 400, headers: { 'X-Request-ID': 'register-reject-123' },
      json: { code: 400, success: false, message: '邮箱验证码错误或已过期' } } : { json: ok(null) });
    return true;
  });
  await page.goto('/register?id=12');
  await page.getByLabel('账号', { exact: true }).fill('acceptance_user');
  await page.getByLabel('注册邮箱', { exact: true }).fill('Alice@example.test');
  await page.getByLabel('邮箱验证码', { exact: true }).fill('000000');
  await page.getByLabel('设置密码', { exact: true }).fill('acceptance-password');
  await page.getByLabel('确认密码', { exact: true }).fill('acceptance-password');
  await page.getByRole('button', { name: '创建账户', exact: true }).click();
  await expect(page.getByText(/邮箱验证码错误或已过期（请求编号：register-reject-123）/)).toBeVisible();
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('acceptance_user');
  await expect(page.getByRole('button', { name: /创建账户$/ })).not.toHaveClass(/ant-btn-loading/);
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456');
  await page.getByRole('button', { name: /创建账户$/ }).click();
  await expect(page.getByRole('heading', { name: '注册成功', exact: true })).toBeVisible();
  expect(submissions.map(x => x.emailCode)).toEqual(['000000', '123456']);
  expect(submissions.every(x => x.email === 'alice@example.test')).toBe(true);
});

test('application upload rejection keeps the previous download and a persistent support ID', async ({ page, isMobile }, info) => {
  let finish: (() => Promise<void>) | undefined;
  const app = { id: 1, ownerId: 7, appName: 'FastScan', appKey: 'test', appSecret: 'test', status: 1,
    businessModel: 1, currentVersion: '2.2.9', minVersion: '2.2.0', createdAt: '2026-10-24T11:00:00',
    updateFileStorageKey: 'app-updates/1/01234567-89ab-cdef-0123-456789abcdef_previous.zip' };
  await mock(page, async (route, url) => {
    if (url.pathname === '/api/user/info') {
      await route.fulfill({ json: ok({ id: 7, name: 'Owner', login: 'owner', roles: [{ roleCode: 'SUPER_ADMIN' }], menus: [] }) }); return true;
    }
    if (url.pathname.includes('/unread/count')) { await route.fulfill({ json: ok({ total: 0, showBadge: false }) }); return true; }
    if (url.pathname === '/api/applications') {
      await route.fulfill({ json: ok({ records: [app], total: 1, current: 1, size: 10 }) }); return true;
    }
    if (url.pathname === '/api/applications/1/update-package') {
      finish = () => route.fulfill({ status: 403, headers: { 'X-Request-ID': 'upload-rejected-123' },
        json: { code: 403, success: false, message: '登录状态已变化，请刷新页面后重新上传' } }); return true;
    }
    return false;
  });
  await page.goto('/applications/list');
  if (isMobile) {
    await page.locator('button').filter({ has: page.locator('[aria-label="more"]') }).first().click();
    await page.getByRole('menuitem', { name: /编辑/ }).click();
  } else await page.getByRole('button', { name: /编辑/ }).first().click();
  await expect(page.getByText('previous.zip', { exact: true })).toBeVisible();
  await page.locator('input[type=file]').setInputFiles({ name: 'next.zip', mimeType: 'application/zip', buffer: Buffer.from('candidate package') });
  await expect.poll(() => Boolean(finish)).toBe(true);
  await expect(page.getByRole('button', { name: /^取\s*消$/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /^确\s*定$/ })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByText('next.zip', { exact: true })).toBeVisible();
  await finish!();
  const failure = page.getByRole('alert').filter({ hasText: '更新包上传未完成' });
  await expect(failure).toBeVisible();
  await expect(failure).toContainText('请求编号：upload-rejected-123');
  await expect(failure).toContainText('登录状态已变化');
  await expect(page.getByText('previous.zip', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /下载更新包/ })).toHaveAttribute('href', '/api/applications/1/update-package/download');
  await expect(page.getByRole('button', { name: /替换更新包/ })).toBeEnabled();
  await page.screenshot({ path: info.outputPath('upload-rejection.png'), fullPage: true });
});
