# 元启Jev聊天助手

## 平台信息

- **API Key**：请进交流群获取（群文件里有详细获取步骤）
- **QQ交流群**: 883640898（[点击加入 可能会崩 建议搜索群号加入](https://qun.qq.com/universal-share/share?ac=1&authKey=qnKnEk9fixjZc6NNjEFbLB8gwREGPnfUP23AZYxGqdxw0iiRJDH1zrztUG8%2BdbAE&busi_data=eyJncm91cENvZGUiOiI4ODM2NDA4OTgiLCJ0b2tlbiI6IlFXVEgzNFEyd3lJNU5oQzljZG4yQW5BMFBWK2RENHpFUkR6eEZ4TWl5U3Z6ZGRYb3ViK2xPK3Nva1p2Wjl4d2MiLCJ1aW4iOiI2MTEwNTM2In0%3D&data=TWSZoYEaYUAG0Euuz6NeLt9YAZI87UxI56LHHlNDI8SV2DSkuJMGelcmcyrKRq1py2DZVMvYrgt4m6sgjkhLXA&svctype=4&tempid=h5_group_info)）
- 聊天框输入`/jev`打开脚本配置页面
- 手机网页请切换电脑模式，否则页面会变形

## 功能特性

- ✅ 基于 Jev（System One）的聊天实时决策分析
- ✅ 自动分析对方发来的消息，**结果以系统消息形式插入聊天**
- ✅ 分析维度：潜台词 / 当前情绪 / 真实意图 / 最佳动作 / 建议动作
- ✅ **每条消息独立并发分析**，互不等待，不用排队
- ✅ **作用域**：同一时间只开启一个会话，开启新会话会自动顶掉之前的
- ✅ **上下文实时读取**聊天记录，插件重载也能立刻拿到上下文
- ⚠️ 需配置 API Key

## 使用方法

1. 进交流群，按群公告获取 API Key
2. 进入需要分析的聊天，聊天框输入`/jev`打开配置页
3. 在配置页的「API Key」输入框填入 Key，点底部「保存」
4. 点「开启本会话」——**只有开启的会话才会自动分析**
5. 对方发消息后，分析结果会以系统消息出现在聊天里
6. 换到另一个聊天再点「开启本会话」，会自动顶掉上一个会话

## 配置项说明

| 配置项 | 说明 |
| --- | --- |
| API Key | 必填，进交流群获取 |
| 开启本会话 | 开启后本会话自动分析，同时只能开启一个 |
| 自动分析对方消息 | 总开关，关闭后不分析 |
| 上下文轮数 | 携带多少条聊天记录作为上下文，范围 0-30，**默认 0 即不启用上下文** |
| 排版宽度 | 系统消息补空格的目标宽度（全角字符数），范围 8-40；**留 0 按屏幕自动估算**。偏右就调小、折行就调大 |

## 输出效果

分析结果以系统消息的形式插入聊天，用分隔线把「判断」和「建议」分开：

```
Jev:
话里有话　　　　　　　90%
当前情绪　　　　　　　生气 94%
真实意图　　　　　　　要行动 72%
最佳动作　　　　　　　给行动 43%
──────────────────────
少解释，直接做
「行，我现在去办，弄好告诉你。」
```

## 已适配框架

微信框架: WA
