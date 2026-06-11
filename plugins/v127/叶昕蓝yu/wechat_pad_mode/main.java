// 微信平板模式 - 叶昕蓝yu

var hookHandle = null;

void onLoad() {
    if (!getBoolean("enabled", true)) return;
    try {
        var keywords = new java.util.ArrayList();
        keywords.add("Lenovo TB-9707F");
        var list = findMemberList(keywords);
        if (list != null && list.size() > 0) {
            hookHandle = hookBefore(list.get(0), param -> {
                param.setResult(true);
            });
            log("pad ok");
        } else {
            log("pad: method not found");
        }
    } catch (Throwable e) {
        log("pad err: " + e.getMessage());
    }
}

void onUnload() {
    if (hookHandle != null) {
        try { unhook(hookHandle); } catch (Throwable ignored) {}
        hookHandle = null;
    }
}
