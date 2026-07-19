void onLoad() {
    toast("天气插件已加载成功");
    log("===== 天气插件已加载成功 =====");
}

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isText()) return;
    var content = msgInfoBean.getContent();
    var talker = msgInfoBean.getTalker();
    log("收到消息: " + content + " isSend=" + msgInfoBean.isSend());

    if (content.startsWith("天气 ")) {
        var city = content.substring(3).trim();
        if (city.isEmpty()) {
            sendText(talker, "请输入城市名，如: 天气 北京");
            return;
        }
        var url = "https://wttr.in/" + city + "?format=j1&lang=zh";
        log("查询: " + url);
        get(url, null, resp -> {
            try {
                var json = new org.json.JSONObject(resp);
                var now = json.optJSONArray("current_condition").optJSONObject(0);
                if (now == null) {
                    sendText(talker, "未找到城市");
                    return;
                }
                var temp = now.optString("temp_C");
                var desc = now.optJSONArray("weatherDesc").optJSONObject(0).optString("value");
                var result = "[天气] " + desc + " " + temp + "°C";
                sendText(talker, result);
            } catch (Exception e) {
                sendText(talker, "查询失败");
            }
        });
    }
}

boolean onClickSendBtn(String text) {
    var content = text.trim();
    if (content.startsWith("天气 ") || content.startsWith("天气查询 ")) {
        return true;
    }
    return false;
}

void openSettings() {
    toast("天气查询 v1.0 - 发「天气 城市名」后长按发送");
}