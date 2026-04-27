
void openSettings() {
    var ctx = getTopActivity()

    var layout = new LinearLayout(ctx)
    layout.setOrientation(LinearLayout.VERTICAL)
    layout.setPadding(32, 32, 32, 0)

    var lpEdt = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    )
    lpEdt.setMargins(0, 16, 0, 16)

    var edtVoice = new EditText(ctx)
    edtVoice.setHint("请输入音色")
    edtVoice.setText(getString("voice", ""))
    layout.addView(edtVoice, lpEdt)

    var edtKey = new EditText(ctx)
    edtKey.setHint("请输入密钥")
    edtKey.setText(getString("key", ""))
    layout.addView(edtKey, lpEdt)

    new AlertDialog.Builder(ctx)
            .setTitle("文转音设置")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                var voice = edtVoice.getText().toString().trim()
                putString("voice", voice)
                var key = edtKey.getText().toString().trim()
                putString("key", key)
                toast("保存成功")
            })
            .setNegativeButton("取消", null)
            .show()
}

boolean onClickSendBtn(String text) {
    var cmd = "#tts "
    if (text.startsWith(cmd)) {
        var str = text.substring(cmd.length())
        var voice = getString("voice", "")
        var key = getString("key", "")
        if (voice.equals("") || key.equals("")) {
            toast("请先配置音色及密钥")
            return true
        }
        var api = "https://www.yx520.ltd/API/wzzyy/silk.php?text=${str}&voice=${voice}&apikey=${key}"
        get(api, null, respContent -> {
            var jsonObj = new JSONObject(respContent)
            var code = jsonObj.optString("code")
            if (code.equals("0")) {
                var url = jsonObj.optString("url")
                var path = "${cacheDir}/voice.silk"
                download(url, path, null, cacheFile -> {
                    var talker = getTargetTalker()
                    sendVoice(talker, cacheFile.getAbsolutePath())
                    cacheFile.delete()
                })
            }
        })
        return true
    }
    return false
}
