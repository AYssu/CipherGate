## 升级前

- 下载 `SHA256SUMS` 和对应产物，执行 `sha256sum --check SHA256SUMS` 校验。
- 备份 MySQL 数据库、当前应用包、`.env` 和持久化卷，保留上一版镜像标签。
- 阅读部署包中的 `DATABASE-UPGRADE.md`。旧库首次接入 Flyway 需按文档核验并显式启用；不要删除历史表、开启自动 baseline 或重跑历史 SQL。
- 阅读自动生成的变更记录，确认本次数据库迁移是否支持回退。MySQL DDL 不能保证事务回滚。

## 首次部署

1. 解压 `ciphergate-deploy.zip`，运行 `python3 setup-env.py` 生成此安装实例独立的 `.env`，配置域名。不要覆盖已有部署的 `.env`。
2. 执行 `docker compose --env-file .env -f docker-compose.server.yml config --quiet`。
3. 执行 `docker compose --env-file .env -f docker-compose.server.yml up -d`。
4. 执行 `docker compose -f docker-compose.server.yml ps`，检查健康状态及后端迁移日志，并验证登录、应用列表和更新包下载。

## 更新已有部署

1. 解压到新目录，保留线上 `.env`、持久化数据及自定义 Nginx 配置。
2. 后端更新替换 `app/app.jar`；前端更新替换 `frontend/dist`；同步本次所需插件和 Compose 配置。
3. 执行 `docker compose --env-file .env -f docker-compose.server.yml up -d --force-recreate backend frontend`。仅覆盖静态文件不会触发容器自动重建。
4. 检查 `docker compose -f docker-compose.server.yml logs --tail=200 backend frontend`，确认迁移成功，再验证 `/health/ready`、登录、核心业务和文件上传下载。

## 回退

立即停止有问题版本，保留失败日志。无数据库变更且兼容时恢复上一版应用包并重建容器；涉及不兼容迁移时，按维护窗口恢复已验证的数据库备份及匹配版本应用。不要直接执行 `flyway clean` 或删除迁移历史。部署包只包含 `.env.example`，不会分发通用凭据。

## 构建产物

- `ciphergate-deploy.zip`：完整部署包。
- `app.jar`：后端更新包。
- `frontend-dist.zip`、`docs-dist.zip`：前端及文档。
- 插件 JAR、`SHA256SUMS`：插件更新和完整性校验。
- Linux 和 Windows 打包均通过后才发布；运行记录保留各平台产物及测试报告。
