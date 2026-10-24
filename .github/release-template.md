## 升级前

- 下载 `SHA256SUMS` 和对应产物，执行 `sha256sum --check SHA256SUMS` 校验。
- 备份 MySQL 数据库、当前应用包、`.env` 和持久化卷，保留上一版镜像标签。
- 阅读部署包中的 `DATABASE-UPGRADE.md`。旧库首次接入 Flyway 需按文档核验并显式启用；不要删除历史表、开启自动 baseline 或重跑历史 SQL。
- 阅读自动生成的变更记录，确认本次数据库迁移是否支持回退。MySQL DDL 不能保证事务回滚。

## 首次部署

1. 校验完整包后解压到固定目录，安装 Docker Compose v2 和 Python 3.12+。
2. 执行 `python3 install.py`；正式 HTTPS 入口首次使用 `python3 install.py --public-url https://你的域名`，证书和反向代理需自己配置。
3. 脚本生成独立 `.env`、启动六个服务并检查前后端版本。已有 `.env` 不覆盖，默认仅本机端口可访问。
4. 打开首页配置自己的 GitHub OAuth App；首位 GitHub 登录用户为超级管理员。支付、邮件等可选集成需要自己的配置。

## 更新已有部署

1. 将新包下载到另一目录，不要直接覆盖解压到线上目录。
2. 在现有部署目录执行 `python3 update.py /path/ciphergate-deploy.zip`；自定义项目/Compose overrides 必须传入原参数，见包内 `OPERATIONS.md`。
3. 脚本保留原 `.env`、Compose、Nginx、基础设施镜像和卷，自动备份应用与数据库、重建前后端并检查版本。
4. 旧版没有脚本时，从另一个目录解压的新包执行 `python3 /tmp/new-release/update.py /tmp/ciphergate-deploy.zip --directory /srv/CipherGate`。旧库首次接入 Flyway 仍需先按数据库文档完成核验。

## 回退

执行 `python3 update.py rollback --backup backups/时间-update`。迁移历史未变时可恢复旧应用；迁移历史改变或无法核验时保留现场、停止应用，按 `OPERATIONS.md` 明确恢复数据库后再回退，不自动覆盖 SQL。不要执行 `flyway clean`、删除迁移历史或删除持久化卷。

## 构建产物

- `ciphergate-deploy.zip`：完整部署包。
- `app.jar`：后端更新包。
- `frontend-dist.zip`、`docs-dist.zip`：前端及文档。
- 插件 JAR、`SHA256SUMS`：插件更新和完整性校验。
- `LICENSE`、`NOTICE`：PolyForm Noncommercial 1.0.0 许可原文和版权声明；完整包、前端及文档分包、项目 JAR 均携带许可。
- 发布包不携带 `.env`、开发配置或开发环境默认凭据；首次部署由 `install.py` 调用生成器在安装主机生成独立凭据。商业用途需另行联系作者取得授权。
- Linux 和 Windows 打包均通过后才发布；运行记录保留各平台产物及测试报告。
