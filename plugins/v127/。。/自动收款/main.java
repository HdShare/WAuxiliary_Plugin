import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

// ================= 配置键名常量 =================
String KEY_ENABLE = "tf_ultra_enable"; // 总开关
String KEY_MODE = "tf_ultra_mode"; // 0:全收, 1:仅白名单, 2:拒黑名单
String KEY_WHITELIST = "tf_ultra_whitelist"; // 白名单wxid
String KEY_BLACKLIST = "tf_ultra_blacklist"; // 黑名单wxid
String KEY_REFUSE = "tf_ultra_refuse"; // 拒收时动作: false忽略, true退回
String KEY_DELAY = "tf_ultra_delay"; // 旧版固定接收延迟(ms)，用于兼容迁移
String KEY_DELAY_MIN = "tf_ultra_delay_min"; // 随机延迟最小值(ms)
String KEY_DELAY_MAX = "tf_ultra_delay_max"; // 随机延迟最大值(ms)
String KEY_DELAY_RANGE_INIT = "tf_ultra_delay_range_init"; // 是否已经保存过新版延迟区间

// 金额规则
String KEY_AMT_ENABLE = "tf_ultra_amt_enable"; // 金额限制开关
String KEY_AMT_COND = "tf_ultra_amt_cond"; // 条件: 0:大于, 1:小于, 2:等于
String KEY_AMT_VAL = "tf_ultra_amt_val"; // 金额数值
String KEY_AMT_ACTION = "tf_ultra_amt_act"; // 动作: 0:拒收, 1:接收

// 关键词规则
String KEY_KW_MODE = "tf_ultra_kw_mode"; // 0:关, 1:包含即收, 2:包含即拒
String KEY_KEYWORDS = "tf_ultra_keywords"; // 关键词

// 自动回复
String KEY_REPLY_ENABLE = "tf_ultra_reply_enable";
String KEY_REPLY_TEXT = "tf_ultra_reply_text"; // 缓存变量

// 每日收款总结
String KEY_SUMMARY_ENABLE = "tf_ultra_summary_enable"; // 每日总结总开关
String KEY_SUMMARY_TIME = "tf_ultra_summary_time"; // 每日总结发送时间(HH:mm)
String KEY_SUMMARY_TARGETS = "tf_ultra_summary_targets"; // 总结发送目标wxid或群聊id
String KEY_SUMMARY_LAST_SENT_DATE = "tf_ultra_summary_last_sent_date"; // 最近一次成功发送总结日期
String KEY_DAILY_STATS_PREFIX = "tf_ultra_daily_stats_"; // 单日收款统计前缀
int SUMMARY_DETAIL_LIMIT = 20; // 仅保留最近明细，避免配置文件无限膨胀
long SUMMARY_CHECK_INTERVAL_MS = 60 * 1000L; // 周期巡检，避免一次性TimerTask只运行一次
Object SUMMARY_LOCK = new Object();
java.util.Timer summaryTimer = null;
java.util.TimerTask summaryTask = null;

List sCachedFriendList = null;
List sCachedGroupList = null;

// ================= 入口函数 =================
/**
 * 插件加载时恢复每日总结调度器。
 */
void onLoad() {
    startSummaryScheduler();
}

/**
 * 插件卸载时释放定时任务，避免旧任务继续占用线程。
 */
void onUnload() {
    stopSummaryScheduler();
}

/**
 * 拦截发送消息，用于触发设置界面
 */
boolean onClickSendBtn(String text) {
    if ("转账设置".equals(text)) {
        showSettingsUI();
        return true; // 拦截，不发送
    }
    if ("转账收款总结".equals(text)) {
        sendManualSummaryToCurrentTalker();
        return true; // 拦截，不发送
    }
    return false;
}

/**
 * 消息监听
 */
void onHandleMsg(Object msgInfoBean) {
    // 1. 绝对过滤：自己发的消息一律不处理
    if (msgInfoBean.isSend()) return;

    // 2. 类型过滤：必须是转账类型
    if (msgInfoBean.isTransfer()) {
        handleTransfer(msgInfoBean);
    }
}

// ================= 转账处理逻辑 =================
void handleTransfer(final Object msg) {
    if (!getBoolean(KEY_ENABLE, false)) return;

    String content = "";
    try {
        content = msg.getContent();
    } catch (Exception e) {}
    if (content == null) content = "";

    String myWxid = getLoginWxid();

    // --- 核心校验 1：严格校验收款人身份 ---
    String receiver = parseReceiverFromXml(content);
    if (!TextUtils.isEmpty(receiver) && !TextUtils.isEmpty(myWxid)) {
        if (!receiver.equals(myWxid)) {
            // log(">> 忽略非本人的转账，收款人是: " + receiver);
            return;
        }
    }

    // --- 核心校验 2：严格校验转账状态 (防止退回消息触发回复) ---
    // paysubtype: 1=待收款(正常), 3=已收款, 4=已退回/拒收
    String paysubtype = parsePaySubtypeFromXml(content);
    if (!"1".equals(paysubtype)) {
        log(">> 忽略非收款请求: paysubtype=" + paysubtype + " (可能是退回或已领取)");
        return;
    }

    // 1. 解析基本信息
    final String talker = msg.getTalker(); // 聊天对象
    String payer = "";
    double amount = 0.0;
    
    // ======== 核心修复 3：动态类型接收参数，解决强转报错 ========
    String transactionId = "";
    String transferId = "";
    String payerUsername = "";
    Object invalidTime = null; // 【关键】使用 Object 防止 invalidTime 为 int 时报类型错误

    try {
        payer = msg.getSendTalker();
        if (msg.transferMsg != null) {
            Object tmpTxId = msg.transferMsg.transactionId;
            if (tmpTxId != null) transactionId = tmpTxId.toString();
            
            Object tmpTfId = msg.transferMsg.transferId;
            if (tmpTfId != null) transferId = tmpTfId.toString();
            
            // 原封不动接收原始值(自动装箱)
            invalidTime = msg.transferMsg.invalidTime;
            
            Object tmpPayer = msg.transferMsg.payerUsername;
            if (TextUtils.isEmpty(payer) && tmpPayer != null) {
                payer = tmpPayer.toString();
            }
        }
    } catch (Throwable t) {
        // log("提取参数报错: " + t.getMessage());
    }

    if (TextUtils.isEmpty(payer)) payer = talker; // 私聊兜底
    payerUsername = payer;

    // 兜底：从 XML 中直接解析转账核心参数 (防止由于版本不兼容导致 msg.transferMsg 为空)
    if (TextUtils.isEmpty(transactionId)) {
        transactionId = parseXmlValue(content, "transcationid");
        if (TextUtils.isEmpty(transactionId)) transactionId = parseXmlValue(content, "transactionid");
    }
    if (TextUtils.isEmpty(transferId)) {
        transferId = parseXmlValue(content, "transferid");
    }
    if (invalidTime == null) {
        String xmlTime = parseXmlValue(content, "invalidtime");
        if (!TextUtils.isEmpty(xmlTime)) {
            try {
                // 尽量转回整数，保持与原接口的类型一致性
                invalidTime = Integer.parseInt(xmlTime);
            } catch (Exception e) {
                invalidTime = xmlTime;
            }
        }
    }

    try {
        // 防止自己转账给自己
        if (!TextUtils.isEmpty(myWxid) && payer.equals(myWxid)) {
            log(">> 付款人是本人，忽略（防止自己转账给自己触发）");
            return;
        }
        // 解析金额
        amount = parseAmountFromXml(content);
    } catch (Exception e) {
        log("转账信息解析异常: " + e.getMessage());
        return;
    }

    // 打印调试日志
    log(">> 检测到转账 | 来自:" + getDisplayName(payer) + " | 金额:" + amount + "元");

    // 2. 规则判定 (rejectReason不为空则拒收)
    String rejectReason = null;

    int listMode = getInt(KEY_MODE, 0); // 0:全收, 1:白名单, 2:黑名单
    boolean isGroup = !payer.equals(talker);  // 群聊时 payer ≠ talker，私聊时相等

    if (listMode == 1) {  // 仅接收白名单
        boolean inWhite = checkUserInList(payer, KEY_WHITELIST);
        if (isGroup) {
            inWhite = inWhite || checkUserInList(talker, KEY_WHITELIST);
        }
        if (!inWhite) {
            rejectReason = "非白名单用户或群聊";
        }
    } else if (listMode == 2) {  // 拒收黑名单
        boolean inBlack = checkUserInList(payer, KEY_BLACKLIST);
        if (isGroup) {
            inBlack = inBlack || checkUserInList(talker, KEY_BLACKLIST);
        }
        if (inBlack) {
            rejectReason = "黑名单用户或群聊";
        }
    }

    // B. 金额检查 
    if (rejectReason == null && getBoolean(KEY_AMT_ENABLE, false)) {
        int cond = getInt(KEY_AMT_COND, 1); // 0:>, 1:<, 2:=
        double limit = Double.parseDouble(getString(KEY_AMT_VAL, "0"));
        int action = getInt(KEY_AMT_ACTION, 0); // 0:拒收(黑名单逻辑), 1:强制接收(白名单逻辑)
        boolean match = false;
        if (cond == 0 && amount > limit + 0.001) match = true; // 大于
        else if (cond == 1 && amount < limit - 0.001) match = true; // 小于
        else if (cond == 2 && Math.abs(amount - limit) < 0.01) match = true; // 等于

        if (action == 0) { // 动作0: 拒收/忽略 -> 满足条件则拒收
            if (match) rejectReason = "金额(" + amount + ")触发拒收规则";
        } else { // 动作1: 强制接收 -> 不满足条件则拒收
            if (!match) rejectReason = "金额(" + amount + ")不满足仅接收条件";
        }
    }

    // C. 关键词检查
    if (rejectReason == null) {
        int kwMode = getInt(KEY_KW_MODE, 0);
        String kws = getString(KEY_KEYWORDS, "");
        if (kwMode == 1 && !containsKeyword(content, kws)) rejectReason = "未包含指定关键词";
        else if (kwMode == 2 && containsKeyword(content, kws)) rejectReason = "包含屏蔽关键词";
    }

    // 3. 执行动作
    final boolean needRefuse = getBoolean(KEY_REFUSE, false);
    long[] delayRange = getDelayRange();
    final long delayMin = delayRange[0];
    final long delayMax = delayRange[1];
    final long delay = getRandomDelay(delayMin, delayMax);
    final boolean replyEnable = getBoolean(KEY_REPLY_ENABLE, false);
    final String replyText = getString(KEY_REPLY_TEXT, "谢谢老板");
    final String fTalker = talker;
    final String fPayer = payer;
    final String fPayerDisplayName = getDisplayName(payer);
    final double fAmount = amount;
    
    // ======== 准备 final 变量传入子线程 ========
    final String fTransactionId = transactionId;
    final String fTransferId = transferId;
    final String fPayerUsername = payerUsername;
    final Object fInvalidTime = invalidTime;

    if (rejectReason == null) {
        // >> 接收 <<
        log(">> 准备收款: " + amount + "元, 延迟区间: " + delayMin + "-" + delayMax + "ms, 本次: " + delay + "ms");
        
        // 如果提取不到必要单号参数，不执行收款并提示日志
        if (TextUtils.isEmpty(fTransactionId) || TextUtils.isEmpty(fTransferId)) {
            log("❌ 收款中断: 无法获取转账单号，可能是当前框架未适配此版本微信结构。");
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    if (delay > 0) Thread.sleep(delay);
                    
                    // 调用收款接口
                    confirmTransfer(fTransactionId, fTransferId, fPayerUsername, fInvalidTime);
                    recordDailyCollection(fTalker, fPayer, fPayerDisplayName, fAmount, fTransferId);
                    log(">> 收款动作执行完成 (单号:" + fTransferId + ")");

                    // 成功后才回复
                    if (replyEnable && !TextUtils.isEmpty(replyText)) {
                        try { Thread.sleep(1000); } catch(Exception e){}
                        sendText(fTalker, replyText);
                        log(">> 已自动回复: " + replyText);
                    }
                } catch (Throwable e) { 
                    final String errorMsg = e.toString();
                    log("❌ 收款异常(可能已被领取或报错): " + errorMsg);
                    if (errorMsg != null && errorMsg.contains("no permission to invoke")) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                toast("需关注微信公众号:“画杂记”才可使用");
                            }
                        });
                    }
                }
            }
        }).start();
    } else {
        // >> 拒收/忽略 <<
        log(">> 忽略/拒收: " + rejectReason);
        if (needRefuse) {
            try {
                if (TextUtils.isEmpty(fTransactionId) || TextUtils.isEmpty(fTransferId)) {
                    log("❌ 退回失败: 无法获取转账单号");
                    return;
                }
                refuseTransfer(fTransactionId, fTransferId, fPayerUsername);
                log(">> 已自动退回");
            } catch (Throwable e) {
                log("退回失败: " + e.toString());
            }
        }
    }
}

// ================= 解析逻辑 =================
String parseXmlValue(String xml, String tag) {
    if (TextUtils.isEmpty(xml)) return "";
    try {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</" + tag + ">");
        Matcher m = p.matcher(xml);
        if (m.find()) return m.group(1).trim();
    } catch (Exception e) {}
    return "";
}

String parseReceiverFromXml(String xml) {
    if (xml == null) return "";
    try {
        Pattern p = Pattern.compile("receiver_username.*?>\\s*<!\\[CDATA\\[(.*?)\\]\\]>");
        Matcher m = p.matcher(xml);
        if (m.find()) return m.group(1).trim();
    } catch (Exception e) {}
    return "";
}

String parsePaySubtypeFromXml(String xml) {
    if (xml == null) return "";
    try {
        Pattern p = Pattern.compile("<paysubtype.*?(\\d+)</paysubtype>");
        Matcher m = p.matcher(xml);
        if (m.find()) return m.group(1).trim();
    } catch (Exception e) {}
    return "";
}

double parseAmountFromXml(String xml) {
    if (xml == null) return 0.0;
    try {
        Pattern p = Pattern.compile("feedesc.*?>\\s*<!\\[CDATA\\[\\s*([^]]+?)\\s*\\]\\]>");
        Matcher m = p.matcher(xml);
        if (m.find()) {
            String raw = m.group(1);
            if (raw != null) {
                String numStr = raw.replaceAll("[^0-9\\.]", "");
                if (!TextUtils.isEmpty(numStr)) {
                    return Double.parseDouble(numStr);
                }
            }
        }
        Pattern pVal = Pattern.compile("feederval.*?>(\\d+)<");
        Matcher mVal = pVal.matcher(xml);
        if (mVal.find()) {
            double fen = Double.parseDouble(mVal.group(1));
            return fen / 100.0;
        }
    } catch (Exception e) {
        log("金额解析错: " + e.getMessage());
    }
    return 0.0;
}

boolean checkUserInList(String user, String key) {
    String listStr = getString(key, "");
    if (TextUtils.isEmpty(listStr)) return false;
    String[] arr = listStr.split(",");
    for (String s : arr) {
        if (s.trim().equals(user)) return true;
    }
    return false;
}

boolean containsKeyword(String text, String kws) {
    if (TextUtils.isEmpty(kws) || TextUtils.isEmpty(text)) return false;
    String[] arr = kws.split("[,，]");
    for (String kw : arr) {
        if (!TextUtils.isEmpty(kw) && text.contains(kw.trim())) return true;
    }
    return false;
}

String getDisplayName(String wxid) {
    try {
        String name = getFriendName(wxid);
        return TextUtils.isEmpty(name) ? wxid : name;
    } catch(Exception e) {
        return wxid;
    }
}

// ================= 随机延迟与每日总结 =================
/**
 * 读取随机延迟区间。未保存过新版配置时，自动沿用旧版固定延迟。
 */
long[] getDelayRange() {
    long min = 0;
    long max = 0;
    if (!getBoolean(KEY_DELAY_RANGE_INIT, false)) {
        long oldDelay = getLong(KEY_DELAY, 0);
        min = oldDelay < 0 ? 0 : oldDelay;
        max = min;
    } else {
        min = getLong(KEY_DELAY_MIN, 0);
        max = getLong(KEY_DELAY_MAX, min);
        if (min < 0) min = 0;
        if (max < 0) max = 0;
        if (max < min) max = min;
    }
    return new long[]{min, max};
}

/**
 * 从闭区间内随机取本次延迟，区间相等时退化为固定延迟。
 */
long getRandomDelay(long min, long max) {
    if (min < 0) min = 0;
    if (max < min) max = min;
    if (max == min) return min;
    return min + (long) (Math.random() * (double) (max - min + 1));
}

long parseNonNegativeLong(String text) {
    if (text == null) return 0;
    String s = text.trim();
    if (s.length() == 0) return 0;
    long value = Long.parseLong(s);
    if (value < 0) throw new IllegalArgumentException("数字不能小于0");
    return value;
}

int[] parseSummaryTimeParts(String timeText) {
    if (timeText == null) return null;
    String s = timeText.trim();
    Matcher m = Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(s);
    if (!m.find()) return null;
    int hour = Integer.parseInt(m.group(1));
    int minute = Integer.parseInt(m.group(2));
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
    return new int[]{hour, minute};
}

String normalizeSummaryTime(String timeText) {
    int[] hm = parseSummaryTimeParts(timeText);
    if (hm == null) return null;
    String hourText = hm[0] < 10 ? "0" + hm[0] : String.valueOf(hm[0]);
    String minuteText = hm[1] < 10 ? "0" + hm[1] : String.valueOf(hm[1]);
    return hourText + ":" + minuteText;
}

String getTodayDateStr() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    return sdf.format(new Date());
}

String getNowTimeStr() {
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    return sdf.format(new Date());
}

String dailyStatsBase(String date) {
    return KEY_DAILY_STATS_PREFIX + date + "_";
}

String cleanSummaryText(String value) {
    if (value == null) return "";
    return value.replace("\n", " ").replace("\r", " ").replace("|", "/").trim();
}

long amountToFen(double amount) {
    return Math.round(amount * 100.0);
}

String formatFen(long fen) {
    return new DecimalFormat("0.00").format(((double) fen) / 100.0);
}

List<String> splitCsv(String value) {
    List<String> out = new ArrayList<String>();
    if (TextUtils.isEmpty(value)) return out;
    String[] arr = value.split(",");
    for (int i = 0; i < arr.length; i++) {
        String item = arr[i] == null ? "" : arr[i].trim();
        if (item.length() > 0 && !out.contains(item)) out.add(item);
    }
    return out;
}

String joinCsv(List<String> values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
        String item = String.valueOf(values.get(i));
        if (TextUtils.isEmpty(item)) continue;
        if (sb.length() > 0) sb.append(",");
        sb.append(item);
    }
    return sb.toString();
}

List<String> splitRecords(String value) {
    List<String> out = new ArrayList<String>();
    if (TextUtils.isEmpty(value)) return out;
    String[] arr = value.split("\\u001E");
    for (int i = 0; i < arr.length; i++) {
        String item = arr[i] == null ? "" : arr[i].trim();
        if (item.length() > 0) out.add(item);
    }
    return out;
}

String joinRecords(List<String> values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
        String item = String.valueOf(values.get(i));
        if (TextUtils.isEmpty(item)) continue;
        if (sb.length() > 0) sb.append("\u001E");
        sb.append(item);
    }
    return sb.toString();
}

/**
 * 收款接口无返回值；未抛异常即按成功收款记录到当天统计。
 */
void recordDailyCollection(String talker, String payer, String payerName, double amount, String transferId) {
    synchronized (SUMMARY_LOCK) {
        try {
            String date = getTodayDateStr();
            String base = dailyStatsBase(date);
            long fen = amountToFen(amount);
            int count = getInt(base + "count", 0) + 1;
            long totalFen = getLong(base + "total_fen", 0) + fen;
            putInt(base + "count", count);
            putLong(base + "total_fen", totalFen);

            String safePayer = TextUtils.isEmpty(payer) ? "unknown" : payer;
            List<String> payers = splitCsv(getString(base + "payers", ""));
            if (!payers.contains(safePayer)) {
                payers.add(safePayer);
                putString(base + "payers", joinCsv(payers));
            }

            String payerBase = base + "payer_" + safePayer + "_";
            putString(payerBase + "name", cleanSummaryText(payerName));
            putInt(payerBase + "count", getInt(payerBase + "count", 0) + 1);
            putLong(payerBase + "total_fen", getLong(payerBase + "total_fen", 0) + fen);

            List<String> details = splitRecords(getString(base + "details", ""));
            String detail = getNowTimeStr() + " | " + cleanSummaryText(payerName) + " | "
                + formatFen(fen) + "元 | 会话:" + cleanSummaryText(talker)
                + (TextUtils.isEmpty(transferId) ? "" : " | 单号:" + cleanSummaryText(transferId));
            details.add(detail);
            while (details.size() > SUMMARY_DETAIL_LIMIT) {
                details.remove(0);
            }
            putString(base + "details", joinRecords(details));
        } catch (Throwable e) {
            log("记录每日收款统计失败: " + e.toString());
        }
    }
}

String buildDailySummaryMessage(String date) {
    final String base = dailyStatsBase(date);
    int count = getInt(base + "count", 0);
    long totalFen = getLong(base + "total_fen", 0);
    StringBuilder sb = new StringBuilder();
    sb.append("自动收款单日总结\n");
    sb.append("日期: ").append(date).append("\n");
    sb.append("笔数: ").append(count).append("\n");
    sb.append("总金额: ").append(formatFen(totalFen)).append("元\n");
    if (count <= 0) {
        sb.append("\n今日暂无自动收款记录。");
        return sb.toString();
    }

    List<String> payers = splitCsv(getString(base + "payers", ""));
    Collections.sort(payers, new Comparator<String>() {
        public int compare(String a, String b) {
            long ta = getLong(base + "payer_" + a + "_total_fen", 0);
            long tb = getLong(base + "payer_" + b + "_total_fen", 0);
            if (ta == tb) return 0;
            return ta > tb ? -1 : 1;
        }
    });

    sb.append("\n付款人统计:\n");
    int payerLimit = Math.min(payers.size(), 10);
    for (int i = 0; i < payerLimit; i++) {
        String payer = String.valueOf(payers.get(i));
        String payerBase = base + "payer_" + payer + "_";
        String name = getString(payerBase + "name", payer);
        int payerCount = getInt(payerBase + "count", 0);
        long payerTotal = getLong(payerBase + "total_fen", 0);
        sb.append(i + 1).append(". ").append(TextUtils.isEmpty(name) ? payer : name)
            .append(": ").append(payerCount).append("笔 / ")
            .append(formatFen(payerTotal)).append("元\n");
    }
    if (payers.size() > payerLimit) {
        sb.append("... 另有 ").append(payers.size() - payerLimit).append(" 位付款人\n");
    }

    List<String> details = splitRecords(getString(base + "details", ""));
    if (!details.isEmpty()) {
        sb.append("\n最近明细:\n");
        for (int i = 0; i < details.size(); i++) {
            sb.append(String.valueOf(details.get(i))).append("\n");
        }
    }
    return sb.toString().trim();
}

/**
 * 手动触发只把当天总结发到当前会话，不清理统计，也不更新自动总结去重日期。
 */
void sendManualSummaryToCurrentTalker() {
    try {
        String talker = getTargetTalker();
        if (TextUtils.isEmpty(talker)) {
            toast("请在目标聊天窗口内发送收款总结");
            return;
        }
        sendText(talker, buildDailySummaryMessage(getTodayDateStr()));
        toast("收款总结已发送");
    } catch (Throwable e) {
        log("手动发送收款总结失败: " + e.toString());
        toast("收款总结发送失败: " + e.toString());
    }
}

/**
 * 自动总结发送成功后清理当天统计，避免历史数据重复累积。
 */
void clearDailyCollectionStats(String date) {
    synchronized (SUMMARY_LOCK) {
        try {
            String base = dailyStatsBase(date);
            List<String> payers = splitCsv(getString(base + "payers", ""));
            for (int i = 0; i < payers.size(); i++) {
                String payer = String.valueOf(payers.get(i));
                String payerBase = base + "payer_" + payer + "_";
                putString(payerBase + "name", "");
                putInt(payerBase + "count", 0);
                putLong(payerBase + "total_fen", 0);
            }
            putInt(base + "count", 0);
            putLong(base + "total_fen", 0);
            putString(base + "payers", "");
            putString(base + "details", "");
            log("已清理 " + date + " 的单日收款统计");
        } catch (Throwable e) {
            log("清理单日收款统计失败: " + e.toString());
        }
    }
}

boolean isSummaryDueNow() {
    String timeText = getString(KEY_SUMMARY_TIME, "23:59");
    int[] hm = parseSummaryTimeParts(timeText);
    if (hm == null) hm = new int[]{23, 59};

    TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
    Calendar now = Calendar.getInstance(tz);
    Calendar target = Calendar.getInstance(tz);
    target.set(Calendar.HOUR_OF_DAY, hm[0]);
    target.set(Calendar.MINUTE, hm[1]);
    target.set(Calendar.SECOND, 0);
    target.set(Calendar.MILLISECOND, 0);
    return now.getTimeInMillis() >= target.getTimeInMillis();
}

void startSummaryScheduler() {
    synchronized (SUMMARY_LOCK) {
        stopSummarySchedulerLocked();
        if (!getBoolean(KEY_SUMMARY_ENABLE, false)) return;
        try {
            summaryTimer = new java.util.Timer("tf-summary-dispatch", true);
            summaryTask = new java.util.TimerTask() {
                public void run() {
                    try {
                        sendDailySummaryIfNeeded();
                    } catch (Throwable e) {
                        log("每日收款总结任务异常: " + e.toString());
                    }
                }
            };
            summaryTimer.scheduleAtFixedRate(summaryTask, 0, SUMMARY_CHECK_INTERVAL_MS);
            log("每日收款总结调度已启动，每60秒检查一次，目标时间: " + getString(KEY_SUMMARY_TIME, "23:59"));
        } catch (Throwable e) {
            log("每日收款总结调度启动失败: " + e.toString());
        }
    }
}

void stopSummaryScheduler() {
    synchronized (SUMMARY_LOCK) {
        stopSummarySchedulerLocked();
    }
}

void stopSummarySchedulerLocked() {
    try {
        if (summaryTask != null) summaryTask.cancel();
    } catch (Exception e) {}
    summaryTask = null;
    try {
        if (summaryTimer != null) summaryTimer.cancel();
    } catch (Exception e) {}
    summaryTimer = null;
}

void sendDailySummaryIfNeeded() {
    synchronized (SUMMARY_LOCK) {
        if (!getBoolean(KEY_SUMMARY_ENABLE, false)) return;
        String today = getTodayDateStr();
        if (today.equals(getString(KEY_SUMMARY_LAST_SENT_DATE, ""))) return;
        if (!isSummaryDueNow()) return;

        List<String> targets = splitCsv(getString(KEY_SUMMARY_TARGETS, ""));
        if (targets.isEmpty()) {
            log("每日收款总结未发送: 未配置发送目标");
            return;
        }

        String message = buildDailySummaryMessage(today);
        int sentCount = 0;
        for (int i = 0; i < targets.size(); i++) {
            String target = String.valueOf(targets.get(i));
            try {
                sendText(target, message);
                sentCount++;
            } catch (Throwable e) {
                log("发送每日收款总结失败(" + target + "): " + e.toString());
            }
        }
        if (sentCount > 0) {
            putString(KEY_SUMMARY_LAST_SENT_DATE, today);
            clearDailyCollectionStats(today);
            log("每日收款总结已发送到 " + sentCount + " 个目标");
        }
    }
}

// ================= UI 构建逻辑 =================
void showSettingsUI() {
    Activity ctx = getTopActivity();
    if (ctx == null) return;
    ctx.runOnUiThread(new Runnable() {
        public void run() {
            try {
                showDialogInternal(ctx);
            } catch (Exception e) {
                toast("UI Error: " + e);
            }
        }
    });
}

void showDialogInternal(final Activity ctx) {
    ScrollView scrollView = new ScrollView(ctx);
    scrollView.setBackgroundColor(Color.parseColor("#F5F6F8"));
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(30, 30, 30, 30);
    scrollView.addView(root);

    // 1. 基础设置
    LinearLayout card1 = createCard(ctx);
    root.addView(card1);
    addSectionTitle(ctx, card1, "🛠️ 基础设置");
    final Switch swEnable = addSwitch(ctx, card1, "开启自动收款", getBoolean(KEY_ENABLE, false));
    final Switch swRefuse = addSwitch(ctx, card1, "拒收时原路退回", getBoolean(KEY_REFUSE, false));
    long[] delayRange = getDelayRange();
    final EditText etDelayMin = addInput(ctx, card1, "随机延迟最小值 (毫秒)", delayRange[0] <= 0 ? "" : String.valueOf(delayRange[0]), InputType.TYPE_CLASS_NUMBER);
    final EditText etDelayMax = addInput(ctx, card1, "随机延迟最大值 (毫秒)", delayRange[1] <= 0 ? "" : String.valueOf(delayRange[1]), InputType.TYPE_CLASS_NUMBER);

    // 2. 自动回复
    LinearLayout cardReply = createCard(ctx);
    root.addView(cardReply);
    addSectionTitle(ctx, cardReply, "🤖 自动回复");
    TextView tvTip = new TextView(ctx);
    tvTip.setTextSize(12);
    tvTip.setTextColor(Color.GRAY);
    cardReply.addView(tvTip);
    final Switch swReply = addSwitch(ctx, cardReply, "收款后回复发送者", getBoolean(KEY_REPLY_ENABLE, false));
    final EditText etReplyText = addInput(ctx, cardReply, "回复内容", getString(KEY_REPLY_TEXT, "谢谢老板"), InputType.TYPE_CLASS_TEXT);

    // 3. 每日总结
    LinearLayout cardSummary = createCard(ctx);
    root.addView(cardSummary);
    addSectionTitle(ctx, cardSummary, "📊 每日总结");
    final Switch swSummary = addSwitch(ctx, cardSummary, "定时发送单日收款总结", getBoolean(KEY_SUMMARY_ENABLE, false));
    final EditText etSummaryTime = addInput(ctx, cardSummary, "发送时间 (HH:mm)", getString(KEY_SUMMARY_TIME, "23:59"), InputType.TYPE_CLASS_TEXT);
    final TextView tvSummaryTargets = new TextView(ctx);
    tvSummaryTargets.setTextSize(12);
    tvSummaryTargets.setTextColor(Color.GRAY);
    tvSummaryTargets.setText("已选择 " + splitCsv(getString(KEY_SUMMARY_TARGETS, "")).size() + " 个发送目标");
    cardSummary.addView(tvSummaryTargets);
    Button btnSummaryTargets = addButton(ctx, cardSummary, "选择总结发送目标", "#607D8B");
    btnSummaryTargets.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "总结发送目标", KEY_SUMMARY_TARGETS);
        }
    });

    // 4. 名单策略
    LinearLayout cardList = createCard(ctx);
    root.addView(cardList);
    addSectionTitle(ctx, cardList, "📋 名单策略");
    String[] modes = {"接收所有人 (默认)", "仅接收白名单", "拒收黑名单"};
    final Spinner spMode = addSpinner(ctx, cardList, modes, getInt(KEY_MODE, 0));
    Button btnWhite = addButton(ctx, cardList, "管理白名单", "#4CAF50");
    Button btnBlack = addButton(ctx, cardList, "管理黑名单", "#F44336");
    btnWhite.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "白名单", KEY_WHITELIST);
        }
    });
    btnBlack.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "黑名单", KEY_BLACKLIST);
        }
    });

    // 5. 金额过滤
    LinearLayout cardAmt = createCard(ctx);
    root.addView(cardAmt);
    addSectionTitle(ctx, cardAmt, "💰 金额规则");
    final Switch swAmt = addSwitch(ctx, cardAmt, "启用金额过滤", getBoolean(KEY_AMT_ENABLE, false));
    LinearLayout amtRow = new LinearLayout(ctx);
    amtRow.setOrientation(LinearLayout.VERTICAL);
    LinearLayout line1 = new LinearLayout(ctx);
    line1.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvWhen = new TextView(ctx);
    tvWhen.setText("当金额 ");
    String[] conds = {"大于 (>)", "小于 (<)", "等于 (=)"};
    final Spinner spCond = new Spinner(ctx);
    spCond.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, conds));
    spCond.setSelection(getInt(KEY_AMT_COND, 1));
    final EditText etVal = new EditText(ctx);
    etVal.setHint("0.00");
    etVal.setText(getString(KEY_AMT_VAL, "0"));
    etVal.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    etVal.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    line1.addView(tvWhen);
    line1.addView(spCond);
    line1.addView(etVal);
    LinearLayout line2 = new LinearLayout(ctx);
    line2.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvThen = new TextView(ctx);
    tvThen.setText("执行: ");
    String[] acts = {"🚫 拒收/忽略", "✅ 仅接收满足条件"};
    final Spinner spAct = new Spinner(ctx);
    spAct.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, acts));
    spAct.setSelection(getInt(KEY_AMT_ACTION, 0));
    line2.addView(tvThen);
    line2.addView(spAct);
    amtRow.addView(line1);
    amtRow.addView(line2);
    amtRow.setBackgroundColor(Color.parseColor("#FAFAFA"));
    amtRow.setPadding(10,10,10,10);
    cardAmt.addView(amtRow);

    // 6. 关键词
    LinearLayout cardKw = createCard(ctx);
    root.addView(cardKw);
    addSectionTitle(ctx, cardKw, "🔑 关键词过滤");
    String[] kwModes = {"不启用", "必须包含关键词", "包含则拒收"};
    final Spinner spKw = addSpinner(ctx, cardKw, kwModes, getInt(KEY_KW_MODE, 0));
    final EditText etKw = addInput(ctx, cardKw, "关键词(逗号分隔)", getString(KEY_KEYWORDS, ""), InputType.TYPE_CLASS_TEXT);

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("转账设置")
        .setView(scrollView)
        .setPositiveButton("保存配置", null)
        .setNegativeButton("关闭", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);

    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                long delayMin = parseNonNegativeLong(etDelayMin.getText().toString());
                long delayMax = parseNonNegativeLong(etDelayMax.getText().toString());
                if (delayMax < delayMin) {
                    toast("随机延迟最大值不能小于最小值，笨蛋~");
                    return;
                }
                String summaryTime = normalizeSummaryTime(etSummaryTime.getText().toString());
                if (summaryTime == null) {
                    toast("每日总结时间格式错误，请填写 HH:mm");
                    return;
                }
                putBoolean(KEY_ENABLE, swEnable.isChecked());
                putBoolean(KEY_REFUSE, swRefuse.isChecked());
                putLong(KEY_DELAY_MIN, delayMin);
                putLong(KEY_DELAY_MAX, delayMax);
                putBoolean(KEY_DELAY_RANGE_INIT, true);
                putLong(KEY_DELAY, delayMin == delayMax ? delayMin : 0);
                putBoolean(KEY_REPLY_ENABLE, swReply.isChecked());
                putString(KEY_REPLY_TEXT, etReplyText.getText().toString());
                putBoolean(KEY_SUMMARY_ENABLE, swSummary.isChecked());
                putString(KEY_SUMMARY_TIME, summaryTime);
                putInt(KEY_MODE, spMode.getSelectedItemPosition());
                putBoolean(KEY_AMT_ENABLE, swAmt.isChecked());
                putInt(KEY_AMT_COND, spCond.getSelectedItemPosition());
                putString(KEY_AMT_VAL, etVal.getText().toString());
                putInt(KEY_AMT_ACTION, spAct.getSelectedItemPosition());
                putInt(KEY_KW_MODE, spKw.getSelectedItemPosition());
                putString(KEY_KEYWORDS, etKw.getText().toString());
                startSummaryScheduler();
                toast("保存成功");
                d.dismiss();
            } catch(Exception e) {
                toast("保存失败:" + e);
            }
        }
    });
}

// ================= UI 组件工厂 =================
LinearLayout createCard(Activity ctx) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.WHITE);
    gd.setCornerRadius(30);
    card.setBackground(gd);
    card.setPadding(40, 40, 40, 40);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, 30);
    card.setLayoutParams(lp);
    card.setElevation(5f);
    return card;
}

void addSectionTitle(Activity ctx, LinearLayout parent, String title) {
    TextView tv = new TextView(ctx);
    tv.setText(title);
    tv.setTextSize(16);
    tv.setTextColor(Color.parseColor("#333333"));
    tv.getPaint().setFakeBoldText(true);
    tv.setPadding(0, 0, 0, 20);
    parent.addView(tv);
}

Switch addSwitch(Activity ctx, LinearLayout parent, String text, boolean checked) {
    Switch s = new Switch(ctx);
    s.setText(text);
    s.setChecked(checked);
    s.setPadding(0, 10, 0, 10);
    parent.addView(s);
    return s;
}

EditText addInput(Activity ctx, LinearLayout parent, String hint, String val, int type) {
    EditText et = new EditText(ctx);
    et.setHint(hint);
    et.setText(val);
    et.setInputType(type);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor("#F5F5F5"));
    gd.setCornerRadius(15);
    et.setBackground(gd);
    et.setPadding(20, 20, 20, 20);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 10, 0, 20);
    et.setLayoutParams(lp);
    parent.addView(et);
    return et;
}

Spinner addSpinner(Activity ctx, LinearLayout parent, String[] items, int sel) {
    Spinner sp = new Spinner(ctx);
    ArrayAdapter<String> adp = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, items);
    sp.setAdapter(adp);
    sp.setSelection(sel);
    parent.addView(sp);
    return sp;
}

Button addButton(Activity ctx, LinearLayout parent, String text, String colorHex) {
    Button btn = new Button(ctx);
    btn.setText(text);
    btn.setTextColor(Color.WHITE);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor(colorHex));
    gd.setCornerRadius(20);
    btn.setBackground(gd);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 10, 0, 10);
    btn.setLayoutParams(lp);
    parent.addView(btn);
    return btn;
}

void setupUnifiedDialog(AlertDialog dialog) {
    try {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(40);
        bg.setColor(Color.parseColor("#F5F6F8"));
        dialog.getWindow().setBackgroundDrawable(bg);
    } catch (Exception e) {}
}

void styleDialogButtons(AlertDialog dialog) {
    try {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#2196F3"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY);
    } catch (Exception e) {}
}

// ================= 名单管理 =================
void showContactSourceDialog(final Activity ctx, final String title, final String saveKey) {
    String[] items = {"👤 从好友列表选择", "🏠 从群聊列表选择"};
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("选择添加方式")
        .setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) loadAndSelect(ctx, title, saveKey, true);
                else loadAndSelect(ctx, title, saveKey, false);
            }
        }).create();
    setupUnifiedDialog(d);
    d.show();
}

void loadAndSelect(final Activity ctx, final String title, final String saveKey, final boolean isFriend) {
    final ProgressDialog loading = new ProgressDialog(ctx);
    loading.setMessage("正在加载列表，请稍候...");
    loading.setCancelable(false);
    loading.show();

    new Thread(new Runnable() {
        public void run() {
            final List<String> names = new ArrayList<>();
            final List<String> ids = new ArrayList<>();
            try {
                if (isFriend) {
                    if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                    if (sCachedFriendList != null) {
                        for (int i=0; i<sCachedFriendList.size(); i++) {
                            FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
                            if (f != null) {
                                String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                                String remark = f.getRemark();
                                String name = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                                String id = f.getWxid();
                                names.add(name);
                                ids.add(id);
                            }
                        }
                    }
                } else {
                    if (sCachedGroupList == null) sCachedGroupList = getGroupList();
                    if (sCachedGroupList != null) {
                        for (int i=0; i<sCachedGroupList.size(); i++) {
                            GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                            if (g != null) {
                                String name = TextUtils.isEmpty(g.getName()) ? "未知群聊" : g.getName();
                                String id = g.getRoomId();
                                names.add(name);
                                ids.add(id);
                            }
                        }
                    }
                }
            } catch(Exception e) {
                log("加载失败: " + e.getMessage());
            } finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        try {
                            if (loading.isShowing()) loading.dismiss();
                        } catch(Exception e){}
                        if (names.isEmpty()) {
                            toast("列表为空或加载失败！");
                        } else {
                            showMultiSelect(ctx, title + (isFriend ? "-好友" : "-群聊"), names, ids, saveKey);
                        }
                    }
                });
            }
        }
    }).start();
}

void showMultiSelect(Activity ctx, String title, final List<String> names, final List<String> ids, final String saveKey) {
    String existStr = getString(saveKey, "");
    final Set<String> selectedSet = new HashSet<>();
    if (!TextUtils.isEmpty(existStr)) {
        for (String s : existStr.split(",")) selectedSet.add(s.trim());
    }

    ScrollView sv = new ScrollView(ctx);
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(20, 20, 20, 20);
    sv.addView(layout);

    final EditText etSearch = new EditText(ctx);
    etSearch.setHint("🔍 搜索...");
    layout.addView(etSearch);

    final ListView lv = new ListView(ctx);
    lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 800));
    layout.addView(lv);

    final List<String> dNames = new ArrayList<>();
    final List<String> dIds = new ArrayList<>();
    final Set<String> tempSet = new HashSet<>(selectedSet);

    final Runnable refresh = new Runnable() {
        public void run() {
            String kw = etSearch.getText().toString().toLowerCase();
            dNames.clear();
            dIds.clear();
            for (int i=0; i<names.size(); i++) {
                if (kw.isEmpty() || names.get(i).toLowerCase().contains(kw)) {
                    dNames.add(names.get(i));
                    dIds.add(ids.get(i));
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_multiple_choice, dNames);
            lv.setAdapter(adapter);
            for (int i=0; i<dIds.size(); i++) {
                if (tempSet.contains(dIds.get(i))) lv.setItemChecked(i, true);
            }
        }
    };

    etSearch.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            refresh.run();
        }
    });

    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            String rid = dIds.get(pos);
            if (lv.isItemChecked(pos)) tempSet.add(rid);
            else tempSet.remove(rid);
        }
    });

    refresh.run();

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title)
        .setView(sv)
        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                StringBuilder sb = new StringBuilder();
                for (String s : tempSet) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(s);
                }
                putString(saveKey, sb.toString());
                toast("名单更新: " + tempSet.size() + "个");
            }
        })
        .setNegativeButton("取消", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}
