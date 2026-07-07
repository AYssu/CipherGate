import { defineConfig } from 'vitepress'

// base 路径统一为 /docs/
// VitePress 会自动根据 base 配置在链接前添加 /docs/
export default defineConfig({
  title: 'CipherGate',
  description: '企业级授权与安全管控平台使用文档',
  lang: 'zh-CN',
  base: '/docs/',
  head: [
    ['link', { rel: 'icon', href: '/favicon.svg' }],
  ],
  themeConfig: {
    logo: '/favicon.svg',
    siteTitle: 'CipherGate Docs',
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/quick-start' },
      { text: '开发者中心', link: '/developer/' },
      { text: '应用用户', link: '/portal/' },
    ],
    sidebar: [
      {
        text: '快速开始',
        items: [
          { text: '什么是 CipherGate', link: '/quick-start' },
          { text: '快速注册', link: '/quick-start/register' },
          { text: '首次登录', link: '/quick-start/login' },
        ]
      },
      {
        text: '开发者中心',
        items: [
          { text: '概览', link: '/developer/' },
          { text: '应用管理', link: '/developer/application' },
          { text: '卡密管理', link: '/developer/license' },
          { text: '终端用户管理', link: '/developer/app-user' },
          { text: '变量管理', link: '/developer/variable' },
          { text: '第三方凭证', link: '/developer/credential' },
          { text: '调用日志', link: '/developer/call-log' },
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
          { text: '会员信息', link: '/portal/membership' },
          { text: '充值', link: '/portal/recharge' },
          { text: '订单历史', link: '/portal/order' },
          { text: '设置', link: '/portal/settings' },
        ]
      },
      {
        text: '自助服务',
        items: [
          { text: '卡密激活', link: '/self-service/license' },
          { text: '终端用户查询', link: '/self-service/app-user' },
        ]
      },
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/AYssu/CipherGate' }
    ],
    footer: {
      message: 'CipherGate - 企业级授权与安全管控平台',
      copyright: `© ${new Date().getFullYear()} Created by AYssu`
    },
    search: {
      provider: 'local'
    }
  }
})
