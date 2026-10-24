# 贡献指南

提交前先搜索已有 Issue 和 Pull Request。较大功能先讨论目标、兼容性和验收方式。安全漏洞按 [安全政策](SECURITY.md) 私下报告。

## 开发准备

| 依赖 | 要求 |
| --- | --- |
| Java | JDK 17，使用仓库 Gradle Wrapper，无需全局 Gradle |
| Node.js | 22.22.0，见 `.node-version` 和 `.nvmrc`，使用 `npm ci` |
| Python | 3.12+，用于部署工具和脚本测试 |
| Docker | Compose v2，用于开发依赖和隔离集成测试 |

开发启动、数据库端口和配置见 [README](README.md)。Windows 使用 `gradlew.bat` 和 `deploy-server.bat`。本地私有配置按示例填写，不提交 `.env`、`.env.server`、密钥、数据库备份或构建产物。

## 本地检查

按变更范围运行检查；提交前确认受影响的检查均通过。以下命令从仓库根目录开始执行：

```bash
# 后端及插件单元测试
./gradlew unitTest --no-daemon

# 前端 lint、类型检查、单元测试和构建
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build

# 浏览器测试，首次运行需安装 Chromium
npx playwright install chromium
npm run test:e2e

# 文档构建
cd ../docs
npm ci
npm run build

# 部署和发布工具测试
cd ..
python3 -m unittest discover -s scripts/tests
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
```

测试应验证行为和失败分支。缺陷修复需补充可重现问题的回归测试；权限、凭证、迁移、充值和文件访问变更需覆盖拒绝或失败场景。浏览器测试验证用户操作，避免将组件实现细节作为断言。

## 集成测试

### 隔离环境

集成测试会清空测试表，必须使用临时数据库，禁止填写生产连接地址。单元测试不依赖开发数据库。

在 MySQL 8.0 或 8.4 创建四个专用数据库，并分别设置环境变量：

| 数据库 | JDBC 地址环境变量 | 密码环境变量 |
| --- | --- | --- |
| `ciphergate_migration_test` | `CIPHERGATE_MIGRATION_JDBC_URL` | `CIPHERGATE_MIGRATION_JDBC_PASSWORD` |
| `ciphergate_credential_test` | `CIPHERGATE_CREDENTIAL_JDBC_URL` | `CIPHERGATE_CREDENTIAL_JDBC_PASSWORD` |
| `ciphergate_transfer_test` | `CIPHERGATE_TRANSFER_JDBC_URL` | `CIPHERGATE_TRANSFER_JDBC_PASSWORD` |
| `ciphergate_boot_test` | `CIPHERGATE_BOOT_JDBC_URL` | `CIPHERGATE_BOOT_JDBC_PASSWORD` |

地址示例：`jdbc:mysql://127.0.0.1:3306/ciphergate_migration_test?allowPublicKeyRetrieval=true&useSSL=false`。关闭 TLS 和允许公钥获取仅用于隔离的本地测试。迁移测试支持通过 `CIPHERGATE_MIGRATION_JDBC_USER` 指定账号；凭证和用户迁移测试使用 `root`，启动测试支持 `CIPHERGATE_BOOT_JDBC_USER`。

全部集成测试还需专用 Redis。按环境设置 `CIPHERGATE_TEST_REDIS_HOST`、`CIPHERGATE_TEST_REDIS_PORT` 和 `CIPHERGATE_TEST_REDIS_PASSWORD`；不要连接包含业务数据的 Redis。

### 执行与结果

```bash
./gradlew unitTest integrationTest jacocoTestReport --no-daemon
```

`integrationTest` 会检查四组 JDBC URL 和密码，缺少必需配置时失败，不能把跳过测试视为通过。测试结果和覆盖率位于 `build/reports/`。CI 的一次性数据库配置见 [检查工作流](.github/workflows/checks.yml)。

数据库变更遵循 [数据库迁移规范](docs/database/README.md)：新增 Flyway 迁移，不修改已发布脚本或校验值，不恢复启动时重跑历史 SQL 的机制。

## 代码与文档规范

### 代码范围

- 将问题相关的代码、测试和文档放在同一 PR，避免混入无关格式化或重构。
- 前端复用项目组件、服务层和权限处理。
- 历史 lint 问题受现有基线约束，不扩大忽略范围、放宽规则或添加无理由的禁用注释。
- 不提交真实用户数据、生产日志、访问令牌或包含个人信息的截图；示例使用明确的占位值。

### Markdown 与术语

- 每份文档使用一个一级标题，按层级组织章节；命令注明执行目录，代码块注明语言。
- 操作说明包含前置条件、执行命令和预期结果；涉及数据修改时说明备份、兼容性及失败处理。
- 使用简洁、中性的表述，避免 emoji、重复分隔线和夸大营销用语；信息不重复维护在多份文档中。
- “平台”指 CipherGate 管理侧，“Portal”指应用用户门户，不将两种账号和权限混称。
- “平台更新”指 CipherGate 发布包更新，“应用更新包”指上传到平台供客户端分发的软件文件。
- 修改配置、命令或流程时，同步检查 README、相关专题文档及发布包内独立运维说明。

具体版本约束、暂缓升级原因及验收要求见 [依赖维护与兼容性](docs/dependency-upgrades.md)。

## Pull Request

1. 从最新默认分支创建功能分支，完成变更及相关检查。
2. 使用清晰的提交说明，建议采用 `fix:`、`feat:`、`docs:`、`test:`、`ci:` 等前缀。
3. PR 描述说明问题、行为变化、验证结果和数据库或部署影响；界面变化附截图。
4. CI 通过后由维护者审查，处理审查意见并解决讨论。

Fork PR 使用只读令牌，不依赖仓库密钥；不得使用 `pull_request_target` 执行贡献者代码。默认分支配置了必需检查 `PR checks`、至少一次审查、解决所有讨论，并禁止强推和删除；管理员保留紧急处理权限。复制或 Fork 仓库需自行配置分支保护，工作流文件不会自动启用保护。

## 发布

### 触发条件

主分支推送运行检查，不直接发布。维护者从已合入默认分支的提交创建 `vMAJOR.MINOR.PATCH` 标签，或手动选择已有标签触发 [发布工作流](.github/workflows/build-deploy-bundle.yml)。流水线核验标签和准确提交，拒绝覆盖已有 Release。

### 检查范围

| 环节 | 验证内容 |
| --- | --- |
| 后端 | 单元测试、插件测试、MySQL 8.0/8.4 集成测试及覆盖率报告 |
| 前端 | lint、类型检查、单元测试、浏览器测试、构建 |
| 文档 | VitePress 构建 |
| 部署工具 | Python 测试及 Linux 上真实发布包安装、更新、回退验收 |
| 打包 | Linux 和 Windows 构建及产物校验；真实 Docker 验收仅在 Linux 执行 |
| 安全 | 依赖审查、锁文件审计和密钥扫描 |

发布前确认版本、数据库兼容性、配置变化和备份方案；发布后按 [发布验收模板](.github/release-template.md) 检查服务及关键业务。

Dependabot 提交的依赖更新需通过同样检查。可信默认分支的依赖提交工作流更新 Gradle 快照，PR 使用只读依赖审查。仓库的 Dependabot alerts、依赖安全更新、Secret scanning 和 Push protection 属于 GitHub 设置，复制仓库时应逐项核对；私有仓库不支持 Dependency review 时，需配置相应能力或等效扫描，不能静默跳过。

## 许可证与贡献授权

项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，允许许可证规定范围内的非商业使用、修改和分发。商业用途需联系 [AYssu](https://github.com/AYssu) 获取授权，具体允许用途以许可证原文为准。

分发时保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)，第三方依赖继续遵循各自许可证。提交贡献前，请确认有权提交相关内容，并同意按项目许可证分发贡献；第三方代码保留其原有许可和声明。
