import { test, expect, type Page, type Route } from '@playwright/test';
const envelope = (data: unknown) => ({ success: true, code: 200, data });
async function mockApi(page: Page, handler: (route: Route, path: string) => Promise<boolean>) {
  await page.context().route('**/api/**', async route => {
    if (new URL(route.request().url()).pathname === '/api/csrf') {
      await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-session-csrf-token' } }); return;
    }
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    if (await handler(route, path)) return;
    let data: unknown = [];
    if (path === '/user/info') data = { id: 7, name: 'Owner', login: 'owner', roles: [{ roleCode: 'SUPER_ADMIN' }], menus: [] };
    if (path.includes('/unread/count')) data = { total: 0, showBadge: false };
    if (path === '/portal/settings/profile') data = { username: 'Portal user', email: 'user@example.com' };
    if (path === '/portal/dashboard/stats') data = {};
    if (path === '/public/challenge') data = { captchaId: 'challenge', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6V8AAAAASUVORK5CYII=' };
    await route.fulfill({ json: envelope(data) });
  });
}
for (const valid of [true, false]) test(`portal login ${valid ? 'persists session and redirects' : 'rejects invalid credentials and refreshes captcha'}`, async ({ page }) => {
  let captchas = 0;
  let payload: unknown;
  await mockApi(page, async (route, path) => {
    if (path === '/portal/auth/captcha') { captchas++; await route.fulfill({ json: envelope({ captchaId: 'captcha-1', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6V8AAAAASUVORK5CYII=' }) }); return true; }
    if (path === '/portal/auth/login') {
      payload = route.request().postDataJSON();
      await route.fulfill(valid ? { json: envelope({ token: 'mock-session', apps: [{ appId: 1 }] }) } : { status: 401, json: { success: false, message: '登录失败' } }); return true;
    }
    return false;
  });
  await page.goto('/portal/login');
  await page.getByPlaceholder('请输入注册邮箱').fill('user@example.com');
  await page.getByPlaceholder('请输入密码', { exact: true }).fill('test-password');
  await page.getByPlaceholder('验证码', { exact: true }).fill('1234');
  await page.getByRole('button', { name: /登\s*录/, exact: true }).click();
  await expect.poll(() => payload).toEqual({ email: 'user@example.com', password: 'test-password', captchaCode: '1234', captchaId: 'captcha-1' });
  if (valid) await expect(page).toHaveURL(/\/portal\/dashboard$/);
  else { await expect.poll(() => captchas).toBeGreaterThanOrEqual(2); await expect(page).toHaveURL(/\/portal\/login$/); }
  expect(await page.evaluate(() => localStorage.getItem('portal_token'))).toBe(valid ? 'mock-session' : null);
});

test('package upload replaces filename and download uses authenticated application endpoint', async ({ page, isMobile, request }, testInfo) => {
  const app = { id: 1, ownerId: 7, appName: 'FastScan', appKey: 'test', appSecret: 'test', status: 1, businessModel: 1,
    currentVersion: '2.2.9', minVersion: '2.2.0', createdAt: '2026-10-24T11:00:00',
    updateFileStorageKey: 'app-updates/1/01234567-89ab-cdef-0123-456789abcdef_release.zip' };
  let upload = '';
  const session = `mock-owner-${testInfo.project.name}-${Date.now()}`;
  await page.context().addCookies([{ name: 'JSESSIONID', value: session, url: 'http://127.0.0.1:5187' }]);
  await mockApi(page, async (route, path) => {
    if (path === '/applications') { await route.fulfill({ json: envelope({ records: [app], total: 1, current: 1, size: 10 }) }); return true; }
    if (path === '/applications/1/update-package') {
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['content-type']).toContain('multipart/form-data');
      upload = route.request().postDataBuffer()?.toString() ?? '';
      app.updateFileStorageKey = 'app-updates/1/01234567-89ab-cdef-0123-456789abcdef_next.zip';
      await route.fulfill({ json: envelope(app) }); return true;
    }
    if (path === '/applications/1/update-package/download') { await route.continue(); return true; }
    return false;
  });
  await page.goto('/applications/list');
  if (isMobile) { await page.locator('button').filter({ has: page.locator('[aria-label="more"]') }).first().click(); await page.getByRole('menuitem', { name: /编辑/ }).click(); }
  else await page.getByRole('button', { name: /编辑/ }).first().click();
  await expect(page.getByText('release.zip', { exact: true })).toBeVisible();
  await expect(page.getByText(app.updateFileStorageKey, { exact: true })).toHaveCount(0);
  await expect(page.getByText('上传到 MinIO', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /下载更新包/ })).toHaveAttribute('href', '/api/applications/1/update-package/download');
  const promise = page.waitForEvent('download');
  await page.getByRole('link', { name: /下载更新包/ }).click();
  expect((await promise).suggestedFilename()).toBe('release.zip');
  await expect.poll(async () => (await (await request.get(`http://127.0.0.1:5188/downloads/${session}`)).json()).downloaded).toBe(true);
  await page.locator('input[type=file]').setInputFiles({ name: 'next.zip', mimeType: 'application/zip', buffer: Buffer.from('test archive') });
  await expect(page.getByText('next.zip', { exact: true })).toBeVisible();
  expect(upload).toContain('filename="next.zip"'); expect(upload).toContain('test archive');
});

for (const blocked of [false, true]) test(`user transfer ${blocked ? 'blocks conflicts' : 'requires preview token and explicit confirmation'}`, async ({ page }) => {
  const requests: { path: string; body: unknown }[] = [];
  const result = { sourceAppName: 'Source', targetAppName: 'Target', previewToken: 'preview-token', createCount: blocked ? 0 : 1,
    overwriteCount: 0, restoreCount: 0, skipCount: 0, blockCount: blocked ? 1 : 0, executed: false,
    items: [{ sourceUserId: 11, username: 'alice', action: blocked ? 'BLOCK' : 'CREATE', reason: blocked ? '身份冲突' : '创建目标账号', deviceCount: 0, bannedDeviceCount: 0, trialRecorded: false }] };
  await mockApi(page, async (route, path) => {
    if (path === '/app-user-transfers/applications') { await route.fulfill({ json: envelope({ records: [{ id: 1, appName: 'Source', ownerId: 7 }, { id: 2, appName: 'Target', ownerId: 7 }], total: 2 }) }); return true; }
    if (path === '/app-user-transfers/users') { await route.fulfill({ json: envelope({ records: [{ id: 11, username: 'alice', email: 'alice@example.com' }], total: 1 }) }); return true; }
    if (path.endsWith('/preview') || path.endsWith('/execute')) { requests.push({ path, body: route.request().postDataJSON() }); await route.fulfill({ json: envelope({ ...result, executed: path.endsWith('/execute') }) }); return true; }
    return false;
  });
  await page.goto('/applications/user-transfer');
  await expect(page.getByRole('button', { name: /预检查复制/ })).toBeDisabled();
  await page.getByRole('combobox', { name: '源应用' }).click(); await page.getByTitle('Source (#1)', { exact: true }).click();
  await page.getByRole('combobox', { name: '目标应用' }).click();
  await page.getByRole('combobox', { name: '目标应用' }).press('ArrowDown');
  await page.getByRole('combobox', { name: '目标应用' }).press('Enter');
  await expect(page.getByText('alice', { exact: true })).toBeVisible();
  await page.getByRole('checkbox').last().check();
  await page.getByRole('button', { name: /预检查复制/ }).click();
  const dialog = page.getByRole('dialog'); await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: '确认复制' })).toBeDisabled();
  expect(requests).toEqual([{ path: '/app-user-transfers/preview', body: { sourceAppId: 1, targetAppId: 2, mode: 'COPY', forceOverwrite: false, userIds: [11] } }]);
  if (blocked) { await expect(dialog.getByRole('checkbox')).toBeDisabled(); await expect(dialog.getByText('有不可处理用户，本批次不会执行')).toBeVisible(); }
  else {
    await dialog.getByRole('checkbox').check(); await dialog.getByRole('button', { name: '确认复制' }).click();
    await expect(dialog.getByText('已完成，账号使用目标应用的登录配置；请重新登录验证')).toBeVisible();
    expect(requests[1]).toEqual({ path: '/app-user-transfers/execute', body: { sourceAppId: 1, targetAppId: 2, mode: 'COPY', forceOverwrite: false, userIds: [11], previewToken: 'preview-token' } });
    expect(requests).toHaveLength(2);
  }
});


test('first-run OAuth defaults use the visited site origin and submit through the API proxy', async ({ page }) => {
  let submitted: unknown;
  await mockApi(page, async (route, path) => {
    if (path === '/config/init/status') { await route.fulfill({ json: envelope({ initialized: false, allowInit: true }) }); return true; }
    if (path === '/config/init') { submitted = route.request().postDataJSON(); await route.fulfill({ json: envelope(null) }); return true; }
    return false;
  });
  await page.goto('/');
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const origin = new URL(page.url()).origin;
  await expect(dialog.locator('#redirectUri')).toHaveValue(`${origin}/api/login/oauth2/code/github`);
  await expect(dialog.locator('#frontendUrl')).toHaveValue(`${origin}/dashboard`);
  await dialog.locator('#clientId').fill('my-oauth-client');
  await dialog.locator('#clientSecret').fill('my-oauth-secret');
  await dialog.getByRole('button', { name: '完成初始化' }).click();
  await expect.poll(() => submitted).toEqual({ clientId: 'my-oauth-client', clientSecret: 'my-oauth-secret',
    redirectUri: `${origin}/api/login/oauth2/code/github`, frontendUrl: `${origin}/dashboard` });
  await expect(dialog).not.toBeVisible();
});


test('application registration validates fields and links to the shared login flow', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await mockApi(page, async (route, path) => {
    if (path === '/portal/auth/captcha') { await route.fulfill({ json: envelope({ captchaId: 'registration-captcha', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6V8AAAAASUVORK5CYII=' }) }); return true; }
    return false;
  });
  await page.goto('/register?id=1');
  await expect(page.getByRole('heading', { name: '创建应用账户', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '发送验证码', exact: true }).click();
  await expect(page.getByText('请先填写账号与有效邮箱', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: '已有账号？登录', exact: true }).click();
  await expect(page).toHaveURL(/\/portal\/login$/);
  await expect(page.getByRole('heading', { name: '应用用户登录', exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('验证码', { exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

for (const succeeds of [true, false]) test(`license redemption ${succeeds ? 'confirms beneficiary and refreshes membership' : 'keeps card available after rejection'}`, async ({ page }) => {
  const submitted: unknown[] = [];
  await mockApi(page, async (route, path) => {
    if (path === '/public/app-user/self/query-expire') {
      await route.fulfill({ json: envelope({ emailMasked: 'al***@example.test', memberActive: false, remainingSeconds: 0 }) }); return true;
    }
    if (path === '/public/app-user/self/redeem-license') {
      submitted.push(route.request().postDataJSON());
      await route.fulfill({ json: succeeds ? envelope({ emailMasked: 'al***@example.test', keyCodeMasked: 'ABCD****WXYZ',
        memberExpiresAt: '2035-01-08T12:00:00', remainingSeconds: 604800, memberActive: true }) : { success: false, code: 400, message: '卡密已兑换，请勿重复充值' } }); return true;
    }
    return false;
  });
  await page.goto('/app-user?id=10');
  await page.getByLabel('邮箱', { exact: true }).fill('alice@example.test');
  await page.getByLabel('账号密码', { exact: true }).fill('test-password');
  await page.getByLabel('图形验证码', { exact: true }).fill('12345');
  await page.getByRole('button', { name: /查\s*询/ }).click();
  await expect(page.getByText('未开通会员', { exact: true })).toBeVisible();
  await page.getByLabel('充值邮箱', { exact: true }).fill('Alice@example.test');
  await page.getByLabel('充值卡密', { exact: true }).fill('ABCD1234WXYZ');
  await page.getByRole('button', { name: /兑换充值/ }).click();
  let dialog=page.getByRole('dialog', { name: '确认卡密充值' });
  await expect(dialog.getByText('alice@example.test', { exact: true })).toBeVisible();
  expect(submitted).toHaveLength(0);
  await dialog.getByRole('button', { name: /取\s*消/ }).click();
  expect(submitted).toHaveLength(0);
  await page.getByRole('button', { name: /兑换充值/ }).click();
  dialog=page.getByRole('dialog', { name: '确认卡密充值' });
  await dialog.getByRole('button', { name: '确认充值', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(submitted).toEqual([{ appId: 10, email: 'alice@example.test', keyCode: 'ABCD1234WXYZ' }]);
  if (succeeds) {
    await expect(page.getByText('卡密充值成功', { exact: true })).toBeVisible();
    await expect(page.getByLabel('充值卡密', { exact: true })).toHaveValue('');
    await expect(page.getByText('未开通会员', { exact: true })).not.toBeVisible();
    await expect(page.getByText('有效', { exact: true })).toBeVisible();
  } else {
    await expect(page.getByText('卡密充值失败', { exact: true })).toBeVisible();
    await expect(page.getByLabel('充值卡密', { exact: true })).toHaveValue('ABCD1234WXYZ');
    await expect(page.getByText('未开通会员', { exact: true })).toBeVisible();
  }
});

test('license redemption cannot submit without a valid application link', async ({ page }) => {
  await mockApi(page, async () => false);
  await page.goto('/app-user');
  await expect(page.getByRole('button', { name: /兑换充值/ })).toBeDisabled();
  await expect(page.getByLabel('充值邮箱', { exact: true })).toBeDisabled();
});
