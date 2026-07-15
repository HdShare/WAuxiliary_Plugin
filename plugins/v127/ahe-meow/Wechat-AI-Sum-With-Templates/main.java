/**
 * AI多模板聊天总结插件
 * 
 * 原作者: 长空X (hjkl950217)
 * 原始项目: https://github.com/hjkl950217/WAuxiliary_Plugin
 * 
 * 改进维护: ahe-meow
 * 当前项目: https://github.com/ahe-meow/Wechat-AI-Sum-With-Templates
 * 
 * 版本: v1.3.1
 * 更新时间: 20260715
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;

import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

String CFG_API_URL = "api_url";
String CFG_API_KEY = "api_key";
String CFG_MODEL = "model";
String CFG_LOG_ENABLE = "log_enable";
String CFG_TEMPLATES = "templates";
String CFG_DEFAULT_TEMPLATE = "default_template";
String CFG_SEND_TO_CURRENT = "send_to_current";
String CFG_SEND_TO_FILEHELPER = "send_to_filehelper";
String CFG_SEND_TO_CUSTOM = "send_to_custom";
String CFG_CUSTOM_WXID = "custom_wxid";
String CFG_API_TYPE = "api_type";
String API_TYPE_OPENAI = "openai";
String API_TYPE_CLAUDE = "claude";
String DEFAULT_API_TYPE = API_TYPE_OPENAI;
String CFG_DISABLE_REASONING = "disable_reasoning";
String CFG_PROMPT_DISABLE_REASONING = "prompt_disable_reasoning";
String CFG_MAX_TOKENS = "max_tokens";
String CFG_ENABLE_MAX_TOKENS = "enable_max_tokens";
boolean DEFAULT_LOG_ENABLE = false;
String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
String DEFAULT_MODEL = "gpt-4o-mini";
boolean DEFAULT_DISABLE_REASONING = true;
boolean DEFAULT_PROMPT_DISABLE_REASONING = true;
int DEFAULT_MAX_TOKENS = 4000;
boolean DEFAULT_ENABLE_MAX_TOKENS = true;
String DEFAULT_SUMMARY_PROMPT = "你是一个聊天记录总结助手。请基于用户提供的聊天记录，用简短、客观、清晰的中文总结最近聊天内容。\n\n" +
        "格式要求：\n" +
        "1. 第一行是标题，用【】包裹，例如【6月25日群聊摘要】\n" +
        "2. 标题后空一行，再写正文\n" +
        "3. 正文按要点分行，每行一个要点\n" +
        "4. 涉及人名时用【】包裹，例如【张三】说...\n\n" +
        "内容要求：\n" +
        "1. 控制在 200 字以内\n" +
        "2. 优先总结事实、结论、待办和重要分歧\n" +
        "3. 不要编造聊天记录中没有的信息\n" +
        "4. 如果聊天内容太少或无有效文本，请直接说明无法总结\n" +
        "5. 只输出最终总结，不要输出推理过程、分析过程或草稿";

boolean mLogEnabled = false;

void onLoad() {
    ensureDefaultConfig();
    mLogEnabled = isLogEnabled();
    logx("AI聊天总结插件已加载");
}

void onUnload() {
    logx("AI聊天总结插件已卸载");
}

void openSettings() {
    showConfigDialog();
}

boolean onClickSendBtn(String text) {
    if (text == null) return false;
    String cmd = text.trim();
    if (isAiCommand(cmd)) {
        String talker = getTargetTalker();
        handleCommand(talker, cmd);
        return true;
    }
    return false;
}

void onHandleMsg(Object msgInfoBean) {
    try {
        if (msgInfoBean == null) return;
        if (!msgInfoBean.isSend()) return;
        if (!msgInfoBean.isText()) return;

        String content = msgInfoBean.getContent();
        if (content == null) return;
        content = content.trim();
        if (!isAiCommand(content)) return;

        handleCommand(msgInfoBean.getTalker(), content);
    } catch (Throwable e) {
        logx("AI聊天总结处理消息失败: " + e.getMessage());
    }
}

boolean isAiCommand(String text) {
    if (TextUtils.isEmpty(text)) return false;
    return text.equals("/ai") || text.startsWith("/ai ");
}

void handleCommand(String talker, String cmd) {
    try {
        if (TextUtils.isEmpty(talker)) {
            toast("请先进入聊天界面");
            return;
        }

        if ("/ai".equals(cmd) || "/ai 帮助".equals(cmd) || "/ai help".equalsIgnoreCase(cmd)) {
            showHelpDialog();
            return;
        }

        if ("/ai 配置".equals(cmd) || "/ai 设置".equals(cmd) || "/ai config".equalsIgnoreCase(cmd)) {
            showConfigDialog();
            return;
        }

        if (isSummaryCommand(cmd)) {
            if ("/ai 总结".equals(cmd)) {
                summarizeWithDefaultTemplate(talker);
            } else if (cmd.startsWith("/ai 总结 ")) {
                String arg = cmd.substring("/ai 总结 ".length()).trim();
                if (arg.matches("\\d+")) {
                    summarizeChat(talker, clampCount(Integer.parseInt(arg)));
                } else {
                    summarizeWithTemplateName(talker, arg);
                }
            }
            return;
        }

        showHelpDialog();
    } catch (Throwable e) {
        logx("[AI总结] 处理命令失败: " + e.getMessage());
    }
}

boolean isSummaryCommand(String cmd) {
    if (TextUtils.isEmpty(cmd)) return false;
    if ("/ai 总结".equals(cmd)) return true;
    if (cmd.startsWith("/ai 总结 ")) {
        String arg = cmd.substring("/ai 总结 ".length()).trim();
        return !TextUtils.isEmpty(arg);
    }
    return false;
}

int clampCount(int count) {
    if (count < 50) return 50;
    if (count > 200) return 200;
    return count;
}

void summarizeChat(final String talker, final int count) {
    try {
        if (!hasApiConfig()) {
            showConfigDialog();
            return;
        }

        toast("AI 正在总结，请稍候...");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String historyText = buildHistoryText(talker, count);
                    if (TextUtils.isEmpty(historyText)) {
                        logx("[AI总结] 无有效聊天记录 talker=" + talker + " count=" + count);
                        toast("AI总结失败：没有读取到有效聊天记录");
                        return;
                    }
                    callSummaryApi(talker, historyText, count);
                } catch (Throwable e) {
                    logx("[AI总结] 生成总结失败: " + e.getMessage());
                    toast("AI总结失败：" + e.getMessage());
                }
            }
        }).start();
    } catch (Throwable e) {
        logx("[AI总结] 启动总结失败: " + e.getMessage());
    }
}

void summarizeWithDefaultTemplate(final String talker) {
    JSONObject tpl = getDefaultTemplateObject();
    if (tpl == null) {
        summarizeByTemplateValues(talker, getSummaryPrompt(), "days", 1, 50, "默认");
        return;
    }
    summarizeByTemplateObject(talker, tpl);
}

void summarizeWithTemplateName(final String talker, final String name) {
    JSONObject tpl = findTemplate(name);
    if (tpl == null) {
        toast("未找到模板：" + name);
        return;
    }
    summarizeByTemplateObject(talker, tpl);
}

void summarizeByTemplateObject(final String talker, JSONObject tpl) {
    String prompt = tpl.optString("prompt", getSummaryPrompt());
    String mode = tpl.optString("mode", "days");
    int days = tpl.optInt("days", 1);
    int count = tpl.optInt("count", 50);
    String name = tpl.optString("name", "");
    summarizeByTemplateValues(talker, prompt, mode, days, count, name);
}

void summarizeByTemplateValues(final String talker, final String prompt, final String mode, final int days, final int count, final String tplName) {
    try {
        if (!hasApiConfig()) {
            showConfigDialog();
            return;
        }

        toast("AI 正在使用模板[" + tplName + "]总结，请稍候...");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String historyText;
                    int reportCount;
                    if ("count".equals(mode)) {
                        int c = clampCount(count);
                        historyText = buildHistoryText(talker, c);
                        reportCount = c;
                    } else {
                        int d = days < 1 ? 1 : days;
                        historyText = buildHistoryTextByDaysRange(talker, d);
                        reportCount = TextUtils.isEmpty(historyText) ? 0 : historyText.split("\n").length;
                    }
                    if (TextUtils.isEmpty(historyText)) {
                        logx("[AI总结] 模板[" + tplName + "]无有效聊天记录 talker=" + talker);
                        toast("AI总结失败：没有读取到有效聊天记录");
                        return;
                    }
                    callSummaryApiInternal(talker, historyText, reportCount, prompt);
                } catch (Throwable e) {
                    logx("[AI总结] 模板总结失败: " + e.getMessage());
                    toast("AI总结失败：" + e.getMessage());
                }
            }
        }).start();
    } catch (Throwable e) {
        logx("[AI总结] 启动模板总结失败: " + e.getMessage());
    }
}

boolean formatMessageLine(Object msg, String talker, StringBuilder sb, SimpleDateFormat sdf) {
    try {
        if (!isReadableMsg(msg)) return false;
        String sender = safeString(callNoArg(msg, "getSendTalker"));
        String content = buildMessageContent(msg);
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(sender)) return false;
        String speaker = resolveDisplayName(sender, talker);
        if (TextUtils.isEmpty(speaker)) speaker = sender;
        long time = normalizeTime(safeLong(callNoArg(msg, "getCreateTime")));
        String timeText = time > 0 ? sdf.format(new Date(time)) : "未知时间";
        sb.append("[").append(timeText).append("] ");
        sb.append(speaker).append(": ");
        sb.append(content.replace("\n", " ").trim()).append("\n");
        return true;
    } catch (Throwable ignored) { return false; }
}

String buildHistoryText(String talker, int count) {
    try {
        count = clampCount(count);
        List list = queryRecentHistoryMsg(talker, count);
        if (list == null || list.size() == 0) return "";

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        int added = 0;
        int limit = Math.min(list.size(), count);

        logx("[AI总结] 最终整理聊天记录 " + list.size() + " 条，准备总结 " + limit + " 条");
        if (list.size() > 0) {
            Object first = list.get(0);
            Object last = list.get(list.size() - 1);
            long firstTime = normalizeTime(safeLong(callNoArg(first, "getCreateTime")));
            long lastTime = normalizeTime(safeLong(callNoArg(last, "getCreateTime")));
            logx("[AI总结] 整理后首条时间=" + formatLogTime(firstTime) + " 末条时间=" + formatLogTime(lastTime));
        }

        for (int i = 0; i < limit; i++) {
            Object msg = list.get(i);
            if (msg == null) continue;
            if (formatMessageLine(msg, talker, sb, sdf)) added++;
        }

        if (added == 0) return "";
        return sb.toString();
    } catch (Throwable e) {
        logx("[AI总结] 读取聊天记录失败: " + e.getMessage());
        return "";
    }
}

String buildHistoryTextByDaysRange(String talker, int days) {
    try {
        long now = currentTimeMillisSafe();
        long day = 24L * 60L * 60L * 1000L;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        long rangeStart = todayStart - (long) (days - 1) * day;

        int queryCount = 1000;
        List list = queryHistoryMsgSafe(talker, rangeStart, queryCount);
        list = filterReadableAndSortByTime(list);
        if (list == null || list.size() == 0) return "";

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        int added = 0;

        for (int i = 0; i < list.size(); i++) {
            Object msg = list.get(i);
            if (msg == null) continue;

            long time = normalizeTime(safeLong(callNoArg(msg, "getCreateTime")));
            if (time <= 0 || time > now) continue;
            if (time < rangeStart) continue;

            if (formatMessageLine(msg, talker, sb, sdf)) added++;
        }

        logx("[AI总结] 按天数提取 days=" + days + " rangeStart=" + formatLogTime(rangeStart) + " added=" + added);
        if (added == 0) return "";
        return sb.toString();
    } catch (Throwable e) {
        logx("[AI总结] 按天数读取聊天记录失败: " + e.getMessage());
        return "";
    }
}

List queryRecentHistoryMsg(String talker, int count) {
    ArrayList best = new ArrayList();
    try {
        if (TextUtils.isEmpty(talker)) return best;

        count = clampCount(count);
        long now = currentTimeMillisSafe();
        long day = 24L * 60L * 60L * 1000L;

        int queryCount = count + 80;
        if (queryCount < 120) queryCount = 120;
        if (queryCount > 300) queryCount = 300;

        long[] startTimes = new long[] {
                now - day,
                now - 3L * day,
                now - 7L * day,
                now - 15L * day,
                now - 30L * day,
                now - 90L * day,
                now - 180L * day,
                now - 365L * day,
                0L
        };

        for (int i = 0; i < startTimes.length; i++) {
            long startTime = startTimes[i];
            if (startTime < 0L) startTime = 0L;

            List found = queryHistoryMsgSafe(talker, startTime, queryCount);
            List sorted = filterReadableAndSortByTime(found);
            int size = sorted == null ? 0 : sorted.size();
            long firstTime = getListTime(sorted, true);
            long lastTime = getListTime(sorted, false);

            logx("[AI总结] 分段查询 startTime=" + formatLogTime(startTime) + " 返回可读=" + size + " 首=" + formatLogTime(firstTime) + " 末=" + formatLogTime(lastTime));

            if (sorted != null && sorted.size() > best.size()) {
                best.clear();
                best.addAll(sorted);
            }

            if (best.size() >= count) break;

            try {
                Thread.sleep(80L);
            } catch (Throwable ignored) {}
        }

        best = new ArrayList(filterReadableAndSortByTime(best));
        if (best.size() == 0) return best;
        if (best.size() <= count) return best;
        return new ArrayList(best.subList(best.size() - count, best.size()));
    } catch (Throwable e) {
        logx("[AI总结] 最近消息查询失败: " + e.getMessage());
        return best;
    }
}

List queryHistoryMsgSafe(String talker, long startTime, int queryCount) {
    try {
        if (TextUtils.isEmpty(talker)) return new ArrayList();

        long now = currentTimeMillisSafe();
        startTime = normalizeTime(startTime);
        if (startTime < 0L) startTime = 0L;
        if (startTime > now) startTime = now - 24L * 60L * 60L * 1000L;
        if (startTime < 0L) startTime = 0L;

        if (queryCount < 50) queryCount = 50;
        if (queryCount > 300) queryCount = 300;

        List found = queryHistoryMsg(talker, startTime, queryCount);
        return found == null ? new ArrayList() : found;
    } catch (Throwable e) {
        logx("[AI总结] queryHistoryMsg失败 startTime=" + formatLogTime(startTime) + " queryCount=" + queryCount + " 错误=" + e.getMessage());
        return new ArrayList();
    }
}

long getListTime(List list, boolean first) {
    if (list == null || list.size() == 0) return 0L;
    Object msg = list.get(first ? 0 : list.size() - 1);
    return normalizeTime(safeLong(callNoArg(msg, "getCreateTime")));
}

List filterReadableAndSortByTime(List source) {
    ArrayList out = new ArrayList();
    if (source == null) return out;
    for (int i = 0; i < source.size(); i++) {
        Object msg = source.get(i);
        if (msg == null) continue;
        if (isReadableMsg(msg)) out.add(msg);
    }
    Collections.sort(out, new Comparator() {
        public int compare(Object a, Object b) {
            long ta = normalizeTime(safeLong(callNoArg(a, "getCreateTime")));
            long tb = normalizeTime(safeLong(callNoArg(b, "getCreateTime")));
            if (ta == tb) return 0;
            return ta < tb ? -1 : 1;
        }
    });
    return out;
}

long currentTimeMillisSafe() {
    return normalizeTime(System.currentTimeMillis());
}

long normalizeTime(long time) {
    if (time <= 0) return time;
    if (time > 100000000000000000L) return time / 1000000L;
    if (time > 100000000000000L) return time / 1000L;
    if (time < 1000000000000L) return time * 1000L;
    return time;
}

String formatLogTime(long time) {
    if (time <= 0) return "0";
    try {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(normalizeTime(time)));
    } catch (Throwable e) {
        return String.valueOf(time);
    }
}

boolean isReadableMsg(Object msg) {
    try {
        if (safeBool(callNoArg(msg, "isText"))) return true;
        if (safeBool(callNoArg(msg, "isImage"))) return true;
        if (safeBool(callNoArg(msg, "isVoice"))) return true;
        if (safeBool(callNoArg(msg, "isVideo"))) return true;
        if (safeBool(callNoArg(msg, "isFile"))) return true;
        if (safeBool(callNoArg(msg, "isLink"))) return true;
        if (safeBool(callNoArg(msg, "isQuote"))) return true;
        if (safeBool(callNoArg(msg, "isPat"))) return true;
    } catch (Throwable ignored) {}
    return false;
}

String buildMessageContent(Object msg) {
    try {
        if (safeBool(callNoArg(msg, "isText"))) {
            return safeString(callNoArg(msg, "getContent"));
        }
        if (safeBool(callNoArg(msg, "isQuote"))) {
            Object quote = callNoArg(msg, "getQuoteMsg");
            String title = quote == null ? "" : safeString(callNoArg(quote, "getTitle"));
            String content = quote == null ? "" : safeString(callNoArg(quote, "getContent"));
            String cur = safeString(callNoArg(msg, "getContent"));
            return "[引用] " + firstNotEmpty(cur, title, content);
        }
        if (safeBool(callNoArg(msg, "isImage"))) return "[图片]";
        if (safeBool(callNoArg(msg, "isVoice"))) return "[语音]";
        if (safeBool(callNoArg(msg, "isVideo"))) return "[视频]";
        if (safeBool(callNoArg(msg, "isFile"))) {
            Object file = callNoArg(msg, "getFileMsg");
            String title = file == null ? "" : safeString(callNoArg(file, "getTitle"));
            return TextUtils.isEmpty(title) ? "[文件]" : "[文件] " + title;
        }
        if (safeBool(callNoArg(msg, "isLink"))) return "[链接] " + safeString(callNoArg(msg, "getContent"));
        if (safeBool(callNoArg(msg, "isPat"))) return "[拍一拍]";
    } catch (Throwable ignored) {}
    return "";
}

Object callNoArg(Object target, String methodName) {
    try {
        return invokeMethod(target, methodName);
    } catch (Throwable e) {
        return null;
    }
}

String resolveDisplayName(String wxid, String talker) {
    if (TextUtils.isEmpty(wxid)) return "";
    try {
        String name = getFriendDisplayName(wxid, talker);
        if (!TextUtils.isEmpty(name)) return name;
    } catch (Throwable ignored) {}
    try {
        String name2 = getFriendDisplayName(wxid);
        if (!TextUtils.isEmpty(name2)) return name2;
    } catch (Throwable ignored) {}
    try {
        String name3 = getFriendNickName(wxid);
        if (!TextUtils.isEmpty(name3)) return name3;
    } catch (Throwable ignored) {}
    return wxid;
}

String firstNotEmpty(String a, String b, String c) {
    if (!TextUtils.isEmpty(a)) return a;
    if (!TextUtils.isEmpty(b)) return b;
    if (!TextUtils.isEmpty(c)) return c;
    return "";
}

String getApiType() { return getString(CFG_API_TYPE, DEFAULT_API_TYPE); }
boolean isClaudeMode() { return API_TYPE_CLAUDE.equals(getApiType()); }
boolean getDisableReasoning() { return getBoolean(CFG_DISABLE_REASONING, DEFAULT_DISABLE_REASONING); }
boolean getPromptDisableReasoning() { return getBoolean(CFG_PROMPT_DISABLE_REASONING, DEFAULT_PROMPT_DISABLE_REASONING); }
int getMaxTokens() { return safeParseInt(getString(CFG_MAX_TOKENS, String.valueOf(DEFAULT_MAX_TOKENS)), DEFAULT_MAX_TOKENS); }
boolean getEnableMaxTokens() { return getBoolean(CFG_ENABLE_MAX_TOKENS, DEFAULT_ENABLE_MAX_TOKENS); }

List buildSingleUserMessages(String content) {
    java.util.ArrayList messages = new java.util.ArrayList();
    Map user = new HashMap();
    user.put("role", "user");
    user.put("content", content);
    messages.add(user);
    return messages;
}

void callSummaryApi(final String talker, String historyText, int count) {
    callSummaryApiInternal(talker, historyText, count, getSummaryPrompt());
}

void callSummaryApiInternal(final String talker, String historyText, int count, String promptText) {
    try {
        logx("[AI总结] 准备调用接口，聊天记录字符数=" + (historyText == null ? 0 : historyText.length()) + " count=" + count);

        final String reasoningPrompt = getPromptDisableReasoning()
            ? "\n\n重要：直接输出最终总结，不要输出任何思考过程、推理过程、分析步骤或草稿。立即开始输出总结内容。"
            : "";
        final String systemPrompt = (TextUtils.isEmpty(promptText) ? getSummaryPrompt() : promptText) + reasoningPrompt;

        String apiUrl = getApiUrl();
        String apiKey = getApiKey();
        String model = getModel();
        String userContent = (getPromptDisableReasoning() ? "请直接输出最终聊天总结，不要输出推理过程、分析过程或草稿。" : "") + "请总结以下最近 " + count + " 条聊天记录：\n\n" + historyText;

        Map params = new HashMap();
        Map headers = new HashMap();
        headers.put("Content-Type", "application/json");

        if (isClaudeMode()) {
            params.put("model", model);
            params.put("system", systemPrompt);
            params.put("messages", buildSingleUserMessages(userContent));
            params.put("max_tokens", Integer.valueOf(getMaxTokens()));
            if (!TextUtils.isEmpty(apiKey)) {
                headers.put("x-api-key", apiKey.trim());
            }
            headers.put("anthropic-version", "2023-06-01");
        } else {
            params.put("model", model);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            messages.put(system);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userContent);
            messages.put(user);

            params.put("messages", jsonArrayToList(messages));
            if (getEnableMaxTokens()) params.put("max_tokens", Integer.valueOf(getMaxTokens()));

            if (getDisableReasoning()) { addOAIThinkingDisabledParams(params); }

            if (!TextUtils.isEmpty(apiKey)) {
                headers.put("Authorization", normalizeAuthHeader(apiKey));
            }
        }

        final String maskedKey = maskApiKey(apiKey);
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        logx("[AI总结] 完整请求参数 时间=" + timestamp);
        logx("[AI总结] 请求地址=" + apiUrl);
        logx("[AI总结] 请求模型=" + model);
        logx("[AI总结] API Key=" + maskedKey);
        logx("[AI总结] 请求头: " + maskHeaders(headers).toString());

        post(apiUrl, params, headers, 90L, body -> {
            try {
                if (TextUtils.isEmpty(body)) {
                    logx("[AI总结] 接口返回为空 时间=" + timestamp + " 地址=" + apiUrl + " key=" + maskedKey + " model=" + model);
                    toast("AI总结失败：接口返回为空");
                    return;
                }

                logx("[AI总结] API响应 时间=" + timestamp + " 地址=" + apiUrl + " key=" + maskedKey + " model=" + model + " 响应长度=" + body.length());

                String summary = parseSummaryResponse(body);
                if (TextUtils.isEmpty(summary)) {
                    logx("[AI总结] 无法解析返回内容");
                    toast("AI总结失败：接口没有返回可用内容");
                    return;
                }

                String endReason = parseEndReason(body);
                logx("[AI总结] 结束原因=" + endReason);

                logx("[AI总结] 成功提取内容，长度=" + summary.length() + "字符");
                String finalText = "【AI聊天总结】\n" + summary.trim();
                sendSummaryByConfig(talker, finalText);

                if ("达到最大 Token 限制，结果可能不完整".equals(endReason)) {
                    toast("AI总结可能不完整：" + endReason);
                } else {
                    toast("AI总结完成：" + endReason);
                }
            } catch (Throwable e) {
                logx("[AI总结] 解析响应异常 时间=" + timestamp + " 地址=" + apiUrl + " key=" + maskedKey + " 错误=" + e.getMessage());
                toast("AI总结失败：解析接口响应异常");
            }
        });
    } catch (Throwable e) {
        logx("[AI总结] 调用接口异常: " + e.getMessage());
        toast("AI总结失败：调用接口异常");
    }
}

List jsonArrayToList(JSONArray arr) {
    java.util.ArrayList out = new java.util.ArrayList();
    if (arr == null) return out;
    for (int i = 0; i < arr.length(); i++) {
        Object item = arr.opt(i);
        if (item instanceof JSONObject) {
            out.add(jsonObjectToMap((JSONObject) item));
        } else {
            out.add(item);
        }
    }
    return out;
}

Map jsonObjectToMap(JSONObject obj) {
    Map out = new HashMap();
    if (obj == null) return out;
    java.util.Iterator it = obj.keys();
    while (it.hasNext()) {
        String key = String.valueOf(it.next());
        Object value = obj.opt(key);
        if (value instanceof JSONObject) value = jsonObjectToMap((JSONObject) value);
        if (value instanceof JSONArray) value = jsonArrayToList((JSONArray) value);
        out.put(key, value);
    }
    return out;
}

String parseSummaryResponse(String body) {
    if (TextUtils.isEmpty(body)) return "";
    try {
        JSONObject json = new JSONObject(body);

        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject first = choices.optJSONObject(0);
            if (first != null) {
                JSONObject msg = first.optJSONObject("message");
                if (msg != null) {
                    // 优先使用 content 字段
                    String content = msg.optString("content", "");
                    if (!TextUtils.isEmpty(content)) return content;

                }
                String text = first.optString("text", "");
                if (!TextUtils.isEmpty(text)) return text;
            }
        }

        JSONArray content = json.optJSONArray("content");
        if (content != null && content.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block == null) continue;
                if ("text".equals(block.optString("type"))) {
                    String text = block.optString("text", "");
                    if (!TextUtils.isEmpty(text)) sb.append(text);
                }
            }
            if (sb.length() > 0) return sb.toString();
        }

        String direct = json.optString("text", "");
        if (!TextUtils.isEmpty(direct)) return direct;

    } catch (Throwable e) {
        logx("[AI总结] JSON解析异常: " + e.getMessage());
    }
    return "";
}

String parseEndReason(String body) {
    if (TextUtils.isEmpty(body)) return "";
    try {
        JSONObject json = new JSONObject(body);
        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject first = choices.optJSONObject(0);
            if (first != null) {
                String reason = first.optString("finish_reason", "");
                if (!TextUtils.isEmpty(reason)) return describeEndReason(reason);
            }
        }
        String stopReason = json.optString("stop_reason", "");
        if (!TextUtils.isEmpty(stopReason)) return describeEndReason(stopReason);
    } catch (Throwable e) {
        logx("[AI总结] 解析结束原因失败: " + e.getMessage());
    }
    return "";
}

String describeEndReason(String reason) {
    if (TextUtils.isEmpty(reason)) return "未知";
    if ("stop".equals(reason) || "end_turn".equals(reason)) return "正常结束";
    if ("length".equals(reason) || "max_tokens".equals(reason)) return "达到最大 Token 限制，结果可能不完整";
    if ("content_filter".equals(reason)) return "内容被过滤";
    if ("tool_calls".equals(reason) || "tool_use".equals(reason)) return "工具调用";
    return reason;
}

String normalizeAuthHeader(String apiKey) {
    if (TextUtils.isEmpty(apiKey)) return "";
    String v = apiKey.trim();
    String low = v.toLowerCase(Locale.getDefault());
    if (low.startsWith("bearer ") || low.startsWith("basic ")) return v;
    return "Bearer " + v;
}

String maskApiKey(String apiKey) {
    if (TextUtils.isEmpty(apiKey)) return "(空)";
    String v = apiKey.trim();
    if (v.length() <= 8) return "****";
    return v.substring(0, 4) + "****" + v.substring(v.length() - 4);
}

Map maskHeaders(Map headers) {
    if (headers == null) return null;
    Map out = new HashMap();
    java.util.Iterator it = headers.keySet().iterator();
    while (it.hasNext()) {
        String key = String.valueOf(it.next());
        Object val = headers.get(key);
        String lowerKey = key.toLowerCase(Locale.getDefault());
        if (val != null && ("authorization".equals(lowerKey) || "x-api-key".equals(lowerKey))) {
            out.put(key, maskApiKey(String.valueOf(val)));
        } else {
            out.put(key, val);
        }
    }
    return out;
}

boolean isLogEnabled() {
    return getBoolean(CFG_LOG_ENABLE, DEFAULT_LOG_ENABLE);
}

void logx(Object msg) {
    if (mLogEnabled) {
        log(msg);
    }
}

// ============ 发送目标配置 ============

void sendSummaryByConfig(String talker, String text) {
    try {
        boolean sent = false;
        StringBuilder targets = new StringBuilder();
        
        if (getSendToCurrentChat()) {
            sendText(talker, text);
            sent = true;
            targets.append("当前聊天");
        }
        
        if (getSendToFileHelper()) {
            sendText("filehelper", text);
            sent = true;
            if (targets.length() > 0) targets.append("、");
            targets.append("文件传输助手");
        }
        
        if (getSendToCustom()) {
            String customWxid = getCustomWxid();
            if (!TextUtils.isEmpty(customWxid)) {
                sendText(customWxid, text);
                sent = true;
                if (targets.length() > 0) targets.append("、");
                targets.append("自定义用户(" + customWxid + ")");
            }
        }
        
        if (sent) {
            toast("AI总结已发送至：" + targets.toString());
        } else {
            toast("AI总结完成，但未配置发送目标");
        }
    } catch (Throwable e) {
        logx("[AI总结] 发送失败: " + e.getMessage());
        toast("AI总结发送失败：" + e.getMessage());
    }
}

boolean getSendToCurrentChat() {
    return getBoolean(CFG_SEND_TO_CURRENT, false);
}

boolean getSendToFileHelper() {
    return getBoolean(CFG_SEND_TO_FILEHELPER, true);
}

boolean getSendToCustom() {
    return getBoolean(CFG_SEND_TO_CUSTOM, false);
}

String getCustomWxid() {
    return getString(CFG_CUSTOM_WXID, "");
}


// ============ 模型列表与接口测试 ============

void fetchAvailableModels(final String apiUrlInput, final String apiKeyInput, final EditText modelInput, final boolean isClaude) {
    try {
        final String apiUrl = normalizeApiUrl(apiUrlInput == null ? "" : apiUrlInput.trim(), isClaude);
        final String apiKey = apiKeyInput == null ? "" : apiKeyInput.trim();
        if (TextUtils.isEmpty(apiUrl)) { toast("请先填写 API 地址"); return; }
        if (TextUtils.isEmpty(apiKey)) { toast("请先填写 API Key"); return; }
        toast("正在获取可用模型列表...\n规范化地址: " + apiUrl);
        new Thread(new Runnable() { public void run() {
            try {
                final String modelListUrl = deriveModelListUrl(apiUrl);
                String body = requestModelList(apiUrl, apiKey, isClaude);
                final ArrayList models = parseModelListResponse(body);
                if (models == null || models.size() == 0) {
                    logx("[AI总结] 模型列表为空 地址=" + modelListUrl + " 响应前200字=" + (body == null ? "" : body.substring(0, Math.min(200, body.length()))));
                    toast("未获取到可用模型，请检查接口是否支持模型列表"); return;
                }
                showModelSelectDialog(models, modelInput);
            } catch (Throwable e) { logx("[AI总结] 获取模型列表失败: " + e.getMessage()); toast("获取模型列表失败：" + trimForToast(e.getMessage())); }
        }}).start();
    } catch (Throwable e) { logx("[AI总结] 启动模型列表获取失败: " + e.getMessage()); toast("获取模型列表失败：" + trimForToast(e.getMessage())); }
}

void showModelSelectDialog(final ArrayList models, final EditText modelInput) {
    final Activity ctx = getTopActivity();
    if (ctx == null) { toast("无法获取当前界面"); return; }
    ctx.runOnUiThread(new Runnable() { public void run() {
        try {
            final String[] items = new String[models.size()];
            for (int i = 0; i < models.size(); i++) items[i] = String.valueOf(models.get(i));
            new AlertDialog.Builder(ctx).setTitle("选择可用模型").setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which >= 0 && which < items.length) { modelInput.setText(items[which]); modelInput.setSelection(modelInput.getText().length()); toast("已填入模型：" + items[which]); }
                }
            }).setNegativeButton("取消", null).show();
        } catch (Throwable e) { toast("显示模型列表失败：" + trimForToast(e.getMessage())); }
    }});
}

String requestModelList(String apiUrl, String apiKey, boolean isClaude) throws Exception { return httpGetText(deriveModelListUrl(apiUrl), buildHeadersForApi(apiUrl, apiKey, isClaude), 20000); }

String deriveModelListUrl(String apiUrl) {
    if (TextUtils.isEmpty(apiUrl)) return "";
    String url = apiUrl.trim(); String lower = url.toLowerCase(Locale.getDefault());
    int q = url.indexOf("?"); if (q >= 0) { url = url.substring(0, q); lower = url.toLowerCase(Locale.getDefault()); }
    while (url.endsWith("/")) { url = url.substring(0, url.length() - 1); lower = url.toLowerCase(Locale.getDefault()); }
    if (lower.endsWith("/chat/completions")) return url.substring(0, url.length() - "/chat/completions".length()) + "/models";
    if (lower.endsWith("/responses")) return url.substring(0, url.length() - "/responses".length()) + "/models";
    if (lower.endsWith("/messages")) return url.substring(0, url.length() - "/messages".length()) + "/models";
    if (lower.endsWith("/completions")) return url.substring(0, url.length() - "/completions".length()) + "/models";
    if (lower.endsWith("/models")) return url;
    int idx = lower.indexOf("/v1/"); if (idx >= 0) return url.substring(0, idx + 3) + "/models";
    if (lower.endsWith("/v1")) return url + "/models";
    return url + "/v1/models";
}

ArrayList parseModelListResponse(String body) {
    ArrayList out = new ArrayList(); if (TextUtils.isEmpty(body)) return out;
    try {
        String trim = body.trim();
        if (trim.startsWith("[")) appendModelIds(out, new JSONArray(trim));
        else { JSONObject json = new JSONObject(trim); JSONArray data = json.optJSONArray("data"); if (data != null) appendModelIds(out, data); JSONArray models = json.optJSONArray("models"); if (models != null) appendModelIds(out, models); JSONArray items = json.optJSONArray("items"); if (items != null) appendModelIds(out, items); }
    } catch (Throwable e) { logx("[AI总结] 解析模型列表失败: " + e.getMessage()); }
    Collections.sort(out, new Comparator() { public int compare(Object a, Object b) { return String.valueOf(a).compareToIgnoreCase(String.valueOf(b)); } }); return out;
}

void appendModelIds(ArrayList out, JSONArray arr) {
    if (out == null || arr == null) return;
    for (int i = 0; i < arr.length(); i++) { String id = ""; Object item = arr.opt(i); if (item instanceof JSONObject) { JSONObject o = (JSONObject) item; id = firstNotEmpty(o.optString("id", ""), o.optString("name", ""), o.optString("model", "")); } else { id = safeString(item); } if (!TextUtils.isEmpty(id) && !out.contains(id)) out.add(id); }
}

void testApiAvailability(final String apiUrlInput, final String apiKeyInput, final String modelInput, final boolean isClaude) {
    try {
        final String apiUrl = normalizeApiUrl(apiUrlInput == null ? "" : apiUrlInput.trim(), isClaude); final String apiKey = apiKeyInput == null ? "" : apiKeyInput.trim(); final String model = modelInput == null ? "" : modelInput.trim();
        if (TextUtils.isEmpty(apiUrl)) { toast("请先填写 API 地址"); return; }
        if (TextUtils.isEmpty(apiKey)) { toast("请先填写 API Key"); return; }
        if (TextUtils.isEmpty(model)) { toast("请先填写或选择模型名称"); return; }
        toast("正在以最小消耗测试 API...\n规范化地址: " + apiUrl);
        new Thread(new Runnable() { public void run() {
            try {
                // 输出完整请求内容到日志
                logx("[AI总结] API测试完整请求 地址=" + apiUrl + " model=" + model);

                String body = requestApiTest(apiUrl, apiKey, model, isClaude);

                // 输出完整 JSON 响应到日志
                logx("[AI总结] API测试完整响应 地址=" + apiUrl + " model=" + model);
                logx("[AI总结] JSON响应: " + body);

                String content = parseSummaryResponse(body);
                if (!TextUtils.isEmpty(content)) {
                    toast("API测试成功，模型可正常响应");
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    if (json.has("error")) {
                        toast("API测试失败：" + trimForToast(json.opt("error")));
                        return;
                    }
                } catch (Throwable ignored) {}
                logx("[AI总结] API测试响应无法解析 - 已输出完整JSON到上方日志");
                toast("API有响应，但未解析到有效内容");
            }
            catch (Throwable e) { logx("[AI总结] API测试失败: " + e.getMessage()); toast("API测试失败：" + trimForToast(e.getMessage())); }
        }}).start();
    } catch (Throwable e) { logx("[AI总结] 启动API测试失败: " + e.getMessage()); toast("API测试失败：" + trimForToast(e.getMessage())); }
}

String requestApiTest(String apiUrl, String apiKey, String model, boolean isClaude) throws Exception {
    Map params = new HashMap();
    if (isClaude) {
        params.put("model", model);
        params.put("messages", buildSingleUserMessages("ping"));
        params.put("max_tokens", Integer.valueOf(1));
    } else {
        params.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", "ping");
        messages.put(user);
        params.put("messages", jsonArrayToList(messages));
        params.put("max_tokens", Integer.valueOf(1));

        if (getDisableReasoning()) { addOAIThinkingDisabledParams(params); }
    }

    String requestBody = buildRequestBodyJson(params);
    logx("[AI总结] API测试请求体JSON: " + requestBody);

    return httpPostText(apiUrl, buildHeadersForApi(apiUrl, apiKey, isClaude), requestBody, 30000);
}

void addOAIThinkingDisabledParams(Map params) {
    Map thinkingConfig = new HashMap();
    thinkingConfig.put("type", "disabled");
    params.put("thinking", thinkingConfig);
    params.put("reasoning", Boolean.FALSE);
    params.put("enable_reasoning", Boolean.FALSE);
    params.put("stream_reasoning", Boolean.FALSE);
}

Map buildHeadersForApi(String apiUrl, String apiKey) {
    return buildHeadersForApi(apiUrl, apiKey, isClaudeMode());
}

Map buildHeadersForApi(String apiUrl, String apiKey, boolean isClaude) {
    Map headers = new HashMap(); headers.put("Content-Type", "application/json");
    if (isClaude) { if (!TextUtils.isEmpty(apiKey)) headers.put("x-api-key", apiKey.trim()); headers.put("anthropic-version", "2023-06-01"); }
    else { if (!TextUtils.isEmpty(apiKey)) headers.put("Authorization", normalizeAuthHeader(apiKey)); }
    return headers;
}

String httpGetText(String urlText, Map headers, int timeoutMs) throws Exception {
    HttpURLConnection conn = null; try { URL url = new URL(urlText); conn = (HttpURLConnection) url.openConnection(); conn.setRequestMethod("GET"); conn.setConnectTimeout(timeoutMs); conn.setReadTimeout(timeoutMs); conn.setUseCaches(false); if (headers != null) { java.util.Iterator it = headers.keySet().iterator(); while (it.hasNext()) { Object k = it.next(); Object v = headers.get(k); if (k != null && v != null) conn.setRequestProperty(String.valueOf(k), String.valueOf(v)); } } int code = conn.getResponseCode(); String body = readStreamText(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()); if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + " " + trimForToast(body)); return body; } finally { if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {} }
}

String httpPostText(String urlText, Map headers, String jsonBody, int timeoutMs) throws Exception {
    HttpURLConnection conn = null; try { URL url = new URL(urlText); conn = (HttpURLConnection) url.openConnection(); conn.setRequestMethod("POST"); conn.setConnectTimeout(timeoutMs); conn.setReadTimeout(timeoutMs); conn.setUseCaches(false); conn.setDoOutput(true); if (headers != null) { java.util.Iterator it = headers.keySet().iterator(); while (it.hasNext()) { Object k = it.next(); Object v = headers.get(k); if (k != null && v != null) conn.setRequestProperty(String.valueOf(k), String.valueOf(v)); } } writeUtf8(conn.getOutputStream(), jsonBody == null ? "{}" : jsonBody); int code = conn.getResponseCode(); String body = readStreamText(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()); if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + " " + trimForToast(body)); return body; } finally { if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {} }
}

String readStreamText(InputStream in) throws Exception { if (in == null) return ""; BufferedReader br = null; try { br = new BufferedReader(new InputStreamReader(in, "UTF-8")); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line).append("\n"); return sb.toString(); } finally { try { if (br != null) br.close(); } catch (Throwable ignored) {} } }
void writeUtf8(OutputStream out, String text) throws Exception { if (out == null) return; try { byte[] data = (text == null ? "" : text).getBytes("UTF-8"); out.write(data); out.flush(); } finally { try { out.close(); } catch (Throwable ignored) {} } }
String buildRequestBodyJson(Map params) { try { return mapToJsonObject(params).toString(); } catch (Throwable e) { return "{}"; } }
JSONObject mapToJsonObject(Map map) throws Exception { JSONObject obj = new JSONObject(); if (map == null) return obj; java.util.Iterator it = map.keySet().iterator(); while (it.hasNext()) { Object key = it.next(); Object value = map.get(key); obj.put(String.valueOf(key), valueToJsonValue(value)); } return obj; }
Object valueToJsonValue(Object value) throws Exception { if (value == null) return JSONObject.NULL; if (value instanceof Map) return mapToJsonObject((Map) value); if (value instanceof List) { JSONArray arr = new JSONArray(); List list = (List) value; for (int i = 0; i < list.size(); i++) arr.put(valueToJsonValue(list.get(i))); return arr; } if (value instanceof JSONArray) return value; if (value instanceof JSONObject) return value; return value; }
String trimForToast(Object value) { String text = safeString(value); if (TextUtils.isEmpty(text)) return "未知错误"; text = text.replace("\n", " ").replace("\r", " ").trim(); if (text.length() > 120) return text.substring(0, 120); return text; }

// ============ 模板存储 ============

JSONArray loadTemplatesArray() {
    String raw = getString(CFG_TEMPLATES, "");
    if (TextUtils.isEmpty(raw)) return new JSONArray();
    try {
        return new JSONArray(raw);
    } catch (Throwable e) {
        logx("[AI总结] 模板数据解析失败: " + e.getMessage());
        return new JSONArray();
    }
}

void saveTemplatesArray(JSONArray arr) {
    putString(CFG_TEMPLATES, arr == null ? "[]" : arr.toString());
}

JSONObject findTemplate(String name) {
    if (TextUtils.isEmpty(name)) return null;
    JSONArray arr = loadTemplatesArray();
    for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        if (name.equals(o.optString("name"))) return o;
    }
    return null;
}

String getDefaultTemplateName() {
    return getString(CFG_DEFAULT_TEMPLATE, "");
}

void setDefaultTemplateName(String name) {
    putString(CFG_DEFAULT_TEMPLATE, name == null ? "" : name);
}

JSONObject getDefaultTemplateObject() {
    JSONArray arr = loadTemplatesArray();
    if (arr.length() == 0) return null;
    String defName = getDefaultTemplateName();
    if (!TextUtils.isEmpty(defName)) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && defName.equals(o.optString("name"))) return o;
        }
    }
    return arr.optJSONObject(0);
}

String nextTemplateName() {
    JSONArray arr = loadTemplatesArray();
    for (int n = 1; n < 100000; n++) {
        String candidate = String.valueOf(n);
        boolean used = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && candidate.equals(o.optString("name"))) {
                used = true;
                break;
            }
        }
        if (!used) return candidate;
    }
    return String.valueOf(System.currentTimeMillis());
}

void upsertTemplate(String oldName, String name, String prompt, String mode, int days, int count) {
    try {
        JSONArray arr = loadTemplatesArray();
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String n = o.optString("name");
            if (n.equals(oldName) || n.equals(name)) continue;
            out.put(o);
        }
        JSONObject t = new JSONObject();
        t.put("name", name);
        t.put("prompt", prompt);
        t.put("mode", mode);
        t.put("days", days);
        t.put("count", count);
        out.put(t);
        saveTemplatesArray(out);

        if (!TextUtils.isEmpty(oldName) && oldName.equals(getDefaultTemplateName())) {
            setDefaultTemplateName(name);
        }
    } catch (Throwable e) {
        logx("[AI总结] 保存模板失败: " + e.getMessage());
        toast("保存模板失败：" + e.getMessage());
    }
}

void deleteTemplate(String name) {
    JSONArray arr = loadTemplatesArray();
    JSONArray out = new JSONArray();
    for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        if (name.equals(o.optString("name"))) continue;
        out.put(o);
    }
    saveTemplatesArray(out);
    if (name.equals(getDefaultTemplateName())) setDefaultTemplateName("");
}

// ============ 配置界面 ============

void showConfigDialog() {
    final Activity ctx = getTopActivity();
    if (ctx == null) { toast("无法获取当前界面"); return; }
    final AlertDialog[] holder = new AlertDialog[1];
    ctx.runOnUiThread(new Runnable() { public void run() {
        LinearLayout root = new LinearLayout(ctx); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 18)); root.setBackgroundColor(Color.rgb(246, 248, 252));
        root.addView(materialTitle(ctx, "AI多模板聊天总结")); TextView subTitle = materialBody(ctx, "配置接口、选择模型、测试可用性，并管理你的总结模板。"); subTitle.setPadding(0, dp(ctx, 4), 0, dp(ctx, 12)); root.addView(subTitle);
        final EditText apiUrlInput = createInput(ctx, "API 地址", getApiUrl(), false, true); final EditText apiKeyInput = createInput(ctx, "API Key", getApiKey(), true, false); final EditText modelInput = createInput(ctx, "模型名称", getModel(), false, false);
        final boolean[] selectedIsClaude = new boolean[]{isClaudeMode()};
        RadioGroup apiTypeGroup = new RadioGroup(ctx);
        apiTypeGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton openaiRadio = new RadioButton(ctx); openaiRadio.setText("OpenAI 兼容"); openaiRadio.setId(2001);
        RadioButton claudeRadio = new RadioButton(ctx); claudeRadio.setText("Claude 原生"); claudeRadio.setId(2002);
        apiTypeGroup.addView(openaiRadio); apiTypeGroup.addView(claudeRadio);
        if (selectedIsClaude[0]) { claudeRadio.setChecked(true); } else { openaiRadio.setChecked(true); }
        apiTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) { selectedIsClaude[0] = (checkedId == 2002); }
        });
        final EditText maxTokensInput = createInput(ctx, "最大输出 Token（默认4000）", String.valueOf(getMaxTokens()), false, false); maxTokensInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        final Switch reasonParamSwitch = new Switch(ctx); reasonParamSwitch.setText("发送禁用思考参数"); reasonParamSwitch.setTextColor(Color.rgb(31, 31, 31)); reasonParamSwitch.setTextSize(15); reasonParamSwitch.setChecked(getDisableReasoning());
        final Switch reasonPromptSwitch = new Switch(ctx); reasonPromptSwitch.setText("在提示词中要求直接输出正文"); reasonPromptSwitch.setTextColor(Color.rgb(31, 31, 31)); reasonPromptSwitch.setTextSize(15); reasonPromptSwitch.setChecked(getPromptDisableReasoning());
        final Switch maxTokensSwitch = new Switch(ctx); maxTokensSwitch.setText("发送最大输出 Token 参数"); maxTokensSwitch.setTextColor(Color.rgb(31, 31, 31)); maxTokensSwitch.setTextSize(15); maxTokensSwitch.setChecked(getEnableMaxTokens());
        LinearLayout apiCard = createMaterialCard(ctx); apiCard.addView(materialSectionTitle(ctx, "接口设置")); apiCard.addView(materialBody(ctx, "API Key 会保存在插件 config.prop 中，请自行确认设备环境安全。")); apiCard.addView(label(ctx, "API 地址")); apiCard.addView(apiUrlInput); apiCard.addView(label(ctx, "API Key")); apiCard.addView(apiKeyInput); apiCard.addView(label(ctx, "模型名称")); apiCard.addView(modelInput); apiCard.addView(label(ctx, "API 类型")); apiCard.addView(apiTypeGroup); apiCard.addView(label(ctx, "模型思考控制")); apiCard.addView(reasonParamSwitch); apiCard.addView(reasonPromptSwitch); TextView reasoningTip = materialTip(ctx, "不同模型和中转接口对禁用思考参数的支持不同：发送该参数可能导致 HTTP 400，也可能被接口忽略；可单独关闭参数开关，保留提示词约束。"); reasoningTip.setPadding(0, dp(ctx, 4), 0, 0); apiCard.addView(reasoningTip); apiCard.addView(label(ctx, "最大输出 Token")); apiCard.addView(maxTokensSwitch); apiCard.addView(maxTokensInput); TextView tokenTip = materialTip(ctx, "打开思考开关会让模型输出使用较多token，请酌情调整\n参考额度：单条微信信息最大能发送4000字符\nClaude 原生接口要求发送 max_tokens，关闭开关时仍会使用此值。"); tokenTip.setPadding(0, dp(ctx, 4), 0, 0); apiCard.addView(tokenTip);
        Button fetchModelBtn = createFilledButton(ctx, "获取可用模型并选择"); fetchModelBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fetchAvailableModels(apiUrlInput.getText().toString(), apiKeyInput.getText().toString(), modelInput, selectedIsClaude[0]); } }); apiCard.addView(fetchModelBtn);
        Button testApiBtn = createTonalButton(ctx, "测试API可用性"); testApiBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testApiAvailability(apiUrlInput.getText().toString(), apiKeyInput.getText().toString(), modelInput.getText().toString(), selectedIsClaude[0]); } }); apiCard.addView(testApiBtn); TextView apiToolTip = materialTip(ctx, "模型列表会根据 API 地址自动推导 /models 接口；测试API仅发送 ping 且 max_tokens=1，尽量降低消耗。"); apiToolTip.setPadding(0, dp(ctx, 8), 0, 0); apiCard.addView(apiToolTip); root.addView(apiCard);
        final Switch logSwitch = new Switch(ctx); logSwitch.setText("开启运行日志"); logSwitch.setTextColor(Color.rgb(31, 31, 31)); logSwitch.setTextSize(15); logSwitch.setChecked(mLogEnabled); logSwitch.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
        LinearLayout logCard = createMaterialCard(ctx); logCard.addView(materialSectionTitle(ctx, "日志设置")); logCard.addView(logSwitch); logCard.addView(materialTip(ctx, "关闭后不写入日志，可避免查询调试日志过多。排查问题时再开启。")); root.addView(logCard);

            LinearLayout targetCard = createMaterialCard(ctx);
            targetCard.addView(materialSectionTitle(ctx, "发送目标设置"));
            targetCard.addView(materialBody(ctx, "总结完成后，将结果发送到以下选中的目标（可多选）。"));
            
            LinearLayout currentChatRow = new LinearLayout(ctx);
            currentChatRow.setOrientation(LinearLayout.HORIZONTAL);
            currentChatRow.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
            
            final android.widget.CheckBox currentChatCheck = new android.widget.CheckBox(ctx);
            currentChatCheck.setChecked(getSendToCurrentChat());
            LinearLayout.LayoutParams currentChatCbLp = new LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24));
            currentChatCheck.setLayoutParams(currentChatCbLp);
            currentChatRow.addView(currentChatCheck);
            
            TextView currentChatText = new TextView(ctx);
            currentChatText.setText("发送到接收命令的聊天");
            currentChatText.setTextColor(Color.rgb(31, 31, 31));
            currentChatText.setTextSize(15);
            currentChatText.setPadding(dp(ctx, 8), 0, 0, 0);
            currentChatRow.addView(currentChatText);
            
            targetCard.addView(currentChatRow);
            
            LinearLayout filehelperRow = new LinearLayout(ctx);
            filehelperRow.setOrientation(LinearLayout.HORIZONTAL);
            filehelperRow.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
            
            final android.widget.CheckBox filehelperCheck = new android.widget.CheckBox(ctx);
            filehelperCheck.setChecked(getSendToFileHelper());
            LinearLayout.LayoutParams filehelperCbLp = new LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24));
            filehelperCheck.setLayoutParams(filehelperCbLp);
            filehelperRow.addView(filehelperCheck);
            
            TextView filehelperText = new TextView(ctx);
            filehelperText.setText("发送到文件传输助手");
            filehelperText.setTextColor(Color.rgb(31, 31, 31));
            filehelperText.setTextSize(15);
            filehelperText.setPadding(dp(ctx, 8), 0, 0, 0);
            filehelperRow.addView(filehelperText);
            
            targetCard.addView(filehelperRow);
            
            LinearLayout customRow = new LinearLayout(ctx);
            customRow.setOrientation(LinearLayout.HORIZONTAL);
            customRow.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
            
            final android.widget.CheckBox customCheck = new android.widget.CheckBox(ctx);
            customCheck.setChecked(getSendToCustom());
            LinearLayout.LayoutParams customCbLp = new LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24));
            customCheck.setLayoutParams(customCbLp);
            customRow.addView(customCheck);
            
            TextView customText = new TextView(ctx);
            customText.setText("发送到自定义用户");
            customText.setTextColor(Color.rgb(31, 31, 31));
            customText.setTextSize(15);
            customText.setPadding(dp(ctx, 8), 0, 0, 0);
            customRow.addView(customText);
            
            targetCard.addView(customRow);
            
            targetCard.addView(label(ctx, "自定义用户 wxid"));
            final EditText customWxidInput = createInput(ctx, "请输入自定义用户 wxid，例如 wxid_xxx", getCustomWxid(), false, false);
            targetCard.addView(customWxidInput);
            
            TextView targetTip = materialTip(ctx, "至少选择一个发送目标。自定义用户 wxid 可以通过聊天界面长按用户头像查看。");
            targetTip.setPadding(0, dp(ctx, 8), 0, 0);
            targetCard.addView(targetTip);
            root.addView(targetCard);

        LinearLayout tplCard = createMaterialCard(ctx); tplCard.addView(materialSectionTitle(ctx, "提示词模板")); tplCard.addView(materialBody(ctx, "发送 /ai 总结 使用默认模板（★）；发送 /ai 总结 模板名 使用指定模板。")); Button newTplBtn = createFilledButton(ctx, "+ 新建模板"); newTplBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (holder[0] != null) holder[0].dismiss(); showTemplateEditDialog("", true); } }); tplCard.addView(newTplBtn);
        String defaultName = getDefaultTemplateName(); JSONArray tpls = loadTemplatesArray(); if (tpls.length() == 0) { TextView empty = materialTip(ctx, "暂无模板，点击上方“新建模板”。"); empty.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4)); tplCard.addView(empty); } else { for (int i = 0; i < tpls.length(); i++) { JSONObject o = tpls.optJSONObject(i); if (o == null) continue; String tName = o.optString("name"); String mode = o.optString("mode", "days"); String modeText = "count".equals(mode) ? ("最近" + o.optInt("count", 50) + "条") : ("最近" + o.optInt("days", 1) + "天"); boolean isDef = !TextUtils.isEmpty(defaultName) && tName.equals(defaultName); addTemplateItemView(ctx, tplCard, holder, tName, modeText, isDef); } } root.addView(tplCard);
        ScrollView scroll = new ScrollView(ctx); scroll.setBackgroundColor(Color.rgb(246, 248, 252)); scroll.addView(root);
        AlertDialog dlg = new AlertDialog.Builder(ctx).setTitle("设置").setView(scroll).setPositiveButton("保存", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { String apiUrl = apiUrlInput.getText().toString().trim(); String apiKey = apiKeyInput.getText().toString().trim(); String model = modelInput.getText().toString().trim(); int maxTokens = safeParseInt(maxTokensInput.getText().toString().trim(), DEFAULT_MAX_TOKENS); if (maxTokens < 1) maxTokens = DEFAULT_MAX_TOKENS; putString(CFG_API_URL, normalizeApiUrl(TextUtils.isEmpty(apiUrl) ? DEFAULT_API_URL : apiUrl, selectedIsClaude[0])); putString(CFG_API_KEY, apiKey); putString(CFG_MODEL, TextUtils.isEmpty(model) ? DEFAULT_MODEL : model); putString(CFG_API_TYPE, selectedIsClaude[0] ? API_TYPE_CLAUDE : API_TYPE_OPENAI); putBoolean(CFG_LOG_ENABLE, logSwitch.isChecked()); mLogEnabled = logSwitch.isChecked(); putBoolean(CFG_SEND_TO_CURRENT, currentChatCheck.isChecked()); putBoolean(CFG_SEND_TO_FILEHELPER, filehelperCheck.isChecked()); putBoolean(CFG_SEND_TO_CUSTOM, customCheck.isChecked()); putString(CFG_CUSTOM_WXID, customWxidInput.getText().toString().trim()); putString(CFG_MAX_TOKENS, String.valueOf(maxTokens)); putBoolean(CFG_ENABLE_MAX_TOKENS, maxTokensSwitch.isChecked()); putBoolean(CFG_DISABLE_REASONING, reasonParamSwitch.isChecked()); putBoolean(CFG_PROMPT_DISABLE_REASONING, reasonPromptSwitch.isChecked()); toast("AI聊天总结配置已保存"); } }).setNegativeButton("关闭", null).create(); holder[0] = dlg; dlg.show();
    }});
}

void addTemplateItemView(final Activity ctx, LinearLayout root, final AlertDialog[] holder, final String tName, String modeText, boolean isDef) {
    TextView item = new TextView(ctx); item.setText((isDef ? "★ " : "○ ") + tName + "   (" + modeText + ")"); item.setTextSize(15); item.setTextColor(Color.rgb(31, 31, 31)); item.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14)); item.setBackground(materialRoundBg(ctx, Color.rgb(249, 250, 255), Color.rgb(226, 229, 240), 14)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(ctx, 10), 0, 0); item.setLayoutParams(lp); item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (holder[0] != null) holder[0].dismiss(); showTemplateEditDialog(tName, false); } }); root.addView(item);
}

void showTemplateEditDialog(final String oldName, final boolean isNew) {
    final Activity ctx = getTopActivity();
    if (ctx == null) {
        toast("无法获取当前界面");
        return;
    }

    JSONObject existing = isNew ? null : findTemplate(oldName);
    final String defName = (existing == null) ? nextTemplateName() : existing.optString("name", nextTemplateName());
    final String defPrompt = (existing == null) ? getSummaryPrompt() : existing.optString("prompt", getSummaryPrompt());
    final String defMode = (existing == null) ? "days" : existing.optString("mode", "days");
    final int defDays = (existing == null) ? 1 : existing.optInt("days", 1);
    final int defCount = (existing == null) ? 50 : existing.optInt("count", 50);

    ctx.runOnUiThread(new Runnable() {
        public void run() {
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(36, 24, 36, 12);

            final EditText nameInput = createInput(ctx, "模板名称", defName, false, false);
            final EditText promptInput = createInput(ctx, "提示词内容", defPrompt, false, true);
            promptInput.setMinLines(4);

            final RadioGroup modeGroup = new RadioGroup(ctx);
            modeGroup.setOrientation(LinearLayout.VERTICAL);
            final RadioButton dayRadio = new RadioButton(ctx);
            dayRadio.setText("按最近天数提取（提取范围内所有消息）");
            dayRadio.setId(1001);
            final RadioButton countRadio = new RadioButton(ctx);
            countRadio.setText("按最近条数提取");
            countRadio.setId(1002);
            modeGroup.addView(dayRadio);
            modeGroup.addView(countRadio);
            if ("count".equals(defMode)) {
                countRadio.setChecked(true);
            } else {
                dayRadio.setChecked(true);
            }

            final EditText daysInput = createInput(ctx, "最近天数（默认1）", String.valueOf(defDays), false, false);
            daysInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText countInput = createInput(ctx, "最近条数 50-200（默认50）", String.valueOf(defCount), false, false);
            countInput.setInputType(InputType.TYPE_CLASS_NUMBER);

            root.addView(label(ctx, "模板名称"));
            root.addView(nameInput);
            root.addView(label(ctx, "提示词内容"));
            root.addView(promptInput);
            root.addView(label(ctx, "提取消息方式（二选一）"));
            root.addView(modeGroup);
            root.addView(label(ctx, "最近天数（选择按天数提取时生效）"));
            root.addView(daysInput);
            root.addView(label(ctx, "最近条数（选择按条数提取时生效）"));
            root.addView(countInput);

            if (!isNew) {
                Button setDefaultBtn = new Button(ctx);
                boolean isDef = oldName.equals(getDefaultTemplateName());
                setDefaultBtn.setText(isDef ? "当前已是默认模板" : "设为默认模板");
                setDefaultBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        setDefaultTemplateName(oldName);
                        toast("已设为默认模板：" + oldName);
                    }
                });
                root.addView(label(ctx, "默认模板设置"));
                root.addView(setDefaultBtn);
            }

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(root);

            AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                    .setTitle(isNew ? "新建模板" : "编辑模板")
                    .setView(scroll)
                    .setPositiveButton("保存", null)
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            showConfigDialog();
                        }
                    });

            if (!isNew) {
                builder.setNeutralButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteTemplate(oldName);
                        toast("已删除模板：" + oldName);
                        showConfigDialog();
                    }
                });
            }

            final AlertDialog dlg = builder.create();
            dlg.show();
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String name = nameInput.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        toast("模板名称不能为空");
                        return;
                    }
                    String prompt = promptInput.getText().toString().trim();
                    if (TextUtils.isEmpty(prompt)) prompt = getSummaryPrompt();
                    String mode = countRadio.isChecked() ? "count" : "days";
                    int days = safeParseInt(daysInput.getText().toString().trim(), 1);
                    if (days < 1) days = 1;
                    if (days > 30) days = 30;
                    int count = clampCount(safeParseInt(countInput.getText().toString().trim(), 50));

                    JSONObject conflict = findTemplate(name);
                    if (conflict != null && (isNew || !name.equals(oldName))) {
                        toast("模板名称已存在：" + name);
                        return;
                    }

                    upsertTemplate(isNew ? "" : oldName, name, prompt, mode, days, count);
                    toast("模板已保存：" + name);
                    dlg.dismiss();
                    showConfigDialog();
                }
            });
        }
    });
}

TextView label(Activity ctx, String text) {
    TextView tv = new TextView(ctx); tv.setText(text); tv.setTextSize(13); tv.setTextColor(Color.rgb(73, 69, 79)); tv.setPadding(0, dp(ctx, 14), 0, dp(ctx, 6)); return tv;
}

EditText createInput(Activity ctx, String hint, String value, boolean password, boolean multiLine) {
    EditText et = new EditText(ctx); et.setHint(hint); et.setText(value == null ? "" : value); et.setSingleLine(!multiLine); et.setTextColor(Color.rgb(31, 31, 31)); et.setHintTextColor(Color.rgb(120, 117, 127)); et.setTextSize(14); et.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10)); et.setBackground(materialRoundBg(ctx, Color.rgb(250, 250, 255), Color.rgb(218, 220, 230), 14)); if (multiLine) { et.setMinLines(2); et.setGravity(android.view.Gravity.TOP); } if (password) { et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); } else if (!multiLine) { et.setInputType(InputType.TYPE_CLASS_TEXT); } else { et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); } return et;
}

int dp(Activity ctx, int value) { try { return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f); } catch (Throwable e) { return value; } }
GradientDrawable materialRoundBg(Activity ctx, int color, int strokeColor, int radiusDp) { GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadius(dp(ctx, radiusDp)); if (strokeColor != 0) gd.setStroke(dp(ctx, 1), strokeColor); return gd; }
LinearLayout createMaterialCard(Activity ctx) { LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16)); card.setBackground(materialRoundBg(ctx, Color.WHITE, Color.rgb(230, 232, 240), 22)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(ctx, 10), 0, dp(ctx, 8)); card.setLayoutParams(lp); return card; }
TextView materialTitle(Activity ctx, String text) { TextView tv = new TextView(ctx); tv.setText(text); tv.setTextSize(24); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setTextColor(Color.rgb(31, 31, 31)); tv.setPadding(0, 0, 0, dp(ctx, 2)); return tv; }
TextView materialSectionTitle(Activity ctx, String text) { TextView tv = new TextView(ctx); tv.setText(text); tv.setTextSize(17); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setTextColor(Color.rgb(31, 31, 31)); tv.setPadding(0, 0, 0, dp(ctx, 6)); return tv; }
TextView materialBody(Activity ctx, String text) { TextView tv = new TextView(ctx); tv.setText(text); tv.setTextSize(13); tv.setTextColor(Color.rgb(73, 69, 79)); tv.setLineSpacing(0, 1.08f); return tv; }
TextView materialTip(Activity ctx, String text) { TextView tv = new TextView(ctx); tv.setText(text); tv.setTextSize(12); tv.setTextColor(Color.rgb(96, 93, 102)); tv.setLineSpacing(0, 1.08f); return tv; }
Button createFilledButton(Activity ctx, String text) { Button btn = new Button(ctx); btn.setText(text); btn.setTextSize(14); btn.setTextColor(Color.WHITE); btn.setAllCaps(false); btn.setBackground(materialRoundBg(ctx, Color.rgb(103, 80, 164), 0, 18)); btn.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(ctx, 12), 0, 0); btn.setLayoutParams(lp); return btn; }
Button createTonalButton(Activity ctx, String text) { Button btn = new Button(ctx); btn.setText(text); btn.setTextSize(14); btn.setTextColor(Color.rgb(49, 48, 51)); btn.setAllCaps(false); btn.setBackground(materialRoundBg(ctx, Color.rgb(234, 221, 255), 0, 18)); btn.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(ctx, 10), 0, 0); btn.setLayoutParams(lp); return btn; }

String buildHelpText() {
    return "AI聊天总结 使用说明\n" +
            "━━━━━━━━━━━━\n" +
            "1. /ai 总结\n" +
            "   使用默认模板（★）的提示词和提取方式进行总结。\n" +
            "   若未设置任何模板，则默认总结最近1天全部消息。\n" +
            "2. /ai 总结 模板名\n" +
            "   使用指定名称的模板，例如 /ai 总结 感情。\n" +
            "3. /ai 总结 120\n" +
            "   临时按最近 120 条消息总结，范围 50~200。\n" +
            "4. /ai 配置\n" +
            "   配置接口参数，获取模型列表，测试API，并管理提示词模板。\n" +
            "5. /ai 帮助\n" +
            "   显示本帮助。\n\n" +
            "提示：总结结果发送目标可在配置中设置。";
}

void showHelpDialog() {
    final Activity ctx = getTopActivity();
    if (ctx == null) {
        toast("无法获取当前界面");
        return;
    }
    ctx.runOnUiThread(new Runnable() {
        public void run() {
            new AlertDialog.Builder(ctx)
                    .setTitle("AI聊天总结")
                    .setMessage(buildHelpText())
                    .setPositiveButton("知道了", null)
                    .show();
        }
    });
}

void ensureDefaultConfig() {
    if (TextUtils.isEmpty(getString(CFG_API_URL, ""))) putString(CFG_API_URL, DEFAULT_API_URL);
    if (TextUtils.isEmpty(getString(CFG_MODEL, ""))) putString(CFG_MODEL, DEFAULT_MODEL);
    if (TextUtils.isEmpty(getString(CFG_MAX_TOKENS, ""))) putString(CFG_MAX_TOKENS, String.valueOf(DEFAULT_MAX_TOKENS));
}

boolean hasApiConfig() {
    return !TextUtils.isEmpty(getApiUrl()) && !TextUtils.isEmpty(getApiKey()) && !TextUtils.isEmpty(getModel());
}

String getApiUrl() {
    return normalizeApiUrl(getString(CFG_API_URL, DEFAULT_API_URL));
}

String normalizeApiUrl(String apiUrl) {
    return normalizeApiUrl(apiUrl, isClaudeMode());
}

String normalizeApiUrl(String apiUrl, boolean isClaude) {
    if (TextUtils.isEmpty(apiUrl)) return DEFAULT_API_URL;
    String url = apiUrl.trim();
    String lower = url.toLowerCase(Locale.getDefault());
    String chatPrefix = isClaude ? "/v1/messages" : "/v1/chat/completions";
    String otherPrefix = isClaude ? "/v1/chat/completions" : "/v1/messages";

    int q = url.indexOf("?");
    if (q >= 0) {
        url = url.substring(0, q);
        lower = url.toLowerCase(Locale.getDefault());
    }

    while (url.endsWith("/")) {
        url = url.substring(0, url.length() - 1);
        lower = url.toLowerCase(Locale.getDefault());
    }

    if (lower.endsWith(chatPrefix)) return url;

    if (lower.endsWith(otherPrefix)) {
        return url.substring(0, url.length() - otherPrefix.length()) + chatPrefix;
    }

    if (lower.endsWith("/v1")) {
        return url + chatPrefix;
    }

    int v1Idx = lower.lastIndexOf("/v1/");
    if (v1Idx >= 0) {
        String afterV1 = lower.substring(v1Idx + 4);
        if (afterV1.startsWith("chat/") || afterV1.startsWith("messages") || afterV1.startsWith("responses")) {
            return url.substring(0, v1Idx + 4) + chatPrefix;
        }
        return url.substring(0, v1Idx + 3) + chatPrefix;
    }

    return url + chatPrefix;
}

String getApiKey() {
    return getString(CFG_API_KEY, "");
}

String getModel() {
    return getString(CFG_MODEL, DEFAULT_MODEL);
}

String getSummaryPrompt() {
    return DEFAULT_SUMMARY_PROMPT;
}

String safeString(Object value) {
    return value == null ? "" : String.valueOf(value);
}

boolean safeBool(Object value) {
    if (value instanceof Boolean) return ((Boolean) value).booleanValue();
    return "true".equalsIgnoreCase(safeString(value));
}

long safeLong(Object value) {
    try {
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(safeString(value));
    } catch (Throwable e) {
        return 0L;
    }
}

int safeParseInt(String text, int defValue) {
    try {
        return Integer.parseInt(text);
    } catch (Throwable e) {
        return defValue;
    }
}
