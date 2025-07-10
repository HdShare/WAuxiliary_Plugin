# 配置方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 读取方法

```java
String getString(String key, String defValue);

Set getStringSet(String key, Set defValue);

boolean getBoolean(String key, boolean defValue);

int getInt(String key, int defValue);

float getFloat(String key, float defValue);

long getLong(String key, long defValue);
```

## 写入方法

```java
void putString(String key, String value);

void putStringSet(String key, Set value);

void putBoolean(String key, boolean value);

void putInt(String key, int value);

void putFloat(String key, float value);

void putLong(String key, long value);
```
