# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## 发送普通文本
```kotlin
fun sendText(talker: String, content: String)
```
```java
sendText("52320127241@chatroom", "你好");

sendText("52320127241@chatroom", "[AtWx=wxid_j3pjhcu3brcy22] 你好");
```

## 发送语音消息
```kotlin
fun sendVoice(talker: String, sendPath: String)
```
```java
sendVoice("52320127241@chatroom", pluginDir + "/voice.silk");
```

## 发送图片消息
```kotlin
fun sendImage(talker: String, sendPath: String)
```
```java
sendImage("52320127241@chatroom", pluginDir + "/image.png");
```

## 发送图片消息
```kotlin
fun sendImage(talker: String, sendPath: String, appId: String)
```
```java
sendImage("52320127241@chatroom", pluginDir + "/image.png", "wx1c37343fc2a86bc4");
```

## 发送表情消息
```kotlin
fun sendEmoji(talker: String, sendPath: String)
```
```java
sendEmoji("52320127241@chatroom", pluginDir + "/emoji.png");
```

## 发送拍一拍
```kotlin
fun sendPat(talker: String, pattedUser: String)
```
```java
sendPat("52320127241@chatroom", "wxid_j3pjhcu3brcy22");
```

## 发送分享名片
```kotlin
fun sendShareCard(talker: String, wxid: String)
```
```java
sendShareCard("52320127241@chatroom", "wxid_j3pjhcu3brcy22");

sendShareCard("52320127241@chatroom", "gh_d58b28374eaa");
```

## 发送位置消息
```kotlin
fun sendLocation(talker: String, poiName: String, label: String, x: String, y: String, scale: String)
```
```java
sendLocation("52320127241@chatroom", "标题", "子标题", "30", "120", "15");
```

## 发送媒体消息
```kotlin
fun sendMediaMsg(talker: String, mediaMessage: Any, appId: String)
```

## 发送文本卡片
```kotlin
fun sendTextCard(talker: String, text: String, appId: String)
```
```java
sendTextCard("52320127241@chatroom", "原神, 启动!", "wx1c37343fc2a86bc4");
```

## 发送音乐卡片
```kotlin
fun sendMusicCard(talker: String, title: String, description: String, playUrl: String, infoUrl: String, appId: String)
```
```java
sendMusicCard("52320127241@chatroom", "与死神对话", "熊猫涂杰", "http://music.163.com/song/media/outer/url?id=1961408503", "http://music.163.com/song/1961408503", "wx8dd6ecd81906fd84");
```

## 发送网页卡片
```kotlin
fun sendWebpageCard(talker: String, title: String, description: String, webpageUrl: String, appId: String)
```
```java
sendWebpageCard("52320127241@chatroom", "WAuxiliary", "WA插件文档", "https://wauxv.apifox.cn/", "wxc87ca23cfe029db3");
```

## 发送密文消息
```kotlin
fun sendCipherMsg(talker: String, title: String, content: String)
```
```java
sendCipherMsg("52320127241@chatroom", "显示标题", "加密内容");
```

## 发送接龙消息
```kotlin
fun sendNoteMsg(talker: String, content: String)
```
```java
sendNoteMsg("52320127241@chatroom", "接龙内容");
```

## 发送引用消息
```kotlin
fun sendQuoteMsg(talker: String, msgId: Long, content: String)
```
```java
sendQuoteMsg("52320127241@chatroom", 114514L, "回复内容");
```

## 发送撤回请求
```kotlin
fun sendRevokeMsg(msgId: Long)
```
```java
sendRevokeMsg(114514L);
```
