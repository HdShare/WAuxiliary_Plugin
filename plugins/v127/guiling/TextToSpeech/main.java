import java.io.File;
import java.net.URLEncoder;
import org.json.JSONObject;

String TTS_API_URL = "https://api-v2.yuafeng.cn/API/kktts.php";
String[] TRIGGER_PREFIXES = {"#语音", "#kk"};
String DEFAULT_VOICE_ID = "94515b46345cc63b379bc046f5f88dd6"; // 温柔女友
String[][] VOICE_ALIASES = {
    {"温柔女友", "94515b46345cc63b379bc046f5f88dd6"},
    {"苹果香", "94b8f3ec59b18723224b7ac5e3fa3a07"},
    {"网恋音", "6240a7ed1f462fdf1b143b3b406ec379"},
    {"夹子音", "ded710805a714c2a4523b84a8ed96388"},
    {"青瘦音", "5ba65879589987546208ed81ade54ca1"},
    {"甜御音", "504f0c1ad23cb7200c1ebf6b39465f78"}
};

void onLoad() {
    log("KK键盘同款恶搞语音 WAuxiliary 适配版已加载");
}

void openSettings() {
    toast("用法：在输入框发送 #语音 文本 或 #kk 文本，会拦截文字并发送语音。");
}

boolean onClickSendBtn(String text) {
    if (text == null) return false;
    String raw = text.trim();

    if (raw.equals("#语音说明") || raw.equals("#kk说明")) {
        sendUsageHelp();
        return true;
    }

    if (raw.equals("#语音音色") || raw.equals("#kk音色")) {
        sendVoiceAliasHelp();
        return true;
    }

    VoiceRequest request = parseVoiceRequest(raw);
    if (request == null) return false;
    if (request.text == null || request.text.length() == 0) {
        toast("请输入要转换的文字，例如：#语音 你好啊");
        return true;
    }

    String talker = getTargetTalker();
    if (talker == null || talker.length() == 0) {
        toast("未获取到当前聊天对象");
        return true;
    }

    toast("正在生成语音...");
    requestVoice(talker, request.text, request.voiceId);
    return true;
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean == null) return;
    if (msgInfoBean.isSend()) return;
    if (!msgInfoBean.isText()) return;

    String talker = msgInfoBean.getTalker();
    String content = msgInfoBean.getContent();
    if (content == null) return;
    content = content.trim();

    String text = extractPayload(content);
    if (text == null || text.length() == 0) return;

    sendText(talker, "正在生成语音...");
    requestVoice(talker, text, DEFAULT_VOICE_ID);
}

String extractPayload(String content) {
    for (int i = 0; i < TRIGGER_PREFIXES.length; i++) {
        String prefix = TRIGGER_PREFIXES[i].trim();
        if (prefix.length() == 0) continue;
        if (content.equals(prefix)) return "";
        if (content.startsWith(prefix + " ")) return content.substring(prefix.length()).trim();
        if (content.startsWith(prefix + "　")) return content.substring(prefix.length()).trim();
    }
    return null;
}

class VoiceRequest {
    String text;
    String voiceId;
}

VoiceRequest parseVoiceRequest(String content) {
    if (content == null) return null;

    if (content.startsWith("#语音id ") || content.startsWith("#kkid ")) {
        String payload = content.startsWith("#语音id ") ? content.substring(6).trim() : content.substring(5).trim();
        int split = payload.indexOf(' ');
        if (split <= 0) return emptyRequest(DEFAULT_VOICE_ID);
        VoiceRequest request = new VoiceRequest();
        request.voiceId = payload.substring(0, split).trim();
        request.text = payload.substring(split + 1).trim();
        if (request.voiceId.length() == 0) request.voiceId = DEFAULT_VOICE_ID;
        return request;
    }

    String payload = extractPayload(content);
    if (payload == null) return null;

    VoiceRequest request = new VoiceRequest();
    request.voiceId = DEFAULT_VOICE_ID;
    request.text = payload;

    int split = payload.indexOf(' ');
    if (split > 0) {
        String first = payload.substring(0, split).trim();
        String aliasVoiceId = findVoiceIdByAlias(first);
        if (aliasVoiceId != null) {
            request.voiceId = aliasVoiceId;
            request.text = payload.substring(split + 1).trim();
        }
    }
    return request;
}

VoiceRequest emptyRequest(String voiceId) {
    VoiceRequest request = new VoiceRequest();
    request.voiceId = voiceId;
    request.text = "";
    return request;
}

String findVoiceIdByAlias(String alias) {
    if (alias == null) return null;
    for (int i = 0; i < VOICE_ALIASES.length; i++) {
        if (alias.equals(VOICE_ALIASES[i][0])) return VOICE_ALIASES[i][1];
    }
    return null;
}

void sendUsageHelp() {
    String talker = getTargetTalker();
    if (talker == null || talker.length() == 0) {
        toast("未获取到当前聊天对象");
        return;
    }
    String msg = "KK语音插件使用说明"
        + "\n\n默认音色：温柔女友"
        + "\n\n1. 默认音色转语音"
        + "\n#语音 你好啊"
        + "\n#kk 你好啊"
        + "\n\n2. 指定内置音色"
        + "\n#语音 温柔女友 你好啊"
        + "\n#语音 苹果香 你好啊"
        + "\n#语音 网恋音 你好啊"
        + "\n\n3. 查看内置音色"
        + "\n#语音音色"
        + "\n\n4. 使用任意音色ID"
        + "\n#语音id 音色ID 文本"
        + "\n例如：#语音id 94515b46345cc63b379bc046f5f88dd6 你好啊"
        + "\n\n当前内置音色：";
    for (int i = 0; i < VOICE_ALIASES.length; i++) {
        msg += "\n- " + VOICE_ALIASES[i][0];
    }
    sendText(talker, msg);
}

void sendVoiceAliasHelp() {
    String talker = getTargetTalker();
    if (talker == null || talker.length() == 0) {
        toast("未获取到当前聊天对象");
        return;
    }
    String msg = "可用音色：";
    for (int i = 0; i < VOICE_ALIASES.length; i++) {
        msg += "\n" + VOICE_ALIASES[i][0];
    }
    msg += "\n\n用法：#语音 音色名 文本\n例如：#语音 温柔女友 你好啊\n高级：#语音id 音色ID 文本";
    sendText(talker, msg);
}

void requestVoice(String talker, String text, String voiceId) {
    try {
        String url = TTS_API_URL + "?action=voice&content=" + URLEncoder.encode(text, "UTF-8");
        if (voiceId != null && voiceId.trim().length() > 0) {
            url += "&voice_id=" + URLEncoder.encode(voiceId.trim(), "UTF-8");
        }
        get(url, null, 30, body -> {
            handleVoiceResponse(talker, body);
        });
    } catch (Exception e) {
        log("requestVoice error: " + e);
        sendText(talker, "语音生成失败：请求构造错误");
    }
}

void handleVoiceResponse(String talker, String body) {
    try {
        if (body == null || body.length() == 0) {
            sendText(talker, "语音生成失败：接口无响应");
            return;
        }
        log("TTS response: " + body);
        JSONObject json = new JSONObject(body);
        int code = json.optInt("code", -1);
        if (code != 0) {
            sendText(talker, "语音生成失败：" + json.optString("msg", "接口返回错误"));
            return;
        }
        JSONObject data = json.optJSONObject("data");
        if (data == null) {
            sendText(talker, "语音生成失败：接口缺少 data");
            return;
        }
        String audioUrl = data.optString("url", "");
        if (audioUrl.length() == 0) {
            sendText(talker, "语音生成失败：接口缺少音频地址");
            return;
        }
        downloadAndSendVoice(talker, audioUrl);
    } catch (Exception e) {
        log("handleVoiceResponse error: " + e);
        sendText(talker, "语音生成失败：响应解析错误");
    }
}

void downloadAndSendVoice(String talker, String audioUrl) {
    try {
        File dir = new File(cacheDir, "kk_voice");
        if (!dir.exists()) dir.mkdirs();
        String baseName = "kk_" + System.currentTimeMillis();
        String mp3Path = new File(dir, baseName + ".mp3").getAbsolutePath();
        download(audioUrl, mp3Path, null, 60, file -> {
            if (file == null || !file.exists()) {
                sendText(talker, "语音下载失败");
                return;
            }
            sendDownloadedVoice(talker, file);
        });
    } catch (Exception e) {
        log("downloadAndSendVoice error: " + e);
        sendText(talker, "语音下载失败：" + e.getMessage());
    }
}

void sendDownloadedVoice(String talker, File mp3File) {
    try {
        boolean convertSilk = getBoolean("convertSilk", true);
        String sendPath = mp3File.getAbsolutePath();
        if (convertSilk) {
            String silkPath = sendPath.replaceAll("\\.mp3$", ".silk");
            int hz = getInt("sampleRate", 24000);
            int result = mp3ToSilk(sendPath, silkPath, hz);
            log("mp3ToSilk result = " + result + ", path = " + silkPath);
            File silkFile = new File(silkPath);
            if (result == 0 && silkFile.exists()) {
                sendPath = silkPath;
            }
        }
        sendVoice(talker, sendPath);
    } catch (Exception e) {
        log("sendDownloadedVoice error: " + e);
        sendText(talker, "语音发送失败：" + e.getMessage());
    }
}