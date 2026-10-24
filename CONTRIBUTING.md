# 参与开发

欢迎通过 Issue 和 Pull Request 参与。提交前先搜索已有问题；较大功能先讨论目标、兼容性和验收方式。安全漏洞请按 [SECURITY.md](SECURITY.md) 私下报告。

## 环境

- JDK 17；使用仓库内 Gradle Wrapper，无需安装全局 Gradle。
- Node.js 22.22.0（见 `.node-version` / `.nvmrc`）；使用 `npm ci` 安装锁定依赖。
- Python 3.12+（部署工具和脚本测试）；Docker 与 Compose v2（开发依赖和真实 MySQL 测试）。
- 本地私有配置按 README 复制示例并填写，不要提交 `.env`、`.env.server`、密钥、数据库备份或构建产物。

```bash
./gradlew unitTest --no-daemon
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
cd ../docs
npm ci
npm run build
cd ..
python -m unittest discover -s scripts/tests
```

开发启动、数据库端口和配置以 [README.md](README.md) 为准。Windows 使用 `gradlew.bat` 和 `deploy-server.bat`。

## 真实数据库测试

单元测试不应依赖开发库。集成测试会清空测试表，必须使用隔离的临时数据库，禁止填写生产连接地址。

在 MySQL 8.0 或 8.4 创建 `ciphergate_migration_test`、`ciphergate_credential_test`、`ciphergate_transfer_test`、`ciphergate_boot_test`，启动本地 Redis。分别设置：

| 测试库 | JDBC 地址环境变量 | 密码环境变量 |
| --- | --- | --- |
| migration | `CIPHERGATE_MIGRATION_JDBC_URL` | `CIPHERGATE_MIGRATION_JDBC_PASSWORD` |
| credential | `CIPHERGATE_CREDENTIAL_JDBC_URL` | `CIPHERGATE_CREDENTIAL_JDBC_PASSWORD` |
| transfer | `CIPHERGATE_TRANSFER_JDBC_URL` | `CIPHERGATE_TRANSFER_JDBC_PASSWORD` |
| boot | `CIPHERGATE_BOOT_JDBC_URL` | `CIPHERGATE_BOOT_JDBC_PASSWORD` |

地址示例：`jdbc:mysql://127.0.0.1:3306/ciphergate_migration_test?allowPublicKeyRetrieval=true&useSSL=false`。`allowPublicKeyRetrieval` 和关闭 TLS 仅用于隔离的本地测试。CI 使用一次性数据库服务，可参考 [.github/workflows/checks.yml](.github/workflows/checks.yml)。

```bash
./gradlew unitTest integrationTest jacocoTestReport --no-daemon
```

`integrationTest` 缺少必需环境变量时应失败，不能以“跳过测试”代替通过。测试结果和覆盖率位于 `build/reports/`。

## 修改要求

- 将问题相关的代码、测试和文档放在同一个 PR，避免混入格式化或无关重构。
- 修复缺陷需补充能重现问题的回归测试；权限、凭证、迁移、充值、文件访问等行为需包含失败分支。
- 数据库变更按 [数据库迁移说明](docs/database/README.md) 新增 Flyway 迁移；不得修改已发布迁移的内容或校验值，不恢复启动时重跑历史 SQL。
- 前端复用项目组件、服务层和权限处理。浏览器测试应验证用户行为，避免把实现细节写成断言。
- lint 中保留的历史欠账受基线约束，不接受为通过检查而扩大忽略范围、放宽规则或增加无理由的禁用注释。
- 不提交真实用户数据、生产日志、访问令牌或截图中的个人信息。示例使用明显的占位值。

## 提交与审查

1. 从最新默认分支创建功能分支，完成修改并运行相关检查。
2. 提交描述清晰的 commit，建议使用 `fix:`、`feat:`、`docs:`、`test:`、`ci:` 等前缀。
3. PR 说明问题、行为变化、验证结果以及数据库或部署影响；界面变化附截图。
4. CI 通过后由维护者审查。Fork PR 使用只读令牌，不依赖仓库密钥；不要改成 `pull_request_target` 执行贡献者代码。

默认分支已配置必需检查 `PR checks`、至少一次审查、解决所有讨论，禁止强推和删除；保留仓库管理员的紧急处理权限。新工作流提交后，PR 将产生该检查。复制或 Fork 仓库时需自行配置这些规则，提交工作流文件不会自动开启分支保护。

## 发布

主分支推送只运行检查。正式版本由维护者创建已合入默认分支的 `vMAJOR.MINOR.PATCH` 标签，或手动选择一个已存在的标签触发 Release 工作流。流水线核验准确提交，运行全部检查及 Linux/Windows 打包后发布，拒绝覆盖已有 Release。

发布前确认版本、数据库兼容性、配置变化和备份方案；发布后按 [.github/release-template.md](.github/release-template.md) 验活。依赖更新由 Dependabot 提 PR，也需通过同样检查。本仓库已开启 Dependabot alerts、依赖安全更新、Secret scanning 和 Push protection；复制仓库时需在 Security 设置检查对应开关。可信默认分支的依赖提交工作流会更新 Gradle 快照，PR 使用只读依赖审查。私有仓库若不支持 Dependency review，需先配置相应安全能力或等效扫描，不应静默跳过。

## 许可证

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，允许许可证规定范围内的非商业使用、修改和分发。商业用途需另行联系 [AYssu](https://github.com/AYssu) 获取授权；具体允许用途以许可证原文为准。

分发时须保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。第三方依赖继续遵循各自许可证。

提交贡献时，请确保你有权提交这些内容，并同意按本项目许可证分发你的贡献；第三方代码须保留其原有许可和声明。
