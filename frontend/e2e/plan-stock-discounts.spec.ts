import { test, expect, type Page } from '@playwright/test';
const ok = (data: unknown) => ({ code: 200, success: true, data });
async function setup(page: Page, initiallySoldOut = false) {
  let remaining = initiallySoldOut ? 0 : 3;
  let orders = 0;
  await page.addInitScript(() => localStorage.setItem('portal_token', 'stock-test-session'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/membership/plans')) {
      await route.fulfill({ json: ok([
        { id: 6, planName: 'v3至尊会员', planType: 'MEMBER', durationDays: 36000, priceFen: 89900, stockRemaining: remaining },
        { id: 4, planName: 'v3月度会员', planType: 'MEMBER', durationDays: 30, priceFen: 2500, originalPriceFen: 4000, stockRemaining: null },
        { id: 5, planName: 'v3体验会员', planType: 'MEMBER', durationDays: 7, priceFen: 1500, originalPriceFen: 2500 },
      ]) }); return;
    }
    if (url.pathname.endsWith('/payment/create')) {
      orders++; remaining = Math.max(0, remaining - 1);
      expect(route.request().postDataJSON()).toEqual({ planId: 6 });
      await route.fulfill({ json: ok({ orderNo: 'STOCK-ORDER', planName: 'v3至尊会员', amount: 89900, payUrl: '' }) }); return;
    }
    if (url.pathname.endsWith('/settings/profile')) { await route.fulfill({ json: ok({ username: '测试用户', email: 'test@example.test' }) }); return; }
    await route.fulfill({ json: ok([]) });
  });
  return () => orders;
}

test('prices, discount labels and limited stock are readable on desktop and mobile', async ({ page }, info) => {
  const orders = await setup(page);
  await page.goto('/portal/recharge'); await page.waitForLoadState('networkidle');
  await expect(page.locator('.recharge-plan__current').filter({ hasText: '899.00' })).toBeVisible();
  await expect(page.locator('.recharge-plan__reference').filter({ hasText: '¥40.00' })).toBeVisible();
  await expect(page.locator('.recharge-plan__reference').filter({ hasText: '¥25.00' })).toBeVisible();
  await expect(page.locator('.recharge-plan__current').filter({ hasText: '25.00' })).toBeVisible();
  await expect(page.locator('.recharge-plan__current').filter({ hasText: '15.00' })).toBeVisible();
  await expect(page.getByText('优惠价', { exact: true })).toHaveCount(2);
  await expect(page.locator('.recharge-plan__stock').filter({ hasText: '3' })).toBeVisible();
  await expect(page.getByText('省 ¥15', { exact: true })).toBeVisible();
  await expect(page.getByText('省 ¥10', { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath('pricing-overview.png'), fullPage: true });
  await page.getByRole('button', { name: /购\s*买/, exact: true }).first().click();
  const dialog = page.getByRole('dialog', { name: '确认支付', exact: true });
  await expect(dialog.getByText('¥899.00', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: '稍后支付', exact: true }).click();
  await expect(page.locator('.recharge-plan__stock').filter({ hasText: '2' })).toBeVisible();
  expect(orders()).toBe(1);
  await expect(dialog).not.toBeVisible();
  await page.setViewportSize({ width: 320, height: 740 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('plan-discounts-mobile.png'), fullPage: true });
});

test('sold-out plans cannot be bought while unlimited plans remain available', async ({ page }) => {
  const orders = await setup(page, true);
  await page.goto('/portal/recharge');
  await expect(page.getByRole('button', { name: '已售罄', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: /购\s*买/, exact: true })).toHaveCount(2);
  await expect(page.getByRole('button', { name: /购\s*买/, exact: true }).first()).toBeEnabled();
  expect(orders()).toBe(0);
});

test('a competing buyer can sell the final unit and the rejected buyer sees refreshed stock', async ({ page }) => {
  await setup(page);
  let soldOut = false;
  await page.route('**/api/portal/membership/plans', route => route.fulfill({ json: ok([
    { id: 6, planName: 'v3至尊会员', planType: 'MEMBER', durationDays: 36000, priceFen: 89900, stockRemaining: soldOut ? 0 : 1 },
  ]) }));
  await page.route('**/api/portal/payment/create', async route => {
    soldOut = true; await route.fulfill({ json: { code: 400, success: false, message: '该方案已售罄，请选择其他方案' } });
  });
  await page.goto('/portal/recharge');
  await page.getByRole('button', { name: /购\s*买/, exact: true }).click();
  await expect(page.getByRole('button', { name: '已售罄', exact: true })).toBeDisabled();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('application owner edits original price and stock using yuan without changing cents incorrectly', async ({ page }) => {
  let saved: Record<string, unknown> | undefined;
  const plan = { id: 6, planName: 'v3至尊会员', planType: 'MEMBER', durationDays: 36000,
    priceFen: 89900, stockRemaining: 3, revision: 0, sortOrder: 0, enabled: true };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === '/api/csrf') { await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }); return; }
    if (path === '/api/user/info') data = { id: 7, name: 'Owner', login: 'owner', roles: [{ roleCode: 'SUPER_ADMIN' }], menus: [] };
    if (path.includes('/unread/count')) data = { total: 0, showBadge: false };
    if (path === '/api/applications') data = { records: [{ id: 40, ownerId: 7, appName: 'v3应用', appKey: 'test', status: 1, businessModel: 1 }], total: 1, current: 1, size: 10 };
    if (path === '/api/applications/40/pricing-plans') data = [plan];
    if (path === '/api/applications/40/pricing-plans/6' && route.request().method() === 'PUT') saved = route.request().postDataJSON();
    await route.fulfill({ json: ok(data) });
  });
  await page.goto('/applications/list');
  await page.locator('button').filter({ has: page.locator('[aria-label="more"]') }).first().click();
  await page.getByRole('menuitem', { name: /价格方案/ }).click();
  await page.getByRole('button', { name: /编\s*辑/, exact: true }).last().click();
  await expect(page.getByLabel('现价(元)', { exact: true })).toHaveValue('899.00');
  await page.getByLabel('原价(元)', { exact: true }).fill('999');
  await page.getByLabel('剩余可售(份)', { exact: true }).fill('2');
  await page.getByRole('button', { name: /保\s*存/, exact: true }).click();
  await expect.poll(() => saved).toMatchObject({ priceFen: 89900, originalPriceFen: 99900, stockRemaining: 2, revision: 0 });
});
