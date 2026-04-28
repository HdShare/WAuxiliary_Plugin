# 回调方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1357 版本
:::

## 插件设置

```beanshell
void openSettings();
```

## 插件加载

```beanshell
void onLoad();
```

## 插件卸载

```beanshell
void onUnload();
```

## 监听收到消息

```beanshell
void onHandleMsg(Object msgInfoBean);
```

## 单击发送按钮

```beanshell
boolean onClickSendBtn(String text);
```

## 监听成员变动

```beanshell
void onMemberChange(String type, String groupWxid, String userWxid, String userName);
```

## 监听好友申请

```beanshell
void onNewFriend(String wxid, String ticket, int scene);
```
