// ============================================================
// 元启Jev聊天助手 v1.3
// 自动分析对方消息，用「插入系统消息」的方式把 Jev 的决策结果
// 显示在聊天里。
// 本版要点：
//   - 上下文实时从微信聊天记录读取（queryHistoryMsg），不再用内存队列
//   - 上下文轮数默认 0，即默认不启用上下文
//   - 每条消息独立并发分析，不排队等待；同时只允许一个会话开启
//   - 配置界面：输入框内嵌、开关原地更新、底部统一保存
// ============================================================

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ==================== 常量 ==================== */

// 服务地址与模型（已切换为 Jev 官方 API）
final String DEFAULT_API_URL = "https://api.typesafe.ai/v1/systemone";
final String DEFAULT_MODEL = "jev-latest";

// 交流群
final String GROUP_NUM = "883640898";
final String GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=qnKnEk9fixjZc6NNjEFbLB8gwREGPnfUP23AZYxGqdxw0iiRJDH1zrztUG8%2BdbAE&busi_data=eyJncm91cENvZGUiOiI4ODM2NDA4OTgiLCJ0b2tlbiI6IlFXVEgzNFEyd3lJNU5oQzljZG4yQW5BMFBWK2RENHpFUkR6eEZ4TWl5U3Z6ZGRYb3ViK2xPK3Nva1p2Wjl4d2MiLCJ1aW4iOiI2MTEwNTM2In0%3D&data=TWSZoYEaYUAG0Euuz6NeLt9YAZI87UxI56LHHlNDI8SV2DSkuJMGelcmcyrKRq1py2DZVMvYrgt4m6sgjkhLXA&svctype=4&tempid=h5_group_info";

// 上下文轮数：0 表示不启用上下文（不发送历史消息）
final int DEFAULT_CONTEXT_ROUNDS = 0;
final int MAX_CONTEXT_ROUNDS = 30;

// 排版兜底宽度（全角字符数）：取不到屏幕信息时使用，正常情况按屏幕宽度自动估算
final int DEFAULT_PAD_CHARS = 24;

// 行尾锚点（U+00A0 不换行空格）：
// Android 排版时会丢掉「行尾空白」，导致补在行尾的全角空格全部失效、
// 每行仍按自身内容宽度居中（越短越靠右）。在行尾放一个非空白字符，
// 前面的空格就不再处于行尾，才能被真正计入行宽。
final String PAD_ANCHOR = String.valueOf((char) 160);

// 横线字符（全角制表线）
final String DIVIDER_CHAR = "─";

// 配置页配色（暗色）
final int BG_PANEL = Color.parseColor("#2A2A2A");
final int BG_CARD = Color.parseColor("#3A3A3A");
final int BG_INPUT = Color.parseColor("#404040");
final int TEXT_MAIN = Color.parseColor("#FFFFFF");
final int TEXT_SUB = Color.parseColor("#AAAAAA");
final int TEXT_HINT = Color.parseColor("#666666");
final int ACCENT_BLUE = Color.parseColor("#4A9EFF");
final int ACCENT_GREEN = Color.parseColor("#4AFF9E");
final int DIVIDER = Color.parseColor("#444444");

/** ==================== 运行时状态 ==================== */

// 线程池：每条消息独立并发分析，互不等待
ExecutorService analyzePool = Executors.newCachedThreadPool();

// 最近一次请求的 HTTP 状态码（503 表示上游暂时不可用）
int lastHttpCode = 0;

/** ==================== 生命周期 ==================== */

/**
 * 插件加载
 * 本插件不需要初始化：上下文在每次分析时实时从聊天记录读取
 */
void onLoad() {
    // 常规流程不输出日志
}

/**
 * 插件卸载：关闭线程池
 */
void onUnload() {
    try {
        analyzePool.shutdownNow();
    } catch (Throwable e) {
        log("onUnload 异常: " + e);
    }
}

/** ==================== 配置读写 ==================== */

/** 读取 API Key */
String getApiKey() {
    return getString("api_key", "").trim();
}

/** 读取当前唯一开启的会话；未开启时返回空串 */
String getActiveTalker() {
    return getString("active_talker", "").trim();
}

/** 设置当前开启的会话（会自动顶掉上一个） */
void setActiveTalker(String talker) {
    putString("active_talker", talker == null ? "" : talker);
}

/** 是否开启自动分析 */
boolean isAutoAnalyze() {
    return getBoolean("auto_analyze", true);
}

/**
 * 读取上下文轮数
 * 返回值用 Math 处理，避免 BeanShell 把 < > 误判成泛型
 * @return 0 表示不启用上下文
 */
int getContextRounds() {
    int n = getInt("context_rounds", DEFAULT_CONTEXT_ROUNDS);
    return Math.max(0, Math.min(MAX_CONTEXT_ROUNDS, n));
}

/** ==================== 消息处理 ==================== */

/**
 * 收到消息回调
 * 只有「当前开启的会话」+「对方发来的消息」才触发分析
 * @param msgInfoBean 消息对象（实际为 MsgInfoBean）
 */
void onHandleMsg(Object msgInfoBean) {
    try {
        if (msgInfoBean == null) return;
        if (!getBoolean(msgInfoBean, "isText")) return;
        if (getBoolean(msgInfoBean, "isSend")) return;

        String talker = getString(msgInfoBean, "getTalker");
        String content = getString(msgInfoBean, "getContent");

        if (isEmpty(talker) || isEmpty(content)) return;
        content = content.trim();
        if (isEmpty(content)) return;

        if (!isAutoAnalyze()) return;
        if (!talker.equals(getActiveTalker())) return;

        enqueueAnalysis(talker, content);

    } catch (Throwable e) {
        log("消息处理异常: " + e);
    }
}

/**
 * 点击发送按钮：拦截 /jev 指令打开配置页
 * @param text 输入框内容
 * @return 是否拦截本次发送
 */
boolean onClickSendBtn(String text) {
    try {
        if (text == null) return false;
        String cmd = text.trim();
        if ("/jev".equalsIgnoreCase(cmd) || "/jev配置".equals(cmd) || "/jev设置".equals(cmd)) {
            showMainDialog();
            return true;
        }
    } catch (Throwable e) {
        log("指令处理异常: " + e);
    }
    return false;
}

/**
 * 提交一条待分析消息
 * 每条消息独立并发执行，不排队等待
 * @param talker 会话 ID
 * @param content 对方消息内容
 */
void enqueueAnalysis(final String talker, final String content) {
    try {
        if (isEmpty(getApiKey())) {
            toast("请先在 /jev 配置页填写 API Key");
            return;
        }

        analyzePool.submit(new Runnable() {
            public void run() {
                try {
                    String result = analyzeByJev(talker, content);
                    if (!isEmpty(result)) {
                        insertSystemMsg(talker, result, System.currentTimeMillis());
                    }
                } catch (Throwable e) {
                    log("分析异常: " + e);
                }
            }
        });
    } catch (Throwable e) {
        log("提交分析异常: " + e);
    }
}

/** ==================== 上下文 ==================== */

/**
 * 从微信聊天记录里取最近若干条消息作为上下文
 * 轮数为 0 时直接返回空数组（不启用上下文）
 * @param talker 会话 ID
 * @return JSON 数组，元素形如「对方：你好」
 */
JSONArray buildHistory(String talker) {
    JSONArray arr = new JSONArray();
    try {
        int limit = getContextRounds();
        if (limit <= 0) return arr;

        // 多取一条，因为要把「当前正在分析的最新那条」排除掉
        List list = queryHistoryMsg(talker, System.currentTimeMillis(), false, limit + 1);
        if (list == null || list.isEmpty()) return arr;

        int size = list.size();
        long[] times = new long[size];
        String[] lines = new String[size];
        int n = 0;

        for (int i = 0; i < size; i++) {
            Object bean = list.get(i);
            if (bean == null) continue;
            if (!getBoolean(bean, "isText")) continue;

            String content = getString(bean, "getContent");
            if (isEmpty(content)) continue;

            lines[n] = formatHistoryLine(bean, content.trim());
            times[n] = getLong(bean, "getCreateTime");
            n++;
        }
        if (n == 0) return arr;

        // 自己按时间升序排序，不依赖接口返回顺序（条数很少，冒泡即可）
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (times[j] > times[j + 1]) {
                    long t = times[j];
                    times[j] = times[j + 1];
                    times[j + 1] = t;
                    String s = lines[j];
                    lines[j] = lines[j + 1];
                    lines[j + 1] = s;
                }
            }
        }

        // 丢掉最后一条（最新消息，已在 state 里单独传），保留最近 limit 条
        int end = n - 1;
        int start = Math.max(0, end - limit);
        for (int i = start; i < end; i++) {
            arr.put(lines[i]);
        }
    } catch (Throwable e) {
        log("读取历史消息异常: " + e);
    }
    return arr;
}

/**
 * 把一条历史消息格式化成「谁：内容」
 * 私聊统一用「对方」；群聊附发送者短标识，便于区分不同人
 * @param bean 历史消息对象
 * @param content 消息内容
 * @return 形如「对方(3f2a)：你好」
 */
String formatHistoryLine(Object bean, String content) {
    try {
        if (getBoolean(bean, "isSend")) return "我：" + content;

        if (!getBoolean(bean, "isGroupChat")) return "对方：" + content;

        String sender = getString(bean, "getSendTalker");
        if (isEmpty(sender)) return "对方：" + content;

        // 注意：不能写成三元表达式，len > 4 的尖括号会被 BeanShell 误判成泛型
        int len = sender.length();
        String shortId = sender;
        if (len > 4) shortId = sender.substring(len - 4);
        return "对方(" + shortId + ")：" + content;
    } catch (Throwable e) {
        return "对方：" + content;
    }
}

/** ==================== 调用 Jev ==================== */

/**
 * 调用 Jev 分析一条消息，返回可直接插入系统消息的多行文本
 * 使用原生 HttpURLConnection 以便发送嵌套 JSON（WA 的 post 只能发扁平键值对）
 * @param talker 会话 ID
 * @param content 对方消息内容
 * @return 多行分析文本；失败返回 null
 */
String analyzeByJev(String talker, String content) {
    String apiKey = getApiKey();
    if (isEmpty(apiKey)) return null;

    try {
        JSONObject body = buildRequestBody(talker, content);

        String respText = postJson(body, apiKey);
        if (lastHttpCode == 503) {
            log("[Jev] 请求失败 503（上游引擎暂时不可用），3 秒后重试一次");
            try { Thread.sleep(3000); } catch (Throwable ignore) {}
            respText = postJson(body, apiKey);
        }
        if (isEmpty(respText)) return null;

        JSONObject json = new JSONObject(respText);
        JSONObject answers = json.optJSONObject("answers");
        if (answers == null) return null;

        return formatAnswers(answers);

    } catch (Throwable e) {
        log("调用 Jev 异常: " + e);
        return null;
    }
}

/**
 * 构造请求体：固定骨架 + 「潜台词」一句话插值（不调用任何生成式模型）
 * @param talker 会话 ID
 * @param content 对方消息内容
 * @return 请求体
 */
JSONObject buildRequestBody(String talker, String content) {
    JSONObject state = new JSONObject();
    state.put("对方最新消息", content);
    state.put("最近几轮对话", buildHistory(talker));

    JSONObject questions = new JSONObject();

    JSONObject emotion = new JSONObject();
    emotion.put("type", "choice");
    emotion.put("instructions", "对方此刻的主要情绪");
    JSONObject emotionCriteria = new JSONObject();
    emotionCriteria.put("开心", "愉快、满意");
    emotionCriteria.put("生气", "不满、恼怒");
    emotionCriteria.put("难过", "失落、委屈");
    emotionCriteria.put("不安", "担心、焦虑");
    emotionCriteria.put("平静", "没情绪波动");
    emotion.put("criteria", emotionCriteria);
    questions.put("情绪", emotion);

    JSONObject intent = new JSONObject();
    intent.put("type", "choice");
    intent.put("instructions", "对方真正想要什么");
    JSONObject intentCriteria = new JSONObject();
    intentCriteria.put("要态度", "看我认不认、在不在乎");
    intentCriteria.put("要行动", "需要我实际去做");
    intentCriteria.put("要解释", "想知道原因");
    intentCriteria.put("要倾诉", "听着就行");
    intentCriteria.put("要表态", "等我明确答复");
    intent.put("criteria", intentCriteria);
    questions.put("对方诉求", intent);

    JSONObject action = new JSONObject();
    action.put("type", "choice");
    action.put("instructions", "我此刻最该做的");
    JSONObject actionCriteria = new JSONObject();
    actionCriteria.put("先安抚", "先共情，别讲道理");
    actionCriteria.put("给行动", "少说，直接做");
    actionCriteria.put("讲清楚", "解释原因");
    actionCriteria.put("简短应承", "一句话到位");
    actionCriteria.put("先别回", "停手，别再说话");
    action.put("criteria", actionCriteria);
    questions.put("最佳动作", action);

    JSONObject subtext = new JSONObject();
    subtext.put("type", "noul");
    subtext.put("instructions", "对方说「" + clip(content, 30) + "」，话里有话");
    JSONObject subCriteria = new JSONObject();
    subCriteria.put("true", "另有意思");
    subCriteria.put("false", "就是字面意思");
    subtext.put("criteria", subCriteria);
    questions.put("潜台词", subtext);

    JSONObject body = new JSONObject();
    body.put("state", state);
    body.put("model", DEFAULT_MODEL);
    body.put("questions", questions);
    return body;
}

/**
 * 发送一次 systemone 请求（同步，必须在子线程调用）
 * 状态码写入字段 lastHttpCode 供调用方判断
 * @param body 请求体
 * @param apiKey API Key
 * @return 响应文本；失败返回 null
 */
String postJson(JSONObject body, String apiKey) {
    HttpURLConnection conn = null;
    try {
        conn = (HttpURLConnection) new URL(DEFAULT_API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(45000);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        lastHttpCode = conn.getResponseCode();
        if (lastHttpCode != 200) {
            String errText = "";
            try {
                InputStream errStream = conn.getErrorStream();
                if (errStream != null) {
                    BufferedReader er = new BufferedReader(new InputStreamReader(errStream, "UTF-8"));
                    StringBuilder eb = new StringBuilder();
                    String el;
                    while ((el = er.readLine()) != null) eb.append(el);
                    er.close();
                    errText = eb.toString();
                }
            } catch (Throwable ignore) {}
            log("[Jev] 请求失败 HTTP " + lastHttpCode + " " + clip(errText, 160));
            return null;
        }

        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();

    } catch (Throwable e) {
        lastHttpCode = -1;
        log("[Jev] 请求异常: " + e);
        return null;
    } finally {
        if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}
    }
}

/**
 * 把 Jev 的 answers 排成多行文本，每行一个结论
 * @param answers 响应里的 answers
 * @return 多行文本
 */
String formatAnswers(JSONObject answers) {
    StringBuilder sb = new StringBuilder();
    try {
        // 先把各行原文收集起来（暂不补齐），这样才能算出「文字最宽到哪」
        java.util.List body = new java.util.ArrayList();
        java.util.List tail = new java.util.ArrayList();

        body.add("Jev:");

        JSONObject sub = answers.optJSONObject("潜台词");
        if (sub != null && sub.has("noul")) {
            body.add("话里有话 " + toPercent(sub.optDouble("noul", 0)));
        }

        String c1 = formatChoice(answers, "情绪", "当前情绪");
        if (!isEmpty(c1)) body.add(c1);
        String c2 = formatChoice(answers, "对方诉求", "真实意图");
        if (!isEmpty(c2)) body.add(c2);
        String c3 = formatChoice(answers, "最佳动作", "最佳动作");
        if (!isEmpty(c3)) body.add(c3);

        JSONObject action = answers.optJSONObject("最佳动作");
        if (action != null) {
            String act = action.optString("choice", "");
            String tip = getActionTip(act);
            String eg = getActionEg(act);
            if (!isEmpty(tip)) tail.add(tip);
            if (!isEmpty(eg)) tail.add("「" + eg + "」");
        }

        // 找出最宽的一行：横线只画到这里，避免横线超出文字右端
        double maxW = 0;
        int i;
        for (i = 0; i < body.size(); i++) {
            double w = visualWidth(String.valueOf(body.get(i)));
            if (w > maxW) maxW = w;
        }
        for (i = 0; i < tail.size(); i++) {
            double w = visualWidth(String.valueOf(tail.get(i)));
            if (w > maxW) maxW = w;
        }

        for (i = 0; i < body.size(); i++) {
            sb.append(padLine(String.valueOf(body.get(i)))).append("\n");
        }
        if (tail.size() > 0) {
            sb.append(dividerLine(maxW)).append("\n");
            for (i = 0; i < tail.size(); i++) {
                sb.append(padLine(String.valueOf(tail.get(i)))).append("\n");
            }
        }
    } catch (Throwable e) {
        log("排版异常: " + e);
    }
    return sb.toString().trim();
}

/** ==================== 排版：靠补全角空格实现「视觉左对齐」 ==================== */

/**
 * 排版目标宽度（以「全角字符」为单位）
 * 微信系统消息由宿主强制居中，插件改不了对齐方式；
 * 但在行尾补全角空格可以把该行撑宽，居中后起点就会左移。
 * 只要每行都补到同一宽度，各行起点就一致，看起来就是左对齐了。
 * @return 目标宽度；优先用配置项 pad_chars，未配置时按屏幕宽度估算
 */
int sysPadWidth() {
    try {
        int cfg = getInt("pad_chars", 0);
        if (cfg >= 8 && cfg <= 40) return cfg;
    } catch (Throwable e) {
    }
    try {
        Activity act = getTopActivity();
        if (act != null) {
            android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
            // 系统消息字号约 12sp；可用宽度约为屏幕宽的 68%
            // （取 76% 时横线会超出容器折行，说明系统消息左右留白比想象中大）
            // 必须用 scaledDensity：用户调大系统字体时字会更宽，用 density 会低估字宽、
            // 把每行补得过长，结果折行反而更乱。取两者较大值更保守。
            float charPx = 12f * Math.max(dm.density, dm.scaledDensity);
            if (charPx > 0) {
                int n = (int) (dm.widthPixels * 0.68f / charPx);
                if (n < 8) n = 8;
                if (n > 40) n = 40;
                return n;
            }
        }
    } catch (Throwable e) {
    }
    return DEFAULT_PAD_CHARS;
}

/**
 * 估算文本占几个「全角字符」宽：ASCII 算 0.5，其余算 1
 * @param s 文本
 * @return 宽度
 */
double visualWidth(String s) {
    double w = 0;
    try {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < 128) w += 0.5;
            else w += 1;
        }
    } catch (Throwable e) {
    }
    return w;
}

/**
 * 在行尾补全角空格，把居中内容顶向左边
 * 已经够宽的行原样返回（再补就会折行，反而更乱）
 * @param line 原始行
 * @return 补齐后的行
 */
String padLine(String line) {
    try {
        if (line == null) return "";
        int target = sysPadWidth();
        double w = visualWidth(line);
        if (w >= target) return line;

        StringBuilder sb = new StringBuilder(line);
        double i = w;
        // 留出锚点宽度，避免总宽略微超出容器
        while (i < target - 1) {
            sb.append("　");
            i += 1;
        }
        sb.append(PAD_ANCHOR);
        return sb.toString();
    } catch (Throwable e) {
        return line;
    }
}

/**
 * 生成分隔线：横线只画到「文字最宽处」，其后补全角空格补足到排版宽度
 * 这样横线右端与文字右端齐平（不会长出一截），同时整行宽度仍与上面各行一致，
 * 起点才不会跑偏。
 * @param contentWidth 正文中最宽一行的宽度（全角字符数）
 * @return 分隔线一行
 */
String dividerLine(double contentWidth) {
    try {
        int target = sysPadWidth();

        int n = (int) Math.ceil(contentWidth);
        if (n < 4) n = 4;
        if (n > target - 1) n = target - 1;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(DIVIDER_CHAR);

        double w = n;
        while (w < target - 1) {
            sb.append("　");
            w += 1;
        }
        sb.append(PAD_ANCHOR);
        return sb.toString();
    } catch (Throwable e) {
        return "─────";
    }
}

/**
 * 把某个 choice 维度排成一行，只显示概率最高的一项
 * @param answers 答案集合
 * @param key 维度键名
 * @param label 显示标签
 * @return 形如「当前情绪 平静 50%」的一行
 */
String formatChoice(JSONObject answers, String key, String label) {
    try {
        JSONObject item = answers.optJSONObject(key);
        if (item == null) return "";
        JSONObject probs = item.optJSONObject("probabilities");
        if (probs == null) return "";

        String best = item.optString("choice", "");
        double bestP = probs.optDouble(best, 0);
        java.util.Iterator it = probs.keys();
        while (it.hasNext()) {
            String k = String.valueOf(it.next());
            double v = probs.optDouble(k, 0);
            if (v > bestP) {
                bestP = v;
                best = k;
            }
        }
        if (isEmpty(best)) return "";
        return label + " " + best + " " + toPercent(bestP);
    } catch (Throwable e) {
        return "";
    }
}

/**
 * 按动作给出话术要点（判断交给 Jev，话术交给代码）
 * @param action 最佳动作名
 * @return 话术要点
 */
String getActionTip(String action) {
    if ("先安抚".equals(action)) return "先接情绪，别讲道理";
    if ("给行动".equals(action)) return "少解释，直接做";
    if ("讲清楚".equals(action)) return "把原因说明白";
    if ("简短应承".equals(action)) return "一句话到位";
    if ("先别回".equals(action)) return "先停手，别急着回";
    return "";
}

/**
 * 按动作给出一句可直接改用的话术例句
 * @param action 最佳动作名
 * @return 例句
 */
String getActionEg(String action) {
    if ("先安抚".equals(action)) return "这事儿让你不舒服了，是我没上心。";
    if ("给行动".equals(action)) return "行，我现在去办，弄好告诉你。";
    if ("讲清楚".equals(action)) return "当时是因为……，我没提前说，是我的问题。";
    if ("简短应承".equals(action)) return "嗯，记着了。";
    if ("先别回".equals(action)) return "等情绪降下来再说。";
    return "";
}

/** ==================== 配置界面 ==================== */

/**
 * 打开插件配置页
 * 交互方式：输入框内嵌、开关原地更新、底部统一保存，全程不重开对话框
 */
void showMainDialog() {
    final Activity activity = getTopActivity();
    if (activity == null) {
        toast("无法获取当前界面，请先打开微信聊天");
        return;
    }

    final String talker = getTargetTalker();
    if (isEmpty(talker)) {
        toast("请先进入一个聊天会话再打开配置");
        return;
    }

    activity.runOnUiThread(new Runnable() {
        public void run() {
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity).create();
                dialog.setCanceledOnTouchOutside(true);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Color.TRANSPARENT);
                root.setPadding(dp(16), dp(40), dp(16), dp(40));

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setBackground(createPanelBg(activity));
                panel.setPadding(dp(18), dp(20), dp(18), dp(18));

                // ---------- 标题行 ----------
                LinearLayout headerRow = new LinearLayout(activity);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(activity);
                title.setText("元启Jev聊天助手");
                title.setTextSize(19);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(TEXT_MAIN);
                title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                headerRow.addView(title);

                TextView closeBtn = new TextView(activity);
                closeBtn.setText("✕");
                closeBtn.setTextSize(17);
                closeBtn.setTextColor(TEXT_SUB);
                closeBtn.setPadding(dp(10), dp(4), dp(4), dp(4));
                closeBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                headerRow.addView(closeBtn);
                panel.addView(headerRow);

                TextView subLabel = new TextView(activity);
                subLabel.setText("v" + pluginVersion + " · 分析结果插入为系统消息");
                subLabel.setTextSize(10);
                subLabel.setTextColor(TEXT_HINT);
                subLabel.setPadding(0, dp(6), 0, dp(14));
                panel.addView(subLabel);

                // ---------- 滚动内容 ----------
                ScrollView scroll = new ScrollView(activity);
                scroll.setVerticalScrollBarEnabled(false);
                scroll.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(400)));

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);

                content.addView(createSectionTitle(activity, "作用域（同时只开启一个会话）"));
                content.addView(createScopeCard(activity, talker));

                content.addView(createSectionTitle(activity, "API Key"));
                final EditText apiKeyInput = createInputCard(activity, content,
                        "进交流群获取 API Key", getApiKey(), false);

                content.addView(createSectionTitle(activity, "分析设置"));
                content.addView(createToggleItem(activity, "自动分析对方消息", "auto_analyze", true));
                final EditText contextInput = createInputCard(activity, content,
                        "上下文轮数，0 表示不启用，最大 " + MAX_CONTEXT_ROUNDS,
                        String.valueOf(getContextRounds()), true);
                final EditText padInput = createInputCard(activity, content,
                        "排版宽度（全角字符数），留 0 按屏幕自动估算；偏右就调小，折行就调大",
                        String.valueOf(getInt("pad_chars", 0)), true);

                content.addView(createSectionTitle(activity, "交流群"));
                content.addView(createGroupCard(activity));

                scroll.addView(content);
                panel.addView(scroll);

                // ---------- 底部按钮 ----------
                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnRowParams.topMargin = dp(14);
                btnRow.setLayoutParams(btnRowParams);

                TextView saveBtn = new TextView(activity);
                saveBtn.setText("保存");
                saveBtn.setTextSize(13);
                saveBtn.setTextColor(TEXT_MAIN);
                saveBtn.setBackground(createChipBg(true));
                saveBtn.setPadding(dp(20), dp(9), dp(20), dp(9));
                saveBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        putString("api_key", apiKeyInput.getText().toString().trim());

                        int rounds = DEFAULT_CONTEXT_ROUNDS;
                        try {
                            rounds = Integer.parseInt(contextInput.getText().toString().trim());
                        } catch (Throwable ignore) {}
                        rounds = Math.max(0, Math.min(MAX_CONTEXT_ROUNDS, rounds));
                        putInt("context_rounds", rounds);
                        contextInput.setText(String.valueOf(rounds));

                        int pad = 0;
                        try {
                            pad = Integer.parseInt(padInput.getText().toString().trim());
                        } catch (Throwable ignore) {}
                        if (pad < 0) pad = 0;
                        if (pad > 40) pad = 40;
                        putInt("pad_chars", pad);
                        padInput.setText(String.valueOf(pad));

                        toast("已保存");
                    }
                });
                btnRow.addView(saveBtn);

                TextView cancelBtn = new TextView(activity);
                cancelBtn.setText("关闭");
                cancelBtn.setTextSize(13);
                cancelBtn.setTextColor(TEXT_SUB);
                cancelBtn.setBackground(createChipBg(false));
                cancelBtn.setPadding(dp(20), dp(9), dp(20), dp(9));
                LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cancelParams.leftMargin = dp(10);
                cancelBtn.setLayoutParams(cancelParams);
                cancelBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                btnRow.addView(cancelBtn);
                panel.addView(btnRow);

                root.addView(panel);
                dialog.show();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setContentView(root);
                    // 让输入法能正常弹出并顶起布局
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }
            } catch (Throwable e) {
                log("配置页异常: " + e);
                toast("配置页打开失败: " + e.getMessage());
            }
        }
    });
}

/**
 * 分节标题
 * @param ctx 上下文
 * @param text 文本
 * @return View
 */
View createSectionTitle(Context ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(TEXT_SUB);
    tv.setPadding(0, dp(12), 0, dp(6));
    return tv;
}

/**
 * 内嵌输入框卡片
 * @param ctx 上下文
 * @param parent 内容容器
 * @param hint 占位提示
 * @param value 初始值
 * @param numberOnly 是否只允许数字
 * @return 创建好的输入框，供保存时读取
 */
EditText createInputCard(Context ctx, LinearLayout parent, String hint, String value, boolean numberOnly) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(14), dp(12), dp(14), dp(12));

    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams.bottomMargin = dp(10);
    card.setLayoutParams(cardParams);

    EditText input = new EditText(ctx);
    input.setText(value);
    input.setHint(hint);
    input.setTextSize(13);
    input.setTextColor(TEXT_MAIN);
    input.setHintTextColor(TEXT_HINT);
    input.setBackground(createInputBg(ctx));
    input.setPadding(dp(14), dp(11), dp(14), dp(11));
    input.setFocusable(true);
    input.setFocusableInTouchMode(true);
    input.setClickable(true);
    input.setLongClickable(true);
    if (numberOnly) {
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
    } else {
        input.setInputType(InputType.TYPE_CLASS_TEXT);
    }
    input.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView hintTv = new TextView(ctx);
    hintTv.setText(hint);
    hintTv.setTextSize(10);
    hintTv.setTextColor(TEXT_HINT);
    hintTv.setPadding(0, dp(6), 0, 0);

    card.addView(input);
    card.addView(hintTv);
    parent.addView(card);
    return input;
}

/**
 * 作用域卡片：点击即时切换，界面原地更新
 * @param ctx 上下文
 * @param talker 当前会话 ID
 * @return View
 */
View createScopeCard(final Context ctx, final String talker) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(14), dp(12), dp(14), dp(12));

    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams.bottomMargin = dp(10);
    card.setLayoutParams(cardParams);

    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);

    final boolean enabled = talker.equals(getActiveTalker());

    final TextView stateTv = new TextView(ctx);
    stateTv.setText(enabled ? "本会话：已开启" : "本会话：未开启");
    stateTv.setTextSize(14);
    stateTv.setTextColor(enabled ? ACCENT_GREEN : TEXT_MAIN);
    stateTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(stateTv);

    final TextView toggle = new TextView(ctx);
    toggle.setText(enabled ? "关闭" : "开启");
    toggle.setTextSize(12);
    toggle.setTextColor(TEXT_MAIN);
    toggle.setBackground(createChipBg(enabled));
    toggle.setPadding(dp(14), dp(5), dp(14), dp(5));
    row.addView(toggle);
    card.addView(row);

    TextView hint = new TextView(ctx);
    hint.setText("开启后本会话才会自动分析，同时只能开启一个\n当前会话: " + talker);
    hint.setTextSize(10);
    hint.setTextColor(TEXT_HINT);
    hint.setPadding(0, dp(6), 0, 0);
    card.addView(hint);

    card.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            if (talker.equals(getActiveTalker())) {
                setActiveTalker("");
            } else {
                setActiveTalker(talker);
            }
            boolean after = talker.equals(getActiveTalker());
            stateTv.setText(after ? "本会话：已开启" : "本会话：未开启");
            stateTv.setTextColor(after ? ACCENT_GREEN : TEXT_MAIN);
            toggle.setText(after ? "关闭" : "开启");
            toggle.setBackground(createChipBg(after));
            toast(after ? "已开启本会话，之前的会话已自动关闭" : "已关闭本会话的分析");
        }
    });
    return card;
}

/**
 * 开关行：点击即时写入配置并原地更新外观
 * @param ctx 上下文
 * @param label 名称
 * @param key 配置键
 * @param def 默认值
 * @return View
 */
View createToggleItem(final Context ctx, String label, final String key, boolean def) {
    LinearLayout item = new LinearLayout(ctx);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setBackground(createCardBg(ctx));
    item.setPadding(dp(14), dp(12), dp(14), dp(12));

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.bottomMargin = dp(10);
    item.setLayoutParams(lp);

    TextView labelTv = new TextView(ctx);
    labelTv.setText(label);
    labelTv.setTextSize(14);
    labelTv.setTextColor(TEXT_MAIN);
    labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    item.addView(labelTv);

    final boolean checked = getBoolean(key, def);
    final TextView toggle = new TextView(ctx);
    toggle.setText(checked ? "开" : "关");
    toggle.setTextSize(12);
    toggle.setTextColor(TEXT_MAIN);
    toggle.setBackground(createChipBg(checked));
    toggle.setPadding(dp(14), dp(5), dp(14), dp(5));
    item.addView(toggle);

    // 用数组保存当前状态，避免点击时重新读配置造成默认值不一致
    final boolean[] state = new boolean[]{checked};
    item.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            state[0] = !state[0];
            putBoolean(key, state[0]);
            toggle.setText(state[0] ? "开" : "关");
            toggle.setBackground(createChipBg(state[0]));
        }
    });
    return item;
}

/**
 * 交流群卡片：点击跳转到 QQ 群
 * @param ctx 上下文
 * @return View
 */
View createGroupCard(final Context ctx) {
    LinearLayout card = new LinearLayout(ctx);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(createCardBg(ctx));
    card.setPadding(dp(14), dp(12), dp(14), dp(12));

    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cardParams.bottomMargin = dp(10);
    card.setLayoutParams(cardParams);

    TextView label = new TextView(ctx);
    label.setText("加入交流群（获取 API Key）");
    label.setTextSize(14);
    label.setTextColor(ACCENT_BLUE);
    card.addView(label);

    TextView hint = new TextView(ctx);
    hint.setText("QQ群: " + GROUP_NUM);
    hint.setTextSize(10);
    hint.setTextColor(TEXT_HINT);
    hint.setPadding(0, dp(6), 0, 0);
    card.addView(hint);

    card.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(GROUP_URL));
                ctx.startActivity(intent);
            } catch (Throwable e) {
                toast("无法打开链接，请手动搜索群号: " + GROUP_NUM);
            }
        }
    });
    return card;
}

/** ==================== 界面工具 ==================== */

/**
 * dp 转像素
 * @param value dp 值
 * @return 像素值
 */
int dp(float value) {
    try {
        return (int) (value * hostContext.getResources().getDisplayMetrics().density + 0.5f);
    } catch (Throwable e) {
        return (int) value;
    }
}

/**
 * 面板背景
 * @param ctx 上下文
 * @return Drawable
 */
GradientDrawable createPanelBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_PANEL);
    gd.setCornerRadius(dp(18));
    return gd;
}

/**
 * 卡片背景
 * @param ctx 上下文
 * @return Drawable
 */
GradientDrawable createCardBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_CARD);
    gd.setCornerRadius(dp(12));
    return gd;
}

/**
 * 输入框背景
 * @param ctx 上下文
 * @return Drawable
 */
GradientDrawable createInputBg(Context ctx) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(BG_INPUT);
    gd.setCornerRadius(dp(10));
    return gd;
}

/**
 * 胶囊按钮背景
 * @param active 是否激活
 * @return Drawable
 */
GradientDrawable createChipBg(boolean active) {
    GradientDrawable gd = new GradientDrawable();
    if (active) {
        gd.setColor(Color.parseColor("#1A3A2A"));
        gd.setStroke(dp(1), ACCENT_GREEN);
    } else {
        gd.setColor(BG_INPUT);
        gd.setStroke(dp(1), DIVIDER);
    }
    gd.setCornerRadius(dp(20));
    return gd;
}

/** ==================== 小工具 ==================== */

/**
 * 概率转整数百分比
 * @param v 0-1 的概率
 * @return 形如 50%
 */
String toPercent(double v) {
    return Math.round(v * 100) + "%";
}

/**
 * 截断过长文本
 * @param text 原文本
 * @param max 最大长度
 * @return 截断后的文本
 */
String clip(String text, int max) {
    if (text == null) return "";
    if (text.length() > max) return text.substring(0, max) + "…";
    return text;
}

/**
 * 字符串是否为空
 * @param s 字符串
 * @return 是否为空
 */
boolean isEmpty(String s) {
    return s == null || s.trim().length() == 0;
}

/**
 * 反射调用对象上的无参方法
 * @param obj 目标对象
 * @param methodName 方法名
 * @return 返回值，异常时返回 null
 */
Object callMethod(Object obj, String methodName) {
    try {
        if (obj == null) return null;
        Class c = obj.getClass();
        while (c != null) {
            Method[] methods = c.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals(methodName)
                        && methods[i].getParameterCount() == 0) {
                    methods[i].setAccessible(true);
                    return methods[i].invoke(obj);
                }
            }
            c = c.getSuperclass();
        }
    } catch (Throwable e) {
        // 反射失败不影响主流程
    }
    return null;
}

/**
 * 反射读取布尔方法（如 isSend / isText）
 * @param obj 目标对象
 * @param methodName 方法名
 * @return 布尔值，异常时为 false
 */
boolean getBoolean(Object obj, String methodName) {
    Object v = callMethod(obj, methodName);
    if (v instanceof Boolean) return ((Boolean) v).booleanValue();
    return false;
}

/**
 * 反射读取字符串方法（如 getTalker / getContent）
 * @param obj 目标对象
 * @param methodName 方法名
 * @return 字符串，异常时为空串
 */
String getString(Object obj, String methodName) {
    Object v = callMethod(obj, methodName);
    if (v == null) return "";
    return String.valueOf(v);
}

/**
 * 反射读取 long 方法（如 getCreateTime / getMsgId）
 * @param obj 目标对象
 * @param methodName 方法名
 * @return 数值，异常时为 0
 */
long getLong(Object obj, String methodName) {
    Object v = callMethod(obj, methodName);
    if (v instanceof Number) return ((Number) v).longValue();
    return 0L;
}
