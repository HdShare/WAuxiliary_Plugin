import android.content.Context;
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
import android.view.View;
import android.widget.*;
import android.content.ContentValues;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

boolean getHookState() {
    try {
        de.robv.android.xposed.XposedBridge.class;
        return true;
    } catch (Throwable e) {
        return false;
    }
}

if (!getHookState()) return toast("请关闭LSPosed调用保护");

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

// ================= 配置键名常量 =================
String KEY_SECRET_ENABLE = "secret_enable";
String KEY_SECRET_WXIDS = "secret_wxids";
String KEY_LOG_ENABLE = "hb_log_enable";
String KEY_DEBUG_LOG_ENABLE = "hb_debug_log_enable"; 
String KEY_PASSWORD_ENABLE = "hb_pwd_enable"; 
String KEY_PASSWORD_VALUE = "hb_pwd_value";   

// ================= 全局变量 =================
List sCachedFriendList = null;
List sCachedGroupList = null;
List<Object> allHooks = new ArrayList<>();
boolean mLogEnabled = true;
boolean mDebugLogEnabled = false;

// 密友状态与安全控制
boolean isHiddenMode = true; 
long lastSecretMessageTime = 0; 
static List<String> sCachedSecretWxids = new ArrayList<>();
static String sCachedInClause = ""; 

static Map<String, String> sSqlCache = new ConcurrentHashMap<>();
static Map<Integer, WeakReference<Object>> sDbInstances = new ConcurrentHashMap<>();

// ================= 日志工具 =================
void logx(Object msg) {
    if (mLogEnabled) {
        log(msg);
    }
}

void logDebug(Object msg) {
    if (mDebugLogEnabled) {
        log("[SQL调试] " + msg);
    }
}

// 极速更新内存缓存
void updateSecretCache() {
    sCachedSecretWxids.clear();
    sCachedInClause = "";
    sSqlCache.clear(); 
    
    String secretStr = getString(KEY_SECRET_WXIDS, "");
    if (!TextUtils.isEmpty(secretStr)) {
        StringBuilder sb = new StringBuilder(" IN (");
        boolean hasItem = false;
        for (String s : secretStr.split("[,，]")) {
            String t = s.trim();
            if (!TextUtils.isEmpty(t)) {
                sCachedSecretWxids.add(t);
                if (hasItem) sb.append(",");
                sb.append("'").append(t).append("'");
                hasItem = true;
            }
        }
        sb.append(")");
        if (hasItem) {
            sCachedInClause = sb.toString();
        }
    }
}

// ================= 无感刷新核心机制 =================
void forceRefreshUI() {
    Iterator<Map.Entry<Integer, WeakReference<Object>>> it = sDbInstances.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<Integer, WeakReference<Object>> entry = it.next();
        Object db = entry.getValue().get();
        if (db != null) {
            try { XposedHelpers.callMethod(db, "execSQL", "UPDATE rconversation SET unReadCount = unReadCount WHERE 1=0"); } catch (Throwable e) {}
        } else {
            it.remove(); 
        }
    }
}

// ================= 动作执行器 (纯净静默版) =================
void doHideAction(Activity ctx) {
    isHiddenMode = true;
    sSqlCache.clear(); 
    forceRefreshUI();
    // 删除了高危的 toast 提示，实现真正的无痕退出
    if (ctx != null) ctx.finish(); 
}

void doShowAction(Activity ctx) {
    isHiddenMode = false;
    sSqlCache.clear();
    forceRefreshUI();
    // 删除了高危的 toast 提示，实现真正的无痕退出
    if (ctx != null) ctx.finish(); 
}

// ================= 入口函数 =================
boolean onClickSendBtn(String text) {
    final Activity ctx = getTopActivity();
    if (ctx == null) return false;

    if ("密友设置".equals(text)) {
        if (getBoolean(KEY_PASSWORD_ENABLE, false)) {
            showPasswordVerifyDialog(ctx, new Runnable() {
                public void run() { showSettingsUI(); }
            });
        } else {
            showSettingsUI();
        }
        return true;
    }
    
    if ("//hide".equals(text)) {
        doHideAction(ctx);
        return true;
    }
    
    if ("//show".equals(text)) {
        if (getBoolean(KEY_PASSWORD_ENABLE, false)) {
            showPasswordVerifyDialog(ctx, new Runnable() {
                public void run() { doShowAction(ctx); }
            });
        } else {
            doShowAction(ctx);
        }
        return true;
    }
    return false;
}

// ================= 密码验证对话框 =================
void showPasswordVerifyDialog(final Activity ctx, final Runnable onSuccess) {
    final EditText etPwd = new EditText(ctx);
    etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    etPwd.setHint("请输入安全验证密码");
    
    LinearLayout layout = new LinearLayout(ctx);
    layout.setPadding(60, 30, 60, 20);
    layout.addView(etPwd, new LinearLayout.LayoutParams(-1, -2));

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("🔒 安全访问受限")
        .setView(layout)
        .setPositiveButton("解锁", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String input = etPwd.getText().toString();
                String savedPwd = getString(KEY_PASSWORD_VALUE, "");
                if (input.equals(savedPwd)) {
                    if (onSuccess != null) onSuccess.run();
                } else {
                    toast("❌ 密码错误，拒绝访问！");
                }
            }
        })
        .setNegativeButton("取消", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

// ================= onLoad 生命周期 =================
void onLoad() {
    mLogEnabled = getBoolean(KEY_LOG_ENABLE, true);
    mDebugLogEnabled = getBoolean(KEY_DEBUG_LOG_ENABLE, false);
    
    logx(">> onLoad开始执行 [密友极致无痕 0卡顿 静默完全版]");
    updateSecretCache();              
    hookNotificationAndSound();       
    hookSecretFriendSQL();            
    hookDatabaseMethods();            
    logx(">> onLoad执行完成");
}

// ================= 模块一：OS级通知与系统声音静默 =================
void hookNotificationAndSound() {
    try {
        XposedBridge.hookAllMethods(android.app.NotificationManager.class, "notify", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (getBoolean(KEY_SECRET_ENABLE, false) && isHiddenMode) {
                    if (System.currentTimeMillis() - lastSecretMessageTime < 2000) {
                        param.setResult(null); 
                    }
                }
            }
        });
        
        try {
            Class<?> sysVibrator = XposedHelpers.findClass("android.os.SystemVibrator", hostContext.getClassLoader());
            XposedBridge.hookAllMethods(sysVibrator, "vibrate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (getBoolean(KEY_SECRET_ENABLE, false) && isHiddenMode && System.currentTimeMillis() - lastSecretMessageTime < 2000) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable ignored) {} 

        try {
            XposedBridge.hookAllMethods(android.media.SoundPool.class, "play", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (getBoolean(KEY_SECRET_ENABLE, false) && isHiddenMode && System.currentTimeMillis() - lastSecretMessageTime < 2000) {
                        param.setResult(0); 
                    }
                }
            });
        } catch (Throwable ignored) {}

    } catch (Throwable e) {
        logx("ERROR: 通知挂载失败: " + e.getMessage());
    }
}

// ================= 模块二：零对象分配极速 SQL 拦截引擎 =================
void hookSecretFriendSQL() {
    XC_MethodHook sqlHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (!getBoolean(KEY_SECRET_ENABLE, false) || !isHiddenMode || sCachedInClause.isEmpty()) return;

            int hash = System.identityHashCode(param.thisObject);
            if (!sDbInstances.containsKey(hash)) {
                sDbInstances.put(hash, new WeakReference<>(param.thisObject));
            }

            int sqlIndex = -1;
            String originalSql = null;
            if (param.args.length > 0 && param.args[0] instanceof String) {
                String s = (String) param.args[0];
                if (s.length() > 6 && (s.charAt(0)=='s' || s.charAt(0)=='S') && (s.charAt(1)=='e' || s.charAt(1)=='E')) {
                    sqlIndex = 0; originalSql = s;
                }
            } else if (param.args.length > 1 && param.args[1] instanceof String) {
                String s = (String) param.args[1];
                if (s.length() > 6 && (s.charAt(0)=='s' || s.charAt(0)=='S') && (s.charAt(1)=='e' || s.charAt(1)=='E')) {
                    sqlIndex = 1; originalSql = s;
                }
            }
            if (sqlIndex == -1 || originalSql == null) return;

            // 光速前置屏障，消灭垃圾分配
            if (!originalSql.contains("rconversation") && 
                !originalSql.contains("RConversation") && 
                !originalSql.contains("rcontact") && 
                !originalSql.contains("RContact") && 
                !originalSql.contains("fts5") && 
                !originalSql.contains("FTS5")) {
                return; 
            }

            String cachedSql = sSqlCache.get(originalSql);
            if (cachedSql != null) {
                if (cachedSql.isEmpty()) return; 
                param.args[sqlIndex] = cachedSql;
                return;
            }

            String lowerSql = originalSql.toLowerCase();

            if (lowerSql.contains("not in (")) {
                putCacheSafely(originalSql, "");
                return;
            }

            boolean isFts = lowerSql.contains("fts5");
            if (!isFts) {
                if (lowerSql.contains(" join ") || lowerSql.contains(" union ") || (lowerSql.contains("select ") && lowerSql.lastIndexOf("select ") > 5)) {
                    putCacheSafely(originalSql, ""); 
                    return;
                }
            }

            String filter = "";

            if (lowerSql.contains("from fts5metacontact")) {
                filter = "aux_index NOT" + sCachedInClause;
            } else if (lowerSql.contains("from fts5message")) {
                filter = "talker NOT" + sCachedInClause;
            } else if (lowerSql.contains("from rconversation")) {
                if (!lowerSql.contains("username =") && !lowerSql.contains("username=")) {
                    filter = "username NOT" + sCachedInClause;
                }
            } else if (lowerSql.contains("from rcontact")) {
                if (!lowerSql.contains("username =") && !lowerSql.contains("username=")) {
                    filter = "username NOT" + sCachedInClause;
                }
            }

            if (!filter.isEmpty()) {
                int whereIdx = lowerSql.indexOf(" where ");
                String newSql;
                if (whereIdx != -1) {
                    newSql = originalSql.substring(0, whereIdx + 7) + "(" + filter + ") AND " + originalSql.substring(whereIdx + 7);
                } else {
                    int groupIdx = lowerSql.indexOf(" group by ");
                    int orderIdx = lowerSql.indexOf(" order by ");
                    int limitIdx = lowerSql.indexOf(" limit ");
                    
                    int insertIdx = -1;
                    if (groupIdx != -1) insertIdx = groupIdx;
                    else if (orderIdx != -1 && (insertIdx == -1 || orderIdx < insertIdx)) insertIdx = orderIdx;
                    else if (limitIdx != -1 && (insertIdx == -1 || limitIdx < insertIdx)) insertIdx = limitIdx;

                    if (insertIdx != -1) {
                        newSql = originalSql.substring(0, insertIdx) + " WHERE (" + filter + ") " + originalSql.substring(insertIdx);
                    } else {
                        newSql = originalSql + " WHERE (" + filter + ")";
                    }
                }
                
                putCacheSafely(originalSql, newSql);
                param.args[sqlIndex] = newSql;
                
                if (mDebugLogEnabled) {
                    logDebug("拦截前: " + originalSql + "\n拦截后: " + newSql);
                }
            } else {
                putCacheSafely(originalSql, "");
            }
        }
    };

    try {
        try {
            Class<?> wcdbClass = XposedHelpers.findClass("com.tencent.wcdb.database.SQLiteDatabase", hostContext.getClassLoader());
            XposedBridge.hookAllMethods(wcdbClass, "rawQuery", sqlHook);
            XposedBridge.hookAllMethods(wcdbClass, "rawQueryWithFactory", sqlHook);
        } catch (Throwable ignored) {}

        try {
            XposedBridge.hookAllMethods(android.database.sqlite.SQLiteDatabase.class, "rawQuery", sqlHook);
            XposedBridge.hookAllMethods(android.database.sqlite.SQLiteDatabase.class, "rawQueryWithFactory", sqlHook);
        } catch (Throwable ignored) {}
        
    } catch (Exception e) {}
}

void putCacheSafely(String key, String value) {
    if (sSqlCache.size() > 500) {
        sSqlCache.clear();
    }
    sSqlCache.put(key, value);
}

// ================= 模块三：置顶守护与免打扰监听合并引擎 =================
void hookDatabaseMethods() {
    try {
        Class<?> wcdbClass = XposedHelpers.findClass("com.tencent.wcdb.database.SQLiteDatabase", hostContext.getClassLoader());
        
        XC_MethodHook writeHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length == 0) return;
                String tableName = (String) param.args[0];
                if (tableName == null) return;
                
                ContentValues values = null;
                for (Object arg : param.args) {
                    if (arg instanceof ContentValues) {
                        values = (ContentValues) arg;
                        break;
                    }
                }
                if (values == null) return;

                if ("message".equals(tableName)) {
                    String talker = values.getAsString("talker");
                    if (getBoolean(KEY_SECRET_ENABLE, false) && isHiddenMode && !TextUtils.isEmpty(talker)) {
                        if (!sCachedSecretWxids.isEmpty() && sCachedSecretWxids.contains(talker.trim())) {
                            lastSecretMessageTime = System.currentTimeMillis(); 
                        }
                    }
                    return;
                }

                if ("rconversation".equals(tableName) && getBoolean(KEY_SECRET_ENABLE, false)) {
                    String username = values.getAsString("username");
                    if (TextUtils.isEmpty(username)) {
                        for (Object arg : param.args) {
                            if (arg instanceof String[]) {
                                String[] arr = (String[]) arg;
                                if (arr.length > 0) {
                                    username = arr[0];
                                    break;
                                }
                            }
                        }
                    }

                    if (!TextUtils.isEmpty(username) && !sCachedSecretWxids.isEmpty() && sCachedSecretWxids.contains(username)) {
                        Long currentFlag = values.getAsLong("flag");
                        String backupKey = "backup_flag_" + username;
                        
                        if (!isHiddenMode) {
                            if (currentFlag != null && currentFlag > 0) {
                                putString(backupKey, String.valueOf(currentFlag));
                            }
                        } else {
                            String backupStr = getString(backupKey, "");
                            if (!TextUtils.isEmpty(backupStr)) {
                                try {
                                    values.put("flag", Long.parseLong(backupStr));
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
            }
        };

        XposedBridge.hookAllMethods(wcdbClass, "insert", writeHook);
        XposedBridge.hookAllMethods(wcdbClass, "insertWithOnConflict", writeHook);
        XposedBridge.hookAllMethods(wcdbClass, "replace", writeHook);
        XposedBridge.hookAllMethods(wcdbClass, "update", writeHook);
        XposedBridge.hookAllMethods(wcdbClass, "updateWithOnConflict", writeHook);

    } catch (Exception e) { }
}

void onUnload() {
    for (Object h : allHooks) {
        try {
            if (h != null) {
                XposedBridge.unhookMethod((XC_MethodHook.Unhook) h);
            }
        } catch (Exception e) {}
    }
    allHooks.clear();
    sCachedFriendList = null;
    sCachedGroupList = null;
    sCachedSecretWxids.clear();
    sCachedInClause = "";
    sSqlCache.clear();
    sDbInstances.clear();
}

String getDisplayName(String wxid) {
    try {
        String name = getFriendName(wxid);
        return TextUtils.isEmpty(name) ? wxid : name;
    } catch (Exception e) {
        return wxid;
    }
}

// ================= UI 构建逻辑 =================

void showSetPasswordDialog(final Activity ctx) {
    final EditText etPwd = new EditText(ctx);
    etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    etPwd.setHint("请输入新的安全验证密码");
    
    String oldPwd = getString(KEY_PASSWORD_VALUE, "");
    if (!TextUtils.isEmpty(oldPwd)) {
        etPwd.setText(oldPwd);
    }
    
    LinearLayout layout = new LinearLayout(ctx);
    layout.setPadding(60, 30, 60, 20);
    layout.addView(etPwd, new LinearLayout.LayoutParams(-1, -2));

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("设置安全验证密码")
        .setView(layout)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String input = etPwd.getText().toString().trim();
                if (TextUtils.isEmpty(input)) {
                    toast("密码不能为空，未保存！");
                    return;
                }
                putString(KEY_PASSWORD_VALUE, input);
                // 仅保留用户主动设置密码时的提示反馈
                toast("✅ 密码设置成功！");
            }
        })
        .setNegativeButton("取消", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

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

    LinearLayout cardSecret = createCard(ctx);
    root.addView(cardSecret);
    addSectionTitle(ctx, cardSecret, "👻 密友安全设置");
    final Switch swSecretEnable = addSwitch(ctx, cardSecret, "开启密友保护功能", getBoolean(KEY_SECRET_ENABLE, false));
    
    TextView tvSecretTip = new TextView(ctx);
    tvSecretTip.setText("隐身下主列表完全隐藏、全局防搜索、自动防查岗\n聊天内发送 //hide 隐藏，发送 //show 恢复\n\nPS: 出于账号防封控的安全考虑，朋友圈隐藏需您手动开启微信原生的“不看他朋友圈”开关。");
    tvSecretTip.setTextSize(12);
    tvSecretTip.setTextColor(Color.parseColor("#F44336"));
    tvSecretTip.setPadding(0, 0, 0, 10);
    cardSecret.addView(tvSecretTip);
    
    LinearLayout secretBtnLayout = new LinearLayout(ctx);
    secretBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
    cardSecret.addView(secretBtnLayout);
    Button btnSecret = createInlineButton(ctx, "管理密友名单", "#9C27B0");
    secretBtnLayout.addView(btnSecret, new LinearLayout.LayoutParams(0, -2, 1));
    btnSecret.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showContactSourceDialog(ctx, "密友", KEY_SECRET_WXIDS);
        }
    });

    LinearLayout cardPwd = createCard(ctx);
    root.addView(cardPwd);
    addSectionTitle(ctx, cardPwd, "🔒 隐私锁设置");
    final Switch swPwdEnable = addSwitch(ctx, cardPwd, "启用密码锁 (拦截设置页与解除隐身)", getBoolean(KEY_PASSWORD_ENABLE, false));
    
    LinearLayout pwdBtnLayout = new LinearLayout(ctx);
    pwdBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
    cardPwd.addView(pwdBtnLayout);
    
    Button btnSetPwd = createInlineButton(ctx, "设置 / 修改密码", "#FF9800");
    pwdBtnLayout.addView(btnSetPwd, new LinearLayout.LayoutParams(0, -2, 1));
    btnSetPwd.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showSetPasswordDialog(ctx);
        }
    });

    LinearLayout cardSystem = createCard(ctx);
    root.addView(cardSystem);
    addSectionTitle(ctx, cardSystem, "⚙️ 系统调试设置");
    final Switch swLogEnable = addSwitch(ctx, cardSystem, "开启基础运行日志输出", getBoolean(KEY_LOG_ENABLE, true));
    
    final Switch swDebugEnable = addSwitch(ctx, cardSystem, "开启底层 SQL 调试日志 (排错用)", getBoolean(KEY_DEBUG_LOG_ENABLE, false));
    TextView tvDebugTip = new TextView(ctx);
    tvDebugTip.setText("开启后会在日志中输出所有被篡改的数据库查询语句。因高频输出会造成轻微卡顿，仅建议调试排错时开启。");
    tvDebugTip.setTextSize(11);
    tvDebugTip.setTextColor(Color.GRAY);
    tvDebugTip.setPadding(0, 0, 0, 5);
    cardSystem.addView(tvDebugTip);

    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("微信密友设置")
        .setView(scrollView)
        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                try {
                    putBoolean(KEY_SECRET_ENABLE, swSecretEnable.isChecked());
                    putBoolean(KEY_LOG_ENABLE, swLogEnable.isChecked());
                    putBoolean(KEY_DEBUG_LOG_ENABLE, swDebugEnable.isChecked());
                    
                    putBoolean(KEY_PASSWORD_ENABLE, swPwdEnable.isChecked());
                    
                    mLogEnabled = swLogEnable.isChecked();
                    mDebugLogEnabled = swDebugEnable.isChecked();
                    
                    updateSecretCache();
                    forceRefreshUI(); 
                    // 这里去掉了保存成功的toast弹窗，实现无痕静默保存
                } catch (Exception e) {
                    toast("保存失败:" + e);
                }
            }
        })
        .setNegativeButton("关闭", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

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

Button createInlineButton(Activity ctx, String text, String colorHex) {
    Button btn = new Button(ctx);
    btn.setText(text);
    btn.setTextColor(Color.WHITE);
    btn.setTextSize(12);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor(colorHex));
    gd.setCornerRadius(15);
    btn.setBackground(gd);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(5, 10, 5, 10);
    btn.setLayoutParams(lp);
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
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#FF5722"));
        }
    } catch (Exception e) {}
}

void showContactSourceDialog(final Activity ctx, final String title, final String saveKey) {
    String[] items = { "从好友列表选择", "从群聊列表选择", "手动输入微信ID", "查看当前名单" };
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle("选择添加方式")
        .setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) loadAndSelect(ctx, title, saveKey, true);
                else if (which == 1) loadAndSelect(ctx, title, saveKey, false);
                else if (which == 2) showManualInput(ctx, title, saveKey);
                else if (which == 3) showCurrentList(ctx, title, saveKey);
            }
        }).create();
    setupUnifiedDialog(d);
    d.show();
}

void showManualInput(final Activity ctx, final String title, final String saveKey) {
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 20, 40, 20);
    TextView tip = new TextView(ctx);
    tip.setText("请输入微信ID，多个用逗号分隔");
    tip.setTextSize(12);
    tip.setTextColor(Color.GRAY);
    layout.addView(tip);
    final EditText etWxid = new EditText(ctx);
    etWxid.setHint("例如: wxid_abc123, wxid_def456");
    etWxid.setInputType(InputType.TYPE_CLASS_TEXT);
    layout.addView(etWxid);
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title + " - 手动输入")
        .setView(layout)
        .setPositiveButton("添加", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String input = etWxid.getText().toString().trim();
                if (TextUtils.isEmpty(input)) {
                    toast("请输入微信ID");
                    return;
                }
                String existStr = getString(saveKey, "");
                Set<String> existSet = new HashSet<>();
                if (!TextUtils.isEmpty(existStr)) {
                    for (String s : existStr.split(",")) {
                        existSet.add(s.trim());
                    }
                }
                String[] newIds = input.split("[,，]");
                int addCount = 0;
                for (String id : newIds) {
                    String trimId = id.trim();
                    if (!TextUtils.isEmpty(trimId) && !existSet.contains(trimId)) {
                        existSet.add(trimId);
                        addCount++;
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (String s : existSet) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(s);
                }
                putString(saveKey, sb.toString());
                if (KEY_SECRET_WXIDS.equals(saveKey)) {
                    updateSecretCache();
                    forceRefreshUI();
                }
                toast("成功添加 " + addCount + " 个ID");
            }
        })
        .setNegativeButton("取消", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
}

void showCurrentList(final Activity ctx, final String title, final String saveKey) {
    String listStr = getString(saveKey, "");
    if (TextUtils.isEmpty(listStr)) {
        toast("当前名单为空");
        return;
    }
    final String[] ids = listStr.split(",");
    final List<String> displayList = new ArrayList<>();
    final List<String> idList = new ArrayList<>();
    for (String id : ids) {
        String trimId = id.trim();
        if (!TextUtils.isEmpty(trimId)) {
            String displayName = getDisplayName(trimId);
            displayList.add(displayName + "\n(" + trimId + ")");
            idList.add(trimId);
        }
    }
    if (displayList.isEmpty()) {
        toast("当前名单为空");
        return;
    }
    ScrollView sv = new ScrollView(ctx);
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(20, 20, 20, 20);
    sv.addView(layout);
    TextView tvCount = new TextView(ctx);
    tvCount.setText("共 " + displayList.size() + " 个");
    tvCount.setTextSize(14);
    tvCount.setTextColor(Color.GRAY);
    tvCount.setPadding(0, 0, 0, 10);
    layout.addView(tvCount);
    final ListView lv = new ListView(ctx);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 800));
    ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, displayList);
    lv.setAdapter(adapter);
    layout.addView(lv);
    lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
            new AlertDialog.Builder(ctx)
                .setTitle("删除确认")
                .setMessage("确定要删除 " + displayList.get(position) + " 吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        idList.remove(position);
                        StringBuilder sb = new StringBuilder();
                        for (String s : idList) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(s);
                        }
                        putString(saveKey, sb.toString());
                        if (KEY_SECRET_WXIDS.equals(saveKey)) {
                            updateSecretCache();
                            forceRefreshUI();
                        }
                        toast("已删除");
                        showCurrentList(ctx, title, saveKey);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    });
    AlertDialog d = new AlertDialog.Builder(ctx)
        .setTitle(title + " - 当前名单")
        .setView(sv)
        .setPositiveButton("清空全部", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                new AlertDialog.Builder(ctx)
                    .setTitle("清空确认")
                    .setMessage("确定要清空所有名单吗？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            putString(saveKey, "");
                            if (KEY_SECRET_WXIDS.equals(saveKey)) {
                                updateSecretCache();
                                forceRefreshUI();
                            }
                            toast("已清空");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        })
        .setNegativeButton("关闭", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
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
                        for (int i = 0; i < sCachedFriendList.size(); i++) {
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
                        for (int i = 0; i < sCachedGroupList.size(); i++) {
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
            } catch (Exception e) {} finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        try {
                            if (loading.isShowing()) loading.dismiss();
                        } catch (Exception e) {}
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
    etSearch.setHint("搜索...");
    GradientDrawable searchBg = new GradientDrawable();
    searchBg.setColor(Color.parseColor("#F5F5F5"));
    searchBg.setCornerRadius(15);
    etSearch.setBackground(searchBg);
    etSearch.setPadding(20, 20, 20, 20);
    layout.addView(etSearch);
    final TextView tvCount = new TextView(ctx);
    tvCount.setTextSize(12);
    tvCount.setTextColor(Color.GRAY);
    tvCount.setPadding(10, 10, 10, 10);
    layout.addView(tvCount);
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
            for (int i = 0; i < names.size(); i++) {
                if (kw.isEmpty() || names.get(i).toLowerCase().contains(kw)) {
                    dNames.add(names.get(i));
                    dIds.add(ids.get(i));
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_multiple_choice, dNames);
            lv.setAdapter(adapter);
            for (int i = 0; i < dIds.size(); i++) {
                if (tempSet.contains(dIds.get(i))) {
                    lv.setItemChecked(i, true);
                }
            }
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
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
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
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
                if (KEY_SECRET_WXIDS.equals(saveKey)) {
                    updateSecretCache();
                    forceRefreshUI();
                }
                toast("名单更新: " + tempSet.size() + "个");
            }
        })
        .setNegativeButton("取消", null)
        .setNeutralButton("全选/反选", null)
        .create();
    setupUnifiedDialog(d);
    d.show();
    styleDialogButtons(d);
    d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            if (tempSet.size() == dIds.size()) {
                tempSet.clear();
                for (int i = 0; i < lv.getCount(); i++) {
                    lv.setItemChecked(i, false);
                }
            } else {
                tempSet.clear();
                tempSet.addAll(dIds);
                for (int i = 0; i < lv.getCount(); i++) {
                    lv.setItemChecked(i, true);
                }
            }
            tvCount.setText("已选择: " + tempSet.size() + " 个 | 显示: " + dNames.size() + " 个");
        }
    });
}
