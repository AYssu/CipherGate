# CipherGate

CipherGate 是一个面向 **SaaS / 客户端软件 / 第三方平台** 的「统一鉴权与安全接入层」：你可以把它当成应用的安全网关与账号/卡密中心，用 **可插拔加密插件** + **标准化对接协议**，把“登录校验、会话保持、变量下发、风控限流”等能力统一收口。

## 你会用它解决什么问题
- **第三方对接割裂**：不同语言/端（Java/C++/JS）各自实现一套鉴权、加密、重放保护，维护成本高
- **接口被刷/被重放**：缺少统一的 nonce/时间窗/限流与封禁策略
- **账号体系混乱**：终端用户、应用、卡密等身份维度散落在各个服务里
- **需要“长连接在线态”**：登录后要持续在线、定时下发配置/变量、实时推送事件

## 为什么选 CipherGate（核心卖点）
- **统一协议，跨语言好接**：HTTP + WebSocket 两套对接入口，适合 Java/C++/JS
- **安全默认值**：时间窗校验、防重放、签名校验、限流与短期封禁（可调整）
- **插件化加解密**：按应用维度选择加解密算法/密钥策略，不把加密逻辑写死在业务里
- **长连接能力**：WS-only 登录 + 认证后心跳推送（可承载在线态与变量下发）

## 核心能力一览
- **应用管理**：为每个应用分配 `appKey/appSecret` 与加密配置
- **终端用户（AppUser）**：应用内用户体系（用户名/密码、登录统计等）
- **卡密体系（LicenseKey）**：卡密登录、绑定策略、到期/使用次数、IP/设备校验等
- **应用变量（AppVariable）**：按应用管理变量，支持在对接链路中下发
- **三方 HTTP 接口**：HMAC 签名 + 防重放 + 可插拔加解密
- **三方 WebSocket**：
  - **WS-only 登录**：`appKey/appSecret + AppUser(用户名/密码)` 一起校验
  - **会话密钥协商**：X25519(ECDH) + HKDF 派生 `sessionKey`
  - **消息加密/防篡改**：AES-GCM(sessionKey)
  - **认证后心跳推送**：每 5 秒推送一次（包含 `variables` + `ts`，加密传输）

## 快速上手（5 分钟）

### 1) 启动依赖（MySQL / MinIO / Redis）
在项目根目录：

```bash
docker compose -f compose.yaml up -d
```

默认端口：
- **MySQL**：`localhost:13306`
- **MinIO**：`http://localhost:19000`（Console：`http://localhost:19001`）
- **Redis**：`localhost:16379`

### 2) 启动后端
```bash
./gradlew bootRun
```

后端：`http://localhost:8080`

### 3) （可选）启动前端
```bash
cd frontend
npm install
npm run dev
```

## 对接入口（给集成方看的）

### HTTP：卡密登录（示例能力）
- `POST /api/v1/card/login`
- 特点：HMAC 签名、时间窗、防重放、响应签名、按应用加解密插件封包

### WebSocket：WS-only 用户登录 + 心跳下发变量
- `/api/v1/ws`
- 登录流程：`CONNECTED -> HELLO -> HELLO_ACK -> AUTH -> AUTH_OK`
- 心跳：认证后服务端每 5 秒推送一次 `HEARTBEAT`（加密 payload：`{ ts, variables }`）

对接示例（可直接跑）：
- `cardUserBindTest` 中的 `ThirdPartyWsUserLoginClient`

## 接口文档
项目内置 OpenAPI/Knife4j（按模块分组）。
> 文档访问权限受安全配置控制（默认仅超级管理员可访问）。

## 部署
- 生产部署 / 镜像构建：见 `README.Docker.md`

