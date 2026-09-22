# WA 插件分层结构

只有在模块职责更清楚时才进行分层。只有一个短回调的小插件可以继续使用单个 `main.java`。

## 推荐目录

```text
PluginName/
|-- info.prop
|-- main.java
`-- src/
    |-- Config.java
    |-- Network.java
    |-- Service.java
    `-- SettingsUi.java
```

这些文件名只是示例。只创建功能确实需要的模块，并按用途命名。

插件根目录已经提供业务上下文时，`src` 文件名优先使用 `Config.java`、`Service.java`、`Renderer.java`、`JsonBuilder.java`、`SettingsUi.java` 等职责短名。例如在 `PluginName/src/` 中使用 `JsonBuilder.java`。同一插件存在多个同类模块或短名会造成歧义时，再添加必要的业务限定词。

## 精简入口脚本

在顶层加载模块，确保回调使用其定义前已经完成加载：

```beanshell
loadJava("src/Config.java");
loadJava("src/Network.java");
loadJava("src/Service.java");
loadJava("src/SettingsUi.java");

void onLoad() {
    pluginServiceOnLoad();
}

void onUnload() {
    pluginServiceOnUnload();
}

void onHandleMsg(Object msgInfoBean) {
    pluginServiceHandleMsg(msgInfoBean);
}

boolean onClickSendBtn(String text) {
    return pluginServiceHandleSend(text);
}

void openSettings() {
    showPluginSettings();
}
```

`main.java` 只保留：

- 明确的模块加载顺序；
- WA 回调和必要的输入判断；
- 入口模块需要共享的 Hook 或任务引用；
- 调用对应的功能模块。

## 加载顺序

一种常用加载顺序是：

```text
Config -> Network -> Service -> SettingsUi -> main callbacks
```

箭头表示左侧模块先加载。每个插件只使用实际需要的模块，模块只能调用左侧已经加载的模块，不能循环依赖。加载顺序统一写在 `main.java` 中。

文件名可以利用插件目录保持简短，但所有脚本会在同一运行环境中执行，顶层函数名可能冲突，因此应使用 `featureServiceHandleMsg` 等带功能前缀的名称。当前 BeanShell 支持时，也可以使用辅助类隔离顶层名称。

## 职责划分

- WA 对象和回调规则保留在入口或服务模块。
- 可复用的解析和业务函数尽量接收普通值，不直接依赖完整的 WA 对象。
- 网络模块负责异步传输，服务模块决定目标会话和用户反馈。
- 设置界面构建与配置读写分开。
- Hook 注册返回的引用由负责清理的模块保存，并在 `onUnload()` 中通过对应的卸载函数释放。

## 模块加载

使用 `loadJava("src/Service.java")` 执行另一个插件脚本。
