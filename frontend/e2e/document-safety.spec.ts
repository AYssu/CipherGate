import { test, expect, type Page } from '@playwright/test';
const envelope = (data: unknown) => ({ success: true, code: 200, data });
async function mockShell(page: Page) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === '/api/user/info') data = { id: 7, name: 'Owner', login: 'owner', roles: [{ roleCode: 'SUPER_ADMIN' }], menus: [] };
    if (path === '/api/config/init/status') data = { initialized: true };
    if (path.endsWith('/unread/count')) data = { total: 0, showBadge: false };
    await route.fulfill({ json: envelope(data) });
  });
}
const document = (id: number, content: string) => ({ id, title: `安全文档 ${id}`, content, categoryId: 1, status: 1, attachments: [] });
test('documentation keeps Markdown but removes active HTML and javascript links', async ({ page }) => {
  await mockShell(page);
  await page.route('**/api/doc/items/7', route => route.fulfill({ json: envelope(document(7,
    '# 正常标题\n\n**正常加粗**\n\n[正常链接](https://example.test/)\n\n<iframe srcdoc="<script>parent.__auditDocExecuted=true</script>"></iframe>\n\n<a href="javascript:window.__auditDocExecuted=true">危险链接</a>\n\n<img src="/invalid-image" onerror="window.__auditDocExecuted=true">')) }));
  await page.goto('/admin/docs/view/7');
  await expect(page.locator('.doc-content h1')).toHaveText('正常标题');
  await expect(page.locator('.doc-content strong')).toHaveText('正常加粗');
  await expect(page.locator('.doc-content a[href="https://example.test/"]')).toBeVisible();
  await expect(page.locator('.doc-content iframe, .doc-content script, .doc-content [onerror], .doc-content a[href^="javascript:"]')).toHaveCount(0);
  expect(await page.evaluate(() => Reflect.get(window, '__auditDocExecuted'))).toBeUndefined();
});
test('late response from previous document cannot overwrite the current selection', async ({ page }) => {
  await mockShell(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let started!: () => void;
  const requested = new Promise<void>(resolve => { started = resolve; });
  await page.route('**/api/doc/items/7', async route => { started(); await pending; await route.fulfill({ json: envelope(document(7, '# 旧文档内容')) }); });
  await page.route('**/api/doc/items/8', route => route.fulfill({ json: envelope(document(8, '# 当前文档内容')) }));
  try {
    await page.goto('/admin/docs/view/7'); await requested;
    await page.evaluate(() => { window.history.pushState({}, '', '/admin/docs/view/8'); window.dispatchEvent(new PopStateEvent('popstate')); });
    await expect(page.locator('.doc-content h1')).toHaveText('当前文档内容');
    const previousResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/doc/items/7');
    release();
    await previousResponse;
    await expect(page.getByRole('heading', { name: '安全文档 8', exact: true })).toBeVisible();
    await expect(page.locator('.doc-content h1')).toHaveText('当前文档内容');
  } finally { release(); }
});
