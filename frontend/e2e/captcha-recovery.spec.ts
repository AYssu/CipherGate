/// <reference lib="dom" />
import { test, expect } from '@playwright/test';
const image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6V8AAAAASUVORK5CYII=';

test('QQ fresh browser recovers from failed captcha and sends the displayed challenge', async ({ page }) => {
  await page.setExtraHTTPHeaders({ 'User-Agent': 'Mozilla/5.0 Android QQ/9.0.0 MQQBrowser/6.2' });
  let calls = 0;
  let payload: unknown;
  const timestamps: string[] = [];
  await page.route('**/api/**', async route => {
    if (new URL(route.request().url()).pathname === '/api/csrf') {
      await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-session-csrf-token' } }); return;
    }
    const url = new URL(route.request().url());
    if (url.pathname === '/api/public/challenge') {
      calls++;
      timestamps.push(url.searchParams.get('_t') || '');
      if (calls === 1) { await route.fulfill({ status: 401, json: { code: 401, message: '未登录', success: false } }); return; }
      await route.fulfill({ json: { code: 200, success: true, data: { captchaId: `fresh-${calls}`, image } } }); return;
    }
    if (url.pathname.endsWith('/send-email-code')) payload = route.request().postDataJSON();
    await route.fulfill({ json: { code: 200, success: true, data: null } });
  });
  await page.goto('/register?id=12');
  await expect(page.getByRole('status')).toContainText('验证码加载失败');
  await page.getByRole('button', { name: '刷新图形验证码', exact: true }).click();
  const img = page.getByRole('img', { name: '图形验证码图片', exact: true });
  await expect(img).toBeVisible();
  expect(await img.evaluate(el => (el as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  await page.getByLabel('账号', { exact: true }).fill('alice_1');
  await page.getByLabel('注册邮箱', { exact: true }).fill('alice@example.com');
  await page.getByLabel('图形验证码', { exact: true }).fill('12345');
  await page.getByRole('button', { name: '发送验证码', exact: true }).click();
  await expect.poll(() => payload).toEqual({ appId: 12, email: 'alice@example.com', captchaId: 'fresh-2', captchaCode: '12345' });
  await expect(page.getByLabel('图形验证码', { exact: true })).toHaveValue('');
  expect(timestamps.every(Boolean)).toBe(true);
  expect(new Set(timestamps).size).toBe(timestamps.length);
  await expect(page).toHaveURL(/\/register\?id=12$/);
});

test('broken captcha can retry and public input heights stay compact', async ({ page }, info) => {
  let attempts = 0;
  await page.route('**/api/public/challenge?**', async route => {
    attempts++;
    await route.fulfill({ json: { code: 200, success: true, data: { captchaId: `id-${attempts}`, image: attempts === 1 ? 'data:image/png;base64,broken' : image } } });
  });
  await page.goto('/register?id=12');
  await expect(page.getByRole('status')).toContainText('验证码加载失败');
  await page.getByRole('button', { name: '刷新图形验证码', exact: true }).click();
  await expect(page.getByRole('img', { name: '图形验证码图片', exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('请输入账号', { exact: true })).toBeVisible();
  const height = await page.getByLabel('账号', { exact: true }).evaluate(input => input.closest('.ant-input-affix-wrapper')!.getBoundingClientRect().height);
  expect(height).toBeGreaterThanOrEqual(39);
  expect(height).toBeLessThanOrEqual(42);
  await page.setViewportSize({ width: 320, height: 740 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('registration-compact.png'), fullPage: true });
});
