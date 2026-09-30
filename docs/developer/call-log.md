# 调用日志

调用日志当前记录第三方充值请求，用于定位签名、限流、幂等和业务失败。

## 可筛选字段

- 应用。
- 第三方凭证。
- 用户邮箱。
- 请求状态和错误码。
- 商户订单号。
- 请求 IP。
- 请求时间范围。

## 日志字段

| 字段 | 用途 |
| --- | --- |
| `signValid` | 签名是否验证通过 |
| `idempotentHit` | 是否命中幂等记录 |
| `errorCode/errorMessage` | 失败原因 |
| `beforeExpiresAt/afterExpiresAt` | 充值前后会员到期时间 |
| `requestTs` | 请求方时间戳 |
| `traceId` | 关联前后端或跨服务日志 |

排查问题时先用订单号或 traceId 定位记录，再核对请求 IP、凭证限制和错误信息。
