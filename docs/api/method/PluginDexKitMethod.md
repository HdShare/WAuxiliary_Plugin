# DexKit 方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

该组方法用于通过字符串特征在宿主 Dex 中查找类和成员，适合做版本适配与动态定位。

## 新手先看

- 这组方法偏进阶，不是日常插件开发的第一选择。
- 如果普通 API 已经能解决问题，优先使用普通 API。
- 如果你要找宿主内部方法或类，再考虑用 DexKit。

## 查找类列表

```beanshell
List<Class<?>> findClassList(List<String> usingStrings);
```

- `usingStrings`：参与匹配的字符串列表，可传一个或多个关键字
- 返回值：命中的类列表

## 查找成员列表

```beanshell
List<Member> findMemberList(List<String> usingStrings);
```

- `usingStrings`：参与匹配的字符串列表
- 返回值：命中的方法或构造函数列表

## 示例

```beanshell
var keyWord = "doRevokeMsg xmlSrvMsgId=%d talker=%s isGet=%s";
var classList = findClassList({keyWord});
var memberList = findMemberList({keyWord});

log("class size = " + classList.size());
log("member size = " + memberList.size());
```

## 进一步示例

```beanshell
var list = findMemberList({"keyword1", "keyword2"});
if (list.size() > 0) {
    var member = list.get(0);
    log(member);
}
```
