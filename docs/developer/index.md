# 开发者中心

开发者中心用于维护应用及其授权数据。登录后从 `/dashboard` 进入，核心菜单包括：

| 菜单 | 路由 | 用途 |
| --- | --- | --- |
| 应用管理 | `/applications/list` | 应用基础信息、密钥、版本、加密、代理和门户支付配置 |
| 卡密管理 | `/applications/licenses` | 单个或批量生成、激活、绑定、加时和解绑 |
| 终端用户管理 | `/applications/users` | 账号用户、会员到期、设备绑定、在线状态和封禁 |
| 变量管理 | `/applications/variables` | 应用变量、安全等级、版本、历史、导入导出 |
| 第三方凭证 | `/applications/credentials` | API Key/Secret、IP 白名单、调用限额和有效期 |
| 调用日志 | `/applications/call-logs` | 第三方充值请求、签名校验、幂等和错误记录 |

## 推荐配置顺序

1. 创建应用并保存生成的 AppKey/AppSecret。
2. 选择加密插件，按插件模板填写加密配置。
3. 配置版本号、公告、更新包和换绑策略。
4. 创建卡密或终端用户。
5. 创建第三方凭证并设置 IP、日调用、总调用和有效期限制。
6. 使用 [API 对接文档](/developer/api-integration) 完成签名、加密和联调。

## 权限说明

开发者只能查看其有权访问的应用数据。应用代理可被限制为只能管理自己创建的数据，或查看应用全部数据；具体可见 [应用管理](/developer/application)。
