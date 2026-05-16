# 回调方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

这些方法由宿主自动调用，不需要你手动执行。

## 新手先看

- 如果你只想做自动回复，优先看 `onHandleMsg(...)`。
- 如果你想拦截发送按钮，使用 `onClickSendBtn(...)`。
- 如果你想做进群欢迎、退群提醒，使用 `onMemberChange(...)`。
- 结构体字段请配合 [PluginStruct.md](./PluginStruct.md) 一起看。

## 最小可用模板

```beanshell
void onLoad() {
    log("plugin loaded: " + pluginName);
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (msgInfoBean.isText()) {
        String talker = msgInfoBean.getTalker();
        String content = msgInfoBean.getContent();
        if (content.equals("在吗")) {
            sendText(talker, "在");
        }
    }
}
```

## 打开插件设置

点击插件设置入口时触发。可在这里弹出说明、打开配置页，或提示当前状态。

```beanshell
void openSettings();
```

### 示例

```beanshell
void openSettings() {
    toast("这里可以打开你的配置界面");
}
```

## 插件加载

插件被加载时触发，一般用于初始化变量、注册 Hook、预加载配置等。

```beanshell
void onLoad();
```

### 示例

```beanshell
void onLoad() {
    log("plugin loaded: " + pluginName);
}
```

## 插件卸载

插件被卸载时触发，一般用于释放资源、取消定时任务、卸载 Hook。

```beanshell
void onUnload();
```

### 示例

```beanshell
void onUnload() {
    log("plugin unloaded");
}
```

## 监听收到消息

收到消息时触发。`msgInfoBean` 实际为 `MsgInfoBean`。

```beanshell
void onHandleMsg(Object msgInfoBean);
```

- `msgInfoBean`：消息对象

### 示例

```beanshell
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;

    if (msgInfoBean.isText()) {
        log("收到文本: " + msgInfoBean.getContent());
    }

    if (msgInfoBean.isAtMe()) {
        String talker = msgInfoBean.getTalker();
        String sender = msgInfoBean.getSendTalker();
        sendText(talker, "[AtWx=" + sender + "] 收到");
    }
}
```

## 单击发送按钮

点击发送按钮时触发。

```beanshell
boolean onClickSendBtn(String text);
```

- `text`：输入框中的文本
- 返回值：
  - `true`：拦截本次发送
  - `false`：不拦截，继续正常发送

### 示例

```beanshell
boolean onClickSendBtn(String text) {
    if (text.equals("测试拦截")) {
        toast("已拦截发送");
        return true;
    }
    return false;
}
```

## 监听成员变动

群成员加入、退出等事件时触发。

```beanshell
void onMemberChange(String type, String groupWxid, String userWxid, String userName);
```

- `type`：事件类型，常见值为 `join`、`left`
- `groupWxid`：群聊 ID
- `userWxid`：成员 `wxid`
- `userName`：成员显示名

### 示例

```beanshell
void onMemberChange(String type, String groupWxid, String userWxid, String userName) {
    if (type.equals("join")) {
        sendText(groupWxid, "[AtWx=" + userWxid + "] 欢迎加入");
    } else if (type.equals("left")) {
        sendText(groupWxid, userName + " 退出了群聊");
    }
}
```

## 监听好友申请

收到新的好友申请时触发。

```beanshell
void onNewFriend(String wxid, String ticket, int scene);
```

- `wxid`：申请人 `wxid`
- `ticket`：申请票据
- `scene`：来源场景值

### 示例

```beanshell
void onNewFriend(String wxid, String ticket, int scene) {
    verifyUser(wxid, ticket, scene);
}
```

## 监听收款消息

收到收款相关消息时触发。`payMsgBean` 实际为 `PayMsgBean`。

```beanshell
void onRecvPayMsg(Object payMsgBean);
```

### 示例

```beanshell
void onRecvPayMsg(Object payMsgBean) {
    log("收款人: " + payMsgBean.getDisplayName());
    log("收款金额: " + payMsgBean.getFee());
}
```
