# CipherGate API 文档

欢迎使用 CipherGate 企业级授权与安全管控平台 OpenAPI 文档。

## 功能范围

- **身份认证**：GitHub OAuth2、密码降级登录、用户门户 JWT
- **用户与权限**：用户、角色、菜单、权限、活动日志、仪表盘
- **应用与授权**：应用、终端用户、卡密、变量、代理与配额
- **商业能力**：会员、套餐、支付、订单、工单
- **平台能力**：插件、文件上传、文档中心、公告、系统配置
- **对外集成**：公开注册/查询、用户门户、第三方签名接口

## 基础地址

- 开发环境：`http://localhost:8080`
- 生产环境：`https://www.ayssu.com`
- 接口路径已包含 `/api` 前缀，例如：`https://www.ayssu.com/api/users`

## 认证方式

1. **管理端接口**：使用 `CIPHERGATE_SESSION` Cookie。
2. **用户门户 `/api/portal/**`**：

   ```http
   Authorization: Bearer <JWT>
   ```

3. **第三方 `/api/v1/**`**：通常使用以下四个请求头：

   ```http
   X-App-Key: <appKey>
   X-Timestamp: <millisecond timestamp>
   X-Nonce: <random nonce>
   X-Signature: <HMAC-SHA256 hex>
   ```

   更新包下载、特殊充值等接口使用各自说明的票据或请求体凭证。

4. 登录、注册、公开查询、初始化和支付回调等公开接口无需认证。

## 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "success": true,
  "timestamp": "2026-09-30 12:00:00"
}
```

常见状态码：

- `200`：成功
- `400`：参数错误
- `401`：未登录或认证失败
- `403`：权限不足
- `404`：资源不存在
- `500`：服务端错误

## API 分组

- 认证与个人中心
- 用户与权限
- 系统管理
- 仪表盘与日志
- 消息、公告与文档
- 应用管理与应用变量
- 终端用户与卡密
- 插件与功能模块
- 会员与配额
- 支付与工单
- 用户门户
- 第三方管理与第三方协议
- 公开与自助接口
- 文件上传
- 运维与调试

## 使用提示

- 列表接口的分页参数以各接口说明为准，常见为 `page/size` 或 `pageNum/pageSize`。
- 请求体默认使用 JSON，文件上传接口使用 `multipart/form-data`。
- 第三方接口必须使用 HTTPS，并严格遵守时间戳、Nonce 和签名规则。
- `/doc.html`、`/v3/api-docs` 和 `/swagger-ui` 仅超级管理员可访问。

## 相关链接

- [项目仓库](https://github.com/AYssu/CipherGate)
- [问题反馈](https://github.com/AYssu/CipherGate/issues)
- [在线 API 文档](https://www.ayssu.com/doc.html)

---

**版本**：v1.0.0

**更新时间**：2026-09-30
