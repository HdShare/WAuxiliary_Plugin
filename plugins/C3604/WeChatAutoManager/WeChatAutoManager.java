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
    
    // === 📱 群组配置中心 ===
    config.put("LOG_GROUP_ID", "12345678901@chatroom");
    
    String[] userGroups = {
        "11111111111@chatroom", // 示例群1
        "22222222222@chatroom", // 示例群2
        "33333333333@chatroom", // 示例群3  
        "44444444444@chatroom"  // 示例群4
    };
    config.put("USER_GROUPS", userGroups);
    
    // 群组名称映射
    Map groupNameMap = new HashMap();
    groupNameMap.put("11111111111@chatroom", "示例群1");
    groupNameMap.put("22222222222@chatroom", "示例群2");
    groupNameMap.put("33333333333@chatroom", "示例群3");
    groupNameMap.put("44444444444@chatroom", "示例群4");
    groupNameMap.put("12345678901@chatroom", "管理日志群");
    config.put("GROUP_NAME_MAP", groupNameMap);
    
    // === 🤝 好友管理配置 ===
    config.put("WELCOME_MESSAGE", "✨ 你好呀～很高兴通过你的好友申请！\n\n有什么想问的或需要帮忙的，尽管说，不用太客气😉 我看到消息会第一时间回复～\n\n🏎️ 想进群？回复「加群」就可以啦～\n\n🤖 （本消息为自动回复）");
    
    // === 🎯 群邀请配置 ===
    config.put("TRIGGER_KEYWORD", "加群");
    config.put("MAX_GROUP_MEMBERS", 500);
    
    config.put("CONFIRM_MESSAGE", "📩 群聊邀请已发送，请注意查收。\n\n❗ 温馨提示：由于微信群环境较为复杂，请您务必提高防范意识，切勿轻信涉及资金往来等操作。\n\n📌 请关注微信公众号【示例公众号】，以防群聊被封后无法联系。届时我们将通过公众号推送最新群信息。\n\n（本消息为自动回复）");
    config.put("ERROR_MESSAGE", "抱歉，群聊邀请发送失败，请稍后重试。");
    config.put("FULL_GROUP_MESSAGE", "抱歉，所有群组都已满员，暂时无法发送邀请，请稍后重试。");
    
    // === 💬 群提示配置 ===
    config.put("ENABLE_JOIN_TIPS", true);
    config.put("ENABLE_LEFT_TIPS", false);
    config.put("ENABLE_AT_USER", true);
    
    config.put("JOIN_MESSAGE", "[AtWx={userWxid}]\n🎉 欢迎 {userName} 加入我们的大家庭！\n\n📋 请阅读群公告，遵守群规\n💬 如有问题可以私聊管理员\n\n祝您在群里玩得开心！");
    config.put("LEFT_MESSAGE", "😢 {userName} 离开了群聊，我们会想念你的！\n\n期待您再次回来！");
    
    // === 📋 日志配置 ===
    // 已优化为汇总日志模式，无需详细日志配置
    
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
        
        // 自动通过好友申请
        verifyUser(wxid, ticket, scene);
        
        // 异步发送欢迎消息，避免阻塞主线程
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 等待一段时间确保好友关系建立
                    Thread.sleep(2000);
                    
                    // 发送欢迎消息
                    sendText(wxid, welcomeMessage);
                    
                    // 发送汇总日志
                    sendFriendSummaryLog(wxid, scene, true, null);
                    
                } catch (Exception e) {
                    // 发送失败日志
                    sendFriendSummaryLog(wxid, scene, false, e.getMessage());
                }
            }
        }).start();
        
    } catch (Exception e) {
        sendErrorLog("处理好友申请异常: " + e.getMessage());
    }
}

/**
 * 发送好友申请处理汇总日志
 */
void sendFriendSummaryLog(String wxid, int scene, boolean success, String errorMsg) {
    try {
        // 获取好友昵称
        String friendName = getFriendName(wxid);
        if (friendName == null || friendName.isEmpty()) {
            friendName = "未知用户";
        }
        
        // 构建汇总日志
        String currentTime = getCurrentTime();
        StringBuilder logContent = new StringBuilder();
        
        if (success) {
            logContent.append("#功能 ").append(currentTime)
                    .append(" 🤝 好友申请监控-完成处理")
                    .append("\n✅ 用户: ").append(friendName).append("(").append(wxid).append(")")
                    .append("\n✅ 场景: ").append(scene)
                    .append("\n✅ 已通过好友申请")
                    .append("\n✅ 已发送欢迎消息");
        } else {
            logContent.append("#报错 ").append(currentTime)
                    .append(" ❌ 好友申请监控-处理失败")
                    .append("\n🔍 用户: ").append(friendName).append("(").append(wxid).append(")")
                    .append("\n🔍 场景: ").append(scene)
                    .append("\n❌ 错误: ").append(errorMsg != null ? errorMsg : "未知错误");
        }
        
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        sendText(logGroupId, logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("发送好友申请汇总日志失败: " + e.getMessage());
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
        String confirmMessage = (String) config.get("CONFIRM_MESSAGE");
        String errorMessage = (String) config.get("ERROR_MESSAGE");
        String fullGroupMessage = (String) config.get("FULL_GROUP_MESSAGE");
        
        // 获取合适的群组（人数不超过500人）
        String suitableGroupId = getSuitableGroupId();
        
        // 检查是否找到合适的群组
        if (suitableGroupId != null && !suitableGroupId.isEmpty()) {
            // 发送群聊邀请
            inviteChatroomMember(suitableGroupId, talker);
            
            // 发送确认消息给用户
            sendText(talker, confirmMessage);
            
            // 发送成功汇总日志
            sendInviteSummaryLog(talker, suitableGroupId, true, null);
            
        } else {
            // 发送错误提示给用户
            sendText(talker, fullGroupMessage);
            
            // 发送失败汇总日志
            sendInviteSummaryLog(talker, null, false, "所有群组已满员");
        }
    } catch (Exception e) {
        Map config = getPluginConfig();
        String errorMessage = (String) config.get("ERROR_MESSAGE");
        
        // 发送错误提示给用户
        sendText(talker, errorMessage);
        
        // 发送异常汇总日志
        sendInviteSummaryLog(talker, null, false, e.getMessage());
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
        return null;
    }
    
    // 收集所有可用的群组
    java.util.List availableGroups = new java.util.ArrayList();
    
    for (int i = 0; i < userGroups.length; i++) {
        String groupId = userGroups[i];
        
        try {
            // 获取群成员数量
            int memberCount = getGroupMemberCount(groupId);
            
            // 检查群人数是否未满
            if (memberCount < maxGroupMembers) {
                availableGroups.add(groupId);
            }
        } catch (Exception e) {
            // 获取失败的群组不加入候选列表
        }
    }
    
    // 从可用群组中随机选择一个
    if (availableGroups.size() == 0) {
        return null;
    }
    
    // 使用当前时间作为随机种子，生成随机索引
    java.util.Random random = new java.util.Random();
    int randomIndex = random.nextInt(availableGroups.size());
    String selectedGroupId = (String) availableGroups.get(randomIndex);
    
    return selectedGroupId;
}

/**
 * 发送群邀请处理汇总日志
 */
void sendInviteSummaryLog(String userWxid, String targetGroupId, boolean success, String errorMsg) {
    try {
        Map config = getPluginConfig();
        String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
        
        // 构建汇总日志
        String currentTime = getCurrentTime();
        StringBuilder logContent = new StringBuilder();
        
        if (success && targetGroupId != null) {
            // 获取群名称和成员数量
            String groupName = getGroupName(targetGroupId);
            int memberCount = 0;
            try {
                memberCount = getGroupMemberCount(targetGroupId);
            } catch (Exception e) {
                // 如果获取失败，设为0
            }
            
            logContent.append("#功能 ").append(currentTime)
                    .append(" 🎯 关键词监控-完成处理")
                    .append("\n✅ 用户: ").append(userWxid)
                    .append("\n✅ 关键词: ").append(triggerKeyword)
                    .append("\n✅ 目标群: ").append(groupName).append("(").append(targetGroupId).append(")")
                    .append("\n✅ 群人数: ").append(memberCount).append("人")
                    .append("\n✅ 已发送群聊邀请")
                    .append("\n✅ 已发送提醒消息");
        } else {
            logContent.append("#报错 ").append(currentTime)
                    .append(" ❌ 关键词监控-处理失败")
                    .append("\n🔍 用户: ").append(userWxid)
                    .append("\n🔍 关键词: ").append(triggerKeyword)
                    .append("\n❌ 错误: ").append(errorMsg != null ? errorMsg : "未知错误");
        }
        
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        sendText(logGroupId, logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("发送群邀请汇总日志失败: " + e.getMessage());
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
    
    if (!enableJoinTips) {
        return;
    }
    
    try {
        // 构建欢迎消息
        String welcomeMessage = buildJoinMessage(userWxid, userName);
        
        // 发送欢迎消息
        sendText(groupWxid, welcomeMessage);
        
        // 发送成功汇总日志
        sendJoinSummaryLog(groupWxid, userWxid, userName, true, null);
        
    } catch (Exception e) {
        // 发送失败汇总日志
        sendJoinSummaryLog(groupWxid, userWxid, userName, false, e.getMessage());
    }
}

/**
 * 处理成员离开
 */
void handleMemberLeft(String groupWxid, String userWxid, String userName) {
    Map config = getPluginConfig();
    boolean enableLeftTips = (Boolean) config.get("ENABLE_LEFT_TIPS");
    
    if (!enableLeftTips) {
        return;
    }
    
    try {
        // 构建离开消息
        String leftMessage = buildLeftMessage(userName);
        
        // 发送离开消息
        sendText(groupWxid, leftMessage);
        
        // 发送成功汇总日志
        sendLeftSummaryLog(groupWxid, userWxid, userName, true, null);
        
    } catch (Exception e) {
        // 发送失败汇总日志
        sendLeftSummaryLog(groupWxid, userWxid, userName, false, e.getMessage());
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
 * 发送新成员入群汇总日志
 */
void sendJoinSummaryLog(String groupWxid, String userWxid, String userName, boolean success, String errorMsg) {
    try {
        // 获取群名称
        String groupName = getGroupName(groupWxid);
        
        // 构建汇总日志
        String currentTime = getCurrentTime();
        StringBuilder logContent = new StringBuilder();
        
        if (success) {
            // 获取群成员数量
            int memberCount = 0;
            try {
                memberCount = getGroupMemberCount(groupWxid);
            } catch (Exception e) {
                // 如果获取失败，设为0
            }
            
            logContent.append("#功能 ").append(currentTime)
                    .append(" 🎉 新入群监控-完成处理")
                    .append("\n✅ 群组: ").append(groupName).append("(").append(groupWxid).append(")")
                    .append("\n✅ 新成员: ").append(userName).append("(").append(userWxid).append(")")
                    .append("\n✅ 群人数: ").append(memberCount).append("人")
                    .append("\n✅ 已发送欢迎消息");
        } else {
            logContent.append("#报错 ").append(currentTime)
                    .append(" ❌ 新入群监控-处理失败")
                    .append("\n🔍 群组: ").append(groupName).append("(").append(groupWxid).append(")")
                    .append("\n🔍 新成员: ").append(userName).append("(").append(userWxid).append(")")
                    .append("\n❌ 错误: ").append(errorMsg != null ? errorMsg : "未知错误");
        }
        
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        sendText(logGroupId, logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("发送入群汇总日志失败: " + e.getMessage());
    }
}

/**
 * 发送成员退群汇总日志
 */
void sendLeftSummaryLog(String groupWxid, String userWxid, String userName, boolean success, String errorMsg) {
    try {
        // 获取群名称
        String groupName = getGroupName(groupWxid);
        
        // 构建汇总日志
        String currentTime = getCurrentTime();
        StringBuilder logContent = new StringBuilder();
        
        if (success) {
            // 获取群成员数量
            int memberCount = 0;
            try {
                memberCount = getGroupMemberCount(groupWxid);
            } catch (Exception e) {
                // 如果获取失败，设为0
            }
            
            logContent.append("#功能 ").append(currentTime)
                    .append(" 👋 退群监控-完成处理")
                    .append("\n✅ 群组: ").append(groupName).append("(").append(groupWxid).append(")")
                    .append("\n✅ 退群成员: ").append(userName).append("(").append(userWxid).append(")")
                    .append("\n✅ 群人数: ").append(memberCount).append("人")
                    .append("\n✅ 已发送退群提示");
        } else {
            logContent.append("#报错 ").append(currentTime)
                    .append(" ❌ 退群监控-处理失败")
                    .append("\n🔍 群组: ").append(groupName).append("(").append(groupWxid).append(")")
                    .append("\n🔍 退群成员: ").append(userName).append("(").append(userWxid).append(")")
                    .append("\n❌ 错误: ").append(errorMsg != null ? errorMsg : "未知错误");
        }
        
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        sendText(logGroupId, logContent.toString());
        
    } catch (Exception e) {
        sendErrorLog("发送退群汇总日志失败: " + e.getMessage());
    }
}

// ==================== 插件初始化 ====================
// 用于跟踪是否已发送启动日志的标记
boolean hasLoggedStartup = false;
boolean hasLoggedBasicStartup = false;

/**
 * 生成完整的状态信息消息（公共方法）
 * @param title 消息标题
 * @param extraInfo 额外信息行（可选）
 * @param footerTip 底部提示信息
 * @return 完整的状态信息字符串
 */
String generateStatusMessage(String title, String extraInfo, String footerTip) {
    try {
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        String triggerKeyword = (String) config.get("TRIGGER_KEYWORD");
        
        // 添加类型安全检查
        Integer maxGroupMembersObj = (Integer) config.get("MAX_GROUP_MEMBERS");
        int maxGroupMembers = maxGroupMembersObj != null ? maxGroupMembersObj : 500;
        
        Boolean enableJoinTipsObj = (Boolean) config.get("ENABLE_JOIN_TIPS");
        boolean enableJoinTips = enableJoinTipsObj != null ? enableJoinTipsObj : true;
        
        Boolean enableLeftTipsObj = (Boolean) config.get("ENABLE_LEFT_TIPS");
        boolean enableLeftTips = enableLeftTipsObj != null ? enableLeftTipsObj : false;
        
        Boolean enableAtUserObj = (Boolean) config.get("ENABLE_AT_USER");
        boolean enableAtUser = enableAtUserObj != null ? enableAtUserObj : true;
        
        // 已改为汇总日志模式，不再需要详细日志配置
        boolean enableDetailedLog = false;
        
        Map groupNameMap = (Map) config.get("GROUP_NAME_MAP");
        String[] userGroups = (String[]) config.get("USER_GROUPS");
        
        // 空值保护
        if (groupNameMap == null) groupNameMap = new HashMap();
        if (userGroups == null) userGroups = new String[0];
        if (triggerKeyword == null) triggerKeyword = "进群";
        if (logGroupId == null) logGroupId = "未配置";
        
        // 获取当前时间
        String currentTime = getCurrentTime();
        
        // 构建完整的状态信息
        StringBuilder message = new StringBuilder();
        // 根据标题判断使用的标签类型
        String logTag = title.contains("当前状态信息") ? "#信息" : "#功能";
        message.append(logTag).append(" ").append(currentTime).append(" 🚀 ").append(title).append("\n\n");
        message.append("📋 插件版本: v1.0.2\n");
        message.append("⏰ 查询时间: ").append(currentTime).append("\n");
        if (extraInfo != null && !extraInfo.isEmpty()) {
            message.append(extraInfo).append("\n");
        }
        message.append("\n");
        
        message.append("=== 🔧 功能配置状态 ===\n");
        message.append("好友自动通过: ✅ 启用\n");
        message.append("群邀请功能: ✅ 启用 (关键词: ").append(triggerKeyword).append(")\n");
        message.append("入群提示: ").append(enableJoinTips ? "✅ 启用" : "❌ 禁用").append("\n");
        message.append("退群提示: ").append(enableLeftTips ? "✅ 启用" : "❌ 禁用").append("\n");
        message.append("@用户功能: ").append(enableAtUser ? "✅ 启用" : "❌ 禁用").append("\n");
        message.append("日志模式: ✅ 汇总模式 (每种操作仅输出一条汇总日志)\n\n");
        
        message.append("=== 📊 群组配置信息 ===\n");
        String logGroupName = (String) groupNameMap.get(logGroupId);
        if (logGroupName == null) logGroupName = "未知群组";
        message.append("日志群组: ").append(logGroupName).append("(").append(logGroupId).append(")\n");
        message.append("群人数上限: ").append(maxGroupMembers).append(" 人\n");
        message.append("用户群组数量: ").append(userGroups.length).append(" 个\n\n");
        
        // 添加用户群组列表和人员数量统计
        message.append("=== 🏘️ 用户群组列表及人员统计 ===\n");
        int totalMembers = 0;
        int fullGroups = 0;
        int availableGroups = 0;
        int failedGroups = 0;
        
        for (int i = 0; i < userGroups.length; i++) {
            String groupId = userGroups[i];
            String groupName = (String) groupNameMap.get(groupId);
            if (groupName == null) groupName = "未知群组";
            
            try {
                // 获取群成员数量
                int memberCount = getGroupMemberCount(groupId);
                totalMembers += memberCount;
                
                // 判断群状态
                String status;
                if (memberCount >= maxGroupMembers) {
                    status = "🔴 已满";
                    fullGroups++;
                } else {
                    status = "🟢 可用";
                    availableGroups++;
                }
                
                // 计算使用率
                int usagePercent = (int)((double)memberCount / maxGroupMembers * 100);
                
                message.append((i + 1)).append(". ").append(groupName)
                    .append(" - 人数: ").append(memberCount).append("/").append(maxGroupMembers)
                    .append(" (").append(usagePercent).append("%) ").append(status).append("\n");
                    
            } catch (Exception e) {
                // 如果获取群成员数量失败，显示错误信息
                message.append((i + 1)).append(". ").append(groupName).append(" - 人数: ❌ 获取失败\n");
                failedGroups++;
            }
        }
        
        // 添加统计汇总信息
        message.append("\n=== 📊 群组统计汇总 ===\n");
        message.append("总群数: ").append(userGroups.length).append(" 个\n");
        message.append("可用群: ").append(availableGroups).append(" 个 🟢\n");
        message.append("已满群: ").append(fullGroups).append(" 个 🔴\n");
        if (failedGroups > 0) {
            message.append("异常群: ").append(failedGroups).append(" 个 ❌\n");
        }
        message.append("监控总人数: ").append(totalMembers).append(" 人\n");
        if (userGroups.length > 0) {
            message.append("平均群人数: ").append(totalMembers / userGroups.length).append(" 人\n");
        }
        
        message.append("\n=============================\n");
        if (footerTip != null && !footerTip.isEmpty()) {
            message.append(footerTip);
        } else {
            message.append("✅ 插件状态信息生成完成！");
        }
        
        return message.toString();
        
    } catch (Exception e) {
        return "#报错 " + getCurrentTime() + " 生成状态信息失败: " + e.getMessage();
    }
}

/**
 * 立即发送完整的启动日志（插件加载时调用）
 */
void sendBasicStartupLog() {
    if (hasLoggedBasicStartup) {
        return;
    }
    hasLoggedBasicStartup = true;
    
    // 异步发送完整的启动日志
    new Thread(new Runnable() {
        public void run() {
            try {
                // 等待WAuxiliary环境完全初始化
                Thread.sleep(3000);
                
                // 调用共用的状态信息生成方法
                String startupMessage = generateStatusMessage("微信自动管理插件已启动", "⏰ 初始化时间: " + getCurrentTime(), "✅ 插件初始化完成，所有功能已就绪！");
                
                Map config = getPluginConfig();
                String logGroupId = (String) config.get("LOG_GROUP_ID");
                
                // 发送完整的初始化消息
                sendText(logGroupId, startupMessage);
                
            } catch (Exception e) {
                // 如果启动日志发送失败，静默处理
            }
        }
    }).start();
}

// 立即调用基础启动日志
{
    sendBasicStartupLog();
}

/**
 * 发送功能首次触发确认日志
 */
void sendStartupLogIfNeeded() {
    if (hasLoggedStartup) {
        return;
    }
    hasLoggedStartup = true;
    
    // 发送首次功能触发确认
    try {
        Map config = getPluginConfig();
        String logGroupId = (String) config.get("LOG_GROUP_ID");
        String currentTime = getCurrentTime();
        
        String confirmMessage = "#信息 " + currentTime + " ✅ 插件功能已激活 - 开始正常运行";
        sendText(logGroupId, confirmMessage);
        
    } catch (Exception e) {
        // 如果确认日志发送失败，静默处理
    }
} 
