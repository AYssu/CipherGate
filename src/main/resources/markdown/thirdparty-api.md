# 第三方卡密登录接口（/api/v1）

## 1. 安全约束
- **必须使用 HTTPS/TLS**
- **严禁传输 appSecret 明文**：仅通过签名 `X-Signature` 证明身份
- **防重放**：`X-Timestamp`（毫秒）+ `X-Nonce`（随机串），服务端做时间窗校验与 nonce 去重

## 2. 请求头（必填）
- `X-App-Key`: 应用 `appKey`
- `X-Timestamp`: 毫秒时间戳
- `X-Nonce`: 随机串
- `X-Signature`: HMAC-SHA256 签名（hex）

## 3. 签名原文与算法

### 3.1 bodyDigest
`bodyDigest = SHA256(原始 HTTP 请求体字节，UTF-8)`（hex）。与报文是否加密无关，**按线上实际发送的 body 原文**计算。

### 3.2 signString
按如下格式拼接（注意换行符 `\\n`）：

```
METHOD\n
PATH\n
TIMESTAMP\n
NONCE\n
bodyDigest
```

例如：

```text
POST
/api/v1/card/login
1712630400000
randomNonce123
<bodyDigestHex>
```

### 3.3 signature
`X-Signature = HMAC-SHA256(appSecret, signString)`（hex）

## 4. 报文加解密（插件）
- 服务端会把 **请求头 + 原始请求体** 等放入 `Map`，交给应用绑定的 `encryptionPlugin` 解密；**请求签名的 bodyDigest 始终基于原始 body**（见 3.1）。
- 默认内置插件 **`aes-default`**（新建应用的 `encryptionPlugin` 默认值），与独立插件工程 **EncryptionModule / `aes-data-v1`** 算法一致（Hutool 默认 **AES/ECB/PKCS5Padding**）：
  - **密钥**（UTF-8 长度须为 **16 / 24 / 32** 字节）：优先读应用 `encryptionConfig` 的 **`aesKey`** 或 **`secretKey`**，否则读插件库 **`pluginConfig.aesKey`**，再否则读服务端内置 `aes-default.defaults.json`（仅便于本地联调，生产请务必配置）。
  - 业务明文为 **canonical 字符串**：`key=value&key2=value2`（**按 key 字典序**拼接，与 EncryptionModule 相同）。
  - 请求体 JSON 根级字段 **`data`**：**上述明文的 AES 密文的十六进制（HEX）**。
  - 响应同样通过 `data` 返回 HEX 密文；`pluginId` 为 `aes-default`。
- 其它算法可通过 PF4J 扩展实现，并在应用上配置对应 `encryptionPlugin` 标识。

## 5. 接口

### 5.1 卡密登录
- `POST /api/v1/card/login`

请求体（解密后）：
- `cardCode`: 卡密（必填）
- `deviceId`: 设备码（必填）
- `ip`: 可选（用于 IP 校验）
- `appUserId`: 可选；不传时服务端会按卡密自动生成内部用户标识

响应（见 `CardLoginResponse`）：
- `appId`、`cardId`、`cardCode`、`expiresAt`、`available`、`bindNumber`、`online`
- **`variables`**：当前应用下 **已启用** 变量的键值映射（与 5.5 专用查询接口返回的 `variables` 语义一致）

说明：
- 首次登录会自动创建绑定并激活卡密（写入 `firstUsedAt`）。
- 后续登录会复用已存在绑定，只要未过期即可登录。
- 不返回登录 token，也不包含心跳接口。

### 5.1.1 卡密换绑设备
- `POST /api/v1/card/rebind`
- 请求头、签名、`bodyDigest`、报文 **AES（`aes-default`）加解密** 与 5.1 及第 4 节一致（签名 PATH 为 `/api/v1/card/rebind`）。
- 解密后业务体：
  - **`cardCode`**：卡密（必填）
  - **`deviceId`**：新的设备标识（必填）
- 规则：
  - 卡密须已激活（曾成功登录过）；未激活返回错误，请走 5.1 登录完成首次绑定。
  - 若当前绑定设备（trim 后）与 **`deviceId` 相同**：返回错误 **`换绑失败，设备一致`**。
  - 否则将卡密绑定设备更新为新的 **`deviceId`**。
  - **扣时**：若换绑前**已有非空设备绑定**，则按应用在管理端配置的「卡密换绑扣时」（百分比或固定小时）从 **`expires_at` 扣减**；**管理员后台解绑设备/IP 不触发扣时**。原先无设备绑定（例如管理员解绑后为空）时，本次仅写入新设备，**不扣时**。无到期时间的卡密不扣时。
- 响应见 `CardRebindResponse`：`appId`、`cardId`、`cardCode`、`deviceId`、`expiresAt`、`available`、`variables`（与登录接口变量语义一致）。
- 限流策略与 5.1 登录相同（`appId + ip` 与 `appId + ip + cardCode`）。

### 5.2 仅应用公告（不含版本校验与更新信息）
- `POST /api/v1/app/announcement`
- 请求头、签名、`bodyDigest`、报文 **AES（`aes-default`）加解密** 与 5.1 及第 4 节一致。
- 解密后业务体须 **非空**（与空 canonical 会触发 `DECRYPT_EMPTY`）；推荐固定传 **`ping=1`** 作为占位字段，服务端不读取其值。
- 响应字段：
  - **`notice`**：管理端「应用公告」全文
  - **`currentVersion`** / **`minVersion`**：与服务端应用配置一致，便于客户端自行展示或比对
- **不做** `x.x.x` 区间校验，**不返回** `updateNotice`、`updateDownloadUrl`。若需要版本判断与更新包地址，请使用 5.3。

### 5.3 检查更新（版本、公告与安装包）
- **推荐** `POST /api/v1/app/update-check`
- **兼容** `POST /api/v1/app/notice`（与上者逻辑相同；**签名原文中的 PATH 必须与实际请求的 URL 路径一致**，迁移后请改用 `update-check`）
- 请求头、签名、`bodyDigest`、报文 **AES（`aes-default`）加解密** 与 5.1 及第 4 节一致。
- 解密后业务体带客户端 **`version`**。规则：
  - 若 **`version` 为三位数字段 `x.x.x`**（如 `1.0.0`，每段为非负整数）：与服务端应用配置的 **`min_version`（低）～`current_version`（高）** 做闭区间比较；仅当两端在库里也是合法 `x.x.x` 时才参与该侧边界；越界返回错误文案（过低/过高）。
  - 若 **不是** 上述格式：视为「按当前版本=三方所传」语义，**不做** 区间判断，直接返回公告数据。
- 响应见 `AppNoticeResponse`：
  - **`isLatestVersion`**：当客户端与服务的 `current_version` 均为合法 `x.x.x` 且相等时为 `true`，否则在无法比较时也为 `true`（仅返回软件公告）。
  - **`true`**：填充 **`notice`**（软件公告），`updateNotice` / `updateDownloadUrl` 为空。
  - **`false`**（客户端版本低于主线）：填充 **`updateNotice`**；若应用在库中配置了 **`update_file_storage_key`** 且 MinIO 默认桶内对象存在，则 **`updateDownloadUrl`** 为 **本服务** `GET /api/v1/app/update-package?ticket=...` 的完整 URL（短时 **ticket**，默认 **5 分钟**有效，HMAC 绑定 `appSecret`），否则为空。客户端可只代理你的后端域名，**无需直连 MinIO**。
- 若经网关暴露，建议配置 **`app.third-party.public-base-url`**（或环境变量 **`THIRD_PARTY_PUBLIC_BASE_URL`**）为对外前缀（无末尾 `/`），用于生成上述链接；不配则按当前请求的 Host/端口/ContextPath 拼接（请确保反向代理转发 `Host`/`X-Forwarded-*` 或显式配置此前缀）。

### 5.4 更新包下载（不走三方请求体签名）
- `GET /api/v1/app/update-package?ticket=<urlencode>`
- 使用 5.3 返回的 `ticket`；校验通过则从 MinIO 读取 `update_file_storage_key` 并以 `application/octet-stream` 附件下载。

### 5.5 查询应用变量
- `POST /api/v1/app/variables`
- 请求头、签名、`bodyDigest`、报文 **AES（`aes-default`）加解密** 与 5.1 及第 4 节一致。
- 解密后业务体须 **非空**；推荐固定传 **`ping=1`** 占位（与 5.2 相同）。
- 响应见 `AppVariablesResponse`：
  - **`variables`**：`变量名 → 值`。仅包含 **已启用** 且未删除的变量；`STRING`/`NUMBER`/`BOOLEAN`/`JSON`/`ARRAY` 类型解析规则与卡密登录响应中的 `variables` 一致。
- **无需卡密**：仅凭 `appKey` + 签名即可拉取（请自行评估是否需在业务层限制调用频率或改为登录后下发）。

## 6. WebSocket 用户登录（AUTH 明文载荷）

`AUTH` 的 AES-GCM 解密后为 JSON，必填字段包括：

- `appKey`、`appSig`、`username`、`password`、`ts`、`nonce`、`seq`（与既有规则一致）
- **`deviceId`**：设备唯一标识，**同一应用下同一用户不可重复**；用于 `app_user_binding` 与 `app_user.last_device_id`
- **`deviceName`**：设备名称（展示），最长 100 字符
- **`deviceOs`**：系统信息（如 `Windows 11`、`Android 14`），最长 50 字符  

以上三项 **不参与** `appSig` 拼串。缺失或超长时服务端关闭连接，原因 `BAD_DEVICE`。  

登录成功且账号密码校验通过后，服务端会校验 **`app_user.member_expires_at`（会员到期）**：

- 为 **null**（未开通会员）或 **不晚于当前时间**（已过期）时，立即关闭连接，关闭原因 **`MEMBER_EXPIRED`**（客户端可读 `CloseStatus.reason`），**不会**写入设备绑定、不会下发 `AUTH_OK`。

校验通过后，服务端会 **写入或更新** `app_user_binding`（`bind_type=ACCOUNT`）：刷新 `device_name` / `device_os` / `device_ip`、`last_active_at`、`use_count`；若该设备行曾被管理员软删/解绑，会 **恢复** 为有效绑定。若该 `device_id` 下已存在 **非 ACCOUNT** 绑定（如卡密 `LICENSE`），则返回 **`DEVICE_CONFLICT`**；设备封禁为 **`DEVICE_BANNED`**。

## 7. WebSocket 变量下发（HEARTBEAT）

在 `HELLO` / `AUTH` 建立 **32 字节 `sessionKey`** 后，服务端约每 5 秒推送 `type=HEARTBEAT`：

- **明文外层**：`connId`、`ts`、`varPacketSeq`（从 1 单调递增，每包不同）。
- **密文**：`cipher` 内 `AES-256-GCM`，与 AUTH 相同字段（`iv` / `data` / `tag`，Base64）。
- **每包子密钥**（与 `sessionKey` 分离，降低单钥泄露后的回放面）：

```text
subKey32 = HKDF-SHA256(
  ikm = sessionKey,
  salt = UTF8("cg-ws-var-packet-salt-v1"),
  info = UTF8("cg-ws-var-packet-v1|") + UTF8(String.valueOf(varPacketSeq)),
  length = 32
)
```

- **GCM AAD**（必须与字节一致）：`UTF8(connId + "|" + varPacketSeq + "|" + ts)`，其中 `ts` 为外层 JSON 的数值毫秒时间戳。

解密后的 **UTF-8 JSON** 结构（版本字段便于以后扩展）：

- `v`：当前为 `1`
- `ts`：毫秒时间戳
- `varPacketSeq`：与外层一致
- `variablesByTier`：对象，固定三个键 **`STANDARD`**、**`SENSITIVE`**、**`CRITICAL`**，值为「变量名 → 值」对象。变量分级来自管理端 `security_tier`（0/1/2）；**新建默认 2（CRITICAL）**；服务端若读到 `securityTier == null` 则按 **CRITICAL** 归桶（显式 `0` 仍为 STANDARD）。

**客户端与 TEE**：`sessionKey` 应在安全环境（如 Android Keystore / Keychain / TPM 绑定的密钥协商结果）中参与 HKDF 子密钥派生与 GCM 解密；**`CRITICAL` 桶内变量仅在 TEE 内解析与使用**，普通进程不应长期持有明文。TEE 由客户端平台实现，服务端仅通过分级字段表达语义。

---

## 8. 常见错误场景（建议对接时重点处理）
- WS `AUTH` 会员未开通或已过期：**`MEMBER_EXPIRED`**
- WS `AUTH` 设备字段缺失/超长：关闭码 **`BAD_DEVICE`**
- WS 设备与卡密等非账号绑定冲突：**`DEVICE_CONFLICT`**
- WS 设备已封禁：**`DEVICE_BANNED`**
- WS 设备绑定写入失败：**`BIND_FAIL`**
- 缺少头：`THIRD_PARTY_AUTH_MISSING`
- 时间戳非法/过期：`THIRD_PARTY_AUTH_BAD_TIMESTAMP` / `THIRD_PARTY_AUTH_EXPIRED`
- 重放：`THIRD_PARTY_AUTH_REPLAY`
- 应用不可用：`APP_DISABLED`
- 签名错误：`THIRD_PARTY_AUTH_BAD_SIGNATURE`
- 解密失败：`DECRYPT_EMPTY`（或插件抛异常导致 `THIRD_PARTY_AUTH_ERROR`）

## 9. 数据库变更（已有环境）

若库表早于「安全分级」字段，请执行：

- `src/main/resources/sql/alter_app_variable_security_tier.sql`
- `src/main/resources/sql/alter_app_user_last_device_id.sql`（终端用户 `last_device_id`）
- `src/main/resources/sql/alter_app_user_member_expires_at.sql`（终端用户 `member_expires_at` 会员到期）

