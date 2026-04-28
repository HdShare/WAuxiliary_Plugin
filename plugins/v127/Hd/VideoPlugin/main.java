
void sendMp4(String talker) {
    var api = "https://api.mmp.cc/api/miss?type=mp4"
    var path = "${cacheDir}/video${System.currentTimeMillis()}.mp4"
    download(api, path, null, cacheFile -> {
        sendVideo(talker, cacheFile.getAbsolutePath())
    })
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        var content = msgInfoBean.getContent()
        var talker = msgInfoBean.getTalker()
        if (content.equals("/视频")) {
            sendMp4(talker)
        }
    }
}
