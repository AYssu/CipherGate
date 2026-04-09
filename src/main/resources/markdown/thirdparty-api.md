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
`bodyDigest = SHA256(解密后的明文 JSON 字符串)`（hex）

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
- 服务端会把 **请求头 + 原始请求体** 组装成一个 `Map` 交给应用绑定的 `encryptionPlugin` 去做解密。\n
- 你方只需要保证：**签名基于“解密后的明文 JSON”计算**。

## 5. 接口

### 5.1 卡密登录（唯一接口）
- `POST /api/v1/card/login`

请求体（解密后）：
- `cardCode`: 卡密（必填）
- `deviceId`: 设备码（必填）
- `ip`: 可选（用于 IP 校验）
- `appUserId`: 可选；不传时服务端会按卡密自动生成内部用户标识

响应：
- `appId`
- `userId` / `bindId`
- `expiresAt`: 卡密授权到期时间

说明：
- 首次登录会自动创建绑定并激活卡密（写入 `firstUsedAt`）。
- 后续登录会复用已存在绑定，只要未过期即可登录。
- 不返回登录 token，也不包含心跳接口。

## 6. 常见错误场景（建议对接时重点处理）
- 缺少头：`THIRD_PARTY_AUTH_MISSING`
- 时间戳非法/过期：`THIRD_PARTY_AUTH_BAD_TIMESTAMP` / `THIRD_PARTY_AUTH_EXPIRED`
- 重放：`THIRD_PARTY_AUTH_REPLAY`
- 应用不可用：`APP_DISABLED`
- 签名错误：`THIRD_PARTY_AUTH_BAD_SIGNATURE`
- 解密失败：`DECRYPT_EMPTY`（或插件抛异常导致 `THIRD_PARTY_AUTH_ERROR`）

