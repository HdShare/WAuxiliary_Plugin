
boolean onLongClickSendBtn(String text) {
    if (text.equals("echo")) {
        sendText(getTargetTalker(), "Hello World");
        return true;
    }
    return false;
}
