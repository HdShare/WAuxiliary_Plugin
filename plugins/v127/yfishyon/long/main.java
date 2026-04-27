
boolean onClickSendBtn(String text) {
    if ("龙图".equals(text)) { // 龙哥就是龙！
        get("https://api.yujn.cn/api/long.php?type=json", null, respContent -> {
            var jsonObj = new JSONObject(respContent)
            var code = jsonObj.optInt("code")
            if (code == 200) {
                var url = jsonObj.getString("data")
                download(url, "${cacheDir}/long.jpg", null, cacheFile -> {
                    sendEmoji(getTargetTalker(), cacheFile.getAbsolutePath())
                    cacheFile.delete()
                })
            }
        })
        return true
    }
    return false
}
