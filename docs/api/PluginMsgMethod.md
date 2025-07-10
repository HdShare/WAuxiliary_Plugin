# 消息方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 发送文本消息

```java
void sendText(String talker, String content);
```

## 发送语音消息

```java
void sendVoice(String talker, String sendPath);

void sendVoice(String talker, String sendPath, int voiceLength);
```

## 发送图片消息

```java
void sendImage(String talker, String sendPath);

void sendImage(String talker, String sendPath, String appId);
```

## 发送视频消息

```java
void sendVideo(String talker, String sendPath);
```

## 发送表情消息

```java
void sendEmoji(String talker, String sendPath);
```

## 发送拍一拍

```java
void sendPat(String talker, String pattedUser);
```

## 发送分享名片

```java
void sendShareCard(String talker, String wxid);
```

## 发送位置消息

```java
void sendLocation(String talker, String poiName, String label, String x, String y, String scale);

void sendLocation(String talker, JSONObject jsonObj);
```

## 发送媒体消息

```java
void sendMediaMsg(String talker, MediaMessage mediaMessage, String appId);
```

## 发送文本卡片

```java
void sendTextCard(String talker, String text, String appId);
```

## 发送音乐卡片

```java
void sendMusicCard(String talker, String title, String description, String playUrl, String infoUrl, String appId);

void sendMusicCard(String talker, JSONObject jsonObj);
```

## 发送网页卡片

```java
void sendWebpageCard(String talker, String title, String description, String webpageUrl, String appId);

void sendWebpageCard(String talker, JSONObject jsonObj);
```

## 发送密文消息

```java
void sendCipherMsg(String talker, String title, String content);
```

## 发送接龙消息

```java
void sendNoteMsg(String talker, String content);
```

## 发送引用消息

```java
void sendQuoteMsg(String talker, long msgId, String content);
```

## 撤回指定消息

```java
void revokeMsg(long msgId);
```

## 插入系统消息

```java
void insertSystemMsg(String talker, String content, long createTime);
```
