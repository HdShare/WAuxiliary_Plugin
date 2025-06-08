boolean onLongClickSendBtn(String text) {
//用于获取id
    if (text.equals("id")) {
        sendText(getTargetTalker(), getTargetTalker());
        return true;
    }
    return false;
}
