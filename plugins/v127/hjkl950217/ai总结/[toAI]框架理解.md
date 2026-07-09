# [toAI]框架理解

本文记录当前插件依赖的 WAuxiliary Plugin 脚本框架理解，供后续 AI 接手修改本插件时快速进入上下文。

## 插件目录结构

WAuxiliary 插件通常以单个目录为单位发布。本插件目录为：

```text
plugins/v127/hjkl950217/ai总结/
├─ info.prop
├─ config.prop
├─ main.java
├─ readme.md
├─ [toAI]框架理解.md
└─ [toAI]本插件说明.md
```

常见文件含义：

- `info.prop`：插件元信息，包括名称、作者、版本、更新时间。
- `config.prop`：插件默认配置。宿主也会把配置读写保存到这里。
- `main.java`：插件主脚本，虽然扩展名是 `.java`，但整体更接近 WAuxiliary 支持的 Beanshell/Java 风格脚本。
- `readme.md`：面向用户的使用说明。

## 运行入口

框架会自动调用以下方法：

```java
void onLoad()
```

插件加载时调用。适合初始化配置、读取运行开关、打印加载日志。

```java
void openSettings()
```

用户从插件设置入口打开设置时调用。本插件直接打开配置弹窗。

```java
boolean onClickSendBtn(String text)
```

用户点击发送按钮时调用。返回 `true` 表示拦截本次发送，返回 `false` 表示继续正常发送。

本插件用它拦截 `/ai ...` 命令，避免命令文本真的发到聊天里。

```java
void onHandleMsg(Object msgInfoBean)
```

收到消息时调用。`msgInfoBean` 实际是消息结构对象。本插件只处理自己发送的文本消息，作为 `onClickSendBtn` 没拦截到时的兜底。

## 常用全局变量

框架提供一些可直接使用的全局变量：

- `pluginDir`：当前插件目录，读写插件自身文件时使用。
- `cacheDir`：当前插件缓存目录，适合临时文件、下载文件。
- `pluginName`：插件名称。
- `pluginVersion`：插件版本。
- `pluginAuthor`：插件作者。
- `hostContext`：Android Context，可用于系统服务，例如剪贴板。

## 配置读写

框架提供轻量配置方法，配置通常保存到插件目录下的 `config.prop`：

```java
String getString(String key, String defValue);
boolean getBoolean(String key, boolean defValue);
int getInt(String key, int defValue);

void putString(String key, String value);
void putBoolean(String key, boolean value);
void putInt(String key, int value);
```

建议：

- 新增配置项时，优先写入 `config.prop`，减少代码中的长默认值。
- `main.java` 中仍保留短兜底默认值，避免配置缺失时插件不可用。
- 用 `ensureDefaultConfig()` 在插件加载时补齐缺失配置。

注意：`config.prop` 中多行模板不方便直接写真实换行时，可以使用 `\n` 字面量，代码读取后再转成真实换行。本插件已有 `decodeConfigText(...)` 用于处理提问回答模板。

## 消息相关 API

常用发送方法：

```java
void sendText(String talker, String content);
```

`taker` 是目标会话 ID，私聊通常是好友 wxid，群聊通常是 `xxx@chatroom`。

当前聊天对象可通过：

```java
String getTargetTalker();
```

查询历史消息：

```java
List queryHistoryMsg(String talker, long startTime, int count);
```

文档中也出现过四参数签名：

```java
List<MsgInfoBean> queryHistoryMsg(String talker, long startTime, boolean isAsc, int count);
```

但仓库现有插件和本插件当前使用的是三参数版本。后续不要轻易只按文档改成四参数，除非实机确认当前 WAuxiliary 版本要求变更。

## 消息对象理解

`onHandleMsg(Object msgInfoBean)` 中常用方法：

```java
boolean isSend();
boolean isText();
String getContent();
String getTalker();
String getSendTalker();
long getCreateTime();
```

消息类型判断：

```java
isImage();
isVoice();
isVideo();
isFile();
isLink();
isQuote();
isPat();
```

引用消息、文件消息等可以继续取子结构，例如：

```java
Object quote = msg.getQuoteMsg();
Object file = msg.getFileMsg();
```

本插件为了兼容运行环境，用了反射辅助 `callNoArg(...)` 调用这些方法，避免部分结构或方法不存在时直接崩溃。

## 联系人显示名

本插件整理聊天记录时会把发送者 wxid 转为显示名，优先顺序大致是：

```java
getFriendDisplayName(wxid, talker)
getFriendDisplayName(wxid)
getFriendNickName(wxid)
wxid
```

注意：文档中明确存在 `getFriendDisplayName(String friendWxid, String roomId)` 和 `getFriendNickName(String friendWxid)`。单参数 `getFriendDisplayName(wxid)` 可能是旧版或兼容 API，所以调用处包了 `try/catch`。

## 网络请求

框架提供异步 HTTP 方法：

```java
void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, Consumer<String> callback);
void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, long timeout, Consumer<String> callback);
```

重要：

- 请求是异步回调，不要当同步返回值使用。
- `Content-Type` 包含 `application/json` 时，参数会按 JSON 发送。
- 回调中的 `body` 可能为空，需要判空。

本插件通过 `Map params` 和 `Map headers` 构造请求，然后调用：

```java
post(apiUrl, params, headers, 90L, body -> { ... });
```

## OpenAI 兼容接口与 Claude 原生接口

本插件同时支持两类接口：

1. OpenAI 兼容 `/v1/chat/completions`
2. Anthropic Claude 原生 `/v1/messages`

判断逻辑在：

```java
boolean isClaudeNativeApi(String apiUrl)
```

当前规则：

- URL 包含 `api.anthropic.com`
- 或 URL 以 `/v1/messages` 结尾

则走 Claude 原生格式。

Claude 原生请求要点：

```java
headers.put("x-api-key", apiKey.trim());
headers.put("anthropic-version", "2023-06-01");
params.put("system", systemPrompt);
params.put("messages", buildSingleUserMessages(userContent));
params.put("max_tokens", Integer.valueOf(1500));
```

OpenAI 兼容请求要点：

```java
headers.put("Authorization", normalizeAuthHeader(apiKey));
params.put("messages", jsonArrayToList(messages));
params.put("max_tokens", Integer.valueOf(1500));
```

## JSON 参数转换

因为框架的 `post(...)` 参数是 `Map`，而不是直接传字符串 JSON，本插件先构造 `JSONObject` / `JSONArray`，再通过：

```java
jsonArrayToList(JSONArray arr)
jsonObjectToMap(JSONObject obj)
```

转成 `List` / `Map`，再放进 `params`。

后续新增请求字段时，优先沿用这个转换方式。

## 日志机制

框架提供：

```java
void log(Object msg);
```

用于输出到插件日志区域。

本插件封装为：

```java
void logx(Object msg)
```

只有 `log_enable = true` 时才写日志，避免日志过多。

框架还存在宿主日志文件 API：

```java
Object getLogFile();
```

仓库其他插件已使用它读取/裁剪插件日志。本插件 `/ai 日志` 也是通过 `getLogFile()` 获取 WAuxiliary 插件日志区域对应的同一个日志文件，避免自己另写一份日志造成不一致。

## UI 弹窗

本插件配置、帮助、日志都使用 Android 原生 `AlertDialog`。

常用控件：

- `AlertDialog.Builder`
- `LinearLayout`
- `ScrollView`
- `TextView`
- `EditText`
- `Switch`

需要在 UI 线程操作界面：

```java
Activity ctx = getTopActivity();
ctx.runOnUiThread(new Runnable() {
    public void run() {
        // 创建和显示弹窗
    }
});
```

日志弹窗中，`ScrollView` 支持滑动，`TextView#setTextIsSelectable(true)` 支持选中文本。

复制日志使用 Android 剪贴板：

```java
ClipboardManager cm = (ClipboardManager) hostContext.getSystemService(Context.CLIPBOARD_SERVICE);
cm.setPrimaryClip(ClipData.newPlainText(label, text));
```

## 修改建议

后续修改本插件时建议遵守：

1. 优先只改本插件目录内文件。
2. 新增用户可调参数时，同步改：
   - `config.prop`
   - `ensureDefaultConfig()`
   - `showConfigDialog()`
   - `readme.md`
   - 必要时改 `[toAI]本插件说明.md`
3. 新增命令时，同步改：
   - `handleCommand(...)`
   - `buildHelpText()`
   - `readme.md`
4. 网络调用尽量复用现有 OpenAI/Claude 分支和 `parseSummaryResponse(...)`。
5. 不要把 API Key 原文写日志；需要记录时用 `maskApiKey(...)`。
6. 涉及聊天记录或用户问题的功能，注意隐私提示：内容会发送到配置的外部 API。
