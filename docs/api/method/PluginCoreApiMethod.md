# 菜单方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

这组方法用于让插件向聊天消息长按菜单、主页加号菜单和会话列表长按菜单添加自定义项目。

## 新手先看

- 菜单添加方法只能在对应的菜单创建回调中同步调用，在其他位置调用会被忽略。
- 菜单创建回调可能在菜单展示和点击匹配时重复执行。发送消息、写配置等业务逻辑必须放进点击 lambda，不要直接写在创建回调中。
- 空标题会被忽略；同一插件在同一次构建中添加相同标题时，只保留第一项。
- 聊天菜单和主页菜单的 `icon` 是内置图标名称。当前版本所有字符串均使用默认菜单图标，不读取插件文件路径。

## 聊天消息长按菜单

只能在 `onCreateChatItemMenu(Object msgInfoBean)` 中调用。

```beanshell
void addChatItemMenuItem(String title, String icon, Consumer<MsgInfoBean> action);
```

- `title`：菜单标题
- `icon`：内置图标名称
- `action`：点击回调，参数是被点击消息对应的 `MsgInfoBean`

### 示例

```beanshell
void onCreateChatItemMenu(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;

    addChatItemMenuItem("打印消息", "info", msg -> {
        log("msgId = " + msg.getMsgId());
        log("content = " + msg.getContent());
    });
}
```

## 主页加号菜单

只能在 `onCreateHomePopMenu()` 中调用。

```beanshell
void addHomePopMenuItem(String title, String icon, Runnable action);
```

- `title`：菜单标题
- `icon`：内置图标名称
- `action`：无参数点击回调

### 示例

```beanshell
void onCreateHomePopMenu() {
    addHomePopMenuItem("主页菜单", "info", () -> {
        log("主页菜单被点击");
    });
}
```

## 会话列表长按菜单

只能在 `onCreateConversationItemMenu(Object conversationBean)` 中调用。该菜单的宿主接口没有图标参数。

```beanshell
void addConversationItemMenuItem(String title, Consumer<ConversationBean> action);
```

- `title`：菜单标题
- `action`：点击回调，参数是被点击会话对应的 `ConversationBean`

### 示例

```beanshell
void onCreateConversationItemMenu(Object conversationBean) {
    addConversationItemMenuItem("打印会话", conversation -> {
        log("username = " + conversation.getUsername());
        log("content = " + conversation.getContent());
    });
}
```
