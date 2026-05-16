# 配置方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

配置数据保存于插件目录下的 `config.prop` 文件，适合保存插件自己的轻量配置。

## 新手先看

- 想保存开关、字符串、数字配置，就用这组方法。
- 不需要自己关心文件创建，写入时会自动处理。
- 读取配置时记得提供默认值。

## 读取配置

```beanshell
String getString(String key, String defValue);

Set<String> getStringSet(String key, Set<String> defValue);

boolean getBoolean(String key, boolean defValue);

int getInt(String key, int defValue);

float getFloat(String key, float defValue);

long getLong(String key, long defValue);
```

- `key`：配置键名
- `defValue`：未命中或格式错误时返回的默认值
- 返回值：对应类型的配置值

## 示例

```beanshell
String apiKey = getString("api_key", "");
boolean enabled = getBoolean("enabled", false);
int maxCount = getInt("max_count", 10);
```

## 写入配置

```beanshell
void putString(String key, String value);

void putStringSet(String key, Set<String> value);

void putBoolean(String key, boolean value);

void putInt(String key, int value);

void putFloat(String key, float value);

void putLong(String key, long value);
```

- `key`：配置键名
- `value`：要保存的值

## 示例

```beanshell
putString("api_key", "sk-xxx");
putBoolean("enabled", true);
putInt("max_count", 20);
```

## 完整示例

```beanshell
void onLoad() {
    boolean enabled = getBoolean("enabled", false);
    if (!enabled) {
        putBoolean("enabled", true);
        putString("first_start_time", "" + System.currentTimeMillis());
    }

    log("enabled = " + getBoolean("enabled", false));
}
```
