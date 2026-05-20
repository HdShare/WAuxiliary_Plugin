# 相关结构

::: warning 警告
本文档适用于 WAuxiliary 最新版本
:::

## 新手先看

- `msgInfoBean` 通常出现在 `onHandleMsg(Object msgInfoBean)` 里。
- `payMsgBean` 通常出现在 `onRecvPayMsg(Object payMsgBean)` 里。
- 下面的示例如果直接用了 `msgInfoBean`，默认都表示“这段代码写在 `onHandleMsg(...)` 里面”。

## 消息结构

`onHandleMsg(Object msgInfoBean)` 中的 `msgInfoBean` 实际可按下列结构使用。

```beanshell
MsgInfoBean {
    long getMsgId();// 消息 ID
    int getType();// 消息类型
    long getCreateTime();// 创建时间戳
    String getTalker();// 目标会话 ID，私聊为好友 wxid，群聊为 chatroom id
    String getSendTalker();// 发送者 wxid
    String getContent();// 消息内容

    String getMsgSource();// 消息来源
    List<String> getAtUserList();// @ 列表
    boolean isAnnounceAll();// 是否公告全体
    boolean isNotifyAll();// 是否 @全体
    boolean isAtMe();// 是否 @我

    ImageMsg getImageMsg();// 图片消息结构
    QuoteMsg getQuoteMsg();// 引用消息结构
    PatMsg getPatMsg();// 拍一拍消息结构
    FileMsg getFileMsg();// 文件消息结构

    boolean isPrivateChat();// 是否私聊
    boolean isOpenIM();// 是否企业微信私聊

    boolean isGroupChat();// 是否群聊
    boolean isChatroom();// 是否普通群聊
    boolean isImChatroom();// 是否企业微信群聊

    boolean isOfficialAccount();// 是否公众号
    boolean isSend();// 是否自己发送

    boolean isText();// 是否文本
    boolean isImage();// 是否图片
    boolean isVoice();// 是否语音
    boolean isShareCard();// 是否名片
    boolean isVideo();// 是否视频
    boolean isEmoji();// 是否表情
    boolean isLocation();// 是否位置
    boolean isApp();// 是否应用消息
    boolean isVoip();// 是否通话消息
    boolean isVoipVoice();// 是否语音通话
    boolean isVoipVideo();// 是否视频通话
    boolean isSystem();// 是否系统消息
    boolean isRecalled();// 是否撤回消息
    boolean isLink();// 是否链接消息
    boolean isTransfer();// 是否转账消息
    boolean isRedBag();// 是否红包消息
    boolean isVideoNumberVideo();// 是否视频号视频
    boolean isNote();// 是否接龙消息
    boolean isQuote();// 是否引用消息
    boolean isPat();// 是否拍一拍
    boolean isFile();// 是否文件消息
}
```

### 示例

```beanshell
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;

    String talker = msgInfoBean.getTalker();
    String sender = msgInfoBean.getSendTalker();
    String content = msgInfoBean.getContent();

    if (msgInfoBean.isText()) {
        log("[" + sender + "] " + content);
    }

    if (msgInfoBean.isAtMe()) {
        sendText(talker, "[AtWx=" + sender + "] 收到");
    }
}
```

## 图片消息结构

```beanshell
ImageMsg {
    String getMd5();// 图片 MD5
    String getBigImgUrl();// 高清图链接
    String getMidImgUrl();// 普通图链接
    String getThumbUrl();// 缩略图链接
    String getKey();// 图片密钥
}
```

### 示例

```beanshell
if (msgInfoBean.isImage()) {
    var imageMsg = msgInfoBean.getImageMsg();
    log("图片 md5 = " + imageMsg.getMd5());
    log("高清图 = " + imageMsg.getBigImgUrl());
}
```

## 引用消息结构

```beanshell
QuoteMsg {
    String getTitle();// 引用标题
    String getMsgSource();// 消息来源
    String getSendTalker();// 原消息发送者 wxid
    String getDisplayName();// 显示昵称
    String getTalker();// 目标会话 ID
    int getType();// 原消息类型
    String getContent();// 原消息内容
}
```

### 示例

```beanshell
if (msgInfoBean.isQuote()) {
    var quoteMsg = msgInfoBean.getQuoteMsg();
    log("引用内容: " + quoteMsg.getContent());
}
```

## 拍一拍消息结构

```beanshell
PatMsg {
    String getTalker();// 目标会话 ID
    String getFromUser();// 发起者 wxid
    String getPattedUser();// 被拍者 wxid
    String getTemplate();// 展示模板
    long getCreateTime();// 创建时间戳
}
```

### 示例

```beanshell
if (msgInfoBean.isPat()) {
    var patMsg = msgInfoBean.getPatMsg();
    log(patMsg.getFromUser() + " 拍了 " + patMsg.getPattedUser());
}
```

## 文件消息结构

```beanshell
FileMsg {
    String getTitle();// 文件标题
    long getSize();// 文件大小，单位字节
    String getExt();// 文件后缀
    String getMd5();// 文件 MD5
    String getUrl();// 文件链接
    String getKey();// 文件密钥
}
```

### 示例

```beanshell
if (msgInfoBean.isFile()) {
    var fileMsg = msgInfoBean.getFileMsg();
    log("文件名: " + fileMsg.getTitle());
    log("文件大小: " + fileMsg.getSize());
}
```

## 收款消息结构

`onRecvPayMsg(Object payMsgBean)` 中的 `payMsgBean` 可按下列结构使用。

```beanshell
PayMsgBean {
    int getTimestamp();// 时间戳
    String getUsername();// 账号
    String getDisplayName();// 显示名
    double getFee();// 金额
    int getStatus();// 状态值
    String getStatusDesc();// 状态描述
}
```

### 示例

```beanshell
void onRecvPayMsg(Object payMsgBean) {
    log("收款人: " + payMsgBean.getDisplayName());
    log("金额: " + payMsgBean.getFee());
    log("状态: " + payMsgBean.getStatusDesc());
}
```
