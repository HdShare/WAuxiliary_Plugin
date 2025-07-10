# 朋友圈方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 上传文字

```java
void uploadText(String content);

void uploadText(String content, String sdkId, String sdkAppName);

void uploadText(JSONObject jsonObj);
```

## 上传图文

```java
void uploadTextAndPicList(String content, String picPath);

void uploadTextAndPicList(String content, String picPath, String sdkId, String sdkAppName);

void uploadTextAndPicList(String content, List picPathList);

void uploadTextAndPicList(String content, List picPathList, String sdkId, String sdkAppName);

void uploadTextAndPicList(JSONObject jsonObj);
```
