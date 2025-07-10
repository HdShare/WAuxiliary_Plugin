# 联系方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 取当前登录Wxid

```java
String getLoginWxid();
```

## 取当前登录微信号

```java
String getLoginAlias();
```

## 取上下文Wxid

```java
String getTargetTalker();
```

## 取好友列表

```java
List getFriendList();
```

## 取好友昵称

```java
String getFriendName(String friendWxid);

String getFriendName(String friendWxid, String roomId);
```

## 取头像链接

```java
void getAvatarUrl(String username);

void getAvatarUrl(String username, boolean isBigHeadImg);
```

## 取群聊列表

```java
List getGroupList();
```

## 取群成员列表

```java
List getGroupMemberList(String groupWxid);
```

## 取群成员数量

```java
int getGroupMemberCount(String groupWxid);
```

## 添加群成员

```java
void addChatroomMember(String chatroomId, String addMember);

void addChatroomMember(String chatroomId, List addMemberList);
```

## 邀请群成员

```java
void inviteChatroomMember(String chatroomId, String inviteMember);

void inviteChatroomMember(String chatroomId, List inviteMemberList);
```

## 移除群成员

```java
void delChatroomMember(String chatroomId, String delMember);

void delChatroomMember(String chatroomId, List delMemberList);
```

## 通过好友申请

```java
void verifyUser(String wxid, String ticket, int scene);

void verifyUser(String wxid, String ticket, int scene, int privacy);
```
