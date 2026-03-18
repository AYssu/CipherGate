# GitHub OAuth2 可获取的信息和权限

## 🔍 当前配置的权限

```yaml
scope:
  - user:email    # 访问用户邮箱信息
  - read:user     # 读取用户基本信息
```

## 📊 基本用户信息（无需额外权限）

通过 `https://api.github.com/user` 可获取：

### 公开信息
- `id` - GitHub 用户 ID
- `login` - 用户名
- `name` - 真实姓名
- `avatar_url` - 头像 URL
- `html_url` - GitHub 个人页面
- `company` - 公司
- `blog` - 个人网站
- `location` - 地理位置
- `bio` - 个人简介
- `twitter_username` - Twitter 用户名
- `public_repos` - 公开仓库数
- `public_gists` - 公开 Gist 数
- `followers` - 关注者数
- `following` - 关注数
- `created_at` - 账号创建时间
- `updated_at` - 最后更新时间

### 需要权限的信息
- `email` - 主邮箱（需要 `user:email`）
- `private_gists` - 私有 Gist 数（需要 `read:user`）
- `total_private_repos` - 私有仓库总数（需要 `read:user`）
- `owned_private_repos` - 拥有的私有仓库数（需要 `read:user`）
- `disk_usage` - 磁盘使用量（需要 `read:user`）
- `collaborators` - 协作者数（需要 `read:user`）
- `two_factor_authentication` - 2FA 状态（需要 `read:user`）
- `plan` - 订阅计划信息（需要 `read:user`）

## 📧 邮箱信息

通过 `https://api.github.com/user/emails` 可获取：

```json
[
  {
    "email": "primary@example.com",
    "verified": true,
    "primary": true,
    "visibility": "public"
  },
  {
    "email": "secondary@example.com",
    "verified": true,
    "primary": false,
    "visibility": "private"
  }
]
```

## 🔐 所有可用的 GitHub OAuth2 Scope

### 用户相关
- `read:user` - 读取用户基本信息
- `user:email` - 访问用户邮箱
- `user:follow` - 关注/取消关注用户

### 仓库相关
- `repo` - 完整仓库访问权限
- `repo:status` - 访问提交状态
- `repo_deployment` - 访问部署状态
- `public_repo` - 访问公开仓库
- `repo:invite` - 接受仓库邀请

### 组织相关
- `read:org` - 读取组织信息
- `write:org` - 管理组织
- `admin:org` - 完整组织管理权限

### 通知相关
- `notifications` - 访问通知

### Gist 相关
- `gist` - 创建和管理 Gist

### 其他
- `delete_repo` - 删除仓库权限
- `write:packages` - 上传包
- `read:packages` - 下载包
- `write:gpg_key` - 管理 GPG 密钥
- `read:gpg_key` - 读取 GPG 密钥

## 🚀 扩展权限示例

如果你想获取更多信息，可以修改配置：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            scope:
              - user:email
              - read:user
              - public_repo      # 访问公开仓库
              - read:org         # 读取组织信息
              - notifications    # 访问通知
```

## 📝 实际获取的数据示例

### 基本用户信息
```json
{
  "login": "octocat",
  "id": 1,
  "name": "The Octocat",
  "email": "octocat@github.com",
  "avatar_url": "https://github.com/images/error/octocat_happy.gif",
  "company": "GitHub",
  "blog": "https://github.com/blog",
  "location": "San Francisco",
  "bio": "There once was...",
  "public_repos": 2,
  "followers": 20,
  "following": 0,
  "created_at": "2008-01-14T04:33:35Z"
}
```

### 邮箱列表
```json
[
  {
    "email": "octocat@github.com",
    "verified": true,
    "primary": true,
    "visibility": "public"
  }
]
```

### 仓库列表（如果有 public_repo 权限）
```json
[
  {
    "id": 1296269,
    "name": "Hello-World",
    "full_name": "octocat/Hello-World",
    "description": "This your first repo!",
    "private": false,
    "html_url": "https://github.com/octocat/Hello-World",
    "clone_url": "https://github.com/octocat/Hello-World.git",
    "language": "C",
    "stargazers_count": 80,
    "watchers_count": 9,
    "forks_count": 9,
    "created_at": "2011-01-26T19:01:12Z",
    "updated_at": "2011-01-26T19:14:43Z"
  }
]
```

## ⚠️ 隐私和安全注意事项

1. **最小权限原则** - 只请求应用真正需要的权限
2. **用户同意** - 用户会看到你请求的所有权限
3. **数据保护** - 妥善保护获取的用户数据
4. **权限审查** - 定期审查是否还需要某些权限
5. **透明度** - 向用户说明为什么需要这些权限