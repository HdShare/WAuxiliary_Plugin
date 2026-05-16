# Hook 方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

该组方法对 Xposed Hook 做了轻量封装，便于插件直接注册和卸载 Hook。

## 新手先看

- 如果你只是做自动回复、群管、发消息，通常不需要 Hook。
- Hook 更适合进阶场景，比如改宿主行为、拦截内部方法。
- 记得在 `onUnload()` 里卸载你自己注册的 Hook。

## 前置 Hook

```beanshell
HookHandle hookBefore(Member member, Consumer<XC_MethodHook.MethodHookParam> callback);
```

- `member`：目标方法或构造函数
- `callback`：原方法执行前触发
- 返回值：`HookHandle`

## 后置 Hook

```beanshell
HookHandle hookAfter(Member member, Consumer<XC_MethodHook.MethodHookParam> callback);
```

- `callback`：原方法执行后触发
- 返回值：`HookHandle`

## 替换 Hook

```beanshell
HookHandle hookReplace(Member member, Function<XC_MethodHook.MethodHookParam, Object> callback);
```

- `callback`：直接替换原方法逻辑，返回值即原方法返回值
- 返回值：`HookHandle`

## 卸载 Hook

```beanshell
void unhook(HookHandle handle);
```

- `handle`：由 `hookBefore`、`hookAfter`、`hookReplace` 返回的句柄

## 示例

```beanshell
var onBeforeHook = null;

void onLoad() {
    var method = com.tencent.mm.ui.MoreTabUI.class.getDeclaredMethod("onResume");
    onBeforeHook = hookBefore(method, param -> {
        log("onResume before");
    });
}

void onUnload() {
    if (onBeforeHook != null) {
        unhook(onBeforeHook);
        onBeforeHook = null;
    }
}
```

## 回调参数常用字段

`MethodHookParam` 常用内容如下：

```beanshell
param.thisObject
param.args
param.getResult()
param.setResult(...)
```
