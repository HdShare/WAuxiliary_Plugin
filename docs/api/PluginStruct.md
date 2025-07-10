
# 相关结构

::: warning 警告
本文档适用于 WAuxiliary v1.2.4 版本
:::

## 消息结构

```java
MsgInfo {
    long getMsgId();// 消息Id
    int getType();// 消息类型
    long getCreateTime();// 创建时间
    String getTalker();// 聊天Id(群聊/私聊)
    String getSendTalker();// 发送者Id
    String getContent();// 消息内容
    
    String getMsgSource();// 消息来源
    List<String> getAtUserList();// 艾特列表
    boolean isAnnounceAll();// 公告通知全体
    boolean isNotifyAll();// 艾特通知全体
    boolean isAtMe();// 艾特我

    QuoteMsg getQuoteMsg();// 引用消息
    PatMsg getPatMsg();// 拍一拍消息
    FileMsg getFileMsg();// 文件消息

    boolean isPrivateChat();// 私聊
    boolean isGroupChat();// 群聊
    boolean isOfficialAccount();// 公众号
    boolean isOpenIM();// 企业微信
    boolean isSend();// 自己发的

    boolean isText();// 文本
    boolean isImage();// 图片
    boolean isVoice();// 语音
    boolean isShareCard();// 名片
    boolean isVideo();// 视频
    boolean isEmoji();// 表情
    boolean isLocation();// 位置
    boolean isApp();// 应用
    boolean isVoip();// 通话
    boolean isVoipVoice();// 语音通话
    boolean isVoipVideo();// 视频通话
    boolean isSystem();// 系统
    boolean isLink();// 链接
    boolean isTransfer();// 转账
    boolean isRedBag();// 红包
    boolean isVideoNumberVideo();// 视频号视频
    boolean isNote();// 接龙
    boolean isQuote();// 引用
    boolean isPat();// 拍一拍
    boolean isFile();// 文件
}

QuoteMsg {
    String getTitle();// 回复标题
    String getMsgSource();// 消息来源
    String getSendTalker();// 发送者Id
    String getDisplayName();// 显示昵称
    String getTalker();// 聊天Id(群聊/私聊)
    int getType();// 消息类型
    String getContent();// 消息内容
}

PatMsg {
    String getTalker();// 聊天Id(群聊/私聊)
    String getFromUser();// 发起者Id
    String getPattedUser();// 被拍者Id
    String getTemplate();// 模板内容
    long getCreateTime();// 创建时间
}

FileMsg {
    String getTitle();// 文件标题
    long getSize();// 文件字节
    String getExt();// 文件后缀
    String getMd5();// 文件MD5
}
```
