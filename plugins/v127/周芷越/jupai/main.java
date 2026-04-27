
boolean onClickSendBtn(String text) {
    if (!text.startsWith("举牌 ")) return false

    var lingkuandukongge = "\u200B"

    var content = text.substring(2).trim()
    var parts = content.split("\\s+", 4)

    var wb1 = parts[0] // 第一个参数必存在
    var wb2 = parts.length > 1 ? parts[1] : lingkuandukongge
    var wb3 = parts.length > 2 ? parts[2] : lingkuandukongge
    var wb4 = parts.length > 3 ? parts[3] : lingkuandukongge

    var id = new Random().nextInt(31) + 1

    var apiUrl = "https://api.zxz.ee/api/jupai/?id=${id}&wb1=${wb1}&wb2=${wb2}&wb3=${wb3}&wb4=${wb4}"
    download(apiUrl, "${cacheDir}/jupai.jpg", null, cacheFile -> {
        sendImage(getTargetTalker(), cacheFile.getAbsolutePath(), "wxe3ad19e142df87b3")
        cacheFile.delete()
    })

    return true
}
