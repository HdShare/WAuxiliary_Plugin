# 消息方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- `talker` 表示目标会话 ID。
- 私聊时，`talker` 一般是对方 `wxid`。
- 群聊时，`talker` 一般是 `xxx@chatroom`。
- 如果你当前就在聊天页面，通常可以直接用 `getTargetTalker()`。
- 图片、语音、视频发送方法里的路径，都要求是本地文件路径。

## 发送文本消息

```beanshell
void sendText(String talker, String content);

void sendText(String talker, String content, Consumer<Long> callback);
```

- `talker`：目标会话 ID
- `content`：文本内容
- `callback`：发送完成回调，参数为服务端消息 ID，失败时可能为 `null`

## 示例

```beanshell
String talker = getTargetTalker();

sendText(talker, "你好");
sendText(talker, "[AtWx=wxid_123] 你好");
sendText(talker, "[AtWx=notify@all] 请注意");
sendText(talker, "hello", svrId -> {
    log("svrId = " + svrId);
});
```

## 发送语音消息

```beanshell
void sendVoice(String talker, String sendPath);

void sendVoice(String talker, String sendPath, int duration);
```

- `sendPath`：本地语音文件路径
- `duration`：语音时长，单位秒

## 示例

```beanshell
sendVoice(getTargetTalker(), cacheDir + "/voice.silk", 3);
```

## 发送图片消息

```beanshell
void sendImage(String talker, String sendPath);

void sendImage(String talker, String sendPath, String appId);
```

- `sendPath`：本地图片路径
- `appId`：应用标识

## 示例

```beanshell
sendImage(getTargetTalker(), cacheDir + "/demo.jpg");
```

## 发送视频消息

```beanshell
void sendVideo(String talker, String sendPath);
```

- `sendPath`：本地视频路径

## 发送表情消息

```beanshell
void sendEmoji(String talker, String sendPath);
```

- `sendPath`：表情文件路径

## 发送拍一拍

```beanshell
void sendPat(String talker, String pattedUser);
```

- `pattedUser`：被拍对象 `wxid`

## 发送分享名片

```beanshell
void sendShareCard(String talker, String wxid);
```

- `wxid`：要分享的联系人 `wxid`

## 发送位置消息

```beanshell
void sendLocation(String talker, String poiName, String label, String x, String y, String scale);

void sendLocation(String talker, JSONObject jsonObj);
```

- `poiName`：地点名称
- `label`：位置描述
- `x`：经度
- `y`：纬度
- `scale`：缩放级别
- `jsonObj`：可包含 `poiName`、`label`、`x`、`y`、`scale`

## 示例

```beanshell
sendLocation(getTargetTalker(), "测试地点", "测试描述", "113.3245", "23.0999", "16");
```

## 发送密文消息

```beanshell
void sendCipherMsg(String talker, String title, String content);
```

## 发送小程序消息

```beanshell
void sendAppBrandMsg(String talker, String title, String pagePath, String ghName);
```

- `pagePath`：小程序页面路径
- `ghName`：小程序账号

## 发送接龙消息

```beanshell
void sendNoteMsg(String talker, String content);
```

## 发送引用消息

```beanshell
void sendQuoteMsg(String talker, long msgId, String content);
```

- `msgId`：被引用消息 ID

## 示例

```beanshell
long msgId = 123456L;
sendQuoteMsg(getTargetTalker(), msgId, "这是引用回复");
```

## 撤回指定消息

```beanshell
void revokeMsg(long msgId);
```

- `msgId`：要撤回的消息 ID

## 插入系统消息

```beanshell
long insertSystemMsg(String talker, String content, long createTime);
```

- `createTime`：消息时间戳，通常可传 `System.currentTimeMillis()`
- 返回值：插入后的消息 ID

## 示例

```beanshell
long msgId = insertSystemMsg(getTargetTalker(), "处理中...", System.currentTimeMillis());
log("system msgId = " + msgId);
```

## 查询历史消息

```beanshell
List<MsgInfoBean> queryHistoryMsg(String talker, long startTime, int count);
```

- `startTime`：起始时间戳
- `count`：查询条数
- 返回值：历史消息列表

## 示例

```beanshell
var list = queryHistoryMsg(getTargetTalker(), 0L, 10);
log("history size = " + list.size());
```

## 下载图片

```beanshell
void downloadImg(String md5, String cdnUrl, String aesKey, String savePath);

void downloadImg(MsgInfoBean.ImageMsg imageMsg, String savePath);
```

- `savePath`：保存路径
- 第二种写法可直接传消息对象中的图片信息
