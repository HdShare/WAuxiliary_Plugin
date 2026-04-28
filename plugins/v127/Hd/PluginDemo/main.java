
// 打开设置
void openSettings() {
}

// 插件加载
void onLoad() {
}

// 插件卸载
void onUnload() {
}

// 监听收到消息
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return

    if (msgInfoBean.isAtMe()) { // 艾特我
        var content = msgInfoBean.getContent() // 消息内容
        var talker = msgInfoBean.getTalker() // 聊天Id
        var sendTalker = msgInfoBean.getSendTalker() // 发送者Id
        sendText(talker, "[AtWx=${sendTalker}] 艾特我干啥?")
    } else if (msgInfoBean.isText()) { // 文本消息
        var content = msgInfoBean.getContent() // 消息内容
        var talker = msgInfoBean.getTalker() // 聊天Id
        var sendTalker = msgInfoBean.getSendTalker() // 发送者Id
        if (content.equals("在吗")) {
            sendText(talker, "不在")
        } else if (content.equals("我要进群")) {
            inviteChatroomMember("demo@chatroom", sendTalker) // 邀请群成员
        }
    } else if (msgInfoBean.isPat()) { // 拍一拍消息
        var myWxid = getLoginWxid() // 当前登录Wxid
        var fromUser = msgInfoBean.getPatMsg().getFromUser() // 发起者Id
        var pattedUser = msgInfoBean.getPatMsg().getPattedUser() // 被拍者Id
        if (!fromUser.equals(myWxid) && pattedUser.equals(myWxid)) { // 非自拍 且 被拍头
            sendText(msgInfoBean.getTalker(), "[AtWx=${fromUser}] 拍我干啥?")
        }
    }
}

// 单击发送按钮
boolean onClickSendBtn(String content) {
    return false
}

// 监听成员变动
void onMemberChange(String type, String groupWxid, String userWxid, String userName) {
    var list = (List) {"demo@chatroom"} // 配置提示群聊
    if (!list.contains(groupWxid)) return

    if (type.equals("join")) { // 加入
        sendText(groupWxid, "[AtWx=${userWxid}] 欢迎加入")
    } else if (type.equals("left")) { // 退出
        sendText(groupWxid, "用户${userName} 退出了")
    }
}

// 监听好友申请
void onNewFriend(String wxid, String ticket, int scene) {
    verifyUser(wxid, ticket, scene) // 通过好友申请
}
