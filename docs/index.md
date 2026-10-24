---
layout: home

hero:
  name: CipherGate
  text: 产品与运维文档
  tagline: 从安装部署到应用接入，按角色和任务查阅操作说明、配置要求与维护流程。
  actions:
    - theme: brand
      text: 开始使用
      link: /quick-start
    - theme: alt
      text: 安装与更新
      link: /operations

features:
  - title: 部署与运维
    details: 运行发布包，配置 HTTPS 入口，执行更新、备份和有条件回退。
    link: /operations
  - title: 应用与授权
    details: 管理应用、卡密、终端用户、更新包和跨应用用户迁移。
    link: /developer/
  - title: 客户端接入
    details: 查阅 HTTP/WebSocket 协议、签名规则、第三方充值与下载接口。
    link: /developer/api-integration
  - title: 平台用户中心
    details: 查看平台会员、余额、签到、邀请、订单和工单。
    link: /user/
  - title: 应用用户 Portal
    details: 登录指定应用，查看会员与订单，管理账号并找回密码。
    link: /portal/
  - title: 系统管理
    details: 配置平台用户、角色、接口权限、插件、支付和邮件服务。
    link: /admin/
---

## 按任务查阅

| 任务 | 阅读入口 |
| --- | --- |
| 第一次运行 CipherGate | [安装、配置与初始化](/operations#首次安装) |
| 更新现有平台 | [更新与备份恢复](/operations) |
| 为应用客户端上传安装包 | [应用管理](/developer/application) |
| 在应用之间复制或移动用户 | [用户迁移](/developer/user-transfer) |
| 使用一组充值凭证授权多个应用 | [第三方凭证](/developer/credential) |
| 排查登录或权限问题 | [登录指南](/quick-start/login)、[用户与权限](/admin/users-permissions) |
| 变更数据库结构 | [数据库迁移规范](/database/README) |

## 文档约定

平台账号、平台用户中心和应用用户 Portal 分别说明；操作前先确认账号范围与权限。命令中的示例域名和占位符需替换为自己的值。功能以所部署版本及配置为准，生产变更前请核对版本、备份和恢复条件。
