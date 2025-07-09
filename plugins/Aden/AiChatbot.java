import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

List msgList = new ArrayList();

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

// 构建Bot API请求参数
Map getBotParam(String content) {
    Map paramMap = new HashMap();
    paramMap.put("model", "deepseek-ai/DeepSeek-V3"); // 替换为实际模型名称
    addUserMsg(content);
    paramMap.put("messages", msgList);
    paramMap.put("temperature", 0.7);
    paramMap.put("max_tokens", 2000);
    return paramMap;
}

// 构建Bot API请求头
Map getBotHeader() {
    Map headerMap = new HashMap();
    headerMap.put("Content-Type", "application/json");
    headerMap.put("Authorization", "sk-"); // 替换为实际API密钥
    return headerMap;
}

// 发送请求到Bot API
void sendBotResp(String talker, String content) {
    post("https://chat.openai.com/v1/chat/completions", // 替换为实际API地址
            getBotParam(content),
            getBotHeader(),
            new PluginCallBack.HttpCallback() {
                public void onSuccess(int code, String respContent) {
                    try {
                        JSONObject jsonObj = new JSONObject(respContent);
                        JSONArray choices = jsonObj.getJSONArray("choices");
                        if (choices.length() > 0) {
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject message = firstChoice.getJSONObject("message");
                            String msgContent = message.getString("content");
                            
                            // 添加助手回复到历史
                            addAssistantMsg(msgContent);
                            
                            // 发送回复给用户
                            sendText(talker, msgContent);
                        }
                    } catch (Exception e) {
                        // 错误处理：解析失败
                        sendText(talker, "[Bot] 解析响应失败: " + e.getMessage());
                    }
                }

                public void onError(Exception e) {
                    // 错误处理：请求异常
                    sendText(talker, "[Bot] 请求异常: " + e.getMessage());
                }
            }
    );
}

// 长按发送键处理函数
boolean onLongClickSendBtn(String text) {
    // 检查是否为重置命令
    if ("重置".equals(text.trim())) {
        // 重置记忆
        resetMemory();
        // 显示toast提示
        toast("✅ 记忆已重置，现在我是全新的Bot啦！");
        return true; // 返回true表示已处理，不再发送消息
    }
    return false; // 返回false表示未处理，继续发送消息
}

void onHandleMsg(Object msgInfoBean) {
    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent().trim();
    
    // 群聊消息处理
    if (msgInfoBean.isGroupChat()) {
        // 只有被@时才响应
        if (msgInfoBean.isAtMe()) {
            // 移除所有@提及
            String cleanedContent = content
                .replaceAll("@[^\\s\\u2005]+\\s*", "") // 移除@提及及后续空格
                .replaceAll("^\\s+|\\s+$", "") // 去除首尾空格
                .replaceAll("\\s+", " "); // 合并多余空格
            
            // 如果清理后的内容非空，则发送给Bot
            if (!cleanedContent.isEmpty()) {
                sendBotResp(talker, cleanedContent);
            }
        }
    }
    // 私聊消息处理
    else if (msgInfoBean.isText()) {
        // 私聊触发关键词
        if (content.startsWith("Bot")) {
            String cleanedContent = content.substring(4).trim();
            if (!cleanedContent.isEmpty()) {
                sendBotResp(talker, cleanedContent);
            }
        }
    }
}
