import org.json.JSONObject;
import java.io.File;
import me.hd.wauxv.plugin.api.callback.PluginCallBack;

boolean onLongClickSendBtn(String text) {
    String apiUrl = null;
    String fileName = null;
    
    if ("黑丝".equals(text)) {
        apiUrl = "https://api.yujn.cn/api/heisi.php?type=json";
        fileName = "heisi.jpg";
    } else if ("白丝".equals(text)) {
        apiUrl = "https://api.yujn.cn/api/baisi.php?type=json";
        fileName = "baisi.jpg";
    } else {
        return false;
    }
    
    final String finalFileName = fileName;
    
    get(apiUrl, null, new PluginCallBack.HttpCallback() {
        public void onSuccess(int respCode, String respContent) {
            JSONObject json = new JSONObject(respContent);
            String code = json.getString("code");
            if (code.equals("1")) {
                String url = json.getString("img");
                download(url, pluginDir + "/" + finalFileName, null, new PluginCallBack.DownloadCallback() {
                    public void onSuccess(File file) {
                        sendImage(getTargetTalker(), file.getAbsolutePath(), "wxe3ad19e142df87b3");
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