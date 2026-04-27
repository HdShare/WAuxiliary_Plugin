
// 是否开启群聊抖音链接解析
var isOpenGroup = true

var regex = "https://v\\.douyin\\.com/[^\\s/]+/"
var pattern = Pattern.compile(regex)

void handleDouyinLink(String talker, String linkUrl) {
    /**
     * 基于 deno 的抖音视频图文无水印下载
     * 项目开源地址: https://github.com/pwh-pwh/douyinVd
     * 可 fork 本项目 到 cf worker,vercel,deno deploy 或 服务器 部署
     */
    /**
     * 备选接口地址
     * https://cf.eval.qzz.io/
     * https://douyinvd.deno.dev/
     */
    var apiUrl = "https://cf.eval.qzz.io/?data&url=${linkUrl}"
    get(apiUrl, null, respContent -> {
        try {
            var jsonObj = new JSONObject(respContent)
            var type = jsonObj.optString("type")
            var id = jsonObj.optString("aweme_id")
            if (type.equals("video")) {
                var videoUrl = jsonObj.optString("video_url")
                download(videoUrl, "${cacheDir}/dyVideo${id}.mp4", null, cacheFile -> {
                    sendVideo(talker, cacheFile.getAbsolutePath())
                })
            } else if (type.equals("img")) {
                val imageUrlList = jsonObj.optJSONArray("image_url_list")
                for (int i = 0; i < imageUrlList.length(); i++) {
                    val imgUrl = imageUrlList.optString(i)
                    download(imgUrl, "${cacheDir}/dyImg${i}.jpg", null, cacheFile -> {
                        sendImage(talker, cacheFile.getAbsolutePath())
                    })
                }
            } else {
                sendText(talker, "[抖音链接解析]未知类型:${type}")
            }
        } catch (Exception e) {
            sendText(talker, "[抖音链接解析]解析异常:" + e.getMessage())
        }
    })
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        var content = msgInfoBean.getContent()
        var talker = msgInfoBean.getTalker()
        var isGroup = msgInfoBean.isGroupChat()
        if (!isGroup || isOpenGroup) {
            var matcher = pattern.matcher(content)
            if (matcher.find()) {
                handleDouyinLink(talker, matcher.group())
            }
        }
    }
}

boolean onClickSendBtn(String content) {
    var matcher = pattern.matcher(content)
    if (matcher.find()) {
        handleDouyinLink(getTargetTalker(), matcher.group())
        return true
    }
    return false
}
