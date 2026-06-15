# 网络方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

所有请求均为异步回调方式，不要把它当作同步返回值来用。

## 新手先看

- 请求结果在 `callback` 里拿，不在方法返回值里拿。
- `headerMap`、`paramMap` 不需要时可以传 `null`。
- 下载完成后的文件路径，通常从 `file.getAbsolutePath()` 取。

## GET 请求

```beanshell
void get(String url, Map<String, String> headerMap, Consumer<String> callback);

void get(String url, Map<String, String> headerMap, long timeout, Consumer<String> callback);
```

- `url`：请求地址
- `headerMap`：请求头，可传 `null`
- `timeout`：超时时间，单位秒，默认 `30`
- `callback`：响应文本回调，失败时可能为 `null`

## 示例

```beanshell
get("https://example.com", null, body -> {
    log(body);
});
```

## POST 请求

```beanshell
void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, Consumer<String> callback);

void post(String url, Map<String, String> paramMap, Map<String, String> headerMap, long timeout, Consumer<String> callback);
```

- `paramMap`：请求参数，可传 `null`
- `headerMap`：请求头，可传 `null`
- 当 `Content-Type` 包含 `application/json` 时，参数会按 JSON 发送
- `callback`：响应文本回调，失败时可能为 `null`

## 示例

```beanshell
var params = new java.util.HashMap();
params.put("q", "hello");

post("https://example.com/api", params, null, body -> {
    log(body);
});
```

## 下载文件

```beanshell
void download(String url, String path, Map<String, String> headerMap, Consumer<File> callback);

void download(String url, String path, Map<String, String> headerMap, long timeout, Consumer<File> callback);
```

- `url`：下载地址
- `path`：保存路径；可以是文件路径或下载目录，具体以宿主下载实现为准
- `headerMap`：请求头，可传 `null`
- `timeout`：超时时间，单位秒，默认 `30`
- `callback`：下载完成后的文件回调，失败时可能为 `null`

## 示例

```beanshell
String imgUrl = "https://example.com/a.jpg";

download(imgUrl, cacheDir + "/demo.jpg", null, file -> {
    if (file != null) {
        log(file.getAbsolutePath());
        sendImage(getTargetTalker(), file.getAbsolutePath());
    }
});
```
