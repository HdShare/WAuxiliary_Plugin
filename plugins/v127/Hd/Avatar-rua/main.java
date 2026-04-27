
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend() && msgInfoBean.isQuote()) {
        var talker = msgInfoBean.getTalker()
        var title = msgInfoBean.getQuoteMsg().getTitle()
        if (title.contains("/rua")) {
            var quoteMsgSendTalker = msgInfoBean.getQuoteMsg().getSendTalker()
            var avatarUrl = getAvatarUrl(quoteMsgSendTalker)
            if (!avatarUrl.equals("")) {
                var api = "https://api.52vmy.cn/api/avath/rua?url=${avatarUrl}"
                var path = "${cacheDir}/avatar.gif"
                download(api, path, null, file -> {
                    sendEmoji(talker, file.getAbsolutePath())
                })
            } else {
                sendText(talker, "[摸头插件]获取异常")
            }
        }
    }
}
