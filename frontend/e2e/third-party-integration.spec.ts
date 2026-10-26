import { expect, test, type Page } from '@playwright/test';

const envelope = (data: unknown) => ({ success: true, code: 200, data });
const credential = {
  id: 12, appId: 4, appIds: [4, 8], name: '机器人凭证', apiKey: 'robot-key',
  status: 1, allowTrial: false, allowQuery: false, allowedIps: '',
};

async function mockCredentials(page: Page, admin: boolean, privileged = false) {
  const writes: Record<string, unknown>[] = [];
  await page.context().route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    let data: unknown = [];
    if (path === '/user/info') {
      data = { id: 7, name: 'Owner', login: 'owner', roles: [{ roleCode: admin ? 'ADMIN' : 'USER' }], menus: [] };
    }
    if (path.includes('/unread/count')) data = { total: 0, showBadge: false };
    if (path === '/applications') {
      data = { records: [{ id: 4, appName: '应用甲', ownerId: 7 }, { id: 8, appName: '应用乙', ownerId: 7 }], current: 1, size: 10, total: 2 };
    }
    if (path === '/third-party/credentials') {
      data = { records: [{ ...credential, allowTrial: privileged, allowQuery: privileged,
        allowedIps: privileged ? '203.0.113.10' : '' }], current: 1, size: 10, total: 1 };
    }
    if (path === '/third-party/credentials/12' && route.request().method() === 'PUT') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      writes.push(body);
      data = { ...credential, ...body };
    }
    await route.fulfill({ json: envelope(data) });
  });
  await page.goto('/applications/credentials');
  await expect(page.getByText('机器人凭证', { exact: true })).toBeVisible();
  return writes;
}

async function editCredential(page: Page) {
  await page.getByRole('button', { name: '更多操作', exact: true }).click();
  await page.getByRole('menuitem', { name: /编辑$/ }).click();
  await expect(page.getByRole('checkbox', { name: '申请试用', exact: true })).toBeVisible();
}

async function saveCredential(page: Page) {
  await page.getByRole('button', { name: /^(?:确\s*定|OK)$/ }).click();
}

test('administrator grants signed trial and authorization queries while retaining all bound apps', async ({ page }) => {
  const writes = await mockCredentials(page, true);
  await editCredential(page);
  await page.getByRole('checkbox', { name: '申请试用', exact: true }).check();
  await page.getByRole('checkbox', { name: '查询授权', exact: true }).check();
  await page.getByLabel('IP 白名单', { exact: true }).fill('203.0.113.10,2001:db8::10');
  await saveCredential(page);
  await expect.poll(() => writes).toHaveLength(1);
  expect(writes[0]).toMatchObject({
    appIds: [4, 8], name: '机器人凭证', allowTrial: true, allowQuery: true,
    allowedIps: '203.0.113.10,2001:db8::10', status: 1,
  });
  await expect(page.getByText('更新成功', { exact: true })).toBeVisible();
});

test('trial and authorization permissions require an IP whitelist before saving', async ({ page }) => {
  const writes = await mockCredentials(page, true);
  await editCredential(page);
  await page.getByRole('checkbox', { name: '查询授权', exact: true }).check();
  await saveCredential(page);
  await expect(page.getByText('开启申请试用或查询授权时，必须填写 IP 白名单', { exact: true })).toBeVisible();
  expect(writes).toHaveLength(0);
  await page.getByLabel('IP 白名单', { exact: true }).fill('203.0.113.0/24');
  await saveCredential(page);
  await expect(page.getByText('请填写精确 IP，不支持网段或通配符', { exact: true })).toBeVisible();
  expect(writes).toHaveLength(0);
});

test('application owner cannot modify or rotate a credential with privileged interface permissions', async ({ page }) => {
  const writes = await mockCredentials(page, false, true);
  await expect(page.getByText('申请试用', { exact: true })).toBeVisible();
  await expect(page.getByText('查询授权', { exact: true })).toBeVisible();
  await expect(page.getByText('仅管理员可修改', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '更多操作', exact: true }).click();
  for (const name of ['编辑', '重置密钥', '删除']) {
    await expect(page.getByRole('menuitem', { name: new RegExp(`${name}$`) })).toHaveAttribute('aria-disabled', 'true');
  }
  expect(writes).toHaveLength(0);
});
