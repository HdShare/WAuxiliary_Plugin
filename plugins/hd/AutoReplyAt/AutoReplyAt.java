
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (msgInfoBean.isAtMe()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();
        String sendTalker = msgInfoBean.getSendTalker();
        sendText(talker, "[AtWx=" + sendTalker + "]\n有事直说 艾特我干嘛");
    }
}
