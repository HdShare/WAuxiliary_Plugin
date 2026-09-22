import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
String CFG_SUMMARY_COUNT = "summary_count";
String CFG_SUMMARY_PROMPT = "summary_prompt";
String CFG_ASK_SYSTEM_PROMPT = "ask_system_prompt";
String CFG_ASK_CONTEXT_COUNT = "ask_context_count";
String CFG_OUTPUT_TEMPLATE = "output_template";
String CFG_LEGACY_ASK_TEMPLATE = "ask_template";
String CFG_LOG_ENABLE = "log_enable";
boolean DEFAULT_LOG_ENABLE = false;
String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
String DEFAULT_MODEL = "gpt-4o-mini";
String DEFAULT_SUMMARY_PROMPT = "请基于聊天记录生成简短中文总结。只输出正文要点，不要输出标题，只输出最终总结。";
String DEFAULT_ASK_SYSTEM_PROMPT = "回答时言简意赅，直接回答结论即可。控制在300字以下。";
String DEFAULT_OUTPUT_TEMPLATE = "问：{问题原文/总结标题}\n---------\n{回复正文}\n---------\n(以上内容由[{模型名称}]整理，仅供参考)";
int DEFAULT_ASK_CONTEXT_COUNT = 30;

boolean mLogEnabled = false;

void onLoad() {
    ensureDefaultConfig();
    mLogEnabled = isLogEnabled();
    logx("AI聊天总结插件已加载");
}

void openSettings() {
    showConfigDialog();
}

boolean onClickSendBtn(String text) {
    if (text == null) return false;
    String cmd = aiSumNormalizeInput(text);
    if (isAiCommand(cmd)) {
        String talker = getTargetTalker();
        handleCommand(talker, cmd, true);
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
        content = aiSumNormalizeInput(content);
        if (!isAiCommand(content)) return;

        handleCommand(msgInfoBean.getTalker(), content, false);
    } catch (Throwable e) {
        logx("AI聊天总结处理消息失败: " + e.getMessage());
    }
}

boolean isAiCommand(String text) {
    if (TextUtils.isEmpty(text)) return false;
    return text.equals("/ai") || text.startsWith("/ai ");
}

// 输入规范化：去掉首尾空白，并把各种宽度的空白（全角、不换行、零宽等）统一成半角空格，连续空白合并为一个
String aiSumNormalizeInput(String text) {
    if (text == null) return "";
    String t = text.replaceAll("[\\s\\u00A0\\u1680\\u2000-\\u200D\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+", " ");
    return t.trim();
}

void handleCommand(String talker, String cmd, boolean intercepted) {
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

        if ("/ai 日志".equals(cmd) || "/ai log".equalsIgnoreCase(cmd) || "/ai logs".equalsIgnoreCase(cmd)) {
            showLogDialog();
            return;
        }

        if (isAskCommand(cmd)) {
            String question = parseAskQuestion(cmd);
            askAi(talker, question);
            return;
        }

        if (isSummaryCommand(cmd)) {
            int count = parseSummaryCount(cmd);
            summarizeChat(talker, count);
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
        return arg.matches("\\d+");
    }
    return false;
}

boolean isAskCommand(String cmd) {
    if (TextUtils.isEmpty(cmd)) return false;
    return cmd.equals("/ai 提问") || cmd.startsWith("/ai 提问 ");
}

String parseAskQuestion(String cmd) {
    if (TextUtils.isEmpty(cmd)) return "";
    if (cmd.length() <= "/ai 提问".length()) return "";
    return cmd.substring("/ai 提问".length()).trim();
}

void askAi(final String talker, final String question) {
    try {
        if (TextUtils.isEmpty(question)) {
            toast("请输入要提问的内容");
            return;
        }
        if (!hasApiConfig()) {
            showConfigDialog();
            return;
        }

        toast("AI 正在回答，请稍候...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String contextText = "";
                    int contextCount = getAskContextCount();
                    if (contextCount > 0) {
                        Map built = buildHistoryText(talker, contextCount);
                        contextText = built == null ? "" : safeString(built.get("text"));
                        if (!TextUtils.isEmpty(contextText)) {
                            logx("[AI提问] 附带最近聊天记录，请求条数=" + contextCount);
                        }
                    }
                    callAskApi(talker, question, contextText);
                } catch (Throwable e) {
                    logx("[AI提问] 提问失败: " + e.getMessage());
                    toast("AI提问失败：" + e.getMessage());
                }
            }
        }).start();
    } catch (Throwable e) {
        logx("[AI提问] 启动提问失败: " + e.getMessage());
        toast("AI提问失败：" + e.getMessage());
    }
}

int parseSummaryCount(String cmd) {
    int count = getSummaryCount();
    try {
        String[] parts = cmd.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p == null) continue;
            p = p.trim();
            if (p.matches("\\d+")) {
                count = Integer.parseInt(p);
                break;
            }
        }
    } catch (Throwable ignored) {}
    return clampCount(count);
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
                    Map built = buildHistoryText(talker, count);
                    String historyText = built == null ? "" : safeString(built.get("text"));
                    String summaryTitle = built == null ? "" : safeString(built.get("title"));
                    if (TextUtils.isEmpty(historyText)) {
                        logx("[AI总结] 无有效聊天记录 talker=" + talker + " count=" + count);
                        toast("AI总结失败：没有读取到有效聊天记录");
                        return;
                    }
                    callSummaryApi(talker, historyText, count, summaryTitle);
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

Map buildHistoryText(String talker, int count) {
    Map out = new HashMap();
    out.put("text", "");
    out.put("title", "");
    try {
        List list = queryRecentHistoryMsg(talker, count);
        if (list == null || list.size() == 0) return out;

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
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
            try {
                if (!isReadableMsg(msg)) continue;

                String sender = safeString(callNoArg(msg, "getSendTalker"));
                String content = buildMessageContent(msg);
                if (TextUtils.isEmpty(content)) continue;

                // 确定发言人名称：统一用 getSendTalker 获取发送者昵称
                String speaker;
                if (!TextUtils.isEmpty(sender)) {
                    speaker = resolveDisplayName(sender, talker);
                    if (TextUtils.isEmpty(speaker)) speaker = sender;
                } else {
                    continue;
                }

                long time = safeLong(callNoArg(msg, "getCreateTime"));
                if (time > 0 && time < 1000000000000L) time = time * 1000L;
                String timeText = time > 0 ? sdf.format(new Date(time)) : "未知时间";

                sb.append("[").append(timeText).append("] ");
                sb.append(speaker).append(": ");
                sb.append(content.replace("\n", " ").trim()).append("\n");
                added++;
            } catch (Throwable ignored) {}
        }

        if (added == 0) return out;
        out.put("text", sb.toString());
        out.put("title", aiSumBuildTitle(list, talker));
        return out;
    } catch (Throwable e) {
        logx("[AI总结] 读取聊天记录失败: " + e.getMessage());
        return out;
    }
}

// 标题由代码按消息时间范围生成，不依赖模型：如「9月22日群聊摘要」「5月20日至25日群聊摘要」
String aiSumBuildTitle(List list, String talker) {
    long firstTime = 0L;
    long lastTime = 0L;
    if (list != null && list.size() > 0) {
        firstTime = normalizeTime(safeLong(callNoArg(list.get(0), "getCreateTime")));
        lastTime = normalizeTime(safeLong(callNoArg(list.get(list.size() - 1), "getCreateTime")));
    }
    String dateLabel = aiSumFormatDateRange(firstTime, lastTime);
    String chatLabel = (talker != null && talker.endsWith("@chatroom")) ? "群聊摘要" : "聊天摘要";
    return dateLabel + chatLabel;
}

String aiSumFormatDateRange(long startTime, long endTime) {
    if (startTime <= 0 && endTime <= 0) return "";
    try {
        long s = startTime > 0 ? startTime : endTime;
        long e = endTime > 0 ? endTime : startTime;
        SimpleDateFormat dayFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy", Locale.getDefault());
        SimpleDateFormat dayOnly = new SimpleDateFormat("d", Locale.getDefault());
        String sDay = dayFmt.format(new Date(s));
        String eDay = dayFmt.format(new Date(e));
        if (sDay.equals(eDay)) return eDay;
        if (monthFmt.format(new Date(s)).equals(monthFmt.format(new Date(e)))) {
            return sDay + "至" + dayOnly.format(new Date(e)) + "日";
        }
        if (yearFmt.format(new Date(s)).equals(yearFmt.format(new Date(e)))) {
            return sDay + "至" + eDay;
        }
        return yearFmt.format(new Date(s)) + "年" + sDay + "至" + yearFmt.format(new Date(e)) + "年" + eDay;
    } catch (Throwable t) {
        return "";
    }
}

List queryRecentHistoryMsg(String talker, int count) {
    int queryCount = Math.max(count * 2, 200);
    long now = currentTimeMillisSafe();

    // 方案一：倒序直取。宿主把 startTime 当下限处理（createTime >= startTime），传 now 会一条都取不到，所以从 0 开始才能拿到最新一批
    List desc = queryHistoryMsgSafe(talker, 0L, false, queryCount);
    logx("[AI总结] 倒序查询 startTime=0 返回=" + (desc == null ? 0 : desc.size()));
    if (desc == null || desc.size() == 0) {
        desc = queryHistoryMsgSafe(talker, now, false, queryCount);
        logx("[AI总结] 倒序查询 startTime=now 返回=" + (desc == null ? 0 : desc.size()));
    }
    if (desc != null && desc.size() > 0) {
        long descNewest = aiSumMaxMsgTime(desc);
        List newer = queryHistoryMsgSafe(talker, descNewest, true, 2 * count + 10);
        boolean hasNewer = false;
        for (int i = 0; newer != null && i < newer.size(); i++) {
            Object m = newer.get(i);
            if (m == null) continue;
            if (normalizeTime(safeLong(callNoArg(m, "getCreateTime"))) > descNewest) { hasNewer = true; break; }
        }
        if (!hasNewer) {
            List filt = filterReadableAndSortByTime(desc);
            if (filt != null && filt.size() > 0) {
                logx("[AI总结] 倒序结果已到最新，可用条数=" + filt.size());
                if (filt.size() <= count) return filt;
                return new ArrayList(filt.subList(filt.size() - count, filt.size()));
            }
        }
        logx("[AI总结] 倒序结果不是最新，转正序兜底");
    }

    // 方案二：正序兜底。窗口从最近逐步向前扩到取够 count 条；整页时从页尾继续翻页补到最新一条
    long day = 24L * 60L * 60L * 1000L;
    long window = day;
    List best = new ArrayList();
    for (int i = 0; i < 10; i++) {
        long start = now - window;
        if (start < 0) start = 0;
        List found = queryHistoryMsgSafe(talker, start, true, queryCount);
        logx("[AI总结] 兜底查询 startTime=" + formatLogTime(start) + " 返回=" + (found == null ? 0 : found.size()));
        if (found != null && found.size() > 0) {
            if (found.size() >= queryCount) {
                found = aiSumPageForward(talker, found, queryCount);
            }
            best = found;
            if (best.size() >= count) break;
        }
        window *= 2;
        if (start <= 0) break;
    }

    best = filterReadableAndSortByTime(best);
    if (best == null || best.size() == 0) return best;
    if (best.size() <= count) return best;
    return new ArrayList(best.subList(best.size() - count, best.size()));
}

// 从已有结果的最新一条继续正序翻页，直到取到最新一页；按 msgId 去掉页与页之间的重复
List aiSumPageForward(String talker, List seed, int queryCount) {
    List all = new ArrayList(seed);
    int pages = 0;
    while (pages < 20) {
        long from = aiSumMaxMsgTime(all);
        if (from <= 0) break;
        List next = queryHistoryMsgSafe(talker, from, true, queryCount);
        pages++;
        if (next == null || next.size() == 0) break;
        int added = 0;
        for (int i = 0; i < next.size(); i++) {
            Object msg = next.get(i);
            if (msg == null) continue;
            if (aiSumContainsMsgId(all, msg)) continue;
            all.add(msg);
            added++;
        }
        if (added == 0) break;
        if (next.size() < queryCount) break;
    }
    logx("[AI总结] 翻页 " + pages + " 次累计 " + all.size() + " 条 最新=" + formatLogTime(aiSumMaxMsgTime(all)));
    return all;
}

boolean aiSumContainsMsgId(List list, Object msg) {
    long id = safeLong(callNoArg(msg, "getMsgId"));
    if (id <= 0) return false;
    for (int i = 0; i < list.size(); i++) {
        Object other = list.get(i);
        if (other == null) continue;
        if (safeLong(callNoArg(other, "getMsgId")) == id) return true;
    }
    return false;
}

long aiSumMaxMsgTime(List list) {
    long max = 0L;
    if (list == null) return max;
    for (int i = 0; i < list.size(); i++) {
        Object msg = list.get(i);
        if (msg == null) continue;
        long t = normalizeTime(safeLong(callNoArg(msg, "getCreateTime")));
        if (t > max) max = t;
    }
    return max;
}

// 渲染输出模板：{变量} 或 {变量A/变量B}（也支持反斜杠分隔），多变量按顺序取第一个非空值；都不匹配替换为空
String formatOutput(String template, Map values) {
    if (TextUtils.isEmpty(template)) return "";
    if (values == null) values = new HashMap();
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < template.length()) {
        int open = template.indexOf("{", i);
        if (open < 0) { sb.append(template.substring(i)); break; }
        int close = template.indexOf("}", open + 1);
        if (close < 0) { sb.append(template.substring(i)); break; }
        sb.append(template.substring(i, open));
        String[] names = template.substring(open + 1, close).split("[/\\\\]");
        String value = "";
        for (int k = 0; k < names.length; k++) {
            String key = names[k].trim();
            if (TextUtils.isEmpty(key)) continue;
            Object v = values.get(key);
            if (v == null) continue;
            String s = String.valueOf(v);
            if (!TextUtils.isEmpty(s)) { value = s; break; }
        }
        sb.append(value);
        i = close + 1;
    }
    return sb.toString();
}

// 总结：标题由代码生成（codeTitle），AI 若仍输出了【标题】行会被剥掉，避免重复
String formatSummaryOutput(String summary, String model, String codeTitle) {
    String text = safeString(summary).trim();
    int nl = text.indexOf("\n");
    String firstLine = nl > 0 ? text.substring(0, nl).trim() : text;
    if (firstLine.startsWith("【") && firstLine.endsWith("】")) {
        text = nl > 0 ? text.substring(nl + 1).trim() : "";
    }
    Map values = new HashMap();
    values.put("总结标题", summaryTitleWithTag(codeTitle));
    values.put("问题原文", summaryTitleWithTag(codeTitle));
    values.put("回复正文", text);
    values.put("回答正文", text);
    values.put("模型名称", safeString(model));
    return formatOutput(getOutputTemplate(), values);
}

// 总结标题统一由代码带 【AI聊天总结】 前缀，模板里就不用再写插件名；AI 自带的外层方括号会被去掉
String summaryTitleWithTag(String aiTitle) {
    String t = safeString(aiTitle).trim();
    if (t.length() > 2 && t.startsWith("【") && t.endsWith("】")) {
        t = t.substring(1, t.length() - 1).trim();
    }
    return "【AI聊天总结】" + t;
}

List queryHistoryMsgSafe(String talker, long startTime, boolean isAsc, int queryCount) {
    try {
        List found = queryHistoryMsg(talker, startTime, isAsc, queryCount);
        return found == null ? new ArrayList() : found;
    } catch (Throwable e) {
        logx("[AI总结] queryHistoryMsg失败 startTime=" + formatLogTime(startTime) + " 错误=" + e.getMessage());
        return new ArrayList();
    }
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

boolean isClaudeNativeApi(String apiUrl) {
    if (TextUtils.isEmpty(apiUrl)) return false;
    String lower = apiUrl.toLowerCase(Locale.getDefault());
    return lower.indexOf("api.anthropic.com") >= 0 || lower.endsWith("/v1/messages");
}

List buildSingleUserMessages(String content) {
    java.util.ArrayList messages = new java.util.ArrayList();
    Map user = new HashMap();
    user.put("role", "user");
    user.put("content", content);
    messages.add(user);
    return messages;
}

void callSummaryApi(final String talker, String historyText, int count, String codeTitle) {
    try {
        String apiUrl = getApiUrl();
        String apiKey = getApiKey();
        String model = getModel();
        String userContent = "请直接输出最终聊天总结，不要输出推理过程、分析过程或草稿。请总结以下最近 " + count + " 条聊天记录：\n\n" + historyText;

        Map params = new HashMap();
        Map headers = new HashMap();
        headers.put("Content-Type", "application/json");

        if (isClaudeNativeApi(apiUrl)) {
            params.put("model", model);
            params.put("system", getSummaryPrompt());
            params.put("messages", buildSingleUserMessages(userContent));
            params.put("max_tokens", Integer.valueOf(1500));
            if (!TextUtils.isEmpty(apiKey)) {
                headers.put("x-api-key", apiKey.trim());
            }
            headers.put("anthropic-version", "2023-06-01");
        } else {
            params.put("model", model);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", getSummaryPrompt());
            messages.put(system);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userContent);
            messages.put(user);

            params.put("messages", jsonArrayToList(messages));
            params.put("max_tokens", Integer.valueOf(1500));
            if (!TextUtils.isEmpty(apiKey)) {
                headers.put("Authorization", normalizeAuthHeader(apiKey));
            }
        }

        final String maskedKey = maskApiKey(apiKey);
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        post(apiUrl, params, headers, 90L, body -> {
            try {
                if (TextUtils.isEmpty(body)) {
                    logx("[AI总结] 接口返回为空 时间=" + timestamp + " 地址=" + apiUrl + " key=" + maskedKey + " model=" + model);
                    toast("AI总结失败：接口返回为空");
                    return;
                }
                String summary = parseSummaryResponse(body);
                if (TextUtils.isEmpty(summary)) {
                    logx("[AI总结] 无法解析返回内容 时间=" + timestamp + " 地址=" + apiUrl + " key=" + maskedKey + " model=" + model + " 响应前200字=" + body.substring(0, Math.min(200, body.length())));
                    toast("AI总结失败：接口没有返回可用内容");
                    return;
                }
                sendText(talker, formatSummaryOutput(summary, model, codeTitle));
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

// 提问附带聊天记录：记录仅作参考，可能与问题无关；拼成发给模型的用户消息，问题原文保持干净用于输出模板
String buildAskUserContent(String question, String contextText) {
    if (TextUtils.isEmpty(contextText)) return question;
    return "以下是当前聊天的最近聊天记录，仅供参考，可能与问题无关，可直接忽略：\n"
            + "--------\n"
            + contextText
            + "--------\n\n"
            + "问题：" + question;
}

void callAskApi(final String talker, final String question, final String contextText) {
    try {
        String apiUrl = getApiUrl();
        String apiKey = getApiKey();
        String model = getModel();
        String systemPrompt = getAskSystemPrompt();
        String userContent = buildAskUserContent(question, contextText);

        Map params = new HashMap();
        Map headers = new HashMap();
        headers.put("Content-Type", "application/json");

        if (isClaudeNativeApi(apiUrl)) {
            params.put("model", model);
            params.put("system", systemPrompt);
            params.put("messages", buildSingleUserMessages(userContent));
            params.put("max_tokens", Integer.valueOf(1500));
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
            params.put("max_tokens", Integer.valueOf(1500));
            if (!TextUtils.isEmpty(apiKey)) {
                headers.put("Authorization", normalizeAuthHeader(apiKey));
            }
        }

        final String maskedKey = maskApiKey(apiKey);
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        final String finalApiUrl = apiUrl;
        final String finalModel = model;

        post(apiUrl, params, headers, 90L, body -> {
            try {
                if (TextUtils.isEmpty(body)) {
                    logx("[AI提问] 接口返回为空 时间=" + timestamp + " 地址=" + finalApiUrl + " key=" + maskedKey + " model=" + finalModel);
                    toast("AI提问失败：接口返回为空");
                    return;
                }
                String answer = parseSummaryResponse(body);
                if (TextUtils.isEmpty(answer)) {
                    logx("[AI提问] 无法解析返回内容 时间=" + timestamp + " 地址=" + finalApiUrl + " key=" + maskedKey + " model=" + finalModel + " 响应前200字=" + body.substring(0, Math.min(200, body.length())));
                    toast("AI提问失败：接口没有返回可用内容");
                    return;
                }
                sendText(talker, formatAskAnswer(question, answer.trim(), finalModel));
            } catch (Throwable e) {
                logx("[AI提问] 解析响应异常 时间=" + timestamp + " 地址=" + finalApiUrl + " key=" + maskedKey + " 错误=" + e.getMessage());
                toast("AI提问失败：解析接口响应异常");
            }
        });
    } catch (Throwable e) {
        logx("[AI提问] 调用接口异常: " + e.getMessage());
        toast("AI提问失败：调用接口异常");
    }
}

String formatAskAnswer(String question, String answer, String model) {
    Map values = new HashMap();
    values.put("问题原文", safeString(question));
    values.put("回复正文", safeString(answer));
    values.put("回答正文", safeString(answer));
    values.put("模型名称", safeString(model));
    return formatOutput(getOutputTemplate(), values);
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

        String message = json.optString("message", "");
        if (!TextUtils.isEmpty(message)) return message;
    } catch (Throwable e) {
        logx("非 JSON 响应: " + e.getMessage());
    }
    return "";
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

boolean isLogEnabled() {
    return getBoolean(CFG_LOG_ENABLE, DEFAULT_LOG_ENABLE);
}

void logx(Object msg) {
    if (mLogEnabled) {
        log(msg);
    }
}

File getHostLogFile() {
    try {
        Object file = getLogFile();
        if (file instanceof File) return (File) file;
    } catch (Throwable ignored) {}
    return null;
}

String readLogContent() {
    BufferedReader reader = null;
    try {
        File file = getHostLogFile();
        if (file == null || !file.exists() || file.length() <= 0) return "";

        StringBuilder sb = new StringBuilder();
        reader = new BufferedReader(new FileReader(file));
        String line;
        int count = 0;
        while ((line = reader.readLine()) != null && count < 3000) {
            sb.append(line).append("\n");
            count++;
        }
        return sb.toString().trim();
    } catch (Throwable e) {
        return "读取日志失败：" + e.getMessage();
    } finally {
        try {
            if (reader != null) reader.close();
        } catch (Throwable ignored) {}
    }
}

void showLogDialog() {
    final Activity ctx = getTopActivity();
    if (ctx == null) {
        toast("无法获取当前界面");
        return;
    }

    ctx.runOnUiThread(new Runnable() {
        public void run() {
            final String logContent = readLogContent();
            final String displayText;
            if (!TextUtils.isEmpty(logContent)) {
                displayText = logContent;
            } else if (isLogEnabled()) {
                displayText = "当前没有日志\n当前日志记录已开启";
            } else {
                displayText = "当前没有日志\n当前日志记录关闭，如需打开请输入'/ai 配置'打开日志开关";
            }

            TextView tv = new TextView(ctx);
            tv.setText(displayText);
            tv.setTextSize(12);
            tv.setTextIsSelectable(true);
            tv.setPadding(36, 24, 36, 24);

            ScrollView scroll = new ScrollView(ctx);
            scroll.setVerticalScrollBarEnabled(true);
            scroll.setScrollbarFadingEnabled(false);
            scroll.addView(tv);

            new AlertDialog.Builder(ctx)
                    .setTitle("AI聊天总结日志")
                    .setView(scroll)
                    .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            copyTextToClipboard("AI聊天总结日志", displayText);
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        }
    });
}

void copyTextToClipboard(String label, String text) {
    try {
        ClipboardManager cm = (ClipboardManager) hostContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            toast("日志已复制");
        } else {
            toast("复制失败：无法获取剪贴板");
        }
    } catch (Throwable e) {
        toast("复制失败：" + e.getMessage());
    }
}

void showConfigDialog() {
    final Activity ctx = getTopActivity();
    if (ctx == null) {
        toast("无法获取当前界面");
        return;
    }

    ctx.runOnUiThread(new Runnable() {
        public void run() {
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(36, 24, 36, 12);

            TextView tip = new TextView(ctx);
            tip.setText("配置通用 messages 风格接口。API Key 会保存在插件 config.prop 中，请自行确认设备环境安全。");
            tip.setTextSize(12);
            root.addView(tip);

            final EditText apiUrlInput = createInput(ctx, "API 地址", getApiUrl(), false, true);
            final EditText apiKeyInput = createInput(ctx, "API Key", getApiKey(), true, false);
            final EditText modelInput = createInput(ctx, "模型名称", getModel(), false, false);
            final EditText countInput = createInput(ctx, "总结条数 50-200", String.valueOf(getSummaryCount()), false, false);
            countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText askContextInput = createInput(ctx, "提问附带记录条数 0-200（0=不带）", String.valueOf(getAskContextCount()), false, false);
            askContextInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            final EditText promptInput = createInput(ctx, "总结提示词", getSummaryPrompt(), false, true);
            promptInput.setMinLines(4);
            final EditText askSystemPromptInput = createInput(ctx, "提问系统提示词", getAskSystemPrompt(), false, true);
            askSystemPromptInput.setMinLines(3);
            final EditText outputTemplateInput = createInput(ctx, "输出模板", getOutputTemplate(), false, true);
            outputTemplateInput.setMinLines(5);

            final Switch logSwitch = new Switch(ctx);
            logSwitch.setText("开启运行日志");
            logSwitch.setChecked(mLogEnabled);
            logSwitch.setPadding(0, 18, 0, 4);

            TextView logTip = new TextView(ctx);
            logTip.setText("关闭后不写入日志，可避免查询调试日志过多。排查问题时再开启。");
            logTip.setTextSize(12);

            root.addView(label(ctx, "API 地址"));
            root.addView(apiUrlInput);
            root.addView(label(ctx, "API Key"));
            root.addView(apiKeyInput);
            root.addView(label(ctx, "模型名称"));
            root.addView(modelInput);
            root.addView(label(ctx, "默认总结条数"));
            root.addView(countInput);
            root.addView(label(ctx, "总结提示词"));
            root.addView(promptInput);
            root.addView(label(ctx, "提问系统提示词"));
            root.addView(askSystemPromptInput);
            root.addView(label(ctx, "提问参考记录条数"));
            root.addView(askContextInput);
            root.addView(label(ctx, "输出模板"));
            root.addView(outputTemplateInput);
            root.addView(label(ctx, "日志设置"));
            root.addView(logSwitch);
            root.addView(logTip);

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(root);

            new AlertDialog.Builder(ctx)
                    .setTitle("AI聊天总结配置")
                    .setView(scroll)
                    .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String apiUrl = apiUrlInput.getText().toString().trim();
                            String apiKey = apiKeyInput.getText().toString().trim();
                            String model = modelInput.getText().toString().trim();
                            String prompt = promptInput.getText().toString().trim();
                            String askSystemPrompt = askSystemPromptInput.getText().toString().trim();
                            String outputTemplate = outputTemplateInput.getText().toString().trim();
                            int count = safeParseInt(countInput.getText().toString().trim(), 100);
                            int askContextCount = safeParseInt(askContextInput.getText().toString().trim(), DEFAULT_ASK_CONTEXT_COUNT);

                            putString(CFG_API_URL, TextUtils.isEmpty(apiUrl) ? DEFAULT_API_URL : apiUrl);
                            putString(CFG_API_KEY, apiKey);
                            putString(CFG_MODEL, TextUtils.isEmpty(model) ? DEFAULT_MODEL : model);
                            putInt(CFG_SUMMARY_COUNT, clampCount(count));
                            putInt(CFG_ASK_CONTEXT_COUNT, clampAskContextCount(askContextCount));
                            putString(CFG_SUMMARY_PROMPT, TextUtils.isEmpty(prompt) ? DEFAULT_SUMMARY_PROMPT : prompt);
                            putString(CFG_ASK_SYSTEM_PROMPT, TextUtils.isEmpty(askSystemPrompt) ? DEFAULT_ASK_SYSTEM_PROMPT : askSystemPrompt);
                            putString(CFG_OUTPUT_TEMPLATE, TextUtils.isEmpty(outputTemplate) ? DEFAULT_OUTPUT_TEMPLATE : outputTemplate);
                            putBoolean(CFG_LOG_ENABLE, logSwitch.isChecked());
                            mLogEnabled = logSwitch.isChecked();
                            toast("AI聊天总结配置已保存");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    });
}

TextView label(Activity ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(13);
    tv.setPadding(0, 18, 0, 4);
    return tv;
}

EditText createInput(Activity ctx, String hint, String value, boolean password, boolean multiLine) {
    EditText et = new EditText(ctx);
    et.setHint(hint);
    et.setText(value == null ? "" : value);
    et.setSingleLine(!multiLine);
    if (multiLine) {
        et.setMinLines(2);
        et.setGravity(android.view.Gravity.TOP);
    }
    if (password) {
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    } else if (!multiLine) {
        et.setInputType(InputType.TYPE_CLASS_TEXT);
    } else {
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    }
    return et;
}

String buildHelpText() {
    return "AI聊天总结 使用说明\n" +
            "━━━━━━━━━━━━\n" +
            "1. /ai 总结\n" +
            "   总结当前聊天最近默认条数的消息。\n" +
            "2. /ai 总结 120\n" +
            "   临时总结最近 120 条消息，范围 50~200。\n" +
            "3. /ai 提问 问题内容\n" +
            "   调用 AI 回答问题，可附带最近聊天记录作参考（条数可配，0=不带）。\n" +
            "4. /ai 配置\n" +
            "   配置 API 地址、Key、模型名称、默认条数、提问参考记录条数、提示词和输出模板。\n" +
            "5. /ai 日志\n" +
            "   查看当前插件日志。\n" +
            "6. /ai 帮助\n" +
            "   显示本帮助。\n\n" +
            "提示：总结和提问结果会直接发送到当前群聊或私聊。";
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
    if (getInt(CFG_SUMMARY_COUNT, 0) <= 0) putInt(CFG_SUMMARY_COUNT, 100);
    if (TextUtils.isEmpty(getString(CFG_ASK_CONTEXT_COUNT, ""))) putInt(CFG_ASK_CONTEXT_COUNT, DEFAULT_ASK_CONTEXT_COUNT);
    if (TextUtils.isEmpty(getString(CFG_SUMMARY_PROMPT, ""))) putString(CFG_SUMMARY_PROMPT, DEFAULT_SUMMARY_PROMPT);
    if (TextUtils.isEmpty(getString(CFG_ASK_SYSTEM_PROMPT, ""))) putString(CFG_ASK_SYSTEM_PROMPT, DEFAULT_ASK_SYSTEM_PROMPT);
    if (TextUtils.isEmpty(getString(CFG_OUTPUT_TEMPLATE, ""))) putString(CFG_OUTPUT_TEMPLATE, DEFAULT_OUTPUT_TEMPLATE);
}

boolean hasApiConfig() {
    return !TextUtils.isEmpty(getApiUrl()) && !TextUtils.isEmpty(getApiKey()) && !TextUtils.isEmpty(getModel());
}

String getApiUrl() {
    return getString(CFG_API_URL, DEFAULT_API_URL);
}

String getApiKey() {
    return getString(CFG_API_KEY, "");
}

String getModel() {
    return getString(CFG_MODEL, DEFAULT_MODEL);
}

int getSummaryCount() {
    return clampCount(getInt(CFG_SUMMARY_COUNT, 100));
}

int getAskContextCount() {
    return clampAskContextCount(getInt(CFG_ASK_CONTEXT_COUNT, DEFAULT_ASK_CONTEXT_COUNT));
}

int clampAskContextCount(int count) {
    if (count < 0) return 0;
    if (count > 200) return 200;
    return count;
}

String getSummaryPrompt() {
    return getString(CFG_SUMMARY_PROMPT, DEFAULT_SUMMARY_PROMPT);
}

String getAskSystemPrompt() {
    return getString(CFG_ASK_SYSTEM_PROMPT, DEFAULT_ASK_SYSTEM_PROMPT);
}

String getOutputTemplate() {
    String t = decodeConfigText(getString(CFG_OUTPUT_TEMPLATE, ""));
    if (TextUtils.isEmpty(t)) t = decodeConfigText(getString(CFG_LEGACY_ASK_TEMPLATE, ""));
    if (TextUtils.isEmpty(t)) t = DEFAULT_OUTPUT_TEMPLATE;
    return t;
}

String decodeConfigText(String text) {
    if (text == null) return "";
    return text.replace("\\n", "\n");
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
