# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## 日志
```kotlin
fun log(text: String)
```

## 提示
```kotlin
fun toast(text: String)
```

## 通知
```kotlin
fun notify(titleStr: String, textStr: String)
```

## 取顶部Activity
```kotlin
fun getTopActivity(): Activity?
```

## 插入系统提示
```kotlin
fun insertSystemMsg(talker: String, content: String, createTime: Long)
```
```java
insertSystemMsg("52320127241@chatroom", "这是一条系统消息", System.currentTimeMillis());
```

## 上传设备步数
```kotlin
fun sendUploadDeviceStep(step: Long)
```
```java
sendUploadDeviceStep(98800L);
```
