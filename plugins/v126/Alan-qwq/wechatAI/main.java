// ****核心配置区 仅需修改此处内容****

// 请在下方填写API key
String API_KEY = "你的API Key";

// 请在下方填写角色设定内容
String SYSTEM_PROMPT = "角色设定内容";

//角色设定关闭时，指令 /新对话 的回复内容
String REPLY_RESET_COMMAND = "已为你开启全新对话，历史聊天记录已清空。";

//角色设定开启时，指令 /新对话 的回复内容
String REPLY_RESET_COMMAND_ENABLE_SYSTEM_PROMPT = "已为你开启全新对话，历史聊天记录已清空。";

// 模型名称（可自行更换）
String MODEL_NAME = "deepseek-ai/DeepSeek-V3";

// 硅基流动API接口地址（可更换其他API运营商）
String API_URL = "https://api.siliconflow.cn/v1/chat/completions";

// 模型温度值 0-1之间，值越高回复越随机发散，越低越严谨稳定
double TEMPERATURE = 0.7;

// 单次回复最大token数 控制回复长度，数值越大回复越长
int MAX_TOKENS = 1024;

// 单用户最大保留对话轮数（1轮=1次用户提问+1次AI回复），可自行调整
int MAX_HISTORY_ROUNDS = 10;

// 新对话触发指令，可自行修改
String RESET_COMMAND = "/新对话";

// 引用回复全局开关：true=启用引用回复；false=关闭引用，使用普通文本发送
boolean ENABLE_QUOTE_REPLY = true;

// AI角色设定全局开关，true=启用AI角色设定；false=关闭角色设定，使用模型默认通用回复
boolean ENABLE_SYSTEM_PROMPT = false;


// **** 配置结束 以下内容无需修改 ****
// **** 配置结束 以下内容无需修改 ****
// **** 配置结束 以下内容无需修改 ****


private java.util.Map<String, org.json.JSONArray> userChatHistory = new java.util.concurrent.ConcurrentHashMap<>();

Object callMethod(Object obj, String methodName) {
    try {
        if (obj == null) return null;
        return obj.getClass().getMethod(methodName).invoke(obj);
    } catch (Exception e) {
        log("反射调用方法[" + methodName + "]失败：" + e.getMessage());
        return null;
    }
}
boolean getBoolean(Object obj, String methodName) {
    Object result = callMethod(obj, methodName);
    return result != null && (Boolean) result;
}

String getString(Object obj, String methodName) {
    Object result = callMethod(obj, methodName);
    return result == null ? "" : result.toString();
}

public void onLoad() {
    log("私聊AI回复插件加载成功");
    if (API_KEY == null || API_KEY.isEmpty() || API_KEY.contains("你的API Key")) {
        toast("错误：请先配置你的API Key！");
        log("插件初始化失败：未配置有效的API Key");
        return;
    }
    
    userChatHistory.clear();
    
    String quoteStatus = ENABLE_QUOTE_REPLY ? "已启用" : "已关闭";
    String systemStatus = ENABLE_SYSTEM_PROMPT ? "已启用" : "已关闭";
    
    if (ENABLE_SYSTEM_PROMPT) {
        log("已加载AI角色设定：" + SYSTEM_PROMPT);
    } else {
        log("AI角色设定开关已关闭，将使用模型默认通用回复");
    }
    log("已开启上下文记忆功能，单用户最大保留" + MAX_HISTORY_ROUNDS + "轮对话，重置指令：" + RESET_COMMAND);
    log("引用回复功能当前状态：" + quoteStatus);
    log("AI角色设定功能当前状态：" + systemStatus);
    toast("私聊AI回复插件启用成功 | 引用：" + quoteStatus + " | 角色设定：" + systemStatus);
}

public void onUnload() {

    userChatHistory.clear();
    log("私聊AI回复插件已卸载，上下文记忆已清空");
    toast("私聊AI回复插件已关闭");
}

public void onHandleMsg(Object msgInfoBean) {
    try {
        boolean isPrivateChat = getBoolean(msgInfoBean, "isPrivateChat");
        boolean isSend = getBoolean(msgInfoBean, "isSend");
        boolean isText = getBoolean(msgInfoBean, "isText");
        boolean isOfficialAccount = getBoolean(msgInfoBean, "isOfficialAccount");

        if (!isPrivateChat || isSend || !isText || isOfficialAccount) {
            return;
        }

        String talkerWxid = getString(msgInfoBean, "getTalker");
        String userInput = getString(msgInfoBean, "getContent").trim();
        Object msgIdObj = callMethod(msgInfoBean, "getMsgId");
        long msgId = 0;
        if (msgIdObj != null) {
            msgId = ((Number) msgIdObj).longValue();
        }

        if (userInput.isEmpty()) {
            return;
        }

        log("收到私聊消息 | 发送者：" + talkerWxid + " | 消息ID：" + msgId + " | 内容：" + userInput);

        if (RESET_COMMAND.equals(userInput)) {
            userChatHistory.remove(talkerWxid);
            String resetReply;
            if (ENABLE_SYSTEM_PROMPT) {
                resetReply = REPLY_RESET_COMMAND;
            } else {
                resetReply = REPLY_RESET_COMMAND_ENABLE_SYSTEM_PROMPT;
            }
            final long finalMsgId = msgId;
            final String finalReply = resetReply;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    if (ENABLE_QUOTE_REPLY && finalMsgId > 0) {
                        sendQuoteMsg(talkerWxid, finalMsgId, finalReply);
                        log("新对话重置回复发送成功（引用回复已启用） | 目标用户：" + talkerWxid);
                    } else {
                        sendText(talkerWxid, finalReply);
                        String logTip = ENABLE_QUOTE_REPLY ? "获取消息ID失败，兜底普通文本发送" : "引用回复开关已关闭，使用普通文本发送";
                        log("新对话重置回复发送成功（" + logTip + "） | 目标用户：" + talkerWxid);
                    }
                } catch (Exception e) {
                    log("发送重置回复失败：" + e.getMessage());
                }
            });
            return;
        }

        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL apiUrl = new java.net.URL(API_URL);
                connection = (java.net.HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                org.json.JSONObject requestBody = new org.json.JSONObject();
                requestBody.put("model", MODEL_NAME);
                requestBody.put("stream", false); 
                requestBody.put("temperature", TEMPERATURE);
                requestBody.put("max_tokens", MAX_TOKENS);

                org.json.JSONArray messages = new org.json.JSONArray();
                if (ENABLE_SYSTEM_PROMPT && SYSTEM_PROMPT != null && !SYSTEM_PROMPT.trim().isEmpty()) {
                    org.json.JSONObject systemMessage = new org.json.JSONObject();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", SYSTEM_PROMPT.trim());
                    messages.put(systemMessage);
                }
                org.json.JSONArray historyList = userChatHistory.getOrDefault(talkerWxid, new org.json.JSONArray());
                for (int i = 0; i < historyList.length(); i++) {
                    messages.put(historyList.getJSONObject(i));
                }
                org.json.JSONObject currentUserMessage = new org.json.JSONObject();
                currentUserMessage.put("role", "user");
                currentUserMessage.put("content", userInput);
                messages.put(currentUserMessage);
                requestBody.put("messages", messages);
                java.io.OutputStream outputStream = connection.getOutputStream();
                outputStream.write(requestBody.toString().getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();
                int responseCode = connection.getResponseCode();
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder responseContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseContent.append(line);
                    }
                    reader.close();
                    org.json.JSONObject responseJson = new org.json.JSONObject(responseContent.toString());
                    org.json.JSONArray choices = responseJson.optJSONArray("choices");

                    if (choices != null && choices.length() > 0) {
                        org.json.JSONObject firstChoice = choices.optJSONObject(0);
                        org.json.JSONObject replyMessage = firstChoice.optJSONObject("message");
                        if (replyMessage != null) {
                            String replyContent = replyMessage.optString("content").trim();
                            if (!replyContent.isEmpty()) {
                                org.json.JSONArray userHistory = userChatHistory.getOrDefault(talkerWxid, new org.json.JSONArray());
                                userHistory.put(currentUserMessage);
                                org.json.JSONObject assistantMessage = new org.json.JSONObject();
                                assistantMessage.put("role", "assistant");
                                assistantMessage.put("content", replyContent);
                                userHistory.put(assistantMessage);

                                while (userHistory.length() > MAX_HISTORY_ROUNDS * 2) {
                                    userHistory.remove(0);
                                    userHistory.remove(0);
                                }

                                userChatHistory.put(talkerWxid, userHistory);
                                log("上下文已更新 | 用户：" + talkerWxid + " | 当前历史轮数：" + (userHistory.length() / 2));

                                final long finalMsgId = msgId;
                                final String finalReply = replyContent;
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    try {
                                        if (ENABLE_QUOTE_REPLY && finalMsgId > 0) {
                                            sendQuoteMsg(talkerWxid, finalMsgId, finalReply);
                                            log("AI引用回复发送成功 | 目标：" + talkerWxid + " | 引用消息ID：" + finalMsgId);
                                        } else {
                                            sendText(talkerWxid, finalReply);
                                            String logTip = ENABLE_QUOTE_REPLY ? "获取消息ID失败，兜底普通文本发送" : "引用回复开关已关闭，使用普通文本发送";
                                            log("AI回复发送成功（" + logTip + "） | 目标：" + talkerWxid);
                                        }
                                    } catch (Exception e) {
                                        log("发送消息失败：" + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                            } else {
                                log("AI回复内容为空，未存入上下文");
                            }
                        } else {
                            log("响应解析失败：无message字段，不符合官方响应规范");
                        }
                    } else {
                        log("响应解析失败：无choices字段或内容为空，不符合官方响应规范");
                    }
                } else {
                    java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    StringBuilder errorContent = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorContent.append(line);
                    }
                    errorReader.close();
                    log("API请求失败 | 响应码：" + responseCode + " | 错误信息：" + errorContent);
                }

            } catch (Exception e) {
                log("网络请求异常：" + e.getMessage());
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();

    } catch (Exception e) {
        log("消息处理异常：" + e.getMessage());
        e.printStackTrace();
    }
}
