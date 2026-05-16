
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import de.robv.android.xposed.*;
import me.hd.wauxv.data.bean.info.FriendInfo;

boolean DEF_AUTO_LIKE_ENABLE = true;
int DEF_REFRESH_INTERVAL = 300000;
int DEF_MIN_LIKE_DELAY_MS = 60000;
int DEF_MAX_LIKE_DELAY_MS = 3600000;
int DEF_MAX_POST_AGE_HOURS = 24;
int DEF_MAX_PROCESSED_SAVE = 300;
int DEF_DUP_SUPPRESS_MS = 20000;
int DEF_DELAY_MODE = 1; // 0=固定 1=随机
int DEF_FIXED_LIKE_DELAY_MS = 300000;
boolean DEF_REFRESH_ENABLE = true;
int DEF_REFRESH_MODE = 0; // 0=全天循环 1=时间段循环
String DEF_REFRESH_START = "08:00";
String DEF_REFRESH_END = "23:00";
boolean DEF_SCHEDULE_ENABLE = false;
String DEF_SCHEDULE_START = "08:00";
String DEF_SCHEDULE_END = "23:30";
int DEF_UNKNOWN_TIME_POLICY = 0; // 0=跳过 1=允许
int DEF_UNKNOWN_TYPE_POLICY = 1; // 0=跳过 1=允许
int DEF_LOG_MAX = 300;

String CFG_ENABLE = "auto_like_enable_v1";
String CFG_REFRESH_INTERVAL = "auto_like_refresh_interval_v1";
String CFG_MIN_LIKE_DELAY_MS = "auto_like_min_delay_ms_v2";
String CFG_MAX_LIKE_DELAY_MS = "auto_like_max_delay_ms_v2";
String CFG_MAX_POST_AGE_HOURS = "auto_like_max_post_age_hours_v2";
String CFG_LIST_MODE = "auto_like_list_mode_v2"; // 0=白名单 1=黑名单
String CFG_WHITE_LIST = "auto_like_white_list_v2";
String CFG_BLACK_LIST = "auto_like_black_list_v2";
String CFG_MAX_PROCESSED_SAVE = "auto_like_max_processed_save_v1";
String CFG_DUP_SUPPRESS_MS = "auto_like_dup_suppress_ms_v1";
String CFG_DELAY_MODE = "auto_like_delay_mode_v3";
String CFG_FIXED_LIKE_DELAY_MS = "auto_like_fixed_delay_ms_v3";
String CFG_REFRESH_ENABLE = "auto_like_refresh_enable_v1";
String CFG_REFRESH_MODE = "auto_like_refresh_mode_v3";
String CFG_REFRESH_START = "auto_like_refresh_start_v6";
String CFG_REFRESH_END = "auto_like_refresh_end_v6";
String CFG_SCHEDULE_ENABLE = "auto_like_schedule_enable_v3";
String CFG_SCHEDULE_START = "auto_like_schedule_start_v3";
String CFG_SCHEDULE_END = "auto_like_schedule_end_v3";
String CFG_SKIP_TEXT = "auto_like_skip_text_v3";
String CFG_SKIP_IMAGE = "auto_like_skip_image_v3";
String CFG_SKIP_VIDEO = "auto_like_skip_video_v3";
String CFG_SKIP_KEYWORDS = "auto_like_skip_keywords_v4";
String CFG_KEYWORD_FILTER_TEXT = "auto_like_keyword_filter_text_v5";
String CFG_KEYWORD_FILTER_IMAGE = "auto_like_keyword_filter_image_v5";
String CFG_KEYWORD_FILTER_VIDEO = "auto_like_keyword_filter_video_v5";
String CFG_UNKNOWN_TIME_POLICY = "auto_like_unknown_time_policy_v3";
String CFG_UNKNOWN_TYPE_POLICY = "auto_like_unknown_type_policy_v3";
String CFG_LIKE_LOGS = "auto_like_logs_v3";
String CFG_LIKE_SUCCESS_LOGS = "auto_like_success_logs_v1";
String CFG_LIKE_SKIP_LOGS = "auto_like_skip_logs_v1";
String CFG_REFRESH_LOGS = "auto_like_refresh_logs_v1";
String CFG_LOG_ENABLE = "auto_like_log_enable_v4";
String CFG_LOG_MAX = "auto_like_log_max_v3";
String CFG_SNS_NOTIFY_ENABLE = "auto_like_sns_notify_enable_v1";
String CFG_SNS_NOTIFY_TOAST = "auto_like_sns_notify_toast_v1";
String CFG_SNS_NOTIFY_LIST = "auto_like_sns_notify_list_v1";
String CFG_SNS_NOTIFY_TITLE_TPL = "auto_like_sns_notify_title_tpl_v1";
String CFG_SNS_NOTIFY_BODY_TPL = "auto_like_sns_notify_body_tpl_v1";
String CFG_SNS_NOTIFY_TOAST_TPL = "auto_like_sns_notify_toast_tpl_v1";

boolean AUTO_LIKE_ENABLE = DEF_AUTO_LIKE_ENABLE;
// 旧单目标配置已清理，白名单默认空
int REFRESH_INTERVAL = DEF_REFRESH_INTERVAL;
int MIN_LIKE_DELAY_MS = DEF_MIN_LIKE_DELAY_MS;
int MAX_LIKE_DELAY_MS = DEF_MAX_LIKE_DELAY_MS;
int MAX_POST_AGE_HOURS = DEF_MAX_POST_AGE_HOURS;
int LIST_MODE = 0;
String WHITE_LIST_RAW = "";
String BLACK_LIST_RAW = "";
HashSet whiteListSet = new HashSet();
HashSet blackListSet = new HashSet();
int DELAY_MODE = DEF_DELAY_MODE;
int FIXED_LIKE_DELAY_MS = DEF_FIXED_LIKE_DELAY_MS;
boolean REFRESH_ENABLE = DEF_REFRESH_ENABLE;
int REFRESH_MODE = DEF_REFRESH_MODE;
String REFRESH_START = DEF_REFRESH_START;
String REFRESH_END = DEF_REFRESH_END;
boolean SCHEDULE_ENABLE = DEF_SCHEDULE_ENABLE;
String SCHEDULE_START = DEF_SCHEDULE_START;
String SCHEDULE_END = DEF_SCHEDULE_END;
boolean SKIP_TEXT = false;
boolean SKIP_IMAGE = false;
boolean SKIP_VIDEO = false;
String SKIP_KEYWORDS_RAW = "";
boolean KEYWORD_FILTER_TEXT = true;
boolean KEYWORD_FILTER_IMAGE = true;
boolean KEYWORD_FILTER_VIDEO = true;
HashSet skipKeywordSet = new HashSet();
int UNKNOWN_TIME_POLICY = DEF_UNKNOWN_TIME_POLICY;
int UNKNOWN_TYPE_POLICY = DEF_UNKNOWN_TYPE_POLICY;
boolean LOG_ENABLE = false;
int LOG_MAX = DEF_LOG_MAX;
boolean SNS_NOTIFY_ENABLE = true;
boolean SNS_NOTIFY_TOAST = true;
String SNS_NOTIFY_LIST_RAW = "";
String SNS_NOTIFY_TITLE_TPL = "";
String SNS_NOTIFY_BODY_TPL = "";
String SNS_NOTIFY_TOAST_TPL = "";
HashSet snsNotifySet = new HashSet();
int MAX_PROCESSED_SAVE = DEF_MAX_PROCESSED_SAVE;

HashSet processedIds = new HashSet();
HashSet pendingIds = new HashSet();
HashSet canceledIds = new HashSet();
HashSet notifiedSnsIds = new HashSet();
java.util.HashMap recentTriggerTs = new java.util.HashMap();
Object dedupLock = new Object();
long lastDiagLogTs = 0L;
long lastDispatcherLogTs = 0L;
long refreshCollectStartMs = 0L;
long refreshCollectEndMs = 0L;
int refreshCollectCount = 0;
long refreshCollectMinCreateSec = 0L;
long refreshCollectMaxCreateSec = 0L;
Object refreshLogLock = new Object();
int DUP_SUPPRESS_MS = DEF_DUP_SUPPRESS_MS;

HandlerThread ghostThread = null;
Handler ghostHandler = null;

boolean started = false;
String CFG_KEY_PROCESSED = "auto_like_processed_ids_v1";
int wxMinor = -1;
long snsNotifyStartSec = 0L;

Object dispatcher = null;    // 网络调度器

java.lang.reflect.Method cachedSnsGetterL4 = null;      // l4 的静态入口方法，如 gi/li/ni...
java.lang.reflect.Method cachedSnsGetterStore = null;   // store 上的取帖方法，如 w0/O/G0...
Class cachedQ2Class = null;                             // NetSceneSnsObjectOp 动态类
ArrayList cachedRefreshSceneClasses = null;             // 刷新链路动态类池
List sCachedFriendNames = null;
List sCachedFriendIds = null;

int clampInt(int v, int min, int max, int def) {
    try {
        if (v < min || v > max) return def;
        return v;
    } catch (Throwable ignored) {}
    return def;
}

int parseIntSafe(String s, int def) {
    try {
        if (s == null) return def;
        s = s.trim();
        if (s.length() == 0) return def;
        return Integer.parseInt(s);
    } catch (Throwable ignored) {}
    return def;
}

HashSet parseSeparatedSet(String raw, boolean lower) {
    HashSet set = new HashSet();
    try {
        if (raw == null) return set;
        String[] arr = raw.replace('\n', ',').replace('，', ',').replace(';', ',').replace('；', ',').split(",");
        for (int i = 0; i < arr.length; i++) {
            String s = arr[i] == null ? "" : arr[i].trim();
            if (s.length() > 0) set.add(lower ? s.toLowerCase() : s);
        }
    } catch (Throwable ignored) {}
    return set;
}

HashSet parseWxidSet(String raw) { return parseSeparatedSet(raw, false); }
HashSet parseKeywordSet(String raw) { return parseSeparatedSet(raw, true); }

String joinSet(HashSet set) {
    try {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Iterator it = set.iterator();
        while (it.hasNext()) {
            String s = String.valueOf(it.next()).trim();
            if (s.length() == 0) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        return sb.toString();
    } catch (Throwable ignored) {}
    return "";
}

String decodeBytesSafe(byte[] b, String charset) {
    try {
        if (b == null || b.length == 0) return "";
        return new String(b, charset);
    } catch (Throwable ignored) {}
    return "";
}
String extractXmlLikeTag(String src, String tag) {
    try {
        if (src == null || tag == null) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?is)<" + java.util.regex.Pattern.quote(tag) + "[^>]*>(?:<!\\[CDATA\\[(.*?)\\]\\]>|(.*?))</" + java.util.regex.Pattern.quote(tag) + ">");
        java.util.regex.Matcher m = p.matcher(src);
        if (m.find()) {
            String v = m.group(1);
            if (v == null) v = m.group(2);
            return v == null ? "" : v;
        }
    } catch (Throwable ignored) {}
    return "";
}

String sanitizeReadableText(String s) {
    try {
        if (s == null) return "";
        s = s.replace("&lt;", "<");
        s = s.replace("&gt;", ">");
        s = s.replace("&amp;", "&");
        s = s.replace("&quo" + "t;", String.valueOf((char)34));
        s = s.replace("&apo" + "s;", "'");
        s = s.replaceAll("(?is)<[^>]+>", " ");
        s = s.replace('\u0000', ' ');
        s = s.replace('\uFFFD', ' ');
        s = s.replaceAll("[\\p{Cntrl}]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        // 去掉 protobuf/二进制残留导致的末尾孤立数字/字段尾巴，例如正文后面的 type=2 或 2&
        s = s.replaceAll("[\\s　]*[0-9]{1,3}\\s*&\\s*$", "");
        s = s.replaceAll("[\\s　]+[0-9]{1,3}$", "");
        s = s.replaceAll("([。！？!?…，,；;：:\\)）】》'\"\\s])[0-9]{1,3}$", "$1");
        s = s.replaceAll("([\\u4e00-\\u9fa5])[0-9]{1,3}$", "$1");
        s = s.replaceAll("[。．·•\\.\\s]+$", "");
        s = s.trim();
        return s;
    } catch (Throwable ignored) {}
    return "";
}

int cjkCount(String s) {
    int n = 0;
    try {
        if (s == null) return 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)) n++;
        }
    } catch (Throwable ignored) {}
    return n;
}
String cleanupLeadingNoiseForCaption(String s) {
    try {
        if (s == null) return "";
        String x = s.trim();
        // 循环剥离 protobuf 残留的段首噪声：单个英文字母、1~3位字段数字、结构符号。
        // 保护常见正文开头：多位数字(如520/2026)、连续英文单词/缩写。
        for (int k = 0; k < 8; k++) {
            String old = x;
            x = x.replaceAll("^[\\s　]+", "");
            x = x.replaceAll("^[\\*\\+\\-_=#@&%$!?,，。．:：;；·•|/\\\\\\)\\(\\]\\[\\{\\}<>《》【】、~～^`'\"“”‘’]+", "");
            // 去单个英文字母噪声：G 文案、H🍤文案；不动 Hi/ABC 这类连续英文开头
            x = x.replaceAll("^[A-Za-z](?=(?:[\\s　]+|[^A-Za-z0-9]))", "");
            x = x.replaceAll("^[\\s　]+", "");
            x = x.replaceAll("^[\\*\\+\\-_=#@&%$!?,，。．:：;；·•|/\\\\\\)\\(\\]\\[\\{\\}<>《》【】、~～^`'\"“”‘’]+", "");
            // 去短字段数字：2正文、8🥐正文、12|正文；不动 520今天、2026年 这类多位数字正文
            x = x.replaceAll("^[0-9]{1,2}(?=(?:[\\s　]*[^0-9A-Za-z]))", "");
            x = x.trim();
            if (x.equals(old)) break;
        }
        return x.trim();
    } catch (Throwable ignored) {}
    return s == null ? "" : s;
}

String cleanCaptionText(String s) {
    try {
        if (s == null) return "";
        String x = s.trim();
        // 去掉 protobuf 段首结构符号，例如 *)正文、)正文、+正文；更复杂的段首噪声交给 cleanupLeadingNoiseForCaption
        x = cleanupLeadingNoiseForCaption(x);
        // 去掉段首字段数字，例如 5🥐正文、8没有照片；只处理单个数字，避免误删“520...”这类正文
        x = x.replaceAll("^[0-9](?=[^\\u4e00-\\u9fa5A-Za-z0-9])", "");
        x = x.replaceAll("^[0-9](?=[\\u4e00-\\u9fa5\\u3400-\\u4dbf])", "");
        x = cleanupLeadingNoiseForCaption(x);
        // 去掉段尾字段残留，例如 正文。2& / 正文。2 / 正文9 ·◇
        x = x.replaceAll("[\\s　]*[0-9]{1,3}\\s*&\\s*$", "");
        x = x.replaceAll("[\\s　]*[0-9]{1,3}[\\s　]*(?:[·•◇◆✧✦⟡˚₊+\\-~～。．\\.]\\s*)+$", "");
        x = x.replaceAll("[\\s　]*[0-9]{1,3}\\s*$", "");
        return x.trim();
    } catch (Throwable ignored) {}
    return s == null ? "" : s;
}

String extractCaptionAfterWxidBeforeType(String raw) {
    try {
        if (raw == null || raw.length() == 0) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("wxid_[A-Za-z0-9_\\-]+\\s+(.{1,180}?)[0-9]{1,3}\\s*&", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(raw);
        if (m.find()) {
            String v = m.group(1);
            if (v != null) {
                v = cleanCaptionText(v);
                if (cjkCount(v) >= 1 && v.length() <= 120) return v;
            }
        }
    } catch (Throwable ignored) {}
    return "";
}

String extractLikelyCaptionFromRaw(String raw) {
    try {
        if (raw == null || raw.length() == 0) return "";
        String direct = extractCaptionAfterWxidBeforeType(raw);
        if (direct.length() > 0) return direct;
        // 不先做强清洗，保留 emoji；仅做轻度切分和噪声剔除
        String x = raw.replace('\u0000', ' ').replace('\uFFFD', ' ');
        x = x.replaceAll("wxid_[A-Za-z0-9_\\-]+", " ")
             .replaceAll("https?://\\S+", " ")
             .replaceAll("[A-Fa-f0-9]{24,}", " ")
             .replaceAll("\\d{12,}", " ");

        // 按明显分隔符切段，挑中文密度高且长度适中的段
        String[] parts = x.split("[\\r\\n]+|\\s{2,}|[\\\"'`|]+|\\*+|@+");
        String best = "";
        int bestScore = -999999;
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p == null) continue;
            p = p.replaceAll("\\s+", " ").trim();
            p = cleanCaptionText(p);
            if (p.length() < 4 || p.length() > 120) continue;

            int cn = cjkCount(p);
            if (cn < 2) continue;

            // 惩罚明显元数据片段
            int penalty = 0;
            if (p.matches(".*[A-Za-z0-9_\\-]{10,}.*")) penalty += 20;
            if (p.matches(".*\\d{6,}.*")) penalty += 20;
            if (p.indexOf("网抑云") >= 0) penalty += 10;

            int score = cn * 12 - Math.abs(p.length() - cn) - penalty;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best.length() > 0) return cleanCaptionText(best);
    } catch (Throwable ignored) {}
    return "";
}

String pickReadableSnsText(String raw) {
    try {
        raw = sanitizeReadableText(raw);
        if (raw.length() == 0) return "";

        // 优先：从原始文本中提取最像正文的片段（保留 emoji）
        String likely = extractLikelyCaptionFromRaw(raw);
        if (likely.length() > 0) {
            return likely;
        }

        String[] tags = {"contentDesc", "content", "description", "desc", "title"};
        for (int i = 0; i < tags.length; i++) {
            String tag = tags[i];
            String v0 = extractXmlLikeTag(raw, tag);
            String v = sanitizeReadableText(v0);
            if (cjkCount(v) >= 2 && v.length() <= 300) {
                return v;
            }
        }

        // protobuf/二进制混杂内容兜底：按不可读字符、wxid、长数字等切段，挑中文最多且不像ID的一段
        String cut = raw.replaceAll("wxid_[A-Za-z0-9_\\-]+", " ")
                        .replaceAll("[A-Za-z0-9_\\-]{12,}", " ")
                        .replaceAll("\\d{10,}", " ")
                        .replaceAll("[^\\u4e00-\\u9fa5\\u3400-\\u4dbfA-Za-z0-9，。！？、；：,.!?;:'\"（）()《》【】\\[\\]\\s]+", " ");
        String[] parts = cut.split("\\s{2,}|[\\r\\n]+");
        String best = "";
        int bestScore = 0;
        for (int i = 0; i < parts.length; i++) {
            String p = sanitizeReadableText(parts[i]);
            if (p.length() == 0 || p.length() > 200) continue;
            int cn = cjkCount(p);
            if (cn < 2) continue;
            int score = cn * 10 - Math.abs(p.length() - cn);
            if (score > bestScore) { bestScore = score; best = p; }
if (best.length() > 0) {
            return sanitizeReadableText(best);
        }

        if (cjkCount(raw) >= 2 && raw.length() <= 160) {
            return sanitizeReadableText(raw);
        }
        }
    } catch (Throwable ignored) {}
    return "";
}

String extractPostText(ContentValues cv) {
    try {
        if (cv == null) return "";
        Object obj = cv.get("content");
        if (obj instanceof byte[]) return pickReadableSnsText(decodeBytesSafe((byte[]) obj, "UTF-8"));
        if (obj != null) return pickReadableSnsText(String.valueOf(obj));
    } catch (Throwable ignored) {}
    return "";
}
boolean isKeywordFilterEnabledForType(int t) {
    try {
        if (t == 0) return KEYWORD_FILTER_TEXT;
        if (t == 1) return KEYWORD_FILTER_IMAGE;
        if (t == 2) return KEYWORD_FILTER_VIDEO;
    } catch (Throwable ignored) {}
    return false;
}

boolean isPostKeywordAllowed(ContentValues cv, int postType) {
    try {
        if (skipKeywordSet == null || skipKeywordSet.isEmpty()) return true;
        if (!isKeywordFilterEnabledForType(postType)) return true;
        String text = extractPostText(cv);
        if (text == null || text.length() == 0) return true;
        String low = text.toLowerCase();
        Iterator it = skipKeywordSet.iterator();
        while (it.hasNext()) {
            String kw = String.valueOf(it.next());
            if (kw.length() > 0 && low.contains(kw)) return false;
        }
    } catch (Throwable ignored) {}
    return true;
}

boolean hasValidAutoLikeTargetConfig() {
    try {
        if (LIST_MODE == 1) {
            // 黑名单模式风险较高：黑名单为空时绝不运行，避免等价于全员点赞
            return blackListSet != null && !blackListSet.isEmpty();
        }
        // 白名单模式：必须明确选择至少一个白名单好友，否则不运行
        return whiteListSet != null && !whiteListSet.isEmpty();
    } catch (Throwable ignored) {}
    return false;
}

boolean shouldAutoLikeUser(String userName) {
    try {
        if (userName == null || userName.length() == 0) return false;
        if (LIST_MODE == 1) {
            // 安全保护：黑名单为空时不默认点赞所有人，避免新装/误配置后批量点赞
            if (blackListSet == null || blackListSet.isEmpty()) return false;
            return !blackListSet.contains(userName);
        }
        return whiteListSet != null && whiteListSet.contains(userName); // 白名单模式：只点白名单
    } catch (Throwable ignored) {}
    return false;
}

long extractPostCreateTimeSec(ContentValues cv) {
    if (cv == null) return 0L;
    String[] keys = {"createTime", "field_createTime", "create_time", "timestamp", "field_timestamp"};
    for (int i = 0; i < keys.length; i++) {
        try {
            Long v = cv.getAsLong(keys[i]);
            if (v != null && v.longValue() > 0) return v.longValue();
        } catch (Throwable ignored) {}
    }
    return 0L;
}
boolean isPostInAllowedAge(ContentValues cv) {
    try {
        if (MAX_POST_AGE_HOURS <= 0) return true;
        long sec = extractPostCreateTimeSec(cv);
        if (sec <= 0) return UNKNOWN_TIME_POLICY == 1; // 取不到时间按配置处理
        long nowSec = System.currentTimeMillis() / 1000L;
        long age = nowSec - sec;
        return age >= 0 && age <= MAX_POST_AGE_HOURS * 3600L;
    } catch (Throwable ignored) {}
    return false;
}

int parseTimeToMinute(String hhmm) {
    try {
        if (hhmm == null) return -1;
        String[] p = hhmm.trim().split(":");
        if (p.length != 2) return -1;
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
        return h * 60 + m;
    } catch (Throwable ignored) {}
    return -1;
}

String normalizeTime(String v, String def) {
    int t = parseTimeToMinute(v);
    if (t < 0) return def;
    int h = t / 60;
    int m = t % 60;
    return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
}

boolean isNowInWindow(String start, String end) {
    try {
        int s = parseTimeToMinute(start);
        int e = parseTimeToMinute(end);
        if (s < 0 || e < 0) return true;
        java.util.Calendar c = java.util.Calendar.getInstance();
        int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        if (s == e) return true;
        if (s < e) {
            if (now >= s) {
                if (now < e) return true;
            }
            return false;
        }
        if (now >= s) return true;
        if (now < e) return true;
        return false;
    } catch (Throwable ignored) {}
    return true;
}

boolean isScheduleAllowed() {
    if (!SCHEDULE_ENABLE) return true;
    return isNowInWindow(SCHEDULE_START, SCHEDULE_END);
}

boolean shouldRunFixedRefreshNow() {
    try {
        if (REFRESH_MODE != 1) return true;
        return isNowInWindow(REFRESH_START, REFRESH_END);
    } catch (Throwable ignored) {}
    return false;
}

int nextLikeDelayMs() {
    try {
        if (DELAY_MODE == 0) return FIXED_LIKE_DELAY_MS;
        int min = Math.min(MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS);
        int max = Math.max(MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS);
        if (max <= min) return min;
        return min + new java.util.Random().nextInt(max - min + 1);
    } catch (Throwable ignored) {}
    return MIN_LIKE_DELAY_MS;
}
void logMsg(String msg) { logToStore(CFG_LIKE_LOGS, msg); }
void logSuccess(String msg) { logToStore(CFG_LIKE_SUCCESS_LOGS, "[点赞成功] " + msg); }
void logSkip(String msg) { logToStore(CFG_LIKE_SKIP_LOGS, "[跳过点赞] " + msg); }
void logRefresh(String msg) { logToStore(CFG_REFRESH_LOGS, "[刷新记录] " + msg); }

String formatTimeHM(long sec) {
    try {
        if (sec <= 0) return "未知";
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(sec * 1000L));
    } catch (Throwable ignored) {}
    return "未知";
}

void recordRefreshObservedPost(ContentValues cv) {
    try {
        long now = System.currentTimeMillis();
        synchronized (refreshLogLock) {
            if (refreshCollectStartMs <= 0 || now < refreshCollectStartMs || now > refreshCollectEndMs) return;
            refreshCollectCount++;
            long sec = extractPostCreateTimeSec(cv);
            if (sec > 0) {
                if (refreshCollectMinCreateSec <= 0 || sec < refreshCollectMinCreateSec) refreshCollectMinCreateSec = sec;
                if (refreshCollectMaxCreateSec <= 0 || sec > refreshCollectMaxCreateSec) refreshCollectMaxCreateSec = sec;
            }
        }
    } catch (Throwable ignored) {}
}

void beginRefreshLogWindow() {
    try {
        if (!LOG_ENABLE) return;
        final long start = System.currentTimeMillis();
        synchronized (refreshLogLock) {
            refreshCollectStartMs = start;
            refreshCollectEndMs = start + 12000L;
            refreshCollectCount = 0;
            refreshCollectMinCreateSec = 0L;
            refreshCollectMaxCreateSec = 0L;
        }
        if (ghostHandler != null) {
            ghostHandler.postDelayed(new Runnable(){ public void run(){ finishRefreshLogWindow(start); }}, 12500L);
        }
    } catch (Throwable ignored) {}
}

void finishRefreshLogWindow(long startMs) {
    try {
        if (!LOG_ENABLE) return;
        int cnt = 0; long minSec = 0L; long maxSec = 0L;
        synchronized (refreshLogLock) {
            if (refreshCollectStartMs != startMs) return;
            cnt = refreshCollectCount;
            minSec = refreshCollectMinCreateSec;
            maxSec = refreshCollectMaxCreateSec;
            refreshCollectStartMs = 0L;
            refreshCollectEndMs = 0L;
        }
        String at = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(startMs));
        String range = cnt > 0 ? (formatTimeHM(minSec) + " 到 " + formatTimeHM(maxSec)) : "无新增";
        logRefresh(at + " 执行后台刷新，新增朋友圈 " + cnt + " 条" + (cnt > 0 ? "，发布时间范围：" + range : ""));
    } catch (Throwable ignored) {}
}



void appendPluginLogFile(String msg) {
    try {
        String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + "  " + msg + "\n";
        String[] paths = {
            "/storage/emulated/999/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log",
            "/sdcard/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log",
            "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log"
        };
        for (int i = 0; i < paths.length; i++) {
            try {
                java.io.File f = new java.io.File(paths[i]);
                java.io.File p = f.getParentFile();
                if (p != null && p.exists()) {
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.write(line);
                    fw.close();
                    return;
                }
            } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
}

void clearPluginLogFiles() {
    try {
        String[] paths = {
            "/storage/emulated/999/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log",
            "/sdcard/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log",
            "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/自动点赞/plugin.log"
        };
        for (int i = 0; i < paths.length; i++) {
            try {
                java.io.File f = new java.io.File(paths[i]);
                if (f.exists()) {
                    java.io.FileWriter fw = new java.io.FileWriter(f, false);
                    fw.write("");
                    fw.close();
                }
            } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {}
}

void logToStore(String key, String msg) {
    try {
        if (!LOG_ENABLE) return;
        try { log(msg); } catch (Throwable ignored) {}
        appendPluginLogFile(msg);
        // 同时写入内置日志存储，供日志查看器显示
        String old = getString(key, "");
        String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + "  " + msg;
        String all = line + (old == null || old.length() == 0 ? "" : "\n" + old);
        String[] arr = all.split("\n");
        StringBuilder sb = new StringBuilder();
        int max = LOG_MAX <= 0 ? DEF_LOG_MAX : LOG_MAX;
        for (int i = 0; i < arr.length && i < max; i++) {
            if (i > 0) sb.append("\n");
            sb.append(arr[i]);
        }
        putString(key, sb.toString());
    } catch (Throwable ignored) {}
}



int detectPostType(ContentValues cv) {
    try {
        if (cv == null) return -1;
        Object x = cv.get("type");
        if (x instanceof Number) {
            int t = ((Number)x).intValue();
            if (t == 0x2) return 0;
            if (t == 0x1) return 1;
            if (t == 0xF) return 2;
        }
    } catch (Throwable ignored) {}
    return -1;
}

boolean isPostTypeAllowed(int t) {
    if (t < 0) return UNKNOWN_TYPE_POLICY == 1;
    return !((t == 0 && SKIP_TEXT) || (t == 1 && SKIP_IMAGE) || (t == 2 && SKIP_VIDEO));
}
String postTypeName(int t) { if (t == 0) return "文字"; if (t == 1) return "图文"; if (t == 2) return "视频"; return "未知"; }

Context getToastContextSafe() {
    try {
        Activity a = getTopActivity();
        if (a != null) return a.getApplicationContext();
    } catch (Throwable ignored) {}
    try {
        Class at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method m = at.getDeclaredMethod("currentApplication", new Class[0]);
        Object app = m.invoke(null, new Object[0]);
        if (app != null) return ((Context) app).getApplicationContext();
    } catch (Throwable ignored) {}
    try { if (hostContext != null) return hostContext.getApplicationContext(); } catch (Throwable ignored) {}
    return null;
}

void nativeToast(final String msg) {
    try {
        if (msg == null || msg.length() == 0) return;
        new Handler(Looper.getMainLooper()).post(new Runnable(){ public void run(){
            try {
                Context c = getToastContextSafe();
                if (c != null) Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
                else toast(msg);
            } catch (Throwable e) { try { toast(msg); } catch (Throwable ignored) {} }
        }});
    } catch (Throwable ignored) {}
}

String getFriendDisplayNameSafe(String wxid) {
    try {
        if (wxid == null || wxid.length() == 0) return "未知好友";
        String r = getFriendRemarkName(wxid);
        if (r != null && r.trim().length() > 0) return r.trim();
    } catch (Throwable ignored) {}
    try {
        if (sCachedFriendIds != null && sCachedFriendNames != null) {
            for (int i = 0; i < sCachedFriendIds.size(); i++) {
                if (wxid.equals(String.valueOf(sCachedFriendIds.get(i)))) return String.valueOf(sCachedFriendNames.get(i));
            }
        }
    } catch (Throwable ignored) {}
    try { String n = getFriendName(wxid); if (n != null && n.length() > 0) return n; } catch (Throwable ignored) {}
    return wxid;
}

void ensureSnsNotifyChannel(NotificationManager nm, String channelId) {
    if (nm == null || Build.VERSION.SDK_INT < 26 || channelId == null) return;
    try {
        NotificationChannel ch = nm.getNotificationChannel(channelId);
        if (ch == null) {
            ch = new NotificationChannel(channelId, "朋友圈发布通知", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 250, 180, 250});
            nm.createNotificationChannel(ch);
        }
    } catch (Throwable ignored) {}
}

Intent[] buildSnsOpenIntents() {
    Intent home = null;
    Intent sns = null;
    try {
        home = new Intent();
        home.setComponent(new ComponentName(hostContext.getPackageName(), "com.tencent.mm.ui.LauncherUI"));
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    } catch (Throwable ignored) {}
    if (home == null) {
        try {
            home = hostContext.getPackageManager().getLaunchIntentForPackage(hostContext.getPackageName());
            if (home != null) home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } catch (Throwable ignored) {}
    }
    try {
        sns = new Intent();
        sns.setComponent(new ComponentName(hostContext.getPackageName(), "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI"));
        sns.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    } catch (Throwable ignored) {}
    if (home != null && sns != null) return new Intent[]{home, sns};
    if (sns != null) return new Intent[]{sns};
    if (home != null) return new Intent[]{home};
    return null;
}

String applySnsNotifyTemplate(String tpl, String sender, String wxid, long snsId, String content, int postType) {
    try {
        if (tpl == null || tpl.length() == 0) return "";
        String clean = content == null ? "" : sanitizeReadableText(content);
        if (clean.length() > 120) clean = clean.substring(0, 120) + "...";
        return tpl.replace("%sender%", sender == null ? "" : sender)
                  .replace("%wxid%", wxid == null ? "" : wxid)
                  .replace("%snsid%", String.valueOf(snsId))
                  .replace("%type%", postTypeName(postType))
                  .replace("%content%", clean);
    } catch (Throwable ignored) {}
    return "";
}

String defaultSnsNotifyBody(String name, String content, int postType) {
    String body = name + " 发布了" + postTypeName(postType) + "朋友圈";
    try {
        if (content != null && content.trim().length() > 0) {
            String clean = sanitizeReadableText(content);
            if (clean.length() > 80) clean = clean.substring(0, 80) + "...";
            if (cjkCount(clean) >= 2) body = body + "：" + clean;
        }
    } catch (Throwable ignored) {}
    return body;
}

void sendSnsPostNotification(String userName, long snsId, String content, int postType) {
    try {
        NotificationManager nm = (NotificationManager) hostContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String channelId = "sns_post_notify_v1";
        ensureSnsNotifyChannel(nm, channelId);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(hostContext, channelId) : new Notification.Builder(hostContext);
        String name = getFriendDisplayNameSafe(userName);
        String title = applySnsNotifyTemplate(SNS_NOTIFY_TITLE_TPL, name, userName, snsId, content, postType);
        if (title.length() == 0) title = "📣 指定好友发布朋友圈";
        String body = applySnsNotifyTemplate(SNS_NOTIFY_BODY_TPL, name, userName, snsId, content, postType);
        if (body.length() == 0) body = defaultSnsNotifyBody(name, content, postType);
        b.setContentTitle(title).setContentText(body).setSmallIcon(android.R.drawable.stat_notify_chat).setAutoCancel(true).setOnlyAlertOnce(false);
        try { b.setCategory(Notification.CATEGORY_MESSAGE); } catch (Throwable ignored) {}
        try { b.setPriority(Notification.PRIORITY_HIGH); } catch (Throwable ignored) {}
        try { b.setVisibility(Notification.VISIBILITY_PRIVATE); } catch (Throwable ignored) {}
        try { b.setWhen(System.currentTimeMillis()); b.setShowWhen(true); } catch (Throwable ignored) {}
        try { b.setStyle(new Notification.BigTextStyle().bigText(body).setBigContentTitle(title).setSummaryText("朋友圈通知")); } catch (Throwable ignored) {}
        Intent[] opens = buildSnsOpenIntents();
        if (opens != null && opens.length > 0) b.setContentIntent(PendingIntent.getActivities(hostContext, ("sns_" + snsId).hashCode(), opens, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        nm.notify(("sns_post_notify_v1_" + snsId).hashCode(), b.build());
    } catch (Throwable e) { logMsg("[朋友圈通知] 系统通知失败: " + e); }
}

void notifySnsPostIfNeeded(String userName, long snsId, ContentValues cv) {
    try {
        if (!SNS_NOTIFY_ENABLE && !SNS_NOTIFY_TOAST) return;
        if (userName == null || userName.length() == 0 || snsId == 0) return;
        if (snsNotifySet == null || snsNotifySet.isEmpty() || !snsNotifySet.contains(userName)) return;
        long createSec = extractPostCreateTimeSec(cv);
        if (snsNotifyStartSec > 0 && createSec > 0 && createSec < snsNotifyStartSec - 30) {
            return;
        }
        if (createSec <= 0) {
            return;
        }
        Long id = Long.valueOf(snsId);
        synchronized (dedupLock) {
            if (notifiedSnsIds.contains(id)) return;
            notifiedSnsIds.add(id);
            if (notifiedSnsIds.size() > 1000) notifiedSnsIds.clear();
        }
        int postType = detectPostType(cv);
        String text = extractPostText(cv);
        String name = getFriendDisplayNameSafe(userName);
        String toastMsg = applySnsNotifyTemplate(SNS_NOTIFY_TOAST_TPL, name, userName, snsId, text, postType);
        if (toastMsg.length() == 0) toastMsg = "📣 " + name + " 发布了" + postTypeName(postType) + "朋友圈";
        if (SNS_NOTIFY_TOAST) nativeToast(toastMsg);
        if (SNS_NOTIFY_ENABLE) sendSnsPostNotification(userName, snsId, text, postType);
    } catch (Throwable e) { logMsg("[朋友圈通知异常] " + e); }
}

void loadRuntimeConfig() {
    try {
        AUTO_LIKE_ENABLE = getBoolean(CFG_ENABLE, DEF_AUTO_LIKE_ENABLE);
        REFRESH_INTERVAL = clampInt(getInt(CFG_REFRESH_INTERVAL, DEF_REFRESH_INTERVAL), 1000, 3600000, DEF_REFRESH_INTERVAL);
        int oldDelay = DEF_MIN_LIKE_DELAY_MS;
        MIN_LIKE_DELAY_MS = clampInt(getInt(CFG_MIN_LIKE_DELAY_MS, oldDelay), 0, 7200000, DEF_MIN_LIKE_DELAY_MS);
        MAX_LIKE_DELAY_MS = clampInt(getInt(CFG_MAX_LIKE_DELAY_MS, DEF_MAX_LIKE_DELAY_MS), 0, 7200000, DEF_MAX_LIKE_DELAY_MS);
        if (MAX_LIKE_DELAY_MS < MIN_LIKE_DELAY_MS) MAX_LIKE_DELAY_MS = MIN_LIKE_DELAY_MS;
        MAX_POST_AGE_HOURS = clampInt(getInt(CFG_MAX_POST_AGE_HOURS, DEF_MAX_POST_AGE_HOURS), 1, 168, DEF_MAX_POST_AGE_HOURS);
        LIST_MODE = clampInt(getInt(CFG_LIST_MODE, 0), 0, 1, 0);
        WHITE_LIST_RAW = getString(CFG_WHITE_LIST, "");
        BLACK_LIST_RAW = getString(CFG_BLACK_LIST, "");
        whiteListSet = parseWxidSet(WHITE_LIST_RAW);
        blackListSet = parseWxidSet(BLACK_LIST_RAW);
        DELAY_MODE = clampInt(getInt(CFG_DELAY_MODE, DEF_DELAY_MODE), 0, 1, DEF_DELAY_MODE);
        FIXED_LIKE_DELAY_MS = clampInt(getInt(CFG_FIXED_LIKE_DELAY_MS, DEF_FIXED_LIKE_DELAY_MS), 0, 7200000, DEF_FIXED_LIKE_DELAY_MS);
        REFRESH_ENABLE = getBoolean(CFG_REFRESH_ENABLE, DEF_REFRESH_ENABLE);
        REFRESH_MODE = clampInt(getInt(CFG_REFRESH_MODE, DEF_REFRESH_MODE), 0, 1, DEF_REFRESH_MODE);
        REFRESH_START = normalizeTime(getString(CFG_REFRESH_START, DEF_REFRESH_START), DEF_REFRESH_START);
        REFRESH_END = normalizeTime(getString(CFG_REFRESH_END, DEF_REFRESH_END), DEF_REFRESH_END);
        SCHEDULE_ENABLE = getBoolean(CFG_SCHEDULE_ENABLE, DEF_SCHEDULE_ENABLE);
        SCHEDULE_START = normalizeTime(getString(CFG_SCHEDULE_START, DEF_SCHEDULE_START), DEF_SCHEDULE_START);
        SCHEDULE_END = normalizeTime(getString(CFG_SCHEDULE_END, DEF_SCHEDULE_END), DEF_SCHEDULE_END);
        SKIP_TEXT = getBoolean(CFG_SKIP_TEXT, false);
        SKIP_IMAGE = getBoolean(CFG_SKIP_IMAGE, false);
        SKIP_VIDEO = getBoolean(CFG_SKIP_VIDEO, false);
        SKIP_KEYWORDS_RAW = getString(CFG_SKIP_KEYWORDS, "");
        KEYWORD_FILTER_TEXT = getBoolean(CFG_KEYWORD_FILTER_TEXT, true);
        KEYWORD_FILTER_IMAGE = getBoolean(CFG_KEYWORD_FILTER_IMAGE, true);
        KEYWORD_FILTER_VIDEO = getBoolean(CFG_KEYWORD_FILTER_VIDEO, true);
        skipKeywordSet = parseKeywordSet(SKIP_KEYWORDS_RAW);
        UNKNOWN_TIME_POLICY = clampInt(getInt(CFG_UNKNOWN_TIME_POLICY, DEF_UNKNOWN_TIME_POLICY), 0, 1, DEF_UNKNOWN_TIME_POLICY);
        UNKNOWN_TYPE_POLICY = clampInt(getInt(CFG_UNKNOWN_TYPE_POLICY, DEF_UNKNOWN_TYPE_POLICY), 0, 1, DEF_UNKNOWN_TYPE_POLICY);
        LOG_ENABLE = getBoolean(CFG_LOG_ENABLE, false);
        LOG_MAX = clampInt(getInt(CFG_LOG_MAX, DEF_LOG_MAX), 20, 2000, DEF_LOG_MAX);
        SNS_NOTIFY_ENABLE = getBoolean(CFG_SNS_NOTIFY_ENABLE, true);
        SNS_NOTIFY_TOAST = getBoolean(CFG_SNS_NOTIFY_TOAST, true);
        SNS_NOTIFY_LIST_RAW = getString(CFG_SNS_NOTIFY_LIST, "");
        SNS_NOTIFY_TITLE_TPL = getString(CFG_SNS_NOTIFY_TITLE_TPL, "");
        SNS_NOTIFY_BODY_TPL = getString(CFG_SNS_NOTIFY_BODY_TPL, "");
        SNS_NOTIFY_TOAST_TPL = getString(CFG_SNS_NOTIFY_TOAST_TPL, "");
        snsNotifySet = parseWxidSet(SNS_NOTIFY_LIST_RAW);
        MAX_PROCESSED_SAVE = clampInt(getInt(CFG_MAX_PROCESSED_SAVE, DEF_MAX_PROCESSED_SAVE), 20, 5000, DEF_MAX_PROCESSED_SAVE);
        DUP_SUPPRESS_MS = clampInt(getInt(CFG_DUP_SUPPRESS_MS, DEF_DUP_SUPPRESS_MS), 1000, 600000, DEF_DUP_SUPPRESS_MS);
    } catch (Throwable e) {
        logMsg("[配置] 加载失败: " + e);
    }
}

void saveAdvancedConfig() {
    try {
        putInt(CFG_DELAY_MODE, DELAY_MODE);
        putInt(CFG_FIXED_LIKE_DELAY_MS, FIXED_LIKE_DELAY_MS);
        putBoolean(CFG_REFRESH_ENABLE, REFRESH_ENABLE);
        putInt(CFG_REFRESH_MODE, REFRESH_MODE);
        putString(CFG_REFRESH_START, normalizeTime(REFRESH_START, DEF_REFRESH_START));
        putString(CFG_REFRESH_END, normalizeTime(REFRESH_END, DEF_REFRESH_END));
        putBoolean(CFG_SCHEDULE_ENABLE, SCHEDULE_ENABLE);
        putString(CFG_SCHEDULE_START, normalizeTime(SCHEDULE_START, DEF_SCHEDULE_START));
        putString(CFG_SCHEDULE_END, normalizeTime(SCHEDULE_END, DEF_SCHEDULE_END));
        putBoolean(CFG_SKIP_TEXT, SKIP_TEXT);
        putBoolean(CFG_SKIP_IMAGE, SKIP_IMAGE);
        putBoolean(CFG_SKIP_VIDEO, SKIP_VIDEO);
        putString(CFG_SKIP_KEYWORDS, SKIP_KEYWORDS_RAW == null ? "" : SKIP_KEYWORDS_RAW.trim());
        putBoolean(CFG_KEYWORD_FILTER_TEXT, KEYWORD_FILTER_TEXT);
        putBoolean(CFG_KEYWORD_FILTER_IMAGE, KEYWORD_FILTER_IMAGE);
        putBoolean(CFG_KEYWORD_FILTER_VIDEO, KEYWORD_FILTER_VIDEO);
        putInt(CFG_UNKNOWN_TIME_POLICY, DEF_UNKNOWN_TIME_POLICY);
        putInt(CFG_UNKNOWN_TYPE_POLICY, DEF_UNKNOWN_TYPE_POLICY);
        putBoolean(CFG_LOG_ENABLE, LOG_ENABLE);
        putInt(CFG_LOG_MAX, LOG_MAX);
        putBoolean(CFG_SNS_NOTIFY_ENABLE, SNS_NOTIFY_ENABLE);
        putBoolean(CFG_SNS_NOTIFY_TOAST, SNS_NOTIFY_TOAST);
        putString(CFG_SNS_NOTIFY_LIST, SNS_NOTIFY_LIST_RAW == null ? "" : SNS_NOTIFY_LIST_RAW.trim());
        putString(CFG_SNS_NOTIFY_TITLE_TPL, SNS_NOTIFY_TITLE_TPL == null ? "" : SNS_NOTIFY_TITLE_TPL.trim());
        putString(CFG_SNS_NOTIFY_BODY_TPL, SNS_NOTIFY_BODY_TPL == null ? "" : SNS_NOTIFY_BODY_TPL.trim());
        putString(CFG_SNS_NOTIFY_TOAST_TPL, SNS_NOTIFY_TOAST_TPL == null ? "" : SNS_NOTIFY_TOAST_TPL.trim());
    } catch (Throwable e) { logMsg("[配置] 保存高级配置失败: " + e); }
}

void saveRuntimeConfig(boolean enable, int mode, String whiteRaw, String blackRaw, int refreshMs, int minDelayMs, int maxDelayMs, int maxAgeHours, int maxSave, int dupMs) {
    try {
        refreshMs = clampInt(refreshMs, 1000, 3600000, DEF_REFRESH_INTERVAL);
        minDelayMs = clampInt(minDelayMs, 0, 7200000, DEF_MIN_LIKE_DELAY_MS);
        maxDelayMs = clampInt(maxDelayMs, 0, 7200000, DEF_MAX_LIKE_DELAY_MS);
        if (maxDelayMs < minDelayMs) maxDelayMs = minDelayMs;
        maxAgeHours = clampInt(maxAgeHours, 1, 168, DEF_MAX_POST_AGE_HOURS);
        maxSave = clampInt(maxSave, 20, 5000, DEF_MAX_PROCESSED_SAVE);
        dupMs = clampInt(dupMs, 1000, 600000, DEF_DUP_SUPPRESS_MS);
        putBoolean(CFG_ENABLE, enable);
        putInt(CFG_LIST_MODE, mode == 1 ? 1 : 0);
        putString(CFG_WHITE_LIST, whiteRaw == null ? "" : whiteRaw.trim());
        putString(CFG_BLACK_LIST, blackRaw == null ? "" : blackRaw.trim());
        putInt(CFG_REFRESH_INTERVAL, refreshMs);
        putInt(CFG_MIN_LIKE_DELAY_MS, minDelayMs);
        putInt(CFG_MAX_LIKE_DELAY_MS, maxDelayMs);
        putInt(CFG_MAX_POST_AGE_HOURS, maxAgeHours);
        putInt(CFG_MAX_PROCESSED_SAVE, maxSave);
        putInt(CFG_DUP_SUPPRESS_MS, dupMs);
        saveAdvancedConfig();
        loadRuntimeConfig();
    } catch (Throwable e) {
        logMsg("[配置] 保存失败: " + e);
    }
}

int detectWxMinorFromHost() {
    try {
        String vn = null;
        try { vn = String.valueOf(hostVerName); } catch (Throwable ignored) {}
        if (vn == null) return -1;
        String[] arr = vn.split("\\.");
        if (arr == null || arr.length < 3) return -1;
        return Integer.parseInt(arr[2]);
    } catch (Throwable ignored) {}
    return -1;
}

String snsIdKey(long snsId) {
    try {
        if (snsId < 0) return java.lang.Long.toUnsignedString(snsId);
    } catch (Throwable ignored) {}
    return String.valueOf(snsId);
}

boolean isSendRetOk(Object ret) {
    if (ret == null) return true; // void
    if (ret instanceof Boolean) return ((Boolean) ret).booleanValue();
    if (ret instanceof Number) return ((Number) ret).intValue() != 0;
    return true;
}

boolean fillDefaultArgs(Object[] args, Class[] pts, int fixedIndex, Object fixedValue) {
    for (int i = 0; i < pts.length; i++) {
        if (i == fixedIndex) {
            args[i] = fixedValue;
            continue;
        }
        Class p = pts[i];
        if (p == Integer.TYPE || p == Short.TYPE || p == Byte.TYPE) args[i] = Integer.valueOf(0);
        else if (p == Long.TYPE) args[i] = Long.valueOf(0L);
        else if (p == Boolean.TYPE) args[i] = Boolean.FALSE;
        else if (p == Float.TYPE) args[i] = Float.valueOf(0f);
        else if (p == Double.TYPE) args[i] = Double.valueOf(0d);
        else if (p == Character.TYPE) args[i] = Character.valueOf((char) 0);
        else if (p == String.class) args[i] = "";
        else if (!p.isPrimitive()) args[i] = null;
        else return false;
    }
    return true;
}

String callStaticString(ClassLoader cl, String clsName, String methodName) {
    try {
        Object x = XposedHelpers.callStaticMethod(Class.forName(clsName, false, cl), methodName);
        if (x != null) return String.valueOf(x);
    } catch (Throwable ignored) {}
    return "";
}

String callMethodString(Object obj, String methodName) {
    try {
        Object x = XposedHelpers.callMethod(obj, methodName);
        if (x != null) return String.valueOf(x);
    } catch (Throwable ignored) {}
    return "";
}

String makeClientId(ClassLoader cl, long sid, String clientClsName) {
    String clientId = String.valueOf(sid);
    try {
        byte[] b = String.valueOf(android.os.SystemClock.elapsedRealtime()).getBytes();
        Object x = XposedHelpers.callStaticMethod(Class.forName(clientClsName, false, cl), "g", b);
        if (x != null) clientId = String.valueOf(x);
    } catch (Throwable ignored) {}
    return clientId;
}

boolean isLikelyDispatcherObject(Object d) {
    if (d == null) return false;
    try {
        java.lang.reflect.Method[] ms = d.getClass().getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            java.lang.reflect.Method m = ms[i];
            if (!"g".equals(m.getName())) continue;
            Class[] pts = m.getParameterTypes();
            if (pts == null || pts.length == 0) continue;
            String p0 = pts[0].getName();
            if (p0.indexOf("com.tencent.mm.modelbase.m1") >= 0 || p0.indexOf("com.tencent.mm.modelbase.n1") >= 0 || p0.indexOf("com.tencent.mm.modelbase.l1") >= 0 || p0.indexOf("com.tencent.mm.modelbase.k1") >= 0) {
                return true;
            }
        }
    } catch (Throwable ignored) {}
    return false;
}

void findDispatcher() {
    ClassLoader cl = hostContext.getClassLoader();
    
    try {
        List members = findMemberList({"MicroMsg.NetSceneQueue"});
        if (members != null && !members.isEmpty()) {
            HashSet classSet = new HashSet();
            for (int mi = 0; mi < members.size(); mi++) {
                Object mObj = members.get(mi);
                if (mObj instanceof java.lang.reflect.Member) {
                    classSet.add(((java.lang.reflect.Member) mObj).getDeclaringClass());
                }
            }
            
            Iterator cit = classSet.iterator();
            while (cit.hasNext()) {
                Class cls = (Class) cit.next();
                
                java.lang.reflect.Method[] ms = cls.getDeclaredMethods();
                for (int i = 0; i < ms.length; i++) {
                    java.lang.reflect.Method m = ms[i];
                    if (m.getParameterTypes().length != 0) continue;
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;

                    Object obj = null;
                    try {
                        m.setAccessible(true);
                        obj = m.invoke(null);
                    } catch (Throwable ignored) {}
                    if (obj == null) continue;

                    if (isLikelyDispatcherObject(obj)) {
                        dispatcher = obj;
                        return;
                    }

                    try {
                        java.lang.reflect.Field bf = obj.getClass().getDeclaredField("b");
                        bf.setAccessible(true);
                        Object d = bf.get(obj);
                        if (isLikelyDispatcherObject(d)) {
                            dispatcher = d;
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    } catch (Throwable e) {
    }

    // 从 DexKit 命中的队列类反推同包单例入口（低版本兼容层）
    try {
        List members2 = findMemberList({"MicroMsg.NetSceneQueue"});
        if (members2 != null && !members2.isEmpty()) {
            HashSet ownerSet = new HashSet();
            for (int i = 0; i < members2.size(); i++) {
                Object mo = members2.get(i);
                if (mo instanceof java.lang.reflect.Member) {
                    Class dc = ((java.lang.reflect.Member)mo).getDeclaringClass();
                    if (dc != null) ownerSet.add(dc);
                }
            }

            String[] suffix = {"h1", "i1", "j1", "k1", "b1"};
            String[] staticMethods = {"d", "n", "i"};
            Iterator it2 = ownerSet.iterator();
            while (it2.hasNext()) {
                Class oc = (Class) it2.next();
                String pkg = null;
                try {
                    Package p = oc.getPackage();
                    if (p != null) pkg = p.getName();
                } catch (Throwable ignored) {}
                if (pkg == null || pkg.length() == 0) continue;

                for (int si = 0; si < suffix.length; si++) {
                    String cn = pkg + "." + suffix[si];
                    Class c = null;
                    try { c = Class.forName(cn, false, cl); } catch (Throwable ignored) {}
                    if (c == null) continue;

                    for (int mi = 0; mi < staticMethods.length; mi++) {
                        String mn = staticMethods[mi];
                        Object obj = null;
                        try { obj = XposedHelpers.callStaticMethod(c, mn); } catch (Throwable ignored) {}
                        if (obj == null) continue;
                        if (isLikelyDispatcherObject(obj)) { dispatcher = obj; return; }
                        try {
                            Object d = XposedHelpers.getObjectField(obj, "b");
                            if (isLikelyDispatcherObject(d)) { dispatcher = d; return; }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    } catch (Throwable ignored) {}

    try {
        String[] names = {"h1", "i1", "j1", "k1", "b1"};
        // 动态枚举 [a-z][a-z0-9]0 前缀（如 tk0 / qd0 / xe0 / d80 / u70）
        String alphaNum = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int a = (int)'a'; a <= (int)'z'; a++) {
            for (int bi = 0; bi < alphaNum.length(); bi++) {
                char b = alphaNum.charAt(bi);
                String pkg = String.valueOf((char)a) + String.valueOf(b) + "0";
                for (int ni = 0; ni < names.length; ni++) {
                    String cn = pkg + "." + names[ni];
                    Class cls = null;
                    try { cls = Class.forName(cn, false, cl); } catch (Throwable ignored) {}
                    if (cls == null) continue;

                    try {
                        Object obj = XposedHelpers.callStaticMethod(cls, "d");
                        if (isLikelyDispatcherObject(obj)) {
                            dispatcher = obj;
                            return;
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Object holder = XposedHelpers.callStaticMethod(cls, "n");
                        if (holder != null) {
                            Object d = XposedHelpers.getObjectField(holder, "b");
                            if (isLikelyDispatcherObject(d)) {
                                dispatcher = d;
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    } catch (Throwable e) {}

}

boolean send(Object req, String label) {
    boolean trustInvokeForQ2 = false;
    try { trustInvokeForQ2 = (label != null && label.indexOf("full-e56") == 0); } catch (Throwable ignored) {}
    if (dispatcher == null) {
        try { findDispatcher(); } catch (Throwable ignored) {}
        if (dispatcher == null) {
            long now = System.currentTimeMillis();
            if (now - lastDispatcherLogTs > 15000L) {
                lastDispatcherLogTs = now;
                logMsg("[发送] dispatcher为空 label=" + String.valueOf(label) + " req=" + (req == null ? "null" : req.getClass().getName()));
            }
            return false;
        }
    }

    java.lang.reflect.Method[] ms = dispatcher.getClass().getDeclaredMethods();
    java.lang.reflect.Method gMethod = null;
    java.lang.reflect.Method gMethod2 = null;
    for (int i = 0; i < ms.length; i++) {
        java.lang.reflect.Method m = ms[i];
        if (!"g".equals(m.getName())) continue;
        Class[] pts = m.getParameterTypes();
        if (pts.length == 1 && pts[0].isAssignableFrom(req.getClass())) {
            gMethod = m;
        } else if (pts.length == 2 && pts[0].isAssignableFrom(req.getClass())) {
            gMethod2 = m;
        }
    }

    if (gMethod != null) {
        gMethod.setAccessible(true);
        try {
            Object ret = gMethod.invoke(dispatcher, req);
            return trustInvokeForQ2 || isSendRetOk(ret);
        } catch (Throwable ignored) {}
    }
    if (gMethod2 != null) {
        gMethod2.setAccessible(true);
        try {
            Object ret = gMethod2.invoke(dispatcher, req, 0);
            return trustInvokeForQ2 || isSendRetOk(ret);
        } catch (Throwable ignored) {}
    }

    try {
        java.lang.reflect.Method[] ms2 = dispatcher.getClass().getDeclaredMethods();
        for (int i = 0; i < ms2.length; i++) {
            java.lang.reflect.Method m = ms2[i];
            if (!"h".equals(m.getName())) continue;
            Class[] pts = m.getParameterTypes();
            if (pts == null || pts.length != 2) continue;
            if (!pts[0].isAssignableFrom(req.getClass())) continue;
            if (pts[1] != Integer.TYPE) continue;
            m.setAccessible(true);
            Object ret = m.invoke(dispatcher, req, 0);
            return trustInvokeForQ2 || isSendRetOk(ret);
        }
    } catch (Throwable e) {
        logMsg("[发送] " + label + " h兜底失败: " + e);
    }

    // 通用兜底仅用于刷新链路，避免点赞链误判“发送成功”
    if (label == null || label.indexOf("刷新-") != 0) return false;
    // 通用兜底：尝试所有“任意参数位可接收 req”的实例方法，自动补默认参数
    try {
        java.lang.reflect.Method[] ms3 = dispatcher.getClass().getDeclaredMethods();
        for (int i = 0; i < ms3.length; i++) {
            java.lang.reflect.Method m = ms3[i];
            Class[] pts = m.getParameterTypes();
            if (pts == null || pts.length == 0) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (pts.length > 4) continue;

            int reqPos = -1;
            for (int rp = 0; rp < pts.length; rp++) {
                try {
                    if (pts[rp].isAssignableFrom(req.getClass())) { reqPos = rp; break; }
                } catch (Throwable ignored) {}
            }
            if (reqPos < 0) continue;

            Object[] args = new Object[pts.length];
            if (!fillDefaultArgs(args, pts, reqPos, req)) continue;

            try {
                m.setAccessible(true);
                Object ret = m.invoke(dispatcher, args);
                if (isSendRetOk(ret)) return true;
            } catch (Throwable ignored) {}
        }
    } catch (Throwable e) {
        // ignore
    }

    return false;
}

Object autoDetectSnsInfoByL4(ClassLoader cl, long snsId) {
    try {
        Class clsL4 = Class.forName("com.tencent.mm.plugin.sns.model.l4", false, cl);

        if (cachedSnsGetterL4 != null && cachedSnsGetterStore != null) {
            try {
                cachedSnsGetterL4.setAccessible(true);
                Object store = cachedSnsGetterL4.invoke(null);
                if (store != null) {
                    cachedSnsGetterStore.setAccessible(true);
                    Object info = cachedSnsGetterStore.invoke(store, snsId);
                    if (info != null) return info;
                }
            } catch (Throwable ignored) {}
        }

        java.lang.reflect.Method[] l4ms = clsL4.getDeclaredMethods();
        for (int i = 0; i < l4ms.length; i++) {
            java.lang.reflect.Method lm = l4ms[i];
            if (!java.lang.reflect.Modifier.isStatic(lm.getModifiers())) continue;
            if (lm.getParameterTypes().length != 0) continue;
            Class rt = lm.getReturnType();
            if (rt == null || rt.isPrimitive()) continue;

            Object store = null;
            try {
                lm.setAccessible(true);
                store = lm.invoke(null);
            } catch (Throwable ignored) {}
            if (store == null) continue;

            java.lang.reflect.Method[] sms = store.getClass().getDeclaredMethods();
            for (int j = 0; j < sms.length; j++) {
                java.lang.reflect.Method sm = sms[j];
                Class[] pts = sm.getParameterTypes();
                if (pts.length != 1 || pts[0] != Long.TYPE) continue;

                Class srt = sm.getReturnType();
                if (srt == null) continue;
                String rn = srt.getName();
                if (rn.indexOf("SnsInfo") < 0 && rn.indexOf(".storage.") < 0) continue;

                try {
                    sm.setAccessible(true);
                    Object info = sm.invoke(store, snsId);
                    if (info != null) {
                        boolean ok = false;
                        try { info.getClass().getDeclaredMethod("setLikeFlag", Integer.TYPE); ok = true; } catch (Throwable ignored2) {}
                        if (!ok) {
                            try { info.getClass().getDeclaredField("field_snsId"); ok = true; } catch (Throwable ignored3) {}
                        }
                        if (!ok) continue;

                        cachedSnsGetterL4 = lm;
                        cachedSnsGetterStore = sm;
                        return info;
                    }
                } catch (Throwable ignored) {}
            }
        }
    } catch (Throwable e) {
    }
    return null;
}

Object getSnsInfo(ClassLoader cl, long snsId) {
    try {
        Object info = autoDetectSnsInfoByL4(cl, snsId);
        if (info != null) return info;
    } catch (Throwable ignored) {}

    try {
        String[] roots = {"com.tencent.mm.plugin.sns.model.j4", "com.tencent.mm.plugin.sns.model.k4", "com.tencent.mm.plugin.sns.model.l4"};
        for (int ri = 0; ri < roots.length; ri++) {
            Class root = Class.forName(roots[ri], false, cl);
            java.lang.reflect.Method[] rms = root.getDeclaredMethods();
            for (int i = 0; i < rms.length; i++) {
                java.lang.reflect.Method rm = rms[i];
                if (!java.lang.reflect.Modifier.isStatic(rm.getModifiers())) continue;
                if (rm.getParameterTypes().length != 0) continue;
                Class rt = rm.getReturnType();
                if (rt == null || rt.isPrimitive()) continue;
                Object store = null;
                try { rm.setAccessible(true); store = rm.invoke(null); } catch (Throwable ignored2) {}
                if (store == null) continue;
                java.lang.reflect.Method[] sms = store.getClass().getDeclaredMethods();
                for (int j = 0; j < sms.length; j++) {
                    java.lang.reflect.Method sm = sms[j];
                    Class[] pts = sm.getParameterTypes();
                    if (pts == null || pts.length != 1) continue;
                    if (pts[0] != Long.TYPE && pts[0] != String.class) continue;
                    Object info = null;
                    try {
                        sm.setAccessible(true);
                        info = (pts[0] == Long.TYPE) ? sm.invoke(store, snsId) : sm.invoke(store, snsIdKey(snsId));
                    } catch (Throwable ignored2) {}
                    if (info == null) continue;
                    try { info.getClass().getDeclaredMethod("setLikeFlag", Integer.TYPE); return info; } catch (Throwable ignored2) {}
                    try { info.getClass().getDeclaredField("field_snsId"); return info; } catch (Throwable ignored2) {}
                }
            }
        }
    } catch (Throwable ignored) {}

    try {
        Class clsL3 = Class.forName("com.tencent.mm.plugin.sns.model.l3", false, cl);
        java.lang.reflect.Method[] ms = clsL3.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            java.lang.reflect.Method m = ms[i];
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Long.TYPE) {
                m.setAccessible(true);
                Object info = m.invoke(null, snsId);
                if (info != null) {
                    try { info.getClass().getDeclaredMethod("setLikeFlag", Integer.TYPE); return info; } catch (Throwable ignored2) {}
                }
            }
        }
    } catch (Throwable ignored) {}
    
    return null;
}

Class resolveQ2ClassByDexKit(ClassLoader cl) {
    if (cachedQ2Class != null) return cachedQ2Class;
    try {
        List clsList = findClassList({"MicroMsg.NetSceneSnsObjectOp"});
        if (clsList != null) {
            for (int i = 0; i < clsList.size(); i++) {
                Object obj = clsList.get(i);
                if (!(obj instanceof Class)) continue;
                Class c = (Class)obj;
                if (!"com.tencent.mm.plugin.sns.model".equals(c.getPackage().getName())) continue;
                java.lang.reflect.Constructor[] ctors = c.getDeclaredConstructors();
                for (int j = 0; j < ctors.length; j++) {
                    Class[] pts = ctors[j].getParameterTypes();
                    if (pts == null || pts.length < 2) continue;
                    if (pts[0] == Long.TYPE && pts[1] == Integer.TYPE) {
                        cachedQ2Class = c;
                        return c;
                    }
                }
            }
        }
    } catch (Throwable ignored) {}
    try {
        cachedQ2Class = Class.forName("com.tencent.mm.plugin.sns.model.q2", false, cl);
        return cachedQ2Class;
    } catch (Throwable ignored) {}
    return null;
}

boolean tryN2For61(ClassLoader cl, Object snsInfo, long sid) {
    try {
        try { XposedHelpers.callMethod(snsInfo, "setLikeFlag", 1); } catch (Throwable ignored) {}
        Class clsGz5 = Class.forName("sn4.gz5", false, cl);
        Class clsHz5 = Class.forName("sn4.hz5", false, cl);
        Class clsN2 = Class.forName("com.tencent.mm.plugin.sns.model.n2", false, cl);

        Object gz5 = clsGz5.newInstance();
        XposedHelpers.setIntField(gz5, "h", 1);
        XposedHelpers.setIntField(gz5, "m", (int)(System.currentTimeMillis() / 1000));
        XposedHelpers.setIntField(gz5, "i", 0);

        String fromUin = callStaticString(cl, "pr0.w1", "n");
        String fromUser = callStaticString(cl, "pr0.w1", "t");
        String toUser = callMethodString(snsInfo, "getUserName");
        XposedHelpers.setObjectField(gz5, "f", fromUin);
        XposedHelpers.setObjectField(gz5, "d", fromUser);
        XposedHelpers.setObjectField(gz5, "e", toUser);
        try {
            Class clsX1 = Class.forName("pr0.x1", false, cl);
            Object x = XposedHelpers.callStaticMethod(clsX1, "c", toUser);
            if (x != null) XposedHelpers.setObjectField(gz5, "g", String.valueOf(x));
        } catch (Throwable ignored) {}

        Object hz5 = clsHz5.newInstance();
        XposedHelpers.setLongField(hz5, "d", sid);
        XposedHelpers.setObjectField(hz5, "f", gz5);
        Object gz5_2 = clsGz5.newInstance();
        XposedHelpers.setObjectField(hz5, "g", gz5_2);

        String clientId = makeClientId(cl, sid, "ej.k");

        try {
            Object vc = XposedHelpers.callStaticMethod(
                Class.forName("com.tencent.mm.plugin.sns.model.k4", false, cl), "Vc");
            XposedHelpers.callMethod(vc, "a", clientId, hz5, 0);
        } catch (Throwable ignored) {}

        Object n2Req = XposedHelpers.newInstance(clsN2, hz5, clientId, 0);
        return send(n2Req, "n2-61");
    } catch (Throwable e) {
        return false;
    }
}

boolean tryN2For58(ClassLoader cl, Object snsInfo, long sid) {
    return tryN2ByConfig(cl, snsInfo, sid, "vk4.xw5", "vk4.ww5", "p", "o", "n", "m", "fq0.x1", "n", "t", "fq0.y1", "i", "f", "i", "aj.j", "D9", "b", 1, "n2-58");
}

boolean tryN2For60(ClassLoader cl, Object snsInfo, long sid) {
    return tryN2ByConfig(cl, snsInfo, sid, "wl4.xx5", "wl4.wx5", "o", "n", "m", "i", "wq0.w1", "n", "t", "wq0.x1", "h", "f", "h", "gj.j", "Rc", "b", 2, "n2-60");
}

boolean tryN2ByConfig(
    ClassLoader cl, Object snsInfo, long sid,
    String mainClsName, String subClsName,
    String subStrField, String subTsField, String subZeroField, String subLikeField,
    String userClsName, String userUinMethod, String userNameMethod,
    String aliasClsName, String aliasField,
    String mainLikeField, String mainEmptyField,
    String clientClsName, String j4EntryMethod, String j4CallMethod, int j4CallMode,
    String sendLabel
) {
    try {
        try { XposedHelpers.callMethod(snsInfo, "setLikeFlag", 1); } catch (Throwable ignored) {}
        Class clsMain = Class.forName(mainClsName, false, cl);
        Class clsSub = Class.forName(subClsName, false, cl);
        Class clsN2 = Class.forName("com.tencent.mm.plugin.sns.model.n2", false, cl);

        String toUser = callMethodString(snsInfo, "getUserName");

        Object sub = clsSub.newInstance();
        XposedHelpers.setObjectField(sub, subStrField, "");
        XposedHelpers.setIntField(sub, subTsField, (int)(System.currentTimeMillis()/1000));
        XposedHelpers.setIntField(sub, subZeroField, 0);
        XposedHelpers.setIntField(sub, subLikeField, 1);

        String fromUin = callStaticString(cl, userClsName, userUinMethod);
        String fromUser = callStaticString(cl, userClsName, userNameMethod);

        XposedHelpers.setObjectField(sub, "f", fromUin);
        XposedHelpers.setObjectField(sub, "d", fromUser);
        XposedHelpers.setObjectField(sub, "e", toUser);
        try {
            Class clsA = Class.forName(aliasClsName, false, cl);
            Object x = XposedHelpers.callStaticMethod(clsA, "c", toUser);
            if (x != null) XposedHelpers.setObjectField(sub, aliasField, String.valueOf(x));
        } catch (Throwable ignored) {}

        Object sub2 = clsSub.newInstance();
        XposedHelpers.setObjectField(sub2, "d", "");

        Object main = clsMain.newInstance();
        XposedHelpers.setLongField(main, "d", sid);
        XposedHelpers.setObjectField(main, mainLikeField, sub);
        XposedHelpers.setObjectField(main, mainEmptyField, sub2);

        String clientId = makeClientId(cl, sid, clientClsName);

        try {
            Object j4Obj = XposedHelpers.callStaticMethod(
                Class.forName("com.tencent.mm.plugin.sns.model.j4", false, cl), j4EntryMethod);
            if (j4CallMode == 0) XposedHelpers.callMethod(j4Obj, j4CallMethod, clientId, main);
            else if (j4CallMode == 1) XposedHelpers.callMethod(j4Obj, j4CallMethod, clientId, main, "");
            else XposedHelpers.callMethod(j4Obj, j4CallMethod, clientId, main, "", 0);
        } catch (Throwable ignored) {}

        Object n2Req = XposedHelpers.newInstance(clsN2, main, clientId, 0);
        return send(n2Req, sendLabel == null ? "n2-cfg" : sendLabel);
    } catch (Throwable ignored) {}
    return false;
}

boolean tryN2For50To61(ClassLoader cl, Object snsInfo, long sid) {
    // minor -> config (来自已验证版本链)
    if (wxMinor == 61) return tryN2For61(cl, snsInfo, sid);
    if (wxMinor == 60) return tryN2For60(cl, snsInfo, sid);
    if (wxMinor == 58) return tryN2For58(cl, snsInfo, sid);
    if (wxMinor == 57) return tryN2ByConfig(cl, snsInfo, sid, "zh4.hv5", "zh4.gv5", "p", "o", "n", "m", "oo0.x1", "m", "s", "oo0.y1", "i", "f", "i", "zi.j", "s9", "b", 1, "n2-cfg");
    if (wxMinor == 56) return tryN2ByConfig(cl, snsInfo, sid, "mg4.yr5", "mg4.xr5", "p", "o", "n", "m", "vn0.x1", "m", "s", "vn0.y1", "i", "f", "i", "yi.j", "h9", "a", 0, "n2-cfg");
    if (wxMinor == 55) return tryN2ByConfig(cl, snsInfo, sid, "td4.jq5", "td4.iq5", "p", "o", "n", "m", "nm0.c2", "m", "s", "nm0.d2", "i", "f", "i", "ki.j", "T8", "a", 1, "n2-cfg");
    if (wxMinor == 54) return tryN2ByConfig(cl, snsInfo, sid, "ce4.io5", "ce4.ho5", "o", "n", "m", "i", "an0.b2", "l", "r", "an0.c2", "h", "f", "h", "ni.j", "V8", "a", 1, "n2-cfg");
    if (wxMinor == 53) return tryN2ByConfig(cl, snsInfo, sid, "ud4.rp5", "ud4.qp5", "o", "n", "m", "i", "zm0.c2", "l", "r", "zm0.d2", "h", "f", "h", "oi.j", "E8", "a", 1, "n2-cfg");
    if (wxMinor == 51) return tryN2ByConfig(cl, snsInfo, sid, "gd4.vo5", "gd4.uo5", "o", "n", "m", "i", "tm0.c2", "l", "r", "tm0.d2", "h", "f", "h", "oi.j", "P8", "a", 1, "n2-cfg");
    if (wxMinor == 50) return tryN2ByConfig(cl, snsInfo, sid, "p84.mg5", "p84.lg5", "n", "j", "i", "h", "ik0.c2", "l", "r", "ik0.d2", "g", "f", "g", "qh.j", "p8", "a", 1, "n2-cfg");
    return false;
}

boolean tryFull(ClassLoader cl, long snsId) {
    try {
        Object snsInfo = getSnsInfo(cl, snsId);
        if (snsInfo == null) {
            logMsg("[tryFull] getSnsInfo=null snsId=" + snsId);
            return false;
        }
        
        try { XposedHelpers.callMethod(snsInfo, "setLikeFlag", 1); } catch (Throwable ignored) {}
        
        long sid = snsId;
        try { sid = XposedHelpers.getLongField(snsInfo, "field_snsId"); } catch (Throwable ignored) {}
        if (sid == 0) try { sid = XposedHelpers.getLongField(snsInfo, "snsId"); } catch (Throwable ignored) {}
        if (sid == 0) sid = snsId;

        // <=61 仅走已验证 n2 模块（无兜底）
        if (wxMinor <= 61) {
            return tryN2For50To61(cl, snsInfo, sid);
        }

        Class clsQ2 = null;
        Object e56 = null;
        try {
            clsQ2 = resolveQ2ClassByDexKit(cl);
            if (clsQ2 == null) throw new java.lang.ClassNotFoundException("q2 not found");
            String[] d6Classes = {"com.tencent.mm.plugin.sns.model.d6", "com.tencent.mm.plugin.sns.d6"};
            for (int dc = 0; dc < d6Classes.length; dc++) {
                String d6cn = d6Classes[dc];
                try {
                    Class clsD6 = Class.forName(d6cn, false, cl);
                    java.lang.reflect.Method[] ms = clsD6.getDeclaredMethods();
                    for (int i = 0; i < ms.length; i++) {
                        java.lang.reflect.Method m = ms[i];
                        Class[] pts = m.getParameterTypes();
                        if (pts == null || pts.length != 4) continue;
                        if (pts[0] == null || !pts[0].isAssignableFrom(snsInfo.getClass())) continue;
                        Object out = null;
                        try {
                            m.setAccessible(true);
                            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) out = m.invoke(null, snsInfo, 1, null, 1);
                            else out = m.invoke(clsD6.newInstance(), snsInfo, 1, null, 1);
                        } catch (Throwable ignored2) {}
                        if (out == null) continue;
                        try {
                            if (XposedHelpers.newInstance(clsQ2, sid, 5, out) != null) { e56 = out; break; }
                        } catch (Throwable ignored3) {}
                    }
                    if (e56 != null) break;
                } catch (Throwable ignored4) {}
            }
            if (e56 != null) {
                try {
                    Object req = XposedHelpers.newInstance(clsQ2, sid, 5, e56);
                    if (send(req, "full-e56")) return true;
                } catch (Throwable ignored5) {}
            }

            // q2 内部兜底：不走 n2，避免双请求
            try {
                Object req = XposedHelpers.newInstance(clsQ2, sid, 5);
                if (send(req, "full-q2-2")) return true;
            } catch (Throwable ignored7) {}
            try {
                Object req = XposedHelpers.newInstance(clsQ2, sid, 5, null);
                if (send(req, "full-q2-3")) return true;
            } catch (Throwable ignored8) {}
        } catch (Throwable ignored6) {}

        return false;
    } catch (Throwable e) {
        return false;
    }
}

void executeLike(long snsId) { executeLikeInternal(snsId, false); }

void executeLikeInternal(long snsId, boolean alreadyMarkedProcessed) {
    Long id = Long.valueOf(snsId);
    try {
        ClassLoader cl = hostContext.getClassLoader();
        if (!alreadyMarkedProcessed && processedIds.contains(id)) return;

        // <=61 走已验证 n2 配置链；>=62 走 q2 链（由 tryFull 分流）
        boolean ok = tryFull(cl, snsId);

        if (!processedIds.contains(id)) {
            processedIds.add(id);
            saveProcessedIds();
        }
        if (ok) {
            logSuccess("snsId=" + snsId + " wxMinor=" + wxMinor);
        } else {
            logSkip("执行失败 snsId=" + snsId + " wxMinor=" + wxMinor + " hostVer=" + String.valueOf(hostVerName));
        }
    } catch (Throwable e) {
        try { if (!processedIds.contains(id)) { processedIds.add(id); saveProcessedIds(); } } catch (Throwable ignored) {}
        logMsg("[执行异常] 已标记已处理 snsId=" + snsId + " err=" + e);
        logSkip("执行异常 snsId=" + snsId + " err=" + e);
    } finally {
        pendingIds.remove(id);
    }
}

ArrayList resolveRefreshSceneClassesByDexKit() {
    if (cachedRefreshSceneClasses != null && cachedRefreshSceneClasses.size() > 0) return cachedRefreshSceneClasses;
    cachedRefreshSceneClasses = new ArrayList();
    try {
        // 第一层：精确日志特征
        String[] keys = {
            "MicroMsg.NetSceneSnsTimeLine",
            "MicroMsg.NetSceneSnsUserPage",
            "MicroMsg.NetSceneSnsTagList",
            "MicroMsg.NetSceneSnsSync",
            "MicroMsg.NetSceneSnsObjectDetail",
            "MicroMsg.NetSceneSnsCommentDetail"
        };
        for (int i = 0; i < keys.length; i++) {
            List list = findClassList(new String[]{keys[i]});
            if (list == null) continue;
            for (int j = 0; j < list.size(); j++) {
                Object o = list.get(j);
                if (!(o instanceof Class)) continue;
                Class c = (Class)o;
                try {
                    Package p = c.getPackage();
                    if (p == null || !"com.tencent.mm.plugin.sns.model".equals(p.getName())) continue;
                } catch (Throwable ignored) {}
                if (!cachedRefreshSceneClasses.contains(c)) cachedRefreshSceneClasses.add(c);
            }
        }
    } catch (Throwable ignored) {}

    try {
        // 第二层：宽匹配所有 NetSceneSns* 成员，再反推声明类
        String[] memberKeys = {
            "MicroMsg.NetSceneSns",
            "NetSceneSnsTimeLine",
            "NetSceneSnsUserPage",
            "NetSceneSnsTag",
            "NetSceneSnsSync"
        };
        for (int i = 0; i < memberKeys.length; i++) {
            List members = findMemberList(new String[]{memberKeys[i]});
            if (members == null) continue;
            for (int j = 0; j < members.size(); j++) {
                Object mo = members.get(j);
                if (!(mo instanceof java.lang.reflect.Member)) continue;
                Class c = ((java.lang.reflect.Member)mo).getDeclaringClass();
                if (c == null) continue;
                try {
                    Package p = c.getPackage();
                    if (p == null || !"com.tencent.mm.plugin.sns.model".equals(p.getName())) continue;
                } catch (Throwable ignored) {}
                // 过滤掉明显不能当请求体的类
                boolean ctorOk = false;
                try {
                    java.lang.reflect.Constructor[] ctors = c.getDeclaredConstructors();
                    for (int ci = 0; ci < ctors.length; ci++) {
                        Class[] pts = ctors[ci].getParameterTypes();
                        if (pts == null) continue;
                        if (pts.length == 0 || pts.length == 1 || pts.length == 2 || pts.length == 3) {
                            ctorOk = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
                if (!ctorOk) continue;
                if (!cachedRefreshSceneClasses.contains(c)) cachedRefreshSceneClasses.add(c);
            }
        }
    } catch (Throwable ignored) {}

    return cachedRefreshSceneClasses;
}

boolean trySendRefreshScene(Class cls) {
    if (cls == null) return false;
    String cn = cls.getName();
    try {
        java.lang.reflect.Constructor[] ctors = cls.getDeclaredConstructors();
        for (int i = 0; i < ctors.length; i++) {
            java.lang.reflect.Constructor ctor = ctors[i];
            Class[] pts = ctor.getParameterTypes();
            if (pts == null) continue;
            if (pts.length > 6) continue;

            Object[] args0 = new Object[pts.length];
            if (!fillDefaultArgs(args0, pts, -1, null)) continue;

            Object req = null;
            try {
                ctor.setAccessible(true);
                req = ctor.newInstance(args0);
                if (req != null) {
                    boolean ok = send(req, "刷新-" + cn + "-ctor" + i + "-z");
                    if (ok) return true;
                }
            } catch (Throwable e) {}

            // 变体：把第一个整型参数置 1，常见于 timeline/page type
            try {
                Object[] args1 = new Object[args0.length];
                for (int k = 0; k < args0.length; k++) args1[k] = args0[k];
                for (int k = 0; k < pts.length; k++) {
                    if (pts[k] == Integer.TYPE || pts[k] == Short.TYPE || pts[k] == Byte.TYPE) {
                        args1[k] = Integer.valueOf(1);
                        break;
                    }
                }
                req = ctor.newInstance(args1);
                if (req != null) {
                    boolean ok = send(req, "刷新-" + cn + "-ctor" + i + "-i1");
                    if (ok) return true;
                }
            } catch (Throwable e) {}
        }
    } catch (Throwable ignored) {}
    return false;
}

void triggerBackgroundRefresh() {
    try {
        if (!AUTO_LIKE_ENABLE || !REFRESH_ENABLE || !isScheduleAllowed() || !shouldRunFixedRefreshNow()) return;
        if (!hasValidAutoLikeTargetConfig()) return;
        beginRefreshLogWindow();
        long now = System.currentTimeMillis();
        if (LOG_ENABLE && now - lastDiagLogTs > 15000L) {
            lastDiagLogTs = now;
            logMsg("[诊断] 刷新循环运行中 wxMinor=" + wxMinor + " mode=" + (LIST_MODE == 1 ? "黑名单" : "白名单") + " age=" + MAX_POST_AGE_HOURS + "h");
        }
        
        ArrayList refreshScenes = resolveRefreshSceneClassesByDexKit();
        if (refreshScenes != null) {
            for (int i = 0; i < refreshScenes.size(); i++) {
                Class c = (Class) refreshScenes.get(i);
                trySendRefreshScene(c);
            }
        }
    } catch (Throwable e) {
        logMsg("[刷新异常] " + e);
    }
}

void loadProcessedIds() {
    try {
        String raw = getString(CFG_KEY_PROCESSED, "");
        if (raw == null || raw.length() == 0) return;
        String[] arr = raw.split(",");
        for (int i = 0; i < arr.length; i++) {
            String s = arr[i]; if (s == null || s.length() == 0) continue;
            try { processedIds.add(Long.valueOf(s)); } catch (Throwable ignored) {}
        }
    } catch (Throwable e) { logMsg("[持久化] 加载失败: " + e); }
}

void saveProcessedIds() {
    try {
        ArrayList list = new ArrayList();
        Iterator it = processedIds.iterator();
        while (it.hasNext()) list.add(it.next());
        while (list.size() > MAX_PROCESSED_SAVE) {
            Object removed = list.remove(0);
            try { processedIds.remove(removed); } catch (Throwable ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(String.valueOf(list.get(i)));
            if (i != list.size() - 1) sb.append(",");
        }
        putString(CFG_KEY_PROCESSED, sb.toString());
    } catch (Throwable e) { logMsg("[持久化] 保存失败: " + e); }
}
void markProcessed(long snsId) {
    try {
        Long id = Long.valueOf(snsId);
        synchronized (dedupLock) {
            if (!processedIds.contains(id)) {
                processedIds.add(id);
                saveProcessedIds();
            }
            pendingIds.remove(id);
        }
    } catch (Throwable ignored) {}
}

void markCanceled(long snsId) {
    Long id = Long.valueOf(snsId);
    canceledIds.add(id); pendingIds.remove(id);
}


Long parseSnsIdFromDeleteArgs(Object[] args) {
    try {
        if (args == null || args.length < 2) return null;
        String where = null; String[] wa = null;
        try { where = (String) args[1]; } catch (Throwable ignored) {}
        try { wa = (String[]) args[2]; } catch (Throwable ignored) {}
        if (where == null) return null;
        int p = where.indexOf("snsId=");
        if (p >= 0) {
            String sub = where.substring(p + 6).trim();
            int end = sub.length();
            int p1 = sub.indexOf(" "); if (p1 > 0 && p1 < end) end = p1;
            int p2 = sub.indexOf(")"); if (p2 > 0 && p2 < end) end = p2;
            int p3 = sub.indexOf(";"); if (p3 > 0 && p3 < end) end = p3;
            try { return Long.valueOf(sub.substring(0, end).replace("'","").replace("\"","").trim()); } catch (Throwable ignored2) {}
        }
        if (where.indexOf("snsId=?") >= 0 && wa != null && wa.length > 0) {
            try { return Long.valueOf(wa[0]); } catch (Throwable ignored3) {}
        }
    } catch (Throwable ignored) {}
    return null;
}

Long parseSnsIdFromContentValues(ContentValues cv) {
    try {
        if (cv == null) return null;
        Long sidObj = null;
        try { sidObj = cv.getAsLong("snsId"); } catch (Throwable ignored) {}
        if (sidObj == null) try { sidObj = cv.getAsLong("field_snsId"); } catch (Throwable ignored) {}
        if (sidObj == null) try { sidObj = cv.getAsLong("svrId"); } catch (Throwable ignored) {}
        return sidObj;
    } catch (Throwable ignored) {}
    return null;
}

boolean isAlreadyHandled(Long id) {
    try {
        if (id == null) return true;
        synchronized (dedupLock) {
            return processedIds.contains(id) || pendingIds.contains(id) || canceledIds.contains(id);
        }
    } catch (Throwable ignored) {}
    return true;
}

void markPendingAndProcessed(Long id) {
    try {
        if (id == null) return;
        synchronized (dedupLock) {
            pendingIds.add(id);
            processedIds.add(id);
            saveProcessedIds();
        }
    } catch (Throwable ignored) {}
}

boolean passDuplicateWindow(Long id) {
    try {
        if (id == null) return false;
        synchronized (dedupLock) {
            Long lastTs = (Long) recentTriggerTs.get(id);
            long nowTs = System.currentTimeMillis();
            if (lastTs != null && nowTs - lastTs.longValue() < DUP_SUPPRESS_MS) return false;
            recentTriggerTs.put(id, Long.valueOf(nowTs));
            if (recentTriggerTs.size() > 1000) recentTriggerTs.clear();
            return true;
        }
    } catch (Throwable ignored) {}
    return false;
}

boolean handleSkipRules(ContentValues cv, String userName, long snsId) {
    try {
        if (!isPostInAllowedAge(cv)) {
            markProcessed(snsId);
            logSkip("非近" + MAX_POST_AGE_HOURS + "小时朋友圈 user=" + String.valueOf(userName) + " snsId=" + snsId);
            return true;
        }

        int postType = detectPostType(cv);
        if (!isPostTypeAllowed(postType)) {
            markProcessed(snsId);
            logSkip("内容类型过滤[" + postTypeName(postType) + "] user=" + String.valueOf(userName) + " snsId=" + snsId);
            return true;
        }

        if (!isPostKeywordAllowed(cv, postType)) {
            markProcessed(snsId);
            logSkip("命中关键词过滤[" + postTypeName(postType) + "] user=" + String.valueOf(userName) + " snsId=" + snsId);
            return true;
        }
    } catch (Throwable ignored) {}
    return false;
}

void scheduleLikeTask(final long snsId, final String userName) {
    final int delayMs = nextLikeDelayMs();
    logMsg("[流程] 捕捉 user=" + userName + " snsId=" + snsId + " delay=" + formatDurationShort(delayMs) + " wxMinor=" + wxMinor);
    if (ghostHandler == null) return;
    ghostHandler.post(new Runnable() {
        public void run() {
            PowerManager.WakeLock wl = null;
            try {
                PowerManager pm = (PowerManager) hostContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) { wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Like:Lock"); wl.acquire(delayMs + 30000); }
                final PowerManager.WakeLock fwl = wl;
                ghostHandler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            Long x = Long.valueOf(snsId);
                            if (!pendingIds.contains(x)) return;
                            if (wxMinor < 62 && canceledIds.contains(x)) {
                                logMsg("[流程] 命中canceled snsId=" + snsId + " wxMinor=" + wxMinor);
                                pendingIds.remove(x);
                                return;
                            }
                            logMsg("[流程] 执行前 snsId=" + snsId + " wxMinor=" + wxMinor);
                            executeLikeInternal(snsId, true);
                        } finally { try { if (fwl != null && fwl.isHeld()) fwl.release(); } catch (Throwable ignored) {} }
                    }
                }, delayMs);
            } catch (Throwable e) {
                logMsg("[流程] 调度错误 " + e);
                pendingIds.remove(Long.valueOf(snsId));
                try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) {}
            }
        }
    });
}

XC_MethodHook dbWriteHook = new XC_MethodHook() {
    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        try {
            Object[] args = param.args;
            if (args == null || args.length == 0) return;
            if (!"SnsInfo".equals(String.valueOf(args[0]))) return;
            ContentValues cv = null;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof ContentValues) { cv = (ContentValues) args[i]; break; }
            }
            if (cv == null) return;
            recordRefreshObservedPost(cv);
            String userName = null;
            try { userName = cv.getAsString("userName"); } catch (Throwable ignored) {}
            long now = System.currentTimeMillis();
            if (LOG_ENABLE && now - lastDiagLogTs > 15000L) {
                lastDiagLogTs = now;
                logMsg("[诊断] 监听到SnsInfo user=" + String.valueOf(userName) + " mode=" + (LIST_MODE == 1 ? "黑名单" : "白名单"));
            }
            Long sidObj = parseSnsIdFromContentValues(cv);
            if (sidObj == null) return;
            final long snsId = sidObj.longValue();
            if (snsId == 0) return;
            notifySnsPostIfNeeded(userName, snsId, cv);
            if (!AUTO_LIKE_ENABLE || !isScheduleAllowed()) return;
            if (!hasValidAutoLikeTargetConfig()) return;
            if (!shouldAutoLikeUser(userName)) return;
            Long id = Long.valueOf(snsId);
            if (isAlreadyHandled(id)) return;
            
            if (handleSkipRules(cv, userName, snsId)) return;
            if (isAlreadyHandled(id)) return;
            if (!passDuplicateWindow(id)) return;
            markPendingAndProcessed(id);
            scheduleLikeTask(snsId, userName);
        } catch (Throwable e) { logMsg("[拦截器异常] " + e); }
    }
};

XC_MethodHook dbDeleteHook = new XC_MethodHook() {
    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        try {
            Object[] args = param.args;
            if (args == null || args.length == 0) return;
            if (!"SnsInfo".equals(String.valueOf(args[0]))) return;
            Long sid = parseSnsIdFromDeleteArgs(args);
            if (sid != null) markCanceled(sid.longValue());
            if (canceledIds.size() > 1000) canceledIds.clear();
        } catch (Throwable e) { logMsg("[删除监听异常] " + e); }
    }
};

void openSettings() { showAutoLikeHomeUI(); }

boolean onClickSendBtn(String text) {
    if ("自动点赞设置".equals(text) || "点赞设置".equals(text)) {
        showAutoLikeHomeUI();
        return true;
    }
    return false;
}

int dp(Activity a, int v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

GradientDrawable roundRect(int color, int radiusPx) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radiusPx); return g; }

void hideSoftInput(Activity ctx) { try { if (ctx == null) return; InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE); if (imm == null) return; View focus = ctx.getCurrentFocus(); if (focus == null) focus = new View(ctx); imm.hideSoftInputFromWindow(focus.getWindowToken(), 0); } catch (Throwable ignored) {} }


void showTimePickerForText(final Activity ctx, String current, final TextView targetRight, final String[] holder, final int index) {
    try {
        int m = parseTimeToMinute(current);
        if (m < 0) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            m = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        }
        TimePickerDialog d = new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                try {
                    String v = (hourOfDay < 10 ? "0" : "") + hourOfDay + ":" + (minute < 10 ? "0" : "") + minute;
                    holder[index] = v;
                    if (targetRight != null) targetRight.setText(v + " >");
                } catch (Throwable ignored) {}
            }
        }, m / 60, m % 60, true);
        d.show();
    } catch (Throwable e) { toast("打开时间选择器失败: " + e); }
}

TextView makeBtn(Activity ctx, String text, boolean primary) {
    TextView v = new TextView(ctx); v.setText(text); v.setTextSize(14f); v.setGravity(Gravity.CENTER); v.setPadding(dp(ctx, primary ? 18 : 14), dp(ctx, 8), dp(ctx, primary ? 18 : 14), dp(ctx, 8));
    if (primary) { v.setTextColor(Color.WHITE); v.setTypeface(null, Typeface.BOLD); GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")}); bg.setCornerRadius(dp(ctx, 999)); v.setBackground(bg); }
    else { v.setTextColor(Color.parseColor("#334155")); GradientDrawable bg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999)); bg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2")); v.setBackground(bg); }
    return v;
}

EditText addInputRow(Activity ctx, LinearLayout parent, String title, String hint, String value, int inputType) {
    LinearLayout row = new LinearLayout(ctx); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
    TextView tv = new TextView(ctx); tv.setText(title); tv.setTextSize(14f); tv.setTextColor(Color.parseColor("#0F172A")); row.addView(tv);
    EditText et = new EditText(ctx); et.setText(value); et.setHint(hint); et.setSingleLine(true); et.setTextSize(15f); et.setInputType(inputType); et.setTextColor(Color.parseColor("#0F172A")); et.setHintTextColor(Color.parseColor("#94A3B8"));
    GradientDrawable bg = roundRect(Color.parseColor("#FFFFFF"), dp(ctx, 10)); bg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); et.setBackground(bg); et.setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7));
    LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, -2); etLp.topMargin = dp(ctx, 6); row.addView(et, etLp); parent.addView(row); return et;
}

void addDivider(Activity ctx, ViewGroup parent) { View v = new View(ctx); v.setBackgroundColor(Color.parseColor("#E8EEF5")); parent.addView(v, new LinearLayout.LayoutParams(-1, 1)); }

LinearLayout createRowText(final Activity ctx, String left, String right, boolean neutralRight) {
    LinearLayout row = new LinearLayout(ctx); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14)); row.setGravity(Gravity.CENTER_VERTICAL);
    TextView l = new TextView(ctx); l.setText(left); l.setTextColor(Color.parseColor("#0F172A")); l.setTextSize(17f); row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
    TextView r = new TextView(ctx); r.setText(right); r.setTextSize(15f); r.setTextColor(neutralRight ? Color.parseColor("#475569") : Color.parseColor("#2563EB")); r.setSingleLine(true); r.setEllipsize(TextUtils.TruncateAt.END); row.addView(r);
    return row;
}
String countSummary(String raw, String emptyText) { try { int n = parseWxidSet(raw).size(); if (n <= 0) return emptyText; return "已选择 " + n + " 人 >"; } catch (Throwable ignored) {} return emptyText; }
String delaySummary() { if (DELAY_MODE == 0) return "固定 · " + formatDurationShort(FIXED_LIKE_DELAY_MS) + " >"; return "随机 · " + formatDurationShort(MIN_LIKE_DELAY_MS) + " ~ " + formatDurationShort(MAX_LIKE_DELAY_MS) + " >"; }
String safeSummary() { try { String run = "全天运行"; if (SCHEDULE_ENABLE) run = SCHEDULE_START + "-" + SCHEDULE_END; return "近" + MAX_POST_AGE_HOURS + "小时 / " + run + " >"; } catch (Throwable ignored) {} return "近" + MAX_POST_AGE_HOURS + "小时 >"; }

String contentFilterSummary() {
    try {
        StringBuilder type = new StringBuilder();
        if (SKIP_TEXT) type.append("文字");
        if (SKIP_IMAGE) { if (type.length() > 0) type.append("/"); type.append("图文"); }
        if (SKIP_VIDEO) { if (type.length() > 0) type.append("/"); type.append("视频"); }
        boolean hasKw = SKIP_KEYWORDS_RAW != null && SKIP_KEYWORDS_RAW.trim().length() > 0;
        StringBuilder kw = new StringBuilder();
        if (hasKw) {
            if (KEYWORD_FILTER_TEXT) kw.append("文字");
            if (KEYWORD_FILTER_IMAGE) { if (kw.length() > 0) kw.append("/"); kw.append("图文"); }
            if (KEYWORD_FILTER_VIDEO) { if (kw.length() > 0) kw.append("/"); kw.append("视频"); }
        }
        if (type.length() == 0 && (!hasKw || kw.length() == 0)) return "不跳过 >";
        if (type.length() == 0) return "关键词:" + kw.toString() + " >";
        if (!hasKw || kw.length() == 0) return "类型:" + type.toString() + " >";
        return "类型:" + type.toString() + " / 关键词:" + kw.toString() + " >";
    } catch (Throwable ignored) {}
    return "已启用过滤 >";
}

String formatDurationShort(int ms) {
    try {
        if (ms < 1000) return "<1秒";
        int sec = ms / 1000;
        if (sec < 60) return sec + "秒";
        int min = sec / 60;
        if (min < 60) return min + "分钟";
        int h = min / 60;
        int m = min % 60;
        if (m == 0) return h + "小时";
        return h + "小时" + m + "分钟";
    } catch (Throwable ignored) {}
    return String.valueOf(ms) + "ms";
}
GradientDrawable makeCardGradientBg(Activity ctx, int radiusDp, String strokeColor) {
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.WHITE, Color.parseColor("#F8FAFC")}); bg.setCornerRadius(dp(ctx, radiusDp)); bg.setStroke(dp(ctx, 1), Color.parseColor(strokeColor)); return bg;
}

void finishDialogLayout(Dialog dialog, FrameLayout mask, View card) {
    mask.addView(card); mask.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }}); card.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){} }); dialog.setContentView(mask);
    Window w = dialog.getWindow(); if (w != null) { w.setLayout(-1, -1); w.setGravity(Gravity.CENTER); w.setDimAmount(0.25f); }
}

void finishDialogLayoutWithSoftInput(Dialog dialog, FrameLayout mask, View card) {
    finishDialogLayout(dialog, mask, card); Window w = dialog.getWindow(); if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
}
void showDialogAnimated(Dialog dialog, View card, Activity ctx, int dyDp, int duration) { dialog.show(); card.setAlpha(0f); card.setTranslationY(dp(ctx, dyDp)); card.animate().alpha(1f).translationY(0).setDuration(duration).start(); }

TextView addSectionTitle(Activity ctx, LinearLayout parent, String text) {
    TextView tv = new TextView(ctx); tv.setText(text); tv.setTextColor(Color.parseColor("#64748B")); tv.setTextSize(13f); tv.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 6)); parent.addView(tv); return tv;
}


void showAutoLikeHomeUI() {
    final Activity ctx = getTopActivity();
    if (ctx == null) { toast("无法获取当前界面"); return; }
    ctx.runOnUiThread(new Runnable() {
        public void run() {
            try {
                hideSoftInput(ctx);
                loadRuntimeConfig();
                final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                FrameLayout root = new FrameLayout(ctx);
                root.setBackgroundColor(Color.parseColor("#66000000"));
                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
                cardLp.leftMargin = dp(ctx, 18); cardLp.rightMargin = dp(ctx, 18); cardLp.gravity = Gravity.CENTER;
                card.setLayoutParams(cardLp);
                card.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 12));
                GradientDrawable cardBg = makeCardGradientBg(ctx, 20, "#DDE6F2");
                card.setBackground(cardBg);
                TextView title = new TextView(ctx);
                title.setText("朋友圈自动点赞"); title.setTextColor(Color.parseColor("#0F172A")); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD);
                card.addView(title);
                TextView sub = new TextView(ctx);
                sub.setText("仅处理后台刷新捕捉到的近期朋友圈"); sub.setTextColor(Color.parseColor("#64748B")); sub.setTextSize(13f);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(ctx, 8); card.addView(sub, subLp);
                LinearLayout body = new LinearLayout(ctx);
                body.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14)); bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg);
                LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(ctx, 14);
                LinearLayout swRow = new LinearLayout(ctx); swRow.setOrientation(LinearLayout.HORIZONTAL); swRow.setGravity(Gravity.CENTER_VERTICAL); swRow.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
                TextView swTitle = new TextView(ctx); swTitle.setText("启用自动点赞"); swTitle.setTextSize(17f); swTitle.setTextColor(Color.parseColor("#0F172A")); swRow.addView(swTitle, new LinearLayout.LayoutParams(0, -2, 1));
                final Switch swEnable = new Switch(ctx); swEnable.setChecked(AUTO_LIKE_ENABLE); swRow.addView(swEnable); body.addView(swRow); addDivider(ctx, body);
                LinearLayout rowObj = createRowText(ctx, "点赞对象规则", (LIST_MODE == 1 ? "黑名单 · " + countSummary(BLACK_LIST_RAW, "将点赞所有人 >") : "白名单 · " + countSummary(WHITE_LIST_RAW, "未选择 >")), true);
                rowObj.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showObjectRuleUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowObj); addDivider(ctx, body);
                LinearLayout rowDelay = createRowText(ctx, "延迟点赞策略", delaySummary(), true);
                rowDelay.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showDelayRuleUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowDelay); addDivider(ctx, body);
                LinearLayout rowRefresh = createRowText(ctx, "后台刷新策略", REFRESH_ENABLE ? ((REFRESH_MODE == 0 ? "全天 · " + formatDurationShort(REFRESH_INTERVAL) : REFRESH_START + "-" + REFRESH_END + " · " + formatDurationShort(REFRESH_INTERVAL)) + " >") : "已关闭 >", true);
                rowRefresh.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showRefreshRuleUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowRefresh); addDivider(ctx, body);
                LinearLayout rowSafe = createRowText(ctx, "安全限制", safeSummary(), true);
                rowSafe.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSafetyRuleUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowSafe); addDivider(ctx, body);
                LinearLayout rowFilter = createRowText(ctx, "内容过滤", contentFilterSummary(), true);
                rowFilter.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showContentFilterUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowFilter); addDivider(ctx, body);
                LinearLayout rowNotify = createRowText(ctx, "朋友圈发布通知", (snsNotifySet == null || snsNotifySet.isEmpty() ? "未选择好友 >" : (SNS_NOTIFY_ENABLE ? (SNS_NOTIFY_TOAST ? "通知+Toast · " : "仅通知 · ") : (SNS_NOTIFY_TOAST ? "仅Toast · " : "已关闭 · ")) + "已选" + snsNotifySet.size() + "人 >"), true);
                rowNotify.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSnsNotifyUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowNotify); addDivider(ctx, body);
                LinearLayout rowLog = createRowText(ctx, "日志记录", LOG_ENABLE ? "已开启 >" : "已关闭 >", true);
                rowLog.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showLikeLogUI(ctx, new Runnable(){ public void run(){ dialog.dismiss(); showAutoLikeHomeUI(); }}); }});
                body.addView(rowLog);
                card.addView(body, bodyLp);
                LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2); actionsLp.topMargin = dp(ctx, 14);
                TextView btnReset = makeBtn(ctx, "恢复默认", false); TextView btnClose = makeBtn(ctx, "完成", true);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2); btnLp.leftMargin = dp(ctx, 10);
                actions.addView(btnReset); actions.addView(btnClose, btnLp); card.addView(actions, actionsLp);
                btnClose.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ try { putBoolean(CFG_ENABLE, swEnable.isChecked()); AUTO_LIKE_ENABLE = swEnable.isChecked(); } catch(Throwable e) { AUTO_LIKE_ENABLE = swEnable.isChecked(); } dialog.dismiss(); }});
                btnReset.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ DELAY_MODE=DEF_DELAY_MODE; FIXED_LIKE_DELAY_MS=DEF_FIXED_LIKE_DELAY_MS; REFRESH_ENABLE=DEF_REFRESH_ENABLE; REFRESH_MODE=DEF_REFRESH_MODE; REFRESH_START=DEF_REFRESH_START; REFRESH_END=DEF_REFRESH_END; SCHEDULE_ENABLE=DEF_SCHEDULE_ENABLE; SCHEDULE_START=DEF_SCHEDULE_START; SCHEDULE_END=DEF_SCHEDULE_END; SKIP_TEXT=false; SKIP_IMAGE=false; SKIP_VIDEO=false; SKIP_KEYWORDS_RAW=""; KEYWORD_FILTER_TEXT=true; KEYWORD_FILTER_IMAGE=true; KEYWORD_FILTER_VIDEO=true; UNKNOWN_TIME_POLICY=DEF_UNKNOWN_TIME_POLICY; UNKNOWN_TYPE_POLICY=DEF_UNKNOWN_TYPE_POLICY; LOG_ENABLE=false; LOG_MAX=DEF_LOG_MAX; saveRuntimeConfig(DEF_AUTO_LIKE_ENABLE, 0, "", "", DEF_REFRESH_INTERVAL, DEF_MIN_LIKE_DELAY_MS, DEF_MAX_LIKE_DELAY_MS, DEF_MAX_POST_AGE_HOURS, DEF_MAX_PROCESSED_SAVE, DEF_DUP_SUPPRESS_MS); toast("已恢复默认，自动点赞默认关闭"); dialog.dismiss(); showAutoLikeHomeUI(); }});
                finishDialogLayout(dialog, root, card);
                showDialogAnimated(dialog, card, ctx, 20, 180);
            } catch (Throwable e) { toast("打开设置失败: " + e); }
        }
    });
}

void showObjectRuleUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 18); lp.rightMargin = dp(ctx, 18); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("点赞对象规则"); title.setTextSize(19f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        LinearLayout body = new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14)); bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(ctx, 14);
        final RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.VERTICAL); rg.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        RadioButton rbW = new RadioButton(ctx); rbW.setText("只点赞白名单好友"); rbW.setId(1); rbW.setTextSize(16f);
        RadioButton rbB = new RadioButton(ctx); rbB.setText("点赞除黑名单外所有好友"); rbB.setId(2); rbB.setTextSize(16f);
        rg.addView(rbW); rg.addView(rbB); rg.check(LIST_MODE == 1 ? 2 : 1); body.addView(rg); addDivider(ctx, body);
        final TextView[] tvList = new TextView[1];
        final LinearLayout rowList = createRowText(ctx, LIST_MODE == 1 ? "黑名单好友" : "白名单好友", LIST_MODE == 1 ? countSummary(BLACK_LIST_RAW, "未排除 >") : countSummary(WHITE_LIST_RAW, "未选择 >"), true);
        tvList[0] = (TextView) rowList.getChildAt(1);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){ public void onCheckedChanged(RadioGroup g, int id){ ((TextView)rowList.getChildAt(0)).setText(id == 2 ? "黑名单好友" : "白名单好友"); tvList[0].setText(id == 2 ? countSummary(BLACK_LIST_RAW, "未排除 >") : countSummary(WHITE_LIST_RAW, "未选择 >")); }});
        rowList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ boolean black = rg.getCheckedRadioButtonId() == 2; showFriendMultiPickerUI(ctx, black ? BLACK_LIST_RAW : WHITE_LIST_RAW, black, tvList[0]); }});
        body.addView(rowList); card.addView(body, bodyLp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(ctx, 14);
        TextView clear = makeBtn(ctx, "清空", false); TextView cancel = makeBtn(ctx, "取消", false); TextView save = makeBtn(ctx, "保存", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10);
        actions.addView(clear); actions.addView(cancel, blp); actions.addView(save, blp); card.addView(actions, al);
        clear.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ if (rg.getCheckedRadioButtonId() == 2) BLACK_LIST_RAW = ""; else WHITE_LIST_RAW = ""; tvList[0].setText(rg.getCheckedRadioButtonId() == 2 ? "未排除 >" : "未选择 >"); }});
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ final int mode = rg.getCheckedRadioButtonId() == 2 ? 1 : 0; if (mode == 1 && parseWxidSet(BLACK_LIST_RAW).isEmpty()) { showBlackModeEmptyConfirmUI(ctx, new Runnable(){ public void run(){ saveRuntimeConfig(AUTO_LIKE_ENABLE, mode, WHITE_LIST_RAW, BLACK_LIST_RAW, REFRESH_INTERVAL, MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS, MAX_POST_AGE_HOURS, MAX_PROCESSED_SAVE, DUP_SUPPRESS_MS); toast("对象规则已保存"); dialog.dismiss(); if (onDone != null) onDone.run(); }}); return; } saveRuntimeConfig(AUTO_LIKE_ENABLE, mode, WHITE_LIST_RAW, BLACK_LIST_RAW, REFRESH_INTERVAL, MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS, MAX_POST_AGE_HOURS, MAX_PROCESSED_SAVE, DUP_SUPPRESS_MS); toast("对象规则已保存"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) { toast("打开对象规则失败: " + e); }
}

void showBlackModeEmptyConfirmUI(final Activity ctx, final Runnable onConfirm) {
    try {
        final Dialog d = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE); d.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 14));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 34); lp.rightMargin = dp(ctx, 34); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp);
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("确认启用黑名单模式？"); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        TextView msg = new TextView(ctx); msg.setText("当前黑名单为空，保存后会对所有捕捉到的好友朋友圈自动点赞。\n\n如果你只是想点赞指定好友，建议返回选择白名单模式。"); msg.setTextSize(15f); msg.setLineSpacing(dp(ctx, 2), 1.0f); msg.setTextColor(Color.parseColor("#475569")); LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2); mlp.topMargin = dp(ctx, 12); card.addView(msg, mlp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2); alp.topMargin = dp(ctx, 18);
        TextView cancel = makeBtn(ctx, "取消", false); TextView ok = makeBtn(ctx, "继续保存", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); actions.addView(cancel); actions.addView(ok, blp); card.addView(actions, alp);
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ d.dismiss(); }});
        ok.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ d.dismiss(); if (onConfirm != null) onConfirm.run(); }});
        finishDialogLayout(d, mask, card);
        showDialogAnimated(d, card, ctx, 14, 160);
    } catch (Throwable e) { toast("打开确认窗口失败: " + e); }
}

void showDelayRuleUI(final Activity ctx, final Runnable onDone) {
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL); FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 18); lp.rightMargin = dp(ctx, 18); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp); card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("延迟点赞策略"); title.setTextSize(19f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        TextView sub = new TextView(ctx); sub.setText("控制捕捉到朋友圈后，等待多久再点赞"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(ctx, 6); card.addView(sub, subLp);
        LinearLayout body = new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14)); bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(ctx, 14);
        final RadioGroup rgMode = new RadioGroup(ctx); rgMode.setOrientation(RadioGroup.HORIZONTAL); rgMode.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        RadioButton rbFixed = new RadioButton(ctx); rbFixed.setText("固定时间"); rbFixed.setId(1);
        RadioButton rbRandom = new RadioButton(ctx); rbRandom.setText("随机时间"); rbRandom.setId(2);
        rgMode.addView(rbFixed); rgMode.addView(rbRandom); rgMode.check(DELAY_MODE == 0 ? 1 : 2); body.addView(rgMode); addDivider(ctx, body);
        final LinearLayout fixedBox = new LinearLayout(ctx); fixedBox.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout randomBox = new LinearLayout(ctx); randomBox.setOrientation(LinearLayout.VERTICAL);
        TextView fixedTip = addSectionTitle(ctx, fixedBox, "固定时间 · 每条朋友圈都等待同样久再点赞，适合测试");
        final EditText etFixed = addInputRow(ctx, fixedBox, "等待秒数", "例如 300 = 5分钟", String.valueOf(FIXED_LIKE_DELAY_MS / 1000), android.text.InputType.TYPE_CLASS_NUMBER);
        TextView fixedDesc = new TextView(ctx); fixedDesc.setText("长期使用更建议选择随机时间，看起来更自然。"); fixedDesc.setTextSize(12f); fixedDesc.setTextColor(Color.parseColor("#64748B")); fixedDesc.setPadding(dp(ctx,24),0,dp(ctx,24),dp(ctx,10)); fixedBox.addView(fixedDesc);
        TextView randomTip = addSectionTitle(ctx, randomBox, "随机时间 · 每条朋友圈在一个范围内随机等待，推荐日常使用");
        final RadioGroup rgPreset = new RadioGroup(ctx); rgPreset.setOrientation(RadioGroup.VERTICAL); rgPreset.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        RadioButton r1 = new RadioButton(ctx); r1.setText("快速测试：5秒 ~ 15秒"); r1.setId(1);
        RadioButton r2 = new RadioButton(ctx); r2.setText("自然模式：1分钟 ~ 1小时"); r2.setId(2);
        RadioButton r3 = new RadioButton(ctx); r3.setText("慢速模式：10分钟 ~ 2小时"); r3.setId(3);
        RadioButton r4 = new RadioButton(ctx); r4.setText("自定义范围"); r4.setId(4);
        rgPreset.addView(r1); rgPreset.addView(r2); rgPreset.addView(r3); rgPreset.addView(r4); rgPreset.check(4); randomBox.addView(rgPreset); addDivider(ctx, randomBox);
        final EditText etMin = addInputRow(ctx, randomBox, "最少等待秒数", "例如 60 = 1分钟", String.valueOf(MIN_LIKE_DELAY_MS / 1000), android.text.InputType.TYPE_CLASS_NUMBER); addDivider(ctx, randomBox);
        final EditText etMax = addInputRow(ctx, randomBox, "最多等待秒数", "例如 3600 = 1小时", String.valueOf(MAX_LIKE_DELAY_MS / 1000), android.text.InputType.TYPE_CLASS_NUMBER);
        body.addView(fixedBox); body.addView(randomBox);
        final Runnable syncVisible = new Runnable(){ public void run(){ boolean fixed = rgMode.getCheckedRadioButtonId() == 1; fixedBox.setVisibility(fixed ? View.VISIBLE : View.GONE); randomBox.setVisibility(fixed ? View.GONE : View.VISIBLE); }};
        rgMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){ public void onCheckedChanged(RadioGroup g, int id){ syncVisible.run(); }});
        rgPreset.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){ public void onCheckedChanged(RadioGroup g, int id){ if (id == 1) { etMin.setText("5"); etMax.setText("15"); } else if (id == 2) { etMin.setText("60"); etMax.setText("3600"); } else if (id == 3) { etMin.setText("600"); etMax.setText("7200"); } }});
        syncVisible.run();
        card.addView(body, bodyLp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(ctx, 14); TextView cancel = makeBtn(ctx, "取消", false); TextView save = makeBtn(ctx, "保存", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); actions.addView(cancel); actions.addView(save, blp); card.addView(actions, al);
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ boolean fixed = rgMode.getCheckedRadioButtonId() == 1; int fixedMs = Math.max(1, parseIntSafe(etFixed.getText().toString(), DEF_FIXED_LIKE_DELAY_MS / 1000)) * 1000; int min = Math.max(1, parseIntSafe(etMin.getText().toString(), DEF_MIN_LIKE_DELAY_MS / 1000)) * 1000; int max = Math.max(1, parseIntSafe(etMax.getText().toString(), DEF_MAX_LIKE_DELAY_MS / 1000)) * 1000; boolean swapped = false; if (!fixed && max < min) { int t = min; min = max; max = t; swapped = true; } if (fixed) { DELAY_MODE = 0; FIXED_LIKE_DELAY_MS = fixedMs; min = fixedMs; max = fixedMs; } else { DELAY_MODE = 1; FIXED_LIKE_DELAY_MS = min; } saveRuntimeConfig(AUTO_LIKE_ENABLE, LIST_MODE, WHITE_LIST_RAW, BLACK_LIST_RAW, REFRESH_INTERVAL, min, max, MAX_POST_AGE_HOURS, MAX_PROCESSED_SAVE, DUP_SUPPRESS_MS); toast(swapped ? "最少/最多等待时间已自动调正并保存" : "延迟策略已保存"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) { toast("打开延迟策略失败: " + e); }
}

void showRefreshRuleUI(final Activity ctx, final Runnable onDone) {
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL); FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 18); lp.rightMargin = dp(ctx, 18); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp); card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("后台刷新策略"); title.setTextSize(19f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        TextView sub = new TextView(ctx); sub.setText("控制多久主动刷新一次朋友圈，用于捕捉新内容"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(ctx, 6); card.addView(sub, subLp);
        LinearLayout body = new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14)); bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(ctx, 14);
        LinearLayout enableRow = new LinearLayout(ctx); enableRow.setOrientation(LinearLayout.HORIZONTAL); enableRow.setGravity(Gravity.CENTER_VERTICAL); enableRow.setPadding(dp(ctx,14),dp(ctx,14),dp(ctx,14),dp(ctx,14)); TextView enableTv = new TextView(ctx); enableTv.setText("启用后台刷新"); enableTv.setTextSize(16f); enableTv.setTextColor(Color.parseColor("#0F172A")); enableRow.addView(enableTv, new LinearLayout.LayoutParams(0,-2,1)); final Switch swRefresh = new Switch(ctx); swRefresh.setChecked(REFRESH_ENABLE); enableRow.addView(swRefresh); body.addView(enableRow); addDivider(ctx, body);
        final RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.VERTICAL); rg.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        RadioButton r1 = new RadioButton(ctx); r1.setText("全天刷新：全天按间隔循环刷新"); r1.setId(1);
        RadioButton r2 = new RadioButton(ctx); r2.setText("时间段刷新：只在指定时间段内循环刷新"); r2.setId(2);
        rg.addView(r1); rg.addView(r2); rg.check(REFRESH_MODE == 1 ? 2 : 1); body.addView(rg); addDivider(ctx, body);
        final LinearLayout loopBox = new LinearLayout(ctx); loopBox.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout fixedBox = new LinearLayout(ctx); fixedBox.setOrientation(LinearLayout.VERTICAL);
        addSectionTitle(ctx, loopBox, "刷新间隔 · 控制后台主动检查朋友圈的频率");
        final EditText etInterval = addInputRow(ctx, loopBox, "刷新间隔秒数", "例如 4 = 每 4 秒刷新一次", String.valueOf(Math.max(1, REFRESH_INTERVAL / 1000)), android.text.InputType.TYPE_CLASS_NUMBER);
        TextView loopDesc = new TextView(ctx); loopDesc.setText("间隔越短越及时，但也更耗电。建议 300 秒左右，想更及时可适当调低。 "); loopDesc.setTextSize(12f); loopDesc.setTextColor(Color.parseColor("#64748B")); loopDesc.setPadding(dp(ctx,24),0,dp(ctx,24),dp(ctx,10)); loopBox.addView(loopDesc);
        addSectionTitle(ctx, fixedBox, "刷新时间段 · 只在下面时间段内按间隔刷新");
        final String[] refreshWindow = new String[]{REFRESH_START, REFRESH_END};
        final LinearLayout rowRefreshStart = createRowText(ctx, "开始刷新时间", refreshWindow[0] + " >", true);
        final LinearLayout rowRefreshEnd = createRowText(ctx, "停止刷新时间", refreshWindow[1] + " >", true);
        final TextView tvRefreshStart = (TextView) rowRefreshStart.getChildAt(1);
        final TextView tvRefreshEnd = (TextView) rowRefreshEnd.getChildAt(1);
        rowRefreshStart.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showTimePickerForText(ctx, refreshWindow[0], tvRefreshStart, refreshWindow, 0); }});
        rowRefreshEnd.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showTimePickerForText(ctx, refreshWindow[1], tvRefreshEnd, refreshWindow, 1); }});
        fixedBox.addView(rowRefreshStart); addDivider(ctx, fixedBox); fixedBox.addView(rowRefreshEnd);
        TextView fixedRefreshDesc = new TextView(ctx); fixedRefreshDesc.setText("支持跨天，例如 23:00 到 07:00。时间段外不会主动刷新。 "); fixedRefreshDesc.setTextSize(12f); fixedRefreshDesc.setTextColor(Color.parseColor("#64748B")); fixedRefreshDesc.setPadding(dp(ctx,24),dp(ctx,8),dp(ctx,24),dp(ctx,10)); fixedBox.addView(fixedRefreshDesc);
        body.addView(loopBox); body.addView(fixedBox);
        final Runnable syncVisible = new Runnable(){ public void run(){ boolean fixed = rg.getCheckedRadioButtonId() == 2; loopBox.setVisibility(View.VISIBLE); fixedBox.setVisibility(fixed ? View.VISIBLE : View.GONE); }};
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){ public void onCheckedChanged(RadioGroup g, int id){ syncVisible.run(); }});
        syncVisible.run();
        card.addView(body, bodyLp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(ctx, 14); TextView cancel = makeBtn(ctx, "取消", false); TextView save = makeBtn(ctx, "保存", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); actions.addView(cancel); actions.addView(save, blp); card.addView(actions, al);
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ REFRESH_ENABLE = swRefresh.isChecked(); REFRESH_MODE = rg.getCheckedRadioButtonId() == 2 ? 1 : 0; REFRESH_START = normalizeTime(refreshWindow[0], DEF_REFRESH_START); REFRESH_END = normalizeTime(refreshWindow[1], DEF_REFRESH_END); int refresh = Math.max(1, parseIntSafe(etInterval.getText().toString(), DEF_REFRESH_INTERVAL / 1000)) * 1000; saveRuntimeConfig(AUTO_LIKE_ENABLE, LIST_MODE, WHITE_LIST_RAW, BLACK_LIST_RAW, refresh, MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS, MAX_POST_AGE_HOURS, MAX_PROCESSED_SAVE, DUP_SUPPRESS_MS); toast(REFRESH_ENABLE ? "刷新策略已保存" : "后台刷新已关闭"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) { toast("打开刷新策略失败: " + e); }
}

void showSafetyRuleUI(final Activity ctx, final Runnable onDone) {
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL); FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 18); lp.rightMargin = dp(ctx, 18); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp); card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("安全限制"); title.setTextSize(19f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        TextView sub = new TextView(ctx); sub.setText("控制点赞范围、运行时间，以及防重复处理规则"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.topMargin = dp(ctx, 6); card.addView(sub, subLp);
        LinearLayout body = new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14)); bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2); bodyLp.topMargin = dp(ctx, 14);
        TextView sec1 = addSectionTitle(ctx, body, "点赞范围"); sec1.setText("点赞范围 · 只处理最近发布的朋友圈");
        final EditText etAge = addInputRow(ctx, body, "只点赞最近多少小时内", "例如 24", String.valueOf(MAX_POST_AGE_HOURS), android.text.InputType.TYPE_CLASS_NUMBER);
        TextView ageTip = new TextView(ctx); ageTip.setText("建议 24 小时内，避免点赞太久以前的内容。"); ageTip.setTextSize(12f); ageTip.setTextColor(Color.parseColor("#64748B")); ageTip.setPadding(dp(ctx,24),0,dp(ctx,24),dp(ctx,10)); body.addView(ageTip); addDivider(ctx, body);
        TextView sec2 = addSectionTitle(ctx, body, "运行时间"); sec2.setText("运行时间 · 可限制每天只在指定时段工作");
        LinearLayout schRow = new LinearLayout(ctx); schRow.setOrientation(LinearLayout.HORIZONTAL); schRow.setGravity(Gravity.CENTER_VERTICAL); schRow.setPadding(dp(ctx,14),dp(ctx,14),dp(ctx,14),dp(ctx,14)); TextView schTv = new TextView(ctx); schTv.setText("只在下面时间段内自动点赞"); schTv.setTextSize(16f); schTv.setTextColor(Color.parseColor("#0F172A")); schRow.addView(schTv, new LinearLayout.LayoutParams(0,-2,1)); final Switch swSch = new Switch(ctx); swSch.setChecked(SCHEDULE_ENABLE); schRow.addView(swSch); body.addView(schRow); addDivider(ctx, body);
        final String[] workWindow = new String[]{SCHEDULE_START, SCHEDULE_END};
        final LinearLayout rowWorkStart = createRowText(ctx, "开始工作时间", workWindow[0] + " >", true);
        final LinearLayout rowWorkEnd = createRowText(ctx, "停止工作时间", workWindow[1] + " >", true);
        final TextView tvWorkStart = (TextView) rowWorkStart.getChildAt(1);
        final TextView tvWorkEnd = (TextView) rowWorkEnd.getChildAt(1);
        View.OnClickListener startTimeClick = new View.OnClickListener(){ public void onClick(View v){ if (!swSch.isChecked()) swSch.setChecked(true); showTimePickerForText(ctx, workWindow[0], tvWorkStart, workWindow, 0); }};
        View.OnClickListener endTimeClick = new View.OnClickListener(){ public void onClick(View v){ if (!swSch.isChecked()) swSch.setChecked(true); showTimePickerForText(ctx, workWindow[1], tvWorkEnd, workWindow, 1); }};
        rowWorkStart.setOnClickListener(startTimeClick); tvWorkStart.setOnClickListener(startTimeClick);
        rowWorkEnd.setOnClickListener(endTimeClick); tvWorkEnd.setOnClickListener(endTimeClick);
        body.addView(rowWorkStart); addDivider(ctx, body); body.addView(rowWorkEnd);
        TextView timeTip = new TextView(ctx); timeTip.setText("每天按该时间段运行，支持跨天，例如 23:00 到 07:00。"); timeTip.setTextSize(12f); timeTip.setTextColor(Color.parseColor("#64748B")); timeTip.setPadding(dp(ctx,24),dp(ctx,8),dp(ctx,24),dp(ctx,10)); body.addView(timeTip); addDivider(ctx, body);
        final LinearLayout advBox = new LinearLayout(ctx); advBox.setOrientation(LinearLayout.VERTICAL); advBox.setVisibility(View.GONE);
        final ScrollView[] safetyScrollRef = new ScrollView[1];
        final TextView advToggle = addSectionTitle(ctx, body, "高级防重复设置 · 默认即可，点击展开 >");
        advToggle.setTextColor(Color.parseColor("#2563EB"));
        advToggle.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ boolean show = advBox.getVisibility() != View.VISIBLE; advBox.setVisibility(show ? View.VISIBLE : View.GONE); advToggle.setText(show ? "高级防重复设置 · 点击收起 ∧" : "高级防重复设置 · 默认即可，点击展开 >"); try { if (safetyScrollRef[0] != null) { ViewGroup.LayoutParams lp2 = safetyScrollRef[0].getLayoutParams(); lp2.height = show ? dp(ctx, 520) : -2; safetyScrollRef[0].setLayoutParams(lp2); } } catch(Throwable ignored){} }});
        TextView sec3 = addSectionTitle(ctx, advBox, "防重复 · 避免同一条朋友圈被反复处理");
        final EditText etDup = addInputRow(ctx, advBox, "同一条朋友圈的重复忽略时间", "例如 20", String.valueOf(Math.max(1, DUP_SUPPRESS_MS / 1000)), android.text.InputType.TYPE_CLASS_NUMBER);
        TextView dupTip = new TextView(ctx); dupTip.setText("单位：秒。比如填 20，表示 20 秒内重复捕捉到同一条朋友圈会忽略。"); dupTip.setTextSize(12f); dupTip.setTextColor(Color.parseColor("#64748B")); dupTip.setPadding(dp(ctx, 24), dp(ctx, 0), dp(ctx, 24), dp(ctx, 10)); advBox.addView(dupTip); addDivider(ctx, advBox);
        final EditText etSave = addInputRow(ctx, advBox, "记住已处理记录数量", "例如 300", String.valueOf(MAX_PROCESSED_SAVE), android.text.InputType.TYPE_CLASS_NUMBER);
        TextView saveTip = new TextView(ctx); saveTip.setText("用于记录已经处理过的朋友圈，避免重复点赞。一般保持默认 300 即可。"); saveTip.setTextSize(12f); saveTip.setTextColor(Color.parseColor("#64748B")); saveTip.setPadding(dp(ctx, 24), dp(ctx, 0), dp(ctx, 24), dp(ctx, 10)); advBox.addView(saveTip);
        body.addView(advBox);
        final Runnable syncVisible = new Runnable(){ public void run(){ boolean on = swSch.isChecked(); tvWorkStart.setTextColor(on ? Color.parseColor("#475569") : Color.parseColor("#94A3B8")); tvWorkEnd.setTextColor(on ? Color.parseColor("#475569") : Color.parseColor("#94A3B8")); }};
        swSch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){ public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){ syncVisible.run(); }});
        syncVisible.run();
        ScrollView sv = new ScrollView(ctx); safetyScrollRef[0] = sv; sv.setFillViewport(false); sv.addView(body); LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(-1, -2); svLp.topMargin = dp(ctx, 14); card.addView(sv, svLp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(ctx, 12); TextView cancel = makeBtn(ctx, "取消", false); TextView save = makeBtn(ctx, "保存", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); actions.addView(cancel); actions.addView(save, blp); card.addView(actions, al);
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ int age = parseIntSafe(etAge.getText().toString(), DEF_MAX_POST_AGE_HOURS); int dup = Math.max(1, parseIntSafe(etDup.getText().toString(), DEF_DUP_SUPPRESS_MS / 1000)) * 1000; int maxSave = parseIntSafe(etSave.getText().toString(), DEF_MAX_PROCESSED_SAVE); SCHEDULE_ENABLE = swSch.isChecked(); SCHEDULE_START = normalizeTime(workWindow[0], DEF_SCHEDULE_START); SCHEDULE_END = normalizeTime(workWindow[1], DEF_SCHEDULE_END); UNKNOWN_TIME_POLICY = DEF_UNKNOWN_TIME_POLICY; saveRuntimeConfig(AUTO_LIKE_ENABLE, LIST_MODE, WHITE_LIST_RAW, BLACK_LIST_RAW, REFRESH_INTERVAL, MIN_LIKE_DELAY_MS, MAX_LIKE_DELAY_MS, age, maxSave, dup); toast("安全限制已保存"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) { toast("打开安全限制失败: " + e); }
}

void showContentFilterUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout mask = new FrameLayout(ctx);
        mask.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(ctx, 18);
        lp.rightMargin = dp(ctx, 18);
        lp.gravity = Gravity.CENTER;
        card.setLayoutParams(lp);
card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));

        TextView title = new TextView(ctx);
        title.setText("内容过滤");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);
        TextView sub=new TextView(ctx); sub.setText("选择要配置的过滤规则"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(ctx,6); card.addView(sub,subLp);
        LinearLayout body=new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg=roundRect(Color.parseColor("#F8FAFC"),dp(ctx,14)); bodyBg.setStroke(dp(ctx,1),Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,-2); bodyLp.topMargin=dp(ctx,14);
        LinearLayout rowType = createRowText(ctx, "类型过滤", (SKIP_TEXT || SKIP_IMAGE || SKIP_VIDEO) ? "已启用 >" : "未启用 >", true);
        LinearLayout rowKw = createRowText(ctx, "关键词过滤", (SKIP_KEYWORDS_RAW != null && SKIP_KEYWORDS_RAW.trim().length() > 0) ? "已设置 >" : "未设置 >", true);
        rowType.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showTypeFilterUI(ctx, onDone); }});
        rowKw.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showKeywordFilterUI(ctx, onDone); }});
        body.addView(rowType); addDivider(ctx, body); body.addView(rowKw); card.addView(body, bodyLp);
        LinearLayout actions=new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2); al.topMargin=dp(ctx,14); TextView close=makeBtn(ctx,"关闭",true); actions.addView(close); card.addView(actions,al);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch(Throwable e){ toast("打开内容过滤失败: "+e); }
}

void showTypeFilterUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout mask = new FrameLayout(ctx);
        mask.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(ctx, 18);
        lp.rightMargin = dp(ctx, 18);
        lp.gravity = Gravity.CENTER;
        card.setLayoutParams(lp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));

        TextView title = new TextView(ctx);
        title.setText("类型过滤");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);
        TextView sub=new TextView(ctx); sub.setText("命中以下类型时直接跳过，不再判断关键词"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(ctx,6); card.addView(sub,subLp);
        LinearLayout body=new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg=roundRect(Color.parseColor("#F8FAFC"),dp(ctx,14)); bodyBg.setStroke(dp(ctx,1),Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,-2); bodyLp.topMargin=dp(ctx,14);
        final Switch swText=new Switch(ctx); final Switch swImg=new Switch(ctx); final Switch swVideo=new Switch(ctx);
        addSwitchLine(ctx, body, "跳过纯文本朋友圈", swText, SKIP_TEXT); addDivider(ctx, body); addSwitchLine(ctx, body, "跳过图文朋友圈", swImg, SKIP_IMAGE); addDivider(ctx, body); addSwitchLine(ctx, body, "跳过视频朋友圈", swVideo, SKIP_VIDEO);
        card.addView(body, bodyLp);
        LinearLayout actions=new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2); al.topMargin=dp(ctx,14); TextView back=makeBtn(ctx,"返回",false); TextView save=makeBtn(ctx,"保存",true); LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2); blp.leftMargin=dp(ctx,10); actions.addView(back); actions.addView(save,blp); card.addView(actions,al);
        back.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showContentFilterUI(ctx, onDone); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ SKIP_TEXT=swText.isChecked(); SKIP_IMAGE=swImg.isChecked(); SKIP_VIDEO=swVideo.isChecked(); UNKNOWN_TYPE_POLICY=DEF_UNKNOWN_TYPE_POLICY; saveRuntimeConfig(AUTO_LIKE_ENABLE,LIST_MODE,WHITE_LIST_RAW,BLACK_LIST_RAW,REFRESH_INTERVAL,MIN_LIKE_DELAY_MS,MAX_LIKE_DELAY_MS,MAX_POST_AGE_HOURS,MAX_PROCESSED_SAVE,DUP_SUPPRESS_MS); toast("类型过滤已保存"); dialog.dismiss(); if(onDone!=null)onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch(Throwable e){ toast("打开类型过滤失败: "+e); }
}

void showKeywordFilterUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        FrameLayout mask = new FrameLayout(ctx);
        mask.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(ctx, 18);
        lp.rightMargin = dp(ctx, 18);
        lp.gravity = Gravity.CENTER;
        card.setLayoutParams(lp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));

        TextView title = new TextView(ctx);
        title.setText("关键词过滤");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);
        TextView sub=new TextView(ctx); sub.setText("只对开启的朋友圈类型检查文案"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2); subLp.topMargin=dp(ctx,6); card.addView(sub,subLp);
        LinearLayout body=new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg=roundRect(Color.parseColor("#F8FAFC"),dp(ctx,14)); bodyBg.setStroke(dp(ctx,1),Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,-2); bodyLp.topMargin=dp(ctx,14);
        final Switch swKwText=new Switch(ctx); final Switch swKwImg=new Switch(ctx); final Switch swKwVideo=new Switch(ctx);
        final EditText etKeywords = addInputRow(ctx, body, "关键词列表", "例如：广告,推广,中奖", SKIP_KEYWORDS_RAW, android.text.InputType.TYPE_CLASS_TEXT); addDivider(ctx, body);
        addSwitchLine(ctx, body, "纯文本朋友圈过滤", swKwText, KEYWORD_FILTER_TEXT); addDivider(ctx, body); addSwitchLine(ctx, body, "图文朋友圈过滤", swKwImg, KEYWORD_FILTER_IMAGE); addDivider(ctx, body); addSwitchLine(ctx, body, "视频加文字朋友圈过滤", swKwVideo, KEYWORD_FILTER_VIDEO);
        TextView tip = new TextView(ctx); tip.setText("多个关键词用逗号或换行分隔。关闭某一类型后，该类型朋友圈即使命中关键词也不会因此跳过。"); tip.setTextSize(12f); tip.setTextColor(Color.parseColor("#64748B")); tip.setPadding(dp(ctx,24),dp(ctx,6),dp(ctx,24),dp(ctx,10)); body.addView(tip);
        card.addView(body, bodyLp);
        LinearLayout actions=new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2); al.topMargin=dp(ctx,14); TextView back=makeBtn(ctx,"返回",false); TextView save=makeBtn(ctx,"保存",true); LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2); blp.leftMargin=dp(ctx,10); actions.addView(back); actions.addView(save,blp); card.addView(actions,al);
        back.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); showContentFilterUI(ctx, onDone); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ SKIP_KEYWORDS_RAW=etKeywords.getText().toString(); KEYWORD_FILTER_TEXT=swKwText.isChecked(); KEYWORD_FILTER_IMAGE=swKwImg.isChecked(); KEYWORD_FILTER_VIDEO=swKwVideo.isChecked(); skipKeywordSet=parseKeywordSet(SKIP_KEYWORDS_RAW); saveRuntimeConfig(AUTO_LIKE_ENABLE,LIST_MODE,WHITE_LIST_RAW,BLACK_LIST_RAW,REFRESH_INTERVAL,MIN_LIKE_DELAY_MS,MAX_LIKE_DELAY_MS,MAX_POST_AGE_HOURS,MAX_PROCESSED_SAVE,DUP_SUPPRESS_MS); toast("关键词过滤已保存"); dialog.dismiss(); if(onDone!=null)onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch(Throwable e){ toast("打开关键词过滤失败: "+e); }
}

Button makeVarBtn(final Activity ctx, String text, final String var, final EditText target) {
    Button b = new Button(ctx);
    b.setText(text);
    b.setTextSize(11f);
    b.setAllCaps(false);
    b.setTextColor(Color.parseColor("#2563EB"));
    GradientDrawable bg = roundRect(Color.parseColor("#EFF6FF"), dp(ctx, 10));
    bg.setStroke(dp(ctx, 1), Color.parseColor("#BFDBFE"));
    b.setBackground(bg);
    b.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
    lp.leftMargin = dp(ctx, 3); lp.rightMargin = dp(ctx, 3); lp.topMargin = dp(ctx, 6); lp.bottomMargin = dp(ctx, 6);
    b.setLayoutParams(lp);
    b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        try {
            int st = Math.max(0, target.getSelectionStart());
            int en = Math.max(0, target.getSelectionEnd());
            int a = Math.min(st, en), z = Math.max(st, en);
            Editable e = target.getText();
            e.replace(a, z, var);
            target.requestFocus();
            target.setSelection(a + var.length());
        } catch (Throwable ex) {
            try { target.append(var); } catch (Throwable ignored) {}
        }
    }});
    return b;
}

void addSnsTemplateVarButtons(final Activity ctx, LinearLayout body, final EditText target) {
    try {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(ctx, 10), 0, dp(ctx, 10), dp(ctx, 4));
        row.addView(makeVarBtn(ctx, "昵称", "%sender%", target));
        row.addView(makeVarBtn(ctx, "wxid", "%wxid%", target));
        row.addView(makeVarBtn(ctx, "类型", "%type%", target));
        row.addView(makeVarBtn(ctx, "正文", "%content%", target));
        row.addView(makeVarBtn(ctx, "snsId", "%snsid%", target));
        body.addView(row);
    } catch (Throwable ignored) {}
}

void addSwitchLine(Activity ctx, LinearLayout body, String text, Switch sw, boolean checked) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));

    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(16f);
    tv.setTextColor(Color.parseColor("#0F172A"));
    row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

    sw.setChecked(checked);
    row.addView(sw);
    body.addView(row);
}

void showSnsNotifyUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2); lp.leftMargin = dp(ctx, 18); lp.rightMargin = dp(ctx, 18); lp.gravity = Gravity.CENTER; card.setLayoutParams(lp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12)); card.setBackground(makeCardGradientBg(ctx, 20, "#DDE6F2"));
        TextView title = new TextView(ctx); title.setText("朋友圈发布通知"); title.setTextSize(19f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); card.addView(title);
        TextView sub = new TextView(ctx); sub.setText("只监听下面单独选择的好友，和自动点赞白名单/黑名单互不影响。"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1,-2); subLp.topMargin = dp(ctx,6); card.addView(sub, subLp);
        LinearLayout body = new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx,14)); bodyBg.setStroke(dp(ctx,1), Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1,-2); bodyLp.topMargin = dp(ctx,14);
        final Switch swNotify = new Switch(ctx); final Switch swToast = new Switch(ctx);
        final TextView[] tvList = new TextView[1];
        LinearLayout rowList = createRowText(ctx, "通知好友名单", countSummary(SNS_NOTIFY_LIST_RAW, "未选择 >"), true);
        tvList[0] = (TextView) rowList.getChildAt(1);
        try { tvList[0].setTag("sns_notify_list"); } catch (Throwable ignored) {}
        rowList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showFriendMultiPickerUI(ctx, SNS_NOTIFY_LIST_RAW, false, tvList[0]); }});
        body.addView(rowList); addDivider(ctx, body);
        addSwitchLine(ctx, body, "系统通知", swNotify, SNS_NOTIFY_ENABLE); addDivider(ctx, body); addSwitchLine(ctx, body, "Toast提示", swToast, SNS_NOTIFY_TOAST); addDivider(ctx, body);
        final EditText etTitle = addInputRow(ctx, body, "通知标题模板", "默认：📣 指定好友发布朋友圈", SNS_NOTIFY_TITLE_TPL, android.text.InputType.TYPE_CLASS_TEXT); addSnsTemplateVarButtons(ctx, body, etTitle); addDivider(ctx, body);
        final EditText etBody = addInputRow(ctx, body, "通知内容模板", "默认：%sender% 发布了%type%朋友圈：%content%", SNS_NOTIFY_BODY_TPL, android.text.InputType.TYPE_CLASS_TEXT); addSnsTemplateVarButtons(ctx, body, etBody); addDivider(ctx, body);
        final EditText etToast = addInputRow(ctx, body, "Toast模板", "默认：📣 %sender% 发布了%type%朋友圈", SNS_NOTIFY_TOAST_TPL, android.text.InputType.TYPE_CLASS_TEXT); addSnsTemplateVarButtons(ctx, body, etToast);
        TextView tip = new TextView(ctx); tip.setText("名单为空时不会提醒。支持变量：%sender% 昵称，%wxid% 微信ID，%type% 类型，%content% 正文，%snsid% 朋友圈ID。模板留空使用默认。通知点击后会打开朋友圈页。 "); tip.setTextSize(12f); tip.setTextColor(Color.parseColor("#64748B")); tip.setPadding(dp(ctx,24),dp(ctx,6),dp(ctx,24),dp(ctx,10)); body.addView(tip);
        card.addView(body, bodyLp);
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1,-2); al.topMargin = dp(ctx,14); TextView cancel = makeBtn(ctx,"取消",false); TextView save = makeBtn(ctx,"保存",true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2,-2); blp.leftMargin = dp(ctx,10); actions.addView(cancel); actions.addView(save,blp); card.addView(actions,al);
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ SNS_NOTIFY_ENABLE = swNotify.isChecked(); SNS_NOTIFY_TOAST = swToast.isChecked(); SNS_NOTIFY_TITLE_TPL = etTitle.getText().toString(); SNS_NOTIFY_BODY_TPL = etBody.getText().toString(); SNS_NOTIFY_TOAST_TPL = etToast.getText().toString(); snsNotifySet = parseWxidSet(SNS_NOTIFY_LIST_RAW); saveAdvancedConfig(); toast("朋友圈发布通知已保存"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) { toast("打开朋友圈通知设置失败: " + e); }
}

void showLikeLogUI(final Activity ctx, final Runnable onDone) {
    try {
        loadRuntimeConfig();
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx);
        mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.leftMargin = dp(ctx, 14);
        lp.rightMargin = dp(ctx, 14);
        lp.topMargin = dp(ctx, 36);
        lp.bottomMargin = dp(ctx, 36);
        card.setLayoutParams(lp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        GradientDrawable bg = roundRect(Color.parseColor("#FCFCFD"), dp(ctx, 22));
        bg.setStroke(dp(ctx, 1), Color.parseColor("#E6EAF0"));
        card.setBackground(bg);
        TextView title = new TextView(ctx);
        title.setText("点赞日志");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        final String successText=getString(CFG_LIKE_SUCCESS_LOGS,"");
        final String skipText=getString(CFG_LIKE_SKIP_LOGS,"");
        final String refreshText=getString(CFG_REFRESH_LOGS,"");
        final String commonText=getString(CFG_LIKE_LOGS,"");

        final RadioGroup rgFilter = new RadioGroup(ctx);
        rgFilter.setOrientation(RadioGroup.HORIZONTAL);
        rgFilter.setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 4));
        RadioButton rbAll = new RadioButton(ctx); rbAll.setId(1001); rbAll.setText("全部");
        RadioButton rbSucc = new RadioButton(ctx); rbSucc.setId(1002); rbSucc.setText("成功");
        RadioButton rbSkip = new RadioButton(ctx); rbSkip.setId(1003); rbSkip.setText("跳过");
        RadioButton rbRefresh = new RadioButton(ctx); rbRefresh.setId(1005); rbRefresh.setText("刷新");
        RadioButton rbOther = new RadioButton(ctx); rbOther.setId(1004); rbOther.setText("其他");
        rgFilter.addView(rbAll); rgFilter.addView(rbSucc); rgFilter.addView(rbSkip); rgFilter.addView(rbRefresh); rgFilter.addView(rbOther);
        rgFilter.check(1001);
        card.addView(rgFilter);

        LinearLayout body=new LinearLayout(ctx); body.setOrientation(LinearLayout.VERTICAL); GradientDrawable bodyBg=roundRect(Color.parseColor("#F8FAFC"),dp(ctx,14)); bodyBg.setStroke(dp(ctx,1),Color.parseColor("#E2E8F0")); body.setBackground(bodyBg); LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,-2); bodyLp.topMargin=dp(ctx,12);
        final Switch swLog=new Switch(ctx); addSwitchLine(ctx, body, "启用日志记录", swLog, LOG_ENABLE);
        TextView tip=new TextView(ctx); tip.setText("关闭后不再写入新的点赞日志，已有日志不会自动删除。默认关闭。 "); tip.setTextSize(12f); tip.setTextColor(Color.parseColor("#64748B")); tip.setPadding(dp(ctx,24),0,dp(ctx,24),dp(ctx,10)); body.addView(tip);
        card.addView(body, bodyLp);

        final TextView logs=new TextView(ctx);
        final Runnable refreshLogs = new Runnable(){ public void run(){
            int mode = rgFilter.getCheckedRadioButtonId();
            StringBuilder logSb = new StringBuilder();
            if (mode == 1001 || mode == 1002) {
                if (mode == 1001) logSb.append("===== 点赞成功 =====\n");
                logSb.append(successText==null || successText.length()==0 ? "暂无" : successText);
                if (mode == 1001) logSb.append("\n\n");
            }
            if (mode == 1001 || mode == 1003) {
                if (mode == 1001) logSb.append("===== 跳过点赞 =====\n");
                logSb.append(skipText==null || skipText.length()==0 ? "暂无" : skipText);
                if (mode == 1001) logSb.append("\n\n");
            }
            if (mode == 1001 || mode == 1005) {
                if (mode == 1001) logSb.append("===== 定时刷新 =====\n");
                logSb.append(refreshText==null || refreshText.length()==0 ? "暂无" : refreshText);
                if (mode == 1001) logSb.append("\n\n");
            }
            if (mode == 1001 || mode == 1004) {
                if (mode == 1001) logSb.append("===== 其他日志 =====\n");
                logSb.append(commonText==null || commonText.length()==0 ? "暂无" : commonText);
            }
            logs.setText(logSb.length()==0 ? "暂无日志" : logSb.toString());
        }};
        rgFilter.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){ public void onCheckedChanged(RadioGroup g, int checkedId){ refreshLogs.run(); }});
        refreshLogs.run();
        logs.setTextSize(13f); logs.setTextColor(Color.parseColor("#334155")); logs.setPadding(dp(ctx,10),dp(ctx,10),dp(ctx,10),dp(ctx,10));

        ScrollView sv=new ScrollView(ctx); sv.addView(logs); LinearLayout.LayoutParams svLp=new LinearLayout.LayoutParams(-1,0,1f); svLp.topMargin=dp(ctx,12); card.addView(sv,svLp);
        LinearLayout actions=new LinearLayout(ctx); actions.setGravity(Gravity.END); TextView clear=makeBtn(ctx,"清空日志",false); TextView save=makeBtn(ctx,"保存",false); TextView close=makeBtn(ctx,"关闭",true); LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2); blp.leftMargin=dp(ctx,10); actions.addView(clear); actions.addView(save,blp); actions.addView(close,blp); card.addView(actions);
        clear.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ putString(CFG_LIKE_LOGS,""); putString(CFG_LIKE_SUCCESS_LOGS,""); putString(CFG_LIKE_SKIP_LOGS,""); putString(CFG_REFRESH_LOGS,""); notifiedSnsIds.clear(); clearPluginLogFiles(); toast("日志已清空"); dialog.dismiss(); }});
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ LOG_ENABLE=swLog.isChecked(); saveAdvancedConfig(); toast(LOG_ENABLE ? "日志记录已开启" : "日志记录已关闭"); dialog.dismiss(); if (onDone != null) onDone.run(); }});
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        finishDialogLayout(dialog, mask, card);
        dialog.show();
    } catch (Throwable e) {
        toast("打开日志失败: " + e);
    }
}

String getContactLabelIdSafe(Object label) {
    try { Object v = XposedHelpers.callMethod(label, "getLabelId"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.callMethod(label, "getLabelID"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.callMethod(label, "getId"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.getObjectField(label, "labelId"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.getObjectField(label, "labelID"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.getObjectField(label, "id"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    return "";
}

String getContactLabelNameSafe(Object label) {
    try { Object v = XposedHelpers.callMethod(label, "getLabelName"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.callMethod(label, "getName"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.getObjectField(label, "labelName"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    try { Object v = XposedHelpers.getObjectField(label, "name"); if (v != null) return String.valueOf(v); } catch (Throwable ignored) {}
    return String.valueOf(label);
}

void showContactLabelPickerUI(final Activity ctx, final HashSet selected, final Runnable onChanged) {
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout root = new LinearLayout(ctx); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 12));
        GradientDrawable rootBg = roundRect(Color.parseColor("#FCFCFD"), dp(ctx, 22)); rootBg.setStroke(dp(ctx, 1), Color.parseColor("#E6EAF0")); root.setBackground(rootBg);
        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(-1, -1); rootLp.leftMargin = dp(ctx, 18); rootLp.rightMargin = dp(ctx, 18); rootLp.topMargin = dp(ctx, 52); rootLp.bottomMargin = dp(ctx, 52); root.setLayoutParams(rootLp);
        TextView title = new TextView(ctx); title.setText("选择标签"); title.setTextSize(22f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); root.addView(title);
        TextView sub = new TextView(ctx); sub.setText("点击标签后查看该标签成员，可勾选要加入名单的好友"); sub.setTextSize(13f); sub.setTextColor(Color.parseColor("#64748B")); sub.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8)); root.addView(sub);
        final List display = new ArrayList(); final List labelIds = new ArrayList(); final List labelNames = new ArrayList();
        try {
            List labels = getContactLabelList();
            if (labels != null) {
                for (int i = 0; i < labels.size(); i++) {
                    Object lb = labels.get(i); if (lb == null) continue;
                    String lid = getContactLabelIdSafe(lb); String ln = getContactLabelNameSafe(lb);
                    if (lid == null) lid = ""; if (ln == null || ln.length() == 0) ln = "未命名标签";
                    List members = null; try { if (lid.length() > 0) members = getContactByLabelId(lid); } catch (Throwable ignored) {}
                    int cnt = members == null ? 0 : members.size();
                    display.add(ln + "  ·  " + cnt + "人\nID: " + lid);
                    labelIds.add(lid); labelNames.add(ln);
                }
            }
        } catch (Throwable e) { toast("读取标签失败: " + e); }
        final ListView lv = new ListView(ctx); lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E9EDF3"))); lv.setDividerHeight(1); lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
        final ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, display); lv.setAdapter(adapter);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(-1, 0, 1f); lvp.topMargin = dp(ctx, 6); root.addView(lv, lvp);
        if (display.isEmpty()) { display.add("当前没有可用标签"); adapter.notifyDataSetChanged(); }
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){ public void onItemClick(AdapterView p, View v, int pos, long id){ try { if (pos < 0 || pos >= labelIds.size()) return; showContactLabelMemberPickerUI(ctx, String.valueOf(labelIds.get(pos)), String.valueOf(labelNames.get(pos)), selected, onChanged); } catch(Throwable e){ toast("打开标签成员失败: " + e); } }});
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2); alp.topMargin = dp(ctx, 12); TextView close = makeBtn(ctx, "关闭", true); actions.addView(close); root.addView(actions, alp);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        finishDialogLayout(dialog, mask, root); dialog.show();
    } catch (Throwable e) { toast("打开标签列表失败: " + e); }
}

void showContactLabelMemberPickerUI(final Activity ctx, final String labelId, final String labelName, final HashSet selected, final Runnable onChanged) {
    try {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout root = new LinearLayout(ctx); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 12));
        GradientDrawable rootBg = roundRect(Color.parseColor("#FCFCFD"), dp(ctx, 22)); rootBg.setStroke(dp(ctx, 1), Color.parseColor("#E6EAF0")); root.setBackground(rootBg);
        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(-1, -1); rootLp.leftMargin = dp(ctx, 10); rootLp.rightMargin = dp(ctx, 10); rootLp.topMargin = dp(ctx, 44); rootLp.bottomMargin = dp(ctx, 44); root.setLayoutParams(rootLp);
        TextView title = new TextView(ctx); title.setText("标签成员 · " + labelName); title.setTextSize(21f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); root.addView(title);
        final TextView count = new TextView(ctx); count.setTextColor(Color.parseColor("#64748B")); count.setTextSize(12f); count.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 4)); root.addView(count);
        final List display = new ArrayList(); final List memberIds = new ArrayList();
        try {
            List members = getContactByLabelId(labelId);
            if (members == null) members = new ArrayList();
            for (int i = 0; i < members.size(); i++) {
                String wxid = String.valueOf(members.get(i)); if (wxid == null || wxid.length() == 0) continue;
                String name = getFriendDisplayNameSafe(wxid);
                display.add(name + "\n" + wxid); memberIds.add(wxid);
            }
        } catch (Throwable e) { toast("读取标签成员失败: " + e); }
        final ListView lv = new ListView(ctx); lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE); lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E9EDF3"))); lv.setDividerHeight(1); lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000")));
        final ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, display); lv.setAdapter(adapter);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(-1, 0, 1f); lvp.topMargin = dp(ctx, 6); root.addView(lv, lvp);
        final Runnable refresh = new Runnable(){ public void run(){ try { for (int i=0;i<memberIds.size();i++) lv.setItemChecked(i, selected.contains(String.valueOf(memberIds.get(i)))); count.setText("标签成员 " + memberIds.size() + " 人 · 总已选 " + selected.size() + " 人"); } catch(Throwable ignored){} }};
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){ public void onItemClick(AdapterView p, View v, int pos, long id){ String wxid=String.valueOf(memberIds.get(pos)); if (lv.isItemChecked(pos)) selected.add(wxid); else selected.remove(wxid); refresh.run(); if (onChanged != null) onChanged.run(); }});
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); actions.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2); alp.topMargin = dp(ctx, 12);
        LinearLayout batch = new LinearLayout(ctx); batch.setGravity(Gravity.END); TextView addAll = makeBtn(ctx, "全选本标签", false); TextView invert = makeBtn(ctx, "反选本标签", false); LinearLayout.LayoutParams blpSmall = new LinearLayout.LayoutParams(-2, -2); blpSmall.leftMargin = dp(ctx, 10); batch.addView(addAll); batch.addView(invert, blpSmall); actions.addView(batch);
        LinearLayout main = new LinearLayout(ctx); main.setGravity(Gravity.END); LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2); mlp.topMargin = dp(ctx, 10); TextView cancel = makeBtn(ctx, "返回", false); TextView ok = makeBtn(ctx, "完成", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); main.addView(cancel); main.addView(ok, blp); actions.addView(main, mlp); root.addView(actions, alp);
        addAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ for (int i=0;i<memberIds.size();i++) selected.add(String.valueOf(memberIds.get(i))); refresh.run(); if (onChanged != null) onChanged.run(); }});
        invert.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ for (int i=0;i<memberIds.size();i++){ String wxid=String.valueOf(memberIds.get(i)); if (selected.contains(wxid)) selected.remove(wxid); else selected.add(wxid); } refresh.run(); if (onChanged != null) onChanged.run(); }});
        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        ok.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ if (onChanged != null) onChanged.run(); dialog.dismiss(); }});
        finishDialogLayout(dialog, mask, root); dialog.show(); refresh.run();
    } catch (Throwable e) { toast("打开标签成员失败: " + e); }
}

void showFriendMultiPickerUI(final Activity ctx, final String rawSelected, final boolean blackMode, final TextView tvValue) {
    if (sCachedFriendNames != null && sCachedFriendIds != null) {
        buildFriendMultiPickerUI(ctx, rawSelected, blackMode, tvValue, sCachedFriendNames, sCachedFriendIds);
        return;
    }
    final Dialog[] loadingRef = new Dialog[1];
    try {
        final Dialog loading = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        loading.requestWindowFeature(Window.FEATURE_NO_TITLE); loading.setCancelable(false);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout card = new LinearLayout(ctx); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2); clp.gravity = Gravity.CENTER; card.setLayoutParams(clp);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.WHITE, Color.parseColor("#F8FAFC")}); bg.setCornerRadius(dp(ctx, 16)); bg.setStroke(dp(ctx, 1), Color.parseColor("#DDE6F2")); card.setBackground(bg);
        card.addView(new ProgressBar(ctx)); TextView tv = new TextView(ctx); tv.setText("加载好友中..."); tv.setTextColor(Color.parseColor("#0F172A")); tv.setTextSize(14f); LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(-2, -2); tvlp.leftMargin = dp(ctx, 10); card.addView(tv, tvlp);
        mask.addView(card); loading.setContentView(mask); Window w = loading.getWindow(); if (w != null) { w.setLayout(-1, -1); w.setGravity(Gravity.CENTER); w.setDimAmount(0.25f); } loading.show(); loadingRef[0] = loading;
    } catch (Throwable ignored) {}
    new Thread(new Runnable(){ public void run(){
        final List names = new ArrayList(); final List ids = new ArrayList();
        try { List friends = getFriendList(); if (friends != null) { for (int i=0;i<friends.size();i++){ FriendInfo f=(FriendInfo)friends.get(i); if (f==null) continue; String wxid=f.getWxid(); String nick=f.getNickname(); if (nick==null || nick.length()==0) nick="未知"; String remark=""; try { remark=getFriendRemarkName(wxid); } catch(Throwable ignored){} if (remark==null) remark=""; remark=remark.trim(); if (remark.length()>0 && remark.equals(nick.trim())) remark=""; names.add(remark.length()>0 ? nick+"("+remark+")" : nick); ids.add(wxid); } } } catch(Throwable ignored){}
        new Handler(Looper.getMainLooper()).post(new Runnable(){ public void run(){ try { if (loadingRef[0] != null && loadingRef[0].isShowing()) loadingRef[0].dismiss(); } catch(Throwable ignored){} sCachedFriendNames=names; sCachedFriendIds=ids; buildFriendMultiPickerUI(ctx, rawSelected, blackMode, tvValue, names, ids); }});
    }}).start();
}

void buildFriendMultiPickerUI(final Activity ctx, final String rawSelected, final boolean blackMode, final TextView tvValue, final List names, final List ids) {
    try {
        final HashSet selected = parseWxidSet(rawSelected);
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCancelable(true);
        FrameLayout mask = new FrameLayout(ctx); mask.setBackgroundColor(Color.parseColor("#66000000"));
        LinearLayout root = new LinearLayout(ctx); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 12));
        GradientDrawable rootBg = roundRect(Color.parseColor("#FCFCFD"), dp(ctx, 22)); rootBg.setStroke(dp(ctx, 1), Color.parseColor("#E6EAF0")); root.setBackground(rootBg);
        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(-1, -1); rootLp.leftMargin = dp(ctx, 10); rootLp.rightMargin = dp(ctx, 10); rootLp.topMargin = dp(ctx, 34); rootLp.bottomMargin = dp(ctx, 34); root.setLayoutParams(rootLp);
        TextView title = new TextView(ctx); title.setText("选择好友"); title.setTextSize(22f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.parseColor("#0F172A")); root.addView(title);
        final EditText search = new EditText(ctx); search.setHint("搜索好友昵称、备注或 wxid"); search.setSingleLine(true); search.setTextSize(15f); search.setTextColor(Color.parseColor("#334155")); search.setHintTextColor(Color.parseColor("#A3AEC0")); GradientDrawable sbg = roundRect(Color.parseColor("#F6F8FB"), dp(ctx, 999)); sbg.setStroke(dp(ctx, 1), Color.parseColor("#E5E9F0")); search.setBackground(sbg); search.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10)); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2); slp.topMargin = dp(ctx, 12); root.addView(search, slp);
        final TextView count = new TextView(ctx); count.setTextColor(Color.parseColor("#64748B")); count.setTextSize(12f); count.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 4)); root.addView(count);
        final ListView lv = new ListView(ctx); lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE); lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#E9EDF3"))); lv.setDividerHeight(1); lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.parseColor("#12000000"))); lv.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(-1, 0, 1f); lvp.topMargin = dp(ctx, 6); root.addView(lv, lvp);
        final List display = new ArrayList(); final List showIds = new ArrayList(); final ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, display); lv.setAdapter(adapter);
        final Runnable update = new Runnable(){ public void run(){ display.clear(); showIds.clear(); String kw = search.getText().toString().trim().toLowerCase(); for (int i=0;i<names.size();i++){ String name=String.valueOf(names.get(i)); String id=String.valueOf(ids.get(i)); String row=name+"\n"+id; if (kw.length()==0 || row.toLowerCase().contains(kw)){ display.add(row); showIds.add(id); } } adapter.notifyDataSetChanged(); for (int i=0;i<showIds.size();i++) lv.setItemChecked(i, selected.contains(String.valueOf(showIds.get(i)))); count.setText("已选 " + selected.size() + " 人"); }};
        final Runnable onLabelChanged = new Runnable(){ public void run(){ update.run(); }};
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){} public void afterTextChanged(Editable e){ update.run(); }});
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){ public void onItemClick(AdapterView p, View v, int pos, long id){ String wxid=String.valueOf(showIds.get(pos)); if (lv.isItemChecked(pos)) selected.add(wxid); else selected.remove(wxid); count.setText("已选 " + selected.size() + " 人"); }});
        LinearLayout actions = new LinearLayout(ctx); actions.setGravity(Gravity.END); actions.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2); alp.topMargin = dp(ctx, 12);
        LinearLayout batchRow = new LinearLayout(ctx); batchRow.setGravity(Gravity.END); TextView addLabel = makeBtn(ctx, "添加标签好友", false); TextView selectAll = makeBtn(ctx, "全选当前", false); TextView invert = makeBtn(ctx, "反选当前", false); LinearLayout.LayoutParams blpSmall = new LinearLayout.LayoutParams(-2, -2); blpSmall.leftMargin = dp(ctx, 10); batchRow.addView(addLabel); batchRow.addView(selectAll, blpSmall); batchRow.addView(invert, blpSmall); actions.addView(batchRow);
        LinearLayout mainRow = new LinearLayout(ctx); mainRow.setGravity(Gravity.END); LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(-1, -2); mainLp.topMargin = dp(ctx, 10); TextView clear = makeBtn(ctx, "清空", false); TextView cancel = makeBtn(ctx, "取消", false); TextView ok = makeBtn(ctx, "完成", true); LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.leftMargin = dp(ctx, 10); mainRow.addView(clear); mainRow.addView(cancel, blp); mainRow.addView(ok, blp); actions.addView(mainRow, mainLp); root.addView(actions, alp);
        addLabel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showContactLabelPickerUI(ctx, selected, onLabelChanged); }});
        selectAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ for (int i=0;i<showIds.size();i++) selected.add(String.valueOf(showIds.get(i))); update.run(); }});
        invert.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ for (int i=0;i<showIds.size();i++){ String wxid=String.valueOf(showIds.get(i)); if (selected.contains(wxid)) selected.remove(wxid); else selected.add(wxid); } update.run(); }});
        clear.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ selected.clear(); update.run(); }}); cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }}); ok.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ String raw = joinSet(selected); boolean snsList = false; try { snsList = tvValue != null && "sns_notify_list".equals(String.valueOf(tvValue.getTag())); } catch (Throwable ignored) {} if (snsList) { SNS_NOTIFY_LIST_RAW = raw; snsNotifySet = parseWxidSet(raw); } else if (blackMode) BLACK_LIST_RAW = raw; else WHITE_LIST_RAW = raw; if (tvValue != null) tvValue.setText(countSummary(raw, blackMode ? "未排除 >" : "未选择 >")); dialog.dismiss(); }});
        finishDialogLayoutWithSoftInput(dialog, mask, root);
        dialog.show(); update.run();
    } catch (Throwable e) { toast("打开好友多选失败: " + e); }
}
void onLoad() {
    try {
        if (started) return;
        started = true;
        loadRuntimeConfig();
        loadProcessedIds();
        snsNotifyStartSec = System.currentTimeMillis() / 1000L;
        
        wxMinor = detectWxMinorFromHost();
        logMsg("[版本] hostVerName=" + String.valueOf(hostVerName) + " wxMinor=" + wxMinor);
        
        findDispatcher();
        
        ghostThread = new HandlerThread("AutoLike");
        ghostThread.start();
        ghostHandler = new Handler(ghostThread.getLooper());

        ghostHandler.postDelayed(new Runnable() {
            public void run() {
                try { triggerBackgroundRefresh(); } catch (Throwable ignored) {}
                if (ghostHandler != null) ghostHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        }, 10000);

        ClassLoader wxLoader = hostContext.getClassLoader();
        Class db1 = XposedHelpers.findClass("com.tencent.wcdb.database.SQLiteDatabase", wxLoader);
        Class db2 = XposedHelpers.findClass("com.tencent.wcdb.compat.SQLiteDatabase", wxLoader);
        String[] wm = {"insert", "insertWithOnConflict", "update", "replace"};
        for (int i = 0; i < wm.length; i++) {
            XposedBridge.hookAllMethods(db1, wm[i], dbWriteHook);
            XposedBridge.hookAllMethods(db2, wm[i], dbWriteHook);
        }
        XposedBridge.hookAllMethods(db1, "delete", dbDeleteHook);
        XposedBridge.hookAllMethods(db2, "delete", dbDeleteHook);

    } catch (Throwable e) {
        logMsg("[致命错误] " + e);
    }
}

void onUnload() {
    try {
        started = false;
        if (ghostThread != null) { ghostThread.quitSafely(); ghostThread = null; }
        ghostHandler = null;
        saveProcessedIds();
        pendingIds.clear(); canceledIds.clear();
        sCachedFriendNames = null; sCachedFriendIds = null;
    } catch (Throwable e) { logMsg("[卸载异常] " + e); }
}