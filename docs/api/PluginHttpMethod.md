# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## get
```kotlin
fun get(url: String, headerMap: Map<String, String>?, callback: PluginCallBack.HttpCallback)
```

## post
```kotlin
fun post(url: String, paramMap: Map<String, String>?, headerMap: Map<String, String>?, callback: PluginCallBack.HttpCallback)
```

## download
```kotlin
fun download(url: String, path: String, headerMap: Map<String, String>?, callback: PluginCallBack.DownloadCallback)
```
