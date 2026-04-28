# 消息方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1357 版本
:::

## 发送文本消息

```beanshell
void sendText(String talker, String content)

sendText("talker", "[AtWx=wxid_123]");// 艾特用户
sendText("talker", "[AtWx=notify@all]");// 艾特全体
```

## 发送语音消息

```beanshell
void sendVoice(String talker, String sendPath);

void sendVoice(String talker, String sendPath, int duration);
```

## 发送图片消息

```beanshell
void sendImage(String talker, String sendPath);

void sendImage(String talker, String sendPath, String appId);
```

## 发送视频消息

```beanshell
void sendVideo(String talker, String sendPath);
```

## 发送表情消息

```beanshell
void sendEmoji(String talker, String sendPath);
```

## 发送拍一拍

```beanshell
void sendPat(String talker, String pattedUser);
```

## 发送分享名片

```beanshell
void sendShareCard(String talker, String wxid);
```

## 发送位置消息

```beanshell
void sendLocation(String talker, String poiName, String label, String x, String y, String scale);

void sendLocation(String talker, JSONObject jsonObj);
```

## 发送密文消息

```beanshell
void sendCipherMsg(String talker, String title, String content);
```

## 发送小程序消息

```beanshell
void sendAppBrandMsg(String talker, String title, String pagePath, String ghName);
```

## 发送接龙消息

```beanshell
void sendNoteMsg(String talker, String content);
```

## 发送引用消息

```beanshell
void sendQuoteMsg(String talker, long msgId, String content);
```

## 撤回指定消息

```beanshell
void revokeMsg(long msgId);
```

## 插入系统消息

```beanshell
void insertSystemMsg(String talker, String content, long createTime);
```

## 查询历史消息

```beanshell
List<MsgInfoBean> queryHistoryMsg(String talker, long startTime, int count);
```
