// 插件加载回调 - 启用时执行
void onLoad() {
    log("【星座插件】加载成功，原生网络终极版");
    toast("星座运势插件已启用，发送【星座 星座名】即可查询");
}

// 插件卸载回调 - 禁用时执行
void onUnLoad() {
    log("【星座插件】已卸载");
    toast("星座运势插件已禁用");
}

// 消息监听核心回调
void onHandleMsg(Object msgInfoBean) {
    try {
        // 安全获取消息核心字段
        String msgContent = msgInfoBean.getContent() == null ? "" : msgInfoBean.getContent().trim();
        boolean isText = msgInfoBean.isText();
        String talker = msgInfoBean.getTalker();

        log("【星座插件】收到消息：" + msgContent + " | 是否文本：" + isText + " | 聊天ID：" + talker);

        // 仅处理非空文本消息
        if (!isText || msgContent.isEmpty()) {
            log("【星座插件】非文本/空消息，跳过");
            return;
        }

        // 兼容全角/半角空格、多空格，匹配触发指令
        String formatContent = msgContent.replaceAll("　", " ").trim();
        if (formatContent.matches("^星座\\s+.*$")) {
            log("【星座插件】指令匹配成功");
            toast("触发星座查询");

            // 拆分获取星座名称
            String[] cmdParts = formatContent.split("\\s+", 2);
            if (cmdParts.length < 2 || cmdParts[1].trim().isEmpty()) {
                sendText(talker, "❌ 格式错误\n正确用法：星座 星座名称\n示例：星座 白羊、星座 狮子座");
                return;
            }

            final String constellationName = cmdParts[1].trim();
            final String targetTalker = talker;
            log("【星座插件】查询星座：" + constellationName);

            // ========== 核心修复1：彻底弃用框架回调类，改用JDK原生线程+网络请求 ==========
            // 用JDK自带的Thread+Runnable，绝对不会报类找不到，同时规避主线程网络请求限制
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // ========== 核心修复2：修正接口参数名为msg，匹配官方请求示例 ==========
                        String requestUrl = "https://api.xcvts.cn/api/hotlist/xzys/wz?msg=" + java.net.URLEncoder.encode(constellationName, "UTF-8");
                        log("【星座插件】请求接口：" + requestUrl);

                        // JDK原生HttpURLConnection，无需依赖框架任何类，所有Java环境通用
                        java.net.URL url = new java.net.URL(requestUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        // 请求配置
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                        conn.setRequestProperty("Accept", "*/*");

                        // 获取响应码
                        int responseCode = conn.getResponseCode();
                        log("【星座插件】接口响应码：" + responseCode);

                        if (responseCode == 200) {
                            // 读取响应内容
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                            String line;
                            StringBuffer result = new StringBuffer();
                            while ((line = br.readLine()) != null) {
                                result.append(line);
                            }
                            br.close();
                            conn.disconnect();

                            String response = result.toString();
                            log("【星座插件】接口返回内容：" + response);

                            // ========== 核心修复3：适配JSON返回格式，精准解析 ==========
                            String sendContent = "✨【" + constellationName + "座 今日运势】✨\n\n";
                            try {
                                // 全类名调用JSON解析，无需import，兼容脚本环境
                                org.json.JSONObject resultJson = new org.json.JSONObject(response);
                                int code = resultJson.optInt("code", -1);
                                if (code == 200) {
                                    // 适配接口返回的字段，优先取data、content，兜底用msg
                                    sendContent += resultJson.optString("data", 
                                                        resultJson.optString("content", 
                                                        resultJson.optString("msg", "暂无该星座的运势信息")));
                                } else {
                                    sendContent = "❌ 查询失败：" + resultJson.optString("msg", "接口返回错误");
                                }
                            } catch (Exception e) {
                                // JSON解析失败，直接返回原始内容兜底
                                log("【星座插件】JSON解析异常：" + e.getMessage());
                                sendContent += response;
                            }

                            // 发送结果
                            log("【星座插件】准备发送消息：" + sendContent);
                            sendText(targetTalker, sendContent);
                            log("【星座插件】消息发送成功");
                        } else {
                            // 响应码错误
                            sendText(targetTalker, "❌ 请求失败，接口响应码：" + responseCode);
                            log("【星座插件】请求失败，响应码：" + responseCode);
                        }
                    } catch (Exception e) {
                        // 网络请求全流程异常捕获
                        log("【星座插件】网络请求异常：" + e.getMessage());
                        sendText(targetTalker, "❌ 网络请求失败\n错误详情：" + e.getMessage());
                    }
                }
            }).start();
        }
    } catch (Exception e) {
        log("【星座插件】主逻辑异常：" + e.getMessage());
        toast("插件运行异常，请查看日志");
    }
}
