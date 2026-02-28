// 记事本插件（BeanShell）- 多用户独立笔记版（纯净版，无定时提醒）
// 功能：
//   - 笔记管理（独立存储，支持分页）
//   - 好友授权（wxid直接授权）

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

// ========== 全局状态（使用Map隔离用户）==========
// 待授权状态：key=talker, value=wxid
Map<String, String> pendingAuthMap = new HashMap<String, String>();

// ========== 生命周期 ==========
void onLoad() {
    log("记事本插件已加载（纯净版）。发送 /使用手册 查看说明。");
}

void onUnLoad() {
    log("记事本插件已卸载");
}

void onHandleMsg(Object msgInfoBean) {
    try {
        Object msg = msgInfoBean;
        String sender = msg.getSendTalker();
        String talker = msg.getTalker();
        String content = msg.getContent();
        String myWxid = getLoginWxid();

        if (!msg.isPrivateChat()) return;

        // 处理使用手册命令（任何人可用）
        if (content != null && content.startsWith("/使用手册")) {
            sendManual(talker);
            return;
        }

        boolean isSelf = sender.equals(myWxid);

        // 自己特有的命令处理
        if (isSelf) {
            // 优先处理待授权状态
            if (pendingAuthMap.containsKey(talker)) {
                handlePendingAuth(talker, content);
                return;
            }

            // 处理配置命令
            if ("配置".equals(content.trim())) {
                handleConfig(talker);
                return;
            }

            if (content.startsWith("/")) {
                String cmd = content.substring(1);
                if (cmd.startsWith("好友列表") || cmd.startsWith("授权") || cmd.startsWith("取消授权") || cmd.startsWith("清空授权")) {
                    handleConfigCommand(talker, cmd);
                    return;
                }
                // 其他笔记命令将在后面处理
            } else {
                // 非命令消息忽略
                return;
            }
        }

        // 权限检查：自己或授权好友才能执行笔记命令
        if (!isSelf && !isAuthFriend(sender)) {
            return;
        }

        if (content.startsWith("/")) {
            handleNoteCommand(talker, content, sender);
        }
    } catch (Exception e) {
        log("onHandleMsg 错误: " + e.toString());
    }
}

// ========== 发送使用手册 ==========
void sendManual(String talker) {
    String manual = "记事本插件使用说明（wxid授权版）\n\n" +
        "📝 基础笔记功能\n" +
        "自己和授权好友均可使用以下命令：\n\n" +
        "· 记笔记\n" +
        "    /记 内容\n" +
        "    例如：/记 今天天气不错\n" +
        "· 查看自己的笔记列表（分页）\n" +
        "    /笔记 [页码]\n" +
        "    例如：/笔记 2 查看第2页，每页10条\n" +
        "· 查看自己的某条笔记详情\n" +
        "    /查看 笔记编号\n" +
        "    例如：/查看 1\n" +
        "· 删除自己的某条笔记\n" +
        "    /删除 笔记编号\n" +
        "    例如：/删除 1\n" +
        "· 清空自己的所有笔记\n" +
        "    /清空\n\n" +
        "⚙️ 授权管理（仅自己可用）\n\n" +
        "· 进入配置面板\n" +
        "    发送：配置\n" +
        "· 查看所有好友（已授权置顶）\n" +
        "    /好友列表\n" +
        "· 授权好友\n" +
        "    /授权 wxid\n" +
        "    例如：/授权 wxid_11t9pia2zt5322\n" +
        "· 取消授权\n" +
        "    /取消授权 wxid\n" +
        "    例如：/取消授权 wxid_11t9pia2zt5322\n" +
        "· 清空所有授权\n" +
        "    /清空授权\n\n" +
        "👑 主人特权：管理好友笔记\n\n" +
        "在 /好友列表 中查看好友的 序号，使用以下命令：\n\n" +
        "· 查看指定好友的笔记列表（分页）\n" +
        "    /笔记 好友序号 [页码]\n" +
        "    例如：/笔记 3 2 查看第3位好友的第2页笔记\n" +
        "· 查看好友的某条笔记详情\n" +
        "    /查看 好友序号 笔记编号\n" +
        "    例如：/查看 3 2 查看第3位好友的第2条笔记\n" +
        "· 删除好友的某条笔记\n" +
        "    /删除 好友序号 笔记编号\n" +
        "    例如：/删除 3 2\n" +
        "· 清空好友的所有笔记\n" +
        "    /清空 好友序号\n" +
        "    例如：/清空 3\n\n" +
        "📌 注意事项\n\n" +
        "· 所有命令必须在 私聊 中发送给插件使用者（自己）才有效。\n" +
        "· 好友只能使用自己的笔记命令（/记、/笔记、/查看、/删除、/清空），不能使用带好友序号的命令。\n" +
        "· 笔记数据按用户独立存储，互不干扰。";
    sendText(talker, manual);
}

// ========== 授权管理 ==========
String AUTH_KEY = "auth_friends";

Set<String> getAuthFriends() {
    String data = getString(AUTH_KEY, "");
    Set<String> set = new HashSet<String>();
    if (data != null && !data.isEmpty()) {
        String[] arr = data.split(",");
        for (int i = 0; i < arr.length; i++) {
            String wxid = arr[i].trim();
            if (wxid.length() > 0) {
                set.add(wxid);
            }
        }
    }
    return set;
}

void saveAuthFriends(Set<String> authSet) {
    StringBuilder sb = new StringBuilder();
    java.util.Iterator<String> it = authSet.iterator();
    boolean first = true;
    while (it.hasNext()) {
        if (!first) sb.append(",");
        sb.append(it.next());
        first = false;
    }
    putString(AUTH_KEY, sb.toString());
}

boolean isAuthFriend(String wxid) {
    Set<String> auth = getAuthFriends();
    return auth.contains(wxid);
}

// ========== 配置命令处理 ==========
void handleConfig(String talker) {
    try {
        Set<String> auth = getAuthFriends();
        StringBuilder sb = new StringBuilder("📋 好友授权配置\n");
        sb.append("当前已授权好友 (").append(auth.size()).append(" 人)：\n");
        if (auth.isEmpty()) {
            sb.append("无\n");
        } else {
            for (String wxid : auth) {
                String name = getFriendName(wxid);
                sb.append("· ").append(name).append(" (").append(wxid).append(")\n");
            }
        }
        sb.append("\n可用命令（仅你自己可见）：\n");
        sb.append("/好友列表      - 查看所有好友（已授权置顶）\n");
        sb.append("/授权 wxid     - 直接授权指定wxid的好友\n");
        sb.append("/取消授权 wxid - 取消授权\n");
        sb.append("/清空授权      - 清空所有授权\n");
        sendText(talker, sb.toString());
    } catch (Exception e) {
        sendText(talker, "配置加载失败，请查看日志");
        log("handleConfig 异常: " + e.toString());
    }
}

void handleConfigCommand(String talker, String cmd) {
    try {
        if (cmd.startsWith("好友列表")) {
            listAllFriends(talker);
        } else if (cmd.startsWith("授权 ")) {
            String[] parts = cmd.split(" ", 2);
            if (parts.length < 2) {
                sendText(talker, "请指定好友的wxid，格式：/授权 wxid");
                return;
            }
            String wxid = parts[1].trim();
            if (wxid.isEmpty()) {
                sendText(talker, "请输入有效的wxid");
                return;
            }
            if (!isValidFriendWxid(wxid)) {
                sendText(talker, "无效的wxid，请确保该wxid在你的好友列表中");
                return;
            }
            if (isAuthFriend(wxid)) {
                sendText(talker, "该好友已经授权");
                return;
            }
            Set<String> auth = getAuthFriends();
            auth.add(wxid);
            saveAuthFriends(auth);
            String name = getFriendName(wxid);
            sendText(talker, "✅ 已授权好友：" + name + " (" + wxid + ")");
        } else if (cmd.startsWith("取消授权 ")) {
            String[] parts = cmd.split(" ", 2);
            if (parts.length < 2) {
                sendText(talker, "请指定好友的wxid，格式：/取消授权 wxid");
                return;
            }
            String wxid = parts[1].trim();
            if (wxid.isEmpty()) {
                sendText(talker, "请输入有效的wxid");
                return;
            }
            Set<String> auth = getAuthFriends();
            if (!auth.contains(wxid)) {
                sendText(talker, "该好友未授权");
                return;
            }
            auth.remove(wxid);
            saveAuthFriends(auth);
            String name = getFriendName(wxid);
            sendText(talker, "❌ 已取消授权：" + name + " (" + wxid + ")");
        } else if (cmd.startsWith("清空授权")) {
            saveAuthFriends(new HashSet<String>());
            sendText(talker, "已清空所有授权好友");
        } else {
            sendText(talker, "未知配置命令，请发送“配置”查看帮助");
        }
    } catch (Exception e) {
        sendText(talker, "命令执行失败，请查看日志");
        log("handleConfigCommand 异常: " + e.toString());
    }
}

// ========== 待授权处理 ==========
void handlePendingAuth(String talker, String input) {
    if (input == null) input = "";
    String wxid = input.trim();
    if (wxid.isEmpty()) {
        sendText(talker, "wxid不能为空，请重新输入（输入“取消”可退出）");
        return;
    }
    if (wxid.equals("取消")) {
        pendingAuthMap.remove(talker);
        sendText(talker, "已取消授权操作");
        return;
    }
    if (!isValidFriendWxid(wxid)) {
        sendText(talker, "无效的wxid，请确保该wxid在你的好友列表中（输入“取消”可退出）");
        return;
    }
    if (isAuthFriend(wxid)) {
        sendText(talker, "该好友已经授权，无需重复授权（输入“取消”可退出）");
        return;
    }
    Set<String> auth = getAuthFriends();
    auth.add(wxid);
    saveAuthFriends(auth);
    String name = getFriendName(wxid);
    sendText(talker, "✅ 已授权好友：" + name + " (" + wxid + ")");
    pendingAuthMap.remove(talker);
}

// ========== 好友列表 ==========
void listAllFriends(String talker) {
    try {
        List friends = getFriendList();
        if (friends == null || friends.isEmpty()) {
            sendText(talker, "好友列表为空");
            return;
        }
        Set<String> auth = getAuthFriends();

        StringBuilder authPart = new StringBuilder();
        StringBuilder unauthPart = new StringBuilder();
        int authCount = 0, unauthCount = 0;

        for (int i = 0; i < friends.size(); i++) {
            Object friend = friends.get(i);
            if (friend == null) continue;

            String wxid = getWxidFromFriendSafe(friend);
            if (wxid == null || wxid.isEmpty()) continue;

            String display = getFriendName(wxid);
            if (display == null) display = "未知昵称";
            boolean isAuth = auth.contains(wxid);
            String status = isAuth ? "✅已授权" : "🔴未授权";
            String numberEmoji = getNumberEmoji(i+1);
            String item = numberEmoji + " 👤 " + display + "\n   🔑 " + wxid + "\n   " + status + "\n━━━━━━━━━━━━━━━━━━\n";

            if (isAuth) {
                authPart.append(item);
                authCount++;
            } else {
                unauthPart.append(item);
                unauthCount++;
            }
        }

        StringBuilder full = new StringBuilder("━━━━━━━━━━━━━━━━━━\n");
        full.append("      📋 好友列表（共 ").append(friends.size()).append(" 人）\n");
        full.append("━━━━━━━━━━━━━━━━━━\n");

        if (authCount > 0) {
            full.append("🟢 已授权好友 (").append(authCount).append(")\n");
            full.append(authPart);
        }
        if (unauthCount > 0) {
            full.append("🔴 未授权好友 (").append(unauthCount).append(")\n");
            full.append(unauthPart);
        }

        // 简单分页
        int pageSize = 2500;
        String content = full.toString();
        int len = content.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + pageSize, len);
            sendText(talker, content.substring(start, end));
            start = end;
        }

        sendText(talker, "📣 使用 /授权 wxid 或 /取消授权 wxid 进行操作。");
    } catch (Exception e) {
        sendText(talker, "获取好友列表失败，请查看日志");
        log("listAllFriends 异常: " + e.toString());
    }
}

String getNumberEmoji(int num) {
    String[] emojis = {"0️⃣","1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣"};
    if (num < 10) return emojis[num];
    return String.valueOf(num);
}

String getWxidFromFriendSafe(Object friend) {
    try {
        try {
            return (String) friend.getClass().getMethod("getWxid").invoke(friend);
        } catch (NoSuchMethodException e1) {
            try {
                return (String) friend.getClass().getMethod("getUserName").invoke(friend);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            try {
                return (String) friend.getClass().getMethod("getUserName").invoke(friend);
            } catch (Exception ex) {
                return null;
            }
        }
    } catch (Exception e) {
        return null;
    }
}

boolean isValidFriendWxid(String wxid) {
    try {
        List friends = getFriendList();
        if (friends == null) return false;
        for (int i = 0; i < friends.size(); i++) {
            Object friend = friends.get(i);
            String id = getWxidFromFriendSafe(friend);
            if (id != null && id.equals(wxid)) {
                return true;
            }
        }
    } catch (Exception e) {
        log("isValidFriendWxid 异常: " + e.toString());
    }
    return false;
}

// ========== 笔记命令处理（支持分页）=========
void handleNoteCommand(String talker, String content, String sender) {
    if (content == null || content.trim().isEmpty()) return;
    String cmd = content.trim();
    if (!cmd.startsWith("/")) return;

    String[] parts = cmd.split(" ", 4); // 最多支持 3 个参数
    String command = parts[0];
    String param1 = (parts.length > 1) ? parts[1] : null;
    String param2 = (parts.length > 2) ? parts[2] : null;
    String param3 = (parts.length > 3) ? parts[3] : null;

    String myWxid = getLoginWxid();
    boolean isSelf = sender.equals(myWxid);

    if ("/记".equals(command)) {
        if (param1 == null || param1.trim().isEmpty()) {
            sendText(talker, "请提供笔记内容，格式：/记 内容");
        } else {
            addNote(talker, param1, sender);
        }
        return;
    }

    if (isSelf) {
        // 主人可以操作任意好友的笔记（使用好友编号）
        if ("/笔记".equals(command)) {
            int page = 1;
            String friendWxid = myWxid;
            if (param1 != null) {
                try {
                    int friendIndex = Integer.parseInt(param1);
                    friendWxid = getFriendWxidByIndex(friendIndex);
                    if (friendWxid == null) {
                        sendText(talker, "无效的好友编号");
                        return;
                    }
                    if (param2 != null) {
                        page = Integer.parseInt(param2);
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "好友编号无效，请输入数字");
                    return;
                }
            }
            listNotesWithPaging(talker, friendWxid, page);
        } else if ("/查看".equals(command)) {
            if (param1 == null) {
                sendText(talker, "请提供笔记编号，格式：/查看 编号 或 /查看 好友编号 笔记编号");
                return;
            }
            if (param2 == null) {
                try {
                    int noteIndex = Integer.parseInt(param1.trim());
                    getNote(talker, noteIndex, myWxid);
                } catch (NumberFormatException e) {
                    sendText(talker, "笔记编号无效，请输入数字");
                }
            } else {
                try {
                    int friendIndex = Integer.parseInt(param1.trim());
                    int noteIndex = Integer.parseInt(param2.trim());
                    String friendWxid = getFriendWxidByIndex(friendIndex);
                    if (friendWxid != null) {
                        getNote(talker, noteIndex, friendWxid);
                    } else {
                        sendText(talker, "无效的好友编号");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "编号无效，请输入数字");
                }
            }
        } else if ("/删除".equals(command)) {
            if (param1 == null) {
                sendText(talker, "请提供笔记编号，格式：/删除 编号 或 /删除 好友编号 笔记编号");
                return;
            }
            if (param2 == null) {
                try {
                    int noteIndex = Integer.parseInt(param1.trim());
                    delNote(talker, noteIndex, myWxid);
                } catch (NumberFormatException e) {
                    sendText(talker, "笔记编号无效，请输入数字");
                }
            } else {
                try {
                    int friendIndex = Integer.parseInt(param1.trim());
                    int noteIndex = Integer.parseInt(param2.trim());
                    String friendWxid = getFriendWxidByIndex(friendIndex);
                    if (friendWxid != null) {
                        delNote(talker, noteIndex, friendWxid);
                    } else {
                        sendText(talker, "无效的好友编号");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "编号无效，请输入数字");
                }
            }
        } else if ("/清空".equals(command)) {
            if (param1 == null) {
                clearNotes(talker, myWxid);
            } else {
                try {
                    int friendIndex = Integer.parseInt(param1.trim());
                    String friendWxid = getFriendWxidByIndex(friendIndex);
                    if (friendWxid != null) {
                        clearNotes(talker, friendWxid);
                    } else {
                        sendText(talker, "无效的好友编号");
                    }
                } catch (NumberFormatException e) {
                    sendText(talker, "好友编号无效，请输入数字");
                }
            }
        }
    } else {
        // 授权好友：只能操作自己的笔记
        if ("/笔记".equals(command)) {
            int page = 1;
            if (param1 != null) {
                try {
                    page = Integer.parseInt(param1);
                } catch (NumberFormatException e) {
                    sendText(talker, "页码无效，请输入数字");
                    return;
                }
            }
            listNotesWithPaging(talker, sender, page);
        } else if ("/查看".equals(command)) {
            if (param1 == null) {
                sendText(talker, "请提供笔记编号，格式：/查看 编号");
            } else {
                try {
                    int noteIndex = Integer.parseInt(param1.trim());
                    getNote(talker, noteIndex, sender);
                } catch (NumberFormatException e) {
                    sendText(talker, "编号无效，请输入数字");
                }
            }
        } else if ("/删除".equals(command)) {
            if (param1 == null) {
                sendText(talker, "请提供笔记编号，格式：/删除 编号");
            } else {
                try {
                    int noteIndex = Integer.parseInt(param1.trim());
                    delNote(talker, noteIndex, sender);
                } catch (NumberFormatException e) {
                    sendText(talker, "编号无效，请输入数字");
                }
            }
        } else if ("/清空".equals(command)) {
            clearNotes(talker, sender);
        }
    }
}

String getFriendWxidByIndex(int index) {
    try {
        List friends = getFriendList();
        if (friends == null || index < 1 || index > friends.size()) return null;
        Object friend = friends.get(index-1);
        return getWxidFromFriendSafe(friend);
    } catch (Exception e) {
        log("getFriendWxidByIndex 异常: " + e.toString());
        return null;
    }
}

// ========== 笔记存储（分页）=========
int getNoteCount(String ownerWxid) {
    return getInt("note_count_" + ownerWxid, 0);
}
void setNoteCount(String ownerWxid, int count) {
    putInt("note_count_" + ownerWxid, count);
}
String getNote(String ownerWxid, int index) {
    return getString("note_" + ownerWxid + "_" + index, "");
}
void setNote(String ownerWxid, int index, String value) {
    if (value == null) value = "";
    putString("note_" + ownerWxid + "_" + index, value);
}

void addNote(String talker, String content, String ownerWxid) {
    int count = getNoteCount(ownerWxid);
    int newIndex = count + 1;
    String timestamp = String.valueOf(System.currentTimeMillis());
    String note = timestamp + ":" + content;
    setNote(ownerWxid, newIndex, note);
    setNoteCount(ownerWxid, newIndex);
    sendText(talker, "✅ 笔记添加成功，编号：" + newIndex);
}

void listNotesWithPaging(String talker, String ownerWxid, int page) {
    int count = getNoteCount(ownerWxid);
    if (count == 0) {
        sendText(talker, "📭 暂无笔记");
        return;
    }
    int pageSize = 10;
    int totalPages = (count + pageSize - 1) / pageSize;
    if (page < 1) page = 1;
    if (page > totalPages) page = totalPages;
    int start = (page - 1) * pageSize + 1;
    int end = Math.min(start + pageSize - 1, count);

    String ownerName = getFriendName(ownerWxid);
    if (ownerName == null) ownerName = ownerWxid;
    StringBuilder sb = new StringBuilder("📒 用户[" + ownerName + "]的笔记列表（第" + page + "/" + totalPages + "页）：\n");
    for (int i = start; i <= end; i++) {
        String note = getNote(ownerWxid, i);
        String display = note;
        int colonIdx = note.indexOf(':');
        if (colonIdx > 0) {
            display = note.substring(colonIdx + 1);
        }
        if (display.length() > 20) {
            display = display.substring(0, 20) + "...";
        }
        sb.append(i).append(": ").append(display).append("\n");
    }
    sb.append("使用 /笔记 ").append(ownerWxid.equals(getLoginWxid()) ? "" : "好友编号 ").append("下一页页码 查看更多");
    sendText(talker, sb.toString());
}

void getNote(String talker, int index, String ownerWxid) {
    int count = getNoteCount(ownerWxid);
    if (index < 1 || index > count) {
        sendText(talker, "❌ 编号不存在");
        return;
    }
    String note = getNote(ownerWxid, index);
    String timestamp = "";
    String content = note;
    int colonIdx = note.indexOf(':');
    if (colonIdx > 0) {
        timestamp = note.substring(0, colonIdx);
        content = note.substring(colonIdx + 1);
    }
    String timeStr = "";
    try {
        long ts = Long.parseLong(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        timeStr = sdf.format(new Date(ts));
    } catch (Exception e) {
        timeStr = timestamp;
    }
    sendText(talker, "编号：" + index + "\n📅 时间：" + timeStr + "\n📝 内容：" + content);
}

void delNote(String talker, int index, String ownerWxid) {
    int count = getNoteCount(ownerWxid);
    if (index < 1 || index > count) {
        sendText(talker, "❌ 编号不存在");
        return;
    }
    for (int i = index; i < count; i++) {
        String nextNote = getNote(ownerWxid, i + 1);
        setNote(ownerWxid, i, nextNote);
    }
    setNote(ownerWxid, count, "");
    setNoteCount(ownerWxid, count - 1);
    sendText(talker, "✅ 笔记删除成功");
}

void clearNotes(String talker, String ownerWxid) {
    int count = getNoteCount(ownerWxid);
    for (int i = 1; i <= count; i++) {
        setNote(ownerWxid, i, "");
    }
    setNoteCount(ownerWxid, 0);
    sendText(talker, "✅ 所有笔记已清空");
}