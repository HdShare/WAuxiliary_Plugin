
void sendKitchen(String emoji1, String emoji2, String talker) {
    var url = "https://oiapi.net/api/EmojiMix?emoji1=${emoji1}&emoji2=${emoji2}"
    get(url, null, respContent -> {
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

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        var content = msgInfoBean.getContent()
        var talker = msgInfoBean.getTalker()

        if (content.contains("+")) {
            var emojis = content.split("\\+")
            if (emojis.length == 2) {
                var emoji1 = emojis[0].trim()
                var emoji2 = emojis[1].trim()

                if (isSimpleEmoji(emoji1) && isSimpleEmoji(emoji2)) {
                    sendKitchen(emoji1, emoji2, talker)
                }
            }
        }
    }
}

boolean isSimpleEmoji(String s) {
    if (s == null || s.isEmpty()) return false

    var codePoints = s.codePoints().toArray()
    if (codePoints.length > 8) return false

    var hasEmojiCore = false
    for (int cp : codePoints) {
        if (isEmojiCore(cp)) {
            hasEmojiCore = true
        } else if (!isEmojiModifier(cp) && !isZWJ(cp) && !isVariationSelector(cp) && !isRegionalIndicator(cp)) {
            return false
        }
    }

    return hasEmojiCore
}

boolean isEmojiCore(int cp) {
    var flag1 = (cp >= 0x1F600 && cp <= 0x1F64F) || (cp >= 0x1F44A && cp <= 0x1F64F) || (cp >= 0x1F90C && cp <= 0x1F9FF) // 表情与手势
    var flag2 = (cp >= 0x1F466 && cp <= 0x1F487) || (cp >= 0x1F9B0 && cp <= 0x1F9B9) // 人物、身体、衣物
    var flag3 = (cp >= 0x1F400 && cp <= 0x1F4D3) || (cp >= 0x1F300 && cp <= 0x1F320) // 动物与自然
    var flag4 = (cp >= 0x1F34E && cp <= 0x1F37F) // 食物和饮料
    var flag5 = (cp >= 0x1F3C0 && cp <= 0x1F3FA) || (cp == 0x26F9) // 活动与运动
    var flag6 = (cp >= 0x1F3B5 && cp <= 0x1F3FF) || (cp >= 0x1F4F0 && cp <= 0x1F579) // 音乐、工具、科技
    var flag7 = (cp >= 0x1F680 && cp <= 0x1F6FF) // 交通工具与地标
    var flag8 = (cp >= 0x2600 && cp <= 0x26FF) || (cp >= 0x2700 && cp <= 0x27BF) // 杂项符号
    return flag1 || flag2 || flag3 || flag4 || flag5 || flag6 || flag7 || flag8
}

boolean isEmojiModifier(int cp) {
    return cp >= 0x1F3FB && cp <= 0x1F3FF
}

boolean isZWJ(int cp) {
    return cp == 0x200D
}

boolean isVariationSelector(int cp) {
    return cp == 0xFE0F
}

boolean isRegionalIndicator(int cp) {
    return cp >= 0x1F1E6 && cp <= 0x1F1FF
}
