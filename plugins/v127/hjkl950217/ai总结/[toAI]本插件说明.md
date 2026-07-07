# [toAI]本插件说明

本文记录 `AI聊天总结` 插件的当前功能、设计和修改注意事项，供后续 AI 接手时快速进行功能修改或 bug 调整。

## 插件定位

插件名称：`AI聊天总结`

目录：

```text
plugins/v127/hjkl950217/ai总结
```

核心作用：

1. 读取当前聊天最近一段历史消息，调用配置的大模型接口生成聊天总结。
2. 直接调用配置的大模型接口回答用户提出的问题。
3. 提供配置弹窗，允许用户配置接口、模型、提示词、回答模板和日志开关。
4. 提供日志查看弹窗，方便排查接口调用或聊天记录读取问题。

## 当前文件职责

```text
main.java
```

插件核心脚本，包含命令解析、配置弹窗、聊天记录整理、模型接口调用、日志查看等逻辑。

```text
config.prop
```

默认配置文件。当前默认配置包含 API 地址、模型、总结条数、日志开关、总结提示词、提问系统提示词、提问回答模板。

```text
info.prop
```

插件元信息。

```text
readme.md
```

面向用户的使用说明。

```text
[toAI]框架理解.md
```

记录 WAuxiliary 插件框架相关理解。

```text
[toAI]本插件说明.md
```

当前文档，记录本插件自身设计。

## 当前命令

### `/ai`

显示帮助弹窗。

### `/ai 帮助`

显示帮助弹窗。

英文兼容：

```text
/ai help
```

### `/ai 配置`

打开配置弹窗。

别名：

```text
/ai 设置
/ai config
```

### `/ai 日志`

打开日志查看弹窗。

英文兼容：

```text
/ai log
/ai logs
```

日志弹窗能力：

- 读取 WAuxiliary 插件日志区域对应的宿主日志文件。
- 内容支持滑动。
- 文本可选中。
- 提供复制按钮。
- 文件不存在、文件为空或取不到日志文件时显示 `当前没有日志`。

### `/ai 总结`

总结当前聊天最近默认条数的消息。

默认条数来自配置项：

```properties
summary_count
```

当前限制范围：`50~200`。

### `/ai 总结 120`

临时总结最近 120 条消息。

输入数字会经过 `clampCount(...)` 限制：

- 小于 50 按 50。
- 大于 200 按 200。

### `/ai 提问 <问题>`

直接调用 AI 回答问题。

示例：

```text
/ai 提问 白糖主要由甘蔗制成的吗？
```

如果只输入 `/ai 提问` 且没有问题正文，会 toast：

```text
请输入要提问的内容
```

### 未知 `/ai ...` 命令

如果 `/ai ` 后面的内容匹配不上任何已知指令，会默认显示帮助弹窗。

## 命令分发设计

入口有两个：

```java
boolean onClickSendBtn(String text)
void onHandleMsg(Object msgInfoBean)
```

`onClickSendBtn(...)`：

- 拦截用户点击发送按钮时的 `/ai ...` 命令。
- 命中后调用 `handleCommand(...)`。
- 返回 `true`，防止命令文本发送到聊天。

`onHandleMsg(...)`：

- 只处理自己发送的文本消息。
- 如果内容是 `/ai ...`，也调用 `handleCommand(...)`。
- 作为没有被 `onClickSendBtn(...)` 拦截时的兜底。

命令识别：

```java
boolean isAiCommand(String text) {
    if (TextUtils.isEmpty(text)) return false;
    return text.equals("/ai") || text.startsWith("/ai ");
}
```

核心分发函数：

```java
void handleCommand(String talker, String cmd, boolean intercepted)
```

当前顺序：

1. 检查 `talker` 为空则提示先进入聊天界面。
2. 匹配帮助命令。
3. 匹配配置命令。
4. 匹配日志命令。
5. 匹配提问命令。
6. 匹配总结命令。
7. 未匹配则显示帮助。

## 配置项

当前 `config.prop` 默认配置包括：

```properties
api_url = https://api.openai.com/v1/chat/completions
api_key =
model = gpt-4o-mini
summary_count = 50
log_enable = false
summary_prompt = ...
ask_system_prompt = ...
ask_template = ...
```

### `api_url`

大模型接口地址。

默认是 OpenAI 兼容：

```text
https://api.openai.com/v1/chat/completions
```

如果地址包含 `api.anthropic.com` 或以 `/v1/messages` 结尾，则按 Claude 原生接口调用。

### `api_key`

接口密钥。

OpenAI 兼容接口会通过 `normalizeAuthHeader(...)` 自动补：

```text
Bearer 
```

Claude 原生接口使用：

```text
x-api-key
```

日志里不要输出原始 Key，只能用 `maskApiKey(...)`。

### `model`

模型名称。

默认：

```text
gpt-4o-mini
```

OpenAI 兼容接口和 Claude 原生接口都会把它放到 `model` 字段。

### `summary_count`

默认总结消息条数。

当前 `getSummaryCount()` 会用 `clampCount(...)` 限制到 `50~200`。

注意：`config.prop` 当前默认写的是 `50`，但 `ensureDefaultConfig()` 在缺失时补的是 `100`。如果希望完全一致，可后续统一为同一个值。

### `log_enable`

是否开启运行日志。

默认：

```properties
log_enable = false
```

关闭时 `logx(...)` 不调用 `log(...)`，不会写入 WAuxiliary 插件日志区域。

开启后，接口错误、查询过程等调试信息才会写日志。

### `summary_prompt`

总结功能的系统提示词。

用于控制 `/ai 总结` 的总结格式和内容要求。

当前完整默认文本放在 `config.prop` 中；`main.java` 中只保留短兜底：

```java
String DEFAULT_SUMMARY_PROMPT = "请基于聊天记录生成简短中文总结，只输出最终总结。";
```

### `ask_system_prompt`

提问功能的系统提示词。

用于控制 `/ai 提问` 时 AI 怎么回答问题。

当前默认：

```text
回答时言简意赅，直接回答结论即可。最好用一段话就能说清，控制在300字以下。
```

代码兜底值略短：

```java
String DEFAULT_ASK_SYSTEM_PROMPT = "回答时言简意赅，直接回答结论即可。控制在300字以下。";
```

### `ask_template`

提问功能的回答模板。

支持占位符：

- `{问题原文}`
- `{回答正文}`
- `{模型名称}`

当前默认：

```text
问：{问题原文}\n---------\n{回答正文}\n--\n(以上回答由[{模型名称}]回答，仅供参考)
```

注意：`config.prop` 中使用 `\n` 字面量表示换行。代码通过：

```java
String decodeConfigText(String text)
```

把 `\n` 转成真实换行。

## 配置弹窗设计

配置弹窗在：

```java
void showConfigDialog()
```

当前包含：

1. API 地址
2. API Key
3. 模型名称
4. 默认总结条数
5. 总结提示词
6. 提问系统提示词
7. 提问回答模板
8. 运行日志开关

保存时写入：

```java
putString(CFG_API_URL, ...)
putString(CFG_API_KEY, ...)
putString(CFG_MODEL, ...)
putInt(CFG_SUMMARY_COUNT, ...)
putString(CFG_SUMMARY_PROMPT, ...)
putString(CFG_ASK_SYSTEM_PROMPT, ...)
putString(CFG_ASK_TEMPLATE, ...)
putBoolean(CFG_LOG_ENABLE, ...)
```

新增配置项时要同步：

- 顶部 `CFG_...` 常量。
- 必要的 `DEFAULT_...` 兜底值。
- `showConfigDialog()` 输入框。
- 保存逻辑。
- `ensureDefaultConfig()`。
- getter 方法。
- `config.prop`。
- `readme.md`。

## 总结功能设计

核心方法：

```java
void summarizeChat(final String talker, final int count)
String buildHistoryText(String talker, int count)
List queryRecentHistoryMsg(String talker, int count)
void callSummaryApi(final String talker, String historyText, int count)
```

流程：

1. 检查 API 配置是否完整。
2. toast：`AI 正在总结，请稍候...`
3. 新线程中读取聊天历史。
4. 整理为文本格式。
5. 调用模型接口。
6. 成功后发送：

```text
【AI聊天总结】
<总结内容>
```

### 历史消息查询

`queryRecentHistoryMsg(...)` 用二分法尝试获取更接近当前时间的最近消息。

原因：代码注释指出 `queryHistoryMsg` 按 `startTime` 正序查询，不能简单从很早时间取，否则可能拿到远古记录。

二分失败后会按以下窗口兜底：

- 最近 1 天
- 最近 3 天
- 最近 7 天
- 最近 15 天
- 最近 30 天
- 最近 90 天
- 最近 180 天
- 最近 365 天
- `0L`

最后会过滤可读消息并按时间排序，只保留最后 `count` 条。

### 可读消息类型

`isReadableMsg(...)` 当前支持：

- 文本
- 图片
- 语音
- 视频
- 文件
- 链接
- 引用
- 拍一拍

`buildMessageContent(...)` 会把非文本消息转成简短占位：

```text
[图片]
[语音]
[视频]
[文件] 文件名
[链接] 内容
[引用] 内容
[拍一拍]
```

### 发言人名称

`resolveDisplayName(...)` 会尝试把 wxid 转成显示名。

优先级：

1. `getFriendDisplayName(wxid, talker)`
2. `getFriendDisplayName(wxid)`
3. `getFriendNickName(wxid)`
4. `wxid`

## 提问功能设计

核心方法：

```java
boolean isAskCommand(String cmd)
String parseAskQuestion(String cmd)
void askAi(final String talker, final String question)
void callAskApi(final String talker, final String question)
String formatAskAnswer(String question, String answer, String model)
```

流程：

1. 匹配 `/ai 提问` 或 `/ai 提问 ...`。
2. 提取问题正文。
3. 如果问题为空，toast 提示。
4. 检查 API 配置。
5. toast：`AI 正在回答，请稍候...`
6. 调用模型接口。
7. 解析回答正文。
8. 套用 `ask_template`。
9. 发送到当前聊天。

### 提问系统提示词

`callAskApi(...)` 会读取：

```java
String systemPrompt = getAskSystemPrompt();
```

OpenAI 兼容接口中放入 system message。

Claude 原生接口中放入 `system` 字段。

### 回答模板

`formatAskAnswer(...)` 替换：

```java
.replace("{问题原文}", safeString(question))
.replace("{回答正文}", safeString(answer))
.replace("{模型名称}", safeString(model))
```

如果用户把模板改坏，只要还包含普通文本，也会照常发送；没有强校验占位符。

## 模型接口调用设计

总结和提问目前分别有独立函数：

```java
callSummaryApi(...)
callAskApi(...)
```

两者结构相似，但为了降低改动风险没有抽象成通用函数。

共同点：

- 都读取 `api_url`、`api_key`、`model`。
- 都支持 OpenAI 兼容和 Claude 原生。
- 都设置 `max_tokens = 1500`。
- 都用 `post(apiUrl, params, headers, 90L, callback)`。
- 都用 `parseSummaryResponse(...)` 解析返回。

后续如果继续新增 AI 功能，可以考虑抽一个通用 `callChatApi(...)`，但当前为了稳定性保留重复结构。

## 返回解析

解析函数：

```java
String parseSummaryResponse(String body)
```

虽然名字叫 `parseSummaryResponse`，提问功能也复用它。

支持格式：

1. OpenAI Chat Completions：

```json
choices[0].message.content
```

2. 旧 completions：

```json
choices[0].text
```

3. Claude Messages：

```json
content[].text
```

4. 顶层：

```json
text
message
```

如果后续重命名，可以改成 `parseAiResponse(...)`，但要同步所有调用。

## 日志功能设计

日志开关：

```java
boolean mLogEnabled
```

读取：

```java
boolean isLogEnabled()
```

封装：

```java
void logx(Object msg)
```

只有 `mLogEnabled = true` 时才调用框架的 `log(msg)`。

日志查看：

```java
File getHostLogFile()
String readLogContent()
void showLogDialog()
void copyTextToClipboard(String label, String text)
```

`getHostLogFile()` 使用宿主 API：

```java
Object file = getLogFile();
```

这样 `/ai 日志` 读取的是 WAuxiliary 插件日志区域的同一个文件，不另写独立日志文件。

`readLogContent()` 最多读 3000 行，避免日志过大导致弹窗卡顿。

## 帮助文本

帮助弹窗文本在：

```java
String buildHelpText()
```

新增命令后必须同步这里，否则用户不知道新功能。

当前帮助包含：

1. `/ai 总结`
2. `/ai 总结 120`
3. `/ai 提问 问题内容`
4. `/ai 配置`
5. `/ai 日志`
6. `/ai 帮助`

## 隐私和安全注意事项

本插件会把以下内容发送到用户配置的外部 API：

- `/ai 总结`：整理后的聊天记录。
- `/ai 提问`：用户输入的问题。

因此后续改动时：

- 不要默认把更多敏感信息发给模型。
- 不要把 API Key 原文写日志。
- README 中应保留外部 API 可信性提醒。
- 日志可能包含接口地址、模型名、错误信息、查询过程，默认关闭是合理的。

## 当前无法在仓库内完整验证的点

该插件运行依赖手机端 WAuxiliary 环境，因此仓库里只能做静态检查，不能完整端到端验证：

- `queryHistoryMsg(...)` 实际返回行为。
- `post(...)` 对 JSON 参数的具体序列化。
- `getLogFile()` 在目标宿主版本上的返回路径。
- `getFriendDisplayName(...)` 单参数重载是否总是存在。
- Android 弹窗在不同宿主界面下的实际展示效果。

后续实机测试建议：

1. 打开 `/ai 配置`，确认所有输入框可见并能保存。
2. 开启运行日志后执行 `/ai 日志`，确认能看到宿主日志。
3. 配置有效 API 后测试 `/ai 提问 1+1等于几？`。
4. 测试 `/ai 总结` 和 `/ai 总结 120`。
5. 测试未知命令 `/ai abc` 是否弹帮助。
6. 修改 `ask_template`，确认 `\n` 能转成真实换行。

## 常见修改入口

### 新增命令

改：

- `handleCommand(...)`
- 新增 `isXxxCommand(...)` / `parseXxx(...)`
- `buildHelpText()`
- `readme.md`

### 新增配置

改：

- 顶部 `CFG_...`
- 顶部 `DEFAULT_...`
- `config.prop`
- `showConfigDialog()`
- `ensureDefaultConfig()`
- getter
- `readme.md`
- 本文档

### 修改模型调用

重点看：

- `isClaudeNativeApi(...)`
- `buildSingleUserMessages(...)`
- `callSummaryApi(...)`
- `callAskApi(...)`
- `parseSummaryResponse(...)`
- `normalizeAuthHeader(...)`

### 修改总结消息范围

重点看：

- `clampCount(...)`
- `getSummaryCount()`
- `parseSummaryCount(...)`
- `queryRecentHistoryMsg(...)`
- `config.prop` 的 `summary_count`
- README 对范围的说明

## 当前设计取舍

1. **配置完整默认值放在 `config.prop`，代码只留短兜底。**
   - 好处：减少代码长文本，方便用户和后续 AI 直接看配置。
   - 风险：如果用户配置文件被删或宿主读不到配置，代码兜底文本较简略，但功能仍可用。

2. **总结和提问调用函数未抽象合并。**
   - 好处：局部逻辑清晰，修改某个功能风险小。
   - 代价：OpenAI/Claude 请求构造有重复。

3. **日志只读宿主日志文件，不再自建日志文件。**
   - 好处：避免 WAuxiliary 日志区域和插件自建日志不一致。
   - 风险：如果未来宿主移除 `getLogFile()`，`/ai 日志` 会显示当前没有日志。

4. **未知 `/ai ...` 命令弹帮助。**
   - 好处：用户输错命令时能快速看到可用指令。

## 当前版本建议

如果这次功能变更准备发布，建议后续按项目习惯更新：

```text
info.prop
```

中的：

- `version`
- `updateTime`

以及必要时更新文档站索引。但这属于发布流程，不是当前代码功能必需项。
