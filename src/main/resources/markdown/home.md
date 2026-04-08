# CipherGate API 文档

欢迎使用 CipherGate 企业级网络安全智能防护平台 API 文档！

## 📖 关于 CipherGate

CipherGate 是一个现代化的企业级网络安全解决方案，提供：

- 🔐 **智能身份验证** - 支持 OAuth2.0 和多种认证方式
- 🛡️ **权限管理** - 基于 RBAC 的细粒度权限控制
- 📊 **活动监控** - 实时监控用户活动和系统事件
- 🔔 **消息通知** - 多级别消息推送和通知系统
- ⚙️ **系统配置** - 灵活的系统配置和管理功能

## 🚀 快速开始

### 认证方式

本 API 使用 Session 认证方式，请先通过 OAuth2.0 登录获取会话。

### 基础 URL

- 开发环境: `http://localhost:8080/api`
- 生产环境: `https://api.ciphergate.com/api`

### 响应格式

所有 API 响应都遵循统一的格式：

```json
{
  "success": true,
  "data": {},
  "message": "操作成功"
}
```

## 📚 API 分组

- **用户管理** - 用户信息、角色、权限管理
- **系统管理** - 菜单、权限、配置管理
- **活动日志** - 用户活动记录和监控
- **消息通知** - 系统消息推送和管理
- **应用管理** - 应用配置和管理

## 💡 使用提示

1. 所有需要权限的接口都会在文档中标注所需权限
2. 请求参数支持 JSON 格式
3. 时间格式统一使用 ISO 8601 标准
4. 分页参数：`pageNum`（页码）和 `pageSize`（每页数量）

## 🔗 相关链接

- [GitHub 仓库](https://github.com/ayssu/ciphergate)
- [在线文档](https://docs.ciphergate.com)
- [问题反馈](https://github.com/ayssu/ciphergate/issues)

## 📧 联系我们

如有任何问题或建议，请联系：

- Email: contact@ciphergate.com
- GitHub: [@ayssu](https://github.com/ayssu)

---

**版本**: v1.0.0  
**更新时间**: 2024-04-07  
**许可证**: MIT License
