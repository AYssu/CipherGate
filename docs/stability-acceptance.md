# 稳定性与体验验收

本规范用于判断一个版本是否具备交付条件。检查通过后仍需维护者审查 PR；合并、发布和生产部署分别授权。测试只使用专用账号、临时数据库和临时文件，不在生产环境执行删除、充值或故障注入。

## 验收范围

| 场景 | 必须满足的行为 | 自动化证据 |
| --- | --- | --- |
| 注册与登录 | 图形验证码可刷新；邮箱验证码错误不创建用户，成功后不可复用；密码散列保存；登录拒绝错误验证码和密码 | `StabilityAcceptanceIntegrationTest`、`captcha-recovery.spec.ts`、`public-pages.spec.ts` |
| 第三方试用与授权 | 签名、操作权限、精确 IP 白名单和 nonce 均校验；重复试用和重放不增加权益 | 同一真实 HTTP 集成测试；`third-party-integration.spec.ts` 验证配置页面 |
| 卡密充值 | 同应用未使用时长卡只入账一次，查询到期时间与实际落库一致；其他应用的卡拒绝兑换 | 同一集成流程、`AppUserLicenseRechargeMysqlTest` 及浏览器兑换测试 |
| 更新包下载 | 有权调用者收到正确内容；凭证错误或流量不足不能读取文件或额外扣量 | 真实 HTTP 下载、数据库流量回归，以及管理端下载浏览器测试 |
| 充值方案与订单 | 加载失败与真正空列表分开；提供重试；一次只能创建一笔订单；回跳地址不能冒充支付成功；订单可翻页 | `stability-acceptance.spec.ts` |
| 操作故障 | 响应携带有效编号时展示；超时和断网提示先查询状态，不能直接断言写入失败；上传失败保留旧包入口和排查说明 | `api-response.test.mjs`、上传拒绝浏览器测试 |
| 恢复 | SQL 与对象分别备份，删除源对象后能恢复到空库和新桶；实际下载内容、摘要、长度和数据库引用一致 | `release-smoke.py`，报告 `checks.objectRecovery` |
| 桌面与移动 | 同一操作在桌面和移动 Chromium 通过；320px 页面无整体横向溢出；验证码请求失败和图片损坏均可恢复 | Playwright 双项目报告和截图 |

浏览器测试会模拟 API，验证页面行为；真实 HTTP 集成测试使用 MySQL、Redis 和完整安全链，仅替代邮件发送及测试所需的外部存储适配器。两类证据结合使用，不能用模拟页面测试证明真实数据入账。QQ 测试验证 QQ User-Agent 下的验证码失败恢复，不等于覆盖所有 QQ 内置浏览器内核。

## 执行检查

在仓库根目录执行。集成测试的四组隔离 JDBC 地址和专用 Redis 配置见[贡献指南](https://github.com/AYssu/CipherGate/blob/master/CONTRIBUTING.md#集成测试)。禁止使用生产连接；集成任务缺少环境配置会失败。

```bash
./gradlew unitTest integrationTest jacocoTestReport bootJar --no-daemon
python3 -m unittest discover -s scripts/tests
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
cd frontend
npm run test:e2e
cd ../docs
npm run build
```

前端测试前安装仓库要求的 Node.js 和 Chromium；首次准备方法见贡献指南。后端报告位于 `build/reports/tests/`，浏览器报告位于 `frontend/playwright-report/`。涉及页面操作的修改应查看截图并检查失败重试，而不是只验证构建成功。

## 安装与恢复演练

完整生命周期测试使用固定的测试容器名和默认端口，**仅在一次性、独占的 CI Docker 主机执行**。不要在开发共用 Docker 主机或生产服务器运行。

```bash
./deploy-server.sh
CIPHERGATE_ACCEPTANCE_REPORT="$PWD/acceptance/recovery.json" \
  python3 scripts/release-smoke.py ciphergate-deploy.zip --isolated-ci
```

演练包括首次安装、初始化、重复安装、升级、回退、迁移历史变化时拒绝不安全回退、V1 数据库备份恢复和重新升级，以及对象恢复。对象包含二进制内容、嵌套空格文件名和空文件；对象经 S3 复制到独立主机目录后，删除源数据，再恢复到新的空目标。

成功报告的 `status` 为 `passed`，包含构建提交、各阶段结果、对象大小和 SHA256、源对象删除及恢复下载验证结果。失败报告保留 `stage`、`errorType` 和已完成检查，不写原始进程输出或凭据。没有生成报告或某阶段未通过时，不得按完整验收通过处理。

PR 检查保存 `deployment-acceptance-evidence` 附件；正式发布的 Linux 打包任务另保存 `release-recovery-evidence`。测试截图、日志和报告使用固定测试数据，禁止加入真实用户信息。

演练报告证明该提交的工具和测试数据可以恢复，不证明某份线上备份已经可恢复。实际生产备份应定期在独立环境按同一时间点恢复数据库、私有配置和对象，并核对充值流水及真实业务文件。操作要求见[安装、更新与恢复](./operations#恢复数据库)。

## 维护者人工验收

1. 使用测试应用，从专用注册链接完成一次真实收信、注册和登录；在实际 QQ 内置浏览器和普通浏览器各检查验证码、键盘输入和错误恢复。
2. 配置测试第三方凭证，分别验证允许和拒绝的 IP；核对调用日志的操作、结果和来源地址。
3. 用测试卡充值，核对充值前后时间及重复兑换拒绝结果。真实支付使用支付渠道沙箱或另行授权的小额验证；不能根据支付回跳参数确认到账。
4. 上传测试更新包、下载并比较摘要；模拟接口拒绝时旧包入口仍可用，故障提示包含请求编号。
5. 复核 CI 报告、迁移和回退影响。全部通过后批准 PR；发布和上线按独立流程执行。

## 后续优化依据

性能调整先保留慢请求、构建体积和错误分布证据，再针对具体瓶颈处理。插件进程隔离、对象清理重试、多节点缓存一致性属于独立架构工作，不应混入普通页面修复。新增功能应说明解决的用户问题，并补入上述验收矩阵。
