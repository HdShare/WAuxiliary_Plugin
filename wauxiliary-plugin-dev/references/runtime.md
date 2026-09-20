# WA 运行时规则

本文用于指导实现决策，不替代完整接口文档。编写代码前仍需在 `references/api/` 或当前工作区文档中核对准确签名。

## 源文件与 info.prop

插件必需的源文件：

```text
PluginName/
|-- info.prop
`-- main.java
```

`info.prop` 必需字段：

```properties
name = 插件名称
author = 作者
version = 1.0.0
updateTime = 20260920
```

`updateTime` 必须是有效的 `YYYYMMDD` 日期。`config.prop` 由配置接口在首次使用时生成。`readme.md` 仅在仓库规范或用户要求时添加。

## 脚本运行方式

- WA 按 BeanShell 规则执行 `.java` 文件，开发和验证均以这种运行方式为准。
- 根据当前示例，脚本可以包含顶层函数、变量、导入和辅助类。
- 文件和流资源采用 WA 当前运行时兼容的普通 `try/catch/finally`，并在 `finally` 中明确调用 `close()`。try-with-resources 的 `try (...)` 语法可能在加载阶段解析失败。
- `loadJava(String path)` 在当前插件运行时执行其他源码；相对路径以 `pluginDir` 为基准。

## 回调说明

- `onLoad()`：初始化状态和注册项。
- `onUnload()`：释放 Hook 和其他长期资源。
- `onHandleMsg(Object msgInfoBean)`：处理消息事件。
- `onClickSendBtn(String text)`：仅在需要拦截本次发送时返回 `true`。
- `openSettings()`：打开设置界面或显示插件状态。
- 菜单回调可能在构建或匹配菜单时重复执行，发消息、写配置等实际操作必须放在注册的操作 lambda 中。

普通入站自动处理示例：

```beanshell
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (!msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent();
    // 完成必要判断后再交给业务模块。
}
```

回复事件来源会话时使用 `msgInfoBean.getTalker()`。只有针对当前打开会话的主动操作才使用 `getTargetTalker()`。

## 网络请求

`get`、`post` 和 `download` 都通过回调异步返回，失败结果可能为 `null`。

```beanshell
String requestTalker = msgInfoBean.getTalker();
get(url, null, body -> {
    if (body == null) {
        sendText(requestTalker, "请求失败");
        return;
    }
    handleResponse(requestTalker, body);
});
```

请求开始前保存发起消息的 `talker`。响应返回后再调用 `getTargetTalker()`，可能因为用户切换会话而发错位置。

## 群聊与联系人

- `getGroupList()` 返回群聊记录。
- `getGroupMemberList(groupWxid)` 返回群成员 wxid。
- `getGroupMemberCount(groupWxid)` 返回群成员数量。
- `addChatroomMember` 和 `inviteChatroomMember` 同时提供单成员、成员列表以及可选 `reason` 重载。
- `reason` 是当前用户邀请他人进群时提交给群主或管理员的申请理由。

插件自有变量使用明确的群聊命名：

```beanshell
var selectedGroupWxids = new ArrayList();
int groupCount = selectedGroupWxids.size();
String reason = "项目协作需要，申请邀请该成员进群";
inviteChatroomMember(groupWxid, inviteMemberWxid, reason);
```

`reason` 表示邀请者提交的入群申请理由；`groupCount` 始终由已选群聊集合计算。

## 生命周期

需要撤销的操作必须保存注册时返回的对象。`onLoad()` 注册 Hook、广播、观察者或重复任务后，`onUnload()` 必须对应卸载或取消，并清空保存的引用。除了首次加载，还要检查插件重载行为。
