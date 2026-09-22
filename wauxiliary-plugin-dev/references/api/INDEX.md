# WA 接口索引

本目录是 `WAuxiliary_Plugin/docs/api` 在 2026-09-20 的快照。当前工作区存在更新文档时优先使用更新版本。只读取当前任务需要的页面。

## 入口与运行时对象

- [QuickStart.md](QuickStart.md)：最小插件结构和入门示例。
- [PluginCallback.md](PluginCallback.md)：WA 回调签名、参数和返回行为。
- [PluginGlobal.md](PluginGlobal.md)：运行时注入的全局变量和插件路径。
- [PluginStruct.md](PluginStruct.md)：消息、收款、会话及相关对象字段。

## 接口方法

- [PluginCoreApiMethod.md](method/PluginCoreApiMethod.md)：聊天、主页和会话列表菜单。
- [PluginMsgMethod.md](method/PluginMsgMethod.md)：发送、查询、撤回和转发消息。
- [PluginMediaMsgMethod.md](method/PluginMediaMsgMethod.md)：媒体消息操作。
- [PluginContactMethod.md](method/PluginContactMethod.md)：好友、群聊、成员数量、标签、邀请理由和好友验证。
- [PluginConfigMethod.md](method/PluginConfigMethod.md)：类型化插件配置读写。
- [PluginHttpMethod.md](method/PluginHttpMethod.md)：异步 GET、POST 和下载。
- [PluginAudioMethod.md](method/PluginAudioMethod.md)：音频播放与控制。
- [PluginSnsMethod.md](method/PluginSnsMethod.md)：朋友圈操作。
- [PluginOtherMethod.md](method/PluginOtherMethod.md)：`loadJava`、Dex/Jar、日志、延迟、通知和重载。
- [PluginHookMethod.md](method/PluginHookMethod.md)：Hook 注册与卸载。
- [PluginDexKitMethod.md](method/PluginDexKitMethod.md)：DexKit 搜索。
- [PluginReflectMethod.md](method/PluginReflectMethod.md)：反射辅助接口。

## 使用规则

- 生成调用前确认准确签名和重载。
- 回调参数即使声明为 `Object`，仍应按照 `PluginStruct.md` 中对应的 WA 结构使用。
- 入门示例存在歧义时，以 `SKILL.md` 和 `runtime.md` 的正确性规则为准，特别是 `config.prop`、消息方向、异步结果和模块加载。
- Hook、反射和 DexKit 对版本敏感，除本目录外还必须检查当前运行时源码和同版本可用插件。
