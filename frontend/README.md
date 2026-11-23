# CipherGate 前端

管理后台、应用用户门户与公开自助页面共用 Ant Design Pro 工程基底，使用 React、TypeScript、Umi Max、Pro Components 与 Ant Design。

## 开发与构建

使用仓库 `.node-version` 指定的 Node.js 版本，在当前目录执行：

```bash
npm ci
npm run dev
```

开发服务位于 `http://localhost:5173`。`/api` 默认代理到 `http://localhost:8080`，可通过 `CIPHERGATE_API_TARGET` 调整；`/docs` 代理到本地文档服务 `http://localhost:5174`。

```bash
npm run check
npm run build
npx playwright install chromium
npm run test:e2e
```

`npm ci` 自动生成 Umi 运行时文件。构建输出仍为 `dist/`，由既有打包、Nginx、安装和更新脚本使用；无需调整后端接口、数据库或生产端口。`npm run preview -- --host 127.0.0.1 --port 5187` 可预览生产构建。完整测试说明见 [TESTING.md](TESTING.md)。

## 工程结构

| 路径 | 职责 |
| --- | --- |
| `config/config.ts` | Umi 构建、代理、插件、公开构建变量 |
| `config/routes.ts` | 后台、公开页面与门户的完整路由 |
| `config/defaultSettings.ts` | ProLayout 布局与间距设置 |
| `src/app.tsx` | 管理员初始数据、运行时布局、全局 Provider |
| `src/access.ts` | 基于当前用户的前端访问能力 |
| `src/navigation/` | 服务端菜单转换、旧菜单路径映射与认证作用域 |
| `src/layouts/` | 后台业务上下文及公开页面容器 |
| `src/portal/` | 独立 Token 会话、门户 ProLayout 与业务页面 |
| `src/services/`、`src/utils/request.ts` | 业务接口契约、会话/CSRF 与错误处理 |
| `src/global.*`、`src/theme/` | 全局初始化、样式与持久化主题 |
| `e2e/`、`tests/` | 浏览器回归与轻量单元测试 |

## 页面与权限

- 路由由 Umi 生成；页面使用 `@umijs/max` 的 `Link`、路由 Hook 和 `Outlet`。公开 URL 与查询参数保持稳定。
- 后台导航来自当前用户接口返回的权限菜单，支持嵌套菜单及动态对接文档。前端访问能力辅助界面控制，业务权限最终由后端验证。
- 管理员初始数据只在后台作用域加载。注册、卡密、自助页面与应用用户门户不请求管理员身份；门户保留自己的 Token、选应用及退出逻辑。
- 后台与门户共享 ProLayout 设计规范；各自的共享布局提供 PageContainer，已有业务组件继续负责表格、表单与操作流程。
- 消息通知、公告、个人资料、API 文档入口和主题切换保留。主题选择继续存储在 `ciphergate_theme`，新用户默认经典蓝。

## 配置与部署

`CIPHERGATE_DOCS_URL` 可在构建时指定使用文档地址，默认 `/docs/`；已有 `VITE_DOCS_URL` 构建变量继续作为后备输入。两者均会进入浏览器产物，只填写公开地址。

生产环境沿用同源 `/api`、OAuth 及 `/docs/` 代理，前端产物更新后即可使用；本次工程迁移无需数据库迁移。回退时使用完整的上一版 `dist/`，避免混用不同版本的 HTML 和分包资源。

采用已正式发布的 Pro Components 与 Ant Design 5 配套版本。依赖由锁文件固定，Umi 可选插件的 peer 兼容策略及安全修复说明见 [TESTING.md](TESTING.md)。
