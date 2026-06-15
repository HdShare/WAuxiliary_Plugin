# 微信平板模式

作者：叶昕蓝yu

WAuxiliary 插件，修改设备信息使微信以平板模式运行，实现手机与平板同时在线。

## 安装

将 `WeChatPad` 文件夹放入 WAuxiliary 插件目录，启用后重启微信。

## 配置

编辑 `config.prop`（首次运行自动生成）：

```properties
enabled=true
model=Lenovo TB-9707F
brand=Lenovo
```

## 原理

微信启动时读取 `android.os.Build.MODEL` 判断设备类型。插件在微信加载前将 MODEL 改为平板型号即可。

## 兼容

- WAuxiliary 最新版
- 微信 8.0.30+
- Android 8.0+
