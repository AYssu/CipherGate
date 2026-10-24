import { defineConfig } from 'vitepress'

// base 路径统一为 /docs/
// VitePress 会自动根据 base 配置在链接前添加 /docs/
export default defineConfig({
  title: 'CipherGate',
  description: '企业级授权与安全管控平台使用文档',
  lang: 'zh-CN',
  base: '/docs/',
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', href: '/docs/favicon.svg' }],
  ],
  themeConfig: {
    logo: '/favicon.svg',
    siteTitle: 'CipherGate Docs',
    nav: [
      { text: '使用指南', link: '/quick-start' },
      { text: '开发者指南', link: '/developer/' },
      { text: '部署与运维', link: '/operations' },
      { text: 'API 对接', link: '/developer/api-integration' },
    ],
    outline: { level: [2, 3], label: '本页内容' },
    docFooter: { prev: '上一篇', next: '下一篇' },
    sidebarMenuLabel: '文档目录',
    returnToTopLabel: '返回顶部',
    darkModeSwitchLabel: '外观',
    lastUpdated: { text: '最后更新' },
    editLink: {
      pattern: 'https://github.com/AYssu/CipherGate/edit/master/docs/:path',
      text: '编辑此页',
    },
    sidebar: [
      {
        text: '快速开始',
        items: [
          { text: '使用路径与账号范围', link: '/quick-start' },
          { text: '快速注册', link: '/quick-start/register' },
          { text: '首次登录', link: '/quick-start/login' },
        ]
      },
      {
        text: '部署与运维',
        items: [
          { text: '安装、更新与恢复', link: '/operations' },
          { text: '数据库迁移规范', link: '/database/README' },
          { text: '依赖维护与兼容性', link: '/dependency-upgrades' },
        ]
      },
      {
        text: '开发者中心',
        items: [
          { text: '概览', link: '/developer/' },
          { text: '应用管理', link: '/developer/application' },
          { text: '卡密管理', link: '/developer/license' },
          { text: '终端用户管理', link: '/developer/app-user' },
          { text: '用户迁移', link: '/developer/user-transfer' },
          { text: '变量管理', link: '/developer/variable' },
          { text: '第三方凭证', link: '/developer/credential' },
          { text: '调用日志', link: '/developer/call-log' },
          { text: '第三方 API 对接', link: '/developer/api-integration' },
        ]
      },
      {
        text: '用户中心',
        items: [
          { text: '概览', link: '/user/' },
          { text: '会员信息', link: '/user/membership' },
          { text: '余额管理', link: '/user/balance' },
          { text: '签到', link: '/user/checkin' },
          { text: '邀请', link: '/user/invite' },
          { text: '订单', link: '/user/order' },
          { text: '工单', link: '/user/ticket' },
        ]
      },
      {
        text: '应用用户（Portal）',
        items: [
          { text: '概览', link: '/portal/' },
          { text: '控制台', link: '/portal/dashboard' },
          { text: '会员信息', link: '/portal/membership' },
          { text: '充值', link: '/portal/recharge' },
          { text: '订单历史', link: '/portal/order' },
          { text: '设置', link: '/portal/settings' },
          { text: '密码找回', link: '/portal/password-recovery' },
        ]
      },
      {
        text: '管理后台',
        items: [
          { text: '概览', link: '/admin/' },
          { text: '用户与权限', link: '/admin/users-permissions' },
          { text: '系统配置', link: '/admin/system' },
          { text: '会员与配额运营', link: '/admin/membership-operation' },
          { text: '插件管理', link: '/admin/plugins' },
          { text: '内容与运营', link: '/admin/content' },
        ]
      },
      {
        text: '自助服务',
        items: [
          { text: '卡密激活', link: '/self-service/license' },
          { text: '用户查询与卡密充值', link: '/self-service/app-user' },
        ]
      },
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/AYssu/CipherGate' }
    ],
    footer: {
      message: '应用授权、用户管理与第三方接入',
      copyright: `© ${new Date().getFullYear()} Created by AYssu`
    },
    search: {
      provider: 'local'
    }
  }
})
