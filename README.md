# CipherGate

![Java](https://img.shields.io/badge/Java-17-2ea44f?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6db33f?style=flat-square)
![React](https://img.shields.io/badge/Frontend-React%20%2B%20TypeScript-61dafb?style=flat-square)
![Database](https://img.shields.io/badge/Stack-MySQL%20%7C%20Redis%20%7C%20MinIO-4c8eda?style=flat-square)
![License](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-orange?style=flat-square)

企业级统一鉴权与安全接入平台，面向 SaaS、客户端软件和第三方生态，提供标准化的接入与安全中台能力。

---

## 项目简介

CipherGate 将分散在业务侧的鉴权、会话、风控、配置分发能力统一收敛，帮助团队实现：

- **更低的接入成本**：HTTP / WebSocket 协议统一
- **更强的安全一致性**：签名、防重放、限流、加密链路
- **更清晰的运营治理**：用户、卡密、在线时长、行为日志

---

## 核心功能

### 🔐 安全管理
- 端到端数据加密，时间窗校验、nonce 防重放
- 细粒度访问控制与签名验证
- 会话加密通信
- OAuth2 登录集成（GitHub 等）

### 👥 用户体系
- 应用用户管理、密码重置、封禁与解封
- 会员等级体系与设备绑定
- 多设备绑定与账号状态治理
- 用户注册验证码（OTP）支持

### 🎫 卡密运营
- 卡密批量生成、分发、加时续期
- 设备/IP 绑定与解绑控制
- 卡密激活统计与状态流转
- 卡密心跳检测与在线管理

### 💰 支付与财务
- 集成 Epay 支付网关
- 订单管理与支付状态追踪
- 余额系统与交易记录
- 定价计划管理

### 📊 数据监控
- 实时数据统计与仪表盘
- 调用日志分析与用户行为追踪
- 在线状态实时展示与掉线感知
- 活动日志审计

### 🎟️ 运营工具
- **工单系统**：技术支持请求与问题反馈
- **公告系统**：系统公告与应用公告管理
- **签到系统**：每日签到与连续签到奖励
- **邀请系统**：邀请好友注册获得奖励

### 🤝 代理协作
- 代理账户管理与权限分配
- 代理配额控制
- 代理绑定用户管理

### 🔌 第三方接入
- HTTP + WebSocket 双通道接入
- 统一开放接口标准
- 变量配置中心与动态下发
- 系统消息推送

### 🧩 插件扩展
- 插件上传、启停、配置管理
- 支持按需扩展加解密或协议处理逻辑
- 平台能力可持续演进而不侵入主干业务

### 🛡️ RBAC 权限
- 后台用户、角色、菜单、权限点统一管理
- 细粒度接口授权与权限隔离
- 多角色协作和审计支持

---

## 适用场景

- **企业应用授权**：管理内部应用的用户授权
- **SaaS 服务**：为 SaaS 应用提供用户管理能力
- **软件分发**：通过卡密进行软件授权分发
- **API 网关**：统一管理 API 访问权限
- **代理商管理**：多级代理体系与分润

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 17, Spring Boot 4, Spring Security, WebSocket, MyBatis-Plus |
| **前端** | React 18, TypeScript, Vite, Ant Design 5, ECharts |
| **基础设施** | MySQL 8, Redis 7, RabbitMQ 3, MinIO |
| **构建交付** | Gradle, Docker Compose, GitHub Actions |
| **扩展机制** | PF4J (插件框架) |
| **工具库** | Hutool, Fastjson2, ip2region, JJWT |

---

## 项目结构

```
CipherGate/
├── src/                          # 后端源码
│   └── main/java/com/ayssu/ciphergate/
│       ├── controller/           # API 控制器
│       ├── entity/               # 数据库实体
│       ├── service/              # 业务逻辑层
│       ├── mapper/               # MyBatis Mapper
│       ├── config/               # 配置类
│       ├── agent/                # 代理模块
│       ├── portal/               # 用户门户
│       └── thirdparty/           # 第三方接入
├── frontend/                     # 前端源码
│   ├── src/
│   │   ├── components/           # 组件
│   │   ├── pages/                # 页面
│   │   └── services/             # API 服务
│   └── dist/                     # 构建产物
├── plugins/                      # 插件模块
│   └── rsa-crypto-plugin/        # RSA 加密插件
├── docs/                         # 文档源码
├── deploy-bundle/                # 部署包
└── ciphergate-plugin-api/        # 插件 API
```

---

## 快速开始

### 环境要求

- Docker & Docker Compose
- Java 17+ (仅本地开发)
- Node.js 22.22.0、Python 3.12+ (源码构建)

### 方式一：本地开发

**1) 启动依赖服务**

```bash
docker compose -f compose.yaml up -d
```

启动 MySQL、Redis、RabbitMQ、MinIO 四个基础服务。

**2) 启动后端服务**

```bash
./gradlew bootRun
```

**3) 启动前端（可选）**

```bash
cd frontend
npm ci
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`。

### 方式二：生产部署

**1) 下载部署包**

从 [GitHub Releases](https://github.com/AYssu/CipherGate/releases) 下载 `ciphergate-deploy.zip`。

**2) 解压并配置**

```bash
unzip ciphergate-deploy.zip -d deploy-bundle
cd deploy-bundle
```

安装发布包只需要 Docker（含 Compose v2）和 Python 3.12+，无需 Java、Node 或 Gradle。

```bash
# 自动生成此安装独立的 .env、启动服务并检查健康/版本
python3 install.py
# 有 HTTPS 域名及反向代理时，首次安装改用：
# python3 install.py --public-url https://auth.example.com
```

默认通过本机 `http://127.0.0.1:5173` 访问；服务器可使用 SSH 隧道或自己的 HTTPS 反向代理。脚本不替你申请证书、改 DNS 或开放端口，已有 `.env` 永不覆盖。Windows 使用 `py -3 install.py`。

打开首页，填写**自己的 GitHub OAuth App** 配置，按页面预填的当前域名设置回调地址；首位 GitHub 登录用户成为超级管理员。部署者先完成初始化和首登，再开放站点。支付、邮件等集成仍需自己的服务配置。

空库由 Flyway 自动初始化，后续只执行新增迁移。**旧库首次接入**必须先备份并按 [数据库升级说明](docs/database/README.md) 核验，不能重放历史 `init.sql`。

---

## 部署架构

生产环境包含以下服务：

| 服务 | 说明 | 默认宿主机端口 |
|------|------|----------|
| **frontend** | Nginx 前端服务 | 5173 |
| **backend** | Java 后端服务 | 8080 |
| **mysql** | MySQL 数据库 | 13306 |
| **redis** | Redis 缓存与会话存储 | 16379 |
| **rabbitmq** | RabbitMQ 消息队列 | 15673（管理界面 15672） |
| **minio** | MinIO 对象存储 | 19000（控制台 19001） |

所有端口均可通过 `.env` 文件自定义。

### 服务依赖关系

```
frontend → backend → mysql
                  → redis
                  → rabbitmq
                  → minio
```

---

## 更新与回退

下载新版完整包，放到部署目录以外，在当前部署目录执行：

```bash
python3 update.py /path/to/ciphergate-deploy.zip
# 如需回退，使用更新时输出的备份目录（迁移历史未变时）
python3 update.py rollback --backup backups/时间-update
```

更新自动校验、备份旧应用和数据库、替换应用并重建前后端、检查健康及版本；保留原 `.env`、Compose、Nginx、镜像及持久化数据。数据库迁移后不会盲目回退旧程序或自动覆盖数据库。不要把新包直接覆盖解压到线上目录，也不要只替换 JAR 后执行 `up -d`，挂载文件更新未必触发容器重启。

旧安装尚无脚本、使用自定义 Compose overrides、数据库已迁移等情况，见 [完整运维说明](docs/operations.md)。单独的 `app.jar`、`frontend-dist.zip` 保留供人工维护使用；推荐完整包更新以核对前后端版本。

---

## 配置说明

### 环境变量

部署包只带空凭证模板 `.env.example`；完整参数以该模板为准。安装时生成私有 `.env`。以下仅说明主要参数，不能直接作为生产配置：

```bash
# 数据库配置
MYSQL_ROOT_PASSWORD=your_password
MYSQL_DATABASE=ciphergate
MYSQL_USER=your_user
MYSQL_PASSWORD=your_password
MYSQL_PORT=3306

# Redis 配置
REDIS_PORT_HOST=16379
REDIS_PASSWORD=<独立强密码>
PORTAL_JWT_SECRET=<至少32字节随机密钥>

# RabbitMQ 配置
RABBITMQ_USERNAME=ciphergate
RABBITMQ_PASSWORD=<独立强密码>
RABBITMQ_PORT=5672
RABBITMQ_MGMT_PORT=15672

# MinIO 配置
MINIO_ACCESS_KEY=your_access_key
MINIO_SECRET_KEY=your_secret_key
MINIO_BUCKET=ciphergate-plugins
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001

# 服务端口
BACKEND_PORT=8080
FRONTEND_PORT=5173
```

### 安全建议

生产环境部署前，务必：

1. 修改所有默认密码
2. 使用强密码策略
3. 限制数据库、Redis 等服务的外网访问
4. 配置 HTTPS 反向代理
5. 定期备份数据库

---

## 功能模块详解

### 开发者中心

面向应用开发者，提供应用全生命周期管理：

| 功能 | 说明 |
|------|------|
| **应用管理** | 创建、编辑、删除应用，配置应用参数 |
| **卡密管理** | 批量生成卡密、查看激活统计、管理卡密状态 |
| **用户管理** | 管理应用用户、封禁/解封、密码重置 |
| **变量配置** | 配置应用变量，支持动态下发 |
| **调用日志** | 查看 API 调用记录与统计数据 |
| **凭证管理** | 管理应用的 appKey/appSecret |

### 用户门户

面向终端用户，提供自助服务：

| 功能 | 说明 |
|------|------|
| **会员服务** | 查看会员等级、续费、升级 |
| **订单管理** | 查看历史订单、支付状态 |
| **余额充值** | 账户余额充值与消费记录 |
| **工单系统** | 提交技术支持、查看处理进度 |
| **签到系统** | 每日签到获取奖励 |
| **邀请好友** | 分享邀请码获取奖励 |

### 第三方接入

支持 HTTP 和 WebSocket 两种接入方式：

| 方式 | 适用场景 | 特点 |
|------|----------|------|
| **HTTP** | 请求式调用 | 签名验证、防重放、限流 |
| **WebSocket** | 实时通信 | 认证握手、加密通信、心跳维持 |

### 代理系统

支持多级代理体系：

- 代理账户创建与管理
- 代理权限分配
- 代理配额控制
- 代理绑定用户管理

---

## 文档

部署后可通过以下路径访问在线文档：

- **管理后台文档**：`http://your-domain/docs/`
- **快速入门**：注册账号、首次登录
- **开发者中心**：应用管理、卡密管理、变量配置
- **用户中心**：会员服务、订单管理、工单系统
- **代理中心**：代理管理、配额分配

---

## 开发指南

### 本地开发环境搭建

```bash
# 1. 克隆项目
git clone https://github.com/AYssu/CipherGate.git
cd CipherGate

# 2. 启动基础服务
docker compose -f compose.yaml up -d

# 3. 启动后端
./gradlew bootRun

# 4. 启动前端（可选）
cd frontend && npm ci && npm run dev
```

### 构建部署包

```bash
# Linux/macOS
./deploy-server.sh

# Windows
deploy-server.bat
```

构建完成后生成 `ciphergate-deploy.zip`、SHA256 校验和及逐文件清单。Linux/Windows 入口共用 Python 打包器，发布包不包含真实凭证。

完整安装、升级、备份恢复、日志排障见 [运维说明](docs/operations.md)；数据库变更见 [Flyway 迁移规范](docs/database/README.md)。

PR 检查包含后端单元/真实 MySQL 集成测试、插件测试、前端 lint/类型/单元/浏览器测试、文档构建、密钥及依赖检查。主分支推送只验证，发布通过版本标签触发。

### 插件开发

插件使用 PF4J 框架，参考 `plugins/rsa-crypto-plugin` 示例：

1. 在 `plugins/` 目录下创建新模块
2. 实现 `ciphergate-plugin-api` 中定义的接口
3. 使用 Gradle 构建插件 JAR
4. 通过管理后台上传插件

---

## 贡献

欢迎提交 Issue / PR。环境准备、测试和审查规范见 [CONTRIBUTING.md](CONTRIBUTING.md)，漏洞报告见 [SECURITY.md](SECURITY.md)。

建议在变更说明中包含：
- 功能范围
- 风险点
- 回滚方案
- 自测结果

---

## 许可证

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，允许许可证规定范围内的非商业使用、修改和分发。商业用途需另行联系 [AYssu](https://github.com/AYssu) 获取授权；具体允许用途以许可证原文为准。

分发时须保留 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。第三方依赖继续遵循各自许可证。
