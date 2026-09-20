# 朋友圈方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- 这组方法用于发朋友圈，不是发聊天消息。
- 文字和图片路径都建议提前准备好。
- 多图时优先用 `List<String>` 版本。

## 上传文字

```beanshell
void uploadText(String content);

void uploadText(String content, String sdkId, String sdkAppName);

void uploadText(JSONObject jsonObj);
```

- `content`：朋友圈文本
- `sdkId`：第三方 SDK 标识
- `sdkAppName`：第三方应用名
- `jsonObj`：可包含 `content`、`sdkId`、`sdkAppName`

## 示例

```beanshell
uploadText("今天状态不错");
```

## 上传图文

```beanshell
void uploadTextAndPicList(String content, String picPath);

void uploadTextAndPicList(String content, String picPath, String sdkId, String sdkAppName);

void uploadTextAndPicList(String content, List<String> picPathList);

void uploadTextAndPicList(String content, List<String> picPathList, String sdkId, String sdkAppName);

void uploadTextAndPicList(JSONObject jsonObj);
```

- `picPath`：单张图片路径
- `picPathList`：多张图片路径列表
- `jsonObj`：可包含 `content`、`picPathList`、`sdkId`、`sdkAppName`

## 示例

```beanshell
uploadTextAndPicList("测试内容", cacheDir + "/1.jpg");
```
