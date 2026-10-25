# 部署与运维

本文适用于完整发布包，包内文件名为 `OPERATIONS.md`。文中的“平台更新”指 CipherGate 自身的后端、前端和插件更新；“应用更新包”指在平台内上传、供客户端分发的软件文件，两者使用不同流程。

## 环境与文件

| 场景 | 依赖 |
| --- | --- |
| 安装完整发布包 | Docker、Compose v2、Python 3.12+ |
| 从源码打包 | 另需 JDK 17、Node.js 22.22.0；使用仓库 Gradle Wrapper |

安装发布包无需在宿主机安装 Java、Node.js 或 Gradle。Linux 与 Windows 使用同一套 Python 部署工具；Windows 命令中的 `python3` 可替换为 `py -3`，Docker Desktop 使用 Linux containers。流水线的真实 Docker 安装、更新和回退验收在 Linux 执行，Windows 执行打包和产物校验。

| 文件 | 用途 |
| --- | --- |
| `.env.example` | 不含真实凭证的公开配置模板 |
| `.env` | 本次安装的私有配置，安装脚本生成，已有文件不覆盖 |
| `install.py` | 校验发布文件、生成配置并首次启动 |
| `update.py` | 平台更新、备份和有条件回退 |
| `operations.py` | 独立数据库备份与恢复 |
| `release_tools.py` | 发布文件校验和服务健康检查 |
| `DATABASE-UPGRADE.md` | 数据库初始化、旧库接入和迁移规则 |

私有配置和备份应独立安全保存，不提交 Git，不随公开发布包分发。

## 首次安装

### 准备发布目录

1. 安装并启动 Docker，确认当前用户可以使用 Compose v2。
2. 从 Releases 下载完整 `ciphergate-deploy.zip` 和 `SHA256SUMS`，核对完整包的 SHA256。
3. 解压到固定安装目录，后续命令均在该目录执行。

### 选择访问方式并启动

本机体验使用 HTTP：

```bash
python3 install.py
```

正式域名使用 HTTPS，提前配置 DNS、证书和反向代理：

```bash
python3 install.py --public-url https://auth.example.com
```

选择其中一条命令。脚本校验文件清单、检查 Docker 访问权限、生成私有 `.env`，等待六个服务健康，再核对前后端版本。首次启动需要下载镜像；镜像是否支持宿主机架构取决于对应的固定镜像。

预期结果是命令成功退出，且前端可访问。失败时返回非零状态，保留配置和数据用于排查、重试，不清空数据卷。已有 `.env` 永不覆盖；再次传入 `--public-url` 也不会修改已有配置。

默认端口只监听本机，前端地址为 `http://127.0.0.1:5173`。远程服务器可先建立隧道：

```bash
ssh -L 5173:127.0.0.1:5173 用户@服务器
```

随后在自己电脑访问该地址。正式部署由同机反向代理将 HTTPS 域名回源到 `127.0.0.1:5173`。脚本不申请证书、不修改 DNS、防火墙或宿主机软件；跨机代理需要显式设置绑定地址并限制来源。

### 自定义配置

需要自定义端口时，先生成配置，编辑 `.env`，再检查和启动：

```bash
python3 setup-env.py
# 编辑 .env
python3 setup-env.py --check
python3 install.py
```

| 配置项 | 要求 |
| --- | --- |
| `APP_BACKEND_PUBLIC_BASE_URL` | 客户端可访问的后端公开地址 |
| `THIRD_PARTY_PUBLIC_BASE_URL` | 第三方下载地址使用的公开地址 |
| `APP_SESSION_COOKIE_SECURE` | HTTPS 为 `true`；仅使用 HTTP 的本机体验为 `false` |
| 各服务绑定地址和端口 | 默认回环地址；对外开放前配置访问限制 |

`install.py --public-url` 在首次生成配置时设置两个公开地址及对应 Cookie 策略。手工运行 `setup-env.py` 默认保留 `APP_SESSION_COOKIE_SECURE=true`；纯 HTTP 环境需自行改为 `false`，并填写两个公开地址。

源码目录的安装入口为 `python3 scripts/install.py --directory deploy-bundle`，需先构建发布包。首次安装使用模板中的 MySQL 8.4，由 Flyway 初始化空库；MinIO 使用固定的 `pgsty/silo` 镜像和独立对象卷。

### 初始化账号

服务健康后还需配置平台账号：

1. 打开首页，完成一次初始化，填写自己创建的 GitHub OAuth App 的 Client ID 和 Client Secret。
2. 设置 OAuth 回调地址为 `https://你的域名/api/login/oauth2/code/github`，前端跳转地址为 `https://你的域名/dashboard`；本机体验使用对应 HTTP 地址。页面按当前域名预填地址。
3. 由部署者先完成 GitHub 首次登录，再开放外部访问。首位 GitHub 登录用户成为超级管理员。
4. 按需配置邮件、支付等可选集成，使用自己的服务凭证。

平台管理账号与 Portal 用户账号分属不同使用场景，平台首次初始化不代表 Portal 用户、第三方凭证或应用业务配置已完成。

## 平台更新

### 更新前检查

- 将新完整包保存在安装目录以外，不覆盖解压到线上目录。
- 确认当前安装目录、Compose 项目名和所有启动配置文件。
- 为短暂停机安排维护窗口，停止其他配置变更及数据库 DDL。
- 独立备份 MinIO 对象。更新脚本的 SQL 导出不包含对象文件。

配置、基础设施镜像和数据库大版本升级应单独评审。新模板使用 MySQL 8.4，已有 MySQL 8.0 安装应保留原镜像和数据卷，不能随应用更新替换模板。

### 执行更新

在当前安装目录运行：

```bash
python3 update.py /path/to/new/ciphergate-deploy.zip
```

旧安装没有脚本时，将新包解压到另一目录，使用新脚本指定原安装目录：

```bash
python3 /tmp/new-release/update.py /path/to/new/ciphergate-deploy.zip --directory /srv/CipherGate
```

自定义项目或多个配置文件必须传入原来整套启动参数，包括基础文件：

```bash
python3 update.py /tmp/ciphergate-deploy.zip --project-name ciphergate \
  --compose-file docker-compose.server.yml --compose-file /srv/runtime.override.json
```

| 更新内容 | 保留内容 |
| --- | --- |
| 后端 JAR、前端、文档、官方插件、运维工具 | 原 `.env`、Compose、Nginx 配置、数据卷、当前运行镜像、其他插件 |

脚本先校验 ZIP、文件清单、校验和及 JAR，并确认运行容器挂载属于指定安装目录。官方旧插件仅在内容仍与旧清单一致时清理；同名插件已被修改时拒绝覆盖，需人工处理。更新不拉取镜像，不重建 MySQL、Redis、RabbitMQ 或 MinIO。

### 备份与验收

脚本短暂停止前后端写入，将旧应用、配置快照、运行镜像及环境、数据库导出保存到 `backups/<时间>-update/`，随后重建 `backend`、`frontend`，检查 readiness 和前后端版本。命令输出备份目录；其中可能含密钥，需限制管理员访问并另存到安全存储。

预期结果是更新成功退出、前后端版本一致。随后使用实际账号验收登录、充值、上传和下载等业务。可单独运行健康检查：

```bash
python3 release_tools.py smoke http://127.0.0.1:5173 --version 版本号
```

### 更新受阻或失败

| 情况 | 处理 |
| --- | --- |
| 外部数据库、非标准挂载或缺失 Flyway 历史 | 脚本在替换应用前拒绝自动接管，按安装实际情况制定迁移方案 |
| 旧库首次接入 | 先按 `DATABASE-UPGRADE.md` 核验并显式接入；不重放 `init.sql`，不绕过校验 |
| 旧版缺少 `REDIS_PASSWORD`、`PORTAL_JWT_SECRET` 等配置 | 单独完成兼容迁移；脚本不轮换密钥、不修改现有 Redis |
| 更新失败，数据库迁移历史未变 | 自动恢复旧应用并重启，命令仍以失败状态退出 |
| 更新失败，迁移历史改变或无法核验 | 保持应用停止并保留现场，按数据库恢复流程处理，不自动导入 SQL |
| 存在 `.ciphergate-update.lock` | 确认无更新或回退进程运行；仅在核实为异常残留后手工删除 |

## 数据库备份与恢复

### 创建备份

在安装目录执行；源码入口为 `scripts/operations.py`：

```bash
python3 operations.py backup backups/before-upgrade.sql.gz
```

备份包含 schema、数据、存储过程、触发器和事件，压缩文件权限为 600，并生成 `.json` 校验侧文件。密码从运行中 MySQL 容器读取，不写入命令参数。将压缩文件及侧文件一起保存到独立加密存储。

`--single-transaction` 适用于 InnoDB，备份期间不要执行 DDL。该备份不包含 MinIO 对象、Redis 或私有配置。

### 恢复数据库

恢复脚本只允许导入**已存在的空库**；默认安装库非空时会拒绝，避免旧备份留下新版本新增表。先停止业务写入，保留当前库和完整备份，在隔离 Compose 项目／数据卷中演练。脚本不会删除现有业务表，也不会自动切换生产连接。

1. 创建恢复库，例如 `ciphergate_restore_20261025`。使用 MySQL 管理账户执行以下 SQL，按实际应用数据库账户授权：

```sql
CREATE DATABASE ciphergate_restore_20261025 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL ON ciphergate_restore_20261025.* TO 'ciphergate'@'%';
```

2. 校验并导入旧备份；库名确认必须与目标完全一致：

```bash
python3 operations.py restore backups/before-upgrade.sql.gz \
  --target-database ciphergate_restore_20261025 \
  --confirm-database ciphergate_restore_20261025
```

自定义项目或多个 Compose 文件使用原参数，参数位于子命令之前；停止业务也使用相同项目与文件：

```bash
python3 operations.py --directory /srv/CipherGate --project-name ciphergate \
  --compose-file docker-compose.server.yml --compose-file /srv/runtime.override.json \
  restore backups/时间-update/database.sql.gz \
  --target-database ciphergate_restore_20261025 --confirm-database ciphergate_restore_20261025
```

3. 在隔离环境将后端 `SPRING_DATASOURCE_URL` 指向恢复库，以目标发布版本启动。Flyway 验证历史并执行尚未应用的迁移；核对关键表行数、会员余额、充值流水和下载对象。CI 从真实发布 JAR 读取全部迁移版本，演练 V1→当前版本（含 V2、V3）→恢复到空库→重新升级，验证业务标记保留、全部迁移各执行一次且没有残留新表。
4. 维护窗口内停止旧前后端写入并再次确认恢复时间点和对账方案。备份私有配置后，按原部署方式显式切换后端连接到验证后的库，再执行 readiness 与业务检查。不要修改 MySQL 容器的 `MYSQL_DATABASE` 来重建原有数据卷；它只参与首次初始化。保留旧库供核对，回切须评估恢复后产生的新写入。

恢复前脚本校验 SHA256、gzip 完整性、目标库名和目标为空。SQL 导入不是原子操作，失败时保持应用停止，保留失败现场，另建空库重试。需要独立演练备份时可使用 `backup 文件 --database 隔离库名`，默认备份仍使用安装库。

MinIO 另用 S3 客户端同步到独立存储并验证下载；SQL、配置、对象应属于同一恢复时间点。备份后的写入不会自动回放，涉及充值时先对账。不要只复制正在写入的 MySQL 数据目录，不运行 `docker compose down -v`。

## 平台回退

数据库尚未迁移时，使用更新日志中的备份目录：

```bash
python3 update.py rollback --backup backups/时间-update
```

脚本使用备份记录的项目和 Compose 参数，校验应用备份及迁移历史，再恢复旧文件、原镜像并重建前后端。

数据库已经迁移时，先在隔离环境确认旧代码与新 schema 的兼容性。不兼容时需将更新前 SQL、对象和配置一起恢复；Flyway 不自动执行破坏性向下迁移。涉及充值等更新后业务写入时，先停止入口并制定数据对账方案，避免直接恢复备份导致业务数据丢失。

## 日志与诊断

```bash
docker compose -f docker-compose.server.yml logs --since=10m --tail=200 backend frontend
docker compose -f docker-compose.server.yml exec -T frontend nginx -T
docker compose -f docker-compose.server.yml exec -T backend wget -qO- http://localhost:8080/actuator/health/readiness
```

使用自定义项目或覆盖文件时，诊断命令也需携带相同参数。

生产配置禁止 MyBatis SQL、参数和结果行打印，业务日志通过 `X-Request-ID` 关联。请求 ID 限制长度和字符，非法值生成新 UUID。对外提供日志前，移除 OAuth code、令牌、密码及用户数据。

当前前端配置仍信任所有来源的 `X-Forwarded-For`，因此前端端口必须仅允许可信 CDN/WAF 回源访问，否则可被伪造 IP。默认回环绑定适合同机雷池回源；跨机器部署需设置可信代理网段并限制端口来源。后端端口也不得允许外部绕过入口访问。代理链以服务商公布的可信网段和头部清洗规则为准，不根据单次日志猜测可信网段。
