# 第三方 API 对接

应用客户端接口以 `/api/v1` 为前缀，使用应用 AppKey/AppSecret 完成 HMAC-SHA256 签名、时间戳/nonce 防重放，并按应用配置加密报文。生产接入应使用 HTTPS。

以下公共请求头适用于卡密、账号登录、公告、更新检查和变量查询。更新包下载使用短时 ticket；[第三方充值凭证](/developer/credential#充值接口与签名)使用请求体中的 API Key/Secret 签名，二者不使用这些公共请求头。

## 公共请求头

| 请求头 | 说明 |
| --- | --- |
| `X-App-Key` | 应用 AppKey |
| `X-Timestamp` | 毫秒时间戳 |
| `X-Nonce` | 每次请求唯一的随机串 |
| `X-Signature` | HMAC-SHA256 签名，十六进制 |

签名原文按以下格式拼接，换行符必须是 `\n`：

```text
METHOD
PATH
TIMESTAMP
NONCE
bodyDigest
```

`bodyDigest` 是原始 HTTP 请求体字节的 SHA-256 十六进制值。即使请求体中的 `data` 是密文，签名仍按实际发送的原始 body 计算。

```text
X-Signature = HMAC-SHA256(appSecret, signString)
```

## 报文加密

服务端把请求头和原始请求体交给应用绑定的加密插件。默认 `aes-default` 使用 AES/ECB/PKCS5Padding：

1. 原有业务明文按 key 字典序拼成 `key=value&key2=value2`；新版默认插件也接受 JSON 对象明文，账号密码登录建议使用 JSON，避免密码中的 `&` 等字符被拆分。
2. 使用应用 `encryptionConfig` 中的 AES 密钥加密。
3. 请求体 JSON 根级 `data` 字段保存 HEX 密文。
4. 响应的 `data` 同样返回 HEX 密文。

生产环境必须在应用加密配置中设置密钥，不要依赖本地默认密钥。

## HTTP 接口

| 接口 | 用途 |
| --- | --- |
| `POST /api/v1/card/login` | 卡密登录、首次激活和设备绑定 |
| `POST /api/v1/user/login` | 用户名或邮箱 + 密码登录、账号设备绑定，不依赖 WS |
| `POST /api/v1/card/rebind` | 卡密换绑设备，可能按应用配置扣时 |
| `POST /api/v1/app/announcement` | 仅获取应用公告和版本 |
| `POST /api/v1/app/update-check` | 检查版本、公告、更新说明和下载凭据 |
| `GET /api/v1/app/update-package?ticket=…` | 使用短时 ticket 下载更新包，不使用公共签名头 |
| `POST /api/v1/app/variables` | 查询应用已启用变量 |

卡密登录解密后的字段包括 `cardCode`、`deviceId`、可选 `ip` 和 `appUserId`；响应包含卡密状态、绑定数、在线状态、到期时间和 `variables`。

### 仅登录客户端与免费访客

登录成功响应包含两个兼容字段，旧客户端可以忽略，不改变原请求格式和卡密授权判断：

| 响应字段 | 卡密模式 | 免费模式 |
| --- | --- | --- |
| `identityType` | `CARD` | `VISITOR` |
| `identityId` | `card_` + 原有卡密 ID | `visitor_` + 应用隔离的设备摘要 |
| `cardId` | 保留真实卡密 ID | 保留原有 `0` |
| `token` | 保留原心跳 token，可按客户端需要接入 | 保留原有空值，不强制接入心跳 |

免费模式仍只需有效的 `deviceId`，不要求填写卡密或账号密码。同一应用和设备再次登录会关联到相同访客身份；来源 IP 改变不产生新身份，不同应用互相隔离。服务端记录设备标识的 SHA-256 摘要，不在访问流水中保存原始设备标识。

统计身份仅用于数据展示和去重，**不能用作卡密、心跳 token、用户登录凭证或付费授权**。只调用登录接口也能在首页查看登录次数、活跃卡密、免费访客和设备数据。没有客户端心跳时只能判断近期登录活跃，不能证明持续在线。原有 `online` 响应字段保留兼容口径：卡密按最近使用时间估计，免费模式仍返回 `false`。

## 更新检查与下载

先在 [应用管理](/developer/application#版本、公告与更新包)设置当前版本、最低版本、更新说明并上传更新包，再调用 `POST /api/v1/app/update-check`。解密后的请求字段 `version` 是客户端版本字符串。

| 响应字段 | 说明 |
| --- | --- |
| `isLatestVersion` | 客户端是否与当前版本一致；无法按三段数字比较时也按已对齐处理 |
| `clientVersion`、`currentVersion`、`minVersion` | 客户端版本、当前版本和最低版本 |
| `versionRangeChecked` | 本次是否按三段数字版本执行区间检查 |
| `notice` | 已对齐时返回应用公告 |
| `updateNotice`、`updateDownloadUrl` | 客户端落后当前版本时返回更新说明及可用的临时下载地址 |

版本为 `x.x.x` 三段数字时，服务端检查闭区间 `[minVersion, currentVersion]`；已配置为合法版本的上下界分别生效。低于最低版本或高于当前版本会返回业务失败，不能把失败响应当作带下载地址的更新提示。版本未提供或不满足三段数字格式时，不执行该区间比较，按已对齐返回公告。

取得 `updateDownloadUrl` 后直接发起 GET：

```text
GET /api/v1/app/update-package?ticket=<检查更新返回的票据>
```

- ticket 默认 **5 分钟**有效，绑定应用并使用其 AppSecret 验签；有效期内可重复使用，**不是一次性票据**。
- 下载不需要 `X-App-Key` / `X-Signature`，响应是安装包字节流，不是加密 JSON。不要将下载地址或 ticket 写入公开日志。
- 没有上传文件或对象不存在时，更新检查的下载地址为空。票据无效、过期、应用停用或流量额度不足可导致下载 HTTP 403；文件不存在返回 404。
- 下载由 CipherGate 后端中转，地址不指向公开对象桶。该接口会检查并计入应用及所有者的流量额度。

临时地址优先使用 `THIRD_PARTY_PUBLIC_BASE_URL`；未设置时按后端收到的请求推断。HTTPS 反向代理可能以 HTTP 回源，不能仅靠开放 443 端口保证地址正确。部署时在私有 `.env` 设置实际公开地址，并确认 Compose 将变量传入后端后重建后端服务：

```dotenv
THIRD_PARTY_PUBLIC_BASE_URL=https://your-domain.example
```

该配置指定下载地址的公开源站，不提供证书或 HTTPS 监听。客户端如果只接受 HTTPS，应核对接口实际返回的 `updateDownloadUrl` 及公网证书；后台已登录用户的更新包下载使用另一条 [管理接口](/developer/application#上传与后台下载)。

## HTTP 账号密码登录

`POST /api/v1/user/login` 登录的是当前应用的**终端用户**，不是管理后台用户，也不是 GitHub 等 OAuth 登录。应用由 `X-App-Key` 确定，请求不能通过传入 `appId` 切换应用。

### 请求字段（加密前）

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `username` | 是 | 用户名或邮箱，去除首尾空白后最多 255 字符；与 WS 共用账号校验 |
| `password` | 是 | 用户原始密码，最多 256 字符；不 trim、不预先做 MD5/SHA 摘要 |
| `deviceId` | 是 | 稳定设备标识，去除首尾空白后最多 255 字符 |
| `deviceName` | 否 | 设备名称，最多 100 字符 |
| `deviceOs` | 否 | 设备系统，最多 50 字符 |

以下是**加密前的示例**，不能直接作为正式请求体发送：

```json
{
  "username": "alice",
  "password": "example&password=with spaces",
  "deviceId": "device-001",
  "deviceName": "Desktop",
  "deviceOs": "Linux"
}
```

使用 `aes-default` 时，把上述 JSON 的 UTF-8 字节用应用配置的 AES 密钥加密为 HEX；算法仍是 AES/ECB/PKCS5Padding。实际 HTTP 请求体仅发送：

```json
{"data":"加密后的HEX字符串"}
```

**不要在密文旁边再放明文密码。** 随后按实际发送的整个 body 字节计算 SHA-256，再生成公共请求头中的 HMAC 签名；签名路径必须是 `/api/v1/user/login`。每次请求更换 nonce，毫秒时间戳需与服务器相差不超过 15 秒。

JSON 明文是新版默认插件的兼容扩展，新账号登录的成功响应也采用 JSON 明文，避免昵称或变量中的 `&` 被误解析成授权字段。原有卡密接口的 canonical 明文和响应编码未改变；应用选择其他加密插件时，遵循该插件的协议，不强制改为默认 AES，也不采用 WS 的 AES-GCM 握手。

### 授权与设备规则

- 用户名/邮箱与密码必须匹配当前应用的未删除用户；密码在服务端按已有 BCrypt 规则验证。
- 收费应用必须有尚未到期的会员，未开通或已过期返回 `MEMBER_EXPIRED`。
- 免费应用仍然校验账号密码和设备，但不强制要求会员。匿名免费访客继续使用原来的 `/api/v1/card/login`，不需要迁移成账号。
- 与 WS 共用 `ACCOUNT` 设备绑定：被封禁设备返回 `DEVICE_BANNED`；同一用户设备已有不兼容的卡密绑定返回 `DEVICE_CONFLICT`。软删除记录按既有规则检查并恢复，不能绕过设备封禁。
- 成功后累计账号登录次数、更新最后登录信息，并记录 HTTP 用户访问流水；不建立或踢出 WS 会话。

### 响应字段（解密后）

外层结构、响应加密和 `X-Resp-Timestamp` / `X-Resp-Nonce` / `X-Resp-Signature` 与卡密接口一致。外层 `data` 是 `{ "data": "HEX密文", "pluginId": "aes-default" }`，客户端需验签并解密其中的密文。

| 字段 | 说明 |
| --- | --- |
| `appId`、`userId` | 当前应用及终端用户 ID |
| `username`、`nickname` | 用户名、昵称，不返回密码/密码哈希或应用密钥 |
| `memberRequired` | 是否要求有效会员；免费应用为 `false` |
| `expiresAt` | 用户实际会员到期时间，epoch **毫秒**；没有会员时为空，不虚构永久到期时间 |
| `available` | 收费应用会员剩余**秒数**；免费应用为空，不能因此判定登录失败 |
| `loginCount`、`lastLoginAt` | 包含本次成功登录的累计次数、最后登录时间（epoch 毫秒） |
| `bindingCount` | 当前用户未删除的设备绑定数量 |
| `variables` | 启用变量组成的 JSON **字符串**，与卡密响应一致，支持用户上下文模板 |
| `online` | 成功登录为 `true`，表示 HTTP 近期活跃（5 分钟），不表示 WS 连接或持续在线 |
| `token` | 空值；不签发心跳 token、Portal JWT 或后台登录凭证 |
| `identityType`、`identityId` | `USER`、`user_` + 用户 ID，仅用于统计展示和去重 |

新账号接口在默认 AES 下的成功响应解密后是 **JSON 对象**，数字和布尔值保留类型，`variables` 仍是 JSON 字符串；空字段可能为 `null` 或被省略。这与旧卡密的 `key=value&...` 明文不同，但 AES 算法、密钥来源、HEX 密文与外层封装不变。业务失败时仍沿用公共的空加密载荷，通过外层 `code` / `message` 判断。`identityId` 不是后续接口的身份凭证，不能用它绕过账号或会员验证。

### 错误与统计

| 业务 `code` | 含义 |
| --- | --- |
| `400` | 必填字段缺失或超长 |
| `401` | 用户名/邮箱或密码错误（统一提示，避免泄露账号是否存在） |
| `403` | 应用停用、会员过期、设备封禁或绑定冲突 |
| `429` | 登录尝试过频：同应用同来源 IP 每分钟最多 120 次，同来源同账号每分钟最多 20 次 |
| `500` | 服务暂不可用，不返回内部异常或敏感参数 |

控制器业务结果沿用卡密接口的 `Result`，应同时检查 `success` 与 `code`，不能仅看 HTTP 状态。签名缺失、过期、重放等会在鉴权过滤器直接返回 HTTP 401/403 等错误；未通过过滤器的响应不属于登录成功的加密结构。

首页「登录与访客统计」展示 HTTP 用户登录次数、今日/7 日活跃用户与最近 5 分钟用户数，并区分 `USER` / `CARD` / `VISITOR`。同一账号重复登录累计次数，活跃用户按应用及用户 ID 去重；HTTP 登录计入综合在线用户（5 分钟窗口），同一用户同时有 WS 和 HTTP 活跃仅计一次；不增加 WS 会话数或连续在线时长。

::: warning 一次性校验不是持续在线授权
本接口只证明登录这一时刻的账号、会员和设备校验通过。客户端没有心跳时，服务端无法确认其是否仍在运行，也无法主动让离线客户端立刻感知会员到期或封禁；需要持续在线与主动断开能力时，仍应接入 WS。
:::

## WebSocket 登录

`AUTH` 载荷经 AES-GCM 解密后必须包含 `appKey`、`appSig`、`username`、`password`、`ts`、`nonce`、`seq`、`deviceId`，并可包含 `deviceName` 和 `deviceOs`。

常见关闭原因：

| 原因 | 处理方式 |
| --- | --- |
| `MEMBER_EXPIRED` | 为用户开通或延长会员 |
| `BAD_DEVICE` | 补齐或缩短设备字段 |
| `DEVICE_CONFLICT` | 解除卡密等其他类型的设备绑定 |
| `DEVICE_BANNED` | 联系管理员解除设备封禁 |
| `BIND_FAIL` | 稍后重试并提供 traceId |

登录成功后，服务端约每 5 秒推送 `HEARTBEAT` 变量包。变量包使用独立 HKDF 子密钥和 AES-256-GCM，按安全等级分为 `STANDARD`、`SENSITIVE`、`CRITICAL` 三组。

## 公共错误

`THIRD_PARTY_AUTH_MISSING`、`THIRD_PARTY_AUTH_BAD_TIMESTAMP`、`THIRD_PARTY_AUTH_EXPIRED`、`THIRD_PARTY_AUTH_REPLAY`、`APP_DISABLED`、`THIRD_PARTY_AUTH_BAD_SIGNATURE` 和 `DECRYPT_EMPTY` 是接入阶段最常见的错误。服务端启用的接口可从 `/doc.html` 查看完整 Schema；该入口仅管理员可访问。

### HTTP 用户近期在线与客户端兼容

- 用户列表的 `online` 为 WS 在线或 HTTP 最近 5 分钟成功登录；`httpOnline` 仅表示后者，`wsOnline` 仍只表示真实 WS 会话。
- 使用 `online=true/false` 筛选综合状态，旧 `wsOnline` 筛选保留原语义。列表状态在后续查询/刷新时更新，历史登录响应不会自动改变。
- HTTP 窗口为 `(当前时间 - 5 分钟, 当前时间]`；再次成功登录刷新窗口，失败请求、普通查询不续期。应用停用/删除、付费会员过期或存在已封禁设备绑定时不再显示 HTTP 近期在线。
- 在线统计按用户去重；HTTP 活跃不是连接，不产生在线时长，不签发心跳 token，不顶掉 WS。
- 新客户端应读取布尔 `online`，同时兼容旧服务器的 `false`，不能以 `online` 决定认证成功与否。旧 core-fs / SeaAlpha HTTP 用户客户端拒绝 `online=true`，须先更新客户端解析再切换后端。
