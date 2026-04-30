# 朋友圈方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1368 版本
:::

## 上传文字

```beanshell
void uploadText(String content);

void uploadText(String content, String sdkId, String sdkAppName);

void uploadText(JSONObject jsonObj);
```

## 上传图文

```beanshell
void uploadTextAndPicList(String content, String picPath);

void uploadTextAndPicList(String content, String picPath, String sdkId, String sdkAppName);

void uploadTextAndPicList(String content, List<String> picPathList);

void uploadTextAndPicList(String content, List<String> picPathList, String sdkId, String sdkAppName);

void uploadTextAndPicList(JSONObject jsonObj);
```
