# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## 配置读
```kotlin
fun getString(key: String, defValue: String): String
fun getStringSet(key: String, defValue: Set<String>): Set<String>
fun getBoolean(key: String, defValue: Boolean): Boolean
fun getInt(key: String, defValue: Int): Int
fun getFloat(key: String, defValue: Float): Float
fun getLong(key: String, defValue: Long): Long
```

## 配置写
```kotlin
fun putString(key: String, value: String)
fun putStringSet(key: String, value: Set<String>)
fun putBoolean(key: String, value: Boolean = false)
fun putInt(key: String, value: Int = 0)
fun putFloat(key: String, value: Float = 0f)
fun putLong(key: String, value: Long = 0L)
```
