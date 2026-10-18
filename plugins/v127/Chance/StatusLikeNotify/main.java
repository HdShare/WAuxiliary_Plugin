// 状态通知与自动点赞（微信状态 / 24小时状态）
// 已取证版本：微信 8.0.79 (versionCode 3200)
// 关键证据（来自当前 APK 反编译）：
//   1) com.tencent.wechat.aff.status.StatusStorageManager
//      - selectStatusInfoWithUsername(String,int,int,long)  微信自身实参: (wxid, 0, 1, now - 86400000)
//   2) com.tencent.wechat.aff.status.StatusLogicCommentManager
//      - performSelfDoLike 仅为本地记录入口，禁止单独用于网络点赞。
// 网络证据：nr4.j 构造器、lr4.k.i(ZZ)、yr4.z1.toJSON/op：
// textstatuslike CGI=4099，topic_id/text_status_id/username/delete_flag=0/type=1/clientId。
//      - isLikeTextStatus(statusId)             已点赞判定（用于去重）
//   3) 状态对象 z56.cz0 有稳定方法 getStatusId()/getIconId()/getTopicId()，字段名为混淆名，
//      因此本插件按"字段类型+已知Get方法"读取，不硬编码混淆字段名。

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import android.app.Activity;

// ================= 配置键 =================
String K_LIKE_ON = "likeOn";
String K_NOTIFY_ON = "notifyOn";
String K_SYSTEM_NOTIFY = "systemNotify";
String K_TOAST_NOTIFY = "toastNotify";
String K_INTERVAL = "interval";
String K_DELAY_MIN = "delayMin";
String K_DELAY_MAX = "delayMax";
String K_LIKE_GAP = "likeGap";
String K_LIKE_HOURLY = "likeHourly";
String K_NOTIFY_GAP = "notifyGap";
String K_LIKE_USERS = "likeUsers";
String K_NOTIFY_USERS = "notifyUsers";
String K_LIKE_TXT = "likeText";
String K_NOTIFY_TXT = "notifyText";
String K_LOG_HOURS = "logCleanHours";
String K_LOG_CLEAN_AT = "logCleanAt";
Object logFileLock = new Object();
java.util.Timer logCleanTimer = null;

// ================= 已取证常量 =================
String CLS_STORAGE = "com.tencent.wechat.aff.status.StatusStorageManager";
String CLS_COMMENT = "com.tencent.wechat.aff.status.StatusLogicCommentManager";
String CLS_CZ0 = "z56.cz0";
String M_SELECT_USER = "selectStatusInfoWithUsername";
String M_IS_LIKED = "isLikeTextStatus";
String M_DO_LIKE = "performSelfDoLike";

// ================= 运行状态 =================
boolean running = false;
boolean ticking = false;
boolean jobScheduled = false;
boolean apiReady = false;
boolean apiProbed = false;
long lastLikeAt = 0L;
long lastNotifyAt = 0L;
List likeTimes = new ArrayList();
List notifyTimes = new ArrayList();
List likeQueue = new ArrayList();
List notifyQueue = new ArrayList();
int notifySuccessCount = 0;
int notifyFailCount = 0;
Map seenStatus = new HashMap();
Map seenNotify = new HashMap();
String lastErr = "";
String TAG = "状态监控";
Object plusHandle = null;
Object msgHandle = null;

// 已解析的微信状态接口成员（避免动态变量，便于排查与复用）
Method mSelectUser = null;
Method mIsLiked = null;
Method mDoLike = null;
Map pendingLikes = java.util.Collections.synchronizedMap(new HashMap());
boolean likeUnloaded = false;
Object friendCacheLock = new Object();
Map friendCacheById = null;
boolean friendCacheLoading = false;

// StatusStorageManager / StatusLogicCommentManager 均为单例，且目标方法都是实例方法
Object storageInst() {
    try { return callStatic(CLS_STORAGE, "getInstance", new Class[]{}, new Object[]{}); } catch (Throwable t) { return null; }
}

Object commentInst() {
    try { return callStatic(CLS_COMMENT, "getInstance", new Class[]{}, new Object[]{}); } catch (Throwable t) { return null; }
}

// ================= 生命周期 =================
void onLoad() {
    try {
        log("插件加载，微信版本 " + hostVerName + "(" + hostVerCode + ")，进程 " + processName);
        if (!isMainProcess) { log("非主进程，跳过"); return; }
        startLogCleanup();
        if (hostVerCode != 3200) log("注意：当前版本未经完整验证，请留意运行日志（已取证版本 8.0.79/3200）");
        if (!probeApi()) log("微信状态接口不可用，自动点赞与监控将保持关闭");
        registerMenus();
        preloadFriendCache();
        startLoop();
    } catch (Throwable t) {
        log("onLoad 异常: " + t);
    }
}

void preloadFriendCache() {
    synchronized (friendCacheLock) {
        if (friendCacheLoading) return;
        friendCacheLoading = true;
    }
    new Thread(new Runnable() {
        public void run() {
            try {
                refreshFriendCache();
                log("好友名单缓存预加载完成，共 " + friendCacheById.size() + " 人");
            } catch (Throwable t) {
                log("好友名单缓存预加载失败: " + t);
            } finally {
                synchronized (friendCacheLock) { friendCacheLoading = false; }
            }
        }
    }).start();
}

Map refreshFriendCache() {
    Map fresh = new java.util.LinkedHashMap();
    Object rawObject = getFriendListInfo();
    if (rawObject instanceof List) {
        for (Object o : (List) rawObject) {
            if (!(o instanceof Map)) continue;
            Map source = (Map) o;
            String wxid = cleanWxid(source.get("wxid"));
            if (wxid.length() > 0) fresh.put(wxid, new HashMap(source));
        }
    }
    synchronized (friendCacheLock) {
        friendCacheById = fresh;
    }
    return fresh;
}

Map getFriendCache() {
    synchronized (friendCacheLock) {
        if (friendCacheById != null && friendCacheById.size() > 0) return new HashMap(friendCacheById);
    }
    return refreshFriendCache();
}

void onUnload() {
    likeUnloaded = true;
    pendingLikes.clear();
    if (logCleanTimer != null) { logCleanTimer.cancel(); logCleanTimer = null; }
    running = false;
    removeMenu(plusHandle);
    removeMenu(msgHandle);
    plusHandle = null;
    msgHandle = null;
    log("插件已卸载");
}

void openSettings() {
    menuMain();
}

// ================= 微信状态接口（反射，全部基于已取证 descriptor） =================
boolean probeApi() {
    if (apiReady) return true;
    if (apiProbed) return false;
    apiProbed = true;
    try {
        Class c1 = reflectFindClass(CLS_STORAGE);
        Class c2 = reflectFindClass(CLS_COMMENT);
        if (c1 == null || c2 == null) { log("未找到微信状态接口类，自动禁用"); return false; }
        c1.getMethod(M_SELECT_USER, new Class[]{String.class, Integer.TYPE, Integer.TYPE, Long.TYPE});
        c2.getMethod(M_DO_LIKE, new Class[]{String.class, Boolean.TYPE});
        c2.getMethod(M_IS_LIKED, new Class[]{String.class});
        apiReady = true;
        log("微信状态接口就绪");
        return true;
    } catch (Throwable t) {
        lastErr = String.valueOf(t);
        log("接口探测失败，自动禁用: " + t);
        apiReady = false;
        return false;
    }
}

// 读取某好友最近 24 小时内的最新微信状态；无状态返回 null
Map statusGet(String wxid) {
    if (!probeApi()) return null;
    try {
        if (mSelectUser == null) {
            Class c1 = reflectFindClass(CLS_STORAGE);
            mSelectUser = c1.getMethod(M_SELECT_USER, new Class[]{String.class, Integer.TYPE, Integer.TYPE, Long.TYPE});
            mSelectUser.setAccessible(true);
        }
        Object storage = storageInst();
        if (storage == null) { lastErr = "StatusStorageManager 实例不可用"; return null; }
        long now = System.currentTimeMillis();
        Object res = mSelectUser.invoke(storage,
                new Object[]{wxid, Integer.valueOf(0), Integer.valueOf(1), Long.valueOf(now - 86400000L)});
        if (res == null) return null;
        if (!boolField(res, "d")) return null;
        List list = listField(res);
        if (list == null || list.isEmpty()) return null;
        Object info = list.get(0);
        if (info == null) return null;
        String sid = getStr(info, "getStatusId");
        if (sid == null || sid.length() == 0) sid = getStr(info, "e");
        String icon = getStr(info, "getIconId");
        String topic = getStr(info, "getTopicId");
        Map m = new HashMap();
        m.put("statusId", sid == null ? "" : sid);
        m.put("iconId", icon == null ? "" : icon);
        m.put("topicId", topic == null ? "" : topic);
        return m;
    } catch (Throwable t) {
        lastErr = String.valueOf(t);
        return null;
    }
}

// 返回 1=微信已确认点赞，0=确认未点赞，-1=查询失败或结果不可判定。
int statusLikeState(String statusId) {
    if (statusId == null || statusId.length() == 0) return -1;
    try {
        if (!probeApi()) return -1;
        if (mIsLiked == null) {
            Class c2 = reflectFindClass(CLS_COMMENT);
            mIsLiked = c2.getMethod(M_IS_LIKED, new Class[]{String.class});
            mIsLiked.setAccessible(true);
        }
        Object cm = commentInst();
        if (cm == null) { lastErr = "StatusLogicCommentManager 实例不可用"; return -1; }
        Object res = mIsLiked.invoke(cm, new Object[]{statusId});
        Object value = fieldOf(res, "d");
        if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? 1 : 0;
        if (value instanceof Number) return ((Number) value).intValue() != 0 ? 1 : 0;
        lastErr = "isLikeTextStatus 返回对象没有可识别的 d 字段";
        return -1;
    } catch (Throwable t) {
        lastErr = String.valueOf(t);
        return -1;
    }
}

boolean statusIsLiked(String statusId) {
    return statusLikeState(statusId) == 1;
}

// 仅记录本插件通过微信原生点赞场景并经状态查询确认的结果。
boolean networkLikeConfirmed(String wxid, String sid) {
    return sid.equals(getString("networkLikeV136_" + wxid, ""));
}

// 直接复用当前版本微信自己的 NetSceneTextStatusLike，避免通用 JSON 发包
// 将 protobuf 字段名误当作数字解析，也确保 BaseRequest、clientId 和字段类型
// 与微信原生点赞路径一致。以下类名、构造器和 dispatcher 方法均已在当前 APK 取证。
boolean submitNativeLike(String topic, String sid, String wxid) {
    try {
        Class requestClass = reflectFindClass("nr4.j");
        Class sceneClass = reflectFindClass("com.tencent.mm.modelbase.n1");
        Class dispatcherClass = reflectFindClass("com.tencent.mm.modelbase.s1");
        if (requestClass == null || sceneClass == null || dispatcherClass == null) {
            lastErr = "微信原生点赞请求类或 dispatcher 不可用";
            return false;
        }
        Constructor ctor = requestClass.getConstructor(new Class[]{
            String.class, String.class, String.class, Integer.TYPE, Integer.TYPE
        });
        ctor.setAccessible(true);
        Object request = ctor.newInstance(new Object[]{
            topic, sid, wxid, Integer.valueOf(0), Integer.valueOf(1)
        });
        Object dispatcher = callStatic("pp0.i1", "e", new Class[]{}, new Object[]{});
        if (dispatcher == null) {
            lastErr = "微信网络 dispatcher 不可用";
            return false;
        }
        // 取证显示 g() 内部会继续调用 i()，再把场景投递到微信自己的 worker
        // handler；这里必须直接调用，不等待 UI Activity，才能在后台轮询时工作。
        Method send = dispatcherClass.getMethod("g", new Class[]{sceneClass});
        send.setAccessible(true);
        Object result = send.invoke(dispatcher, new Object[]{request});
        if (result instanceof Boolean) {
            boolean accepted = ((Boolean) result).booleanValue();
            if (!accepted) lastErr = "微信 NetSceneQueue 拒绝提交点赞场景";
            return accepted;
        }
        lastErr = "微信原生点赞请求返回值不可识别";
        return false;
    } catch (Throwable t) {
        lastErr = String.valueOf(t);
        return false;
    }
}

void verifyNativeLike(final String wxid, final String sid, final String taskKey, final int round) {
    if (likeUnloaded || pendingLikes.get(taskKey) == null) return;
    try {
        int state = statusLikeState(sid);
        if (state == 1) {
            pendingLikes.remove(taskKey);
            putString("networkLikeV136_" + wxid, sid);
            lastLikeAt = System.currentTimeMillis();
            log("微信原生点赞已确认: " + name(wxid) + " [" + sid + "]");
            return;
        }
        if (state < 0) log("点赞结果查询失败，第 " + round + " 次复核: " + lastErr);
        if (round >= 3) {
            pendingLikes.remove(taskKey);
            lastErr = state < 0 ? lastErr : "微信原生请求已提交但点赞状态仍未确认";
            log("点赞结果未知: " + name(wxid) + " [" + sid + "]；未写入成功标志");
            return;
        }
        delay(round == 1 ? 12000L : 25000L, new Runnable() {
            public void run() { verifyNativeLike(wxid, sid, taskKey, round + 1); }
        });
    } catch (Throwable t) {
        pendingLikes.remove(taskKey);
        lastErr = String.valueOf(t);
        log("点赞结果复核异常: " + name(wxid) + " [" + sid + "] " + t);
    }
}

void submitNetworkLike(final String wxid, final String sid) {
    final String taskKey = wxid + "|" + sid;
    synchronized (pendingLikes) {
        if (pendingLikes.containsKey(taskKey)) return;
        pendingLikes.put(taskKey, Long.valueOf(System.currentTimeMillis()));
    }
    new Thread(new Runnable() {
        public void run() {
            try {
                if (likeUnloaded) { pendingLikes.remove(taskKey); return; }
                if (!getBoolean(K_LIKE_ON, true) || !inList(true, wxid)) {
                    pendingLikes.remove(taskKey); return;
                }
                if (hostVerCode != 3200) throw new IllegalStateException("网络点赞仅对已取证的 8.0.79/3200 开放");
                Map st = statusGet(wxid);
                if (st == null || !sid.equals(String.valueOf(st.get("statusId")))) {
                    throw new IllegalStateException("状态已变化或不可读取，取消旧状态请求");
                }
                int likedState = statusLikeState(sid);
                if (likedState == 1) {
                    pendingLikes.remove(taskKey);
                    log("查询确认已经点赞，跳过请求: " + name(wxid) + " [" + sid + "]");
                    return;
                }
                if (likedState < 0) throw new IllegalStateException("无法确认当前点赞状态，取消请求: " + lastErr);
                String topic = String.valueOf(st.get("topicId"));
                if (topic.length() == 0 || "null".equals(topic)) throw new IllegalStateException("缺少 topicId，不猜测请求参数");
                // 不再调用通用 sendProtobufPacket：该路径在本机曾把 topic_id
                // 报为数字解析错误。微信自身构造器会生成完整 protobuf 请求。
                boolean submitted = submitNativeLike(topic, sid, wxid);
                if (!submitted) {
                    pendingLikes.remove(taskKey);
                    // 只有提交失败才允许下一轮重新尝试；提交后结果未知时仍不自动重发。
                    seenStatus.remove("L|" + wxid + "|" + sid);
                    log("微信原生点赞请求未提交: " + name(wxid) + " [" + sid + "] " + lastErr);
                    return;
                }
                likeTimes.add(Long.valueOf(System.currentTimeMillis()));
                // 请求提交后必须以微信点赞状态查询确认，不能把 dispatcher 返回值当成成功。
                lastLikeAt = System.currentTimeMillis();
                log("微信原生点赞请求已提交，等待状态确认: " + name(wxid) + " [" + sid + "]");
                delay(8000L, new Runnable() {
                    public void run() { verifyNativeLike(wxid, sid, taskKey, 1); }
                });
            } catch (Throwable t) {
                pendingLikes.remove(taskKey);
                seenStatus.remove("L|" + wxid + "|" + sid);
                lastErr = String.valueOf(t);
                log("点赞请求异常: " + name(wxid) + " [" + sid + "] " + t);
            }
        }
    }).start();
}

// ================= 轮询与限流 =================
void startLoop() {
    if (running) return;
    if (!probeApi()) { toast("微信状态接口不可用，已停止"); return; }
    running = true;
    log("启动状态监控轮询，间隔 " + getInt(K_INTERVAL, 300) + " 秒");
    scheduleNext();
}

void stopLoop() {
    running = false;
    log("已停止状态监控轮询");
}

void scheduleNext() {
    if (!running) return;
    int sec = getInt(K_INTERVAL, 300);
    if (sec < 30) sec = 30;
    delay((long) sec * 1000L, new Runnable() {
        public void run() {
            safeTick();
            scheduleNext();
        }
    });
}

void safeTick() {
    if (ticking) return;
    ticking = true;
    try {
        sweep();
    } catch (Throwable t) {
        log("轮询异常（已忽略，不影响微信）: " + t);
    } finally {
        ticking = false;
    }
}

void sweep() {
    if (!probeApi()) return;
    boolean likeOn = getBoolean(K_LIKE_ON, true);
    boolean notifyOn = getBoolean(K_NOTIFY_ON, true);
    List ids = new ArrayList();
    ids.addAll(getList(K_LIKE_USERS));
    ids.addAll(getList(K_NOTIFY_USERS));
    Set uniq = new LinkedHashSet();
    uniq.addAll(ids);
    long now = System.currentTimeMillis();
    int n = 0;
    for (Object o : uniq) {
        String wxid = String.valueOf(o);
        if (wxid == null || wxid.length() == 0 || "null".equals(wxid)) continue;
        try {
            handleFriend(wxid, likeOn, notifyOn, now);
            n++;
        } catch (Throwable t) {
            log("处理 " + wxid + " 异常: " + t);
        }
    }
    cleanup(seenStatus, now, 48L * 3600000L);
    cleanup(seenNotify, now, 48L * 3600000L);
    if (n > 0) log("本轮检查 " + n + " 个好友");
}

void handleFriend(String wxid, boolean likeOn, boolean notifyOn, long now) {
    boolean wantLike = likeOn && inList(true, wxid);
    boolean wantNotify = notifyOn && inList(false, wxid);
    if (!wantLike && !wantNotify) return;
    Map st = statusGet(wxid);
    if (st == null) return;
    String sid = String.valueOf(st.get("statusId"));
    if (sid == null || sid.length() == 0 || "null".equals(sid)) return;
    if (wantLike) {
        String key = "L|" + wxid + "|" + sid;
        if (!seenStatus.containsKey(key)) {
            int likedState = statusLikeState(sid);
            if (likedState == 1) {
                seenStatus.put(key, Long.valueOf(now));
                log("查询确认已经点赞，跳过: " + name(wxid) + " [" + sid + "]");
            } else if (likedState == 0) {
                seenStatus.put(key, Long.valueOf(now));
                scheduleLike(wxid, sid, key);
            } else {
                log("无法确认是否已点赞，暂不发送: " + name(wxid) + " [" + sid + "] " + lastErr);
            }
        }
    }
    if (wantNotify) {
        String key2 = "N|" + wxid + "|" + sid;
        if (!notifySeenPersisted(wxid, sid, now) && !seenNotify.containsKey(key2)) {
            seenNotify.put(key2, Long.valueOf(now));
            putLong(notifySeenKey(wxid, sid), now);
            scheduleNotify(wxid, st);
        }
    }
}

String notifySeenKey(String wxid, String sid) {
    return "notifySeenV155_" + wxid + "_" + sid;
}

boolean notifySeenPersisted(String wxid, String sid, long now) {
    long seenAt = getLong(notifySeenKey(wxid, sid), 0L);
    return seenAt > 0L && now - seenAt < 48L * 3600000L;
}

void scheduleLike(String wxid, String sid, String key) {
    int lo = getInt(K_DELAY_MIN, 5);
    int hi = getInt(K_DELAY_MAX, 30);
    if (lo < 0) lo = 0;
    if (hi < lo) hi = lo;
    int span = hi - lo + 1;
    long wait = (long) (lo + (int) (Math.random() * span)) * 1000L;
    Map job = new HashMap();
    job.put("wxid", wxid);
    job.put("sid", sid);
    job.put("key", key);
    job.put("at", Long.valueOf(System.currentTimeMillis() + wait));
    likeQueue.add(job);
    log("计划点赞 " + name(wxid) + "，随机延时 " + (wait / 1000) + " 秒");
    ensureJobRunner();
}

void scheduleNotify(String wxid, Map st) {
    Map job = new HashMap();
    job.put("wxid", wxid);
    job.put("st", st);
    job.put("at", Long.valueOf(System.currentTimeMillis()));
    notifyQueue.add(job);
    ensureJobRunner();
}

void ensureJobRunner() {
    if (jobScheduled) return;
    jobScheduled = true;
    delay(1000L, new Runnable() {
        public void run() {
            jobScheduled = false;
            try { runJobs(); } catch (Throwable t) { log("任务执行异常: " + t); }
        }
    });
}

void runJobs() {
    long now = System.currentTimeMillis();
    if (!likeQueue.isEmpty()) {
        List dueLikes = new ArrayList(likeQueue);
        likeQueue = new ArrayList();
        for (Object o : dueLikes) {
            Map j = (Map) o;
            long at = ((Long) j.get("at")).longValue();
            if (at <= now) {
                doLike(String.valueOf(j.get("wxid")), String.valueOf(j.get("sid")), String.valueOf(j.get("key")));
            } else {
                likeQueue.add(j);
            }
        }
    }
    if (!notifyQueue.isEmpty()) {
        List remain2 = new ArrayList();
        for (Object o : new ArrayList(notifyQueue)) {
            Map j = (Map) o;
            long at = ((Long) j.get("at")).longValue();
            if (at <= now) {
                doNotify(String.valueOf(j.get("wxid")), (Map) j.get("st"));
            } else {
                remain2.add(j);
            }
        }
        notifyQueue = remain2;
    }
    if (!likeQueue.isEmpty() || !notifyQueue.isEmpty()) ensureJobRunner();
}

void doLike(String wxid, String sid, String key) {
    try {
        if (!getBoolean(K_LIKE_ON, true)) return;
        if (!inList(true, wxid)) { log("点赞名单已变更，取消: " + name(wxid)); return; }
        if (!allow("like")) {
            requeue(likeQueue, wxid, sid, 60000L);
            log("点赞限流中，稍后重试: " + name(wxid));
            return;
        }
        submitNetworkLike(wxid, sid);
    } catch (Throwable t) {
        log("点赞异常: " + t);
    }
}

void requeue(List queue, String wxid, String sid, long delayMs) {
    Map job = new HashMap();
    job.put("wxid", wxid);
    job.put("sid", sid);
    job.put("key", "R|" + wxid + "|" + sid);
    job.put("at", Long.valueOf(System.currentTimeMillis() + delayMs));
    queue.add(job);
    ensureJobRunner();
}

void doNotify(String wxid, Map st) {
    try {
        if (!getBoolean(K_NOTIFY_ON, true)) return;
        if (!inList(false, wxid)) return;
        if (!allow("notify")) {
            Map job = new HashMap();
            job.put("wxid", wxid);
            job.put("st", st);
            job.put("at", Long.valueOf(System.currentTimeMillis() + 30000L));
            notifyQueue.add(job);
            ensureJobRunner();
            return;
        }
        long t = System.currentTimeMillis();
        lastNotifyAt = t;
        notifyTimes.add(Long.valueOf(t));
        String msg = "好友 " + name(wxid) + " 发布了新微信状态";
        log("通知: " + msg + " [" + st.get("statusId") + "]");
        if (showNotice("微信状态提醒", msg)) notifySuccessCount++;
        else notifyFailCount++;
    } catch (Throwable e) {
        notifyFailCount++;
        log("通知异常: " + e);
    }
}

boolean allow(String kind) {
    long now = System.currentTimeMillis();
    if ("like".equals(kind)) {
        long gap = (long) getInt(K_LIKE_GAP, 30) * 1000L;
        if (gap > 0 && now - lastLikeAt < gap) return false;
        prune(likeTimes, now, 3600000L);
        int cap = getInt(K_LIKE_HOURLY, 20);
        if (cap > 0 && likeTimes.size() >= cap) return false;
        return true;
    }
    long gap2 = (long) getInt(K_NOTIFY_GAP, 5) * 1000L;
    if (gap2 > 0 && now - lastNotifyAt < gap2) return false;
    prune(notifyTimes, now, 60000L);
    return notifyTimes.size() < 10;
}

void prune(List list, long now, long windowMs) {
    List keep = new ArrayList();
    for (Object o : list) {
        if (o instanceof Long && now - ((Long) o).longValue() < windowMs) keep.add(o);
    }
    list.clear();
    list.addAll(keep);
}

void cleanup(Map map, long now, long windowMs) {
    List bad = new ArrayList();
    for (Object o : map.keySet()) {
        Object v = map.get(o);
        if (v instanceof Long && now - ((Long) v).longValue() > windowMs) bad.add(o);
    }
    for (Object o : bad) map.remove(o);
}

// ================= 通知 =================
boolean showNotice(String title, String msg) {
    try {
        boolean systemOn = getBoolean(K_SYSTEM_NOTIFY, true);
        boolean toastOn = getBoolean(K_TOAST_NOTIFY, true);
        boolean success = false;
        if (systemOn) {
            try {
                notify(title, msg);
                success = true;
            } catch (Throwable t) {
                log("系统通知发送失败: " + t);
            }
        }
        if (toastOn) {
            if (isForeground()) { toast("[" + title + "] " + msg); success = true; }
            else log("Toast提醒已启用，但微信不在前台，未显示 Toast");
        }
        if (!systemOn && !toastOn) log("系统通知和 Toast 提醒均已关闭");
        return success;
    } catch (Throwable t) {
        log("发送通知失败: " + t);
        return false;
    }
}

boolean isForeground() {
    try {
        return getTopActivity() != null;
    } catch (Throwable t) {
        return false;
    }
}

// ================= 名单管理 =================
String listName(boolean like) { return like ? "点赞名单" : "通知名单"; }
String attrKey(boolean like) { return like ? K_LIKE_USERS : K_NOTIFY_USERS; }
String textKey(boolean like) { return like ? K_LIKE_TXT : K_NOTIFY_TXT; }

List getList(String key) {
    try {
        Set s = getStringSet(key, new LinkedHashSet());
        List l = new ArrayList();
        if (s != null) l.addAll(s);
        return l;
    } catch (Throwable t) {
        return new ArrayList();
    }
}

boolean inList(boolean like, String wxid) {
    if (wxid == null) return false;
    List l = getList(attrKey(like));
    return l.contains(wxid);
}

void addTo(boolean like, String wxid) {
    if (wxid == null) return;
    wxid = wxid.trim();
    if (wxid.length() == 0) return;
    List l = getList(attrKey(like));
    if (!l.contains(wxid)) {
        l.add(wxid);
        putStringSet(attrKey(like), new LinkedHashSet(l));
        log("加入" + listName(like) + "：" + wxid);
    }
    String txt = getString(textKey(like), "");
    if (txt.indexOf(wxid) < 0) {
        if (txt.length() > 0 && !txt.endsWith("\n")) txt = txt + "\n";
        txt = txt + wxid;
        putString(textKey(like), txt);
    }
    resetSeen();
}

void removeFrom(boolean like, String wxid) {
    if (wxid == null) return;
    List l = getList(attrKey(like));
    l.remove(wxid);
    putStringSet(attrKey(like), new LinkedHashSet(l));
    String txt = getString(textKey(like), "");
    List keep = new ArrayList();
    String[] parts = txt.split("\n");
    for (int i = 0; i < parts.length; i++) {
        String ln = parts[i].trim();
        if (ln.length() == 0) continue;
        if (ln.equals(wxid)) continue;
        if (ln.endsWith(wxid)) continue;
        keep.add(ln);
    }
    putString(textKey(like), joinLines(keep));
    log("移出" + listName(like) + "：" + wxid);
    resetSeen();
}

void clearList(boolean like) {
    putStringSet(attrKey(like), new LinkedHashSet());
    putString(textKey(like), "");
    log("已清空" + listName(like));
    resetSeen();
}

void resetSeen() {
    seenStatus = new HashMap();
    seenNotify = new HashMap();
}

String joinLines(List l) {
    StringBuffer sb = new StringBuffer();
    for (Object o : l) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(String.valueOf(o));
    }
    return sb.toString();
}

String listSummary(boolean like) {
    List l = getList(attrKey(like));
    StringBuffer sb = new StringBuffer();
    sb.append("当前" + listName(like) + "中的具体好友：\n");
    int n = 0;
    for (Object o : l) {
        if (n >= 30) { sb.append("\n... 共 " + l.size() + " 人"); break; }
        String wxid = String.valueOf(o);
        sb.append((n + 1) + ". " + name(wxid) + "（wxid：" + wxid + "）\n");
        n++;
    }
    if (l.isEmpty()) sb.append("（当前没有具体好友）");
    return sb.toString();
}

void showFriendList(final boolean like) {
    final List members = getList(attrKey(like));
    if (members.isEmpty()) {
        showInfoReturn("查看" + listName(like) + "具体好友", "（当前没有具体好友）", "list", like);
        return;
    }
    final List choices = new ArrayList();
    for (int i = 0; i < members.size(); i++) {
        String wxid = String.valueOf(members.get(i));
        choices.add((i + 1) + ". " + name(wxid) + "（wxid：" + wxid + "）");
    }
    final Set initial = new LinkedHashSet();
    for (int i = 0; i < members.size(); i++) initial.add(Integer.valueOf(i));
    boolean shown = showModuleMultiChoiceDialog(
            "查看" + listName(like) + "具体好友",
            "共 " + members.size() + " 人；已默认全选，取消勾选后点击确定即可移除对应好友。",
            choices,
            initial,
            "center",
            new Consumer() {
                public void accept(Object result) {
                    if (result instanceof Set) {
                        Set selected = (Set) result;
                        LinkedHashSet keep = new LinkedHashSet();
                        for (Object item : selected) {
                            if (!(item instanceof Number)) continue;
                            int index = ((Number) item).intValue();
                            if (index >= 0 && index < members.size()) {
                                keep.add(String.valueOf(members.get(index)));
                            }
                        }
                        putStringSet(attrKey(like), keep);
                        putString(textKey(like), joinLines(new ArrayList(keep)));
                        resetSeen();
                        toast("已保存" + listName(like) + "，当前共 " + keep.size() + " 人");
                    }
                    returnToPage("list", like);
                }
            });
    if (!shown) toast("无法显示好友名单，请保持微信在前台后重试");
}

String name(String wxid) {
    try {
        String n = getFriendName(wxid);
        if (n != null && n.length() > 0) return n;
        String r = getFriendRemarkName(wxid);
        if (r != null && r.length() > 0) return r;
    } catch (Throwable t) {
    }
    return wxid;
}

// ================= 菜单 =================
void registerMenus() {
    try {
        plusHandle = null;
        msgHandle = registerMessageMenu("状态监控", new Consumer() {
            public void accept(Object b) { menuMain(); }
        });
        log("已注册消息菜单");
    } catch (Throwable t) {
        log("注册菜单失败（可继续用插件设置进入）: " + t);
    }
}

String onOff(boolean b) { return b ? "开" : "关"; }

// 仅在确认/操作完成后返回；取消不触发导航。
void returnToPage(final String page, final boolean like) {
    delay(180L, new Runnable() {
        public void run() {
            try {
                Activity act = getTopActivity();
                if (act == null) { toast("请回到微信前台后重新打开菜单"); return; }
                act.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if ("list".equals(page)) listMenu(like);
                        else if ("label".equals(page)) labelBatch(like);
                        else if ("log".equals(page)) logMenu();
                        else menuMain();
                    } catch (Throwable t) { log("返回上一级失败: " + t); }
                }
                });
            } catch (Throwable t) {
                log("调度返回上一级失败: " + t);
                toast("返回菜单失败，请重新打开插件菜单");
            }
        }
    });
}

void showInfoReturn(String title, String message, final String page, final boolean like) {
    showModuleConfirmDialog(title, message, new Consumer() {
        public void accept(Object result) {
            if (Boolean.TRUE.equals(result)) returnToPage(page, like);
        }
    });
}

// 子菜单使用内置多选弹窗承载，选择一项后点确定才执行；取消不执行。
// 公开单选接口没有按钮配置参数，不能借它强制显示确定按钮。
boolean showConfirmedMenu(final String title, final String summary, final List choices,
        final String parentPage, final boolean like, final Consumer action) {
    final List menuChoices = new ArrayList(choices);
    return showModuleMultiChoiceDialog(title, summary + "；选择一项进入；不勾选直接点确定返回上一级", menuChoices,
            new LinkedHashSet(), "bottom", new Consumer() {
        public void accept(Object result) {
            if (!(result instanceof Set)) {
                toast("未取得选择结果，已返回上一级");
                returnToPage(parentPage, like);
                return;
            }
            Set selected = (Set) result;
            if (selected.isEmpty()) {
                returnToPage(parentPage, like);
                return;
            }
            if (selected.size() == 1) {
                Object value = selected.iterator().next();
                if (value instanceof Number) {
                    int index = ((Number) value).intValue();
                    if (index >= 0 && index < choices.size()) {
                        try { action.accept(Integer.valueOf(index)); }
                        catch (Throwable t) {
                            log("子菜单操作失败: " + t);
                            toast("操作失败，已返回上一级");
                            returnToPage(parentPage, like);
                        }
                        return;
                    }
                }
            }
            toast("只能选择一项，未执行操作，已返回上一级");
            returnToPage(parentPage, like);
        }
    });
}

void menuMain() {
    List op = new ArrayList();
    op.add("自动点赞：" + onOff(getBoolean(K_LIKE_ON, true)));
    op.add("通知设置");
    op.add("轮询间隔：" + getInt(K_INTERVAL, 300) + " 秒");
    op.add("点赞随机延时：" + getInt(K_DELAY_MIN, 5) + "~" + getInt(K_DELAY_MAX, 30) + " 秒");
    op.add("名单设置");
    op.add("查看运行状态");
    op.add("查看/清空日志");
    op.add("恢复默认设置");
    showModuleChoiceDialog("状态通知与自动点赞", "选择操作", op, new Consumer() {
        public void accept(Object r) {
            try { menuAction(((Integer) r).intValue()); } catch (Throwable t) { log("菜单异常: " + t); }
        }
    });
}

void menuAction(int i) {
    if (i == 0) toggle(K_LIKE_ON, true);
    else if (i == 1) notificationMenu();
    else if (i == 2) askInt(K_INTERVAL, "轮询间隔（秒）", "建议 60~1800，最小 30", 300, 30);
    else if (i == 3) askDelayRange();
    else if (i == 4) listSettingsMenu();
    else if (i == 5) showStatus();
    else if (i == 6) logMenu();
    else if (i == 7) resetDefault();
    if (i == 0) returnToPage("main", false);
}

void notificationMenu() {
    List op = new ArrayList();
    op.add("自动通知：" + onOff(getBoolean(K_NOTIFY_ON, true)));
    op.add("系统通知：" + onOff(getBoolean(K_SYSTEM_NOTIFY, true)));
    op.add("Toast提醒：" + onOff(getBoolean(K_TOAST_NOTIFY, true)));
    showModuleChoiceDialog("通知设置", "分别设置通知总开关、系统通知和 Toast 提醒", op, new Consumer() {
        public void accept(Object r) {
            int i = ((Integer) r).intValue();
            if (i == 0) toggle(K_NOTIFY_ON, true);
            else if (i == 1) toggle(K_SYSTEM_NOTIFY, true);
            else if (i == 2) toggle(K_TOAST_NOTIFY, true);
            returnToPage("main", false);
        }
    });
}

void listSettingsMenu() {
    List op = new ArrayList();
    op.add("编辑点赞名单（" + getList(K_LIKE_USERS).size() + " 人）");
    op.add("编辑通知名单（" + getList(K_NOTIFY_USERS).size() + " 人）");
    showModuleChoiceDialog("名单设置", "分别管理点赞名单和通知名单", op, new Consumer() {
        public void accept(Object r) {
            int i = ((Integer) r).intValue();
            if (i == 0) listMenu(true);
            else if (i == 1) listMenu(false);
        }
    });
}

void toggle(String key, boolean def) {
    boolean v = getBoolean(key, def);
    putBoolean(key, !v);
    toast(key + " -> " + onOff(!v));
    log("配置变更: " + key + " = " + (!v));
}

void askInt(String key, String title, String summary, int def, int min) {
    showModuleInputDialog(title, summary, String.valueOf(getInt(key, def)), "请输入数字", new Consumer() {
        public void accept(Object r) {
            try {
                int v = Integer.parseInt(String.valueOf(r).trim());
                if (v < min) v = min;
                putInt(key, v);
                toast("已设置: " + v);
                log("配置变更: " + v);
            } catch (Throwable t) { toast("输入无效"); }
            returnToPage("main", false);
        }
    });
}

void askDelayRange() {
    int lo = getInt(K_DELAY_MIN, 5);
    int hi = getInt(K_DELAY_MAX, 30);
    showModuleInputDialog("点赞随机延时", "格式：最小-最大（秒），例如 5-30", lo + "-" + hi, "5-30", new Consumer() {
        public void accept(Object r) {
            try {
                String s = String.valueOf(r).trim();
                String[] p = s.split("-");
                int a = Integer.parseInt(p[0].trim());
                int b = Integer.parseInt(p[1].trim());
                if (a < 0) a = 0;
                if (b < a) b = a;
                putInt(K_DELAY_MIN, a);
                putInt(K_DELAY_MAX, b);
                toast("点赞延时: " + a + "~" + b + " 秒");
                log("点赞延时改为 " + a + "~" + b + " 秒");
            } catch (Throwable t) { toast("格式错误，示例 5-30"); }
            returnToPage("main", false);
        }
    });
}

void resetDefault() {
    showModuleConfirmDialog("恢复默认设置", "将把开关、间隔、延时恢复为默认值（名单保留）。确认？", new Consumer() {
        public void accept(Object r) {
            if (!(r instanceof Boolean) || !((Boolean) r).booleanValue()) { toast("已取消"); return; }
            putBoolean(K_LIKE_ON, true);
            putBoolean(K_NOTIFY_ON, true);
            putBoolean(K_SYSTEM_NOTIFY, true);
            putBoolean(K_TOAST_NOTIFY, true);
            putInt(K_INTERVAL, 300);
            putInt(K_DELAY_MIN, 5);
            putInt(K_DELAY_MAX, 30);
            putInt(K_LIKE_GAP, 30);
            putInt(K_LIKE_HOURLY, 20);
            putInt(K_NOTIFY_GAP, 5);
            toast("已恢复默认设置");
            log("已恢复默认设置");
            returnToPage("main", false);
        }
    });
}

void showStatus() {
    List l = getList(K_LIKE_USERS);
    List n = getList(K_NOTIFY_USERS);
    int[] likeStats = countLikeLogStats();
    StringBuffer sb = new StringBuffer();
    sb.append("版本：" + hostVerName + "(" + hostVerCode + ")\n");
    sb.append("轮询：" + (running ? "运行中" : "已停止") + "，间隔 " + getInt(K_INTERVAL, 300) + " 秒\n");
    sb.append("点赞名单：" + l.size() + " 人，通知名单：" + n.size() + " 人\n");
    sb.append("最近点赞请求/确认：" + fmt(lastLikeAt) + "，最近通知：" + fmt(lastNotifyAt) + "\n");
    sb.append("点赞成功次数：" + likeStats[0] + "\n");
    sb.append("点赞失败次数：" + likeStats[1] + "\n");
    sb.append("通知成功次数：" + notifySuccessCount + "\n");
    sb.append("通知失败次数：" + notifyFailCount + "\n");
    sb.append("最近错误：" + (lastErr.length() == 0 ? "无" : lastErr));
    showInfoReturn("运行状态", sb.toString(), "main", false);
}

String fmt(long t) {
    if (t <= 0) return "无";
    try {
        return new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date(t));
    } catch (Throwable e) {
        return String.valueOf(t);
    }
}

// 统计以 run.log 为唯一来源，确保显示数字可由日志核对；清空日志后自然归零。
// 成功只认“微信原生点赞已确认”，即提交后再次查询到微信点赞状态为已点赞。
// 失败只统计一次请求的终态：未提交、请求异常、三次复核后仍未知或复核异常。
int[] countLikeLogStats() {
    int success = 0;
    int failed = 0;
    synchronized (logFileLock) {
        try {
            File f = new File(pluginDirFile, "run.log");
            if (!f.exists()) return new int[]{0, 0};
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.indexOf("微信原生点赞已确认") >= 0) {
                    success++;
                } else if (line.indexOf("点赞请求异常") >= 0
                        || line.indexOf("微信原生点赞请求未提交") >= 0
                        || line.indexOf("点赞结果未知") >= 0
                        || line.indexOf("点赞结果复核异常") >= 0) {
                    failed++;
                }
            }
            br.close();
        } catch (Throwable t) {
            lastErr = "统计点赞日志失败: " + t;
        }
    }
    return new int[]{success, failed};
}

// ================= 名单文本 UI =================
void listMenu(boolean like) {
    List op = new ArrayList();
    op.add("查看" + listName(like));
    op.add("添加好友");
    op.add("按标签批量设置");
    op.add("清空" + listName(like));
    final boolean isLike = like;
    showConfirmedMenu("管理" + listName(like), "共 " + getList(attrKey(like)).size() + " 人", op, "main", like, new Consumer() {
        public void accept(Object r) {
            listAction(((Integer) r).intValue(), isLike);
        }
    });
}

void listAction(int i, boolean like) {
    if (i == 0) {
        showFriendList(like);
    } else if (i == 1) {
        searchFriend(like);
    } else if (i == 2) {
        labelBatch(like);
    } else if (i == 3) {
        clearList(like);
        toast("已清空" + listName(like));
        returnToPage("list", like);
    }
}

void searchFriend(final boolean like) {
    boolean shown = showModuleInputDialog("搜索添加好友", "搜索已有微信好友的备注、昵称、微信号或 wxid；留空查看全部好友。已添加好友显示“（已添加）”且默认不勾选，勾选已添加好友会移出名单；未添加好友勾选后加入名单。", "", "请输入搜索关键词，可留空", new Consumer() {
        public void accept(Object result) {
            try {
                String keyword = result == null ? "" : String.valueOf(result).trim();
                pickFriendFiltered(like, true, keyword);
            } catch (Throwable t) {
                log("搜索好友失败: " + t);
                toast("搜索好友失败");
                returnToPage("list", like);
            }
        }
    });
    if (!shown) toast("无法显示搜索框，请保持微信在前台后重试");
}

// ================= 安全名单选择器 =================
// 不再创建原生 Dialog/View。名单选择统一使用模块内置多选弹窗，避免脚本 UI
// 与微信 Activity 生命周期冲突导致闪退。好友数据在后台读取，弹窗提交后一次性写入。
boolean matchesFriendSearch(Map friend, String wxid, String keyword) {
    if (keyword.length() == 0) return true;
    String needle = keyword.toLowerCase(java.util.Locale.ROOT);
    if (wxid.toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
    if (friend == null) return false;
    String[] keys = new String[]{"remarkName", "nickname", "displayName", "customWxId"};
    for (int i = 0; i < keys.length; i++) {
        Object value = friend.get(keys[i]);
        if (value != null && String.valueOf(value).toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
    }
    return false;
}

void pickFriendFiltered(final boolean like, final boolean add, final String keyword) {
    if (getTopActivity() == null) { toast("请先打开微信界面再操作"); return; }
    toast(add ? "正在加载全部好友…" : "正在加载名单成员…");
    new Thread(new Runnable() {
        public void run() {
            final List wxids = new ArrayList();
            final List choices = new ArrayList();
            try {
                List current = getList(attrKey(like));
                Set currentSet = new LinkedHashSet(current);
                Map byId = getFriendCache();

                if (add) {
                    for (Object o : byId.keySet()) {
                        String wxid = String.valueOf(o);
                        Map m = (Map) byId.get(wxid);
                        if (!matchesFriendSearch(m, wxid, keyword)) continue;
                        wxids.add(wxid);
                        choices.add(friendChoiceText(m, wxid, currentSet.contains(wxid)));
                    }
                } else {
                    for (Object o : current) {
                        String wxid = cleanWxid(o);
                        if (wxid.length() == 0) continue;
                        Map m = (Map) byId.get(wxid);
                        wxids.add(wxid);
                        choices.add(friendChoiceText(m, wxid, currentSet.contains(wxid)));
                    }
                }
            } catch (Throwable t) {
                log("读取名单候选失败: " + t);
            }

            final String loadError = choices.isEmpty()
                    ? (add ? (keyword.length() > 0 ? "没有匹配的好友，请返回名单菜单后重新搜索" : "未读取到微信好友") : "当前名单为空") : "";
            Activity act = getTopActivity();
            if (act == null) { toast("微信界面已离开，请重试"); return; }
            act.runOnUiThread(new Runnable() {
                public void run() {
                    if (choices.isEmpty()) { showInfoReturn("名单选择", loadError, "list", like); return; }
                    Set initial = new LinkedHashSet();
                    boolean shown = showModuleMultiChoiceDialog(
                            add ? "添加到" + listName(like) : "从" + listName(like) + "移除",
                            (add && keyword.length() > 0 ? "搜索：" + keyword + "；" : "") + "共 " + choices.size() + " 人；已添加好友带“（已添加）”标签，勾选后移出；未添加好友勾选后加入。",
                            choices,
                            initial,
                            "center",
                            new Consumer() {
                                public void accept(Object result) {
                                    try {
                                        if (!(result instanceof Set)) { toast("未取得选择结果"); return; }
                                        applyFriendSelection(like, add, wxids, (Set) result);
                                    } catch (Throwable t) {
                                        log("保存名单选择失败: " + t);
                                        toast("保存名单失败");
                                    } finally {
                                        returnToPage("list", like);
                                    }
                                }
                            });
                    if (!shown) toast("无法显示选择器，请保持微信在前台后重试");
                }
            });
        }
    }).start();
}

String cleanWxid(Object value) {
    if (value == null) return "";
    String wxid = String.valueOf(value).trim();
    if (wxid.length() == 0 || "null".equals(wxid)) return "";
    return wxid;
}

String friendChoiceText(Map m, String wxid, boolean added) {
    String display = "";
    if (m != null) {
        String[] keys = new String[]{"displayName", "remarkName", "nickname", "customWxId"};
        for (int i = 0; i < keys.length; i++) {
            Object value = m.get(keys[i]);
            if (value == null) continue;
            String text = String.valueOf(value).trim();
            if (text.length() > 0 && !"null".equals(text)) { display = text; break; }
        }
    }
    // 选择器中的逐项 getFriendName 可能触发重复的微信查询；列表资料没有可用名称时直接显示 wxid。
    if (display.length() == 0) display = wxid;
    return display + (added ? "（已添加）" : "") + "\n" + wxid;
}

void applyFriendSelection(boolean like, boolean add, List wxids, Set selectedIndexes) {
    LinkedHashSet target = new LinkedHashSet(getList(attrKey(like)));
    int changed = 0;
    Set visible = new HashSet();
    Set selected = new HashSet();
    for (Object o : selectedIndexes) {
        if (!(o instanceof Number)) continue;
        int index = ((Number) o).intValue();
        if (index >= 0 && index < wxids.size()) selected.add(Integer.valueOf(index));
    }
    if (add) {
        for (int i = 0; i < wxids.size(); i++) {
            String wxid = cleanWxid(wxids.get(i));
            if (wxid.length() == 0) continue;
            if (!selected.contains(Integer.valueOf(i))) continue;
            if (target.contains(wxid)) {
                if (target.remove(wxid)) changed++;
            } else {
                if (target.add(wxid)) changed++;
            }
        }
    } else {
        for (Object o : selectedIndexes) {
            if (!(o instanceof Number)) continue;
            int index = ((Number) o).intValue();
            if (index < 0 || index >= wxids.size()) continue;
            String wxid = cleanWxid(wxids.get(index));
            if (wxid.length() > 0 && target.remove(wxid)) changed++;
        }
    }
    putStringSet(attrKey(like), target);
    putString(textKey(like), joinLines(new ArrayList(target)));
    resetSeen();
    log((add ? "同步" : "批量移出") + listName(like) + "：变更 " + changed + " 人");
    toast((add ? "名单已同步，变更 " : "已移除 ") + changed + " 人，当前共 " + target.size() + " 人");
}

void labelBatch(boolean like) {
    List labels = new ArrayList();
    try {
        Object lo = getContactLabelListInfo();
        if (lo instanceof List) labels.addAll((List) lo);
    } catch (Throwable t) { }
    if (labels.isEmpty()) { showInfoReturn("按标签批量添加", "没有好友标签", "list", like); return; }
    List opts = new ArrayList();
    for (Object o : labels) {
        Map m = (Map) o;
        opts.add(String.valueOf(m.get("labelName")));
    }
    final List l2 = labels;
    showConfirmedMenu("按标签批量添加", "选择标签后，可取消勾选不想加入的好友", opts, "list", like, new Consumer() {
        public void accept(Object r) {
            Map m = (Map) l2.get(((Integer) r).intValue());
            labelBatchNamed(like, true, String.valueOf(m.get("labelName")));
        }
    });
}

void applyLabelSelection(boolean like, List wxids, Set selectedIndexes) {
    LinkedHashSet target = new LinkedHashSet(getList(attrKey(like)));
    Set selected = new HashSet();
    for (Object o : selectedIndexes) {
        if (!(o instanceof Number)) continue;
        int index = ((Number) o).intValue();
        if (index >= 0 && index < wxids.size()) selected.add(Integer.valueOf(index));
    }

    int added = 0;
    int removed = 0;
    for (int i = 0; i < wxids.size(); i++) {
        String wxid = cleanWxid(wxids.get(i));
        if (wxid.length() == 0) continue;
        if (selected.contains(Integer.valueOf(i))) {
            if (target.add(wxid)) added++;
        } else {
            if (target.remove(wxid)) removed++;
        }
    }

    putStringSet(attrKey(like), target);
    putString(textKey(like), joinLines(new ArrayList(target)));
    resetSeen();
    log("按标签同步" + listName(like) + "：添加 " + added + " 人，移除 " + removed + " 人");
    toast("已添加 " + added + " 人，取消 " + removed + " 人；当前共 " + target.size() + " 人");
}

void labelBatchNamed(final boolean like, final boolean add, final String labelName) {
    new Thread(new Runnable() {
        public void run() {
            final List wxids = new ArrayList();
            final List choices = new ArrayList();
            try {
                LinkedHashSet memberIds = new LinkedHashSet();
                Object r = getContactByLabelName(labelName);
                if (r instanceof List) {
                    for (Object item : (List) r) {
                        Object value = item;
                        if (item instanceof Map) {
                            Map member = (Map) item;
                            value = member.get("wxid");
                            if (value == null) value = member.get("username");
                            if (value == null) value = member.get("userName");
                        }
                        String wxid = cleanWxid(value);
                        if (wxid.length() > 0) memberIds.add(wxid);
                    }
                }

                Map byId = new HashMap();
                Object friends = getFriendListInfo();
                if (friends instanceof List) {
                    for (Object item : (List) friends) {
                        if (!(item instanceof Map)) continue;
                        Map friend = (Map) item;
                        String wxid = cleanWxid(friend.get("wxid"));
                        if (wxid.length() > 0) byId.put(wxid, friend);
                    }
                }

                for (Object item : memberIds) {
                    String wxid = cleanWxid(item);
                    if (wxid.length() == 0) continue;
                    wxids.add(wxid);
                    choices.add(friendChoiceText((Map) byId.get(wxid), wxid, false));
                }
            } catch (Throwable t) {
                log("读取标签成员失败: " + t);
            }

            Activity act = getTopActivity();
            if (act == null) { toast("微信界面已离开，请重试"); return; }
            act.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if (choices.isEmpty()) {
                            showInfoReturn("按标签批量添加", "该标签下没有好友", "label", like);
                            return;
                        }
                        Set initial = new LinkedHashSet();
                        for (int i = 0; i < wxids.size(); i++) initial.add(Integer.valueOf(i));
                        boolean shown = showModuleMultiChoiceDialog(
                                "设置标签好友",
                                "标签：" + labelName + "；已默认全选，取消勾选的好友不会保留在当前名单中",
                                choices,
                                initial,
                                "center",
                                new Consumer() {
                                    public void accept(Object result) {
                                        try {
                                            if (!(result instanceof Set)) { toast("未取得选择结果"); return; }
                                            applyLabelSelection(like, wxids, (Set) result);
                                            log("标签[" + labelName + "]已同步到" + listName(like));
                                        } catch (Throwable t) {
                                            log("保存标签选择失败: " + t);
                                            toast("保存名单失败");
                                        } finally {
                                            returnToPage("label", like);
                                        }
                                    }
                                });
                        if (!shown) toast("无法显示选择器，请保持微信在前台后重试");
                    } catch (Throwable t) {
                        log("显示标签成员选择器失败: " + t);
                        toast("无法显示标签成员选择器");
                    }
                }
            });
        }
    }).start();
}

// ================= 日志 =================
// 独立于状态监控总开关；微信进程结束后不再运行，重载时补做一次到期清理。
void startLogCleanup() {
    if (logCleanTimer != null) logCleanTimer.cancel();
    logCleanTimer = new java.util.Timer("StatusLogCleanup", true);
    logCleanTimer.schedule(new java.util.TimerTask() {
        public void run() {
            try {
                synchronized (logFileLock) {
                    int hours = getInt(K_LOG_HOURS, 0);
                    if (hours <= 0) return;
                    long now = System.currentTimeMillis();
                    long last = getLong(K_LOG_CLEAN_AT, 0L);
                    if (last <= 0L || last > now) { putLong(K_LOG_CLEAN_AT, now); return; }
                    if (now - last >= ((long) hours) * 3600000L) clearRunLog();
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "定时清理日志失败: " + t);
            }
        }
    }, 0L, 60000L);
}

void clearRunLog() {
    synchronized (logFileLock) {
        File file = new File(pluginDirFile, "run.log");
        List keep = new ArrayList();
        if (file.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.indexOf("通知:") >= 0) keep.add(line);
                }
            } finally { reader.close(); }
        }
        FileWriter writer = new FileWriter(file, false);
        try {
            for (Object line : keep) writer.write(String.valueOf(line) + "\n");
            writer.flush();
        } finally { writer.close(); }
        putLong(K_LOG_CLEAN_AT, System.currentTimeMillis());
    }
}

String logCleanupLabel() {
    int hours = getInt(K_LOG_HOURS, 0);
    return hours <= 0 ? "关闭" : "每 " + hours + " 小时";
}

void logCleanupSettings() {
    showModuleInputDialog("定时清理日志", "自定义周期（单位：小时），输入非负整数；0 表示关闭。确定后返回日志菜单，取消不修改。只清空本插件 run.log。",
            String.valueOf(getInt(K_LOG_HOURS, 0)), "请输入小时数，0 为关闭", "bottom", new Consumer() {
        public void accept(Object result) {
            try {
                int hours;
                try {
                    hours = Integer.parseInt(String.valueOf(result).trim());
                    if (hours < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    toast("请输入 0 至 2147483647 的整数小时数，原设置未修改");
                    return;
                }
                synchronized (logFileLock) {
                    if (getInt(K_LOG_HOURS, 0) != hours) {
                        putLong(K_LOG_CLEAN_AT, System.currentTimeMillis());
                        putInt(K_LOG_HOURS, hours);
                    }
                }
                toast("定时清理：" + logCleanupLabel());
            } catch (Throwable t) { toast("保存清理周期失败"); }
            finally { returnToPage("log", false); }
        }
    });
}

void logMenu() {
    List op = new ArrayList();
    op.add("查看最近日志");
    op.add("清空日志文件");
    op.add("定时清理：" + logCleanupLabel());
    showConfirmedMenu("日志", "选择操作", op, "main", false, new Consumer() {
        public void accept(Object r) {
            int i = ((Integer) r).intValue();
            if (i == 2) { logCleanupSettings(); return; }
            final int action = i;
            new Thread(new Runnable() {
                public void run() {
                    if (action == 0) {
                        String text;
                        synchronized (logFileLock) { text = tailLog(50); }
                        showInfoReturn("最近日志", text, "log", false);
                    } else {
                        try { clearRunLog(); toast("日志已清空"); }
                        catch (Throwable t) { toast("清空失败"); }
                        finally { returnToPage("log", false); }
                    }
                }
            }).start();
        }
    });
}

String tailLog(int max) {
    StringBuffer sb = new StringBuffer();
    try {
        File f = new File(pluginDirFile, "run.log");
        if (!f.exists()) return "（暂无日志）";
        BufferedReader br = new BufferedReader(new FileReader(f));
        List lines = new ArrayList();
        String s;
        while ((s = br.readLine()) != null) {
            lines.add(s);
            if (lines.size() > max) lines.remove(0);
        }
        br.close();
        for (Object o : lines) sb.append(String.valueOf(o) + "\n");
    } catch (Throwable t) {
        return "读取日志失败: " + t;
    }
    return sb.toString();
}

void log(Object msg) {
    try {
        String line = fmt(System.currentTimeMillis()) + "  " + String.valueOf(msg);
        try { android.util.Log.i(TAG, line); } catch (Throwable t) { }
        synchronized (logFileLock) {
            FileWriter fw = new FileWriter(new File(pluginDirFile, "run.log"), true);
            try { fw.write(line + "\n"); fw.flush(); }
            finally { fw.close(); }
        }
    } catch (Throwable t) {
    }
}

// ================= 反射工具 =================
Class reflectFindClass(String fullName) {
    try {
        Class c = findClass(fullName);
        if (c != null) return c;
    } catch (Throwable t) {
    }
    try {
        return Class.forName(fullName, false, classLoader);
    } catch (Throwable t) {
        return null;
    }
}

Object callStatic(String cls, String method, Class[] types, Object[] args) {
    try {
        Class c = reflectFindClass(cls);
        if (c == null) { lastErr = "class not found: " + cls; return null; }
        Method m = c.getMethod(method, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    } catch (Throwable t) {
        lastErr = String.valueOf(t);
        return null;
    }
}

Object fieldOf(Object o, String name) {
    if (o == null) return null;
    try {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    } catch (Throwable t) {
        return null;
    }
}

boolean boolField(Object o, String name) {
    try {
        Object v = fieldOf(o, name);
        if (v == null) return false;
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return false;
    } catch (Throwable t) {
        return false;
    }
}

List listField(Object o) {
    try {
        Field[] fs = o.getClass().getFields();
        for (int i = 0; i < fs.length; i++) {
            if (!List.class.isAssignableFrom(fs[i].getType())) continue;
            Object v = fs[i].get(o);
            if (v instanceof List) return (List) v;
        }
    } catch (Throwable t) {
    }
    try {
        Field[] fs2 = o.getClass().getDeclaredFields();
        for (int i = 0; i < fs2.length; i++) {
            if (!List.class.isAssignableFrom(fs2[i].getType())) continue;
            fs2[i].setAccessible(true);
            Object v = fs2[i].get(o);
            if (v instanceof List) return (List) v;
        }
    } catch (Throwable t) {
    }
    return null;
}

String getStr(Object o, String method) {
    try {
        Method m = o.getClass().getMethod(method, new Class[]{});
        m.setAccessible(true);
        Object v = m.invoke(o, new Object[]{});
        return v == null ? "" : String.valueOf(v);
    } catch (Throwable t) {
        return "";
    }
}
