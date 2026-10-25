/// <reference lib="dom" />
import { test, expect, type Page } from '@playwright/test';
const envelope = (data: unknown) => ({ success: true, code: 200, data });
async function mock(page: Page) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/captcha') || path.endsWith('/challenge')) data = { captchaId: 'captcha', image: '' };
    if (path.endsWith('/recovery/apps')) data = [{ appId: 1, appName: '我的应用' }];
    if (path.endsWith('/query-remaining')) data = { keyCodeMasked: 'ABCD****1234', status: 2, remainingSeconds: 86400, boundDevice: true, boundIp: false, unbindCount: 0, unbindLimit: 3, unbindRemaining: 3 };
    if (path.endsWith('/query-expire')) data = { emailMasked: 'us***@example.com', memberActive: false, remainingSeconds: 0 };
    await route.fulfill({ json: envelope(data) });
  });
}
for (const [path, title] of [
  ['/register?id=1', '创建应用账户'], ['/app-user?id=1', '用户自助管理'], ['/license?id=1', '卡密自助管理'],
  ['/portal/login', '应用用户登录'], ['/portal/recovery', '找回密码'], ['/portal/select-app', '选择应用'],
]) test(`public page ${path} supports narrow screens and keyboard navigation`, async ({ page }, info) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await mock(page);
  await page.goto(path); await page.waitForLoadState('networkidle');
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('page.png'), fullPage: true });
  await page.setViewportSize({ width: 320, height: 740 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'CipherGate', exact: true })).toBeFocused();
  expect(errors).toEqual([]);
});

test('registration sends the exact form values and shows a success receipt', async ({ page }) => {
  await mock(page); let submitted: unknown;
  await page.route('**/api/public/app-user/register/submit', async route => { submitted = route.request().postDataJSON(); await route.fulfill({ json: envelope(null) }); });
  await page.goto('/register?id=12');
  await page.getByLabel('用户名', { exact: true }).fill('alice_1');
  await page.getByLabel('注册邮箱', { exact: true }).fill('Alice@example.com');
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456');
  await page.getByLabel('设置密码', { exact: true }).fill('password-123');
  await page.getByLabel('确认密码', { exact: true }).fill('password-123');
  await page.getByRole('button', { name: '创建账户', exact: true }).click();
  await expect(page.getByRole('heading', { name: '注册成功', exact: true })).toBeVisible();
  expect(submitted).toEqual({ appId: 12, username: 'alice_1', email: 'alice@example.com', emailCode: '123456', password: 'password-123' });
});

test('card query binds its input, masks the secret and invalidates stale actions', async ({ page }) => {
  await mock(page); let payload: unknown;
  await page.route('**/api/public/license/query-remaining', async route => {
    payload = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ keyCodeMasked: 'ABCD****1234', status: 2, remainingSeconds: 86400, boundDevice: true, boundIp: false, unbindCount: 0, unbindLimit: 3, unbindRemaining: 3 }) });
  });
  await page.goto('/license?id=12');
  const unbind = page.getByRole('button', { name: /解绑设备与 IP/ });
  await expect(unbind).toBeDisabled();
  await page.getByLabel('卡密', { exact: true }).fill('abcd1234');
  await expect(page.getByLabel('卡密', { exact: true })).toHaveAttribute('type', 'password');
  await page.getByLabel('查询验证码', { exact: true }).fill('12345');
  await page.getByRole('button', { name: /查\s*询$/ }).click();
  await expect(page.getByText('ABCD****1234', { exact: true })).toBeVisible();
  expect(payload).toEqual({ appId: 12, keyCode: 'ABCD1234', captchaId: 'captcha', captchaCode: '12345' });
  await expect(unbind).toBeEnabled();
  await page.getByLabel('卡密', { exact: true }).fill('changed');
  await expect(unbind).toBeDisabled();
  await expect(page.getByText('ABCD****1234', { exact: true })).not.toBeVisible();
});

test('recovery progresses with labelled fields and submits the selected application', async ({ page }) => {
  await mock(page); let payload: unknown;
  await page.route('**/api/portal/auth/recovery/reset-password', async route => { payload = route.request().postDataJSON(); await route.fulfill({ json: envelope(null) }); });
  await page.goto('/portal/recovery');
  await page.getByLabel('注册邮箱', { exact: true }).fill('user@example.com');
  await page.getByLabel('图形验证码', { exact: true }).fill('1234');
  await page.getByRole('button', { name: '发送验证码', exact: true }).click();
  await expect(page.getByText('验证码已发送至 user@example.com')).toBeVisible();
  await page.getByLabel('邮箱验证码', { exact: true }).fill('123456');
  await page.getByRole('button', { name: '验证邮箱', exact: true }).click();
  await page.getByRole('combobox', { name: '需要重置的应用' }).click();
  await page.getByRole('combobox', { name: '需要重置的应用' }).press('ArrowDown');
  await page.getByRole('combobox', { name: '需要重置的应用' }).press('Enter');
  await page.getByLabel('新密码', { exact: true }).fill('new-password');
  await page.getByRole('button', { name: '重置密码', exact: true }).click();
  await expect(page).toHaveURL(/\/portal\/login$/);
  expect(payload).toEqual({ email: 'user@example.com', verifyCode: '123456', appId: 1, newPassword: 'new-password' });
});

test('damaged application selection storage offers login recovery', async ({ page }) => {
  await mock(page);
  await page.addInitScript(() => localStorage.setItem('portal_apps', '{invalid'));
  await page.goto('/portal/select-app');
  await expect(page.getByText('登录信息已失效', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '请重新登录后选择应用' })).toBeVisible();
});

test('application selection uses the temporary session and blocks duplicate submissions', async ({ page }) => {
  await mock(page);
  await page.addInitScript(() => {
    localStorage.setItem('portal_temp_token', 'temporary-session');
    localStorage.setItem('portal_apps', JSON.stringify([{ appId: 12, appName: '应用甲' }, { appId: 13, appName: '应用乙' }]));
  });
  let finish: (() => Promise<void>) | undefined;
  let attempts = 0;
  await page.route('**/api/portal/auth/select-app?appId=12', async route => {
    attempts++;
    expect(route.request().headers().authorization).toBe('Bearer temporary-session');
    finish = () => route.fulfill({ json: envelope('selected-session') });
  });
  await page.goto('/portal/select-app');
  await page.getByRole('button', { name: '进入应用甲', exact: true }).click();
  await expect(page.getByRole('button', { name: '进入应用乙', exact: true })).toBeDisabled();
  await expect.poll(() => finish !== undefined).toBe(true);
  await finish!();
  await expect(page).toHaveURL(/\/portal\/dashboard$/);
  expect(attempts).toBe(1);
  expect(await page.evaluate(() => localStorage.getItem('portal_token'))).toBe('selected-session');
  expect(await page.evaluate(() => localStorage.getItem('portal_app_id'))).toBe('12');
  expect(await page.evaluate(() => localStorage.getItem('portal_temp_token'))).toBeNull();
});


test('public membership query requires password and an action-scoped challenge', async ({ page }) => {
  await mock(page); let payload: unknown;
  await page.route('**/api/public/app-user/self/query-expire', async route => {
    payload = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ emailMasked: 'us***@example.com', memberActive: false, remainingSeconds: 0 }) });
  });
  await page.goto('/app-user?id=12');
  await page.getByLabel('邮箱', { exact: true }).fill('user@example.com');
  await page.getByRole('button', { name: /查\s*询$/ }).click();
  expect(payload).toBeUndefined();
  await page.getByLabel('账号密码', { exact: true }).fill('password-123');
  await page.getByLabel('图形验证码', { exact: true }).fill('12345');
  await page.getByRole('button', { name: /查\s*询$/ }).click();
  await expect(page.getByText('us***@example.com', { exact: true })).toBeVisible();
  expect(payload).toEqual({ appId: 12, email: 'user@example.com', password: 'password-123', captchaId: 'captcha', captchaCode: '12345' });
});

test('public route does not load management editors or charts', async ({ page }) => {
  const scripts: string[] = [];
  page.on('request', request => { if (request.resourceType() === 'script') scripts.push(new URL(request.url()).pathname); });
  await mock(page); await page.goto('/license?id=12'); await page.waitForLoadState('networkidle');
  expect(scripts.some(path => /(?:DocManagement|Dashboard|echarts|codemirror|md-editor)/i.test(path))).toBe(false);
  expect(scripts.some(path => /LicenseSelfServicePage/.test(path))).toBe(true);
});

test('failed login preserves an existing unrelated portal session', async ({ page }) => {
  await mock(page);
  await page.addInitScript(() => { localStorage.setItem('portal_token', 'existing-session'); localStorage.setItem('portal_app_id', '12'); });
  await page.route('**/api/portal/auth/login', async route => route.fulfill({ status: 401, json: { code: 401, message: '邮箱或密码错误' } }));
  await page.goto('/portal/login');
  await page.getByPlaceholder('邮箱地址').fill('other@example.com');
  await page.getByPlaceholder('密码', { exact: true }).fill('wrong-password');
  await page.getByPlaceholder('验证码', { exact: true }).fill('1234');
  await page.getByRole('button', { name: /登\s*录/, exact: true }).click();
  await expect(page.getByText('邮箱或密码错误', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('portal_token'))).toBe('existing-session');
  expect(await page.evaluate(() => localStorage.getItem('portal_app_id'))).toBe('12');
});

test('home login binds captcha and exchanges a selection ticket for the string session response', async ({ page }) => {
  await mock(page);
  let loginPayload: unknown;
  await page.route('**/api/portal/auth/login', async route => {
    loginPayload = route.request().postDataJSON();
    await route.fulfill({ json: envelope({ token: 'temporary-home-ticket', needSelectApp: true, apps: [{ appId: 1, appName: '测试应用' }, { appId: 2, appName: '其他应用' }] }) });
  });
  await page.route('**/api/portal/auth/select-app?appId=1', async route => {
    expect(route.request().headers().authorization).toBe('Bearer temporary-home-ticket');
    await route.fulfill({ json: envelope('formal-home-session') });
  });
  await page.goto('/');
  await page.getByRole('button', { name: /应用用户登录|用户登录/ }).first().click();
  const dialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: '应用用户登录', exact: true }) });
  await dialog.getByPlaceholder('邮箱地址').fill('user@example.com');
  await dialog.getByPlaceholder('密码', { exact: true }).fill('valid-password');
  await dialog.getByPlaceholder('验证码', { exact: true }).fill('1234');
  await dialog.getByRole('button', { name: /登\s*录/, exact: true }).click();
  await page.getByRole('button', { name: '进入测试应用', exact: true }).click();
  await expect(page).toHaveURL(/\/portal\/dashboard$/);
  expect(loginPayload).toEqual({ email: 'user@example.com', password: 'valid-password', captchaCode: '1234', captchaId: 'captcha' });
  expect(await page.evaluate(() => localStorage.getItem('portal_token'))).toBe('formal-home-session');
  expect(await page.evaluate(() => localStorage.getItem('portal_app_id'))).toBe('1');
});
