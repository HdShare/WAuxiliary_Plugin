# 快速开始

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 你先知道这几件事

- 插件脚本常见入口是 `onLoad()` 和 `onHandleMsg(Object msgInfoBean)`。
- 收到消息后的判断，大多从 `msgInfoBean.isText()`、`msgInfoBean.getContent()`、`msgInfoBean.getTalker()` 开始。
- 想给当前聊天发消息，通常直接用 `getTargetTalker()`。
- 临时文件、下载文件、生成图片，通常放在 `cacheDir`。

## 最小插件目录结构

```text
YourPlugin/
├─ info.prop
├─ main.java
└─ config.prop
```

- `info.prop`：插件基本信息
- `main.java`：主脚本入口
- `config.prop`：插件运行后可能自动生成的配置文件

如果文档里提到“相对路径”，默认都是相对于当前插件目录，也就是 `pluginDir`。

## 最小自动回复示例

```beanshell
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (!msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent();

    if (content.equals("在吗")) {
        sendText(talker, "在");
    }
}
```

## 最小群欢迎示例

```beanshell
void onMemberChange(String type, String groupWxid, String userWxid, String userName) {
    if (type.equals("join")) {
        sendText(groupWxid, "[AtWx=" + userWxid + "] 欢迎加入");
    }
}
```

## 最小下载并发送图片示例

```beanshell
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (!msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent();

    if (content.equals("来张图")) {
        download("https://example.com/a.jpg", cacheDir + "/demo.jpg", null, file -> {
            if (file != null) {
                sendImage(talker, file.getAbsolutePath());
            }
        });
    }
}
```

## 新手最常用页面

- 回调入口看 [PluginCallback.md](./PluginCallback.md)
- 全局变量看 [PluginGlobal.md](./PluginGlobal.md)
- 消息结构看 [PluginStruct.md](./PluginStruct.md)
- 发消息看 [PluginMsgMethod.md](./method/PluginMsgMethod.md)
- 联系人和群信息看 [PluginContactMethod.md](./method/PluginContactMethod.md)
- 网络请求看 [PluginHttpMethod.md](./method/PluginHttpMethod.md)
