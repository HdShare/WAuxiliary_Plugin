// ==================== 微信自动管理插件 ====================
// 功能：
// 1. 自动通过好友申请并发送欢迎消息
// 2. 群聊邀请功能（根据关键词自动邀请用户进群）
// 3. 入群/退群提示消息

import java.util.HashMap;
import java.util.Map;

// ==================== 配置方法 ====================
/**
 * 获取插件配置
 */
Map getPluginConfig() {
    Map config = new HashMap();
    
    // === �� 群组配置中心 ===
    // ⚠️ 重要：以下所有群组ID均为示例，使用前必须替换为您的真实群组ID
    config.put("LOG_GROUP_ID", "your_log_group_id@chatroom");
    
    String[] userGroups = {
        "your_group1_id@chatroom", // 群组1
        "your_group2_id@chatroom", // 群组2
        "your_group3_id@chatroom", // 群组3  
        "your_group4_id@chatroom"  // 群组4
    };
    config.put("USER_GROUPS", userGroups);
    
    // 群组名称映射
    Map groupNameMap = new HashMap();
    groupNameMap.put("your_group1_id@chatroom", "群组1");
    groupNameMap.put("your_group2_id@chatroom", "群组2");
    groupNameMap.put("your_group3_id@chatroom", "群组3");
    groupNameMap.put("your_group4_id@chatroom", "群组4");
    groupNameMap.put("your_log_group_id@chatroom", "管理日志群");
    config.put("GROUP_NAME_MAP", groupNameMap);
    
    // === 🤝 好友管理配置 ===
    config.put("WELCOME_MESSAGE", "✨ 你好呀～很高兴通过你的好友申请！\n\n有什么想问的或需要帮忙的，尽管说，不用太客气😉 我看到消息会第一时间回复～\n\n🏎️ 想进群？回复「加群」就可以啦～\n\n🤖 （本消息为自动回复）");
    
    // === 🎯 群邀请配置 ===
    // ⚠️ 重要：请将触发关键词修改为您需要的关键词
    config.put("TRIGGER_KEYWORD", "加群");
    config.put("MAX_GROUP_MEMBERS", 500);
    
    config.put("CONFIRM_MESSAGE", "📩 群聊邀请已发送，请注意查收。\n\n❗ 温馨提示：由于微信群环境较为复杂，请您务必提高防范意识，切勿轻信涉及资金往来等操作。\n\n📌 请关注我们的相关信息渠道，以防群聊被封后无法联系。\n\n（本消息为自动回复）");
    config.put("ERROR_MESSAGE", "抱歉，群聊邀请发送失败，请稍后重试。");
    config.put("FULL_GROUP_MESSAGE", "抱歉，所有群组都已满员，暂时无法发送邀请，请稍后重试。");
    
    // === 💬 群提示配置 ===
    config.put("ENABLE_JOIN_TIPS", true);
    config.put("ENABLE_LEFT_TIPS", false);
    config.put("ENABLE_AT_USER", true);
    
    config.put("JOIN_MESSAGE", "[AtWx={userWxid}]\n🎉 欢迎 {userName} 加入我们的大家庭！\n\n📋 请阅读群公告，遵守群规\n💬 如有问题可以私聊管理员\n\n祝您在群里玩得开心！");
    config.put("LEFT_MESSAGE", "😢 {userName} 离开了群聊，我们会想念你的！\n\n期待您再次回来！");
    
    // === 📋 日志配置 ===
    config.put("ENABLE_DETAILED_LOG", true);
    
    return config;
}

/**
 * 获取当前时间戳
 */
String getCurrentTime() {
    return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        .format(new java.util.Date());
}

/**
 * 根据群ID获取群名称
 */
String getGroupName(String groupWxid) {
    Map config = getPluginConfig();
    Map groupNameMap = (Map) config.get("GROUP_NAME_MAP");
    String groupName = (String) groupNameMap.get(groupWxid);
    return groupName != null ? groupName : "未知群组(" + groupWxid + ")";
}

/**
 * 向日志群组发送带标签和时间的日志消息
 */
void sendLog(String tag, String message) {
    try {
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        if (logGroupId != null && !logGroupId.isEmpty()) {
            String time = getCurrentTime();
            String logMessage = tag + " " + time + " " + message;
            sendText(logGroupId, logMessage);
        }
    } catch (Exception e) {
        // 如果发送日志失败，静默处理，避免循环错误
    }
}

/**
 * 发送错误日志
 */
void sendErrorLog(String message) {
    sendLog("#报错", message);
}

/**
 * 发送功能日志
 */
void sendFunctionLog(String message) {
    sendLog("#功能", message);
}

/**
 * 发送信息日志
 */
void sendInfoLog(String message) {
    sendLog("#信息", message);
}

/**
 * 向日志群组发送详细日志消息
 */
void sendDetailedLog(String title, String content) {
    try {
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        if (logGroupId != null && !logGroupId.isEmpty()) {
            String time = getCurrentTime();
            String message = "#信息 " + time + " " + title + "\n" + content;
            sendText(logGroupId, message);
        }
    } catch (Exception e) {
        // 如果发送日志失败，静默处理，避免循环错误
    }
}

// ==================== 好友管理功能 ====================
void onNewFriend(String wxid, String ticket, int scene) {
    try {
        // 首次触发时发送启动日志
        sendStartupLogIfNeeded();
        
        Map config = getPluginConfig();
        String welcomeMessage = (String) config.get("WELCOME_MESSAGE");
        boolean enableDetailedLog = (Boolean) config.get("ENABLE_DETAILED_LOG");
        
        // 记录开始处理好友申请
        sendInfoLog("检测到好友申请: wxid=" + wxid + ", scene=" + scene);
        
        // 自动通过好友申请
        verifyUser(wxid, ticket, scene);
        sendFunctionLog("已自动通过好友申请: " + wxid);
        
        // 异步发送欢迎消息，避免阻塞主线程
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 等待一段时间确保好友关系建立
                    Thread.sleep(2000);
                    
                    // 发送欢迎消息
                    sendText(wxid, welcomeMessage);
                    sendFunctionLog("已发送欢迎消息给: " + wxid);
                    
                    // 详细日志记录
                    if (enableDetailedLog) {
                        logDetailedFriendInfo(wxid, scene);
                    }
                } catch (Exception e) {
                    sendErrorLog("发送欢迎消息异常: " + e.getMessage());
                }
            }
        }).start();
        
    } catch (Exception e) {
        sendErrorLog("处理好友申请异常: " + e.getMessage());
    }
}

/**
 * 记录详细的好友申请信息
 */
void logDetailedFriendInfo(String wxid, int scene) {
    try {
        Map config = getPluginConfig();
        String welcomeMessage = (String) config.get("WELCOME_MESSAGE");
        
        // 获取好友昵称
        String friendName = getFriendName(wxid);
        if (friendName == null || friendName.isEmpty()) {
            friendName = "未知用户";
        }
        
        // 获取当前时间
        String currentTime = getCurrentTime();
        
        StringBuilder logContent = new StringBuilder();
        logContent.append("=== 详细好友申请信息 ===\n");
        logContent.append("时间: ").append(currentTime).append("\n");
        logContent.append("用户ID: ").append(wxid).append("\n");
        logContent.append("用户昵称: ").append(friendName).append("\n");
        logContent.append("申请场景: ").append(scene).append("\n");
        logContent.append("欢迎消息: ").append(welcomeMessage.substring(0, Math.min(50, welcomeMessage.length()))).append("...").append("\n");
        logContent.append("======================");
        
        sendDetailedLog("新好友申请", logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("获取详细好友申请信息失败: " + e.getMessage());
    }
}

// ==================== 群邀请功能 ====================
void onHandleMsg(Object msgInfoBean) {
    // 如果是发送的消息，直接返回
    if (msgInfoBean.isSend()) return;
    
    // 处理文本消息
    if (msgInfoBean.isText()) {
        // 首次触发时发送启动日志
        sendStartupLogIfNeeded();
        
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        
        // 获取消息内容和发送者
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();
        
        // 检查是否是日志群的 /info 命令
        if (msgInfoBean.isGroupChat() && talker.equals(logGroupId) && content != null && content.trim().equals("/info")) {
            handleInfoCommand();
            return;
        }
        
        // 只处理私聊消息中的群邀请关键词
        if (!msgInfoBean.isGroupChat()) {
            String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
            
            if (content.equals(triggerKeyword)) {
                handleGroupInviteRequest(talker);
            }
        }
    }
}

/**
 * 处理 /info 命令，重新发送完整的初始化信息
 */
void handleInfoCommand() {
    // 异步发送完整的状态信息
    new Thread(new Runnable() {
        public void run() {
            try {
                // 调用共用的状态信息生成方法
                String statusMessage = generateStatusMessage("📊 微信自动管理插件 - 当前状态信息", "🔧 命令触发: /info", "💡 提示: 输入 /info 可随时查看状态信息");
                
                Map config = getPluginConfig();
                String logGroupId = (String) config.get("LOG_GROUP_ID");
                
                // 发送完整的状态信息
                sendText(logGroupId, statusMessage);
                
                // 记录命令使用日志
                sendInfoLog("/info 命令执行完成");
                
            } catch (Exception e) {
                sendErrorLog("/info 命令执行失败: " + e.getMessage());
            }
        }
    }).start();
}

/**
 * 处理群邀请请求
 */
void handleGroupInviteRequest(String talker) {
    try {
        Map config = getPluginConfig();
        String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
        String confirmMessage = (String) config.get("CONFIRM_MESSAGE");
        String errorMessage = (String) config.get("ERROR_MESSAGE");
        String fullGroupMessage = (String) config.get("FULL_GROUP_MESSAGE");
        boolean enableDetailedLog = (Boolean) config.get("ENABLE_DETAILED_LOG");
        
        // 记录触发日志
        sendInfoLog("检测到关键词触发 - 用户: " + talker + ", 关键词: " + triggerKeyword);
        
        // 获取合适的群组（人数不超过500人）
        String suitableGroupId = getSuitableGroupId();
        
        // 检查是否找到合适的群组
        if (suitableGroupId != null && !suitableGroupId.isEmpty()) {
            // 获取群名称（一次获取，多次使用）
            String groupName = getGroupName(suitableGroupId);
            
            // 第一步：发送群聊邀请
            sendFunctionLog("开始发送群聊邀请 - 用户: " + talker + ", 目标群: " + groupName + "(" + suitableGroupId + ")");
            inviteChatroomMember(suitableGroupId, talker);
            
            // 记录邀请发送成功日志
            sendFunctionLog("群聊邀请发送成功 - 用户: " + talker + ", 目标群: " + groupName + "(" + suitableGroupId + ")");
            
            // 第二步：发送确认消息给用户
            sendInfoLog("发送确认消息给用户: " + talker);
            sendText(talker, confirmMessage);
            
            sendFunctionLog("完整流程执行成功 - 用户: " + talker);
            
            // 详细日志记录
            if (enableDetailedLog) {
                logDetailedInviteInfo(talker, suitableGroupId);
            }
        } else {
            sendErrorLog("无法找到合适的群组（所有群都已满员）");
            // 发送错误提示给用户
            sendText(talker, fullGroupMessage);
        }
    } catch (Exception e) {
        Map config = getPluginConfig();
        String errorMessage = (String) config.get("ERROR_MESSAGE");
        // 记录错误日志
        sendErrorLog("群聊邀请发送失败 - 用户: " + talker + ", 错误: " + e.getMessage());
        // 发送错误提示给用户
        sendText(talker, errorMessage);
    }
}

/**
 * 获取合适的群组ID（随机选择人数不超过500人的群）
 * @return 合适的群组ID，如果所有群都满员则返回null
 */
String getSuitableGroupId() {
    Map config = getPluginConfig();
    String[] userGroups = (String[]) config.get("USER_GROUPS");
    int maxGroupMembers = (Integer) config.get("MAX_GROUP_MEMBERS");
    
    if (userGroups == null || userGroups.length == 0) {
        sendErrorLog("警告：用户群组数组为空");
        return null;
    }
    
    // 第一步：收集所有可用的群组
    java.util.List availableGroups = new java.util.ArrayList();
    
    sendInfoLog("开始扫描所有群组，寻找可用的群...");
    
    for (int i = 0; i < userGroups.length; i++) {
        String groupId = userGroups[i];
        String groupName = getGroupName(groupId);
        
        sendInfoLog("检查群组 " + (i + 1) + "/" + userGroups.length + " - " + groupName);
        
        try {
            // 获取群成员数量
            int memberCount = getGroupMemberCount(groupId);
            sendInfoLog("群组 " + groupName + " 当前人数: " + memberCount + "/" + maxGroupMembers);
            
            // 检查群人数是否未满
            if (memberCount < maxGroupMembers) {
                availableGroups.add(groupId);
                sendInfoLog("群组 " + groupName + " 可用，已加入候选列表 (人数: " + memberCount + "/" + maxGroupMembers + ")");
            } else {
                sendInfoLog("群组 " + groupName + " 已满员，跳过");
            }
        } catch (Exception e) {
            sendErrorLog("获取群组 " + groupName + " 人数失败: " + e.getMessage());
            // 获取失败的群组不加入候选列表
        }
    }
    
    // 第二步：从可用群组中随机选择一个
    if (availableGroups.size() == 0) {
        sendErrorLog("所有群组都已满员或无法访问，无法找到合适的群组");
        return null;
    }
    
    sendInfoLog("找到 " + availableGroups.size() + " 个可用群组，开始随机选择...");
    
    // 使用当前时间作为随机种子，生成随机索引
    java.util.Random random = new java.util.Random();
    int randomIndex = random.nextInt(availableGroups.size());
    String selectedGroupId = (String) availableGroups.get(randomIndex);
    String selectedGroupName = getGroupName(selectedGroupId);
    
    // 再次获取选中群组的成员数量，用于日志记录
    try {
        int memberCount = getGroupMemberCount(selectedGroupId);
        sendFunctionLog("随机选择群组 - " + selectedGroupName + " (第" + (randomIndex + 1) + "/" + availableGroups.size() + "个, 人数: " + memberCount + "/" + maxGroupMembers + ")");
    } catch (Exception e) {
        sendFunctionLog("随机选择群组 - " + selectedGroupName + " (第" + (randomIndex + 1) + "/" + availableGroups.size() + "个, 人数获取失败)");
    }
    
    return selectedGroupId;
}

/**
 * 记录详细的邀请信息
 */
void logDetailedInviteInfo(String userWxid, String targetGroupId) {
    try {
        Map config = getPluginConfig();
        int maxGroupMembers = (Integer) config.get("MAX_GROUP_MEMBERS");
        String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
        
        // 获取群成员数量
        int memberCount = getGroupMemberCount(targetGroupId);
        
        // 获取当前时间
        String currentTime = getCurrentTime();
        
        StringBuilder logContent = new StringBuilder();
        // 获取群名称
        String groupName = getGroupName(targetGroupId);
        
        logContent.append("=== 详细邀请信息 ===\n");
        logContent.append("时间: ").append(currentTime).append("\n");
        logContent.append("用户ID: ").append(userWxid).append("\n");
        logContent.append("群名称: ").append(groupName).append("\n");
        logContent.append("群ID: ").append(targetGroupId).append("\n");
        logContent.append("目标群成员数: ").append(memberCount).append("\n");
        logContent.append("群人数上限: ").append(maxGroupMembers).append("\n");
        logContent.append("触发关键词: ").append(triggerKeyword).append("\n");
        logContent.append("==================");
        
        sendDetailedLog("群聊邀请成功", logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("获取详细邀请信息失败: " + e.getMessage());
    }
}

// ==================== 群提示功能 ====================
void onMemberChange(String type, String groupWxid, String userWxid, String userName) {
    // 检查是否在目标群组中
    if (!isTargetGroup(groupWxid)) {
        return;
    }
    
    // 首次触发时发送启动日志
    sendStartupLogIfNeeded();
    
    // 记录基础日志
    String groupName = getGroupName(groupWxid);
    sendInfoLog("检测到成员变动 - 类型: " + type + ", 群: " + groupName + "(" + groupWxid + "), 用户: " + userName + " (" + userWxid + ")");
    
    if (type.equals("join")) {
        handleMemberJoin(groupWxid, userWxid, userName);
    } else if (type.equals("left")) {
        handleMemberLeft(groupWxid, userWxid, userName);
    }
}

/**
 * 处理新成员加入
 */
void handleMemberJoin(String groupWxid, String userWxid, String userName) {
    Map config = getPluginConfig();
    boolean enableJoinTips = (Boolean) config.get("ENABLE_JOIN_TIPS");
    boolean enableDetailedLog = (Boolean) config.get("ENABLE_DETAILED_LOG");
    
    if (!enableJoinTips) {
        sendInfoLog("入群提示已禁用，跳过处理");
        return;
    }
    
    try {
        // 构建欢迎消息
        String welcomeMessage = buildJoinMessage(userWxid, userName);
        
        // 发送欢迎消息
        sendText(groupWxid, welcomeMessage);
        
        // 记录成功日志
        String groupName = getGroupName(groupWxid);
        sendFunctionLog("欢迎消息发送成功 - 群: " + groupName + "(" + groupWxid + "), 用户: " + userName);
        
        // 详细日志记录
        if (enableDetailedLog) {
            logDetailedJoinInfo(groupWxid, userWxid, userName);
        }
        
    } catch (Exception e) {
        sendErrorLog("发送欢迎消息失败: " + e.getMessage());
    }
}

/**
 * 处理成员离开
 */
void handleMemberLeft(String groupWxid, String userWxid, String userName) {
    Map config = getPluginConfig();
    boolean enableLeftTips = (Boolean) config.get("ENABLE_LEFT_TIPS");
    boolean enableDetailedLog = (Boolean) config.get("ENABLE_DETAILED_LOG");
    
    if (!enableLeftTips) {
        sendInfoLog("退群提示已禁用，跳过处理");
        return;
    }
    
    try {
        // 构建离开消息
        String leftMessage = buildLeftMessage(userName);
        
        // 发送离开消息
        sendText(groupWxid, leftMessage);
        
        // 记录成功日志
        String groupName = getGroupName(groupWxid);
        sendFunctionLog("离开消息发送成功 - 群: " + groupName + "(" + groupWxid + "), 用户: " + userName);
        
        // 详细日志记录
        if (enableDetailedLog) {
            logDetailedLeftInfo(groupWxid, userWxid, userName);
        }
        
    } catch (Exception e) {
        sendErrorLog("发送离开消息失败: " + e.getMessage());
    }
}

/**
 * 构建入群欢迎消息
 */
String buildJoinMessage(String userWxid, String userName) {
    Map config = getPluginConfig();
    String joinMessage = (String) config.get("JOIN_MESSAGE");
    boolean enableAtUser = (Boolean) config.get("ENABLE_AT_USER");
    
    String message = joinMessage;
    
    // 替换变量
    message = message.replace("{userWxid}", userWxid);
    message = message.replace("{userName}", userName);
    
    // 处理@功能
    if (enableAtUser) {
        // 确保消息开头包含@语法
        if (!message.contains("[AtWx=")) {
            message = "[AtWx=" + userWxid + "]\n" + message;
        }
    } else {
        // 移除@语法
        message = message.replaceAll("\\[AtWx=[^\\]]+\\]\\s*", "");
    }
    
    return message.trim();
}

/**
 * 构建退群离开消息
 */
String buildLeftMessage(String userName) {
    Map config = getPluginConfig();
    String leftMessage = (String) config.get("LEFT_MESSAGE");
    
    String message = leftMessage;
    
    // 替换变量
    message = message.replace("{userName}", userName);
    
    return message.trim();
}

/**
 * 检查是否为目标群组
 */
boolean isTargetGroup(String groupWxid) {
    Map config = getPluginConfig();
    String[] userGroups = (String[]) config.get("USER_GROUPS");
    
    // 如果用户群组数组为空，则监控所有群
    if (userGroups == null || userGroups.length == 0) {
        return true;
    }
    
    // 检查是否在用户群组列表中
    for (String targetGroup : userGroups) {
        if (targetGroup.equals(groupWxid)) {
            return true;
        }
    }
    
    return false;
}

/**
 * 记录详细的入群信息
 */
void logDetailedJoinInfo(String groupWxid, String userWxid, String userName) {
    try {
        // 获取群成员数量
        int memberCount = getGroupMemberCount(groupWxid);
        
        // 获取当前时间
        String currentTime = getCurrentTime();
        
        StringBuilder logContent = new StringBuilder();
        // 获取群名称
        String groupName = getGroupName(groupWxid);
        
        logContent.append("=== 详细入群信息 ===\n");
        logContent.append("时间: ").append(currentTime).append("\n");
        logContent.append("群名称: ").append(groupName).append("\n");
        logContent.append("群ID: ").append(groupWxid).append("\n");
        logContent.append("用户ID: ").append(userWxid).append("\n");
        logContent.append("用户昵称: ").append(userName).append("\n");
        logContent.append("群成员数: ").append(memberCount).append("\n");
        logContent.append("================");
        
        sendDetailedLog("新成员入群", logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("获取详细入群信息失败: " + e.getMessage());
    }
}

/**
 * 记录详细的退群信息
 */
void logDetailedLeftInfo(String groupWxid, String userWxid, String userName) {
    try {
        // 获取群成员数量
        int memberCount = getGroupMemberCount(groupWxid);
        
        // 获取当前时间
        String currentTime = getCurrentTime();
        
        StringBuilder logContent = new StringBuilder();
        // 获取群名称
        String groupName = getGroupName(groupWxid);
        
        logContent.append("=== 详细退群信息 ===\n");
        logContent.append("时间: ").append(currentTime).append("\n");
        logContent.append("群名称: ").append(groupName).append("\n");
        logContent.append("群ID: ").append(groupWxid).append("\n");
        logContent.append("用户ID: ").append(userWxid).append("\n");
        logContent.append("用户昵称: ").append(userName).append("\n");
        logContent.append("群成员数: ").append(memberCount).append("\n");
        logContent.append("================");
        
        sendDetailedLog("成员退群", logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("获取详细退群信息失败: " + e.getMessage());
    }
}

// ==================== 状态信息功能 ====================
/**
 * 生成状态信息
 */
String generateStatusMessage(String title, String extraInfo, String footerTip) {
    Map config = getPluginConfig();
    String logGroupId = (String) config.get("LOG_GROUP_ID");
    String[] userGroups = (String) config.get("USER_GROUPS");
    String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
    int maxGroupMembers = (Integer) config.get("MAX_GROUP_MEMBERS");
    boolean enableJoinTips = (Boolean) config.get("ENABLE_JOIN_TIPS");
    boolean enableLeftTips = (Boolean) config.get("ENABLE_LEFT_TIPS");
    boolean enableAtUser = (Boolean) config.get("ENABLE_AT_USER");
    boolean enableDetailedLog = (Boolean) config.get("ENABLE_DETAILED_LOG");
    
    StringBuilder message = new StringBuilder();
    
    // 标题和基本信息
    message.append("#信息 ").append(getCurrentTime()).append(" 🚀 ").append(title).append("\n\n");
    
    message.append("📋 插件版本: v1.0\n");
    message.append("⏰ 查询时间: ").append(getCurrentTime()).append("\n");
    if (extraInfo != null && !extraInfo.isEmpty()) {
        message.append(extraInfo).append("\n");
    }
    message.append("\n");
    
    // 功能配置状态
    message.append("=== 🔧 功能配置状态 ===\n");
    message.append("好友自动通过: ✅ 启用\n");
    message.append("群邀请功能: ✅ 启用 (关键词: ").append(triggerKeyword).append(")\n");
    message.append("入群提示: ").append(enableJoinTips ? "✅ 启用" : "❌ 禁用").append("\n");
    message.append("退群提示: ").append(enableLeftTips ? "✅ 启用" : "❌ 禁用").append("\n");
    message.append("@用户功能: ").append(enableAtUser ? "✅ 启用" : "❌ 禁用").append("\n");
    message.append("详细日志: ").append(enableDetailedLog ? "✅ 启用" : "❌ 禁用").append("\n");
    message.append("\n");
    
    // 群组配置信息
    message.append("=== 📊 群组配置信息 ===\n");
    String logGroupName = getGroupName(logGroupId);
    message.append("日志群组: ").append(logGroupName).append("(").append(logGroupId).append(")\n");
    message.append("群人数上限: ").append(maxGroupMembers).append(" 人\n");
    message.append("用户群组数量: ").append(userGroups.length).append(" 个\n");
    message.append("\n");
    
    // 用户群组列表及人员统计
    message.append("=== 🏘️ 用户群组列表及人员统计 ===\n");
    int totalUsers = 0;
    int availableGroups = 0;
    int fullGroups = 0;
    
    for (int i = 0; i < userGroups.length; i++) {
        String groupId = userGroups[i];
        String groupName = getGroupName(groupId);
        
        try {
            int memberCount = getGroupMemberCount(groupId);
            double usagePercentage = ((double) memberCount / maxGroupMembers) * 100;
            
            message.append((i + 1)).append(". ").append(groupName);
            message.append(" - 人数: ").append(memberCount).append("/").append(maxGroupMembers);
            message.append(" (").append(String.format("%.0f", usagePercentage)).append("%) ");
            
            if (memberCount >= maxGroupMembers) {
                message.append("🔴 已满");
                fullGroups++;
            } else {
                message.append("🟢 可用");
                availableGroups++;
            }
            message.append("\n");
            
            totalUsers += memberCount;
        } catch (Exception e) {
            message.append((i + 1)).append(". ").append(groupName);
            message.append(" - ❌ 获取失败 (").append(e.getMessage()).append(")\n");
        }
    }
    message.append("\n");
    
    // 群组统计汇总
    message.append("=== 📊 群组统计汇总 ===\n");
    message.append("总群数: ").append(userGroups.length).append(" 个\n");
    message.append("可用群: ").append(availableGroups).append(" 个 🟢\n");
    message.append("已满群: ").append(fullGroups).append(" 个 🔴\n");
    message.append("监控总人数: ").append(totalUsers).append(" 人\n");
    if (userGroups.length > 0) {
        int avgUsers = totalUsers / userGroups.length;
        message.append("平均群人数: ").append(avgUsers).append(" 人\n");
    }
    message.append("\n");
    
    message.append("=============================\n");
    if (footerTip != null && !footerTip.isEmpty()) {
        message.append(footerTip);
    } else {
        message.append("✅ 插件运行正常，所有功能已就绪！");
    }
    
    return message.toString();
}

// ==================== 启动日志功能 ====================
/**
 * 立即发送插件启动的基础日志
 */
void sendBasicStartupLog() {
    // 异步发送基础启动日志，避免阻塞主线程
    new Thread(new Runnable() {
        public void run() {
            try {
                // 调用共用的状态信息生成方法
                String statusMessage = generateStatusMessage("🚀 微信自动管理插件已启动", "📋 插件版本: v1.0\n⏰ 初始化时间: " + getCurrentTime(), "✅ 插件初始化完成，所有功能已就绪！");
                
                Map config = getPluginConfig();
                String logGroupId = (String) config.get("LOG_GROUP_ID");
                
                // 发送完整的状态信息
                sendText(logGroupId, statusMessage);
                
            } catch (Exception e) {
                sendErrorLog("发送启动日志失败: " + e.getMessage());
            }
        }
    }).start();
}

private static boolean hasStartupLogSent = false;

/**
 * 如果未发送过，则发送启动日志
 */
void sendStartupLogIfNeeded() {
    if (!hasStartupLogSent) {
        synchronized (this) {
            if (!hasStartupLogSent) {
                hasStartupLogSent = true;
                
                // 延迟3秒发送启动日志，确保插件完全加载
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(3000); // 等待3秒
                            sendBasicStartupLog();
                        } catch (Exception e) {
                            sendErrorLog("延迟发送启动日志失败: " + e.getMessage());
                        }
                    }
                }).start();
            }
        }
    }
} 