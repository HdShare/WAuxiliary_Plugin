import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'WAuxiliary Plugin',
  description: 'WAuxiliary Plugin',
  lang: 'zh-Hans',
  base: '/WAuxiliary_Plugin/',
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '接口文档', link: '/api/QuickStart', activeMatch: '/api' },
    ],
    sidebar: [
      {
        text: '接口文档',
        collapsed: false,
        items: [
          { text: '快速开始', link: '/api/QuickStart' },
          { text: '全局变量', link: '/api/PluginGlobal' },
          { text: '回调方法', link: '/api/PluginCallback' },
          { text: '相关结构', link: '/api/PluginStruct' },
          { text: '音频方法', link: '/api/method/PluginAudioMethod' },
          { text: '配置方法', link: '/api/method/PluginConfigMethod' },
          { text: '联系方法', link: '/api/method/PluginContactMethod' },
          { text: '网络方法', link: '/api/method/PluginHttpMethod' },
          { text: '媒体方法', link: '/api/method/PluginMediaMsgMethod' },
          { text: '消息方法', link: '/api/method/PluginMsgMethod' },
          { text: '其他方法', link: '/api/method/PluginOtherMethod' },
          { text: '朋友圈方法', link: '/api/method/PluginSnsMethod' },
          { text: 'DexKit 方法', link: '/api/method/PluginDexKitMethod' },
          { text: 'Reflect 方法', link: '/api/method/PluginReflectMethod' },
          { text: 'Hook 方法', link: '/api/method/PluginHookMethod' },
        ]
      }
    ],
    lastUpdated: {
      text: '最后更新',
      formatOptions: {
        dateStyle: 'medium',
        timeStyle: 'short'
      }
    },
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    search: {
      provider: 'local'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/HdShare/WAuxiliary_Plugin' }
    ],
  }
})
