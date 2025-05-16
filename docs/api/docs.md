# 接口文档

::: warning 警告
此文档为 WAuxiliary 1.2.1 编写
:::

## 监听收到消息
```java
void onHandleMsg(Object msgInfo)
```

## 消息结构
```java
MsgInfo {
    long msgId;// 消息Id
    int type;// 消息类型
    String talker;// 发送者(接收的 群聊Id/好友Id)
    String getSendTalker();// 发送者(群聊中 发送者Id)
    String getContent();// 消息内容
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
    boolean isCard();// 卡片
    boolean isVoip();// 通话
    boolean isVoipVoice();// 语音通话
    boolean isVoipVideo();// 视频通话
    boolean isSystem();// 系统
    boolean isLink();// 链接
    boolean isTransfer();// 转账
    boolean isRedPacket();// 红包
    boolean isNote();// 接龙
    boolean isQuote();// 引用
    boolean isPat()();// 拍一拍
    boolean isFile();// 文件
}
```

## 长按发送按钮
```java
boolean onLongClickSendBtn(String text)
```
