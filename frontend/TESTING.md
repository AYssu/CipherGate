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

## 类型契约与零豁免

`eslint-legacy-any.json` 当前为 `{}`。历史显式 `any` 已清理，所有 ESLint 规则保持启用，没有类型豁免。`npm run lint` 与 `npm run lint:raw` 都必须通过；不得扩大基线、关闭规则或添加忽略注释来容纳新问题。

- 服务层按后端DTO声明输入和返回值，HTTP业务结果统一为 `ApiResponse<T>`。由于拦截器返回业务信封，Axios调用使用第二个返回值泛型，例如 `request.get<unknown, ApiResponse<User>>('/user/info')`；不要把 `AxiosResponse` 的嵌套 `data` 当成后端响应。
- 终端用户门户契约位于 `src/portal/services/types.ts`；平台会员、订单、工单和JSON配置契约位于 `src/services/contracts.ts`，已有业务服务继续导出自己的DTO。
- 动态JSON使用递归 `JsonValue` / `JsonObject`，读取未知字段前检查实际类型。异常使用 `unknown` 并通过 `getErrorMessage` 或显式类型保护处理；不得通过双重断言伪造对象结构。
- 表单值与接口DTO存在差异时分别建模，例如开关组件使用布尔值，提交时转换为后端整数状态。更新接口确实允许部分字段时使用 `Partial<DTO>`，不能把必需字段随意改成可选以绕过检查。
- 浏览器回归覆盖公开页面、邮箱验证后查询关联应用、卡密动作状态、临时票据换正式会话及首页登录。修改认证或响应契约时，同时验证桌面和移动端流程。

## 文档依赖

`docs/package.json` 暂时将稳定版 VitePress 1.6 的 Vite 依赖覆盖到 `^6.4.4`，避免旧 Vite/esbuild 的审计告警，同时保留现有文档结构。更新文档依赖时必须验证 `npm ci`、`npm audit`、`npm run build`；未来 VitePress 稳定版原生支持安全的 Vite 后，可删除覆盖配置。

API 模拟只证明前端交互和请求契约。权限、数据库事务及实际存储访问由后端测试验证。
