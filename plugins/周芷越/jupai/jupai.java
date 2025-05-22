import java.io.File;
import java.util.Random;
import me.hd.wauxv.plugin.api.callback.PluginCallBack;

boolean onLongClickSendBtn(String text) {
    if (text == null || !text.startsWith("举牌 ")) {
        return false;
    }
    
    String content = text.substring(2).trim();
    String[] parts = content.split("\\s+", 4);
    
    String wb1 = parts.length > 0 ? parts[0] : "";
    String wb2 = parts.length > 1 ? parts[1] : "";
    String wb3 = parts.length > 2 ? parts[2] : "";
    String wb4 = parts.length > 3 ? parts[3] : "";
    
    Random random = new Random();
    int id = random.nextInt(31) + 1; // 1~31随机数
    
    String imageUrl = "https://api.zxz.ee/api/jupai/" + 
                     "?type=" +
                     "&url=" +
                     "&id=" + id +
                     "&wb1=" + wb1 +
                     "&wb2=" + wb2 +
                     "&wb3=" + wb3 +
                     "&wb4=" + wb4;
    
    // 直接下载图片
    download(imageUrl, pluginDir + "/jupai.jpg", null, new PluginCallBack.DownloadCallback() {
        public void onSuccess(File file) {
            sendImage(getTargetTalker(), file.getAbsolutePath(), "wxe3ad19e142df87b3");
        }

        public void onError(Exception e) {
            sendText(getTargetTalker(), "图片生成失败: " + e.getMessage());
        }
    });
    
    return true;
}