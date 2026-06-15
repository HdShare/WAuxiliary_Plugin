# 媒体方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

该组方法用于发送或分享 App 消息、文件、小程序、音乐、网页等媒体内容。

## 新手先看

- 这组方法偏“分享类消息”，不是普通文本/图片/语音发送。
- `talker` 的含义和消息方法相同：表示目标会话 ID，私聊用好友 `wxid`，群聊用 `xxx@chatroom`。
- 如果你只是发文字、图片、语音，优先看 `PluginMsgMethod`。

## 发送媒体消息

```beanshell
void sendMediaMsg(String talker, Object mediaMessage, String appId);
```

- `talker`：目标会话 ID
- `mediaMessage`：媒体消息对象
- `appId`：应用标识

## 分享文件

```beanshell
void shareFile(String talker, String title, String filePath, String appId);
```

- `title`：消息标题
- `filePath`：本地文件路径

## 示例

```beanshell
shareFile(getTargetTalker(), "测试文件", cacheDir + "/demo.pdf", "wx123");
```

## 分享小程序

```beanshell
void shareMiniProgram(String talker, String title, String description, String userName, String path, byte[] thumbData, String appId);
```

- `userName`：小程序原始 ID
- `path`：页面路径
- `thumbData`：封面缩略图字节数组，可为 `null`

## 分享音乐

```beanshell
void shareMusic(String talker, String title, String description, String musicUrl, String musicDataUrl, byte[] thumbData, String appId);
```

- `musicUrl`：音乐页地址
- `musicDataUrl`：音频直链

## 分享音乐视频

```beanshell
void shareMusicVideo(String talker, String title, String description, String musicUrl, String musicDataUrl, String singerName, int duration, String songLyric, byte[] thumbData, String appId);
```

- `singerName`：歌手名
- `duration`：时长，单位秒
- `songLyric`：歌词文本

## 分享文本

```beanshell
void shareText(String talker, String text, String appId);
```

## 分享视频

```beanshell
void shareVideo(String talker, String title, String description, String videoUrl, byte[] thumbData, String appId);
```

- `videoUrl`：视频链接

## 分享网页

```beanshell
void shareWebpage(String talker, String title, String description, String webpageUrl, byte[] thumbData, String appId);
```

- `webpageUrl`：网页链接

## 示例

```beanshell
shareWebpage(getTargetTalker(), "示例标题", "示例描述", "https://example.com", null, "wx123");
```
