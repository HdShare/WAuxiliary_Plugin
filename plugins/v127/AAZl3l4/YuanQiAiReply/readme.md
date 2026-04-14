# 元启Ai自动回复 - WA模块版

## 平台信息

- 元启Ai平台：http://47.105.51.84
- QQ交流群: 883640898([点击加入 可能会崩 建议搜索群号加入](https://qun.qq.com/universal-share/share?ac=1&authKey=qnKnEk9fixjZc6NNjEFbLB8gwREGPnfUP23AZYxGqdxw0iiRJDH1zrztUG8%2BdbAE&busi_data=eyJncm91cENvZGUiOiI4ODM2NDA4OTgiLCJ0b2tlbiI6IlFXVEgzNFEyd3lJNU5oQzljZG4yQW5BMFBWK2RENHpFUkR6eEZ4TWl5U3Z6ZGRYb3ViK2xPK3Nva1p2Wjl4d2MiLCJ1aW4iOiI2MTEwNTM2In0%3D&data=TWSZoYEaYUAG0Euuz6NeLt9YAZI87UxI56LHHlNDI8SV2DSkuJMGelcmcyrKRq1py2DZVMvYrgt4m6sgjkhLXA&svctype=4&tempid=h5_group_info))
- 平台默认使用轻量级的免费模型，可安心使用
- 手机网页请切换电脑模式，否则页面会变形

## 功能特性

- ✅ 基于元启Ai Agents平台的Ai自动回复
- ✅ 支持艾特触发、关键词触发、正则触发
- ✅ 支持拍一拍回复
- ✅ 支持设定上下文（针对每个微信用户）
- ✅ 可配置作用域（群聊/私聊单独开启）
- ✅ 支持引用回复
- ✅ 支持知识库功能
- 需配置API Key

## 使用方法

### 1. 获取API Key

1. 登录元启Ai平台：http://47.105.51.84
2. 按照平台指示配置角色人设
3. 按照平台指示获取ApiKey

### 2. 配置插件

1. **打开配置界面**：在任意聊天窗口发送 `/元启` 或 `/元启配置` 或 `/元启设置`
2. **配置API Key**：点击设置图标，输入元启平台的API Key
3. **其他配置**（可选）：
   - 默认回复词：AI调用失败时发送的内容
   - 上下文轮数：AI记忆对话的轮数（0-20轮）
   - 启用知识库：启用知识库功能（会导致回复变慢）

### 3. 开启AI回复

**重要**：需要先进入要开启AI回复的聊天界面，然后发送 `/元启` 点击设置图标，打开设置

- **私聊**：进入私聊界面 → 发送 `/元启` → 开启"启用AI回复"
- **群聊**：进入群聊界面 → 发送 `/元启` → 开启"启用AI回复" → 添加触发规则

### 4. 添加触发规则（群聊必需）

群聊需要添加触发规则才会回复，支持三种触发方式：

- **关键词触发**：消息包含指定关键词时触发
- **正则触发**：消息匹配正则表达式时触发
- **艾特触发**：被艾特时触发

## 注意事项

1. **首次使用需要配置API Key**
2. **必须在当前聊天界面打开配置才能启用该聊天的AI回复**
3. **群聊需要添加触发规则才会生效**
4. **不懂可进QQ交流群查看群公告**

