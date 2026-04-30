# 其他方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1368 版本
:::

## 执行

```beanshell
void eval(String code);
```

## 导入Java

```beanshell
void loadJava(String path);
```

## 导入Dex

```beanshell
void loadDex(String path);
```

## 日志

```beanshell
void log(Object msg);
```

## 延迟

```beanshell
void delay(long millis, Runnable action);
```

## 提示

```beanshell
void toast(String text);
```

## 通知

```beanshell
void notify(String title, String text);
```

## 取顶部Activity

```beanshell
Activity getTopActivity();
```

## 上传设备步数

```beanshell
void uploadDeviceStep(long step);
```
