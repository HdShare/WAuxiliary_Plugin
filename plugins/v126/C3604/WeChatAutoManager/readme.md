# 微信自动管理插件 WeChatAutoManager

![Version](https://img.shields.io/badge/版本-v1.0.2-blue) ![Platform](https://img.shields.io/badge/平台-WAuxiliary-green) ![Language](https://img.shields.io/badge/语言-Java-orange) ![API](https://img.shields.io/badge/API-v1.2.3.r722.c2ba115-red)

基于[WAuxiliary](https://github.com/HdShare/WAuxiliary_Public)框架开发的微信自动化管理插件，集成好友管理、群邀请、群提示和状态监控功能。

## 🚀 核心功能

- 🤝 **智能好友管理** - 自动通过好友申请并发送欢迎消息
- 🎯 **智能群邀请** - 关键词触发群邀请，智能选择未满群组
- 💬 **群成员提示** - 入群欢迎和退群提醒（可配置）
- 📊 **状态监控** - 群人数统计、汇总日志、实时状态查询


### 安装步骤
1. 下载 `WeChatAutoManager.java` 文件
2. 复制到 `…/plugins/` 目录下
3. 在WAuxiliary设置中启用插件
4. 插件加载后会在日志群发送启动信息

## ⚙️ 配置说明

### 📱 群组配置
```java
// 日志群组ID（接收插件日志）
config.put("LOG_GROUP_ID", "12345678901@chatroom");

// 用户群组列表（用于邀请和提示）
String[] userGroups = {
    "11111111111@chatroom", // 示例群1
    "22222222222@chatroom", // 示例群2
    "33333333333@chatroom", // 示例群3  
    "44444444444@chatroom"  // 示例群4
};

// 群组名称映射
Map groupNameMap = new HashMap();
groupNameMap.put("11111111111@chatroom", "示例群1");
groupNameMap.put("22222222222@chatroom", "示例群2");
// ...
```

### 🤝 好友管理配置
```java
config.put("WELCOME_MESSAGE", 
    "✨ 你好呀～很高兴通过你的好友申请！\n\n" +
    "有什么想问的或需要帮忙的，尽管说，不用太客气😉\n\n" +
    "🏎️ 想进群？回复「加群」就可以啦～\n\n" +
    "🤖 （本消息为自动回复）");
```

### 🎯 群邀请配置
```java
config.put("TRIGGER_KEYWORD", "加群");    // 触发关键词
config.put("MAX_GROUP_MEMBERS", 500);         // 群人数上限

config.put("CONFIRM_MESSAGE", "📩 群聊邀请已发送，请注意查收...");
config.put("ERROR_MESSAGE", "抱歉，群聊邀请发送失败，请稍后重试。");
config.put("FULL_GROUP_MESSAGE", "抱歉，所有群组都已满员，暂时无法发送邀请...");
```

### 💬 群提示配置
```java
// 功能开关
config.put("ENABLE_JOIN_TIPS", true);     // 入群欢迎
config.put("ENABLE_LEFT_TIPS", false);    // 退群提醒
config.put("ENABLE_AT_USER", true);       // @新成员

// 消息模板（支持{userName}、{userWxid}变量）
config.put("JOIN_MESSAGE", 
    "[AtWx={userWxid}]\n🎉 欢迎 {userName} 加入我们的大家庭！\n\n" +
    "📋 请阅读群公告，遵守群规\n💬 如有问题可以私聊管理员");

config.put("LEFT_MESSAGE", "😢 {userName} 离开了群聊，我们会想念你的！");
```

## 🎮 使用指南

### 基本功能
- **好友申请**：自动通过并发送欢迎消息
- **群邀请**：私聊发送"加群"触发邀请
- **群提示**：新成员入群自动欢迎，支持@功能
- **状态查询**：日志群发送`/info`查看实时状态

### 日志输出示例
```
#功能 2024-01-20 10:35:24 🤝 好友申请监控-完成处理
✅ 用户: 张三(user123)
✅ 场景: 1
✅ 已通过好友申请
✅ 已发送欢迎消息

#功能 2024-01-20 10:40:16 🎯 关键词监控-完成处理
✅ 用户: user456
✅ 关键词: 加群
✅ 目标群: 示例群1(11111111111@chatroom)
✅ 群人数: 456人
✅ 已发送群聊邀请
✅ 已发送提醒消息
```


### 方法3: /info命令
在日志群输入`/info`查看群组配置信息

## 🛡️ 注意事项

- ⚠️ 确保机器人在目标群组中有发言和邀请权限
- ⚠️ 注意消息发送频率，避免触发微信反垃圾机制
- ⚠️ 定期备份配置文件
- 💡 新配置建议先在测试群验证

## 🔧 故障排除

- **插件未加载**：检查文件路径和权限
- **日志未显示**：检查LOG_GROUP_ID和机器人权限
- **群邀请失败**：检查群ID和邀请权限
- **群提示不生效**：检查功能开关和群组配置

## 📝 版本历史

### v1.0.2
- 🔧 日志系统优化：汇总日志模式，减少日志数量
- 📊 每类操作仅输出一条完整汇总日志
- ⚡ 性能提升，降低信息噪音
- 🔄 代码重构，移除冗余日志方法

### v1.0.1
- ✨ 初始版本发布
- ✅ 好友自动管理、智能群邀请、群成员提示
- ✅ 完整日志系统、群人员监控、配置统一管理

## 🤝 技术支持

基于 [WAuxiliary](https://github.com/HdShare/WAuxiliary_Public) 框架开发，遵循 MIT 开源协议。

如遇问题请：
1. 检查配置和权限
2. 查看日志分析问题
3. 使用`/info`命令检查状态 
