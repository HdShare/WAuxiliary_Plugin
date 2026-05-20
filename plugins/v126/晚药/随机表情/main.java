import java.util.*;
import java.util.function.Consumer;

String API_URL = "https://oiapi.net/api/FunBoxEmoji";

Map<String, String> typeMap = new HashMap<String, String>() {{
    put("龙图", "dragon");
    put("柴郡", "Cheshire");
    put("奶龙", "nailong");
}};

void onLoad() {
    toast("随机表情插件已加载");
}

void onUnload() {}

void onHandleMsg(Object msg) {
    if (!msg.isText()) return;

    String content = msg.getContent().trim();
    String type = null;

    if (typeMap.containsKey(content)) {
        type = typeMap.get(content);
    } else if (content.startsWith("表情 ")) {
        String cmd = content.substring(3).trim();
        type = typeMap.get(cmd);
    } else if (content.equals("表情帮助") || content.equals("帮助")) {
        sendText(msg.getTalker(), "发送以下关键词获取随机表情：\n龙图\n柴郡\n奶龙\n或者发送“表情 龙图”");
        return;
    }

    if (type != null) {
        fetchAndSendEmoji(type, msg.getTalker());
    }
}

void fetchAndSendEmoji(String type, String talker) {
    String url = API_URL + "?0=" + type;
    String filePath = cacheDir + "/emoji_" + System.currentTimeMillis() + ".jpg";

    download(url, filePath, null, 10000, new Consumer<File>() {
        public void accept(File file) {
            if (file != null && file.exists() && file.length() > 0) {
                sendImage(talker, filePath);
            } else {
                sendText(talker, "下载图片失败，请稍后再试");
            }
        }
    });
}