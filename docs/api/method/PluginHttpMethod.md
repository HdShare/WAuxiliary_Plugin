# 网络方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1368 版本
:::

## get

```beanshell
void get(String url, Map<String, String> headerMap, Consumer<String> callback);

void get(String url, Map<String, String> headerMap, long timeout, Consumer<String> callback);
```

## post

```beanshell
void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, Consumer<String> callback);

void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, long timeout, Consumer<String> callback);
```

## download

```beanshell
void download(String url, String path, Map<String, String> headerMap, Consumer<File> callback);

void download(String url, String path, Map<String, String> headerMap, long timeout, Consumer<File> callback);
```
