import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

// 步骤 1：下载并发送 Emoji Kitchen 图像
void sendKitchen(String emoji1, String emoji2, String talker) {
    // 构造 API 地址
    String url = "https://emojik.vercel.app/s/" + emoji1 + "_" + emoji2 + "?size=256";

    // 复制参数
    final String finalTalker = talker;

    // 下载合成图
    download(url, pluginDir + "/emoji.png", null, new PluginCallBack.DownloadCallback() {
        public void onSuccess(File file) {
            sendEmoji(finalTalker, file.getAbsolutePath());
        }

        public void onError(Exception e) {
            sendText(finalTalker, "[Emoji Kitchen] 下载失败: " + e.getMessage());
        }
    });
}

// 步骤 2：监听消息并调用 sendKitchen
void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();

        // 匹配 emoji1+emoji2 格式
        if (content.contains("+")) {
            String[] emojis = content.split("\\+");
            if (emojis.length == 2) {
                String emoji1 = emojis[0].trim();
                String emoji2 = emojis[1].trim();

                sendKitchen(emoji1, emoji2, talker);
            }
        }
    }
}
