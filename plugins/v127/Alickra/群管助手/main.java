// 群管助手 — 社区版 + 完整版加载器
// 社区功能：欢迎词、违禁词、关键词回复、帮助
// 完整功能：需要通过 #激活 邀请码 激活

import com.groupadmin.BshBridge;
import com.groupadmin.NativeBridge;
import com.groupadmin.PluginMain;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

// ===== 状态 =====
Object core = null;
boolean fullMode = false;

// ===== 社区版数据 =====
String welcomeTemplate = null;
JSONArray bannedWords = null;
JSONObject keywordReplies = null;
JSONArray superAdmins = null;
String serverUrl = null;

// ===== 生命周期 =====

void onLoad() {
    loadCommunityConfig();
    log("群管助手已加载（社区版）");

    String activated = getString("activated", "false");
    if ("true".equals(activated)) {
        tryLoadFullVersion();
    }
}

void onUnload() {
    if (core != null) {
        core.onUnload();
    }
    log("群管助手已卸载");
}

void openSettings() {
    String current = getString("super_admins", "");
    String url = getString("server_url", "http://127.0.0.1:8080");
}

// ===== 社区版配置 =====

void loadCommunityConfig() {
    welcomeTemplate = getString("welcome_template", "欢迎 {昵称} 加入本群！");
    serverUrl = getString("server_url", "http://127.0.0.1:8080");

    try {
        bannedWords = new JSONArray(getString("banned_words", "[]"));
    } catch (JSONException e) {
        bannedWords = new JSONArray();
    }

    try {
        keywordReplies = new JSONObject(getString("keyword_replies", "{}"));
    } catch (JSONException e) {
        keywordReplies = new JSONObject();
    }

    try {
        superAdmins = new JSONArray(getString("super_admins", "[]"));
    } catch (JSONException e) {
        superAdmins = new JSONArray();
    }
}

boolean isSuperAdmin(String wxid) {
    for (int i = 0; i < superAdmins.length(); i++) {
        if (wxid.equals(superAdmins.optString(i))) return true;
    }
    return false;
}

// ===== 完整版加载 =====

void tryLoadFullVersion() {
    try {
        loadDex(pluginDir + "/bridge.dex");

        String soPath = pluginDir + "/lib/libcore.so";
        File soFile = new File(soPath);
        if (!soFile.exists()) {
            log("SO 文件不存在，退回社区版");
            return;
        }
        System.load(soPath);

        if (!NativeBridge.isEnvironmentSafe()) {
            log("环境检测未通过，退回社区版");
            return;
        }

        String deviceId = NativeBridge.getDeviceId();
        String wxid = getLoginWxid();
        String appHash = NativeBridge.getAppHash();
        String token = doAuth(deviceId, wxid, appHash);
        if (token == null) {
            log("认证失败，退回社区版");
            return;
        }

        byte[] encrypted = doDownloadBytes(
            serverUrl + "/api/plugin/download",
            "Bearer " + token
        );
        if (encrypted == null) {
            log("下载插件失败，退回社区版");
            return;
        }

        byte[] dexBytes = NativeBridge.decryptDex(encrypted, deviceId, wxid);
        if (dexBytes == null) {
            log("解密失败，退回社区版");
            return;
        }

        File tmpDex = new File(pluginDir + "/tmp_core.dex");
        FileOutputStream fos = new FileOutputStream(tmpDex);
        fos.write(dexBytes);
        fos.close();
        loadDex(tmpDex.getAbsolutePath());
        tmpDex.delete();

        setupBshBridge();
        core = new PluginMain();
        core.init(getLoginWxid(), token, deviceId, wxid);
        fullMode = true;
        log("完整版已加载");

    } catch (Exception e) {
        log("加载完整版失败: " + e.getMessage() + "，退回社区版");
        core = null;
        fullMode = false;
    }
}

void setupBshBridge() {
    BshBridge.setBridge(new BshBridge.Bridge() {
        public void sendText(String talker, String content) { global.sendText(talker, content); }
        public void sendImage(String talker, String path) { global.sendImage(talker, path); }
        public void sendVoice(String talker, String path) { global.sendVoice(talker, path); }
        public void delChatroomMember(String id, String m) { global.delChatroomMember(id, m); }
        public void addChatroomMember(String id, String m) { global.addChatroomMember(id, m); }
        public void verifyUser(String w, String t, int s) { global.verifyUser(w, t, s); }
        public void log(String msg) { global.log(msg); }
        public String getString(String k, String d) { return global.getString(k, d); }
        public void putString(String k, String v) { global.putString(k, v); }
        public boolean getBoolean(String k, boolean d) { return global.getBoolean(k, d); }
        public void putBoolean(String k, boolean v) { global.putBoolean(k, v); }
        public String getFriendDisplayName(String w, String g) { return global.getFriendDisplayName(w, g); }
        public byte[] downloadBytes(String url, String auth) { return doDownloadBytes(url, auth); }
    });
}

// ===== 激活流程 =====

void handleActivate(String groupWxid, String sender, String code) {
    if (code == null || code.isEmpty()) {
        sendText(groupWxid, "📝 用法：#激活 你的邀请码");
        return;
    }

    new Thread(new Runnable() {
        public void run() {
            try {
                String deviceId = computeDeviceId();
                String wxid = getLoginWxid();
                String appHash = computeAppHash();

                JSONObject body = new JSONObject();
                body.put("code", code);
                body.put("device_id", deviceId);
                body.put("wxid", wxid);
                body.put("app_hash", appHash);

                String resp = postJson(serverUrl + "/api/activate", body.toString());
                if (resp == null) {
                    sendText(groupWxid, "激活失败：无法连接服务器");
                    return;
                }

                JSONObject json = new JSONObject(resp);
                if (!"ok".equals(json.optString("status"))) {
                    sendText(groupWxid, "激活失败：" + json.optString("message", "未知错误"));
                    return;
                }

                String token = json.getString("token");

                loadDex(pluginDir + "/bridge.dex");

                String abi = System.getProperty("os.arch", "arm64-v8a");
                if (abi.contains("aarch64") || abi.contains("arm64")) {
                    abi = "arm64-v8a";
                } else {
                    abi = "armeabi-v7a";
                }

                String[] soHashOut = new String[1];
                byte[] soData = doDownloadBytesWithHash(
                    serverUrl + "/api/plugin/so?abi=" + abi,
                    "Bearer " + token,
                    "X-SO-SHA256",
                    soHashOut
                );
                if (soData == null) {
                    sendText(groupWxid, "激活失败：下载 SO 文件失败");
                    return;
                }

                if (soHashOut[0] == null || soHashOut[0].isEmpty()) {
                    sendText(groupWxid, "激活失败：服务器未返回 SO 校验哈希");
                    return;
                }
                MessageDigest soMd = MessageDigest.getInstance("SHA-256");
                String actualHash = bytesToHex(soMd.digest(soData));
                if (!soHashOut[0].equalsIgnoreCase(actualHash)) {
                    sendText(groupWxid, "激活失败：SO 文件校验失败");
                    return;
                }

                File libDir = new File(pluginDir + "/lib");
                libDir.mkdirs();
                File soFile = new File(libDir, "libcore.so");
                FileOutputStream fos = new FileOutputStream(soFile);
                fos.write(soData);
                fos.close();

                System.load(soFile.getAbsolutePath());

                if (!NativeBridge.isEnvironmentSafe()) {
                    sendText(groupWxid, "激活失败：环境检测未通过");
                    return;
                }

                byte[] encrypted = doDownloadBytes(
                    serverUrl + "/api/plugin/download",
                    "Bearer " + token
                );
                if (encrypted == null) {
                    sendText(groupWxid, "激活失败：下载插件失败");
                    return;
                }

                String realDeviceId = NativeBridge.getDeviceId();
                byte[] dexBytes = NativeBridge.decryptDex(encrypted, realDeviceId, wxid);
                if (dexBytes == null) {
                    sendText(groupWxid, "激活失败：解密失败");
                    return;
                }

                File tmpDex = new File(pluginDir + "/tmp_core.dex");
                FileOutputStream dfos = new FileOutputStream(tmpDex);
                dfos.write(dexBytes);
                dfos.close();
                loadDex(tmpDex.getAbsolutePath());
                tmpDex.delete();

                setupBshBridge();
                core = new PluginMain();
                core.init(getLoginWxid(), token, realDeviceId, wxid);
                fullMode = true;

                putString("activated", "true");
                putString("auth_token", token);

                sendText(groupWxid, "🎉 激活成功！完整版功能已启用，重启后自动加载");
            } catch (Exception e) {
                sendText(groupWxid, "激活失败：" + e.getMessage());
            }
        }
    }).start();
}

// ===== 消息回调 =====

void onHandleMsg(Object msgInfoBean) {
    if (core != null) {
        core.onHandleMsg(
            msgInfoBean.isGroupChat(),
            msgInfoBean.getTalker(),
            msgInfoBean.getSendTalker(),
            msgInfoBean.getContent(),
            msgInfoBean.getMsgId(),
            msgInfoBean.isText()
        );
        return;
    }
    handleCommunityMsg(msgInfoBean);
}

void onMemberChange(String type, String groupWxid, String userWxid, String userName) {
    if (core != null) {
        core.onMemberChange(type, groupWxid, userWxid, userName);
        return;
    }
    handleCommunityMemberChange(type, groupWxid, userWxid, userName);
}

void onNewFriend(String wxid, String ticket, int scene) {
    if (core != null) {
        core.onNewFriend(wxid, ticket, scene);
        return;
    }
}

// ===== 社区版消息处理 =====

void handleCommunityMsg(Object msgInfoBean) {
    if (!msgInfoBean.isGroupChat() || !msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String sender = msgInfoBean.getSendTalker();
    String content = msgInfoBean.getContent();

    if (bannedWords != null && bannedWords.length() > 0) {
        for (int i = 0; i < bannedWords.length(); i++) {
            String word = bannedWords.optString(i, "");
            if (!word.isEmpty() && content.contains(word)) {
                sendText(talker, "[AtWx=" + sender + "] ⚠️ 请注意用词规范");
                return;
            }
        }
    }

    if (content.startsWith("#")) {
        String stripped = content.substring(1).trim();
        if (stripped.isEmpty()) return;

        String cmd;
        String arg;
        int spaceIdx = stripped.indexOf(' ');
        if (spaceIdx > 0) {
            cmd = stripped.substring(0, spaceIdx);
            arg = stripped.substring(spaceIdx + 1).trim();
        } else {
            cmd = stripped;
            arg = "";
        }

        handleCommunityCommand(talker, sender, cmd, arg);
        return;
    }

    if (keywordReplies != null && keywordReplies.length() > 0) {
        java.util.Iterator keys = keywordReplies.keys();
        while (keys.hasNext()) {
            String keyword = (String) keys.next();
            if (content.contains(keyword)) {
                sendText(talker, keywordReplies.optString(keyword));
                return;
            }
        }
    }
}

void handleCommunityCommand(String talker, String sender, String cmd, String arg) {
    if ("帮助".equals(cmd)) {
        showCommunityHelp(talker);
        return;
    }
    if ("激活".equals(cmd)) {
        handleActivate(talker, sender, arg);
        return;
    }
    if ("设置欢迎词".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        if (arg.isEmpty()) { sendText(talker, "📝 用法：#设置欢迎词 欢迎{昵称}加入本群"); return; }
        welcomeTemplate = arg;
        putString("welcome_template", arg);
        sendText(talker, "✅ 欢迎词已更新");
        return;
    }
    if ("添加违禁词".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        if (arg.isEmpty()) { sendText(talker, "📝 用法：#添加违禁词 词1 词2（空格分隔）"); return; }
        String[] words = arg.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            bannedWords.put(words[i]);
        }
        putString("banned_words", bannedWords.toString());
        sendText(talker, "✅ 已添加 " + words.length + " 个违禁词");
        return;
    }
    if ("删除违禁词".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < bannedWords.length(); i++) {
            String w = bannedWords.optString(i, "");
            if (!w.equals(arg)) newArr.put(w);
        }
        bannedWords = newArr;
        putString("banned_words", bannedWords.toString());
        sendText(talker, "✅ 已删除违禁词：" + arg);
        return;
    }
    if ("违禁词列表".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        if (bannedWords.length() == 0) {
            sendText(talker, "📋 违禁词列表为空");
        } else {
            StringBuilder sb = new StringBuilder("🚫 违禁词列表（共 " + bannedWords.length() + " 个）\n━━━━━━━━━━━━\n");
            for (int i = 0; i < bannedWords.length(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(bannedWords.optString(i)).append("\n");
            }
            sendText(talker, sb.toString());
        }
        return;
    }
    if ("添加回复".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        if (!arg.contains("=>")) { sendText(talker, "📝 用法：#添加回复 关键词=>回复内容"); return; }
        String[] parts = arg.split("=>", 2);
        try {
            keywordReplies.put(parts[0].trim(), parts[1].trim());
            putString("keyword_replies", keywordReplies.toString());
            sendText(talker, "✅ 已添加关键词回复：" + parts[0].trim());
        } catch (JSONException e) { /* ignore */ }
        return;
    }
    if ("删除回复".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        keywordReplies.remove(arg);
        putString("keyword_replies", keywordReplies.toString());
        sendText(talker, "✅ 已删除关键词回复：" + arg);
        return;
    }
    if ("回复列表".equals(cmd)) {
        if (!isSuperAdmin(sender)) { sendText(talker, "⚠️ 权限不足，仅管理员可操作"); return; }
        if (keywordReplies.length() == 0) {
            sendText(talker, "📋 回复列表为空");
        } else {
            StringBuilder sb = new StringBuilder("💬 关键词回复列表\n━━━━━━━━━━━━\n");
            java.util.Iterator keys = keywordReplies.keys();
            int idx = 1;
            while (keys.hasNext()) {
                String k = (String) keys.next();
                sb.append("  ").append(idx++).append(". ").append(k).append(" → ").append(keywordReplies.optString(k)).append("\n");
            }
            sendText(talker, sb.toString());
        }
        return;
    }

    if (isAdvancedCommand(cmd)) {
        sendText(talker, "🔒 此功能需要激活完整版\n💡 发送 #激活 邀请码 解锁");
        return;
    }
}

boolean isAdvancedCommand(String cmd) {
    String[] advanced = {"踢", "加黑", "禁言", "解禁", "设管理", "删管理", "自动审核",
                         "签到", "积分", "排行榜", "抽签", "随机选人"};
    for (int i = 0; i < advanced.length; i++) {
        if (advanced[i].equals(cmd)) return true;
    }
    return false;
}

void showCommunityHelp(String talker) {
    String help = "━━━━━━━━━━━━━━━━\n"
        + "  🛡️ 群管助手 v1.0.0\n"
        + "━━━━━━━━━━━━━━━━\n"
        + "\n"
        + "📋 基础命令\n"
        + "┌──────────────────\n"
        + "│ #帮助          查看此菜单\n"
        + "│ #设置欢迎词    设置入群欢迎语\n"
        + "│ #添加违禁词    添加违禁词（空格分隔）\n"
        + "│ #删除违禁词    删除指定违禁词\n"
        + "│ #违禁词列表    查看当前违禁词\n"
        + "│ #添加回复      关键词=>回复内容\n"
        + "│ #删除回复      删除指定回复规则\n"
        + "│ #回复列表      查看所有回复规则\n"
        + "└──────────────────\n"
        + "\n"
        + "🔑 完整版命令（需激活）\n"
        + "┌──────────────────\n"
        + "│ #签到    每日签到得积分\n"
        + "│ #积分    查看我的积分\n"
        + "│ #排行榜  积分排行\n"
        + "│ #踢      踢出群成员\n"
        + "│ #拉黑    拉黑用户\n"
        + "│ #设管理  设置群管理员\n"
        + "│ #删管理  移除群管理员\n"
        + "└──────────────────\n"
        + "\n"
        + "💡 发送 #激活 邀请码 解锁完整版";
    sendText(talker, help);
}

// ===== 社区版入群欢迎 =====

void handleCommunityMemberChange(String type, String groupWxid, String userWxid, String userName) {
    if ("join".equals(type)) {
        String name = (userName != null && !userName.isEmpty()) ? userName : userWxid;
        try {
            name = getFriendNickName(userWxid, groupWxid);
        } catch (Exception e) { /* fallback to userName */ }
        String msg = welcomeTemplate.replace("{昵称}", name);
        sendText(groupWxid, msg);
    }
}

// ===== 网络工具 =====

String computeDeviceId() {
    try {
        String raw = System.getProperty("ro.serialno", "unknown")
                   + System.getProperty("ro.product.model", "unknown")
                   + System.getProperty("ro.product.manufacturer", "unknown")
                   + System.getProperty("ro.product.board", "unknown")
                   + System.getProperty("ro.hardware", "unknown");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(raw.getBytes("UTF-8"));
        return bytesToHex(hash).substring(0, 32);
    } catch (Exception e) {
        return "fallback_device_id";
    }
}

String computeAppHash() {
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest("groupadmin-v1".getBytes("UTF-8"));
        return bytesToHex(hash);
    } catch (Exception e) {
        return "unknown";
    }
}

String doAuth(String deviceId, String wxid, String appHash) {
    try {
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("wxid", wxid);
        body.put("app_hash", appHash);

        String resp = postJson(serverUrl + "/api/auth", body.toString());
        if (resp == null) return null;

        JSONObject json = new JSONObject(resp);
        return json.optString("token", null);
    } catch (Exception e) {
        return null;
    }
}

byte[] doDownloadBytes(String urlStr, String authHeader) {
    HttpURLConnection conn = null;
    try {
        URL url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) return null;

        InputStream is = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    } catch (Exception e) {
        return null;
    } finally {
        if (conn != null) conn.disconnect();
    }
}

// Returns String[]{hex_hash_from_header, null} as out[0], bytes as return value
byte[] doDownloadBytesWithHash(String urlStr, String authHeader, String hashHeader, String[] hashOut) {
    HttpURLConnection conn = null;
    try {
        URL url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) return null;

        if (hashOut != null && hashOut.length > 0) {
            hashOut[0] = conn.getHeaderField(hashHeader);
        }

        InputStream is = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    } catch (Exception e) {
        return null;
    } finally {
        if (conn != null) conn.disconnect();
    }
}

String postJson(String urlStr, String jsonBody) {
    HttpURLConnection conn = null;
    try {
        URL url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            InputStream es = conn.getErrorStream();
            if (es != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(es, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    } catch (Exception e) {
        return null;
    } finally {
        if (conn != null) conn.disconnect();
    }
}

String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
        sb.append(String.format("%02x", bytes[i]));
    }
    return sb.toString();
}
