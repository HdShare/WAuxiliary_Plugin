# AiChatbot Java 插件

基于[WAuxiliary](https://github.com/HdShare/WAuxiliary_Public)框架开发的微信Ai聊天机器人。

## 项目概述

这个 Java 项目实现了一个智能聊天机器人 `AiChatbot`，它可以模拟一个角色，与用户进行自然亲切的交流。该聊天机器人支持私聊和群聊场景，并且可以通过特定指令重置记忆。

## 功能特性

1. **角色初始化**：初始化聊天机器人为一个特定的角色，可以自行编辑，具备特定的交流风格和特点。
2. **记忆管理**：支持重置聊天记忆，将聊天记录清空并重新初始化角色。
3. **消息处理**：可以添加系统消息、用户消息和助手消息到消息列表中。
4. **API 请求**：构建 Bot API 请求参数和请求头，并发送请求到指定的 API 地址。
5. **错误处理**：在 API 请求过程中，对解析失败和请求异常进行相应的错误处理。
6. **群聊和私聊支持**：在群聊中，只有被 @ 时才响应；在私聊中，以 `Bot` 开头的消息才会触发聊天机器人的回复。

## 代码结构

以下是主要的类和方法说明：

- `AiChatbot` 类

  ：

  - `initCompanionRole()`：初始化聊天机器人的角色。
  - `resetMemory()`：重置聊天记忆。
  - `addSystemMsg(String content)`：添加系统消息到消息列表。
  - `addUserMsg(String content)`：添加用户消息到消息列表。
  - `addAssistantMsg(String content)`：添加助手消息到消息列表。
  - `getBotParam(String content)`：构建 Bot API 请求参数。
  - `getBotHeader()`：构建 Bot API 请求头。
  - `sendBotResp(String talker, String content)`：发送请求到 Bot API 并处理响应。
  - `onLongClickSendBtn(String text)`：处理长按发送键的操作，支持重置记忆指令。
  - `onHandleMsg(Object msgInfoBean)`：处理群聊和私聊消息。

## 使用方法

### 加载

下载AiChatbot.java，放置在Wauxiliary Plugin文件夹下，一般地址为`…/plugins/`

### 配置

在代码中，需要替换以下信息：

- `getBotParam()` 方法中的 `model` 参数，替换为实际使用的模型名称。
- `getBotHeader()` 方法中的 `Authorization` 参数，替换为实际的 API 密钥。
- `sendBotResp()` 方法中的 API 地址，替换为实际的 Bot API 地址。

### 群聊使用

在群聊中，@ 聊天机器人并发送消息，聊天机器人会进行回复。

### 私聊使用

在私聊中，以 `Bot` 开头发送消息，聊天机器人会进行回复。

### 重置记忆

在输入框中输入 `重置` 并长按发送键，聊天机器人的记忆将被重置。

## 依赖

该项目依赖于 `org.json` 库，用于处理 JSON 数据。

## 注意事项

- 请确保 API 密钥和 API 地址的正确性，否则可能会导致请求失败。
- 在使用过程中，可能会遇到网络问题或 API 服务不可用的情况，请根据实际情况进行处理。