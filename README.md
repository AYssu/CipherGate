# CipherGate

![Java](https://img.shields.io/badge/Java-17-2ea44f?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6db33f?style=flat-square)
![React](https://img.shields.io/badge/Frontend-React%20%2B%20TypeScript-61dafb?style=flat-square)
![Database](https://img.shields.io/badge/Stack-MySQL%20%7C%20Redis%20%7C%20MinIO-4c8eda?style=flat-square)
![License](https://img.shields.io/badge/License-Open%20Source-orange?style=flat-square)

> 企业级统一鉴权与安全接入平台  
> 聚焦第三方接入、账号体系、卡密会员、在线会话、变量下发与运营可观测

---

## 📌 项目简介

CipherGate 面向 SaaS、客户端软件和第三方生态，提供一套标准化的接入与安全中台能力。  
它将分散在业务侧的鉴权、会话、风控、配置分发能力统一收敛，帮助团队实现：

- 更低的接入成本（HTTP / WebSocket 协议统一）
- 更强的安全一致性（签名、防重放、限流、加密链路）
- 更清晰的运营治理（用户、卡密、在线时长、行为日志）

---

## 🚀 核心优势

- 🔐 **安全默认内建**：时间窗校验、nonce 防重放、签名验证、会话加密
- 🧩 **可插拔扩展**：支持插件化加解密与策略扩展，适配不同业务模型
- 🌐 **双通道接入**：HTTP + WebSocket，兼顾请求式与长连接场景
- 📊 **可运营可观测**：仪表盘、登录事件、在线时长、系统消息全链路沉淀
- 🏢 **企业级治理**：RBAC 权限体系 + 多应用隔离 + 代理协作模型

---

## 🧭 功能模块

### 1. 权限与账号中心（RBAC）
- 后台用户、角色、菜单、权限点统一管理
- 支持细粒度接口授权与权限隔离
- 适用于多角色协作和审计要求较高的场景

### 2. 应用管理中心
- 应用生命周期管理（创建、维护、启停）
- 应用级 `appKey/appSecret` 与安全参数管理
- 支持加密策略、更新发布等运营能力

### 3. 终端用户中心（AppUser）
- 客户端用户管理、密码重置、封禁与解封
- 设备绑定与账号状态治理
- 会员到期与有效性统一管理

### 4. 卡密与授权体系（License）
- 卡密生成、批量发放、加时续期、状态流转
- 设备/IP 绑定与解绑控制
- 支持按业务策略进行授权与风控

### 5. 第三方接入网关（HTTP + WS）
- 统一开放接口标准，降低多端对接复杂度
- HTTP 场景：签名、防重放、请求/响应安全处理
- WS 场景：认证握手、加密通信、会话维持与实时下发

### 6. 在线会话与时长统计
- 在线状态实时展示，掉线立即感知
- 30 秒内重连可延续在线时长，不从 0 重新计算
- Redis 持久化会话片段，支持当天在线时长统计

### 7. 变量配置中心（AppVariable）
- 变量管理、历史追踪、导入导出
- 支持在接入链路中实时下发，减少客户端硬编码
- 适合灰度开关、动态配置、分层策略场景

### 8. 插件扩展体系
- 插件上传、启停、配置管理
- 支持按需扩展加解密或协议处理逻辑
- 让平台能力可持续演进而不侵入主干业务

### 9. 运营分析与审计中心
- 仪表盘指标、访问事件、活动日志
- 站内消息与未读提醒
- 支撑运营洞察、故障追踪与风控回溯

### 10. 系统配置与初始化
- 首次初始化引导
- OAuth、站点信息、邮件能力等系统参数统一配置
- 提供基础运行状态查看能力

---

## 🛠 技术栈

- **Backend**: Java 17, Spring Boot 4, Spring Security, WebSocket, MyBatis-Plus
- **Frontend**: React, TypeScript
- **Infrastructure**: MySQL, Redis, MinIO
- **Build & Delivery**: Gradle, Docker Compose, GitHub Actions
- **Extensibility**: PF4J

---

## ⚡ 快速开始

### 1) 启动依赖服务
```bash
docker compose -f compose.yaml up -d
```

### 2) 启动后端服务
```bash
./gradlew bootRun
```

### 3) 启动前端（本地开发可选）
```bash
cd frontend
npm install
npm run dev
```

---

## 🔌 接入能力（对接方重点）

- HTTP 登录/鉴权接口
- WebSocket 长连接认证与心跳
- 应用变量动态下发
- 客户端在线态与登录事件可观测

---

## 📦 部署与更新

- 首次部署：下载并使用全量包 `deploy-bundle.zip`
- 增量更新：
  - 仅后端：替换 `app.jar`
  - 仅前端：替换 `frontend-dist.zip` 解压内容

统一启动命令：
```bash
docker compose -f docker-compose.server.yml up -d
```

---

## 📚 API 文档

内置 OpenAPI / Knife4j 文档，默认受权限控制（管理员可见）。

---

## 🤝 贡献

欢迎提交 Issue / PR。  
建议在变更说明中包含：功能范围、风险点、回滚方案与自测结果。

