import java.io.File;
import me.hd.wauxv.plugin.api.callback.PluginCallBack;

boolean onLongClickSendBtn(String text) {
    if (text == null || !text.startsWith("黑丝举牌 ")) {
        return false;
    }
    // 爱来自周芷越
    String content = text.substring(4).trim();
    String[] parts = content.split("\\s+", 3);
    
    String msg = parts.length > 0 ? parts[0] : "";
    String msg1 = parts.length > 1 ? parts[1] : "";
    String msg2 = parts.length > 2 ? parts[2] : "";
    String rgb1 = "1"; // 颜色
    
    String imageUrl = "http://api.yujn.cn/api/hsjp1.php?msg=" + msg + 
                     "&msg1=" + msg1 + "&msg2=" + msg2 + "&rgb1=" + rgb1;
    
    // 直接下载图片
    download(imageUrl, pluginDir + "/hsjp.jpg", null, new PluginCallBack.DownloadCallback() {
        public void onSuccess(File file) {
            sendImage(getTargetTalker(), file.getAbsolutePath(), "wxe3ad19e142df87b3");
        }

        public void onError(Exception e) {
            sendText(getTargetTalker(), "图片下载失败: " + e.getMessage());
        }
    });
    
    return true;
}