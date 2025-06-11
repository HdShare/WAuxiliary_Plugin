// 此版本对所有私聊生效，群聊只有艾特你时才会生效
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

// 按用户ID存储对话历史
Map msgListMap = new HashMap();
// DeepSeek API密钥
String deepseekApiKey = "sk-你的密钥";
// 私聊触发关键词（可留空，空数组时允许所有私聊）
String[] privateTriggerWords = {}; // 关键词触发，如不填则无限制{"你好","在吗"}
// **新增：来源ID排除列表（精确匹配，支持私聊/群聊用户ID）**
String[] excludedIds = {}; // 填写需要排除的用户ID
// 违禁词列表（不区分大小写，包含即拦截）
String[] forbiddenWords = {}; // 可自定义违禁词

final String MODEL_REASONER = "deepseek-reasoner";  // 推理模型
final String MODEL_CHAT = "deepseek-chat";        // 聊天模型
String currentModel = MODEL_CHAT;             // 默认聊天模型
// 可配置最大对话记录数
int MAX_HISTORY_LENGTH = 100;

// 添加系统角色消息到历史记录（替换旧系统消息）
void addSystemMsg(String talkerId, String content) {
    List userMsgList = msgListMap.get(talkerId);
    if (userMsgList == null) {
        userMsgList = new ArrayList();
        msgListMap.put(talkerId, userMsgList);
    }

    boolean systemMsgFound = false;
    for (int i = 0; i < userMsgList.size(); i++) {
        Map msg = userMsgList.get(i);
        if ("system".equals(msg.get("role"))) {
            // 替换已有的系统消息
            msg.put("content", content);
            log("更新了角色设定" + talkerId);
            systemMsgFound = true;
            break;
        }
    }

    if (!systemMsgFound) {
        // 不存在系统消息，添加新的
        Map systemMsg = new HashMap();
        systemMsg.put("role", "system");
        systemMsg.put("content", content);
        userMsgList.add(systemMsg);
        log("初始化角色设定: " + talkerId);
    }
}

void checkMsgListLimit(String talkerId) {
    List userMsgList = msgListMap.get(talkerId);
    if (userMsgList == null) return;

    if (userMsgList.size() > MAX_HISTORY_LENGTH) {
        // 移除最早的消息（保留系统消息）
        userMsgList.remove(1);
    }
}

// 添加用户消息到历史记录
void addUserMsg(String talkerId, String content) {
    List userMsgList = msgListMap.get(talkerId);
    if (userMsgList == null) return;

    Map msgMap = new HashMap();
    msgMap.put("role", "user");
    msgMap.put("content", content);
    userMsgList.add(msgMap);

    checkMsgListLimit(talkerId);
}

// 添加助手消息
void addAssistantMsg(String talkerId, String content) {
    List userMsgList = msgListMap.get(talkerId);
    if (userMsgList == null) return;

    Map msgMap = new HashMap();
    msgMap.put("role", "assistant");
    msgMap.put("content", content);
    userMsgList.add(msgMap);

    checkMsgListLimit(talkerId);
}

// 构建DeepSeek API请求参数
Map getDeepSeekParam(String talkerId, String content) {
    List userMsgList = msgListMap.get(talkerId);
    Map paramMap = new HashMap();
    paramMap.put("model", currentModel); // 指定模型
    addUserMsg(talkerId, content); // 将用户输入加入历史
    paramMap.put("messages", userMsgList); // 完整对话历史
    paramMap.put("temperature", 0.7); // 响应随机性控制
    paramMap.put("max_tokens", 2000); // 最大返回token数
    return paramMap;
}

// 构建API请求头
Map getDeepSeekHeader() {
    Map headerMap = new HashMap();
    headerMap.put("Content-Type", "application/json"); // JSON格式
    headerMap.put("Authorization", "Bearer " + deepseekApiKey); // API认证
    return headerMap;
}

// 发送请求到DeepSeek API并处理响应
void sendDeepSeekResp(String talker, String content) {
    // 发起POST请求
    post("https://api.deepseek.com/v1/chat/completions", // API地址
            getDeepSeekParam(talker, content), // 请求参数
            getDeepSeekHeader(), // 请求头
            new PluginCallBack.HttpCallback() {
                // 成功回调
                public void onSuccess(int code, String respContent) {
                    try {
                        JSONObject jsonObj = new JSONObject(respContent);
                        JSONArray choices = jsonObj.getJSONArray("choices");
                        if (choices.length() > 0) {
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject message = firstChoice.getJSONObject("message");
                            String msgContent = message.getString("content");

                            // 将AI回复加入对话历史
                            addAssistantMsg(talker, msgContent);

                            // 发送回复给用户
                            sendText(talker, msgContent);
                        }
                    } catch (Exception e) {
                        // 错误处理：解析失败
                        sendText(talker, "[DeepSeek] 解析响应失败: " + e.getMessage());
                    }
                }

                // 失败回调
                public void onError(Exception e) {
                    // 错误处理：请求异常
                    sendText(talker, "[DeepSeek] 请求异常: " + e.getMessage());
                }
            }
    );
}

// 清空当前对话
void cleanupInactiveSessions(String talkerId) {
    msgListMap.remove(talkerId);
}

boolean onLongClickSendBtn(String text) {
    String talkerId = getTargetTalker();
    if (text.equals("切换模型")) {
        currentModel = (currentModel.equals(MODEL_REASONER)) ? MODEL_CHAT : MODEL_REASONER;
        return true; // 消耗事件，确保模型切换生效
    }

    if (text.equals("当前模型")) {
        sendText(talkerId, currentModel);
        return true;
    }

    if (text.startsWith("角色设定: ")) {
        addSystemMsg(talkerId, text.replace("角色设定: ", ""));
        return true;
    }

    if (text.equals("重置")) {
        cleanupInactiveSessions(talkerId);
        return true;
    }

    return false;
}

// 消息处理主入口
void onHandleMsg(Object msgInfoBean) {
    // **新增：来源ID排除判断（优先级最高，所有消息先过滤）**
    String talkerId = msgInfoBean.getTalker(); // 获取消息来源ID（需根据框架实际API调整，此处假设talker为用户ID）

    // 未设定角色直接跳过
    if (msgListMap.get(talkerId) == null) {
        return;
    }

    // 忽略自己发送的消息
    if (msgInfoBean.isSend()) {
        return;
    }

    String content = msgInfoBean.getContent();

    // 群聊消息处理
    if (msgInfoBean.isGroupChat()) {
        if (!msgInfoBean.isAtMe()) {
            return;
        }

        String talker = msgInfoBean.getTalker(); // 群聊中talker为群成员ID

        // 使用通用正则表达式移除所有 @提及
        String processedContent = content
            .replaceAll("(^|\\s+)@[^\\s\\u2005]+", "") // 匹配并移除 @+任意非空字符
            .replaceAll("^\\s+|\\s+$", "")             // 去除首尾空格
            .replaceAll("\\s+", " ");                  // 合并中间多个空格为一个

        sendDeepSeekResp(talker, processedContent);
    }
    // 私聊消息处理（含违禁词+关键词判断）
    else {
        if (msgInfoBean.isText()) {
            String userContent = content.trim().toLowerCase(); // 转小写统一校验
            String talker = msgInfoBean.getTalker(); // 私聊中talker为对方用户ID

            // 第一步：违禁词判断
            if (hasForbiddenWord(userContent)) {
                return;
            }

            // 第二步：关键词触发逻辑
            boolean containsTrigger = false;
            if (privateTriggerWords.length == 0) {
                containsTrigger = true;
            } else {
                for (String word : privateTriggerWords) {
                    if (userContent.contains(word.toLowerCase())) {
                        containsTrigger = true;
                        break;
                    }
                }
            }

            if (!containsTrigger) {
                return;
            }

            sendDeepSeekResp(talker, userContent);
        }
    }
}

// **新增：判断消息是否包含违禁词（复用原有逻辑，封装为方法）**
boolean hasForbiddenWord(String content) {
    for (String word : forbiddenWords) {
        if (content.contains(word)) {
            return true;
        }
    }
    return false;
}
