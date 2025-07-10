# 回调方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 监听收到消息

```java
void onHandleMsg(Object msgInfoBean);
```

## 长按发送按钮

```java
boolean onLongClickSendBtn(String text);
```

## 监听成员变动

```java
void onMemberChange(String type, String groupWxid, String userWxid, String userName);
```

## 监听好友申请

```java
void onNewFriend(String wxid, String ticket, int scene);
```
