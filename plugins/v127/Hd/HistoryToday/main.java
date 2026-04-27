
void sendToday(String talker) {
    var api = "https://v2.xxapi.cn/api/historypic"
    get(api, null, respContent -> {
        var jsonObj = new JSONObject(respContent)
        var code = jsonObj.optInt("code")
        if (code == 200) {
            var url = jsonObj.optString("data")
            var path = "${cacheDir}/image.png"
            download(url, path, null, cacheFile -> {
                sendImage(talker, cacheFile.getAbsolutePath())
                cacheFile.delete()
            })
        }
    })
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        var content = msgInfoBean.getContent()
        var talker = msgInfoBean.getTalker()
        if (content.equals("/历史今天")) {
            sendToday(talker)
        }
    }
}
