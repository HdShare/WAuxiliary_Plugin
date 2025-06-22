
import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend() && msgInfoBean.isQuote()) {
        String talker = msgInfoBean.getTalker();
        String title = msgInfoBean.getQuoteMsg().getTitle();
        if (title.contains("/rua")) {
            String quoteMsgSendTalker = msgInfoBean.getQuoteMsg().getSendTalker();
            String avatarUrl = getAvatarUrl(quoteMsgSendTalker);
            if (!avatarUrl.equals("")) {
                download("https://api.52vmy.cn/api/avath/rua?url=" + avatarUrl, pluginDir + "/avatar.gif", null, new PluginCallBack.DownloadCallback() {
                    public void onSuccess(File file) {
                        sendEmoji(talker, file.getAbsolutePath());
                    }
            
                    public void onError(Exception e) {
                        sendText(talker, "[维梦API]生成异常:" + e.getMessage());
                    }
                });
            } else {
                sendText(talker, "获取头像异常");
            }
        }
    }
}
