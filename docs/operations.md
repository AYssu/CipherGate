# 部署、升级与故障恢复

运行环境：Docker、Compose v2、Python 3.12+。源码打包另需 JDK 17 和 Node.js 22.22.0；安装发布包不需要安装 Java、Node 或 Gradle。Linux 与 Windows 的打包入口共用 `scripts/project.py`。

## 首次安装

先安装并启动 Docker（含 Compose v2）以及 Python 3.12+。从 Releases 下载完整 `ciphergate-deploy.zip` 和 `SHA256SUMS`，对照发布页校验完整包的 SHA256，再解压到固定目录。Windows 使用 `py -3` 代替 `python3`；Docker Desktop 使用 Linux containers。

在解压目录运行：

```bash
# 本机体验：自动生成独立密码、HTTP Cookie 设置，启动全部六个服务
python3 install.py

# 正式域名：首次安装时改用这一条（提前准备 DNS、HTTPS 证书和反向代理）
python3 install.py --public-url https://auth.example.com
```

两条命令选择其一。脚本校验包内文件清单、检查 Docker 访问权限，为本次安装生成权限 600 的 `.env`，等待数据库及应用健康，然后核对前后端版本。失败返回非零状态，保留配置和数据供排查、重试；不会清空卷。已有 `.env` 永不覆盖，`--public-url` 不会隐式修改已有配置。首次启动需要下载镜像，耗时取决于网络；支持 Docker 镜像平台还取决于各固定镜像的架构。

默认端口全部只监听本机：前端 `http://127.0.0.1:5173`。远程服务器可以先建立 `ssh -L 5173:127.0.0.1:5173 用户@服务器`，再在自己电脑访问该地址。正式上线由同机反向代理把 HTTPS 域名回源到 `127.0.0.1:5173`。脚本不申请证书、不修改 DNS、防火墙或宿主机软件。跨机代理需明确配置绑定地址并限制来源。公开 URL 会同时用于后端和第三方下载地址，HTTPS 自动启用 Secure Cookie；只支持 HTTP 的本机体验配置为 false。

需要自定义端口时，先运行 `python3 setup-env.py`，编辑 `.env` 后 `python3 setup-env.py --check`，再运行安装脚本。注意手工生成器默认 `APP_SESSION_COOKIE_SECURE=true`；仅使用 HTTP 的环境必须显式改成 false，并填写两个公开地址。源码目录入口是 `python3 scripts/install.py --directory deploy-bundle`（先构建发布包）。

服务启动不代表第三方账号已配置。打开首页完成一次初始化：创建**自己的 GitHub OAuth App**，填写 Client ID、Client Secret，回调地址为 `https://你的域名/api/login/oauth2/code/github`，前端跳转地址为 `https://你的域名/dashboard`；本机体验改用对应 HTTP 地址。页面自动按当前域名预填这两个地址。首位 GitHub 登录用户成为超级管理员，部署者应先完成初始化及首登，再开放外部访问。邮件、支付等可选集成仍需填写自己的服务配置。

`.env` 含凭证，独立安全备份，不提交 Git、不随发布包分发。公开模板只有 `.env.example`。首次安装 MySQL 使用模板中的 8.4；后端由 Flyway 初始化空库。MinIO 使用固定 `pgsty/silo` 镜像及独立对象卷。

## 已有服务器升级

将新完整包放在部署目录以外，不要覆盖解压到线上目录。在当前安装目录执行：

```bash
python3 update.py /path/to/new/ciphergate-deploy.zip
```

旧安装没有更新脚本时，把新包解压到**另一目录**，从新包执行：

```bash
python3 /tmp/new-release/update.py /path/to/new/ciphergate-deploy.zip --directory /srv/CipherGate
```

脚本先核对 ZIP 文件清单、校验和及 JAR，再确认运行容器挂载对应的安装目录。它保留原 `.env`、Compose、Nginx 配置、数据卷和当前运行镜像；更新 JAR、前端、文档、官方插件和运维工具，保留其他插件。官方旧插件只有内容仍与旧清单一致时才清理。不会拉取新镜像，也不会重建 MySQL、Redis、RabbitMQ、MinIO。配置/基础设施升级需单独评审，不能直接拿新模板覆盖旧安装。

更新会短暂停止前后端写入，将旧应用、配置快照、运行镜像/环境及数据库导出保存到 `backups/<时间>-update/`，随后重建 backend/frontend 并检查 readiness 和前后端版本。备份可能含密钥，只允许管理员读取，需另存到安全存储。数据库导出不包含 MinIO 对象；对象仍需独立备份。更新日志会输出备份目录。

使用自定义 Compose 项目或多个配置文件时，必须传入**原来的整套启动参数**（包含 base 文件），例如：

```bash
python3 update.py /tmp/ciphergate-deploy.zip --project-name ciphergate \
  --compose-file docker-compose.server.yml --compose-file /srv/runtime.override.json
```

脚本不自动接管外部数据库、非标准挂载或缺失 Flyway 历史的旧安装；这些情况会在替换应用前停止并提示。旧库首次接入必须先按 `DATABASE-UPGRADE.md` 核验并显式接入，不能重放 `init.sql` 或绕过校验。旧版新增 `REDIS_PASSWORD`、`PORTAL_JWT_SECRET` 等配置也应单独完成兼容迁移；脚本不会替你轮换密钥或修改现有 Redis。

**MySQL 大版本不随应用升级。** 新模板是 8.4，既有 8.0 安装保持原镜像和数据卷。数据库大版本升级另做备份、隔离恢复与兼容性核验。

更新失败时：数据库迁移历史未变，会自动恢复旧应用并重启；迁移历史改变或无法核验时，停止应用并保留现场，要求按下文数据库恢复流程处理，不自动恢复 SQL。更新完成后仍需用实际账号验证登录、充值、上传和下载等业务。

手工触发同样的健康检查：

```bash
python3 release_tools.py smoke http://127.0.0.1:5173 --version 版本号
```

## 数据库备份与恢复

在部署目录执行，源码入口对应 `scripts/operations.py`：

```bash
python3 operations.py backup backups/before-upgrade.sql.gz
```

备份包含 schema、数据、存储过程、触发器和事件，压缩文件权限为 600，并生成 `.json` 校验侧文件。密码从运行中 MySQL 容器环境读取，不写进命令参数。两份文件一起保存到独立加密存储。`--single-transaction` 适用于 InnoDB；备份期间不要运行 DDL。此操作不包含 MinIO 文件、Redis 和配置。

恢复会修改整个目标库，必须先停止业务写入，优先在另一个 Compose 项目/全新卷演练。保留原现场，不把生产目录当演练目录：

```bash
docker compose -f docker-compose.server.yml stop backend frontend
python3 operations.py restore backups/before-upgrade.sql.gz --confirm-database ciphergate
```

恢复前校验 SHA256、gzip 完整性和明确目标库名。导入 SQL 非原子，失败后保持应用停止并排查，必要时重建隔离目标后重试。恢复到空库避免旧备份不存在的新表残留；全库备份包含 DROP/CREATE，不可当增量补丁使用。恢复后核对关键表行数、Flyway 历史及业务读取，再启动应用。

MinIO 另行使用 S3 客户端向独立存储同步对象并验证下载；配置、MinIO 数据和 SQL 备份应属于同一恢复时间点。不要只复制正在写入的 MySQL 数据目录，也不要运行 `docker compose down -v`。

## 回滚

数据库尚未迁移时，可直接使用升级日志中的备份目录：

```bash
python3 update.py rollback --backup backups/时间-update
```

脚本使用备份记录的项目及 Compose 参数，核验迁移历史后恢复旧文件、原镜像并重建 backend/frontend。数据库已迁移时，先在隔离环境确认旧代码兼容新 schema；不兼容则使用升级前 SQL、对象和配置一起恢复。Flyway 不自动执行破坏性的向下迁移。涉及充值等业务写入时先停止入口并制定数据对账方案，避免直接恢复备份丢失升级后的业务。

## 日志和真实 IP

```bash
docker compose -f docker-compose.server.yml logs --since=10m --tail=200 backend frontend
docker compose -f docker-compose.server.yml exec -T frontend nginx -T
docker compose -f docker-compose.server.yml exec -T backend wget -qO- http://localhost:8080/actuator/health/readiness
```

prod 禁止 MyBatis SQL/参数/结果行打印，业务日志通过 `X-Request-ID` 关联。请求 ID 限制长度和字符，非法值生成新 UUID。不要公开含 OAuth code、令牌、密码、用户数据的原始日志。

当前前端保留既有信任所有来源 X-Forwarded-For 的配置；**只允许可信 CDN/WAF 回源访问此前端端口**，否则攻击者可伪造 IP。默认回环绑定满足同机雷池回源；跨机器部署时配置可信代理网段并限制端口访问。后端端口也不能让外部绕过入口直接访问。代理链需以可信代理的官方网段/清洗规则为准，不能从某次日志猜测整个运营商网段都是可信代理。
