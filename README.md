# CipherGate

应用授权、用户管理与第三方接入平台。

![Java 17](https://img.shields.io/badge/Java-17-2563eb?style=flat-square)
![Spring Boot 4](https://img.shields.io/badge/Spring_Boot-4-16a34a?style=flat-square)
![React + TypeScript](https://img.shields.io/badge/React-TypeScript-0284c7?style=flat-square)
![PolyForm Noncommercial 1.0.0](https://img.shields.io/badge/License-PolyForm_Noncommercial_1.0.0-64748b?style=flat-square)

CipherGate 为客户端软件和应用服务提供卡密授权、应用用户、设备管理、会员运营、调用审计与插件扩展。管理后台、平台用户中心和应用用户 Portal 共用前端，由各自的账号体系和权限控制访问。

## 文档导航

| 目标 | 文档 |
| --- | --- |
| 安装、更新、备份与恢复 | [部署运维指南](docs/operations.md) |
| 了解账号与入口 | [使用指南](docs/quick-start.md) |
| 创建应用、管理更新包 | [应用管理](docs/developer/application.md) |
| 在应用间复制或移动用户 | [用户迁移](docs/developer/user-transfer.md) |
| 接入客户端与第三方充值 | [API 对接](docs/developer/api-integration.md)、[第三方凭证](docs/developer/credential.md) |
| 修改数据库结构 | [数据库迁移规范](docs/database/README.md) |
| 参与开发与提交 PR | [贡献指南](CONTRIBUTING.md) |
| 报告安全问题 | [安全政策](SECURITY.md) |

部署后，使用文档位于站点的 `/docs/` 路径。

## 功能概览

| 领域 | 能力 |
| --- | --- |
| 应用与授权 | 应用配置、卡密生成与激活、设备/IP 绑定、版本检查和更新包分发 |
| 应用用户 | 注册、登录、封禁、密码重置、会员到期管理与跨应用用户迁移 |
| 第三方接入 | HTTP/WebSocket 接口、签名与防重放、加密插件、变量下发和调用日志 |
| 充值凭证 | 独立 API Key/Secret、多应用授权、IP 限制、调用额度和订单幂等 |
| 会员与运营 | 会员等级、配额产品、支付订单、余额、签到、邀请和工单 |
| 权限与审计 | 平台用户、角色、菜单与接口权限、活动记录及应用代理权限 |
| 扩展与管理 | PF4J 加密/函数插件、公告、系统配置和文档管理 |

> **账号边界**：平台账号与应用用户账号分别管理。Portal 中的会员、设备和订单属于选定应用，不会与平台用户中心自动合并。

## 安装发布包

### 环境要求

| 使用方式 | 所需环境 |
| --- | --- |
| 运行发布包 | Docker、Docker Compose v2、Python 3.12+ |
| 从源码开发或构建 | JDK 17、Node.js 22.22.0、Python 3.12+；Docker 用于依赖服务和集成测试 |

运行发布包无需在宿主机安装 Java、Node.js 或 Gradle。Windows 使用支持 Linux containers 的 Docker Desktop，并将下文 `python3` 替换为 `py -3`。

### 1. 获取发布包

从 [Releases](https://github.com/AYssu/CipherGate/releases) 下载完整 `ciphergate-deploy.zip` 和对应的 `SHA256SUMS`。核对完整包的 SHA256 后，解压到固定安装目录：

```bash
unzip ciphergate-deploy.zip -d CipherGate
cd CipherGate
```

本文中的自动化命令适用于**包含 `install.py` 和 `update.py` 的发布包**。早期版本升级方式见[运维指南](docs/operations.md)。

### 2. 安装并启动

本机体验：

```bash
python3 install.py
```

正式域名首次安装：

```bash
python3 install.py --public-url https://auth.example.com
```

两种方式选择其一。脚本校验发布文件、生成本次安装独立的 `.env`、启动全部服务，最后检查健康状态及前后端版本。已有 `.env` 永不覆盖；重试安装不会重新生成凭据。

默认仅监听本机，访问地址为 `http://127.0.0.1:5173`。远程服务器可使用 SSH 隧道；正式域名需要自行准备 DNS、HTTPS 证书和反向代理。安装脚本不修改防火墙或申请证书。

### 3. 完成首次初始化

1. 打开首页，填写自己的 GitHub OAuth App 的 Client ID 和 Client Secret。
2. 在 GitHub OAuth App 中设置与页面一致的回调地址：`https://auth.example.com/api/login/oauth2/code/github`。
3. 确认前端跳转地址为 `https://auth.example.com/dashboard`，完成初始化并登录。

本机体验使用对应的 HTTP 地址。首位 GitHub 登录用户获得超级管理员角色，部署者应先完成初始化和首登，再开放站点。支付、邮件等可选集成仍需配置自己的服务信息。

空数据库由 Flyway 自动初始化；后续只执行新增迁移。已有业务库首次接入必须按[数据库迁移规范](docs/database/README.md)核验，不能直接重放历史 `init.sql`。

## 更新与回退

### 完整更新

将新版完整 ZIP 上传到服务器，放在安装目录之外。在**现有安装目录**执行：

```bash
python3 update.py /path/to/ciphergate-deploy.zip
```

| 自动更新 | 保留原状 |
| --- | --- |
| 后端 JAR、前端、使用文档、官方插件和运维工具 | `.env`、Compose、Nginx 配置、运行镜像及持久化数据卷 |

脚本停止前后端、备份应用和数据库，替换文件后重建应用容器，最后检查健康状态及版本。MySQL、Redis、RabbitMQ 和 MinIO 不随应用更新重建。

> **升级边界**：不要将新包直接覆盖解压到线上目录。数据库已迁移或迁移历史无法核验时，脚本会阻止自动回退旧程序，保留备份并停止应用，交由运维人员处理。

### 回退应用

数据库迁移历史未变化时，使用更新日志输出的备份目录：

```bash
python3 update.py rollback --backup backups/<时间>-update
```

旧版没有脚本、自定义 Compose overrides、配置升级和数据库恢复流程，见[部署运维指南](docs/operations.md)。

独立的 `app.jar`、`frontend-dist.zip`、`docs-dist.zip` 仍供人工维护使用；推荐完整包更新，统一验证前后端版本并自动执行备份与健康检查。

## 配置与服务

`.env.example` 是空凭据模板，`.env` 是安装主机上的私有配置。完整参数以模板为准；已有安装升级时保留原值，不能重新生成数据库或对象存储凭据。

| 配置 | 用途 |
| --- | --- |
| `APP_BACKEND_PUBLIC_BASE_URL` | 后端对外访问地址 |
| `THIRD_PARTY_PUBLIC_BASE_URL` | 第三方下载地址使用的公开根地址；要求 HTTPS 的客户端应配置 HTTPS 域名 |
| `APP_SESSION_COOKIE_SECURE` | HTTPS 环境启用 Secure Cookie；仅本机 HTTP 体验设为 `false` |
| `FRONTEND_BIND_ADDRESS`、`FRONTEND_PORT` | 前端宿主机监听地址及端口 |
| `SERVICE_BIND_ADDRESS` | 后端及基础设施宿主机监听地址，默认回环地址 |
| `MYSQL_IMAGE` | MySQL 镜像；新安装与旧安装分别保留已确认的版本 |

默认宿主机端口如下，可在 `.env` 中调整：

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| Frontend | 5173 | Web 界面、API 反向代理及 `/docs/` |
| Backend | 8080 | 后端服务，外部访问通过前端或受控反向代理 |
| MySQL | 13306 | 数据库 |
| Redis | 16379 | 缓存与在线状态 |
| RabbitMQ | 15673 / 15672 | AMQP / 管理界面 |
| MinIO | 19000 / 19001 | S3 API / 控制台 |

`.env`、数据库备份和对象存储数据应分别安全备份。不要将基础设施端口直接暴露到公网，也不要在原数据卷上随应用更新切换 MySQL 大版本。

## 本地开发

以下命令在仓库根目录执行。开发 Compose 的配置与生产配置分别维护，只用于本地环境。

启动依赖服务：

```bash
docker compose -f compose.yaml up -d --wait
```

启动后端：

```bash
./gradlew bootRun
```

在另一个终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

前端默认位于 `http://localhost:5173`，将 `/api` 请求代理到后端 `http://localhost:8080`。使用仓库自带的 Gradle Wrapper，无需安装全局 Gradle。根目录的 `package.json` 不属于前端，前端依赖在 `frontend/` 中安装。

### 检查与构建

| 任务 | 命令 | 条件 |
| --- | --- | --- |
| 后端单元测试 | `./gradlew unitTest --no-daemon` | JDK 17 |
| 后端集成测试及覆盖率 | `./gradlew unitTest integrationTest jacocoTestReport --no-daemon` | 独立测试数据库与 Redis，见贡献指南 |
| 前端检查 | `cd frontend && npm ci && npm run check` | Node.js 22.22.0 |
| 前端浏览器测试 | `cd frontend && npm run build && npm run test:e2e` | 已安装 Playwright Chromium |
| 文档构建 | `cd docs && npm ci && npm run build` | Node.js 22.22.0 |
| 运维工具测试 | `python3 -m unittest discover -s scripts/tests` | Python 3.12+ |
| 完整发布包 | `./deploy-server.sh` | 全部源码构建环境 |

Windows 使用 `gradlew.bat` 和 `deploy-server.bat`。完整构建会生成 `deploy-bundle/`、`ciphergate-deploy.zip` 及 SHA256 校验文件。

CI 包含后端单元测试、MySQL 8.0/8.4 集成测试、前端检查与浏览器测试、文档构建、密钥和依赖扫描，以及真实 Docker 安装、更新和回退验证。正式发布由版本标签触发，并在 Linux、Windows 完成打包。

## 项目结构

```text
CipherGate/
├── src/                    # 后端代码、测试与 Flyway 迁移
├── frontend/               # React 管理界面与用户门户
├── docs/                   # VitePress 使用和运维文档
├── plugins/                # PF4J 插件实现
├── ciphergate-plugin-api/  # 插件接口
├── scripts/                # 构建、安装、更新及备份恢复工具
├── .github/                # CI、发布与协作模板
└── deploy-bundle/          # 生成的部署目录，不提交 Git
```

后端采用 Java 17、Spring Boot 4、Spring Security、MyBatis-Plus 与 Flyway；前端采用 React、TypeScript、Vite、Ant Design 与 ECharts。基础设施版本以对应 Compose 文件为准。

## 贡献与许可

提交问题和 Pull Request 前，请阅读[贡献指南](CONTRIBUTING.md)。安全漏洞按[安全政策](SECURITY.md)私下报告，不在公开 Issue 中提交凭据或可利用的漏洞细节。

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)。允许用途以许可证原文为准；商业用途需另行联系 [AYssu](https://github.com/AYssu) 获取授权。分发须保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)，第三方依赖继续遵循各自许可证。
