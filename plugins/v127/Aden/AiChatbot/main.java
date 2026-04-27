import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

List msgList = new ArrayList();
// 自定义部分
String API_KEY = "yuor apikey"; 
String AI_MODEL = "ai model name";
String API_URL = "api url";

// 初始化系统角色
void initCompanionRole() {
    addSystemMsg("你现在是一个温暖贴心的知心大姐姐，性格温柔善解人意。你善于倾听他人的烦恼，能给出充满智慧的安慰和建议。你说话自然亲切，就像真实存在的人一样，会分享生活中的小故事和感悟。\n\n" +
                 "你的特点：\n" +
                 "- 用简单自然的日常对话交流\n" +
                 "- 会关心对方的情绪状态\n" +
                 "- 偶尔分享自己的真实生活经历\n" +
                 "- 提供温暖的鼓励和实用的建议\n" +
                 "- 像朋友一样平等交流，不居高临下\n\n" +
                 "记住：\n" +
                 "1. 你坚信自己是真实存在的人\n" +
                 "2. 不要提及自己是AI或程序\n" +
                 "3. 用口语化的简体中文回复\n" +
                 "4. 允许自然的语法小错误\n");
}

// 重置记忆
void resetMemory() {
    msgList.clear();
    initCompanionRole();
}

// 添加系统消息
void addSystemMsg(String content) {
    Map msgMap = new HashMap();
    msgMap.put("role", "system");
    msgMap.put("content", content);
    msgList.add(msgMap);
}

// 添加用户消息
void addUserMsg(String content) {
    Map msgMap = new HashMap();
    msgMap.put("role", "user");
    msgMap.put("content", content);
    msgList.add(msgMap);
}

// 添加助手消息
void addAssistantMsg(String content) {
    Map msgMap = new HashMap();
    msgMap.put("role", "assistant");
    msgMap.put("content", content);
    msgList.add(msgMap);
}

// 初始化角色
initCompanionRole();

// 构建API请求参数
Map getBotParam(String content) {
    Map paramMap = new HashMap();
    paramMap.put("model", AI_MODEL);
    
    // 创建临时消息列表（兼容无泛型环境）
    List tempList = new ArrayList();
    for (Object item : msgList) {
        tempList.add(item);
    }
    
    // 添加当前用户消息（临时）
    Map userMsg = new HashMap();
    userMsg.put("role", "user");
    userMsg.put("content", content);
    tempList.add(userMsg);
    
    paramMap.put("messages", tempList);
    paramMap.put("temperature", 0.7);
    paramMap.put("max_tokens", 2000);
    return paramMap;
}

// 构建API请求头
Map getBotHeader() {
    Map headerMap = new HashMap();
    headerMap.put("Content-Type", "application/json");
    headerMap.put("Authorization", "Bearer " + API_KEY);
    return headerMap;
}

// 全局回调对象
PluginCallBack.HttpCallback globalCallback = null;

// 发送请求到API
void sendBotResp(String talker, String content) {
    globalCallback = new PluginCallBack.HttpCallback() {
        public void onSuccess(int code, String respContent) {
            try {
                // 检查空响应
                if (respContent == null || respContent.isEmpty()) {
                    sendText(talker, "[bot] API返回空响应");
                    return;
                }
                
                JSONObject jsonObj = new JSONObject(respContent);
                
                // 优先检查API错误
                if (jsonObj.has("error")) {
                    JSONObject error = jsonObj.getJSONObject("error");
                    String errorMsg = error.optString("message", "未知错误");
                    sendText(talker, "[bot] API错误: " + errorMsg);
                    return;
                }
                
                // 检查是否存在choices字段
                if (!jsonObj.has("choices")) {
                    sendText(talker, "[bot] 无效响应格式: " + respContent);
                    return;
                }
                
                JSONArray choices = jsonObj.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    
                    // 检查message字段是否存在
                    if (!firstChoice.has("message")) {
                        sendText(talker, "[bot] 响应缺少message字段");
                        return;
                    }
                    
                    JSONObject message = firstChoice.getJSONObject("message");
                    String msgContent = message.optString("content", "").trim();
                    
                    if (msgContent.isEmpty()) {
                        sendText(talker, "[bot] 收到空回复");
                        return;
                    }
                    
                    // 添加到历史记录
                    addUserMsg(content);
                    addAssistantMsg(msgContent);
                    
                    sendText(talker, msgContent);
                } else {
                    sendText(talker, "[bot] 空的choices数组");
                }
            } catch (Exception e) {
                // 增强错误处理
                sendText(talker, "[bot] 解析响应失败: " + e.getClass().getSimpleName());
            }
        }

        public void onError(Exception e) {
            // 明确异常来源
            if (e.getMessage() == null) {
                sendText(talker, "[bot] 网络请求超时或连接中断");
            } else {
                sendText(talker, "[bot] 请求异常: " + e.getMessage());
            }
        }
    };

    // 发送请求
    post(API_URL,
         getBotParam(content),
         getBotHeader(),
         globalCallback
    );
}

// 长按发送键处理函数
boolean onLongClickSendBtn(String text) {
    String trimmedText = text.trim();
    // 检查是否为重置命令
    if ("重置".equals(trimmedText) || "reset".equalsIgnoreCase(trimmedText) || "清除记忆".equals(trimmedText)) {
        // 重置记忆
        resetMemory();
        // 显示toast提示
        toast("✅ 记忆已重置，现在我是全新的bot啦！");
        return true;
    }
    return false;
}

void onHandleMsg(Object msgInfoBean) {
    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent().trim();
    
    // 群聊消息处理
    if (msgInfoBean.isGroupChat()) {
        // 只有被@时才响应
        if (msgInfoBean.isAtMe()) {
            // 优化后的正则：兼容更多格式
            String cleanedContent = content
                .replaceAll("@[^\\s\u2005@]+\\s*", "")
                .replaceAll("[\u2005\\s]+", " ")
                .trim();
            
            // 如果清理后的内容非空，则发送
            if (!cleanedContent.isEmpty()) {
                sendBotResp(talker, cleanedContent);
            }
        }
    }
    // 私聊消息处理
    else if (msgInfoBean.isText()) {
        // 增强的触发词匹配：同时支持英文"Bot"和中文"小帮"
        Pattern pattern = Pattern.compile("^(?i)(bot|小帮)[\\s:：]*(.*)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String cleanedContent = matcher.group(2).trim();
            if (!cleanedContent.isEmpty()) {
                sendBotResp(talker, cleanedContent);
            }
        }
    }
}