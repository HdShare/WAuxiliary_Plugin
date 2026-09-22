# 回调方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

这些方法由宿主自动调用，不需要你手动执行。

## 新手先看

- 如果你只想做自动回复，优先看 `onHandleMsg(...)`。
- 如果你想拦截发送按钮，使用 `onClickSendBtn(...)`。
- 如果你想做进群欢迎、退群提醒，使用 `onMemberChange(...)`。
- 如果你想添加聊天、主页或会话列表菜单，使用对应的 `onCreate...Menu(...)`。
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

下面三个菜单创建回调在菜单展示和点击匹配时都可能执行。不要直接在创建回调中执行发送消息、写配置等有副作用的业务逻辑，应将这些操作放进菜单项的点击 lambda。

## 创建聊天消息长按菜单

用户打开一条消息的长按菜单时触发。通过 `addChatItemMenuItem(...)` 添加菜单项。

```beanshell
void onCreateChatItemMenu(Object msgInfoBean);
```

- `msgInfoBean`：当前长按消息

### 示例

```beanshell
void onCreateChatItemMenu(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;

    addChatItemMenuItem("打印消息", "info", msg -> {
        log(msg.getContent());
    });
}
```

## 创建主页加号菜单

主页加号菜单构建时触发。通过 `addHomePopMenuItem(...)` 添加菜单项。

```beanshell
void onCreateHomePopMenu();
```

### 示例

```beanshell
void onCreateHomePopMenu() {
    addHomePopMenuItem("主页菜单", "info", () -> {
        log("主页菜单被点击");
    });
}
```

## 创建会话列表长按菜单

用户打开一个会话的长按菜单时触发。通过 `addConversationItemMenuItem(...)` 添加菜单项。

```beanshell
void onCreateConversationItemMenu(Object conversationBean);
```

- `conversationBean`：当前长按会话

### 示例

```beanshell
void onCreateConversationItemMenu(Object conversationBean) {
    addConversationItemMenuItem("打印会话", conversation -> {
        log(conversation.getUsername());
    });
}
```

菜单添加方法的完整签名和限制见 [PluginCoreApiMethod.md](./method/PluginCoreApiMethod.md)。

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
