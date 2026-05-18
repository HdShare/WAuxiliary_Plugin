import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.text.TextWatcher;
import android.text.Editable;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import me.hd.wauxv.data.bean.info.FriendInfo;

// ================= 全局状态与拦截器 =================
boolean isRunning = false;
List pendingList = new ArrayList();
List deadList = new ArrayList();
Set filterWxids = new HashSet();
Set filterLabelNames = new HashSet();

int checkedTotalCount = 0; 
int dispatchedBatchCount = 0;
int runtimeMinDelay = 2;
int runtimeMaxDelay = 4;
int runtimeTimeoutMs = 10000;
int runtimeMaxInflight = 1;
int runtimeMaxRetry = 100;
int runtimeTagDeleteDelaySec = 1;
int runtimeAutoDeleteDelaySec = 3;

String currentStatusText = "状态: 引擎就绪，等待检测";
String consoleLogBuffer = "SYSTEM READY\n";
String lastLabelError = "";
Object labelOpLock = new Object();
Object probeLock = new Object();
int inFlightProbeCount = 0;
java.util.Map probeWxidMap = new java.util.IdentityHashMap();
java.util.Map probeNameMap = new java.util.IdentityHashMap();
java.util.Map probeIndexMap = new java.util.IdentityHashMap();
java.util.Map probeRetryMap = new java.util.IdentityHashMap();
Object sceneEndHooks = null;
int totalTargetCount = 0;
String notificationChannelId = "jay_zombie_progress";
int notificationId = 10001; 
PowerManager.WakeLock zombieWakeLock = null;
Class remittanceSceneClass = null;
Class netSceneQueueClass = null;
Constructor remittanceSceneCtor = null;
Method queueDoSceneMethod = null;
Object netSceneQueueObj = null;
Class deleteContactServiceClass = null;
Method deleteContactMethod = null;
Class addContactLabelSceneClass = null;
Constructor addContactLabelSceneCtor = null;

Handler uiHandler = new Handler(Looper.getMainLooper());
HandlerThread dispatchThread;
Handler dispatchHandler;
Random random = new Random();

// ================= 主题工具 =================
GradientDrawable getRoundRect(String bgColor, int radiusDp) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor(bgColor));
    gd.setCornerRadius(radiusDp * 3); 
    return gd;
}

int dp(Activity a, int v) {
    return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
}

GradientDrawable roundRect(int color, int radiusPx) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(radiusPx);
    return g;
}

TextView makeUiBtn(Activity ctx, String text, boolean primary) {
    TextView v = new TextView(ctx);
    v.setText(text);
    v.setTextSize(14f);
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(ctx, primary ? 18 : 14), dp(ctx, 8), dp(ctx, primary ? 18 : 14), dp(ctx, 8));
    if (primary) {
        v.setTextColor(Color.WHITE);
        v.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
        bg.setCornerRadius(dp(ctx, 999));
        v.setBackground(bg);
    } else {
        v.setTextColor(Color.parseColor("#334155"));
        GradientDrawable bg = roundRect(Color.parseColor("#EFF3F8"), dp(ctx, 999));
        bg.setStroke(dp(ctx, 1), Color.parseColor("#DEE7F2"));
        v.setBackground(bg);
    }
    return v;
}

void addUiDivider(Activity ctx, ViewGroup parent) {
    View v = new View(ctx);
    v.setBackgroundColor(Color.parseColor("#E8EEF5"));
    parent.addView(v, new LinearLayout.LayoutParams(-1, 1));
}

GradientDrawable makeUiCardBg(Activity ctx, int radiusDp, String strokeColor) {
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.WHITE, Color.parseColor("#F8FAFC")});
    bg.setCornerRadius(dp(ctx, radiusDp));
    bg.setStroke(dp(ctx, 1), Color.parseColor(strokeColor));
    return bg;
}

void finishUiDialogLayout(final Dialog dialog, FrameLayout mask, View card) {
    mask.addView(card);
    mask.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
    card.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){} });
    dialog.setContentView(mask);
    Window w = dialog.getWindow();
    if (w != null) {
        w.setLayout(-1, -1);
        w.setGravity(Gravity.CENTER);
        w.setDimAmount(0.25f);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}

void showUiDialogAnimated(Dialog dialog, View card, Activity ctx, int dyDp, int duration) {
    dialog.show();
    card.setAlpha(0f);
    card.setTranslationY(dp(ctx, dyDp));
    card.animate().alpha(1f).translationY(0).setDuration(duration).start();
}

String safeToStr(Object o) {
    return o == null ? "" : String.valueOf(o);
}

List strings(Object[] arr) {
    List out = new ArrayList();
    for (int i = 0; i < arr.length; i++) out.add(arr[i]);
    return out;
}

boolean isIntType(Class t) {
    return t == int.class || t == Integer.class;
}

boolean isSceneBaseClass(Class c) {
    if (c == null) return false;
    try {
        c.getMethod("getType", new Class[0]);
        return true;
    } catch (Throwable ignore) {}
    return false;
}

Class getSceneBaseClass(Class sceneClass) {
    Class c = sceneClass;
    while (c != null && c != Object.class) {
        if (isSceneBaseClass(c)) return c;
        c = c.getSuperclass();
    }
    return sceneClass;
}

Class resolveRemittanceSceneClass() {
    if (remittanceSceneClass != null) return remittanceSceneClass;
    try {
        List members = findMemberList(strings(new Object[]{
            "Micromsg.NetSceneTenpayRemittanceGen",
            "receiver_openid",
            "placeorder_attach"
        }));
        for (int i = 0; i < members.size(); i++) {
            Member member = (Member) members.get(i);
            Class c = member.getDeclaringClass();
            Constructor[] ctors = c.getConstructors();
            for (int j = 0; j < ctors.length; j++) {
                Class[] pts = ctors[j].getParameterTypes();
                if (pts.length >= 29 && pts[0] == double.class) {
                    remittanceSceneClass = c;
                    remittanceSceneCtor = ctors[j];
                    return remittanceSceneClass;
                }
            }
        }
    } catch (Throwable t) {
        addLog("DexKit 定位转账场景失败: " + t.getMessage());
    }
    try {
        remittanceSceneClass = hostContext.getClassLoader().loadClass("com.tencent.mm.plugin.remittance.model.q0");
        return remittanceSceneClass;
    } catch (Throwable ignore) {}
    return null;
}

Constructor resolveRemittanceCtor() {
    if (remittanceSceneCtor != null) return remittanceSceneCtor;
    Class c = resolveRemittanceSceneClass();
    if (c == null) return null;
    Constructor[] ctors = c.getConstructors();
    Constructor best = null;
    for (int i = 0; i < ctors.length; i++) {
        Class[] pts = ctors[i].getParameterTypes();
        if (pts.length >= 29 && pts[0] == double.class) {
            if (best == null || pts.length > best.getParameterTypes().length) best = ctors[i];
        }
    }
    remittanceSceneCtor = best;
    return best;
}

Object defaultArg(Class t) {
    if (t == double.class || t == Double.class) return new Double(0.0d);
    if (t == float.class || t == Float.class) return new Float(0.0f);
    if (t == long.class || t == Long.class) return new Long(0L);
    if (t == int.class || t == Integer.class) return new Integer(0);
    if (t == short.class || t == Short.class) return new Short((short) 0);
    if (t == byte.class || t == Byte.class) return new Byte((byte) 0);
    if (t == boolean.class || t == Boolean.class) return Boolean.FALSE;
    if (t == String.class) return "";
    return null;
}

void setCtorArg(Object[] args, Class[] pts, int idx, Object value) {
    if (idx < 0 || idx >= args.length) return;
    try {
        if (pts[idx] == String.class) args[idx] = String.valueOf(value);
        else if (isIntType(pts[idx])) args[idx] = new Integer(Integer.parseInt(String.valueOf(value)));
        else if (pts[idx] == double.class || pts[idx] == Double.class) args[idx] = new Double(Double.parseDouble(String.valueOf(value)));
        else if (pts[idx] == boolean.class || pts[idx] == Boolean.class) args[idx] = Boolean.valueOf(String.valueOf(value));
        else args[idx] = value;
    } catch (Throwable ignore) {}
}

Object buildRemittanceScene(String wxid) throws Throwable {
    Constructor ctor = resolveRemittanceCtor();
    if (ctor == null) throw new RuntimeException("未定位到收款检测场景构造函数");
    Class[] pts = ctor.getParameterTypes();
    Object[] args = new Object[pts.length];
    for (int i = 0; i < pts.length; i++) args[i] = defaultArg(pts[i]);

    setCtorArg(args, pts, 0, new Double(1.0d));
    setCtorArg(args, pts, 1, "1");
    setCtorArg(args, pts, 2, wxid);
    setCtorArg(args, pts, 4, new Integer(31));
    setCtorArg(args, pts, 5, new Integer(2));
    setCtorArg(args, pts, 13, new Integer(11));
    setCtorArg(args, pts, 23, String.valueOf(System.currentTimeMillis()));
    setCtorArg(args, pts, 24, new Integer(0));
    setCtorArg(args, pts, 26, new Integer(0));
    return ctor.newInstance(args);
}

Class resolveNetSceneQueueClass() {
    if (netSceneQueueClass != null) return netSceneQueueClass;
    try {
        List classes = findClassList(strings(new Object[]{
            "MicroMsg.NetSceneQueue",
            "doScene failed clearRunningQueue"
        }));
        if (classes != null && classes.size() > 0) {
            netSceneQueueClass = (Class) classes.get(0);
            return netSceneQueueClass;
        }
    } catch (Throwable t) {
        addLog("DexKit 定位网络队列失败: " + t.getMessage());
    }
    try {
        netSceneQueueClass = hostContext.getClassLoader().loadClass("com.tencent.mm.modelbase.r1");
        return netSceneQueueClass;
    } catch (Throwable ignore) {}
    return null;
}

Object invokeStaticNoArg(Method m) throws Throwable {
    m.setAccessible(true);
    return m.invoke(null, new Object[0]);
}

Object findQueueFromObject(Object obj, Class queueClass) {
    if (obj == null || queueClass == null) return null;
    if (queueClass.isInstance(obj)) return obj;
    try {
        Method[] methods = obj.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (m.getParameterTypes().length == 0 && queueClass.isAssignableFrom(m.getReturnType())) {
                try { return m.invoke(obj, new Object[0]); } catch (Throwable ignore) {}
            }
        }
    } catch (Throwable ignore) {}
    try {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            f.setAccessible(true);
            Object value = f.get(obj);
            if (queueClass.isInstance(value)) return value;
        }
    } catch (Throwable ignore) {}
    return null;
}

Object resolveNetSceneQueueObject() {
    Class queueClass = resolveNetSceneQueueClass();
    if (netSceneQueueObj != null && queueClass != null && queueClass.isInstance(netSceneQueueObj)) return netSceneQueueObj;
    try {
        List members = findMemberList(strings(new Object[]{"Kernel not initialized by MMApplication!"}));
        for (int i = 0; i < members.size(); i++) {
            Member member = (Member) members.get(i);
            if (!(member instanceof Method)) continue;
            Method m = (Method) member;
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
            Object core = invokeStaticNoArg(m);
            Object queue = findQueueFromObject(core, queueClass);
            if (queue != null) {
                netSceneQueueObj = queue;
                return netSceneQueueObj;
            }
        }
    } catch (Throwable t) {
        addLog("DexKit 定位 MMKernel 失败: " + t.getMessage());
    }

    String[][] fallbacks = new String[][]{
        {"tk0.j1", "i"},
        {"u70.k1", "n"}
    };
    for (int i = 0; i < fallbacks.length; i++) {
        try {
            Class c = hostContext.getClassLoader().loadClass(fallbacks[i][0]);
            Object core = XposedHelpers.callStaticMethod(c, fallbacks[i][1]);
            Object queue = findQueueFromObject(core, queueClass);
            if (queue == null) {
                try { queue = XposedHelpers.getObjectField(core, "b"); } catch (Throwable ignore) {}
            }
            if (queue != null) {
                netSceneQueueObj = queue;
                return netSceneQueueObj;
            }
        } catch (Throwable ignore) {}
    }
    return null;
}

Method resolveQueueDoSceneMethod(Object queue, Object scene) {
    if (queueDoSceneMethod != null) return queueDoSceneMethod;
    if (queue == null || scene == null) return null;
    Class sceneBase = getSceneBaseClass(scene.getClass());
    Method[] methods = queue.getClass().getMethods();
    for (int i = 0; i < methods.length; i++) {
        Method m = methods[i];
        Class[] pts = m.getParameterTypes();
        if (pts.length == 2 && pts[1] == int.class && m.getReturnType() == boolean.class && pts[0].isAssignableFrom(scene.getClass())) {
            queueDoSceneMethod = m;
            return m;
        }
        if (pts.length == 2 && pts[1] == int.class && m.getReturnType() == boolean.class && pts[0].isAssignableFrom(sceneBase)) {
            queueDoSceneMethod = m;
            return m;
        }
    }
    for (int i = 0; i < methods.length; i++) {
        Method m = methods[i];
        Class[] pts = m.getParameterTypes();
        if (pts.length == 2 && pts[1] == int.class && (pts[0].isAssignableFrom(scene.getClass()) || pts[0].isAssignableFrom(sceneBase))) {
            queueDoSceneMethod = m;
            return m;
        }
    }
    return null;
}

boolean dispatchRemittanceScene(Object scene) {
    try {
        Object queue = resolveNetSceneQueueObject();
        Method method = resolveQueueDoSceneMethod(queue, scene);
        if (queue == null || method == null) return false;
        method.setAccessible(true);
        method.invoke(queue, new Object[]{scene, new Integer(0)});
        return true;
    } catch (Throwable t) {
        addLog("发包失败: " + t.getMessage());
    }
    return false;
}

Method resolveOnGYNetEndMethod(Class sceneClass) {
    if (sceneClass == null) return null;
    Method[] methods = sceneClass.getDeclaredMethods();
    for (int i = 0; i < methods.length; i++) {
        Method m = methods[i];
        Class[] pts = m.getParameterTypes();
        if ("onGYNetEnd".equals(m.getName()) && pts.length == 3 && pts[0] == int.class && pts[1] == String.class) {
            return m;
        }
    }
    return null;
}

Class resolveDeleteContactServiceClass() {
    if (deleteContactServiceClass != null) return deleteContactServiceClass;
    try {
        Class c = resolveDeleteContactServiceClassByVersion();
        if (c != null) return c;
    } catch (Throwable ignore) {}
    try {
        List members = findMemberList(strings(new Object[]{
            "MicroMsg.DeleteContactService",
            "delete contact %s"
        }));
        for (int i = 0; i < members.size(); i++) {
            Member member = (Member) members.get(i);
            Class c = member.getDeclaringClass();
            Method m = findDeleteMethodInClass(c);
            if (isUsableDeleteServiceClass(c, m)) {
                deleteContactServiceClass = c;
                deleteContactMethod = m;
                addLog("删除服务命中: " + c.getName() + "." + m.getName());
                return deleteContactServiceClass;
            }
        }
    } catch (Throwable t) {
        addLog("DexKit 精确定位删除好友服务失败: " + t.getMessage());
    }
    try {
        List classes = findClassList(strings(new Object[]{"MicroMsg.DeleteContactService"}));
        for (int i = 0; i < classes.size(); i++) {
            Class c = (Class) classes.get(i);
            Method m = findDeleteMethodInClass(c);
            if (m == null) m = findKnownDeleteMethodByName(c);
            if (isUsableDeleteServiceClass(c, m)) {
                deleteContactServiceClass = c;
                deleteContactMethod = m;
                addLog("删除服务类锚点命中: " + c.getName() + "." + m.getName());
                return deleteContactServiceClass;
            }
        }
    } catch (Throwable t) {
        addLog("DexKit 类锚点定位删除好友服务失败: " + t.getMessage());
    }
    try {
        String[] fallbackNames = new String[]{
            "ni1.n",  // WeChat 8.0.63
            "fo1.n",  // WeChat 8.0.71
            "hl1.n",  // WeChat 8.0.69
            "gj1.n",  // WeChat 8.0.65
            "ng1.n",  // WeChat 8.0.61
            "pa1.o"   // WeChat 8.0.54
        };
        for (int i = 0; i < fallbackNames.length; i++) {
            try {
                Class c = hostContext.getClassLoader().loadClass(fallbackNames[i]);
                Method m = findDeleteMethodInClass(c);
                if (m == null) m = findKnownDeleteMethodByName(c);
                if (!isUsableDeleteServiceClass(c, m)) {
                    addLog("删除服务兜底不匹配: " + fallbackNames[i] + "，ctor=" + hasNoArgConstructor(c) + "，method=" + (m == null ? "null" : m.getName()));
                    continue;
                }
                deleteContactServiceClass = c;
                deleteContactMethod = m;
                addLog("删除服务兜底命中: " + c.getName() + "." + m.getName());
                return deleteContactServiceClass;
            } catch (Throwable t) {
                addLog("删除服务兜底加载失败: " + fallbackNames[i] + "，" + t.getMessage());
            }
        }
    } catch (Throwable t2) {
        addLog("删除服务兜底失败: " + t2.getMessage());
    }
    return null;
}

Class resolveDeleteContactServiceClassByVersion() {
    String ver = safeToStr(hostVerName);
    String[] versionHints = null;
    if (ver.indexOf("8.0.63") >= 0) {
        versionHints = new String[]{"ni1.n"};
    } else if (ver.indexOf("8.0.61") >= 0) {
        versionHints = new String[]{"ng1.n"};
    } else if (ver.indexOf("8.0.65") >= 0) {
        versionHints = new String[]{"gj1.n"};
    } else if (ver.indexOf("8.0.69") >= 0) {
        versionHints = new String[]{"hl1.n"};
    } else if (ver.indexOf("8.0.71") >= 0) {
        versionHints = new String[]{"fo1.n"};
    } else if (ver.indexOf("8.0.54") >= 0) {
        versionHints = new String[]{"pa1.o"};
    }
    if (versionHints == null) return null;
    for (int i = 0; i < versionHints.length; i++) {
        try {
            Class c = hostContext.getClassLoader().loadClass(versionHints[i]);
            Method m = findDeleteMethodInClass(c);
            if (m == null) m = findKnownDeleteMethodByName(c);
            if (isUsableDeleteServiceClass(c, m)) {
                deleteContactServiceClass = c;
                deleteContactMethod = m;
                addLog("删除服务版本命中: " + ver + " -> " + c.getName() + "." + m.getName());
                return deleteContactServiceClass;
            }
        } catch (Throwable t) {
            addLog("删除服务版本兜底失败: " + versionHints[i] + "，" + t.getMessage());
        }
    }
    return null;
}

boolean isUsableDeleteServiceClass(Class c, Method m) {
    if (c == null || m == null) return false;
    if (!hasNoArgConstructor(c)) return false;
    Class[] pts = m.getParameterTypes();
    if (pts.length == 2 && pts[0] == String.class && (pts[1] == boolean.class || pts[1] == Boolean.class)) return true;
    if (pts.length == 1 && pts[0] == String.class) return true;
    return false;
}

boolean hasNoArgConstructor(Class c) {
    try {
        c.getDeclaredConstructor(new Class[0]);
        return true;
    } catch (Throwable t) {}
    return false;
}

Method resolveDeleteContactMethod(Class serviceClass) {
    if (deleteContactMethod != null) return deleteContactMethod;
    if (serviceClass == null) return null;
    deleteContactMethod = findDeleteMethodInClass(serviceClass);
    if (deleteContactMethod == null) deleteContactMethod = findKnownDeleteMethodByName(serviceClass);
    return deleteContactMethod;
}

Method findKnownDeleteMethodByName(Class serviceClass) {
    if (serviceClass == null) return null;
    String[] names = new String[]{"a", "b", "c", "d"};
    Class[][] sigs = new Class[][]{
        new Class[]{String.class, boolean.class},
        new Class[]{String.class, Boolean.class},
        new Class[]{String.class, int.class},
        new Class[]{String.class, Integer.class},
        new Class[]{String.class}
    };
    for (int i = 0; i < names.length; i++) {
        for (int j = 0; j < sigs.length; j++) {
            try {
                Method m = serviceClass.getDeclaredMethod(names[i], sigs[j]);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignore) {}
            try {
                Method m = serviceClass.getMethod(names[i], sigs[j]);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignore) {}
        }
    }
    return null;
}

Method findDeleteMethodInClass(Class serviceClass) {
    if (serviceClass == null) return null;
    Method best = null;
    try {
        Method[] methods = serviceClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            Class[] pts = m.getParameterTypes();
            if (pts.length == 2 && pts[0] == String.class && (pts[1] == boolean.class || pts[1] == Boolean.class)) {
                if (m.getReturnType() == void.class) return m;
                if (best == null) best = m;
            }
        }
        if (best != null) return best;
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            Class[] pts = m.getParameterTypes();
            if (pts.length == 1 && pts[0] == String.class) {
                if (m.getReturnType() == void.class) return m;
                if (best == null) best = m;
            }
        }
    } catch (Throwable ignore) {}
    try {
        Method[] methods = serviceClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            Class[] pts = m.getParameterTypes();
            if (pts.length == 2 && pts[0] == String.class && (pts[1] == boolean.class || pts[1] == Boolean.class)) {
                if (m.getReturnType() == void.class) return m;
                if (best == null) best = m;
            }
        }
        if (best != null) return best;
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            Class[] pts = m.getParameterTypes();
            if (pts.length == 1 && pts[0] == String.class) {
                if (m.getReturnType() == void.class) return m;
                if (best == null) best = m;
            }
        }
    } catch (Throwable ignore) {}
    return best;
}

boolean invokeDeleteContactService(Class serviceClass, Method method, String wxid, boolean clearRecord) throws Throwable {
    Constructor ctor = serviceClass.getDeclaredConstructor(new Class[0]);
    ctor.setAccessible(true);
    Object service = ctor.newInstance(new Object[0]);
    method.setAccessible(true);
    Class[] pts = method.getParameterTypes();
    if (pts.length == 2) {
        method.invoke(service, new Object[]{wxid, Boolean.valueOf(clearRecord)});
        return true;
    }
    if (pts.length == 1) {
        method.invoke(service, new Object[]{wxid});
        return true;
    }
    return false;
}

boolean invokeKnownDeleteContactService(Class serviceClass, String wxid, boolean clearRecord) {
    try {
        Constructor ctor = serviceClass.getDeclaredConstructor(new Class[0]);
        ctor.setAccessible(true);
        Object service = ctor.newInstance(new Object[0]);
        XposedHelpers.callMethod(service, "a", wxid, Boolean.valueOf(clearRecord));
        return true;
    } catch (Throwable ignore) {}
    try {
        Constructor ctor = serviceClass.getDeclaredConstructor(new Class[0]);
        ctor.setAccessible(true);
        Object service = ctor.newInstance(new Object[0]);
        XposedHelpers.callMethod(service, "b", wxid);
        return true;
    } catch (Throwable ignore) {}
    try {
        Constructor ctor = serviceClass.getDeclaredConstructor(new Class[0]);
        ctor.setAccessible(true);
        Object service = ctor.newInstance(new Object[0]);
        XposedHelpers.callMethod(service, "c", wxid, new Integer(0));
        return true;
    } catch (Throwable ignore) {}
    return false;
}

boolean deleteZombieFriend(String wxid) {
    try {
        Class serviceClass = resolveDeleteContactServiceClass();
        Method method = resolveDeleteContactMethod(serviceClass);
        if (serviceClass == null) {
            addLog("删除失败: 未定位到 DeleteContactService，wxid=" + wxid);
            return false;
        }
        if (method == null) {
            if (invokeKnownDeleteContactService(serviceClass, wxid, true)) {
                addLog("删除方法直调命中: " + serviceClass.getName() + "，wxid=" + wxid);
                return true;
            }
            addLog("删除失败: 未定位到删除方法，service=" + serviceClass.getName() + "，wxid=" + wxid);
            return false;
        }
        return invokeDeleteContactService(serviceClass, method, wxid, true);
    } catch (Throwable t) {
        addLog("删除失败: wxid=" + wxid + "，原因=" + t.getMessage());
    }
    return false;
}

boolean deleteFriendByCandidates(String wxid) {
    try {
        List candidates = buildLabelCandidates(wxid);
        if (candidates == null || candidates.isEmpty()) {
            candidates = new ArrayList();
            candidates.add(wxid);
        }
        for (int i = 0; i < candidates.size(); i++) {
            String id = safeToStr(candidates.get(i));
            if (id.length() == 0) continue;
            if (deleteZombieFriend(id)) return true;
        }
    } catch (Throwable t) {
        addLog("删除候选失败: wxid=" + wxid + "，原因=" + t.getMessage());
    }
    return false;
}

// ================= 核心：静默打标签引擎 =================
Constructor resolveAddContactLabelSceneCtor() {
    if (addContactLabelSceneCtor != null) return addContactLabelSceneCtor;
    try {
        List members = findMemberList(strings(new Object[]{
            "/cgi-bin/micromsg-bin/addcontactlabel"
        }));
        for (int i = 0; i < members.size(); i++) {
            Member member = (Member) members.get(i);
            Class c = member.getDeclaringClass();
            Constructor[] ctors = c.getDeclaredConstructors();
            for (int j = 0; j < ctors.length; j++) {
                Class[] pts = ctors[j].getParameterTypes();
                if (pts.length == 1 && (pts[0] == String.class || java.util.List.class.isAssignableFrom(pts[0]))) {
                    addContactLabelSceneClass = c;
                    addContactLabelSceneCtor = ctors[j];
                    return addContactLabelSceneCtor;
                }
            }
        }
    } catch (Throwable t) {
        addLog("DexKit 定位创建标签场景失败: " + t.getMessage());
    }
    return null;
}

void setLabelError(String msg) {
    lastLabelError = msg;
    try { addLog("标签失败: " + msg); } catch (Throwable ignore) {}
}

boolean requestCreateZombieLabel() {
    try {
        if (labelNameExists("僵尸粉")) return true;
        Constructor ctor = resolveAddContactLabelSceneCtor();
        if (ctor == null) {
            setLabelError("未定位到创建标签接口");
            return false;
        }
        ctor.setAccessible(true);
        Class[] pts = ctor.getParameterTypes();
        Object scene;
        if (pts[0] == String.class) {
            scene = ctor.newInstance(new Object[]{"僵尸粉"});
        } else {
            List names = new ArrayList();
            names.add("僵尸粉");
            scene = ctor.newInstance(new Object[]{names});
        }
        boolean sent = dispatchRemittanceScene(scene);
        if (!sent) setLabelError("创建标签请求发送失败");
        return sent;
    } catch (Throwable t) {
        setLabelError("创建标签异常: " + t.getMessage());
    }
    return false;
}

List buildLabelCandidates(String wxid) {
    Set uniq = new HashSet();
    List out = new ArrayList();
    if (wxid != null && wxid.length() > 0) {
        uniq.add(wxid); out.add(wxid);
    }
    try {
        List friends = getFriendList();
        for (int i = 0; i < friends.size(); i++) {
            FriendInfo f = (FriendInfo) friends.get(i);
            if (!wxid.equals(f.getWxid())) continue;
            String a = safeToStr(f.getWxid());
            if (a.length() > 0 && !uniq.contains(a)) { uniq.add(a); out.add(a); }
            try {
                Object bObj = f.getClass().getMethod("getUserName").invoke(f);
                String b = safeToStr(bObj);
                if (b.length() > 0 && !uniq.contains(b)) { uniq.add(b); out.add(b); }
            } catch (Throwable ignore) {}
            break;
        }
    } catch (Throwable t) {}
    return out;
}

String getLabelNameSafe(Object label) {
    if (label == null) return "";
    String name = "";
    try { name = safeToStr(XposedHelpers.callMethod(label, "getName")); } catch (Throwable ignore) {}
    if (name.length() == 0) {
        try { name = safeToStr(XposedHelpers.callMethod(label, "getLabelName")); } catch (Throwable ignore) {}
    }
    if (name.length() == 0) {
        try { name = safeToStr(XposedHelpers.getObjectField(label, "name")); } catch (Throwable ignore) {}
    }
    if (name.length() == 0) {
        try { name = safeToStr(XposedHelpers.getObjectField(label, "labelName")); } catch (Throwable ignore) {}
    }
    if (name.length() == 0) name = safeToStr(label);
    return name;
}

String getLabelIdSafe(Object label) {
    if (label == null) return "";
    String id = "";
    try { id = safeToStr(XposedHelpers.callMethod(label, "getId")); } catch (Throwable ignore) {}
    if (id.length() == 0) {
        try { id = safeToStr(XposedHelpers.callMethod(label, "getLabelId")); } catch (Throwable ignore) {}
    }
    if (id.length() == 0) {
        try { id = safeToStr(XposedHelpers.callMethod(label, "getLabelID")); } catch (Throwable ignore) {}
    }
    if (id.length() == 0) {
        try { id = safeToStr(XposedHelpers.getObjectField(label, "id")); } catch (Throwable ignore) {}
    }
    if (id.length() == 0) {
        try { id = safeToStr(XposedHelpers.getObjectField(label, "labelId")); } catch (Throwable ignore) {}
    }
    if (id.length() == 0) {
        try { id = safeToStr(XposedHelpers.getObjectField(label, "labelID")); } catch (Throwable ignore) {}
    }
    return id;
}

boolean labelNameExists(String labelName) {
    try {
        List labels = getContactLabelList();
        if (labels == null) return false;
        for (int i = 0; i < labels.size(); i++) {
            String name = getLabelNameSafe(labels.get(i));
            if (labelName.equals(name)) return true;
        }
    } catch (Throwable t) {}
    return false;
}

boolean labelContainsAnyCandidate(String labelName, List candidates) {
    try {
        List users = getContactByLabelName(labelName);
        if (users == null) return false;
        for (int i = 0; i < users.size(); i++) {
            String u = safeToStr(users.get(i));
            for (int j = 0; j < candidates.size(); j++) {
                String c = safeToStr(candidates.get(j));
                if (c.length() > 0 && c.equals(u)) return true;
            }
        }
    } catch (Throwable t) {
        setLabelError("标签验证失败: " + t.getMessage());
    }
    return false;
}

boolean waitLabelContainsAnyCandidate(String labelName, List candidates, int attempts, int sleepMs) {
    for (int i = 0; i < attempts; i++) {
        if (labelContainsAnyCandidate(labelName, candidates)) return true;
        try { Thread.sleep(sleepMs); } catch (Throwable ignore) {}
    }
    return false;
}

void writeZombieLabel(String wxid) throws Throwable {
    if (!labelNameExists("僵尸粉")) requestCreateZombieLabel();
    try {
        modifyContactLabelList(wxid, "僵尸粉");
        return;
    } catch (Throwable first) {
        List names = new ArrayList();
        names.add("僵尸粉");
        modifyContactLabelList(wxid, names);
    }
}

boolean applyZombieLabel(String targetWxid) {
    synchronized (labelOpLock) {
        boolean ok = false;
        lastLabelError = "";
        try {
            List candidates = buildLabelCandidates(targetWxid);
            for (int i = 0; i < candidates.size(); i++) {
                String id = safeToStr(candidates.get(i));
                if (id.length() == 0) continue;
                try {
                    writeZombieLabel(id);
                    if (waitLabelContainsAnyCandidate("僵尸粉", candidates, 4, 2500)) {
                        ok = true;
                        break;
                    }
                    if (!labelNameExists("僵尸粉")) {
                        setLabelError("WA 标签接口未创建 [僵尸粉]");
                    } else {
                        setLabelError("标签存在，但未查到好友已加入");
                    }
                } catch (Throwable e1) {
                    setLabelError("标签写入失败(" + id + "): " + e1.getMessage());
                }
            }
            if (!ok && lastLabelError.length() == 0) setLabelError("没有可用 wxid");
        } catch (Throwable t) {
            setLabelError("标签流程异常: " + t.getMessage());
        }
        return ok;
    }
}

// ================= UI 控件 =================
Dialog consoleDialog;
TextView logTextView;
TextView statsTextView;
TextView currentProbeTextView;
EditText minDelayInput;
EditText maxDelayInput;
EditText inflightInput;
EditText tagDeleteDelayInput;
EditText autoDeleteDelayInput;
boolean autoDeleteEnabled = false;
TextView autoDeleteIconView;
TextView autoDeleteLabelView;

void refreshAutoDeleteToggle(Activity ctx) {
    if (autoDeleteIconView == null || autoDeleteLabelView == null) return;
    GradientDrawable boxBg;
    if (autoDeleteEnabled) {
        boxBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
        boxBg.setCornerRadius(dp(ctx, 6));
        autoDeleteIconView.setText("✓");
        autoDeleteIconView.setTextColor(Color.WHITE);
        autoDeleteLabelView.setTextColor(Color.parseColor("#DC2626"));
    } else {
        boxBg = roundRect(Color.WHITE, dp(ctx, 6));
        boxBg.setStroke(dp(ctx, 1), Color.parseColor("#CBD5E1"));
        autoDeleteIconView.setText("");
        autoDeleteLabelView.setTextColor(Color.parseColor("#64748B"));
    }
    autoDeleteIconView.setBackground(boxBg);
}

// ================= 断点记忆系统 =================
void saveProgress() {
    try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pendingList.size(); i++) {
            sb.append(pendingList.get(i)).append(",");
        }
        putString("Jay_Pending_Wxids", sb.toString()); 
    } catch (Throwable t) {}
}

void loadProgress() {
    try {
        String saved = getString("Jay_Pending_Wxids", "");
        pendingList.clear();
        if (saved != null && saved.length() > 0) {
            String[] arr = saved.split(",");
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].trim().length() > 0) {
                    pendingList.add(arr[i].trim());
                }
            }
        }
    } catch (Throwable t) {}
}

// ================= 参数持久化 =================
void saveParams() {
    try {
        putInt("Jay_MinDelay", runtimeMinDelay);
        putInt("Jay_MaxDelay", runtimeMaxDelay);
        putInt("Jay_MaxInflight", runtimeMaxInflight);
        putInt("Jay_TagDeleteDelaySec", runtimeTagDeleteDelaySec);
        putInt("Jay_AutoDeleteDelaySec", runtimeAutoDeleteDelaySec);
    } catch (Throwable t) {}
}

void loadParams() {
    try {
        int savedMin = getInt("Jay_MinDelay", -1);
        if (savedMin >= 0) runtimeMinDelay = savedMin;
        int savedMax = getInt("Jay_MaxDelay", -1);
        if (savedMax >= 0) runtimeMaxDelay = savedMax;
        int savedInflight = getInt("Jay_MaxInflight", -1);
        if (savedInflight >= 1) runtimeMaxInflight = savedInflight;
        int savedTagDelete = getInt("Jay_TagDeleteDelaySec", -1);
        if (savedTagDelete >= 0) runtimeTagDeleteDelaySec = savedTagDelete;
        int savedAutoDelete = getInt("Jay_AutoDeleteDelaySec", -1);
        if (savedAutoDelete >= 0) runtimeAutoDeleteDelaySec = savedAutoDelete;
    } catch (Throwable t) {}
}

// ================= 参考朋友圈自动点赞风格的控制台 UI =================
void showJayConsole() {
    if (dispatchHandler == null) {
        dispatchThread = new HandlerThread("jay-dispatch");
        dispatchThread.start();
        dispatchHandler = new Handler(dispatchThread.getLooper());
    }
    loadParams();
    final Activity ctx = (Activity) getTopActivity();
    if (ctx == null) { toast("请先进入微信主界面！"); return; }
    ctx.runOnUiThread(new Runnable() {
        public void run() {
            try {
                final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);

                FrameLayout root = new FrameLayout(ctx);
                root.setBackgroundColor(Color.parseColor("#66000000"));

                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
                cardLp.leftMargin = dp(ctx, 18);
                cardLp.rightMargin = dp(ctx, 18);
                cardLp.gravity = Gravity.CENTER;
                card.setLayoutParams(cardLp);
                card.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 12));
                card.setBackground(makeUiCardBg(ctx, 20, "#DDE6F2"));

                TextView title = new TextView(ctx);
                title.setText("僵尸粉检测");
                title.setTextColor(Color.parseColor("#0F172A"));
                title.setTextSize(22f);
                title.setTypeface(null, Typeface.BOLD);
                card.addView(title);

                TextView sub = new TextView(ctx);
                sub.setText("静默检测好友关系，支持标签标记与可选删除");
                sub.setTextColor(Color.parseColor("#64748B"));
                sub.setTextSize(13f);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
                subLp.topMargin = dp(ctx, 8);
                card.addView(sub, subLp);

                LinearLayout body = new LinearLayout(ctx);
                body.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable bodyBg = roundRect(Color.parseColor("#F8FAFC"), dp(ctx, 14));
                bodyBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
                body.setBackground(bodyBg);
                LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
                bodyLp.topMargin = dp(ctx, 14);

                LinearLayout statRow = new LinearLayout(ctx);
                statRow.setOrientation(LinearLayout.VERTICAL);
                statRow.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
                statsTextView = new TextView(ctx);
                statsTextView.setTextColor(Color.parseColor("#2563EB"));
                statsTextView.setTextSize(17f);
                statsTextView.setTypeface(null, Typeface.BOLD);
                statRow.addView(statsTextView);
                currentProbeTextView = new TextView(ctx);
                currentProbeTextView.setTextColor(Color.parseColor("#475569"));
                currentProbeTextView.setTextSize(14f);
                currentProbeTextView.setText(currentStatusText);
                LinearLayout.LayoutParams probeLp = new LinearLayout.LayoutParams(-1, -2);
                probeLp.topMargin = dp(ctx, 8);
                statRow.addView(currentProbeTextView, probeLp);
                body.addView(statRow);
                addUiDivider(ctx, body);

                LinearLayout paramPanel = new LinearLayout(ctx);
                paramPanel.setOrientation(LinearLayout.VERTICAL);
                paramPanel.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
                TextView paramTitle = new TextView(ctx);
                paramTitle.setText("运行参数");
                paramTitle.setTextColor(Color.parseColor("#0F172A"));
                paramTitle.setTextSize(17f);
                paramPanel.addView(paramTitle);

                LinearLayout inputRow = new LinearLayout(ctx);
                inputRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(-1, -2);
                inputRowLp.topMargin = dp(ctx, 10);

                LinearLayout minBox = new LinearLayout(ctx); minBox.setOrientation(LinearLayout.VERTICAL);
                TextView minLabel = new TextView(ctx); minLabel.setText("最小延迟"); minLabel.setTextColor(Color.parseColor("#64748B")); minLabel.setTextSize(12f); minBox.addView(minLabel);
                minDelayInput = new EditText(ctx); minDelayInput.setText(String.valueOf(runtimeMinDelay)); minDelayInput.setSingleLine(true); minDelayInput.setTextSize(15f); minDelayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); minDelayInput.setTextColor(Color.parseColor("#0F172A")); minDelayInput.setGravity(Gravity.CENTER);
                GradientDrawable inputBg1 = roundRect(Color.WHITE, dp(ctx, 10)); inputBg1.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); minDelayInput.setBackground(inputBg1); minDelayInput.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7)); minBox.addView(minDelayInput);

                LinearLayout maxBox = new LinearLayout(ctx); maxBox.setOrientation(LinearLayout.VERTICAL);
                TextView maxLabel = new TextView(ctx); maxLabel.setText("最大延迟"); maxLabel.setTextColor(Color.parseColor("#64748B")); maxLabel.setTextSize(12f); maxBox.addView(maxLabel);
                maxDelayInput = new EditText(ctx); maxDelayInput.setText(String.valueOf(runtimeMaxDelay)); maxDelayInput.setSingleLine(true); maxDelayInput.setTextSize(15f); maxDelayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); maxDelayInput.setTextColor(Color.parseColor("#0F172A")); maxDelayInput.setGravity(Gravity.CENTER);
                GradientDrawable inputBg2 = roundRect(Color.WHITE, dp(ctx, 10)); inputBg2.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); maxDelayInput.setBackground(inputBg2); maxDelayInput.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7)); maxBox.addView(maxDelayInput);

                LinearLayout inflightBox = new LinearLayout(ctx); inflightBox.setOrientation(LinearLayout.VERTICAL);
                TextView inflightLabel = new TextView(ctx); inflightLabel.setText("并发数"); inflightLabel.setTextColor(Color.parseColor("#64748B")); inflightLabel.setTextSize(12f); inflightBox.addView(inflightLabel);
                inflightInput = new EditText(ctx); inflightInput.setText(String.valueOf(runtimeMaxInflight)); inflightInput.setSingleLine(true); inflightInput.setTextSize(15f); inflightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); inflightInput.setTextColor(Color.parseColor("#0F172A")); inflightInput.setGravity(Gravity.CENTER);
                GradientDrawable inputBg4 = roundRect(Color.WHITE, dp(ctx, 10)); inputBg4.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); inflightInput.setBackground(inputBg4); inflightInput.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7)); inflightBox.addView(inflightInput);

                LinearLayout.LayoutParams fieldLp1 = new LinearLayout.LayoutParams(0, -2, 1f); fieldLp1.rightMargin = dp(ctx, 10);
                LinearLayout.LayoutParams fieldLp2 = new LinearLayout.LayoutParams(0, -2, 1f); fieldLp2.rightMargin = dp(ctx, 10);
                inputRow.addView(minBox, fieldLp1);
                inputRow.addView(maxBox, fieldLp2);
                inputRow.addView(inflightBox, new LinearLayout.LayoutParams(0, -2, 1f));
                paramPanel.addView(inputRow, inputRowLp);

                LinearLayout delayRow = new LinearLayout(ctx);
                delayRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams delayRowLp = new LinearLayout.LayoutParams(-1, -2);
                delayRowLp.topMargin = dp(ctx, 10);

                LinearLayout tagDelayBox = new LinearLayout(ctx); tagDelayBox.setOrientation(LinearLayout.VERTICAL);
                TextView tagDelayLabel = new TextView(ctx); tagDelayLabel.setText("标签删延(秒)"); tagDelayLabel.setTextColor(Color.parseColor("#64748B")); tagDelayLabel.setTextSize(12f); tagDelayBox.addView(tagDelayLabel);
                tagDeleteDelayInput = new EditText(ctx); tagDeleteDelayInput.setText(String.valueOf(runtimeTagDeleteDelaySec)); tagDeleteDelayInput.setSingleLine(true); tagDeleteDelayInput.setTextSize(15f); tagDeleteDelayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); tagDeleteDelayInput.setTextColor(Color.parseColor("#0F172A")); tagDeleteDelayInput.setGravity(Gravity.CENTER);
                GradientDrawable tagDelayBg = roundRect(Color.WHITE, dp(ctx, 10)); tagDelayBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); tagDeleteDelayInput.setBackground(tagDelayBg); tagDeleteDelayInput.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7)); tagDelayBox.addView(tagDeleteDelayInput);

                LinearLayout autoDelayBox = new LinearLayout(ctx); autoDelayBox.setOrientation(LinearLayout.VERTICAL);
                TextView autoDelayLabel = new TextView(ctx); autoDelayLabel.setText("僵尸删延(秒)"); autoDelayLabel.setTextColor(Color.parseColor("#64748B")); autoDelayLabel.setTextSize(12f); autoDelayBox.addView(autoDelayLabel);
                autoDeleteDelayInput = new EditText(ctx); autoDeleteDelayInput.setText(String.valueOf(runtimeAutoDeleteDelaySec)); autoDeleteDelayInput.setSingleLine(true); autoDeleteDelayInput.setTextSize(15f); autoDeleteDelayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); autoDeleteDelayInput.setTextColor(Color.parseColor("#0F172A")); autoDeleteDelayInput.setGravity(Gravity.CENTER);
                GradientDrawable autoDelayBg = roundRect(Color.WHITE, dp(ctx, 10)); autoDelayBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0")); autoDeleteDelayInput.setBackground(autoDelayBg); autoDeleteDelayInput.setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7)); autoDelayBox.addView(autoDeleteDelayInput);

                delayRow.addView(tagDelayBox, fieldLp1);
                delayRow.addView(autoDelayBox, new LinearLayout.LayoutParams(0, -2, 1f));
                paramPanel.addView(delayRow, delayRowLp);

                autoDeleteEnabled = false;
                LinearLayout autoDeleteRow = new LinearLayout(ctx);
                autoDeleteRow.setOrientation(LinearLayout.HORIZONTAL);
                autoDeleteRow.setGravity(Gravity.CENTER_VERTICAL);
                autoDeleteRow.setPadding(0, dp(ctx, 12), 0, 0);
                autoDeleteIconView = new TextView(ctx);
                autoDeleteIconView.setGravity(Gravity.CENTER);
                autoDeleteIconView.setTextSize(14f);
                autoDeleteIconView.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams toggleIconLp = new LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22));
                autoDeleteRow.addView(autoDeleteIconView, toggleIconLp);
                autoDeleteLabelView = new TextView(ctx);
                autoDeleteLabelView.setText("检测到僵尸粉后自动删除好友");
                autoDeleteLabelView.setTextSize(14f);
                autoDeleteLabelView.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams toggleLabelLp = new LinearLayout.LayoutParams(0, -2, 1f);
                toggleLabelLp.leftMargin = dp(ctx, 8);
                autoDeleteRow.addView(autoDeleteLabelView, toggleLabelLp);
                refreshAutoDeleteToggle(ctx);
                autoDeleteRow.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        autoDeleteEnabled = !autoDeleteEnabled;
                        refreshAutoDeleteToggle(ctx);
                    }
                });
                paramPanel.addView(autoDeleteRow);
                body.addView(paramPanel);
                addUiDivider(ctx, body);

                LinearLayout logPanel = new LinearLayout(ctx);
                logPanel.setOrientation(LinearLayout.VERTICAL);
                logPanel.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
                TextView logTitle = new TextView(ctx);
                logTitle.setText("运行日志");
                logTitle.setTextColor(Color.parseColor("#0F172A"));
                logTitle.setTextSize(17f);
                logPanel.addView(logTitle);
                ScrollView scrollView = new ScrollView(ctx);
                GradientDrawable logBg = roundRect(Color.parseColor("#FFFFFF"), dp(ctx, 12));
                logBg.setStroke(dp(ctx, 1), Color.parseColor("#E2E8F0"));
                scrollView.setBackground(logBg);
                logTextView = new TextView(ctx);
                logTextView.setTextColor(Color.parseColor("#334155"));
                logTextView.setTextSize(12f);
                logTextView.setTypeface(Typeface.MONOSPACE);
                logTextView.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
                logTextView.setText(consoleLogBuffer);
                scrollView.addView(logTextView);
                LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(-1, dp(ctx, 180));
                logLp.topMargin = dp(ctx, 10);
                logPanel.addView(scrollView, logLp);
                body.addView(logPanel);
                card.addView(body, bodyLp);

                LinearLayout actionTop = new LinearLayout(ctx);
                actionTop.setGravity(Gravity.END);
                LinearLayout.LayoutParams actionTopLp = new LinearLayout.LayoutParams(-1, -2);
                actionTopLp.topMargin = dp(ctx, 14);
                final TextView startBtn = makeUiBtn(ctx, "开始 / 继续", true);
                final TextView pauseBtn = makeUiBtn(ctx, "结束并保存", false);
                LinearLayout.LayoutParams actionBtnLp = new LinearLayout.LayoutParams(-2, -2);
                actionBtnLp.leftMargin = dp(ctx, 10);
                actionTop.addView(pauseBtn);
                actionTop.addView(startBtn, actionBtnLp);
                card.addView(actionTop, actionTopLp);

                LinearLayout actionLabel = new LinearLayout(ctx);
                actionLabel.setGravity(Gravity.END);
                LinearLayout.LayoutParams actionLabelLp = new LinearLayout.LayoutParams(-1, -2);
                actionLabelLp.topMargin = dp(ctx, 10);
                final TextView filterLabelBtn = makeUiBtn(ctx, "过滤标签好友", false);
                final TextView deleteLabelBtn = makeUiBtn(ctx, "删除标签好友", false);
                actionLabel.addView(filterLabelBtn);
                actionLabel.addView(deleteLabelBtn, actionBtnLp);
                card.addView(actionLabel, actionLabelLp);

                LinearLayout actionBottom = new LinearLayout(ctx);
                actionBottom.setGravity(Gravity.END);
                LinearLayout.LayoutParams actionBottomLp = new LinearLayout.LayoutParams(-1, -2);
                actionBottomLp.topMargin = dp(ctx, 10);
                final TextView resetBtn = makeUiBtn(ctx, "重置全部", false);
                final TextView selectBtn = makeUiBtn(ctx, "自选好友", false);
                final TextView closeBtn = makeUiBtn(ctx, "完成", true);
                actionBottom.addView(resetBtn);
                actionBottom.addView(selectBtn, actionBtnLp);
                actionBottom.addView(closeBtn, actionBtnLp);
                card.addView(actionBottom, actionBottomLp);

                startBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isRunning) return;
                        if (pendingList.isEmpty()) {
                            loadProgress();
                            if (pendingList.isEmpty()) { addLog("⚠️ 记忆为空，请点击[重置全部]或[自选好友]！"); return; }
                        }
                        try { runtimeMinDelay = Integer.parseInt(minDelayInput.getText().toString()); } catch (Exception e) { runtimeMinDelay = 4; }
                        try { runtimeMaxDelay = Integer.parseInt(maxDelayInput.getText().toString()); } catch (Exception e) { runtimeMaxDelay = 8; }
                        try { runtimeMaxInflight = Integer.parseInt(inflightInput.getText().toString()); } catch (Exception e) { runtimeMaxInflight = 3; }
                        try { runtimeTagDeleteDelaySec = Integer.parseInt(tagDeleteDelayInput.getText().toString()); } catch (Exception e) { runtimeTagDeleteDelaySec = 1; }
                        try { runtimeAutoDeleteDelaySec = Integer.parseInt(autoDeleteDelayInput.getText().toString()); } catch (Exception e) { runtimeAutoDeleteDelaySec = 3; }
                        if (runtimeMaxInflight < 1) runtimeMaxInflight = 1;
                        if (runtimeMaxInflight > 10) runtimeMaxInflight = 10;
                        if (runtimeMinDelay < 0) runtimeMinDelay = 0;
                        if (runtimeMaxDelay < runtimeMinDelay) runtimeMaxDelay = runtimeMinDelay;
                        if (runtimeTagDeleteDelaySec < 0) runtimeTagDeleteDelaySec = 0;
                        if (runtimeAutoDeleteDelaySec < 0) runtimeAutoDeleteDelaySec = 0;
                        saveParams();
                        dispatchedBatchCount = 0;
                        synchronized (probeLock) {
                            inFlightProbeCount = 0;
                            probeWxidMap.clear();
                            probeNameMap.clear();
                            probeIndexMap.clear();
                            probeRetryMap.clear();
                        }
                        isRunning = true;
                        totalTargetCount = pendingList.size() + inFlightProbeCount;
                        acquireZombieWakeLock();
                        createNotificationChannel();
                        updateNotification();
setProbeStatus("状态: 引擎运行中，等待回包");
                        addLog("🚀 引擎启动！待测队列: " + pendingList.size() + " 人");
                        dispatchChecker();
                    }
                });
                
                pauseBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        isRunning = false; saveProgress();
                        releaseZombieWakeLock();
                        synchronized (probeLock) {
                            inFlightProbeCount = 0;
                            probeWxidMap.clear();
                            probeNameMap.clear();
                            probeIndexMap.clear();
                        }
                        updateNotification();
                        setProbeStatus("🛑 已强行结束，当前进度已安全保存！");
                    }
                });

                resetBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isRunning) { addLog("⚠️ 请先结束任务！"); return; }
                        addLog("⏳ 正在后台加载通讯录...");
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    List friends = getFriendList();
                                    pendingList.clear(); checkedTotalCount = 0; deadList.clear();
                                    int skipped = 0;
                                    for (int i = 0; i < friends.size(); i++) {
                                        FriendInfo f = (FriendInfo) friends.get(i);
                                        String wxid = f.getWxid();
                                        if (wxid.contains("gh_") || wxid.equals("filehelper") || wxid.equals("weixin")) continue;
                                        if (isFilterWxid(wxid)) { skipped++; continue; }
                                        pendingList.add(wxid);
                                    }
                                    saveProgress();
                                    addLog("✅ 已加载全部通讯录，共计: " + pendingList.size() + " 人" + (skipped > 0 ? "，已跳过过滤好友 " + skipped + " 位" : ""));
                                    refreshStats();
                                } catch (Throwable t) {
                                    addLog("加载通讯录失败: " + t.getMessage());
                                }
                            }
                        }).start();
                    }
                });

                selectBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isRunning) { addLog("⚠️ 请先结束任务！"); return; }
                        showFriendSelectDialog(ctx);
                    }
                });

                deleteLabelBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isRunning) { addLog("⚠️ 请先结束任务！"); return; }
                        showDeleteLabelFriendDialog(ctx);
                    }
                });

                filterLabelBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (isRunning) { addLog("⚠️ 请先结束任务！"); return; }
                        showFilterLabelFriendDialog(ctx);
                    }
                });

                loadProgress(); refreshStats();

                closeBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { dialog.dismiss(); }
                });

                consoleDialog = dialog;
                finishUiDialogLayout(dialog, root, card);
                showUiDialogAnimated(dialog, card, ctx, 20, 180);
            } catch (Throwable t) { addLog("UI 初始化失败: " + t.getMessage()); }
        }
    });
}

List getContactsByLabelSafe(String labelId, String labelName) {
    try {
        if (labelId != null && labelId.length() > 0) {
            List byId = getContactByLabelId(labelId);
            if (byId != null && byId.size() > 0) return byId;
        }
    } catch (Throwable ignore) {}
    try {
        if (labelName != null && labelName.length() > 0) {
            List byName = getContactByLabelName(labelName);
            if (byName != null) return byName;
        }
    } catch (Throwable ignore) {}
    return new ArrayList();
}

boolean isFilterWxid(String wxid) {
    return wxid != null && filterWxids.contains(wxid);
}

void removeFilteredPending() {
    try {
        if (filterWxids == null || filterWxids.isEmpty()) return;
        List kept = new ArrayList();
        int removed = 0;
        for (int i = 0; i < pendingList.size(); i++) {
            String wxid = safeToStr(pendingList.get(i));
            if (isFilterWxid(wxid)) removed++;
            else kept.add(wxid);
        }
        if (removed > 0) {
            pendingList.clear();
            pendingList.addAll(kept);
            saveProgress();
            addLog("🧹 已从待测队列跳过过滤好友 " + removed + " 位");
            refreshStats();
        }
    } catch (Throwable t) {
        addLog("过滤待测队列失败: " + t.getMessage());
    }
}

void addFilterLabelMembers(String labelId, String labelName) {
    try {
        List users = getContactsByLabelSafe(labelId, labelName);
        if (users != null) {
            for (int i = 0; i < users.size(); i++) {
                String wxid = safeToStr(users.get(i));
                if (wxid.length() > 0) filterWxids.add(wxid);
            }
        }
        if (labelName != null && labelName.length() > 0) filterLabelNames.add(labelName);
    } catch (Throwable t) {
        addLog("添加过滤标签失败: " + labelName + "，" + t.getMessage());
    }
}

String getFriendDisplaySafe(String wxid) {
    String nick = "";
    String remark = "";
    try { nick = safeToStr(getFriendNickName(wxid)); } catch (Throwable ignore) {}
    try { remark = safeToStr(getFriendRemarkName(wxid)); } catch (Throwable ignore) {}
    if (nick.length() == 0 && remark.length() == 0) return wxid;
    if (remark.length() > 0 && !remark.equals(nick)) return nick + "(" + remark + ")";
    return nick;
}

void deleteLabelFriendList(final String labelName, final List wxids, final Runnable onDone) {
    if (wxids == null || wxids.isEmpty()) {
        toast("该标签下没有好友");
        return;
    }
    new Thread(new Runnable() {
        public void run() {
            int ok = 0;
            int fail = 0;
            addLog("🗑️ 开始删除标签 [" + labelName + "] 下的 " + wxids.size() + " 位好友");
            for (int i = 0; i < wxids.size(); i++) {
                String wxid = safeToStr(wxids.get(i));
                if (wxid.length() == 0) continue;
                boolean deleted = deleteFriendByCandidates(wxid);
                if (deleted) ok++; else fail++;
                final int cur = i + 1;
                final int okCount = ok;
                final int failCount = fail;
                setProbeStatus("标签删除: " + cur + "/" + wxids.size() + "，成功 " + okCount + "，失败 " + failCount);
                try { Thread.sleep(runtimeTagDeleteDelaySec * 1000L); } catch (Throwable ignore) {}
            }
            addLog("✅ 标签 [" + labelName + "] 删除完成，成功 " + ok + "，失败 " + fail);
            if (onDone != null) {
                uiHandler.post(new Runnable() {
                    public void run() { onDone.run(); }
                });
            }
        }
    }).start();
}

void showUiConfirmDialog(final Activity ctx, String titleText, String messageText, final Runnable onConfirm) {
    try {
        final Dialog confirm = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        confirm.requestWindowFeature(Window.FEATURE_NO_TITLE);
        confirm.setCancelable(true);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
        cardLp.leftMargin = dp(ctx, 24);
        cardLp.rightMargin = dp(ctx, 24);
        cardLp.gravity = Gravity.CENTER;
        card.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 14));
        card.setBackground(makeUiCardBg(ctx, 18, "#DDE6F2"));
        card.setLayoutParams(cardLp);

        TextView title = new TextView(ctx);
        title.setText(titleText);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTextSize(21f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView msg = new TextView(ctx);
        msg.setText(messageText);
        msg.setTextColor(Color.parseColor("#475569"));
        msg.setTextSize(15f);
        msg.setLineSpacing(dp(ctx, 3), 1.0f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.topMargin = dp(ctx, 14);
        card.addView(msg, msgLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 18);
        TextView cancel = makeUiBtn(ctx, "取消", false);
        TextView ok = makeUiBtn(ctx, "确认删除", true);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(-2, -2);
        okLp.leftMargin = dp(ctx, 10);
        actions.addView(cancel);
        actions.addView(ok, okLp);
        card.addView(actions, actionsLp);

        cancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ confirm.dismiss(); }});
        ok.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                confirm.dismiss();
                if (onConfirm != null) onConfirm.run();
            }
        });

        finishUiDialogLayout(confirm, root, card);
        showUiDialogAnimated(confirm, card, ctx, 10, 140);
    } catch (Throwable t) {
        if (onConfirm != null) onConfirm.run();
    }
}

void showDeleteLabelFriendDialog(final Activity ctx) {
    try {
        final int displayLimit = 300;
        final List labelNames = new ArrayList();
        final List labelIds = new ArrayList();
        final List labelDisplay = new ArrayList();
        final List contactWxids = new ArrayList();
        final List contactDisplayRows = new ArrayList();
        final List currentDisplay = new ArrayList();
        final Set selectedDeleteWxids = new HashSet();
        final Set deletedInDialogWxids = new HashSet();
        final int[] stage = new int[]{0};
        final String[] selectedLabelName = new String[]{""};
        final String[] selectedLabelId = new String[]{""};
        final boolean[] contactsLoaded = new boolean[]{false};

        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -1);
        cardLp.leftMargin = dp(ctx, 16);
        cardLp.rightMargin = dp(ctx, 16);
        cardLp.topMargin = dp(ctx, 40);
        cardLp.bottomMargin = dp(ctx, 40);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeUiCardBg(ctx, 22, "#DDE6F2"));
        card.setLayoutParams(cardLp);

        final TextView title = new TextView(ctx);
        title.setText("删除标签好友");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        final TextView hint = new TextView(ctx);
        hint.setText("正在读取标签列表...");
        hint.setTextColor(Color.parseColor("#64748B"));
        hint.setTextSize(12f);
        hint.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
        card.addView(hint);

        final ListView listView = new ListView(ctx);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = dp(ctx, 8);
        card.addView(listView, listLp);

        LinearLayout selectActions = new LinearLayout(ctx);
        selectActions.setGravity(Gravity.END);
        LinearLayout.LayoutParams selectActionsLp = new LinearLayout.LayoutParams(-1, -2);
        selectActionsLp.topMargin = dp(ctx, 10);
        final TextView selectAllContactsBtn = makeUiBtn(ctx, "全选好友", false);
        final TextView clearSelectedBtn = makeUiBtn(ctx, "清空选择", false);
        LinearLayout.LayoutParams selectBtnLp = new LinearLayout.LayoutParams(-2, -2);
        selectBtnLp.leftMargin = dp(ctx, 10);
        selectActions.addView(selectAllContactsBtn);
        selectActions.addView(clearSelectedBtn, selectBtnLp);
        selectActions.setVisibility(View.GONE);
        card.addView(selectActions, selectActionsLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 12);
        final TextView backBtn = makeUiBtn(ctx, "返回标签", false);
        final TextView cancelBtn = makeUiBtn(ctx, "取消", false);
        final TextView deleteBtn = makeUiBtn(ctx, "删除所选好友", true);
        backBtn.setVisibility(View.GONE);
        deleteBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(-2, -2);
        btnLp1.leftMargin = dp(ctx, 10);
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(-2, -2);
        btnLp2.leftMargin = dp(ctx, 10);
        actions.addView(backBtn);
        actions.addView(cancelBtn, btnLp1);
        actions.addView(deleteBtn, btnLp2);
        card.addView(actions, actionsLp);

        final Runnable showLabelsRunnable = new Runnable() {
            public void run() {
                stage[0] = 0;
                title.setText("删除标签好友");
                hint.setText("请选择一个标签，下一步会读取该标签下的好友");
                selectedDeleteWxids.clear();
                selectActions.setVisibility(View.GONE);
                backBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
                ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labelDisplay);
                listView.setAdapter(adapter);
            }
        };

        final Runnable updateDeleteSelectionHint = new Runnable() {
            public void run() {
                String suffix = contactWxids.size() > displayLimit ? "，仅显示前 " + displayLimit + " 人" : "";
                hint.setText("该标签下共 " + contactWxids.size() + " 位好友，已选 " + selectedDeleteWxids.size() + " 位" + suffix);
            }
        };

        final Runnable showContactsRunnable = new Runnable() {
            public void run() {
                stage[0] = 1;
                currentDisplay.clear();
                currentDisplay.addAll(contactDisplayRows);
                title.setText("标签: " + selectedLabelName[0]);
                updateDeleteSelectionHint.run();
                selectActions.setVisibility(View.VISIBLE);
                backBtn.setVisibility(View.VISIBLE);
                deleteBtn.setVisibility(View.VISIBLE);
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, currentDisplay);
                listView.setAdapter(adapter);
                for (int i = 0; i < contactDisplayRows.size() && i < contactWxids.size(); i++) {
                    listView.setItemChecked(i, selectedDeleteWxids.contains(safeToStr(contactWxids.get(i))));
                }
            }
        };

        final Runnable loadSelectedLabelContacts = new Runnable() {
            public void run() {
                contactsLoaded[0] = false;
                contactWxids.clear();
                contactDisplayRows.clear();
                selectedDeleteWxids.clear();
                hint.setText("正在读取标签 [" + selectedLabelName[0] + "] 下的好友...");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            final List users = getContactsByLabelSafe(selectedLabelId[0], selectedLabelName[0]);
                            final List filteredUsers = new ArrayList();
                            final List rows = new ArrayList();
                            if (users != null) {
                                for (int i = 0; i < users.size(); i++) {
                                    String wxid = safeToStr(users.get(i));
                                    if (deletedInDialogWxids.contains(wxid)) continue;
                                    filteredUsers.add(wxid);
                                    if (rows.size() >= displayLimit) continue;
                                    String name = getFriendDisplaySafe(wxid);
                                    rows.add(name + "\n" + wxid);
                                }
                            }
                            uiHandler.post(new Runnable() {
                                public void run() {
                                    contactWxids.clear();
                                    contactDisplayRows.clear();
                                    contactWxids.addAll(filteredUsers);
                                    contactDisplayRows.addAll(rows);
                                    contactsLoaded[0] = true;
                                    showContactsRunnable.run();
                                }
                            });
                        } catch (final Throwable t) {
                            uiHandler.post(new Runnable() {
                                public void run() {
                                    contactsLoaded[0] = true;
                                    hint.setText("读取标签好友失败: " + t.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        };

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (stage[0] == 1) {
                    if (position < 0 || position >= contactDisplayRows.size() || position >= contactWxids.size()) return;
                    String wxid = safeToStr(contactWxids.get(position));
                    if (listView.isItemChecked(position)) selectedDeleteWxids.add(wxid);
                    else selectedDeleteWxids.remove(wxid);
                    updateDeleteSelectionHint.run();
                    return;
                }
                if (stage[0] != 0 || position < 0 || position >= labelNames.size()) return;
                selectedLabelName[0] = safeToStr(labelNames.get(position));
                selectedLabelId[0] = safeToStr(labelIds.get(position));
                loadSelectedLabelContacts.run();
            }
        });

        selectAllContactsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (stage[0] != 1 || !contactsLoaded[0]) { toast("标签好友还在加载"); return; }
                selectedDeleteWxids.clear();
                for (int i = 0; i < contactWxids.size(); i++) selectedDeleteWxids.add(safeToStr(contactWxids.get(i)));
                showContactsRunnable.run();
            }
        });

        clearSelectedBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedDeleteWxids.clear();
                showContactsRunnable.run();
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showLabelsRunnable.run(); }});
        cancelBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!contactsLoaded[0]) { toast("标签好友还在加载"); return; }
                if (contactWxids.isEmpty()) { toast("该标签下没有好友"); return; }
                if (selectedDeleteWxids.isEmpty()) { toast("请先选择要删除的好友"); return; }
                final List targets = new ArrayList();
                targets.addAll(selectedDeleteWxids);
                showUiConfirmDialog(ctx, "确认删除", "将删除标签 [" + selectedLabelName[0] + "] 中已选的 " + targets.size() + " 位好友。\n\n此操作不可撤销。", new Runnable() {
                    public void run() {
                        deletedInDialogWxids.addAll(targets);
                        deleteLabelFriendList(selectedLabelName[0], targets, null);
                        dialog.dismiss();
                    }
                });
            }
        });

        finishUiDialogLayout(dialog, root, card);
        showUiDialogAnimated(dialog, card, ctx, 14, 160);

        new Thread(new Runnable() {
            public void run() {
                try {
                    List labels = getContactLabelList();
                    labelNames.clear();
                    labelIds.clear();
                    labelDisplay.clear();
                    if (labels != null) {
                        for (int i = 0; i < labels.size(); i++) {
                            Object label = labels.get(i);
                            String name = getLabelNameSafe(label);
                            String lid = getLabelIdSafe(label);
                            if (name.length() == 0) continue;
                            labelNames.add(name);
                            labelIds.add(lid);
                            labelDisplay.add("🏷️ " + name + (lid.length() > 0 ? "  #" + lid : ""));
                        }
                    }
                    uiHandler.post(new Runnable() {
                        public void run() {
                            if (labelNames.isEmpty()) {
                                hint.setText("未读取到标签");
                            } else {
                                showLabelsRunnable.run();
                            }
                        }
                    });
                } catch (final Throwable t) {
                    uiHandler.post(new Runnable() {
                        public void run() { hint.setText("读取标签列表失败: " + t.getMessage()); }
                    });
                }
            }
        }).start();
    } catch (Throwable t) {
        addLog("删除标签好友弹窗失败: " + t.getMessage());
    }
}

void showFilterLabelFriendDialog(final Activity ctx) {
    try {
        final int displayLimit = 300;
        final List labelNames = new ArrayList();
        final List labelIds = new ArrayList();
        final List labelDisplay = new ArrayList();
        final List memberWxids = new ArrayList();
        final List memberRows = new ArrayList();
        final Set tempSelected = new HashSet();
        final int[] stage = new int[]{0};
        final String[] currentLabelName = new String[]{""};
        final String[] currentLabelId = new String[]{""};
        final boolean[] loaded = new boolean[]{false};

        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -1);
        cardLp.leftMargin = dp(ctx, 16);
        cardLp.rightMargin = dp(ctx, 16);
        cardLp.topMargin = dp(ctx, 40);
        cardLp.bottomMargin = dp(ctx, 40);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeUiCardBg(ctx, 22, "#DDE6F2"));
        card.setLayoutParams(cardLp);

        final TextView title = new TextView(ctx);
        title.setText("过滤标签好友");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        final TextView hint = new TextView(ctx);
        hint.setText("正在读取标签列表...");
        hint.setTextColor(Color.parseColor("#64748B"));
        hint.setTextSize(12f);
        hint.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
        card.addView(hint);

        final ListView listView = new ListView(ctx);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = dp(ctx, 8);
        card.addView(listView, listLp);

        LinearLayout tools = new LinearLayout(ctx);
        tools.setGravity(Gravity.END);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, -2);
        toolsLp.topMargin = dp(ctx, 10);
        final TextView clearAllFilterBtn = makeUiBtn(ctx, "清除所有过滤", false);
        final TextView allLabelBtn = makeUiBtn(ctx, "过滤整个标签", false);
        final TextView allMemberBtn = makeUiBtn(ctx, "全选好友", false);
        final TextView clearBtn = makeUiBtn(ctx, "清空选择", false);
        LinearLayout.LayoutParams toolBtnLp = new LinearLayout.LayoutParams(-2, -2);
        toolBtnLp.leftMargin = dp(ctx, 10);
        tools.addView(clearAllFilterBtn);
        tools.addView(allLabelBtn, toolBtnLp);
        tools.addView(allMemberBtn, toolBtnLp);
        tools.addView(clearBtn, toolBtnLp);
        clearAllFilterBtn.setVisibility(View.GONE);
        allLabelBtn.setVisibility(View.GONE);
        allMemberBtn.setVisibility(View.GONE);
        clearBtn.setVisibility(View.GONE);
        card.addView(tools, toolsLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 12);
        final TextView backBtn = makeUiBtn(ctx, "返回标签", false);
        final TextView cancelBtn = makeUiBtn(ctx, "取消", false);
        final TextView saveBtn = makeUiBtn(ctx, "保存过滤", true);
        backBtn.setVisibility(View.GONE);
        saveBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionBtnLp = new LinearLayout.LayoutParams(-2, -2);
        actionBtnLp.leftMargin = dp(ctx, 10);
        actions.addView(backBtn);
        actions.addView(cancelBtn, actionBtnLp);
        actions.addView(saveBtn, actionBtnLp);
        card.addView(actions, actionsLp);

        final Runnable showLabels = new Runnable() {
            public void run() {
                stage[0] = 0;
                title.setText("过滤标签好友");
                hint.setText("请选择标签，点标签可进入成员选择；当前过滤 " + filterWxids.size() + " 位好友");
                listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
                listView.setAdapter(new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labelDisplay));
                allLabelBtn.setVisibility(View.GONE);
                allMemberBtn.setVisibility(View.GONE);
                clearBtn.setVisibility(View.GONE);
                backBtn.setVisibility(View.GONE);
                saveBtn.setVisibility(View.GONE);
                clearAllFilterBtn.setVisibility(View.VISIBLE);
            }
        };

        final Runnable showMembers = new Runnable() {
            public void run() {
                stage[0] = 1;
                title.setText("过滤: " + currentLabelName[0]);
                String suffix = memberWxids.size() > displayLimit ? "，仅显示前 " + displayLimit + " 人" : "";
                hint.setText("标签内 " + memberWxids.size() + " 位好友，已选 " + tempSelected.size() + " 位" + suffix);
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                listView.setAdapter(new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, memberRows));
                for (int i = 0; i < memberRows.size() && i < memberWxids.size(); i++) {
                    listView.setItemChecked(i, tempSelected.contains(safeToStr(memberWxids.get(i))));
                }
                allLabelBtn.setVisibility(View.VISIBLE);
                allMemberBtn.setVisibility(View.VISIBLE);
                clearBtn.setVisibility(View.VISIBLE);
                backBtn.setVisibility(View.VISIBLE);
                saveBtn.setVisibility(View.VISIBLE);
            }
        };

        final Runnable loadMembers = new Runnable() {
            public void run() {
                loaded[0] = false;
                tempSelected.clear();
                memberWxids.clear();
                memberRows.clear();
                hint.setText("正在读取标签 [" + currentLabelName[0] + "] 下的好友...");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            final List users = getContactsByLabelSafe(currentLabelId[0], currentLabelName[0]);
                            final List rows = new ArrayList();
                            if (users != null) {
                                int max = users.size() > displayLimit ? displayLimit : users.size();
                                for (int i = 0; i < max; i++) {
                                    String wxid = safeToStr(users.get(i));
                                    rows.add(getFriendDisplaySafe(wxid) + "\n" + wxid);
                                }
                            }
                            uiHandler.post(new Runnable() {
                                public void run() {
                                    memberWxids.clear();
                                    memberRows.clear();
                                    if (users != null) memberWxids.addAll(users);
                                    memberRows.addAll(rows);
                                    loaded[0] = true;
                                    showMembers.run();
                                }
                            });
                        } catch (final Throwable t) {
                            uiHandler.post(new Runnable(){ public void run(){ loaded[0] = true; hint.setText("读取标签好友失败: " + t.getMessage()); }});
                        }
                    }
                }).start();
            }
        };

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (stage[0] == 0) {
                    if (position < 0 || position >= labelNames.size()) return;
                    currentLabelName[0] = safeToStr(labelNames.get(position));
                    currentLabelId[0] = safeToStr(labelIds.get(position));
                    loadMembers.run();
                    return;
                }
                if (stage[0] == 1) {
                    if (position < 0 || position >= memberRows.size() || position >= memberWxids.size()) return;
                    String wxid = safeToStr(memberWxids.get(position));
                    if (listView.isItemChecked(position)) tempSelected.add(wxid);
                    else tempSelected.remove(wxid);
                    showMembers.run();
                }
            }
        });

        allLabelBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (!loaded[0]) { toast("标签好友还在加载"); return; }
            addFilterLabelMembers(currentLabelId[0], currentLabelName[0]);
            removeFilteredPending();
            addLog("🚫 已过滤标签 [" + currentLabelName[0] + "]，当前过滤 " + filterWxids.size() + " 位好友");
            dialog.dismiss();
        }});
        clearAllFilterBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            filterWxids.clear();
            filterLabelNames.clear();
            removeFilteredPending();
            addLog("🧹 已清除所有过滤，当前过滤 0 位好友");
            showLabels.run();
        }});
        allMemberBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (!loaded[0]) { toast("标签好友还在加载"); return; }
            tempSelected.clear();
            for (int i = 0; i < memberWxids.size(); i++) tempSelected.add(safeToStr(memberWxids.get(i)));
            showMembers.run();
        }});
        clearBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ tempSelected.clear(); showMembers.run(); }});
        backBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showLabels.run(); }});
        cancelBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        saveBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (tempSelected.isEmpty()) { toast("请先选择要过滤的好友"); return; }
            filterWxids.addAll(tempSelected);
            removeFilteredPending();
            addLog("🚫 已新增过滤好友 " + tempSelected.size() + " 位，当前过滤 " + filterWxids.size() + " 位");
            dialog.dismiss();
        }});

        finishUiDialogLayout(dialog, root, card);
        showUiDialogAnimated(dialog, card, ctx, 14, 160);

        new Thread(new Runnable() {
            public void run() {
                try {
                    List labels = getContactLabelList();
                    labelNames.clear(); labelIds.clear(); labelDisplay.clear();
                    if (labels != null) {
                        for (int i = 0; i < labels.size(); i++) {
                            Object label = labels.get(i);
                            String name = getLabelNameSafe(label);
                            String lid = getLabelIdSafe(label);
                            if (name.length() == 0) continue;
                            labelNames.add(name);
                            labelIds.add(lid);
                            labelDisplay.add("🏷️ " + name + (lid.length() > 0 ? "  #" + lid : ""));
                        }
                    }
                    uiHandler.post(new Runnable(){ public void run(){ if (labelNames.isEmpty()) hint.setText("未读取到标签"); else showLabels.run(); }});
                } catch (final Throwable t) {
                    uiHandler.post(new Runnable(){ public void run(){ hint.setText("读取标签列表失败: " + t.getMessage()); }});
                }
            }
        }).start();
    } catch (Throwable t) {
        addLog("过滤标签好友弹窗失败: " + t.getMessage());
    }
}

void showLabelFriendPickerDialog(final Activity ctx, final Set selectedFriends, final Runnable onChanged) {
    try {
        final int displayLimit = 300;
        final List labelNames = new ArrayList();
        final List labelIds = new ArrayList();
        final List labelDisplay = new ArrayList();
        final List memberWxids = new ArrayList();
        final List memberRows = new ArrayList();
        final Set tempSelected = new HashSet();
        final int[] stage = new int[]{0};
        final String[] currentLabelName = new String[]{""};
        final String[] currentLabelId = new String[]{""};
        final boolean[] loaded = new boolean[]{false};

        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -1);
        cardLp.leftMargin = dp(ctx, 16);
        cardLp.rightMargin = dp(ctx, 16);
        cardLp.topMargin = dp(ctx, 40);
        cardLp.bottomMargin = dp(ctx, 40);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeUiCardBg(ctx, 22, "#DDE6F2"));
        card.setLayoutParams(cardLp);

        final TextView title = new TextView(ctx);
        title.setText("标签好友");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        final TextView hint = new TextView(ctx);
        hint.setText("正在读取标签列表...");
        hint.setTextColor(Color.parseColor("#64748B"));
        hint.setTextSize(12f);
        hint.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
        card.addView(hint);

        final ListView listView = new ListView(ctx);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = dp(ctx, 8);
        card.addView(listView, listLp);

        LinearLayout tools = new LinearLayout(ctx);
        tools.setGravity(Gravity.END);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(-1, -2);
        toolsLp.topMargin = dp(ctx, 10);
        final TextView allMemberBtn = makeUiBtn(ctx, "全选好友", false);
        final TextView clearBtn = makeUiBtn(ctx, "清空选择", false);
        LinearLayout.LayoutParams toolBtnLp = new LinearLayout.LayoutParams(-2, -2);
        toolBtnLp.leftMargin = dp(ctx, 10);
        tools.addView(allMemberBtn);
        tools.addView(clearBtn, toolBtnLp);
        allMemberBtn.setVisibility(View.GONE);
        clearBtn.setVisibility(View.GONE);
        card.addView(tools, toolsLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 12);
        final TextView backBtn = makeUiBtn(ctx, "返回标签", false);
        final TextView cancelBtn = makeUiBtn(ctx, "取消", false);
        final TextView addBtn = makeUiBtn(ctx, "加入自选", true);
        backBtn.setVisibility(View.GONE);
        addBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionBtnLp = new LinearLayout.LayoutParams(-2, -2);
        actionBtnLp.leftMargin = dp(ctx, 10);
        actions.addView(backBtn);
        actions.addView(cancelBtn, actionBtnLp);
        actions.addView(addBtn, actionBtnLp);
        card.addView(actions, actionsLp);

        final Runnable showLabels = new Runnable() {
            public void run() {
                stage[0] = 0;
                title.setText("标签好友");
                hint.setText("请选择一个标签，点标签进入好友选择");
                listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
                listView.setAdapter(new ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labelDisplay));
                allMemberBtn.setVisibility(View.GONE);
                clearBtn.setVisibility(View.GONE);
                backBtn.setVisibility(View.GONE);
                addBtn.setVisibility(View.GONE);
            }
        };

        final Runnable showMembers = new Runnable() {
            public void run() {
                stage[0] = 1;
                title.setText("标签: " + currentLabelName[0]);
                String suffix = memberWxids.size() > displayLimit ? "，仅显示前 " + displayLimit + " 人" : "";
                hint.setText("标签内 " + memberWxids.size() + " 位好友，已选 " + tempSelected.size() + " 位" + suffix);
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                listView.setAdapter(new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, memberRows));
                for (int i = 0; i < memberRows.size() && i < memberWxids.size(); i++) {
                    listView.setItemChecked(i, tempSelected.contains(safeToStr(memberWxids.get(i))));
                }
                allMemberBtn.setVisibility(View.VISIBLE);
                clearBtn.setVisibility(View.VISIBLE);
                backBtn.setVisibility(View.VISIBLE);
                addBtn.setVisibility(View.VISIBLE);
            }
        };

        final Runnable loadMembers = new Runnable() {
            public void run() {
                loaded[0] = false;
                tempSelected.clear();
                memberWxids.clear();
                memberRows.clear();
                hint.setText("正在读取标签 [" + currentLabelName[0] + "] 下的好友...");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            final List users = getContactsByLabelSafe(currentLabelId[0], currentLabelName[0]);
                            final List rows = new ArrayList();
                            if (users != null) {
                                int max = users.size() > displayLimit ? displayLimit : users.size();
                                for (int i = 0; i < max; i++) {
                                    String wxid = safeToStr(users.get(i));
                                    rows.add(getFriendDisplaySafe(wxid) + "\n" + wxid);
                                }
                            }
                            uiHandler.post(new Runnable() {
                                public void run() {
                                    memberWxids.clear();
                                    memberRows.clear();
                                    if (users != null) memberWxids.addAll(users);
                                    memberRows.addAll(rows);
                                    loaded[0] = true;
                                    showMembers.run();
                                }
                            });
                        } catch (final Throwable t) {
                            uiHandler.post(new Runnable(){ public void run(){ loaded[0] = true; hint.setText("读取标签好友失败: " + t.getMessage()); }});
                        }
                    }
                }).start();
            }
        };

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (stage[0] == 0) {
                    if (position < 0 || position >= labelNames.size()) return;
                    currentLabelName[0] = safeToStr(labelNames.get(position));
                    currentLabelId[0] = safeToStr(labelIds.get(position));
                    loadMembers.run();
                    return;
                }
                if (stage[0] == 1) {
                    if (position < 0 || position >= memberRows.size() || position >= memberWxids.size()) return;
                    String wxid = safeToStr(memberWxids.get(position));
                    if (listView.isItemChecked(position)) tempSelected.add(wxid);
                    else tempSelected.remove(wxid);
                    showMembers.run();
                }
            }
        });

        allMemberBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (!loaded[0]) { toast("标签好友还在加载"); return; }
            tempSelected.clear();
            for (int i = 0; i < memberWxids.size(); i++) tempSelected.add(safeToStr(memberWxids.get(i)));
            showMembers.run();
        }});
        clearBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ tempSelected.clear(); showMembers.run(); }});
        backBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showLabels.run(); }});
        cancelBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        addBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if (tempSelected.isEmpty()) { toast("请先选择标签好友"); return; }
            selectedFriends.addAll(tempSelected);
            if (onChanged != null) onChanged.run();
            addLog("🏷️ 已从标签 [" + currentLabelName[0] + "] 加入自选好友 " + tempSelected.size() + " 位");
            dialog.dismiss();
        }});

        finishUiDialogLayout(dialog, root, card);
        showUiDialogAnimated(dialog, card, ctx, 14, 160);

        new Thread(new Runnable() {
            public void run() {
                try {
                    List labels = getContactLabelList();
                    labelNames.clear(); labelIds.clear(); labelDisplay.clear();
                    if (labels != null) {
                        for (int i = 0; i < labels.size(); i++) {
                            Object label = labels.get(i);
                            String name = getLabelNameSafe(label);
                            String lid = getLabelIdSafe(label);
                            if (name.length() == 0) continue;
                            labelNames.add(name);
                            labelIds.add(lid);
                            labelDisplay.add("🏷️ " + name + (lid.length() > 0 ? "  #" + lid : ""));
                        }
                    }
                    uiHandler.post(new Runnable(){ public void run(){ if (labelNames.isEmpty()) hint.setText("未读取到标签"); else showLabels.run(); }});
                } catch (final Throwable t) {
                    uiHandler.post(new Runnable(){ public void run(){ hint.setText("读取标签列表失败: " + t.getMessage()); }});
                }
            }
        }).start();
    } catch (Throwable t) {
        addLog("标签好友选择弹窗失败: " + t.getMessage());
    }
}

// ================= 自选好友弹窗，同款卡片 UI =================
void showFriendSelectDialog(Context rawCtx) {
    try {
        final Activity ctx = (Activity) rawCtx;
        final int displayLimit = 300;
        final List allWxids = new ArrayList();
        final List allRows = new ArrayList();
        final Set selectedFriends = new HashSet();
        final List currentMatchedWxids = new ArrayList();
        final List currentFilteredWxids = new ArrayList();
        final List currentFilteredNames = new ArrayList();
        final boolean[] friendListLoaded = new boolean[]{false};

        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#66000000"));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -1);
        cardLp.leftMargin = dp(ctx, 16);
        cardLp.rightMargin = dp(ctx, 16);
        cardLp.topMargin = dp(ctx, 36);
        cardLp.bottomMargin = dp(ctx, 36);
        card.setLayoutParams(cardLp);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 12));
        card.setBackground(makeUiCardBg(ctx, 22, "#DDE6F2"));

        TextView title = new TextView(ctx);
        title.setText("选择待测好友");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        final TextView countText = new TextView(ctx);
        countText.setTextColor(Color.parseColor("#64748B"));
        countText.setTextSize(12f);
        countText.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
        countText.setText("正在后台加载好友列表...");
        card.addView(countText);

        final EditText searchEditText = new EditText(ctx);
        searchEditText.setHint("搜索好友昵称、备注或 wxid");
        searchEditText.setSingleLine(true);
        searchEditText.setTextSize(15f);
        searchEditText.setTextColor(Color.parseColor("#334155"));
        searchEditText.setHintTextColor(Color.parseColor("#A3AEC0"));
        GradientDrawable searchBg = roundRect(Color.parseColor("#F6F8FB"), dp(ctx, 999));
        searchBg.setStroke(dp(ctx, 1), Color.parseColor("#E5E9F0"));
        searchEditText.setBackground(searchBg);
        searchEditText.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        card.addView(searchEditText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout toolRow = new LinearLayout(ctx);
        toolRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams toolLp = new LinearLayout.LayoutParams(-1, -2);
        toolLp.topMargin = dp(ctx, 10);

        final EditText randomCountInput = new EditText(ctx);
        randomCountInput.setHint("随机数");
        randomCountInput.setSingleLine(true);
        randomCountInput.setTextSize(14f);
        randomCountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        randomCountInput.setTextColor(Color.parseColor("#334155"));
        randomCountInput.setHintTextColor(Color.parseColor("#A3AEC0"));
        GradientDrawable randomBg = roundRect(Color.WHITE, dp(ctx, 999));
        randomBg.setStroke(dp(ctx, 1), Color.parseColor("#E5E9F0"));
        randomCountInput.setBackground(randomBg);
        randomCountInput.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        toolRow.addView(randomCountInput, new LinearLayout.LayoutParams(-1, -2));

        final TextView selectAllBtn = makeUiBtn(ctx, "全选结果", false);
        final TextView labelFriendBtn = makeUiBtn(ctx, "标签好友", false);
        final TextView randomPickBtn = makeUiBtn(ctx, "随机勾选", true);
        LinearLayout toolBtnRow = new LinearLayout(ctx);
        toolBtnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams toolBtnRowLp = new LinearLayout.LayoutParams(-1, -2);
        toolBtnRowLp.topMargin = dp(ctx, 10);
        LinearLayout.LayoutParams toolBtnLp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams toolBtnLpMid = new LinearLayout.LayoutParams(0, -2, 1f);
        toolBtnLpMid.leftMargin = dp(ctx, 8);
        LinearLayout.LayoutParams toolBtnLpLast = new LinearLayout.LayoutParams(0, -2, 1f);
        toolBtnLpLast.leftMargin = dp(ctx, 8);
        toolBtnRow.addView(selectAllBtn, toolBtnLp);
        toolBtnRow.addView(labelFriendBtn, toolBtnLpMid);
        toolBtnRow.addView(randomPickBtn, toolBtnLpLast);
        toolRow.addView(toolBtnRow, toolBtnRowLp);
        card.addView(toolRow, toolLp);

        final ListView friendListView = new ListView(ctx);
        friendListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = dp(ctx, 12);
        card.addView(friendListView, listLp);

        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                if (!friendListLoaded[0]) {
                    countText.setText("正在后台加载好友列表...");
                    return;
                }
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentMatchedWxids.clear();
                currentFilteredWxids.clear();
                currentFilteredNames.clear();
                for (int i = 0; i < allWxids.size(); i++) {
                    String wxid = (String) allWxids.get(i);
                    String row = (String) allRows.get(i);
                    if (searchText.length() == 0 || row.toLowerCase().contains(searchText)) {
                        currentMatchedWxids.add(wxid);
                        if (currentFilteredWxids.size() < displayLimit) {
                            currentFilteredWxids.add(wxid);
                            currentFilteredNames.add(row);
                        }
                    }
                }
                ArrayAdapter adapter = new ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, currentFilteredNames);
                friendListView.setAdapter(adapter);
                for (int i = 0; i < currentFilteredWxids.size(); i++) {
                    String id = (String) currentFilteredWxids.get(i);
                    friendListView.setItemChecked(i, selectedFriends.contains(id));
                }
                String suffix = currentMatchedWxids.size() > displayLimit ? "，仅显示前 " + displayLimit + " 人，请搜索缩小范围" : "";
                countText.setText("已选 " + selectedFriends.size() + " 人 / 匹配 " + currentMatchedWxids.size() + " 人" + suffix);
            }
        };

        friendListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                if (position >= currentFilteredWxids.size()) return;
                String selectedId = (String) currentFilteredWxids.get(position);
                if (friendListView.isItemChecked(position)) selectedFriends.add(selectedId);
                else selectedFriends.remove(selectedId);
                String suffix = currentMatchedWxids.size() > displayLimit ? "，仅显示前 " + displayLimit + " 人，请搜索缩小范围" : "";
                countText.setText("已选 " + selectedFriends.size() + " 人 / 匹配 " + currentMatchedWxids.size() + " 人" + suffix);
            }
        });

        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!friendListLoaded[0]) { toast("好友列表还在加载"); return; }
                for (int i = 0; i < currentMatchedWxids.size(); i++) selectedFriends.add(currentMatchedWxids.get(i));
                updateListRunnable.run();
            }
        });

        labelFriendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showLabelFriendPickerDialog(ctx, selectedFriends, updateListRunnable);
            }
        });

        randomPickBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int count = 0;
                try { count = Integer.parseInt(randomCountInput.getText().toString().trim()); } catch (Exception e) { count = 0; }
                if (count <= 0) { toast("请输入数量"); return; }
                if (!friendListLoaded[0]) { toast("好友列表还在加载"); return; }
                List pool = new ArrayList(); pool.addAll(currentMatchedWxids);
                if (pool.isEmpty()) return;
                if (count > pool.size()) count = pool.size();
                selectedFriends.clear();
                for (int i = 0; i < count; i++) {
                    int idx = random.nextInt(pool.size());
                    selectedFriends.add(pool.remove(idx));
                }
                updateListRunnable.run();
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateListRunnable.run(); }
            public void afterTextChanged(Editable s) {}
        });

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(ctx, 12);
        TextView clearBtn = makeUiBtn(ctx, "清空", false);
        TextView cancelBtn = makeUiBtn(ctx, "取消", false);
        TextView okBtn = makeUiBtn(ctx, "载入", true);
        LinearLayout.LayoutParams smallBtnLp = new LinearLayout.LayoutParams(-2, -2);
        smallBtnLp.leftMargin = dp(ctx, 10);
        actions.addView(clearBtn);
        actions.addView(cancelBtn, smallBtnLp);
        actions.addView(okBtn, smallBtnLp);
        card.addView(actions, actionsLp);

        clearBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ selectedFriends.clear(); updateListRunnable.run(); }});
        cancelBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dialog.dismiss(); }});
        okBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!friendListLoaded[0]) { toast("好友列表还在加载"); return; }
                pendingList.clear();
                int skipped = 0;
                java.util.Iterator it = selectedFriends.iterator();
                while (it.hasNext()) {
                    String wxid = safeToStr(it.next());
                    if (isFilterWxid(wxid)) skipped++;
                    else pendingList.add(wxid);
                }
                saveProgress();
                refreshStats();
                addLog("🎯 成功载入指定的 " + pendingList.size() + " 名好友" + (skipped > 0 ? "，已跳过过滤好友 " + skipped + " 位" : ""));
                dialog.dismiss();
            }
        });

        finishUiDialogLayout(dialog, root, card);
        showUiDialogAnimated(dialog, card, ctx, 14, 160);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List rawFriends = getFriendList();
                    final List loadedWxids = new ArrayList();
                    final List loadedRows = new ArrayList();
                    if (rawFriends != null) {
                        for (int i = 0; i < rawFriends.size(); i++) {
                            FriendInfo friendInfo = (FriendInfo) rawFriends.get(i);
                            String wxid = friendInfo.getWxid();
                            if (wxid == null || wxid.contains("gh_") || wxid.equals("filehelper") || wxid.equals("weixin")) continue;
                            String nickname = friendInfo.getNickname();
                            String remark = friendInfo.getRemark();
                            String displayName = (remark != null && remark.length() > 0) ? nickname + "(" + remark + ")" : nickname;
                            if (displayName == null || displayName.length() == 0) displayName = "未知好友";
                            loadedWxids.add(wxid);
                            loadedRows.add(displayName + "\n" + wxid);
                        }
                    }
                    uiHandler.post(new Runnable() {
                        public void run() {
                            try {
                                allWxids.clear();
                                allRows.clear();
                                allWxids.addAll(loadedWxids);
                                allRows.addAll(loadedRows);
                                friendListLoaded[0] = true;
                                if (allWxids.isEmpty()) {
                                    countText.setText("未获取到好友列表");
                                    toast("未获取到好友列表");
                                } else {
                                    updateListRunnable.run();
                                }
                            } catch (Throwable t2) {
                                countText.setText("好友列表加载失败: " + t2.getMessage());
                            }
                        }
                    });
                } catch (final Throwable t) {
                    uiHandler.post(new Runnable() {
                        public void run() {
                            friendListLoaded[0] = true;
                            countText.setText("好友列表加载失败: " + t.getMessage());
                            addLog("自选好友加载失败: " + t.getMessage());
                        }
                    });
                }
            }
        }).start();
    } catch (Exception e) { addLog("弹窗失败: " + e.getMessage()); }
}

// ================= UI 刷新逻辑 =================
void refreshStats() {
    uiHandler.post(new Runnable() {
        public void run() {
            if (statsTextView != null) {
                statsTextView.setText("待测: " + pendingList.size() + "  |  已测: " + checkedTotalCount + "  |  僵尸粉: " + deadList.size());
            }
        }
    });
}

void setProbeStatus(String text) {
    currentStatusText = text;
    uiHandler.post(new Runnable() {
        public void run() {
            if (currentProbeTextView != null) currentProbeTextView.setText(currentStatusText);
        }
    });
}

void addLog(String text) {
    try { log(text); } catch (Throwable ignore) {}
    try {
        consoleLogBuffer = consoleLogBuffer + "\n" + text;
        if (consoleLogBuffer.length() > 8000) consoleLogBuffer = consoleLogBuffer.substring(consoleLogBuffer.length() - 5000);
    } catch (Throwable ignore) {}
    uiHandler.post(new Runnable() {
        public void run() {
            if (logTextView != null) {
                logTextView.setText(consoleLogBuffer);
                try {
                    ScrollView sv = (ScrollView) logTextView.getParent();
                    sv.post(new Runnable() { public void run() { sv.fullScroll(ScrollView.FOCUS_DOWN); }});
                } catch(Exception e){}
            }
            refreshStats();
        }
    });
}

// ================= 常驻进度通知 =================
void createNotificationChannel() {
    try {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Context ctx = getNotifyContext();
            if (ctx == null) return;
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            android.app.NotificationChannel channel = new android.app.NotificationChannel(notificationChannelId, "僵尸粉检测进度", android.app.NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示僵尸粉检测的实时进度");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    } catch (Throwable t) {}
}

Context getNotifyContext() {
    try {
        Context ctx = (Context) getTopActivity();
        if (ctx != null) return ctx;
    } catch (Throwable ignore) {}
    try {
        if (hostContext != null) return hostContext;
    } catch (Throwable ignore) {}
    return null;
}

void acquireZombieWakeLock() {
    try {
        if (zombieWakeLock != null && zombieWakeLock.isHeld()) return;
        Context ctx = getNotifyContext();
        if (ctx == null) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        zombieWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wauxv:zombie-check");
        zombieWakeLock.setReferenceCounted(false);
        zombieWakeLock.acquire(60 * 60 * 1000L);
        addLog("🔋 已申请后台保活 WakeLock");
    } catch (Throwable t) {
        addLog("WakeLock 申请失败: " + t.getMessage());
    }
}

void releaseZombieWakeLock() {
    try {
        if (zombieWakeLock != null && zombieWakeLock.isHeld()) {
            zombieWakeLock.release();
            addLog("🔋 已释放后台保活 WakeLock");
        }
    } catch (Throwable ignore) {}
    zombieWakeLock = null;
}

void updateNotification() {
    try {
        Context ctx = getNotifyContext();
        if (ctx == null) return;
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        
        int done = checkedTotalCount;
        int total = totalTargetCount > 0 ? totalTargetCount : Math.max(done + pendingList.size() + inFlightProbeCount, done);
        int deadCount = deadList.size();
        
        android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.Notification.Builder builder = new android.app.Notification.Builder(ctx, notificationChannelId);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        builder.setContentTitle("僵尸粉检测");
        
        if (isRunning) {
            builder.setContentText("已测 " + done + " / " + total + "，发现 " + deadCount + " 僵尸粉");
            builder.setProgress(total, done, false);
            builder.setOngoing(true);
        } else if (total > 0) {
            builder.setContentText("已暂停：已测 " + done + "，发现 " + deadCount + " 僵尸粉");
            builder.setProgress(0, 0, false);
            builder.setOngoing(false);
        } else {
            nm.cancel(notificationId);
            return;
        }
        nm.notify(notificationId, builder.build());
    } catch (Throwable t) {}
}

void clearNotification() {
    try {
        Context ctx = getNotifyContext();
        if (ctx == null) return;
        android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notificationId);
    } catch (Throwable t) {}
}

// ================= 核心发包逻辑 =================
boolean probeSceneActive(Object scene) {
    synchronized (probeLock) {
        return scene != null && probeWxidMap.containsKey(scene);
    }
}

void finishProbeScene(Object scene) {
    synchronized (probeLock) {
        if (scene != null && probeWxidMap.containsKey(scene)) {
            String wxid = safeToStr(probeWxidMap.get(scene));
            probeWxidMap.remove(scene);
            probeNameMap.remove(scene);
            probeIndexMap.remove(scene);
            if (wxid.length() > 0) probeRetryMap.remove(wxid);
            if (inFlightProbeCount > 0) inFlightProbeCount--;
        }
    }
    saveProgress();
    refreshStats();
    updateNotification();
}

void dispatchChecker() {
    if (!isRunning) return;
    // 兜底：长时间无 inflight 但有数据，打印日志便于排查
    if (inFlightProbeCount == 0 && !pendingList.isEmpty()) {
        addLog("⏱️ 调度兜底触发");
        dispatchHandler.post(new Runnable() { public void run() { triggerNextTarget(); }});
    }
    if (isRunning) {
        uiHandler.postDelayed(new Runnable() { public void run() { dispatchChecker(); }}, 10000);
    }
}

void triggerNextTarget() {
    if (!isRunning) return;
    dispatchNextProbe();
}

void dispatchNextProbe() {
    if (!isRunning) return;

    // ===== 批次内连续发包：填满并发槽位为止 =====
    int dispatched = 0;
    while (isRunning && inFlightProbeCount < runtimeMaxInflight && !pendingList.isEmpty()) {
        
        // 过滤跳过
        while (!pendingList.isEmpty() && isFilterWxid(safeToStr(pendingList.get(0)))) {
            pendingList.remove(0);
            dispatched++;
        }
        if (pendingList.isEmpty()) break;
        
        if (dispatched > 0) {
            saveProgress();
            addLog("🚫 已跳过过滤好友 " + dispatched + " 位");
            refreshStats();
        }

        final String wxid = (String) pendingList.remove(0);
        final String name = getFriendDisplaySafe(wxid);
        dispatchedBatchCount++;
        final int probeIndex = dispatchedBatchCount;

        try {
            final Object myScene = buildRemittanceScene(wxid);
            if (myScene == null) {
                addLog("❌ " + name + " -> 适配失败，未找到可用发包入口");
                saveProgress();
                continue;
            }
            synchronized (probeLock) {
                probeWxidMap.put(myScene, wxid);
                probeNameMap.put(myScene, name);
                probeIndexMap.put(myScene, new Integer(probeIndex));
                inFlightProbeCount++;
            }
            setProbeStatus("▶️ 正在静默核验: " + name + "（并发 " + inFlightProbeCount + "/" + runtimeMaxInflight + "）");
            if (!dispatchRemittanceScene(myScene)) {
                addLog("❌ " + name + " -> 发包失败，已跳过");
                finishProbeScene(myScene);
                continue;
            }
            // 超时重试
            dispatchHandler.postDelayed(new Runnable() { public void run() {
                if (isRunning && probeSceneActive(myScene)) {
                    int retryCount = 0;
                    synchronized (probeLock) {
                        Integer rc = (Integer) probeRetryMap.get(wxid);
                        retryCount = (rc == null) ? 0 : rc.intValue();
                    }
                    if (retryCount < runtimeMaxRetry) {
                        synchronized (probeLock) {
                            probeRetryMap.put(wxid, new Integer(retryCount + 1));
                            probeWxidMap.remove(myScene);
                            probeNameMap.remove(myScene);
                            probeIndexMap.remove(myScene);
                            if (inFlightProbeCount > 0) inFlightProbeCount--;
                        }
                        addLog("🔄 [" + probeIndex + "] " + name + " -> ⏳ 超时重试 [" + (retryCount + 1) + "/" + runtimeMaxRetry + "]");
                        pendingList.add(0, wxid);
                        saveProgress();
                        refreshStats();
                        dispatchHandler.post(new Runnable() { public void run() { triggerNextTarget(); }});
                    } else {
                        addLog("⚠️ [" + probeIndex + "] " + name + " -> ⏳ 超时放弃（已重试" + retryCount + "次）");
                        finishProbeScene(myScene);
                        dispatchHandler.post(new Runnable() { public void run() { triggerNextTarget(); }});
                    }
                }
            }}, runtimeTimeoutMs);
        } catch (Throwable t) {
            addLog("❌ " + name + " -> 异常跳过: " + t.getMessage());
            saveProgress();
        }
    }

    // 检查是否跑完
    if (pendingList.isEmpty() && inFlightProbeCount <= 0) {
        isRunning = false; saveProgress();
        releaseZombieWakeLock();
        updateNotification();
        setProbeStatus("🎉 状态: 全部检测彻底完成！");
        addLog("🎉 队列为空，扫雷结束！");
    }
}

// 调度下一个
void scheduleNextAfterCallback() {
    if (!isRunning) return; 
    
    int minD = runtimeMinDelay; int maxD = runtimeMaxDelay;
    if (minD < 0) minD = 0;
    if (maxD < minD) maxD = minD;
    
    int delayMs = (minD + random.nextInt(maxD - minD + 1)) * 1000;
    if (delayMs > 0) {
        dispatchHandler.postDelayed(new Runnable() { public void run() {
            triggerNextTarget();
        }}, delayMs);
    } else {
        dispatchHandler.post(new Runnable() { public void run() { triggerNextTarget(); }});
    }
}

// ================= 网络回包拦截 (极致单行防崩版) =================
void onLoad() {
    try {
        Class q0Class = resolveRemittanceSceneClass();
        Method onGYNetEndMethod = resolveOnGYNetEndMethod(q0Class);
        if (q0Class == null || onGYNetEndMethod == null) {
            addLog("僵尸粉检测: 未定位到回包方法，宿主版本=" + hostVerName + " (" + hostVerCode + ")");
            return;
        }
        onGYNetEndMethod.setAccessible(true);
        sceneEndHooks = XposedBridge.hookMethod(onGYNetEndMethod, new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                Object[] args = param.args;
                if (args != null && args.length == 3) {
                    String errCodeStr = String.valueOf(args[0]);
                    String errMsg = String.valueOf(args[1]);
                    
                    final Object sceneObj = param.thisObject;
                    String mappedWxid = "";
                    String mappedName = "";
                    int mappedIndex = 0;
                    synchronized (probeLock) {
                        mappedWxid = safeToStr(probeWxidMap.get(sceneObj));
                        mappedName = safeToStr(probeNameMap.get(sceneObj));
                        try { mappedIndex = ((Integer) probeIndexMap.get(sceneObj)).intValue(); } catch (Throwable ignore) {}
                    }
                    final String targetWxid = mappedWxid;
                    final String targetName = mappedName;
                    final int targetIndex = mappedIndex;
                    
                    if (targetWxid.length() > 0) {
                        checkedTotalCount++;
                        
                        if (!"0".equals(errCodeStr) && (errMsg.contains("不是收款方好友") || errMsg.contains("拒绝接收") || errMsg.contains("请确认"))) {
                            deadList.add(targetWxid);
                            
                            new Thread(new Runnable() {
                                public void run() {
                                    try { Thread.sleep(500); } catch (Throwable ignore) {}
                                    boolean tagSuccess = false;
                                    try { tagSuccess = applyZombieLabel(targetWxid); } catch (Throwable t) { setLabelError("后台打标异常: " + t.getMessage()); }
                                    String tagError = lastLabelError;
                                    boolean deleteEnabled = autoDeleteEnabled;
                                    addLog("🚫 [" + targetIndex + "] " + targetName + " -> 💀 僵尸粉" + (tagSuccess ? "(已标签)" : "(标签失败:" + tagError + ")") + (deleteEnabled ? "(等待删除)" : ""));
                                    if (deleteEnabled) {
                                        try { Thread.sleep(runtimeAutoDeleteDelaySec * 1000L); } catch (Throwable ignore2) {}
                                        boolean deleteSuccess = deleteZombieFriend(targetWxid);
                                        addLog("🗑️ " + targetName + " -> " + (deleteSuccess ? "已删除好友" : "删除失败"));
                                    }
                                    if (!tagSuccess) {
                                        try {
                                            if (applyZombieLabel(targetWxid)) addLog("🏷️ " + targetName + " -> 标签重试成功");
                                            else addLog("🏷️ " + targetName + " -> 标签重试失败: " + lastLabelError);
                                        } catch (Throwable t2) {
                                            addLog("🏷️ " + targetName + " -> 标签重试异常: " + t2.getMessage());
                                        }
                                    }
                                }
                            }).start();

                        } else {
                            addLog("🟢 [" + targetIndex + "] " + targetName + " -> 正常好友");
                        }
                        
                        finishProbeScene(sceneObj);
                        dispatchHandler.post(new Runnable() { public void run() { scheduleNextAfterCallback(); }});
                    }
                }
            }
        });
        addLog("僵尸粉检测适配完成: " + q0Class.getName() + " / " + onGYNetEndMethod.getName() + " / 微信 " + hostVerName);
    } catch (Throwable t) {
        addLog("僵尸粉检测适配失败: " + t.getMessage());
    }
}

// ================= 生命周期与指令拦截 =================
void onUnload() {
    releaseZombieWakeLock();
    clearNotification();
    if (sceneEndHooks != null) {
        try {
            if (sceneEndHooks instanceof java.util.Set) {
                java.util.Iterator it = ((java.util.Set) sceneEndHooks).iterator();
                while (it.hasNext()) {
                    Object hook = it.next();
                    XposedHelpers.callMethod(hook, "unhook");
                }
            } else {
                XposedHelpers.callMethod(sceneEndHooks, "unhook");
            }
        } catch (Throwable t) {}
        sceneEndHooks = null;
    }
}

boolean onClickSendBtn(String text) {
    try {
        if (text != null && text.equals("僵尸检测")) {
            showJayConsole();
            return true; // 拦截发送，避免刷屏
        }
    } catch (Throwable t) {}
    return false;
}

void openSettings() {
    try {
        showJayConsole();
    } catch (Throwable t) {
        addLog("打开插件设置失败: " + t.getMessage());
        try { toast("打开设置失败: " + t.getMessage()); } catch (Throwable ignore) {}
    }
}
