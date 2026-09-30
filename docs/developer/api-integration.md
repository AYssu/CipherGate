# 第三方 API 对接

第三方接口以 `/api/v1` 为前缀，要求 HTTPS、HMAC-SHA256 签名、时间戳/nonce 防重放，并按应用配置进行报文加密。

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

1. 业务明文按 key 字典序拼成 `key=value&key2=value2`。
2. 使用应用 `encryptionConfig` 中的 AES 密钥加密。
3. 请求体 JSON 根级 `data` 字段保存 HEX 密文。
4. 响应的 `data` 同样返回 HEX 密文。

生产环境必须在应用加密配置中设置密钥，不要依赖本地默认密钥。

## HTTP 接口

| 接口 | 用途 |
| --- | --- |
| `POST /api/v1/card/login` | 卡密登录、首次激活和设备绑定 |
| `POST /api/v1/card/rebind` | 卡密换绑设备，可能按应用配置扣时 |
| `POST /api/v1/app/announcement` | 仅获取应用公告和版本 |
| `POST /api/v1/app/update-check` | 检查版本、公告、更新说明和下载凭据 |
| `POST /api/v1/app/update-download` | 使用一次性 ticket 下载更新包 |
| `POST /api/v1/app/variables` | 查询应用已启用变量 |

卡密登录解密后的字段包括 `cardCode`、`deviceId`、可选 `ip` 和 `appUserId`；响应包含卡密状态、绑定数、在线状态、到期时间和 `variables`。

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
