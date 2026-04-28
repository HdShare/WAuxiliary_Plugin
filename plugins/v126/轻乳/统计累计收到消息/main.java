// 好友消息统计助手 + 每日定时消息（硬编码改进版，修复时区问题）
// 功能：
//   1. 统计好友消息总数和关键词出现次数（从插件加载后开始）
//   2. 每天0-6点自动给指定好友发送固定消息（北京时间）

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

// ========== 固定配置（焊死） ==========
// 每天自动发送的好友wxid和消息内容（请根据实际情况修改）
String NIGHT_FRIEND_WXID = "your_friend_wxid_here";
String NIGHT_MESSAGE = "我好想你";

// 时区设置（中国用户请勿修改）
String TIMEZONE = "Asia/Shanghai";

// ========== 关键词存储 ==========
String KEYWORDS_COUNT_KEY = "stats_keywords_count";
int getKeywordsCount() { return getInt(KEYWORDS_COUNT_KEY, 0); }
void setKeywordsCount(int c) { putInt(KEYWORDS_COUNT_KEY, c); }

String getKeyword(int idx) { return getString("stats_keyword_" + idx, ""); }
void setKeyword(int idx, String kw) { putString("stats_keyword_" + idx, kw); }

void deleteKeyword(int idx) {
    int cnt = getKeywordsCount();
    if (idx < 1 || idx > cnt) return;
    for (int i = idx; i < cnt; i++) {
        setKeyword(i, getKeyword(i+1));
    }
    setKeyword(cnt, "");
    setKeywordsCount(cnt - 1);
}

// ========== 好友统计存储 ==========
int getFriendTotal(String wxid) {
    return getInt("stats_friend_" + wxid + "_total", 0);
}
void setFriendTotal(String wxid, int total) {
    putInt("stats_friend_" + wxid + "_total", total);
}
void incFriendTotal(String wxid) {
    setFriendTotal(wxid, getFriendTotal(wxid) + 1);
}

int getFriendKeywordCount(String wxid, int kwIdx) {
    return getInt("stats_friend_" + wxid + "_kw_" + kwIdx, 0);
}
void setFriendKeywordCount(String wxid, int kwIdx, int count) {
    putInt("stats_friend_" + wxid + "_kw_" + kwIdx, count);
}
void incFriendKeywordCount(String wxid, int kwIdx) {
    setFriendKeywordCount(wxid, kwIdx, getFriendKeywordCount(wxid, kwIdx) + 1);
}

// ========== 定时消息状态存储 ==========
String NIGHT_LAST_DATE_KEY = "night_msg_last_date";
String getLastNightSendDate() { return getString(NIGHT_LAST_DATE_KEY, ""); }
void setLastNightSendDate(String date) { putString(NIGHT_LAST_DATE_KEY, date); }

// ========== 辅助方法（带时区） ==========
// 获取指定时区的当前时间
Calendar getCurrentCalendar() {
    return Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE));
}

// 获取当前日期字符串（yyyy-MM-dd，带时区）
String getCurrentDateStr() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.setTimeZone(TimeZone.getTimeZone(TIMEZONE));
    return sdf.format(new Date());
}

// 获取当前小时（0-23，带时区）
int getCurrentHour() {
    return getCurrentCalendar().get(Calendar.HOUR_OF_DAY);
}

List<String> getAllFriendWxids() {
    List<String> result = new java.util.ArrayList<String>();
    try {
        List friends = getFriendList();
        if (friends == null) return result;
        for (int i = 0; i < friends.size(); i++) {
            Object f = friends.get(i);
            String wxid = getWxidFromFriend(f);
            if (wxid != null && getFriendTotal(wxid) > 0) {
                result.add(wxid);
            }
        }
    } catch (Exception e) {
        log("getAllFriendWxids 异常: " + e.toString());
    }
    return result;
}

String getWxidFromFriend(Object friend) {
    try {
        try {
            return (String) friend.getClass().getMethod("getWxid").invoke(friend);
        } catch (NoSuchMethodException e1) {
            try {
                return (String) friend.getClass().getMethod("getUserName").invoke(friend);
            } catch (Exception e2) {
                return null;
            }
        }
    } catch (Exception e) {
        return null;
    }
}

String getFriendNickname(String wxid) {
    try {
        return getFriendName(wxid);
    } catch (Exception e) {
        return wxid;
    }
}

// 验证wxid是否为好友
boolean isValidFriendWxid(String wxid) {
    try {
        List friends = getFriendList();
        if (friends == null) return false;
        for (int i = 0; i < friends.size(); i++) {
            Object friend = friends.get(i);
            String id = getWxidFromFriend(friend);
            if (id != null && id.equals(wxid)) {
                return true;
            }
        }
    } catch (Exception e) {
        log("isValidFriendWxid 异常: " + e.toString());
    }
    return false;
}

// ========== 检查并发送夜间消息 ==========
void checkAndSendNightMessage() {
    String today = getCurrentDateStr();
    String last = getLastNightSendDate();

    // 如果今天已经发送过，则不再发送
    if (today.equals(last)) return;

    // 时间窗口：北京时间0-6点
    int hour = getCurrentHour();
    if (hour < 0 || hour > 6) {
        return;
    }

    // 验证好友是否有效
    if (!isValidFriendWxid(NIGHT_FRIEND_WXID)) {
        log("夜间消息发送失败：好友 " + NIGHT_FRIEND_WXID + " 不在好友列表中");
        return;
    }

    // 发送消息（无返回值，直接调用）
    sendText(NIGHT_FRIEND_WXID, NIGHT_MESSAGE);
    setLastNightSendDate(today);
    log("夜间消息已发送给 " + NIGHT_FRIEND_WXID);
}

// ========== 插件生命周期 ==========
void onLoad() {
    log("========================================");
    log("好友消息统计助手（含每日定时消息）已加载");
    log("时区：" + TIMEZONE);
    log("发送 /统计 帮助 查看说明");
    log("========================================");
    // 启动时立即检查一次（防止错过0点）
    checkAndSendNightMessage();
}

void onUnload() {
    log("好友消息统计助手已卸载");
}

void onHandleMsg(Object msgInfoBean) {
    try {
        // 每次收到消息先检查夜间定时消息
        checkAndSendNightMessage();

        Object msg = msgInfoBean;
        String sender = msg.getSendTalker();
        String talker = msg.getTalker();
        String content = msg.getContent();
        String myWxid = getLoginWxid();

        if (!msg.isPrivateChat()) return;

        if (sender.equals(myWxid)) {
            handleCommand(talker, content);
            return;
        }

        String friendWxid = sender;
        incFriendTotal(friendWxid);

        int kwCount = getKeywordsCount();
        if (kwCount > 0 && content != null) {
            String lowerContent = content.toLowerCase();
            for (int i = 1; i <= kwCount; i++) {
                String kw = getKeyword(i);
                if (kw == null || kw.isEmpty()) continue;
                int count = countOccurrences(lowerContent, kw.toLowerCase());
                if (count > 0) {
                    int old = getFriendKeywordCount(friendWxid, i);
                    setFriendKeywordCount(friendWxid, i, old + count);
                }
            }
        }

    } catch (Exception e) {
        log("onHandleMsg 错误: " + e.toString());
    }
}

int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) != -1) {
        count++;
        idx += sub.length();
    }
    return count;
}

void handleCommand(String talker, String content) {
    if (content == null || content.trim().isEmpty()) return;
    String cmd = content.trim();

    if (cmd.equals("/统计") || cmd.equals("/统计 帮助")) {
        sendHelp(talker);
        return;
    }

    if (cmd.startsWith("/统计 关键词")) {
        handleKeywordCommand(talker, cmd);
        return;
    }

    if (cmd.startsWith("/统计 列表")) {
        listAllFriendsStats(talker);
        return;
    }

    // 定时测试命令（立即发送，不检查时间窗口）
    if (cmd.equals("/统计 定时测试") || cmd.equals("/统计 定时测验")) {
        if (isValidFriendWxid(NIGHT_FRIEND_WXID)) {
            sendText(NIGHT_FRIEND_WXID, NIGHT_MESSAGE);
            sendText(talker, "✅ 测试消息已发送\n⚠️ 注意：无法判断对方是否实际收到");
        } else {
            sendText(talker, "❌ 发送失败，好友 " + NIGHT_FRIEND_WXID + " 无效");
        }
        return;
    }

    // 重置定时状态命令（下次触发时可再次发送）
    if (cmd.equals("/统计 定时开始") || cmd.equals("/统计 定时开启")) {
        setLastNightSendDate("");
        sendText(talker, "✅ 已重置定时状态，下次0-6点收到消息时将发送");
        return;
    }

    if (cmd.startsWith("/统计 ")) {
        String wxid = cmd.substring(3).trim();
        if (wxid.length() > 0) {
            showFriendStats(talker, wxid);
        } else {
            sendHelp(talker);
        }
        return;
    }
}

void sendHelp(String talker) {
    String help = "📊 好友消息统计助手使用说明（北京时间版）\n\n" +
        "/统计 好友wxid - 查看指定好友的统计（消息总数、关键词次数）\n" +
        "/统计 列表 - 列出所有有过消息的好友及消息总数\n" +
        "/统计 关键词 添加 词 - 添加要统计的关键词（不区分大小写）\n" +
        "/统计 关键词 删除 编号 - 删除指定编号的关键词\n" +
        "/统计 关键词 列表 - 查看当前统计的关键词列表\n" +
        "/统计 定时测试 - 立即测试发送定时消息\n" +
        "/统计 定时开始 - 重置定时状态（下次0-6点触发）\n" +
        "/统计 帮助 - 显示本帮助\n\n" +
        "⚠️ 注意：统计数据仅从插件加载后开始累积，无法回溯历史消息。\n\n" +
        "（本插件内置每日北京时间0-6点自动发送消息功能，无需额外配置）";
    sendText(talker, help);
}

void handleKeywordCommand(String talker, String cmd) {
    String[] parts = cmd.split(" ", 4);
    if (parts.length < 3) {
        sendText(talker, "关键词子命令错误，可用：添加、删除、列表");
        return;
    }
    String sub = parts[2];
    if ("列表".equals(sub)) {
        listKeywords(talker);
    } else if ("添加".equals(sub) && parts.length >= 4) {
        String kw = parts[3].trim();
        if (kw.isEmpty()) {
            sendText(talker, "关键词不能为空");
            return;
        }
        int cnt = getKeywordsCount();
        int newIdx = cnt + 1;
        setKeyword(newIdx, kw);
        setKeywordsCount(newIdx);
        sendText(talker, "✅ 关键词添加成功，编号：" + newIdx);
    } else if ("删除".equals(sub) && parts.length >= 4) {
        try {
            int idx = Integer.parseInt(parts[3].trim());
            int cnt = getKeywordsCount();
            if (idx < 1 || idx > cnt) {
                sendText(talker, "编号超出范围");
                return;
            }
            deleteKeyword(idx);
            sendText(talker, "✅ 关键词删除成功");
        } catch (NumberFormatException e) {
            sendText(talker, "编号无效，请输入数字");
        }
    } else {
        sendText(talker, "未知关键词子命令，可用：添加、删除、列表");
    }
}

void listKeywords(String talker) {
    int cnt = getKeywordsCount();
    if (cnt == 0) {
        sendText(talker, "📭 暂无统计关键词");
        return;
    }
    StringBuilder sb = new StringBuilder("📋 当前统计关键词列表：\n");
    for (int i = 1; i <= cnt; i++) {
        sb.append(i).append(". ").append(getKeyword(i)).append("\n");
    }
    sendText(talker, sb.toString());
}

void listAllFriendsStats(String talker) {
    List<String> wxids = getAllFriendWxids();
    if (wxids.isEmpty()) {
        sendText(talker, "📭 暂无好友消息统计");
        return;
    }
    StringBuilder sb = new StringBuilder("📊 好友消息总数统计：\n");
    for (String wxid : wxids) {
        String name = getFriendNickname(wxid);
        int total = getFriendTotal(wxid);
        sb.append("· ").append(name).append(" (").append(wxid).append(") : ").append(total).append("条\n");
    }
    sendText(talker, sb.toString());
}

void showFriendStats(String talker, String wxid) {
    int total = getFriendTotal(wxid);
    if (total == 0) {
        sendText(talker, "该好友暂无统计（可能插件加载后未收到消息）");
        return;
    }
    String name = getFriendNickname(wxid);
    StringBuilder sb = new StringBuilder("📊 好友[" + name + "] 统计：\n");
    sb.append("消息总数：").append(total).append("条\n");

    int kwCount = getKeywordsCount();
    if (kwCount > 0) {
        sb.append("关键词出现次数：\n");
        for (int i = 1; i <= kwCount; i++) {
            String kw = getKeyword(i);
            int cnt = getFriendKeywordCount(wxid, i);
            if (cnt > 0) {
                sb.append("  ").append(kw).append(" : ").append(cnt).append("次\n");
            }
        }
    }
    sendText(talker, sb.toString());
}