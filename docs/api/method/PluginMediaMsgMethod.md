# 媒体方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1357 版本
:::

## 发送媒体

```beanshell
void sendMediaMsg(String talker, MediaMessage mediaMessage, String appId);
```

## 分享文件

```beanshell
void shareFile(String talker, String title, String filePath, String appId);
```

## 分享小程序

```beanshell
void shareMiniProgram(String talker, String title, String description, String userName, String path, byte[] thumbData, String appId);
```

## 分享音乐

```beanshell
void shareMusic(String talker, String title, String description, String musicUrl, String musicDataUrl, byte[] thumbData, String appId);
```

## 分享音乐视频

```beanshell
void shareMusicVideo(String talker, String title, String description, String musicUrl, String musicDataUrl, String singerName, String duration, String songLyric, byte[] thumbData, String appId);
```

## 分享文本

```beanshell
void shareText(String talker, String text, String appId);
```

## 分享视频

```beanshell
void shareVideo(String talker, String title, String description, String videoUrl, byte[] thumbData, String appId);
```

## 分享网页

```beanshell
void shareWebpage(String talker, String title, String description, String webpageUrl, byte[] thumbData, String appId);
```
