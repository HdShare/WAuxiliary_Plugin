---
layout: home

hero:
  name: "WAuxiliary Plugins"
  text: "WAuxiliary 插件"

features:
  - title: 示例插件@hd
    details: 监听长按发送按钮, 发送 Hello World 文本
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/DemoPlugin

  - title: 文转音@hd
    details: 命令[#tts 你好], 长按发送按钮将文本转语音发出
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/TextToSpeech

  - title: 点歌插件@hd
    details: 命令[/点歌 你好], 监听收到消息时自动发送歌曲卡片
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/MusicPlugin

  - title: 视频插件@hd
    details: 命令[/视频], 监听收到消息时自动发送视频
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/VideoPlugin

  - title: 自动回复@hd
    details: 监听收到文本消息时自动回复对应内容
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/AutoReply

  - title: 自动回复艾特@hd
    details: 监听收到艾特消息时自动艾特询问干嘛
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/AutoReplyAt

  - title: 历史今天@hd
    details: 命令[/历史今天], 监听收到消息时自动发送历史今天图片
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/HistoryToday

  - title: 进退群提醒@hd
    details: 监听群成员变动时自动发送对应内容
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/JoinAndLeftGroupTips

  - title: 被拍自动回复@hd
    details: 监听到被拍头时自动回复对应内容
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/PatAutoReply

  - title: 文转图@hd
    details: 命令[/作图 你好 世界], 长按发送按钮将文本转图片发出
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/TextToImg

  - title: 智能聊天@hd
    details: 需修改API域名,路径,密钥,模型,好友wxid等参数, 监听收到消息时自动聊天
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/OpenAiChat

  - title: 表情合成@hd
    details: 监听收到[系统表情1+系统表情2]时自动合成新表情并发送
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/EmojiMix

  - title: 进群邀请@hd
    details: 监听私聊收到[进群]时自动发送群聊邀请(需修改群聊wxid)
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/JoinGroupInvite

  - title: 自动同意好友@hd
    details: 监听收到好友申请时自动通过
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/hd/AutoAgreeFriend

  - title: 文生图@CkBcDD
    details: 命令[/作图 Corn Hub], 长按发送按钮生成并发送图片
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/CkBcDD/TXT-to-IMG

  - title: 群投票@CkBcDD
    details: 命令[/投票 标题 选项1 选项2 ……], 监听消息发送，其他群成员发送数字[1, 2, etc.]选择。发送[/结束投票]以结算。
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/CkBcDD/Group-Poll

  - title: 点歌@CkBcDD
    details: 命令[/点歌 Never Gonna Give You Up], 监听收到消息时自动发送歌曲卡片
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/CkBcDD/Fetch-Music

  - title: 看看腿@yfishyon
    details: 输入框输入[黑丝]/[白丝], 长按发送看看腿
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/yfishyon/sese

  - title: 龙图@yfishyon
    details: 输入框输入[龙图], 长按发送成为龙王
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/yfishyon/long

  - title: 举牌@周芷越
    details: 输入框输入[举牌 内容], 生成举牌
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/周芷越/jupai

  - title: 表情合成@Kyo
    details: 监听收到[系统表情1+系统表情2]时自动合成新表情并发送(使用emojik.vercel.app)
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/Kyo/EmojiKitchen

  - title: Http主动发送@CK
    details: 通过 http 请求主动发送消息 `curl -X POST 'http://0.0.0.0:13333' --data '{"ids":["wxid1","wxid2"],"msg":"HelloWorld"}' --location`
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/CK/HttpSend

  - title: DeepSeek多角色智能体@CK
    details: "监听收到消息时通过DeepSeek AI自动聊天(需修改密钥)，不同的群和好友可设置不同的角色。选定对应对话框输入“角色设定: 你是一个逗逼”(不含引号)，长按发送按钮即可设定，重复设定可更新角色"
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/CK/DeepSeek

  - title: 抖音视频解析下载@coderpwh
    details: "监听消息，或者长按发送按钮，解析抖音视频(图文)链接并下载发送"
    link: https://github.com/HdShare/WAuxiliary_Plugin/tree/main/plugins/coderpwh/douyin
---
