# 其他方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- 这组方法是杂项能力集合，包含日志、提示、延迟执行、导入额外文件等。
- 最常用的是 `log(...)`、`toast(...)`、`delay(...)`。
- `loadJava`、`loadDex`、`loadJar` 偏进阶，普通插件通常用不到。

## 执行代码

在当前插件运行时内继续执行一段脚本代码。

```beanshell
void eval(String code);
```

## 示例

```beanshell
eval("log(\"hello from eval\")");
```

## 导入 Java 源文件

```beanshell
void loadJava(String path);
```

- `path`：支持绝对路径；相对路径默认相对于当前插件目录，也就是 `pluginDir`

## 导入 Dex

```beanshell
void loadDex(String path);
```

- `path`：支持绝对路径；相对路径默认相对于当前插件目录，也就是 `pluginDir`

## 导入 Jar

```beanshell
void loadJar(String path);
```

- `path`：支持绝对路径；相对路径默认相对于当前插件目录，也就是 `pluginDir`

## 示例

```beanshell
loadJar("libs/demo.jar");
loadDex("libs/bridge.dex");
loadJava("extra/Main.java");
```

## 日志

```beanshell
void log(Object msg);
```

- 输出调试日志到插件日志区域

## 提示

```beanshell
void toast(String text);
```

- 会自动带上当前插件 ID 前缀

## 示例

```beanshell
toast("执行成功");
log("done");
```

## 延迟执行

```beanshell
void delay(long millis, Runnable action);
```

- `millis`：延迟毫秒数
- `action`：延迟后执行的回调

## 示例

```beanshell
delay(3000, () -> {
    toast("3 秒后执行");
});
```

## 通知

```beanshell
void notify(String title, String text);
```

- 系统通知标题会自动带上当前插件 ID 前缀

## 取顶部 Activity

```beanshell
Activity getTopActivity();
```

- 返回值：当前顶部 `Activity`，取不到时为 `null`

## 上传设备步数

```beanshell
void uploadDeviceStep(long step);
```

- `step`：要上传的步数
