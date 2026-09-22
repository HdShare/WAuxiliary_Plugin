# Reflect 方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

该组方法基于 KavaRef，对常见反射查询、调用、字段访问做了简化封装。

## 新手先看

- `instance` 可以传对象实例，也可以传 `SomeClass.class`。
- 如果你只是想直接调用方法，优先用 `invokeMethod(...)`。
- 如果重载很多，优先传 `paramCount`。
- 反射适合进阶用法；如果普通 API 已能解决，优先使用普通 API。

## 查找第一个方法

```beanshell
Method firstMethod(Object instance, String methodName);

Method firstMethod(Object instance, String methodName, int paramCount);
```

- `instance`：对象实例或 `Class`
- `methodName`：方法名
- `paramCount`：参数个数
- 返回值：命中的 `Method`

## 示例

```beanshell
var method = firstMethod(java.lang.String.class, "substring", 2);
log(method);
```

## 查找第一个构造函数

```beanshell
Constructor firstConstructor(Object instance, int paramCount);
```

- `instance`：对象实例或 `Class`
- `paramCount`：构造参数个数
- 返回值：命中的 `Constructor`

## 查找第一个字段

```beanshell
Field firstField(Object instance, String fieldName);
```

- `fieldName`：字段名
- 返回值：命中的 `Field`

## 示例

```beanshell
class DemoBean {
    String name = "demo";
}

var demo = new DemoBean();
var field = firstField(demo, "name");
log(field);
```

## 调用方法

```beanshell
Object invokeMethod(Object instance, String methodName);

Object invokeMethod(Object instance, String methodName, Object[] params);

Object invokeMethod(Object instance, String methodName, int paramCount);

Object invokeMethod(Object instance, String methodName, int paramCount, Object[] params);
```

- `invokeMethod(instance, methodName)`：调用无参方法
- `invokeMethod(instance, methodName, Object[] params)`：按方法名调用，并传入参数
- `invokeMethod(instance, methodName, int paramCount)`：按参数个数筛选后调用，通常只适合无参调用时进一步限定重载
- `invokeMethod(instance, methodName, int paramCount, Object[] params)`：重载较多时推荐使用
- 返回值：目标方法返回值，无返回值时为 `null`

## 示例

```beanshell
String text = "hello";

var upper = invokeMethod(text, "toUpperCase");
log(upper);

var sub = invokeMethod(text, "substring", 2, new Object[]{1, 3});
log(sub);
```

## 创建实例

```beanshell
Object createInstance(Object instance, int paramCount);

Object createInstance(Object instance, int paramCount, Object[] params);
```

- `instance`：对象实例或 `Class`
- `paramCount`：构造参数个数
- 返回值：新创建的对象

## 示例

```beanshell
var sb = createInstance(java.lang.StringBuilder.class, 1, new Object[]{"demo"});
log(sb.toString());
```

## 读取字段

```beanshell
Object getField(Object instance, String fieldName);
```

- 返回值：字段值

## 示例

```beanshell
class DemoBean {
    String name = "old";
}

var demo = new DemoBean();
log(getField(demo, "name"));
```

## 设置字段

```beanshell
void setField(Object instance, String fieldName, Object value);
```

## 示例

```beanshell
class DemoBean {
    String name = "old";
}

var demo = new DemoBean();
log("before = " + getField(demo, "name"));

setField(demo, "name", "newValue");
log("after = " + getField(demo, "name"));
```
