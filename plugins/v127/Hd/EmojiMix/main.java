
void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isSend()) return
    if (msgInfoBean.isText()) {
        var content = msgInfoBean.getContent()
        var talker = msgInfoBean.getTalker()
        if (content.contains("+")) {
            var emojis = content.split("\\+")
            if (emojis.length == 2) {
                var api = "https://oiapi.net/api/EmojiMix?emoji1=${emojis[0].trim()}&emoji2=${emojis[1].trim()}"
                get(api, null, respContent -> {
                    var jsonObj = new JSONObject(respContent)
                    var code = jsonObj.optInt("code")
                    if (code == 1) {
                        var data = jsonObj.optJSONObject("data")
                        var url = data.optString("url")
                        var path = "${cacheDir}/emoji.png"
                        download(url, path, null, file -> {
                            sendEmoji(talker, file.getAbsolutePath())
                        })
                    } else {
                        var msg = jsonObj.optString("message")
                        sendText(talker, "[表情合成]合成失败: $msg")
                    }
                })
            }
        }
    }
}
