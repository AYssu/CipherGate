# 前端检查与回归测试

使用仓库 `.node-version` 指定的 Node.js 版本。首次运行：

```sh
npm ci
npm run check
npm run build
npx playwright install chromium
npm run test:e2e
```

Linux CI 安装浏览器时使用 `npx playwright install --with-deps chromium`。
`test:e2e` 检查生产构建，运行前必须先执行 `npm run build`。Playwright 自动启动预览服务（5187）和仅监听本机的下载模拟服务（5188），无需数据库、真实账号或 MinIO；端口须空闲。

- `npm run lint`：所有 ESLint 规则保持启用，禁止新增违规。
- `npm test`：签名状态判断、lint 基线检查器回归测试。
- `npm run typecheck`：检查应用、Vite 配置及浏览器测试的 TypeScript 类型。
- `npm run check`：依次执行 lint、单元测试及类型检查。
- `npm run test:e2e`：桌面与移动端的登录成功/失败、更新包上传及认证下载、用户迁移预检查/确认/冲突阻止。

浏览器失败时，`test-results/` 保留截图、视频和 trace；`playwright-report/` 为 HTML 报告。执行 `npx playwright show-report` 查看。CI 保留这些报告。

## 逐步减少历史类型欠账

项目原有大量 `any`。`eslint-legacy-any.json` 只记录现存 `@typescript-eslint/no-explicit-any`，没有关闭该规则；其他规则不存在豁免。匹配包含文件、源代码行摘要及该行内的出现位置，并限制重复次数，因此新增、复制或修改这些声明都会失败。

基线只应缩减。修复历史 `any` 后，按 lint 给出的键删除相应基线项，不要重新生成基线来容纳新错误。`npm run lint:raw` 展示全部历史欠账，预期仍会报错；PR 检查使用 `npm run lint`。

## 文档依赖

`docs/package.json` 暂时将稳定版 VitePress 1.6 的 Vite 依赖覆盖到 `^6.4.4`，避免旧 Vite/esbuild 的审计告警，同时保留现有文档结构。更新文档依赖时必须验证 `npm ci`、`npm audit`、`npm run build`；未来 VitePress 稳定版原生支持安全的 Vite 后，可删除覆盖配置。

API 模拟只证明前端交互和请求契约。权限、数据库事务及实际存储访问由后端测试验证。
