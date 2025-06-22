
void onHandleMsg(Object msgInfo) {
    if (msgInfo.isPat()) {
        String myWxid = getLoginWxid();
        String fromUser = msgInfo.getPatMsg().getFromUser();
        String pattedUser = msgInfo.getPatMsg().getPattedUser();
        if (!fromUser.equals(myWxid) && pattedUser.equals(myWxid)) {
            sendText(msgInfo.getTalker(), "[AtWx=" + fromUser + "] 干啥子?");
        }
    }
}
