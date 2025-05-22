import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

void sendMusic(String talker, String title) {
    try {
        log("开始搜索歌曲: " + title);
        // 发送请求到落月API
        get("https://api.vkeys.cn/v2/music/netease?word=" + title + "&choose=1", null, new PluginCallBack.HttpCallback() {
            public void onSuccess(int respCode, String respContent) {
                try {
                    if (respCode != 200) {
                        String errorMsg = "API请求失败: HTTP " + respCode;
                        toast(errorMsg);
                        log(errorMsg);
                        sendText(talker, "点歌失败: " + errorMsg);
                        return;
                    }
                    
                    // 解析JSON数据
                    JSONObject jsonObject = JSON.parseObject(respContent);
                    if (jsonObject == null) {
                        handleError(talker, "JSON解析失败: 返回数据为空", null);
                        return;
                    }

                    // 获取数据并检查
                    Object idObj = JSONPath.eval(jsonObject, "$.data.id");
                    Object nameObj = JSONPath.eval(jsonObject, "$.data.song");
                    Object singerObj = JSONPath.eval(jsonObject, "$.data.singer");
                    Object linkObj = JSONPath.eval(jsonObject, "$.data.link");
                    Object urlObj = JSONPath.eval(jsonObject, "$.data.url");
                    
                    if (nameObj == null || urlObj == null) {
                        handleError(talker, "未找到歌曲: " + title, null);
                        return;
                    }
                    
                    String id = idObj != null ? idObj.toString() : "";
                    String name = nameObj.toString();
                    String singer = singerObj != null ? singerObj.toString() : "未知歌手";
                    String link = linkObj != null ? linkObj.toString() : "";
                    String url = urlObj.toString();
                    String appId = "wx8dd6ecd81906fd84";    // 网易云音乐
                    
                    log("找到歌曲: " + name + " - " + singer);
                    sendMusicCard(talker, name, singer, url, link, appId);
                } catch (Exception e) {
                    handleError(talker, "处理音乐数据异常", e);
                }
            }

            public void onError(Exception e) {
                handleError(talker, "网络请求异常", e);
            }
        });
    } catch (Exception e) {
        handleError(talker, "点歌功能异常", e);
    }
}

void handleError(String talker, String message, Exception e) {
    String fullMessage = e != null ? message + ": " + e.getMessage() : message;
    
    // 记录日志
    log("点歌错误: " + fullMessage);
    
    // 显示toast提示
    toast("点歌失败: " + (e != null ? e.getMessage() : message));
    
    // 发送消息通知
//     sendText(talker, "点歌失败: " + message);
}

void onHandleMsg(Object msgInfo) {
    if (!msgInfo.isSend()) return;
    if (msgInfo.isText()) {
        String content = msgInfo.getContent();
        String talker = msgInfo.getTalker();
        if (content.startsWith("/点歌 ")) {
            // 直接提取命令后的所有内容作为歌名
            String title = content.substring("/点歌 ".length()).trim();
            
            // 检查歌名是否为空
            if (title.isEmpty()) {
                String errorMsg = "搜索关键词不能为空";
                toast(errorMsg);
                log(errorMsg);
                return;
            }
            
            // 调用发送音乐函数
            sendMusic(talker, title);
        }
    }
}
