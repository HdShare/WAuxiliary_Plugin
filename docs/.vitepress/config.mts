import { defineConfig } from 'vitepress'

export default defineConfig({
  title: "WAuxiliary Plugin",
  description: "WAuxiliary Plugin",
  lang: "zh-Hans",
  base: '/WAuxiliary_Plugin/',
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: "开发文档", link: "/dev/docs", activeMatch: "/dev" },
      { text: "接口文档", link: "/api/docs", activeMatch: "/api" },
    ],
    sidebar: [
      {
        text: "开发文档",
        collapsed: false,
        items: [
          { text: "说明", link: "/dev/docs" },
        ]
      },
      {
        text: "接口文档",
        collapsed: false,
        items: [
          { text: "回调方法", link: "/api/docs" },
          { text: "配置方法", link: "/api/PluginConfigMethod" },
          { text: "联系人方法", link: "/api/PluginContactMethod" },
          { text: "网络方法", link: "/api/PluginHttpMethod" },
          { text: "其他方法", link: "/api/PluginOtherMethod" },
          { text: "发送方法", link: "/api/PluginSendMethod" },
          { text: "朋友圈方法", link: "/api/PluginSnsMethod" },
        ]
      }
    ],
    lastUpdated: {
      text: "最后更新",
      formatOptions: {
        dateStyle: "medium",
        timeStyle: "short"
      }
    },
    docFooter: {
      prev: "上一页",
      next: "下一页"
    },
    search: {
      provider: 'local'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/HdShare/WAuxilary_Plugin' }
    ],
  }
})
