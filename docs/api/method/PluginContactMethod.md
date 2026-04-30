# 联系方法

::: warning 警告
本文档适用于 WAuxiliary v1.2.7.r1368 版本
:::

## 取当前登录Wxid

```beanshell
String getLoginWxid();
```

## 取当前登录微信号

```beanshell
String getLoginAlias();
```

## 取上下文Wxid

```beanshell
String getTargetTalker();
```

## 取好友列表

```beanshell
List<FriendInfo> getFriendList();
```

## 取好友昵称

```beanshell
String getFriendNickName(String friendWxid);
```

## 取好友备注

```beanshell
String getFriendRemarkName(String friendWxid);
```

## 取好友群内昵称

```beanshell
String getFriendDisplayName(String friendWxid, String roomId);
```

## 取好友昵称(后续废弃及移除)

```beanshell
// 好友备注 > 群内昵称 > 好友昵称
String getFriendName(String friendWxid);

String getFriendName(String friendWxid, String roomId);
```

## 取头像链接

```beanshell
void getAvatarUrl(String username);

void getAvatarUrl(String username, boolean isBigHeadImg);
```

## 取群聊列表

```beanshell
List<GroupInfo> getGroupList();
```

## 取群成员列表

```beanshell
List<String> getGroupMemberList(String groupWxid);
```

## 取群成员数量

```beanshell
int getGroupMemberCount(String groupWxid);
```

## 添加群成员

```beanshell
void addChatroomMember(String chatroomId, String addMember);

void addChatroomMember(String chatroomId, List<String> addMemberList);
```

## 邀请群成员

```beanshell
void inviteChatroomMember(String chatroomId, String inviteMember);

void inviteChatroomMember(String chatroomId, List<String> inviteMemberList);
```

## 移除群成员

```beanshell
void delChatroomMember(String chatroomId, String delMember);

void delChatroomMember(String chatroomId, List<String> delMemberList);
```

## 通过好友申请

```beanshell
void verifyUser(String wxid, String ticket, int scene);

void verifyUser(String wxid, String ticket, int scene, int privacy);
```

## 修改好友标签

```beanshell

void modifyContactLabelList(String username, String labelName);

void modifyContactLabelList(String username, List<String> labelNames);
```
