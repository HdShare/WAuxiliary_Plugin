import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;

import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

void onHandleMsg(Object msgInfoBean) {
    if (!msgInfoBean.isSend()) return;
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();
        if (content.contains("+")) {
            String[] emojis = content.split("\\+");
            if (emojis.length == 2) {
                String emoji1 = emojis[0].trim();
                String emoji2 = emojis[1].trim();
                get("https://api.317ak.com/API/yljk/emo/emo.php?emoji1=" + emoji1 + "&emoji2=" + emoji2, null, new PluginCallBack.HttpCallback() {
                    public void onSuccess(int respCode, String respContent) {
                        JSONObject jsonObject = JSON.parseObject(respContent);
                        int code = JSONPath.eval(jsonObject, "$.code");
                        if (code == 1) {
                            String url = JSONPath.eval(jsonObject, "$.data.url");
                            download(url, pluginDir + "/emoji.png", null, new PluginCallBack.DownloadCallback() {
                                public void onSuccess(File file) {
                                    sendEmoji(talker, file.getAbsolutePath());
                                }

                                public void onError(Exception e) {
                                    sendText(talker, "[倾梦API]下载异常:" + e.getMessage());
                                }
                            });
                        } else {
                            sendText(talker, "[倾梦API]生成失败:" + JSONPath.eval(jsonObject, "$.text"));
                        }
                    }

                    public void onError(Exception e) {
                        sendText(talker, "[倾梦API]生成异常:" + e.getMessage());
                    }
                });
            }
        }
    }
}
