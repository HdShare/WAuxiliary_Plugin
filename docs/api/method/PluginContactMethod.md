# 联系方法

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- `wxid`：微信用户或群成员的唯一标识。
- `chatroom id`：群聊 ID，通常形如 `123456@chatroom`。
- 如果你在当前聊天页面，优先用 `getTargetTalker()` 取当前目标会话 ID。
- 群成员管理相关方法通常要求你当前账号对该群有对应权限。

## 取当前登录 Wxid

```beanshell
String getLoginWxid();
```

- 返回值：当前账号的 `wxid`

## 取当前登录微信号

```beanshell
String getLoginAlias();
```

- 返回值：当前账号微信号；若无微信号则回退为 `wxid`

## 取上下文聊天对象

```beanshell
String getTargetTalker();
```

- 返回值：当前目标会话 ID，私聊为对方 `wxid`，群聊为群 `chatroom id`

## 示例

```beanshell
String talker = getTargetTalker();
log("current talker = " + talker);
```

## 获取公众号列表

```beanshell
List<FriendInfo> getOfficialList();
```

- 返回值：公众号列表

## 示例

```beanshell
var officialList = getOfficialList();
log("official count = " + officialList.size());
```

## 取好友列表

```beanshell
List<FriendInfo> getFriendList();
```

- 返回值：好友列表

## 示例

```beanshell
var friendList = getFriendList();
log("friend count = " + friendList.size());
```

## 取好友昵称

```beanshell
String getFriendNickName(String friendWxid);
```

- `friendWxid`：好友 `wxid`
- 返回值：好友昵称

## 取好友备注

```beanshell
String getFriendRemarkName(String friendWxid);
```

- `friendWxid`：好友 `wxid`
- 返回值：好友备注

## 取好友群内显示名

```beanshell
String getFriendDisplayName(String friendWxid, String roomId);
```

- `friendWxid`：成员 `wxid`
- `roomId`：群聊 `chatroom id`
- 返回值：群内显示名称，取不到时回退为好友昵称

## 示例

```beanshell
String name = getFriendDisplayName("wxid_xxx", "123456@chatroom");
log(name);
```

## 取好友名称

后续可能废弃，建议优先使用 `getFriendNickName`、`getFriendRemarkName`、`getFriendDisplayName`。

```beanshell
String getFriendName(String friendWxid);

String getFriendName(String friendWxid, String roomId);
```

- 返回值：综合名称，通常会按备注、群显示名、好友昵称等逻辑返回

## 取头像链接

```beanshell
String getAvatarUrl(String username);

String getAvatarUrl(String username, boolean isBigHeadImg);
```

- `username`：联系人 `wxid`
- `isBigHeadImg`：是否取大图
- 返回值：头像 URL

## 示例

```beanshell
String avatarUrl = getAvatarUrl("wxid_xxx", true);
log(avatarUrl);
```

## 取群聊列表

```beanshell
List<GroupInfo> getGroupList();
```

- 返回值：群聊列表

## 取群成员列表

```beanshell
List<String> getGroupMemberList(String groupWxid);
```

- `groupWxid`：群聊 `chatroom id`
- 返回值：群成员 `wxid` 列表

## 取群成员数量

```beanshell
int getGroupMemberCount(String groupWxid);
```

- `groupWxid`：群聊 `chatroom id`
- 返回值：群成员数量

## 示例

```beanshell
int count = getGroupMemberCount("123456@chatroom");
log("member count = " + count);
```

## 添加群成员

```beanshell
void addChatroomMember(String chatroomId, String addMember);

void addChatroomMember(String chatroomId, List<String> addMemberList);
```

- `chatroomId`：群聊 `chatroom id`
- `addMember` / `addMemberList`：要添加的成员 `wxid`

## 邀请群成员

```beanshell
void inviteChatroomMember(String chatroomId, String inviteMember);

void inviteChatroomMember(String chatroomId, List<String> inviteMemberList);
```

- `chatroomId`：群聊 `chatroom id`
- `inviteMember` / `inviteMemberList`：要邀请的成员 `wxid`

## 移除群成员

```beanshell
void delChatroomMember(String chatroomId, String delMember);

void delChatroomMember(String chatroomId, List<String> delMemberList);
```

- `chatroomId`：群聊 `chatroom id`
- `delMember` / `delMemberList`：要移除的成员 `wxid`

## 通过好友申请

```beanshell
void verifyUser(String wxid, String ticket, int scene);

void verifyUser(String wxid, String ticket, int scene, int privacy);
```

- `wxid`：申请人 `wxid`
- `ticket`：好友申请携带的票据
- `scene`：申请场景值
- `privacy`：隐私参数

## 示例

```beanshell
verifyUser("wxid_xxx", "ticket_xxx", 17);
```

## 获取标签列表

```beanshell
List<ContactLabelBean> getContactLabelList();
```

- 返回值：当前账号下全部标签

## 示例

```beanshell
var labelList = getContactLabelList();
log("label count = " + labelList.size());
```

## 通过标签 ID 获取联系人

```beanshell
List<String> getContactByLabelId(String labelId);
```

- `labelId`：标签 ID
- 返回值：命中该标签的联系人 `wxid` 列表

## 示例

```beanshell
var userList = getContactByLabelId("1");
log(userList);
```

## 通过标签名称获取联系人

```beanshell
List<String> getContactByLabelName(String labelName);
```

- `labelName`：标签名称
- 返回值：命中该标签的联系人 `wxid` 列表

## 示例

```beanshell
var userList = getContactByLabelName("重要好友");
log(userList);
```

## 修改好友标签

```beanshell
void modifyContactLabelList(String username, String labelName);

void modifyContactLabelList(String username, List<String> labelNames);
```

- `username`：好友 `wxid`
- `labelName` / `labelNames`：标签名称或标签列表

## 示例

```beanshell
modifyContactLabelList("wxid_xxx", "重要好友");
```
