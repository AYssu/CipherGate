# 部署、升级与故障恢复

运行环境：Docker、Compose v2、Python 3.12+。源码打包另需 JDK 17 和 Node.js 22.22.0；安装发布包不需要安装 Java、Node 或 Gradle。Linux 与 Windows 的打包入口共用 `scripts/project.py`。

## 首次安装

校验发布包附带的 SHA256，解压到固定目录。每套安装独立生成密码：

```bash
python3 setup-env.py
python3 setup-env.py --check
# 编辑 .env 中的域名、端口等设置后再检查
python3 setup-env.py --check
docker compose -f docker-compose.server.yml config --quiet
docker compose -f docker-compose.server.yml up -d --wait --wait-timeout 300
```

源码目录使用 `python3 scripts/setup-env.py --output .env`。生成器拒绝覆盖已有文件。`.env` 含凭证，权限应为 600，独立安全备份，不提交 Git、不随发布包分发。公开模板只有 `.env.example`。首次安装 MySQL 默认使用模板中的 8.4；已有安装遵循下文的版本约束。

默认所有宿主机端口只监听回环地址。同机 host 网络的雷池可以回源 `127.0.0.1:<FRONTEND_PORT>`。远程反向代理需明确设置可达地址并在防火墙限制源地址。生产入口使用 HTTPS；`APP_SESSION_COOKIE_SECURE=true` 需要 HTTPS，纯 HTTP 本地调试才设为 false。

后端读取 prod profile 的环境变量，缺失必需凭证直接报错。Redis 同时配置服务端和后端密码。MySQL、Redis 的健康探针就绪后后端启动，后端 readiness 就绪后前端启动。MinIO 使用固定版本 `pgsty/silo` 镜像，原有对象卷和 S3 接口不变，需保留原始访问密钥及卷。

## 已有服务器升级

1. 保存旧发布包、原 `.env`、当前镜像版本和 Compose 项目名，先做数据库和对象备份。运行 `docker compose -f docker-compose.server.yml config --images` 保存镜像列表；旧版没有此参数时使用 `docker inspect`。确认备份可在隔离环境恢复。
2. 保留原来的部署目录、Compose 项目名、数据卷、数据库账号密码、MinIO 密钥；发布包不提供 `.env`，不会替换它。历史仓库里的 `.env.server` 已撤出版本管理，本机副本为 `.env.server.local`，不会自动上传或替换服务器配置。
3. 对照 `.env.example` 添加缺失变量：`REDIS_PASSWORD`、足够强的 `PORTAL_JWT_SECRET`、绑定地址及 HTTPS Cookie 设置。已启用 Redis 密码则保留原值；新启用密码需同时重建 Redis 与后端。更换 JWT 密钥会让现有 Portal 登录失效。
4. **MySQL 大版本不随应用升级。** 新模板是 8.4，既有 8.0 安装保持 `MYSQL_IMAGE` 为原 8.0 镜像；未设变量的 Compose 回退仍为 8.0。禁止将新模板直接盖到旧配置后在原数据卷启动 8.4。数据库大版本升级另做备份、隔离恢复与官方升级兼容性检查。
5. 首次接入 Flyway 按 `DATABASE-UPGRADE.md`（源码 `docs/database/README.md`）操作。旧库只有完整核验通过才能显式接入；不能重放 init.sql 或跳过校验。接入完成删除临时接入开关。
6. 替换应用、前端、插件及 Compose 配置后执行以下命令。JAR 是挂载文件，单纯 `up -d` 可能不重启既有容器，因此升级显式重建应用：

```bash
python3 setup-env.py --check
docker compose -f docker-compose.server.yml config --quiet
docker compose -f docker-compose.server.yml up -d --wait --wait-timeout 300 mysql redis rabbitmq minio
docker compose -f docker-compose.server.yml up -d --force-recreate --wait --wait-timeout 300 backend frontend
docker compose -f docker-compose.server.yml ps
```

在源码目录可运行 `python3 scripts/project.py smoke http://127.0.0.1:<FRONTEND_PORT> --version <版本号>`；发布目录可手工检查 `/health/ready` 返回 UP、`/api/config/init/status` 返回 JSON、`/build-info.json` 与发布版本一致。用实际账号验证登录、关键查询和更新包下载。旧数据、原凭证已暴露时需要协调轮换；移出 Git 不会删除历史副本，也不会自动更改服务器密码。

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

数据库尚未迁移时，恢复旧包及原镜像并重建 backend/frontend。数据库已迁移时，先在隔离环境确认旧代码兼容新 schema；不兼容则使用升级前 SQL、对象和配置一起恢复。Flyway 不自动执行破坏性的向下迁移。涉及充值等业务写入时先停止入口并制定数据对账方案，避免直接恢复备份丢失升级后的业务。

## 日志和真实 IP

```bash
docker compose -f docker-compose.server.yml logs --since=10m --tail=200 backend frontend
docker compose -f docker-compose.server.yml exec -T frontend nginx -T
docker compose -f docker-compose.server.yml exec -T backend wget -qO- http://localhost:8080/actuator/health/readiness
```

prod 禁止 MyBatis SQL/参数/结果行打印，业务日志通过 `X-Request-ID` 关联。请求 ID 限制长度和字符，非法值生成新 UUID。不要公开含 OAuth code、令牌、密码、用户数据的原始日志。

当前前端保留既有信任所有来源 X-Forwarded-For 的配置；**只允许可信 CDN/WAF 回源访问此前端端口**，否则攻击者可伪造 IP。默认回环绑定满足同机雷池回源；跨机器部署时配置可信代理网段并限制端口访问。后端端口也不能让外部绕过入口直接访问。代理链需以可信代理的官方网段/清洗规则为准，不能从某次日志猜测整个运营商网段都是可信代理。
