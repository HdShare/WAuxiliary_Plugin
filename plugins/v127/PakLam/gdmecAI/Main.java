void onLoad() {
    log("gdmecAI 插件已成功加载！");
}

void onHandleMsg(Object msg) {
    log("===== 收到新消息 =====");

    // 1. 过滤非文本消息
    if (!msg.isText()) return;

//    boolean isSend = false;
//    try {
//        isSend = msg.isSend();
//    } catch (Exception e) {
//        isSend = false;
//    }
//    if (isSend) return;

    String talker = msg.getTalker();
    String content = msg.getContent();

    if (content == null) return;
    log("消息内容: " + content);

    if (!content.startsWith("机电问问")) return;

    String question = content.replace("机电问问", "").trim();
    if (question.equals("")) return;

    // 2. 发送提示
    sendText(talker, "正在为您编写代码，预计需要 1-2 分钟，请稍候...");
    log("准备向 Flask 提问: " + question);

    // 3. 开启新线程请求 Flask API
    new Thread(new Runnable() {
        public void run() {
            try {
                String apiUrl = "http://192.168.110.126:5000/api/ask";

                String safeQ = question.replace("\"", "'");
                String json = "{\"question\":\"" + safeQ + "\"}";

                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);

                // 【修改点 1】将读取超时从 60秒 延长到 300秒（5分钟）
                conn.setReadTimeout(300000);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                log("HTTP 状态码: " + code);

                java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                conn.disconnect();

                String response = sb.toString();
                log("Flask 响应长度: " + response.length());

                String answer = extractJsonValue(response, "answer");

                if (answer != null && answer.contains("\\u")) {
                    answer = decodeUnicode(answer);
                }

                if (answer != null && !answer.equals("")) {
                    // 【修改点 2】使用分段发送方法，防止代码太长被微信吞掉
                    sendLongText(talker, answer);
                    log("成功发送 AI 回答");
                } else {
                    sendText(talker, "AI返回为空，请查看日志。");
                }

            } catch (Exception e) {
                String errMsg = e.toString();
                log("线程异常: " + errMsg);
                sendText(talker, "插件报错: " + errMsg);
            }
        }
    }).start();
}

// 【新增】长文本分段发送功能（专门对付几百行的代码）
void sendLongText(String talker, String text) {
    int maxLen = 1500; // 微信安全字数限制，超过容易发送失败
    if (text.length() <= maxLen) {
        sendText(talker, text);
    } else {
        int count = (text.length() + maxLen - 1) / maxLen;
        for (int i = 0; i < count; i++) {
            int start = i * maxLen;
            int end = Math.min((i + 1) * maxLen, text.length());
            sendText(talker, "【代码 " + (i + 1) + "/" + count + "】\n" + text.substring(start, end));
            // 稍微停顿一下，防止发太快导致微信消息乱序
            try { Thread.sleep(1500); } catch (Exception e) {}
        }
    }
}

// 简易 JSON 解析器
String extractJsonValue(String json, String key) {
    String search = "\"" + key + "\":";
    int idx = json.indexOf(search);
    if (idx == -1) {
        search = "\"" + key + "\" :";
        idx = json.indexOf(search);
    }
    if (idx == -1) return null;

    int start = idx + search.length();
    while (start < json.length() && json.charAt(start) == ' ') start++;

    if (start < json.length() && json.charAt(start) == '"') {
        int end = start + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start + 1, end);
    }
    return null;
}

// Unicode 解码器
String decodeUnicode(String unicode) {
    if (unicode == null) return null;
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < unicode.length()) {
        if (i < unicode.length() - 5 && unicode.charAt(i) == '\\' && unicode.charAt(i+1) == 'u') {
            String hex = unicode.substring(i+2, i+6);
            sb.append((char) Integer.parseInt(hex, 16));
            i += 6;
        } else {
            sb.append(unicode.charAt(i));
            i++;
        }
    }
    return sb.toString();
}