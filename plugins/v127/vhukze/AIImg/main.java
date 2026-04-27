import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

String[] trigger = {"小喵画画：", "小喵画画:"};
// 设置请求参数
String url = "https://api.siliconflow.cn/v1/images/generations";
String token = "sk-"; // 替换为你的实际token
// 语音模型名称
String model = "Kwai-Kolors/Kolors";
// Qwen/Qwen-Image模型可用图片大小 // 此模型0.3元一张图
// 图像分辨率 “1328x1328”（1：1）
// “1664x928”（16：9）
// “928x1664” （9：16）
// “1472x1140”（4：3）
// “1140x1472”（3：4）
// “1584x1056”（3：2）
// “1056x1584”（2：3）

// Kwai-Kolors/Kolors模型可用图片大小 // 此模型免费
// “1024x1024”（1：1）
// “960x1280”（3：4）
// “768x1024”（3：4）
// “720x1440”（1：2）
// “720x1280”（9：16）
String imageSize = "1024x1024";
Map<String, Object> headers = new HashMap<>();
Map<String, Object> params = new HashMap<>();

void onHandleMsg(Object msgInfo) {

    String content = msgInfo.getContent();

    if (isDo(content, msgInfo) || (msgInfo.isQuote() &&
            isDo(msgInfo.getQuoteMsg().getTitle(), msgInfo))) {

        if (msgInfo.isQuote()) {
            content = msgInfo.getQuoteMsg().getContent() + "。" +
                    getContentAfterSubstring(msgInfo.getQuoteMsg().getTitle());
        } else {
            content = getContentAfterSubstring(content);
        }

        if ("".equals(content.trim())) {
            return;
        }

        params.put("model", model);
        params.put("prompt", content);
        params.put("image_size", imageSize);

        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");

        post(url, params, headers, new PluginCallBack.HttpCallback() {
            public void onSuccess(int respCode, String respContent) {
                JSONObject json = new JSONObject(respContent);
                if (respCode == 200) {

                    // 获取images数组
                    JSONArray imagesArray = json.getJSONArray("images");

                    // 检查数组是否有元素
                    if (imagesArray.length() > 0) {
                        // 获取数组中的第一个对象
                        JSONObject firstImage = imagesArray.getJSONObject(0);

                        // 获取url值
                        String url = firstImage.getString("url");

                        String path = cacheDir + "/img.png";
                        download(url, path, null, new PluginCallBack.DownloadCallback() {
                            public void onSuccess(File file) {
                                sendImage(msgInfo.getTalker(), file.getAbsolutePath());
                                file.delete();
                            }
                        });
                    } else {
                        sendText(msgInfo.getTalker(), "没画出来……再试试呢");
                    }

                } else {
                    sendText(msgInfo.getTalker(), "不太会画……再试试呢");
                }
            }
        });

        sendText(msgInfo.getTalker(), "小喵作画中……");
    }
}


boolean isDo(String content, Object msgInfo) {
    return content.startsWith(trigger[0]) || content.startsWith(trigger[1])
            || (msgInfo.isAtMe() && (content.contains(trigger[0]) || content.contains(trigger[1])));
}

String getContentAfterSubstring(String originalStr) {
    // 定义两个可能的子串
    String sub1 = trigger[0]; // 中文冒号
    String sub2 = trigger[1];  // 英文冒号

    // 查找两个子串的位置
    int index1 = originalStr.indexOf(sub1);
    int index2 = originalStr.indexOf(sub2);

    // 确定有效的起始位置
    int startIndex = -1;
    if (index1 != -1) {
        startIndex = index1 + sub1.length();
    } else if (index2 != -1) {
        startIndex = index2 + sub2.length();
    }

    // 如果找到有效位置，返回后续内容；否则返回空字符串
    if (startIndex != -1 && startIndex <= originalStr.length()) {
        return originalStr.substring(startIndex);
    } else {
        return ""; // 或者根据需求返回null或原字符串
    }
}
