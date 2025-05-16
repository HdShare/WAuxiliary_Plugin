# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## 取当前登录Wxid
```kotlin
fun getLoginWxid(): String
```

## 取当前登录微信号
```kotlin
fun getLoginAlias(): String
```

## 取上下文Wxid
```kotlin
fun getTargetTalker(): String
```

## 取好友列表
```kotlin
fun getFriendList(): List<FriendInfo>
```

## 取好友昵称
```kotlin
fun getFriendName(friendWxid: String): String
```

## 取群聊列表
```kotlin
fun getGroupList(): List<GroupInfo>
```

## 取群成员列表
```kotlin
fun getGroupMemberList(groupWxid: String): List<String>
```

## 取群成员数量
```kotlin
fun getGroupMemberCount(groupWxid: String): Int
```

## 添加群成员
```kotlin
fun addChatroomMember(chatroomId: String, addMemberList: List<String>)
```

## 邀请群成员
```kotlin
fun inviteChatroomMember(chatroomId: String, inviteMemberList: List<String>)
```

## 移除群成员
```kotlin
fun delChatroomMember(chatroomId: String, delMemberList: List<String>)
```
