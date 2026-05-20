# 全局变量

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

以下变量可在插件脚本中直接使用，无需自行声明。

## 新手先看

- 想发文件、下载图片，最常用的是 `cacheDir`。
- 想读写插件自己的文件，最常用的是 `pluginDir`。
- 想打印当前插件信息，最常用的是 `pluginName`、`pluginVersion`。
- 想做版本兼容判断，可先打印 `hostVerName`、`hostVerCode`、`hostVerClient`。

## 宿主相关

- `hostContext`
  - 宿主 `Context`
  - 常见用途：取包名、取系统服务、创建路径
  - 示例：`log(hostContext.getPackageName());`

- `hostVerName`
  - 宿主版本名
  - 常见用途：日志输出、版本判断

- `hostVerCode`
  - 宿主版本号
  - 常见用途：做兼容分支

- `hostVerClient`
  - 宿主客户端标识
  - 常见用途：排查不同版本差异
  - 建议：先 `log(hostVerClient);` 再根据实际值做判断

- `moduleVer`
  - 模块版本
  - 常见用途：日志输出、兼容排查

## 路径相关

- `cacheDir`
  - 当前插件可用缓存目录
  - 常见用途：下载临时文件、生成图片、转码语音
  - 示例：`String imgPath = cacheDir + "/demo.jpg";`

- `pluginDir`
  - 当前插件目录
  - 常见用途：读取配置、放自带资源、加载额外文件
  - 示例：`String cfgPath = pluginDir + "/config.prop";`

## 插件自身信息

- `pluginId`
  - 插件唯一标识

- `pluginName`
  - 插件名称

- `pluginAuthor`
  - 插件作者

- `pluginVersion`
  - 插件版本

- `pluginUpdateTime`
  - 插件更新时间

### 示例

```beanshell
void onLoad() {
    log("插件名称: " + pluginName);
    log("插件版本: " + pluginVersion);
    log("缓存目录: " + cacheDir);
    log("插件目录: " + pluginDir);
    log("宿主版本: " + hostVerName + " (" + hostVerCode + ")");
    log("hostVerClient = " + hostVerClient);
}
```
