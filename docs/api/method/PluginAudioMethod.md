# 音频方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- 音频转换最常见用途是“把普通音频转成可发送的语音文件”。
- 路径参数都要求是本地文件路径。
- 生成的临时文件通常建议放在 `cacheDir`。

## mp3 转 Silk

将 MP3 音频转换为微信语音常用的 Silk 格式。

```beanshell
int mp3ToSilk(String mp3Path, String silkPath);

int mp3ToSilk(String mp3Path, String silkPath, int hz);
```

- `mp3Path`：源 MP3 文件路径
- `silkPath`：输出 Silk 文件路径
- `hz`：采样率，默认 `24000`
- 返回值：转换结果码，通常 `0` 表示成功

## 示例

```beanshell
int code = mp3ToSilk("/sdcard/test.mp3", cacheDir + "/test.silk");
log("convert result = " + code);
```

## Silk 转 mp3

将 Silk 语音转换为 MP3 文件，便于外部播放器或后续处理。

```beanshell
int silkToMp3(String silkPath, String mp3Path);

int silkToMp3(String silkPath, String mp3Path, int hz);
```

- `silkPath`：源 Silk 文件路径
- `mp3Path`：输出 MP3 文件路径
- `hz`：采样率，默认 `24000`
- 返回值：转换结果码，通常 `0` 表示成功

## 示例

```beanshell
int code = silkToMp3(cacheDir + "/voice.silk", cacheDir + "/voice.mp3");
log("convert result = " + code);
```

## 取音频时长

返回音频文件时长，单位为毫秒。

```beanshell
long getDuration(String filePath);
```

- `filePath`：音频文件路径
- 返回值：音频时长，单位毫秒

## 示例

```beanshell
long duration = getDuration(cacheDir + "/voice.mp3");
log("duration = " + duration + " ms");
```
