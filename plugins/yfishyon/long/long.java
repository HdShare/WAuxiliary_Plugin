import org.json.JSONObject;
import java.io.File;
import me.hd.wauxv.plugin.api.callback.PluginCallBack;

boolean onLongClickSendBtn(String text) {
    if ("龙图".equals(text)) { //龙哥就是龙！
        get("https://api.yujn.cn/api/long.php?type=json", null, new PluginCallBack.HttpCallback() {
            public void onSuccess(int respCode, String respContent) {
                JSONObject json = new JSONObject(respContent);
                String code = json.getString("code");
                if (code.equals("200")) {
                    String url = json.getString("data");
                    download(url, pluginDir + "/long.jpg", null, new PluginCallBack.DownloadCallback() {
                        public void onSuccess(File file) {
                            sendEmoji(getTargetTalker(), file.getAbsolutePath());
                        }

                        public void onError(Exception e) {
                            sendText(getTargetTalker(), "下载异常:" + e.getMessage());
                        }
                    });
                }
            }

            public void onError(Exception e) {
                sendText(getTargetTalker(), "API错误:" + e.getMessage());
            }
        });
        return true;
    }
    return false;
}