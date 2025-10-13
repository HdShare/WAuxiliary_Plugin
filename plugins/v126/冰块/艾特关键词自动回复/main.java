import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.*;

/*
Author: by冰块
Version: 2.0 (Tabbed UI)
Contact TG: @bingkuai_666
— Protected: do not remove/modify the header above —
*/
final String __AUTH_MARK = "[AUTH]|作者:by冰块|版本:1.0|TG:@bingkuai_666";
final String __AUTH_MD5  = "75da8347863b4fc171f5ccdde5b782ae";
boolean __protect_ok() {
    try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] d = md.digest(__AUTH_MARK.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        boolean ok = sb.toString().equals(__AUTH_MD5);
        if (!ok) try { toast("插件校验失败：作者信息被修改/删除"); } catch (Throwable ignore) {}
        return ok;
    } catch (Throwable e) { return false; }
}

/** ================= Colors & Dark Mode ================= */
private static final String C_BG        = "#F6F8FB";
private static final String C_CARD      = "#FFFFFF";
private static final String C_DIV       = "#E5E7EB";
private static final String C_TEXT_MAIN = "#0F172A";
private static final String C_TEXT_SUB  = "#64748B";
private static final String C_PRIMARY   = "#4C7DFF";
private static final String C_PRIMARY_D = "#3A66E0";
private static final String C_ACCENT    = "#22C55E";
private static final String C_WARN      = "#F97316";
private static final String C_DANGER    = "#EF4444";

private boolean isDarkMode() {
    try {
        Context c = (uiContext != null) ? uiContext : getTopActivity();
        if (c == null) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    } catch (Throwable e) { return false; }
}
private String colorBg()            { return isDarkMode() ? "#0E1116" : C_BG; }
private String colorCard()          { return isDarkMode() ? "#161B22" : C_CARD; }
private String colorDiv()           { return isDarkMode() ? "#26303C" : C_DIV; }
private String colorTextMain()      { return isDarkMode() ? "#E6EDF3" : C_TEXT_MAIN; }
private String colorTextSub()       { return isDarkMode() ? "#9DA9B5" : C_TEXT_SUB; }
private String colorInputBg()       { return isDarkMode() ? "#0F141A" : "#F8FAFC"; }
private String colorInputStroke()   { return isDarkMode() ? "#2B3B4A" : "#CBD5E1"; }
private String colorSwitchTrackOn() { return isDarkMode() ? "#1F2A44" : "#C7D5FF"; }
private String colorSwitchTrackOff(){ return isDarkMode() ? "#2B3642" : "#E2E8F0"; }
private String chipBgOn()           { return isDarkMode() ? "#0A2F1A" : "#E6F8EF"; }
private String chipBgOff()          { return isDarkMode() ? "#1F2937" : "#F2F4F8"; }

/** ================= Biz Const ================= */
private static final long DEFAULT_COOLDOWN_AT_MS = 10 * 1000L;
private static final long DEFAULT_COOLDOWN_KW_MS = 10 * 1000L;
private static final String BASE_DIR        = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/回复/";
private static final String DEFAULT_AT_FILE = "别老艾特我你个大傻逼.silk";

// Storage keys
private static final String KEY_SWITCH_AT_PREFIX    = "switch-at-";
private static final String KEY_SWITCH_KW_PREFIX    = "switch-kw-";
private static final String KEY_AT_FILE_PREFIX      = "at-file-";
private static final String KEY_KW_LIST_PREFIX      = "kw-list-";
private static final String KEY_KW_BIND_PREFIX      = "kw-bind-";
private static final String KEY_AT_CD_MS_PREFIX     = "cd-at-ms-";
private static final String KEY_KW_CD_MS_PREFIX     = "cd-kw-ms-";
// New: optional text reply
private static final String KEY_AT_TEXT_PREFIX      = "at-text-";
private static final String KEY_KW_TEXT_PREFIX      = "kw-text-";

/** ================= Runtime ================= */
private final Map<String, Long> atCooldownMap = new HashMap<String, Long>();
private final Map<String, Long> kwCooldownMap = new HashMap<String, Long>();
private final LinkedHashSet<Long> seenMsgIds = new LinkedHashSet<Long>() {
    protected boolean removeEldestEntry(Map.Entry eldest) { return false; }
};
private final Map<String, Long> tipGuard = new HashMap<String, Long>();

private volatile long lastLongClickTs = 0L;
private volatile String lastLongClickKey = "";
private volatile long lastDialogShowTs = 0L;
private Dialog currentDialog = null;
private volatile Context uiContext = null;

// For unique IDs
private static int __VID = 100000;
private int genViewId() {
    try { if (Build.VERSION.SDK_INT >= 17) return View.generateViewId(); } catch (Throwable ignore) {}
    __VID++; if (__VID > 0x1FFFFF) __VID = 100000;
    return __VID;
}

/** ================= Utils ================= */
private int dp(int v) {
    try {
        Context c = (uiContext != null) ? uiContext : getTopActivity();
        if (c == null) return v;
        return (int)(v * c.getResources().getDisplayMetrics().density + 0.5f);
    } catch (Throwable e) { return v; }
}
private boolean isOn(String key) { return !"__off__".equals(getString(key, "__off__")); }

private void rememberMsgId(long id) {
    synchronized (seenMsgIds) {
        seenMsgIds.add(id);
        if (seenMsgIds.size() > 200) {
            Iterator<Long> it = seenMsgIds.iterator();
            while (seenMsgIds.size() > 180 && it.hasNext()) { it.next(); it.remove(); }
        }
    }
}
private boolean hasSeenMsgId(long id) { synchronized (seenMsgIds) { return seenMsgIdContains(id); } }
private boolean seenMsgIdContains(long id) { return seenMsgIds.contains(id); }

private boolean isDuplicateLongClick(String roomId, String text, long now) {
    String key = roomId + "|" + text;
    if (key.equals(lastLongClickKey) && (now - lastLongClickTs) < 800) return true;
    lastLongClickKey = key; lastLongClickTs = now; return false;
}

private boolean passTipGuard(String key, long now, long windowMs) {
    Long last = tipGuard.get(key);
    if (last != null && (now - last) < windowMs) return false;
    tipGuard.put(key, now);
    if (tipGuard.size() > 256) {
        Iterator<Map.Entry<String, Long>> it = tipGuard.entrySet().iterator();
        while (tipGuard.size() > 180 && it.hasNext()) { it.next(); it.remove(); }
    }
    return true;
}
private void safeInsertSystemMsg(String roomId, String content) {
    long now = System.currentTimeMillis();
    String key = "sys|" + roomId + "|" + content;
    if (passTipGuard(key, now, 1500)) insertSystemMsg(roomId, content, now);
}
private void safeToast(String content) {
    long now = System.currentTimeMillis();
    if (passTipGuard("toast|" + content, now, 1500)) toast(content);
}

private GradientDrawable shape(String color, int radiusDp) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(Color.parseColor(color));
    g.setCornerRadius(dp(radiusDp));
    return g;
}
private StateListDrawable pressBg(String normal, String pressed, int radiusDp) {
    StateListDrawable s = new StateListDrawable();
    GradientDrawable gN = shape(normal, radiusDp);
    GradientDrawable gP = shape(pressed, radiusDp);
    s.addState(new int[]{android.R.attr.state_pressed}, gP);
    s.addState(new int[]{}, gN);
    return s;
}

/** Card container */
private LinearLayout card(Context c) {
    LinearLayout box = new LinearLayout(c);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(14), dp(12), dp(14), dp(12));
    GradientDrawable bg = shape(colorCard(), 12);
    bg.setStroke(dp(1), Color.parseColor(isDarkMode() ? "#1E2732" : "#14000000"));
    box.setBackground(bg);
    return box;
}

private View divider(Context c) {
    View v = new View(c);
    v.setBackgroundColor(Color.parseColor(colorDiv()));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
    lp.topMargin = dp(10); lp.bottomMargin = dp(10);
    v.setLayoutParams(lp);
    return v;
}
private TextView sectionTitle(Context c, String text) {
    TextView t = new TextView(c);
    t.setText(text);
    t.setTextSize(16);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setTextColor(Color.parseColor(colorTextMain()));
    t.setPadding(0, dp(2), 0, dp(8));
    return t;
}
private TextView label(Context c, String text, String bg, String fg) {
    TextView tv = new TextView(c);
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(Color.parseColor(fg));
    tv.setPadding(dp(8), dp(4), dp(8), dp(4));
    tv.setBackground(shape(bg, 999));
    return tv;
}
private Button pillButton(Context c, String text, String normal, String pressed) {
    Button b = new Button(c);
    b.setAllCaps(false);
    b.setText(text);
    b.setTextColor(Color.WHITE);
    b.setTextSize(14);
    b.setPadding(dp(16), dp(10), dp(16), dp(10));
    b.setBackground(pressBg(normal, pressed, 999));
    return b;
}
private Button ghostButton(Context c, String text, String strokeColor) {
    Button b = new Button(c);
    b.setAllCaps(false);
    b.setText(text);
    b.setTextSize(14);
    b.setPadding(dp(14), dp(8), dp(14), dp(8));
    GradientDrawable g = shape("#00FFFFFF", 999);
    g.setStroke(dp(1), Color.parseColor(strokeColor));
    b.setBackground(g);
    b.setTextColor(Color.parseColor(strokeColor));
    return b;
}
private void tintSwitch(Switch sw, String thumb, String track) {
    try {
        if (Build.VERSION.SDK_INT >= 21) {
            if (sw.getThumbDrawable() != null)
                sw.getThumbDrawable().setColorFilter(Color.parseColor(thumb), PorterDuff.Mode.SRC_IN);
            if (sw.getTrackDrawable() != null)
                sw.getTrackDrawable().setColorFilter(Color.parseColor(track), PorterDuff.Mode.SRC_IN);
        }
    } catch (Throwable ignore) {}
}

/** Inputs */
private void styleInput(EditText e) {
    try {
        e.setTextSize(14);
        e.setTextColor(Color.parseColor(colorTextMain()));
        e.setHintTextColor(Color.parseColor(colorTextSub()));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(colorInputBg()));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), Color.parseColor(colorInputStroke()));
        e.setBackground(g);
    } catch (Throwable ignore) {}
}

/** ================= Cooldowns & Text ================= */
private long getAtCooldownMs(String roomId) { long v = getLong(KEY_AT_CD_MS_PREFIX + roomId, -1L); return v > -1 ? v : DEFAULT_COOLDOWN_AT_MS; }
private long getKwCooldownMs(String roomId) { long v = getLong(KEY_KW_CD_MS_PREFIX + roomId, -1L); return v > -1 ? v : DEFAULT_COOLDOWN_KW_MS; }
private void setAtCooldownSec(String roomId, long sec) { if (sec < 0) sec = 0; putLong(KEY_AT_CD_MS_PREFIX + roomId, sec * 1000L); }
private void setKwCooldownSec(String roomId, long sec) { if (sec < 0) sec = 0; putLong(KEY_KW_CD_MS_PREFIX + roomId, sec * 1000L); }

private String readAtText(String roomId) { return getString(KEY_AT_TEXT_PREFIX + roomId, ""); }
private void writeAtText(String roomId, String s) { putString(KEY_AT_TEXT_PREFIX + roomId, s == null ? "" : s.trim()); }
private String kwTextKey(String roomId, String kw) { return KEY_KW_TEXT_PREFIX + roomId + "|" + (kw == null ? "" : kw.trim()); }
private String readKwText(String roomId, String kw) { return getString(kwTextKey(roomId, kw), ""); }
private void writeKwText(String roomId, String kw, String s) { putString(kwTextKey(roomId, kw), s == null ? "" : s.trim()); }
private void removeKwText(String roomId, String kw) { putString(kwTextKey(roomId, kw), null); }

/** ================= KW storage ================= */
private List<String> readKwList(String roomId) {
    String raw = getString(KEY_KW_LIST_PREFIX + roomId, "").trim();
    List<String> out = new ArrayList<String>();
    if (raw.length() == 0) return out;
    String[] arr = raw.split("\n");
    for (String s : arr) {
        String k = s == null ? "" : s.trim();
        if (k.length() > 0 && !out.contains(k)) out.add(k);
    }
    return out;
}
private void writeKwList(String roomId, List<String> list) {
    if (list == null || list.isEmpty()) { putString(KEY_KW_LIST_PREFIX + roomId, ""); return; }
    StringBuilder sb = new StringBuilder();
    for (String k : list) {
        if (k == null) continue;
        k = k.trim(); if (k.length() == 0) continue;
        if (sb.length() > 0) sb.append("\n");
        sb.append(k);
    }
    putString(KEY_KW_LIST_PREFIX + roomId, sb.toString());
}
private String kwBindKey(String roomId, String kw) { return KEY_KW_BIND_PREFIX + roomId + "|" + (kw == null ? "" : kw.trim()); }
private String readKwBind(String roomId, String kw) { return getString(kwBindKey(roomId, kw), ""); }
private void writeKwBind(String roomId, String kw, String fileName) { putString(kwBindKey(roomId, kw), fileName == null ? "" : fileName.trim()); }
private void removeKwBind(String roomId, String kw) { putString(kwBindKey(roomId, kw), null); }

// At bind
private String readAtBind(String roomId) { return getString(KEY_AT_FILE_PREFIX + roomId, ""); }
private void writeAtBind(String roomId, String fileName) { putString(KEY_AT_FILE_PREFIX + roomId, fileName == null ? "" : fileName.trim()); }

/** ================= Media helpers ================= */
private boolean isSilk(String name) {
    if (name == null) return false;
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith(".silk");
}
private boolean isImage(String name) {
    if (name == null) return false;
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".webp");
}
private boolean isVideo(String name) {
    if (name == null) return false;
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".m4v") || n.endsWith(".mkv") || n.endsWith(".avi");
}
private void sendMediaAuto(String roomId, String path) {
    try {
        if (path == null || path.length() == 0) return;
        String name = path;
        int idx = path.lastIndexOf('/');
        if (idx >= 0 && idx < path.length()-1) name = path.substring(idx+1);
        if (isSilk(name)) {
            sendVoice(roomId, path);
        } else if (isImage(name)) {
            sendImage(roomId, path);
        } else if (isVideo(name)) {
            sendVideo(roomId, path);
        } else {
            safeInsertSystemMsg(roomId, "不支持的文件类型：" + name);
        }
    } catch (Throwable e) { log("sendMediaAuto 异常: " + e); }
}

/** ================= Files ================= */
private List<String> listMediaFiles() {
    List<String> out = new ArrayList<String>();
    try {
        File dir = new File(BASE_DIR);
        if (!dir.exists() || !dir.isDirectory()) return out;
        File[] files = dir.listFiles(); if (files == null) return out;
        for (File f : files) {
            if (f != null && f.isFile()) {
                String n = f.getName().toLowerCase(Locale.ROOT).trim();
                if (n.endsWith(".silk") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".mp4") || n.endsWith(".mov")
                        || n.endsWith(".m4v") || n.endsWith(".mkv") || n.endsWith(".avi")) {
                    out.add(f.getName());
                }
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    } catch (Throwable e) { log("listMediaFiles 异常: " + e); }
    return out;
}

/** ================= Message handling ================= */
public void onHandleMsg(Object data) {
    if (!__protect_ok()) return;
    if (data == null || data.isSend()) return;
    String roomId = data.getTalker();
    if (roomId == null || roomId.length() == 0) return;
    try { long msgId = data.getMsgId(); if (hasSeenMsgId(msgId)) return; rememberMsgId(msgId); } catch (Throwable ignore) {}
    boolean handled = false;

    // keywords first
    if (isOn(KEY_SWITCH_KW_PREFIX + roomId) && data.isText()) {
        String content = String.valueOf(data.getContent());
        if (content != null && content.length() > 0) {
            List<String> kwList = readKwList(roomId);
            String hitKw = null;
            for (String kw : kwList) {
                if (kw != null && kw.length() > 0 && content.contains(kw)) { hitKw = kw; break; }
            }
            if (hitKw != null) {
                long now = System.currentTimeMillis();
                Long last = kwCooldownMap.get(roomId);
                long cd = getKwCooldownMs(roomId);
                if (last == null || now - last >= cd) {
                    // Text first (optional)
                    try {
                        String t = readKwText(roomId, hitKw);
                        if (t != null && t.trim().length() > 0) sendText(roomId, t);
                    } catch (Throwable ignore) {}
                    // Media (optional)
                    String fileName = readKwBind(roomId, hitKw);
                    if (fileName != null && fileName.length() > 0) {
                        String path = BASE_DIR + fileName;
                        if (new File(path).exists()) sendMediaAuto(roomId, path);
                        else safeInsertSystemMsg(roomId, "关键词文件不存在：" + path);
                    } else {
                        safeInsertSystemMsg(roomId, "关键词命中（未绑定文件）：" + hitKw);
                    }
                    kwCooldownMap.put(roomId, now);
                    handled = true;
                }
            }
        }
    }

    // at mention
    if (!handled && isOn(KEY_SWITCH_AT_PREFIX + roomId)) {
        boolean atMe = false;
        try {
            if (data.isAtMe()) atMe = true;
            if (!atMe) {
                List atList = data.getAtUserList();
                String me = getLoginWxid();
                if (atList != null && me != null && atList.contains(me)) atMe = true;
            }
            if (!atMe && (data.isAnnounceAll() || data.isNotifyAll())) atMe = true;
        } catch (Throwable e) { log("AT 检测异常: " + e); }
        if (atMe) {
            long now = System.currentTimeMillis();
            Long last = atCooldownMap.get(roomId);
            long cd = getAtCooldownMs(roomId);
            if (last == null || now - last >= cd) {
                // Text first (optional)
                try {
                    String t = readAtText(roomId);
                    if (t != null && t.trim().length() > 0) sendText(roomId, t);
                } catch (Throwable ignore) {}

                String chosen = readAtBind(roomId);
                if (chosen == null || chosen.length() == 0) chosen = DEFAULT_AT_FILE;
                String path = BASE_DIR + chosen;
                if (new File(path).exists()) sendMediaAuto(roomId, path);
                else safeInsertSystemMsg(roomId, "艾特文件不存在：" + path);
                atCooldownMap.put(roomId, now);
                handled = true;
            }
        }
    }
}

/** ================= Long-press commands ================= */
public boolean onLongClickSendBtn(String text) {
    if (!__protect_ok()) return false;
    String roomId = "" + getTargetTalker();
    if (roomId == null || roomId.length() == 0) return false;
    long now = System.currentTimeMillis();
    if (isDuplicateLongClick(roomId, String.valueOf(text), now)) return true;

    if ("回复设置".equals(text) || "/回复设置".equals(text)) {
        showReplySettingsDialog(roomId); return true;
    }
    if ("开启艾特回复".equals(text)) { putString(KEY_SWITCH_AT_PREFIX + roomId, "1"); safeInsertSystemMsg(roomId, "已开启艾特回复"); return true; }
    if ("关闭艾特回复".equals(text)) { putString(KEY_SWITCH_AT_PREFIX + roomId, null); safeInsertSystemMsg(roomId, "已关闭艾特回复"); return true; }
    if ("开启关键词回复".equals(text)) { putString(KEY_SWITCH_KW_PREFIX + roomId, "1"); safeInsertSystemMsg(roomId, "已开启关键词回复"); return true; }
    if ("关闭关键词回复".equals(text)) { putString(KEY_SWITCH_KW_PREFIX + roomId, null); safeInsertSystemMsg(roomId, "已关闭关键词回复"); return true; }
    return false;
}

/** ================= Dialog (Tabbed) ================= */
private String safeGroupName(String roomId) {
    try {
        String n = getFriendName(roomId);
        if (n == null || n.trim().length() == 0) n = roomId;
        return n;
    } catch (Throwable e) { return roomId; }
}
private void showReplySettingsDialog(final String roomId) {
    if (!__protect_ok()) return;
    final Activity activity = getTopActivity();
    if (activity == null) { safeToast("当前界面不可用，稍后再试"); return; }

    if ((System.currentTimeMillis() - lastDialogShowTs) < 700) return;

    new Handler(Looper.getMainLooper()).post(new Runnable() {
        public void run() {
            try {
                uiContext = activity;

                if (currentDialog != null && currentDialog.isShowing()) {
                    try { currentDialog.dismiss(); } catch (Throwable ignore) {}
                }

                Dialog dialog = new Dialog(activity);
                currentDialog = dialog;

                // Root with rounded corners
                LinearLayout root = new LinearLayout(uiContext);
                root.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable dialogBg = new GradientDrawable();
                dialogBg.setColor(Color.parseColor(colorBg()));
                dialogBg.setCornerRadius(dp(16));
                dialogBg.setStroke(dp(1), Color.parseColor(isDarkMode() ? "#222B36" : "#14000000"));
                root.setBackground(dialogBg);

                // Header (rounded top)
                LinearLayout header = new LinearLayout(uiContext);
                header.setOrientation(LinearLayout.VERTICAL);
                header.setPadding(dp(18), dp(18), dp(18), dp(14));
                GradientDrawable headBg = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{ Color.parseColor(C_PRIMARY), Color.parseColor(C_PRIMARY_D) }
                );
                headBg.setCornerRadii(new float[]{
                        dp(16), dp(16),
                        dp(16), dp(16),
                        0, 0, 0, 0
                });
                header.setBackground(headBg);

                TextView title = new TextView(uiContext);
                title.setText("回复设置（" + safeGroupName(roomId) + "）");
                title.setTextSize(18);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.WHITE);
                header.addView(title);

                                root.addView(header, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                // Scroll container
                ScrollView scroll = new ScrollView(uiContext);
                LinearLayout body = new LinearLayout(uiContext);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding(dp(14), dp(14), dp(14), dp(18));
                scroll.addView(body);
                root.addView(scroll, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                // Author (centered, single line)
                LinearLayout authorCard = card(uiContext);
                TextView author = new TextView(uiContext);
                author.setText("作者:by冰块    版本:2.0    TG:@bingkuai_666");
                author.setTextSize(12);
                author.setTextColor(Color.parseColor(colorTextSub()));
                author.setGravity(Gravity.CENTER);
                authorCard.addView(author, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                body.addView(authorCard, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                // Tabs bar
                body.addView(space(dp(10)));
                final LinearLayout tabs = new LinearLayout(uiContext);
                tabs.setOrientation(LinearLayout.HORIZONTAL);
                tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
                GradientDrawable tabsBg = shape(colorCard(), 12);
                tabsBg.setStroke(dp(1), Color.parseColor(isDarkMode() ? "#1E2732" : "#14000000"));
                tabs.setBackground(tabsBg);
                body.addView(tabs);

                final TextView tabAt = new TextView(uiContext);
                final TextView tabKw = new TextView(uiContext);
                styleTab(tabAt, "艾特回复");
                styleTab(tabKw, "关键词回复");

                LinearLayout.LayoutParams lpTab = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpTab.setMargins(dp(2), dp(2), dp(2), dp(2));
                tabs.addView(tabAt, lpTab);
                tabs.addView(tabKw, lpTab);

                // Pages
                body.addView(space(dp(10)));
                final LinearLayout pageAt = new LinearLayout(uiContext);
                pageAt.setOrientation(LinearLayout.VERTICAL);
                final LinearLayout pageKw = new LinearLayout(uiContext);
                pageKw.setOrientation(LinearLayout.VERTICAL);
                body.addView(pageAt);
                body.addView(pageKw);

                // Fill pages with existing sections
                addSectionAtUnified(pageAt, roomId);
                addSectionKwUnified(pageKw, roomId);

                // Default select AT page
                selectTab(tabAt, tabKw, pageAt, pageKw, true);

                tabAt.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { selectTab(tabAt, tabKw, pageAt, pageKw, true); }
                });
                tabKw.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { selectTab(tabAt, tabKw, pageAt, pageKw, false); }
                });

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(root);
                dialog.setCanceledOnTouchOutside(true);

                Window window = dialog.getWindow();
                if (window != null) {
                    WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                    int width = wm.getDefaultDisplay().getWidth();
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.width = Math.min((int)(width * 0.92f), width - dp(48));
                    window.setAttributes(lp);
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                }

                dialog.show();
                lastDialogShowTs = System.currentTimeMillis();

            } catch (Throwable e) {
                log("showReplySettingsDialog 异常: " + e);
                safeToast("弹窗构建失败：" + e.getClass().getSimpleName());
            }
        }
    });
}
private void styleTab(TextView tv, String text) {
    tv.setText(text);
    tv.setGravity(Gravity.CENTER);
    tv.setPadding(dp(12), dp(10), dp(12), dp(10));
    tv.setTextSize(14);
    tv.setTextColor(Color.parseColor(colorTextMain()));
    tv.setBackground(shape(colorCard(), 10));
}
private void setTabActive(TextView tv, boolean active) {
    GradientDrawable g = new GradientDrawable();
    g.setCornerRadius(dp(10));
    if (active) {
        g.setColor(Color.parseColor(isDarkMode() ? "#0E1D3A" : "#E8F0FF"));
        g.setStroke(dp(1), Color.parseColor(isDarkMode() ? "#2C3960" : "#BBD0FF"));
        tv.setTextColor(Color.parseColor(isDarkMode() ? "#D6E2FF" : "#1F3B90"));
    } else {
        g.setColor(Color.parseColor(colorCard()));
        g.setStroke(dp(1), Color.parseColor(isDarkMode() ? "#1E2732" : "#14000000"));
        tv.setTextColor(Color.parseColor(colorTextMain()));
    }
    tv.setBackground(g);
}
private void selectTab(TextView tabAt, TextView tabKw, LinearLayout pageAt, LinearLayout pageKw, boolean at) {
    setTabActive(tabAt, at);
    setTabActive(tabKw, !at);
    pageAt.setVisibility(at ? View.VISIBLE : View.GONE);
    pageKw.setVisibility(at ? View.GONE : View.VISIBLE);
}
private View space(int h) {
    View v = new View(uiContext);
    v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
    return v;
}

/** ================= Section: AT ================= */
private void addSectionAtUnified(LinearLayout parent, final String roomId) {
    LinearLayout card = card(uiContext);
    parent.addView(card, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    // Title + status
    LinearLayout rowTop = new LinearLayout(uiContext);
    rowTop.setOrientation(LinearLayout.HORIZONTAL);
    rowTop.setGravity(Gravity.CENTER_VERTICAL);

    TextView h = sectionTitle(uiContext, "🔔 艾特回复");
    LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    rowTop.addView(h, lpH);

    final boolean atOnInit = isOn(KEY_SWITCH_AT_PREFIX + roomId);
    final TextView statusChip = label(uiContext, atOnInit ? "已开启" : "未开启",
            atOnInit ? chipBgOn() : chipBgOff(),
            atOnInit ? C_ACCENT : colorTextSub());
    rowTop.addView(statusChip);

    card.addView(rowTop);
    card.addView(divider(uiContext));

    // Switch + cooldown
    final String switchKey = KEY_SWITCH_AT_PREFIX + roomId;
    final long cdSecInitial = getAtCooldownMs(roomId) / 1000L;

    LinearLayout rowSwitch = new LinearLayout(uiContext);
    rowSwitch.setOrientation(LinearLayout.HORIZONTAL);
    rowSwitch.setGravity(Gravity.CENTER_VERTICAL);

    TextView swLabel = new TextView(uiContext);
    swLabel.setText("功能开关");
    swLabel.setTextSize(14);
    swLabel.setTextColor(Color.parseColor(colorTextMain()));
    LinearLayout.LayoutParams lpLbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    rowSwitch.addView(swLabel, lpLbl);

    final Switch sw = new Switch(uiContext);
    sw.setChecked(atOnInit);
    tintSwitch(sw, atOnInit ? C_PRIMARY : "#B0B8C4", atOnInit ? colorSwitchTrackOn() : colorSwitchTrackOff());
    rowSwitch.addView(sw);

    card.addView(rowSwitch);
    card.addView(space(dp(6)));

    final TextView tip = new TextView(uiContext);
    tip.setText((atOnInit ? "状态：已开启" : "状态：未开启") + "（当前冷却：" + cdSecInitial + " 秒）");
    tip.setTextSize(12);
    tip.setTextColor(Color.parseColor(colorTextSub()));
    card.addView(tip);

    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            putString(switchKey, isChecked ? "1" : null);
            tintSwitch((Switch)buttonView, isChecked ? C_PRIMARY : "#B0B8C4", isChecked ? colorSwitchTrackOn() : colorSwitchTrackOff());
            statusChip.setText(isChecked ? "已开启" : "未开启");
            statusChip.setBackground(shape(isChecked ? chipBgOn() : chipBgOff(), 999));
            statusChip.setTextColor(Color.parseColor(isChecked ? C_ACCENT : colorTextSub()));
            tip.setText((isChecked ? "状态：已开启" : "状态：未开启") + "（当前冷却：" + (getAtCooldownMs(roomId) / 1000L) + " 秒）");
            safeToast(isChecked ? "已开启艾特回复" : "已关闭艾特回复");
        }
    });

    // Cooldown
    LinearLayout cdRow = new LinearLayout(uiContext);
    cdRow.setOrientation(LinearLayout.HORIZONTAL);
    cdRow.setGravity(Gravity.CENTER_VERTICAL);

    final EditText et = new EditText(uiContext);
    et.setHint("艾特冷却（秒）");
    et.setInputType(InputType.TYPE_CLASS_NUMBER);
    et.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
    et.setText(String.valueOf(cdSecInitial));
    styleInput(et);
    LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    lpEt.rightMargin = dp(10);
    cdRow.addView(et, lpEt);

    Button btnSaveCd = pillButton(uiContext, "保存冷却", C_PRIMARY, C_PRIMARY_D);
    btnSaveCd.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                String s = String.valueOf(et.getText()).trim();
                long sec = (s.length() == 0) ? 0L : Long.parseLong(s);
                if (sec < 0) sec = 0; if (sec > 86400L) sec = 86400L;
                setAtCooldownSec(roomId, sec);
                tip.setText((isOn(switchKey) ? "状态：已开启" : "状态：未开启") + "（当前冷却：" + sec + " 秒）");
                safeToast("艾特冷却已保存：" + sec + " 秒");
            } catch (Throwable e) { safeToast("输入有误"); }
        }
    });
    cdRow.addView(btnSaveCd);
    card.addView(space(dp(6)));
    card.addView(cdRow);

    // Text reply (optional)
    card.addView(space(dp(6)));
    final EditText etText = new EditText(uiContext);
    etText.setHint("输入要发送的文本内容");
    etText.setText(readAtText(roomId));
    etText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    etText.setMaxLines(4);
    etText.setVerticalScrollBarEnabled(true);
    etText.setMovementMethod(new ScrollingMovementMethod());
    etText.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
    etText.setOnTouchListener(new View.OnTouchListener(){
        public boolean onTouch(View v, MotionEvent event){
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        }
    });
    styleInput(etText);
    card.addView(etText);

    // Media choose
    card.addView(space(dp(8)));
    final List<String> allMedia = listMediaFiles();
    if (allMedia.isEmpty()) {
        TextView empty = new TextView(uiContext);
        empty.setText("未在目录找到可用文件：\n" + BASE_DIR);
        empty.setTextSize(13);
        empty.setTextColor(Color.parseColor(C_DANGER));
        card.addView(empty);
        return;
    }

    final String current = readAtBind(roomId);
    final RadioGroup rg = new RadioGroup(uiContext);
    rg.setOrientation(RadioGroup.VERTICAL);
    int checkedId = -1;
    for (int i = 0; i < allMedia.size(); i++) {
        String fn = allMedia.get(i);
        RadioButton rb = new RadioButton(uiContext);
        rb.setId(genViewId());
        rb.setText(fn);
        rb.setTextColor(Color.parseColor(colorTextMain()));
        rb.setPadding(0, dp(6), 0, dp(6));
        rg.addView(rb);
        if (fn.equals(current)) checkedId = rb.getId();
    }
    if (checkedId != -1) rg.check(checkedId);
    card.addView(rg);

    LinearLayout act = new LinearLayout(uiContext);
    act.setOrientation(LinearLayout.HORIZONTAL);

    Button save = pillButton(uiContext, "保存", C_ACCENT, "#17A34A");
    save.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            // 同步保存文本与所选媒体
            writeAtText(roomId, String.valueOf(etText.getText()));
            int id = rg.getCheckedRadioButtonId();
            String sel = "";
            if (id != -1) {
                RadioButton rb = (RadioButton) rg.findViewById(id);
                sel = rb == null ? "" : String.valueOf(rb.getText()).trim();
            }
            writeAtBind(roomId, sel);
            safeToast("已保存（文本 + 媒体）" + (sel.length()>0? ("："+sel):"（未绑定文件）"));
        }
    });
    act.addView(save);

    View spacer = new View(uiContext);
    LinearLayout.LayoutParams lpSp = new LinearLayout.LayoutParams(0, 1, 1f);
    act.addView(spacer, lpSp);

    Button hint = ghostButton(uiContext, "使用默认", colorTextSub());
    hint.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { safeToast("未绑定时默认使用：" + DEFAULT_AT_FILE); }
    });
    act.addView(hint);

    card.addView(space(dp(6)));
    card.addView(act);
}

/** ================= Section: Keywords ================= */
private void addSectionKwUnified(LinearLayout parent, final String roomId) {
    LinearLayout card = card(uiContext);
    parent.addView(card, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    // Title + status
    LinearLayout rowTop = new LinearLayout(uiContext);
    rowTop.setOrientation(LinearLayout.HORIZONTAL);
    rowTop.setGravity(Gravity.CENTER_VERTICAL);

    TextView h = sectionTitle(uiContext, "🧩 关键词回复");
    LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    rowTop.addView(h, lpH);

    final boolean kwOnInit = isOn(KEY_SWITCH_KW_PREFIX + roomId);
    final TextView statusChip = label(uiContext, kwOnInit ? "已开启" : "未开启",
            kwOnInit ? chipBgOn() : chipBgOff(),
            kwOnInit ? C_ACCENT : colorTextSub());
    rowTop.addView(statusChip);
    card.addView(rowTop);
    card.addView(divider(uiContext));

    final String switchKey = KEY_SWITCH_KW_PREFIX + roomId;
    final long cdSecInitial = getKwCooldownMs(roomId) / 1000L;

    // Switch row
    LinearLayout rowSwitch = new LinearLayout(uiContext);
    rowSwitch.setOrientation(LinearLayout.HORIZONTAL);
    rowSwitch.setGravity(Gravity.CENTER_VERTICAL);

    TextView swLabel = new TextView(uiContext);
    swLabel.setText("功能开关");
    swLabel.setTextSize(14);
    swLabel.setTextColor(Color.parseColor(colorTextMain()));
    LinearLayout.LayoutParams lpLbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    rowSwitch.addView(swLabel, lpLbl);

    final Switch sw = new Switch(uiContext);
    sw.setChecked(kwOnInit);
    tintSwitch(sw, kwOnInit ? C_PRIMARY : "#B0B8C4", kwOnInit ? colorSwitchTrackOn() : colorSwitchTrackOff());
    rowSwitch.addView(sw);

    card.addView(rowSwitch);
    card.addView(space(dp(6)));

    final TextView tip = new TextView(uiContext);
    tip.setText((kwOnInit ? "状态：已开启" : "状态：未开启") + "（当前冷却：" + cdSecInitial + " 秒）");
    tip.setTextSize(12);
    tip.setTextColor(Color.parseColor(colorTextSub()));
    card.addView(tip);

    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            putString(switchKey, isChecked ? "1" : null);
            tintSwitch((Switch)buttonView, isChecked ? C_PRIMARY : "#B0B8C4", isChecked ? colorSwitchTrackOn() : colorSwitchTrackOff());
            statusChip.setText(isChecked ? "已开启" : "未开启");
            statusChip.setBackground(shape(isChecked ? chipBgOn() : chipBgOff(), 999));
            statusChip.setTextColor(Color.parseColor(isChecked ? C_ACCENT : colorTextSub()));
            tip.setText((isChecked ? "状态：已开启" : "状态：未开启") + "（当前冷却：" + (getKwCooldownMs(roomId) / 1000L) + " 秒）");
            safeToast(isChecked ? "已开启关键词回复" : "已关闭关键词回复");
        }
    });

    // List container
    card.addView(space(dp(8)));
    final LinearLayout listWrap = new LinearLayout(uiContext);
    listWrap.setOrientation(LinearLayout.VERTICAL);
    card.addView(listWrap);

    final Runnable render = new Runnable() {
        public void run() {
            listWrap.removeAllViews();
            final List<String> allMedia = listMediaFiles();
            final List<String> kwList = readKwList(roomId);

            if (kwList.isEmpty()) {
                TextView empty = new TextView(uiContext);
                empty.setText("暂无关键词，点击下方「新增关键词」添加");
                empty.setTextSize(13);
                empty.setTextColor(Color.parseColor(colorTextSub()));
                listWrap.addView(empty);
            }

            for (final String originKw : kwList) {
                final String originMedia = readKwBind(roomId, originKw);
                final String originText  = readKwText(roomId, originKw);

                LinearLayout itemCard = card(uiContext);

                // 关键词输入
                final EditText etKw = new EditText(uiContext);
                etKw.setHint("关键词");
                etKw.setSingleLine(true);
                etKw.setText(originKw);
                styleInput(etKw);
                itemCard.addView(etKw);

                itemCard.addView(space(dp(6)));

                // 文本输入（可选）
                final EditText etText = new EditText(uiContext);
                etText.setHint("输入要发送的文本内容");
                etText.setText(originText);
                etText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                etText.setMaxLines(4);
                etText.setVerticalScrollBarEnabled(true);
                etText.setMovementMethod(new ScrollingMovementMethod());
                etText.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                etText.setOnTouchListener(new View.OnTouchListener(){
                    public boolean onTouch(View v, MotionEvent event){
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        if (event.getAction() == MotionEvent.ACTION_UP) v.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                    }
                });
                styleInput(etText);
                itemCard.addView(etText);

                itemCard.addView(space(dp(6)));

                // 媒体单选
                final RadioGroup rg = new RadioGroup(uiContext);
                rg.setOrientation(RadioGroup.VERTICAL);
                int checkedId = -1;
                for (int i = 0; i < allMedia.size(); i++) {
                    String fn = allMedia.get(i);
                    RadioButton rb = new RadioButton(uiContext);
                    rb.setId(genViewId());
                    rb.setText(fn);
                    rb.setTextColor(Color.parseColor(colorTextMain()));
                    rb.setPadding(0, dp(6), 0, dp(6));
                    rg.addView(rb);
                    if (fn.equals(originMedia)) checkedId = rb.getId();
                }
                if (checkedId != -1) rg.check(checkedId);
                itemCard.addView(rg);

                itemCard.addView(space(dp(8)));

                // 操作行
                LinearLayout rowBtns = new LinearLayout(uiContext);
                rowBtns.setOrientation(LinearLayout.HORIZONTAL);

                Button save = pillButton(uiContext, "保存", C_ACCENT, "#17A34A");
                final Runnable rerender = new Runnable() { public void run() { listWrap.post(this); } };
                save.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String newKw = String.valueOf(etKw.getText()).trim();
                        if (newKw.length() == 0) { safeToast("关键词不能为空"); return; }
                        int id = rg.getCheckedRadioButtonId();
                        String newMedia = "";
                        if (id != -1) {
                            RadioButton rb = (RadioButton) rg.findViewById(id);
                            newMedia = rb == null ? "" : String.valueOf(rb.getText()).trim();
                        }
                        String newText = String.valueOf(etText.getText()).trim();

                        List<String> curList = readKwList(roomId);
                        if (!newKw.equals(originKw)) {
                            if (curList.contains(newKw)) { safeToast("已存在同名关键词：" + newKw); return; }
                            curList.remove(originKw);
                            curList.add(newKw);
                            writeKwList(roomId, curList);
                            removeKwBind(roomId, originKw);
                            removeKwText(roomId, originKw);
                        }
                        writeKwBind(roomId, newKw, newMedia);
                        writeKwText(roomId, newKw, newText);
                        safeToast("已保存：" + newKw + (newMedia.length()==0 ? "（未绑定文件）" : (" -> " + newMedia)));
                        listWrap.post(this);
                    }
                });
                rowBtns.addView(save);

                View spacer = new View(uiContext);
                rowBtns.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

                Button del = ghostButton(uiContext, "删除", C_DANGER);
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        List<String> curList = readKwList(roomId);
                        curList.remove(originKw);
                        writeKwList(roomId, curList);
                        removeKwBind(roomId, originKw);
                        removeKwText(roomId, originKw);
                        safeToast("已删除：" + originKw);
                        listWrap.post(this);
                    }
                });
                rowBtns.addView(del);

                itemCard.addView(rowBtns);

                LinearLayout.LayoutParams lpItem = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpItem.bottomMargin = dp(10);
                listWrap.addView(itemCard, lpItem);
            }
        }
    };
    render.run();

    LinearLayout bottom = new LinearLayout(uiContext);
    bottom.setOrientation(LinearLayout.HORIZONTAL);

    Button addBtn = pillButton(uiContext, "新增关键词", C_PRIMARY, C_PRIMARY_D);
    addBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            List<String> cur = readKwList(roomId);
            int n = cur.size() + 1;
            String kw = "新关键词" + n;
            int seq = n;
            while (cur.contains(kw)) { seq++; kw = "新关键词" + seq; }
            cur.add(kw);
            writeKwList(roomId, cur);
            listWrap.post(render);
        }
    });
    bottom.addView(addBtn);

    View spacer = new View(uiContext);
    bottom.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

    Button guide = ghostButton(uiContext, "使用说明", colorTextSub());
    guide.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            safeToast("命中后先发文本（若有），再发送绑定文件。开关控制启用，冷却单位：秒。");
        }
    });
    bottom.addView(guide);

    card.addView(space(dp(8)));
    card.addView(bottom);
}
