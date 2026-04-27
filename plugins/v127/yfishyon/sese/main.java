
boolean onClickSendBtn(String text) {
    var name = when(text) {
        "黑丝" ->"heisi"
        "白丝" ->"baisi"
        else ->""
    }
    if (name.equals("")) return false

    var apiUrl = "https://api.yujn.cn/api/${name}.php?type=json"
    get(apiUrl, null, respContent -> {
        var jsonObj = new JSONObject(respContent)
        var code = jsonObj.getInt("code")
        if (code = 1) {
            var url = jsonObj.getString("img")
            download(url, "${cacheDir}/${name}.jpg", null, cacheFile -> {
                sendImage(getTargetTalker(), cacheFile.getAbsolutePath(), "wxe3ad19e142df87b3")
                cacheFile.delete()
            })
        }
    })

    return true
}
