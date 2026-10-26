# 第三方凭证

第三方凭证用于授权外部服务为指定应用的终端用户充值加时，以及经系统管理员开通的试用申请和授权查询。每条凭证包含独立的 `apiKey` 和 `apiSecret`，可授权一个或多个应用。它与应用 AppKey/AppSecret、更新包下载 ticket 相互独立。

## 创建与轮换

在「应用管理 → 第三方凭证」中创建凭证，选择授权应用，填写名称、允许 IP、调用和充值天数限额、过期时间及备注。创建成功后立即保存 API Secret；列表、详情和普通编辑不会再次返回 Secret。

API Secret 支持轮换。轮换后旧 Secret 失效，调用方必须更新签名密钥。

## 限制项

| 限制 | 说明 |
| --- | --- |
| 允许 IP | 逗号分隔的精确 IP 地址列表；为空时不限制，不按 CIDR 网段匹配 |
| 日调用上限 | 每个自然日允许的调用次数 |
| 总调用上限 | 凭证生命周期内允许的总次数 |
| 总天数上限 | 同一凭证累计可充值的天数，不是凭证的有效天数 |
| 过期时间 | 到期后凭证不可用 |
| 状态 | 启用或停用 |

## 管理权限

操作需要对应的 `THIRD_PARTY_CREDENTIAL_LIST`、`DETAIL`、`CREATE`、`UPDATE` 或 `DELETE` 权限（完整权限名前缀均为 `THIRD_PARTY_CREDENTIAL_`）。普通用户还必须拥有完整授权应用集合；管理员可跨所有者管理。应用代理授权不等于充值凭证管理权限。

## 调用记录

凭证相关请求会记录到 [调用日志](/developer/call-log)，包含请求 IP、签名结果、幂等命中、错误码、变更前后到期时间和 traceId。

::: warning
API Secret 是长期凭证，只能保存在服务端安全配置中。不要写入前端代码、公开仓库或客户端安装包。
:::

## 一组充值凭证授权多个应用

此处是第三方充值凭证（`tpk_…` / `tps_…`），不是应用的 App Key，也不改变用户登录接口。

在**三方凭证管理 → 新增凭证 / 编辑**中，通过「授权应用」搜索并选择一个或多个应用（最多 100 个）。列表和移动端卡片会显示完整绑定集合；按应用筛选时，只要凭证包含该应用，就可以查到。

- **一次请求只充值一个应用。** 充值接口的 `projectKey` 是目标应用 ID 的 Base64 编码。后端只在该应用内查找 `userEmail`，不会同步给其他应用里相同邮箱的用户加时。
- **未绑定应用仍拒绝。** 即使签名正确，也不能充值授权范围外的应用。
- **额度按整组 Key 共享。** 每日调用量、累计调用量和累计消费天数不会因绑定多个应用而增加额度。
- **订单号按整组 Key 去重。** 如果要分别给两个应用充值，需要发起两次请求，使用不同的 `outTradeNo`；同一 Key 下复用订单号会被视为已处理。
- **修改绑定不重置 Key / Secret。** 添加应用后，调用方继续使用原签名密钥，只需指定相应 `projectKey`。移除应用后，该应用不能继续使用此凭证充值。
- **管理权限覆盖完整集合。** 普通应用所有者只能管理全部绑定应用都属于自己的凭证。跨所有者的共享凭证由系统管理员管理；调用日志仍按目标应用权限隔离。

### 兼容旧凭证与管理接口

升级时创建授权关联表，不重新生成现有凭证。尚未编辑绑定的旧凭证，仍只授权原 `appId`，不会自动扩大范围。

管理接口创建/编辑可提交 `appIds` 数组，例如：

```json
{
  "name": "商城充值服务",
  "appIds": [10, 20],
  "status": 1
}
```

`appIds` 表示**完整绑定集合**，不是追加列表；空数组、无效 ID 或无权管理的应用会被拒绝。详情和列表返回 `appIds`，并保留 `appId` 作为旧版单应用兼容字段。新客户端应按 `appIds` 展示授权范围。

旧版创建请求只传 `appId` 时仍创建单应用凭证。编辑不传绑定字段，或旧表单仅回传原 `appId` 时，保留原集合；新客户端要改绑定，应明确提交 `appIds`。

API Secret 仅在创建和主动轮换成功时返回一次，列表、详情和普通编辑不返回。创建后请立即保存；普通编辑不会清除或重置 Secret。

### 区分应用授权与签名错误

| 调用日志错误码 | 含义 | 处理方式 |
| --- | --- | --- |
| `APP_DENIED` | 此充值 Key 未授权目标应用 | 在凭证编辑中添加正确应用，核对 `projectKey` |
| `BAD_PROJECT_KEY` | 无法解析目标应用 ID | 使用目标应用数字 ID 的 Base64 编码，不是应用 App Key |
| `BAD_SIGN` | 签名与服务端校验不一致 | 核对当前 Secret、签名字段、排序与 MD5 计算；多应用授权不会跳过签名 |

应用授权检查先于签名检查，因此授权被拒绝的日志可能显示「签名未通过/未验证」。应结合错误码判断，不能仅凭签名列认定 Secret 错误。

## 充值接口与签名

```text
POST /api/v1/third_party/recharge
Content-Type: application/json
```

该接口使用请求体中的 `apiKey`、`timestamp` 和 `sign` 鉴权，不使用应用客户端接口的 `X-App-Key` / `X-Signature` 请求头，也不发送默认 AES 的 `data` 密文信封。生产调用应通过 HTTPS。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `apiKey` | 是 | 第三方充值凭证的 API Key |
| `userEmail` | 是 | 目标应用中已有终端用户的邮箱 |
| `projectKey` | 是 | 目标应用数字 ID 的标准 Base64 编码，例如字符串 `10` 编码为 `MTA=` |
| `days` | 是 | 整数充值天数，至少为 1 |
| `timestamp` | 是 | 毫秒时间戳，与服务器相差不超过 5 分钟 |
| `sign` | 是 | 下述签名原文的 MD5 十六进制摘要，服务端比较时不区分大小写 |
| `outTradeNo` | 否 | 商户订单号；建议填写，作为同一凭证范围内的幂等标识 |

按下列**固定字段顺序**生成签名原文，不要复用其他接口的 HMAC 格式：

```text
apiKey=<apiKey>&days=<days>&projectKey=<projectKey>&timestamp=<timestamp>&userEmail=<userEmail>&secret=<apiSecret>
```

`apiKey`、`projectKey`、`userEmail` 和 `apiSecret` 在签名前去除首尾空白。按 UTF-8 计算该原文的 MD5；`outTradeNo` 不参与当前签名。充值只作用于目标应用内指定邮箱的账号；返回业务结果应检查响应 `code` / `success`，不能仅按 HTTP 200 判断充值成功。

建议联调顺序：先校准时间与目标应用 ID，再确认凭证授权集合和 IP 白名单，最后核对签名及限额。成功后查询目标用户的会员到期时间，并在 [调用日志](/developer/call-log)按订单号核对一次处理结果。网络异常时用同一 `outTradeNo` 核对幂等结果，不要生成新订单号盲目重充。

## 凭证申请试用与查询授权

机器人、商城等服务端集成可复用此处创建的独立凭证。**系统管理员**在同一凭证编辑页按需开启「申请试用」「查询授权」，并填写精确 IP 白名单；应用所有者不能自行开启或修改已含这些权限的凭证。无需新建菜单，也不使用应用 AppKey/AppSecret。

原有凭证升级后两个开关均关闭，充值权限与旧签名保持不变。新接口必须同时满足：凭证启用且未过期、目标应用属于授权集合、对应接口已开启、来源 IP 命中非空白名单、签名正确、随机串未重放及调用额度允许。新权限作用于这条凭证的整个授权应用集合；不同应用需要不同权限时请拆分凭证。

| 操作 | 请求地址 | 签名操作值 |
| --- | --- | --- |
| 申请试用 | `POST /api/v1/third_party/trial` | `TRIAL_APPLY` |
| 查询授权 | `POST /api/v1/third_party/authorization` | `AUTH_QUERY` |

请求为普通 JSON，经 HTTPS 发送，无需 AES 信封、`X-App-Key`、用户密码或图形验证码。

| 字段 | 规则 |
| --- | --- |
| `apiKey` | 当前第三方凭证 API Key |
| `appId` | 正整数应用 ID，直接使用数字，无需 Base64 |
| `userEmail` | 目标应用中已存在用户的邮箱，签名前去除首尾空格并转小写 |
| `timestamp` | 毫秒时间戳，服务端允许前后5分钟偏差 |
| `nonce` | 每次请求新生成的16～64位字母、数字、下划线或短横线；推荐UUID十六进制 |
| `sign` | HMAC-SHA256 十六进制摘要，使用当前 API Secret 作为密钥 |

按以下固定顺序拼接，以单个 LF（`\n`）分隔，最后一行后**不加换行**：

```text
操作值
apiKey
appId
userEmail
timestamp
nonce
```

签名绑定操作与应用，查询签名不能用于申请试用。随机串在同一凭证范围内一次性使用，至少保留11分钟；超时重试请生成新随机串重新签名，试用的数据库唯一约束仍保证一个用户在同一应用只能领取一次。

### Python 机器人调用示例

```python
import hashlib
import hmac
import os
import time
import uuid
import httpx

async def query_authorization(app_id: int, email: str):
    action = "AUTH_QUERY"
    body = {
        "apiKey": os.environ["CIPHERGATE_API_KEY"].strip(),
        "appId": app_id,
        "userEmail": email.strip().lower(),
        "timestamp": time.time_ns() // 1_000_000,
        "nonce": uuid.uuid4().hex,
    }
    text = "\n".join(str(value) for value in (
        action, body["apiKey"], body["appId"], body["userEmail"],
        body["timestamp"], body["nonce"],
    ))
    body["sign"] = hmac.new(
        os.environ["CIPHERGATE_API_SECRET"].encode("utf-8"),
        text.encode("utf-8"), hashlib.sha256,
    ).hexdigest()
    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.post(
            "https://你的域名/api/v1/third_party/authorization", json=body,
        )
        response.raise_for_status()
        result = response.json()
        if not result.get("success"):
            raise RuntimeError(result.get("message", "查询失败"))
        return result["data"]  # appId、expiresAt；没有会员时 expiresAt 可以为空
```

申请试用使用同一封装，把操作值改成 `TRIAL_APPLY`、地址改为 `/api/v1/third_party/trial`，并重新生成随机串及签名。成功返回 `appId`、`trialDays`、`expiresAt`，不返回密码、用户内部ID或其他应用信息。

试用仍要求应用启用并选择试用业务模式，目标用户事先存在且未领过试用；有效会员从原到期时间顺延，已过期则从当前时间起加1天。查询授权也要求应用启用。

### 额度、IP与调用日志

新接口的成功请求与充值共用凭证的日调用及总调用额度；失败请求不消耗成功调用额度，但仍受每IP、每凭证每分钟120次的防刷限制。新接口不使用充值消费天数额度；申请试用受每应用每用户一次的独立限制。

IP 只读取经过可信代理规范化的 `remoteAddr`。白名单仅支持精确 IPv4/IPv6 地址，不支持域名、CIDR或通配符；IPv6不同文本写法会规范化比较。**新接口白名单为空时拒绝，原充值的空白名单语义不变。** 代理与源站信任链需要正确配置，不能直接相信调用方自填的转发头。

现有「调用日志」新增调用接口类型，记录来源 IP、签名结果、失败原因、凭证不可逆短指纹与 traceId。未知凭证、IP不匹配及签名失败也会记录。无法验证目标应用归属的拒绝记录只供有调用日志权限的系统管理员查看，避免伪造请求污染其他应用所有者的日志；成功签名后的应用内记录仍按原应用权限隔离。日志不保存 Secret、签名或完整请求体。

| HTTP 状态 / 日志错误码 | 含义 |
| --- | --- |
| `400 / INVALID_REQUEST` | 字段缺失或格式错误、目标用户不存在 |
| `401 / BAD_API_KEY`、`BAD_SIGN`、`TIMESTAMP_EXPIRED` | 凭证、签名或时间戳无效 |
| `403 / OPERATION_DENIED`、`IP_DENIED`、`APP_DENIED` | 接口未开通、IP不匹配或应用未授权 |
| `403 / CREDENTIAL_EXPIRED`、`APP_UNAVAILABLE` | 凭证过期、应用不存在或禁用 |
| `409 / REPLAY`、`BUSINESS_CONFLICT` | 随机串重复、重复领试用或试用不可用 |
| `429 / RATE_LIMITED`、`DAILY_LIMIT_EXCEEDED`、`TOTAL_CALL_LIMIT_EXCEEDED` | 来源限流或额度耗尽 |
| `503 / SYSTEM_ERROR`、`LIMIT_UNAVAILABLE` | 服务暂不可用 |

原 `/api/open/trial` 和 `/api/open/trial/expires-at` 的密码与图形挑战规则继续保留。机器人应改用上述凭证端点，不能去掉原公开端点的安全校验。

### 升级与撤销

Flyway V4新增两个默认关闭的权限列及调用类型列，历史充值日志默认显示「充值加时」，不轮换已有密钥或修改充值签名。撤销任一开关、禁用/过期凭证、修改授权应用或轮换 Secret 均会限制后续调用。已有新权限的凭证修改、轮换、删除仅系统管理员可执行，防止普通所有者通过改IP或扩展绑定绕过管理员授权。
