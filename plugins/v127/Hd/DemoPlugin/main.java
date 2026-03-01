
void onLoad() {
    log("DemoPlugin onLoad");
}

void onUnload() {
    log("DemoPlugin onUnload");
}

boolean onClickSendBtn(String content) {
    var talker = getTargetTalker();
    if (content.equals("echo")) {
        sendText(talker, "Hello World");
        return true;
    }
    return false;
}
