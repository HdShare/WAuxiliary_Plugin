# WA 资料位置

优先使用当前工作区中的资料。以下路径对应当前 WAuxiliary 插件仓库；仓库位置变化时查找等价文件。

本 Skill 在 `references/api/` 中保存了一份接口文档快照。工作区有更新的文档时优先使用更新版本；默认从 `references/api/INDEX.md` 开始查找。

## 优先查找的文档

- `docs/api/QuickStart.md`：最小插件结构和基础示例。
- `docs/api/PluginCallback.md`：回调和返回值说明。
- `docs/api/PluginGlobal.md`：插件信息和路径等运行时全局变量。
- `docs/api/PluginStruct.md`：消息及回调对象的方法。
- `docs/api/method/PluginCoreApiMethod.md`：菜单和核心辅助方法。
- `docs/api/method/PluginMsgMethod.md`：消息发送与查询。
- `docs/api/method/PluginContactMethod.md`：联系人、群聊、成员数量和邀请申请理由。
- `docs/api/method/PluginHttpMethod.md`：异步网络和下载接口。
- `docs/api/method/PluginConfigMethod.md`：类型化配置接口。
- `docs/api/method/PluginOtherMethod.md`：`loadJava`、路径、日志、延迟和重载。
- `docs/api/method/PluginHookMethod.md`、`PluginDexKitMethod.md`：版本敏感的高级能力。

调用接口前先查清方法名和重载。`references/api/` 保持了相同的目录结构。

## 文档不明确时检查源码

在 WAuxiliary 应用仓库中检查：

- `app/src/main/kotlin/me/hd/wauxv/plugin/PluginRuntime.kt`
- `app/src/main/kotlin/me/hd/wauxv/plugin/api/method/`

例如，`PluginOtherMethod.kt` 可以确认 `loadJava` 的相对路径会先按插件目录解析，再交给 `PluginRuntime.loadJava`。

## 可参考的现有插件

在 `plugins/<version>/<author>/<plugin>/` 中优先选择与目标 WA 版本一致的参考：

- 用短小插件确认 BeanShell 脚本语法、最小目录和基础回调。
- 用同类功能插件确认接口的实际调用方式，但仍要重新检查消息方向、`null` 和异步回调的会话目标。
- 涉及 Hook 时，选择会保存 Hook 返回值并在 `onUnload()` 卸载的实现。
- 涉及界面、配置或生命周期时，选择能正确处理插件重新加载的实现。
- 涉及多文件结构时，确认它通过 `loadJava(...)` 加载模块；模块名称和拆分方式仍按当前架构规则重新判断。

示例只能证明某种写法曾经运行；接口行为仍以当前文档和运行时源码为准。
