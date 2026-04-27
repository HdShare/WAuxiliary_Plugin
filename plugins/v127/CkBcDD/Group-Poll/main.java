import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ******** User-configurable constants ********

// MAX_POLL_OPTIONS : 最大允许的投票选项数量
final int MAX_POLL_OPTIONS = 64;

// SHOW_FULL_POLL_RESULTS_DETAILS : 投票结果是否显示完整选项文本 (false: 仅显示序号+票数, true: 显示完整选项文本+票数)
final boolean SHOW_FULL_POLL_RESULTS_DETAILS = false;
// ***** End of user-configurable constants *****

// Global variables for managing poll state
// 是否有投票正在进行
boolean isActivePoll = false;
// 投票发起者的微信ID
String pollInitiatorWxid = null;
// 投票所在的群聊ID
String pollChatId = null;
// 投票的标题
String pollTitle = null;
// 投票所有选项的文本内容 (使用原始类型以兼容环境)
List pollOptions = new ArrayList();
// 投票记录 <String (wxid), Integer (1-based option index)> (使用原始类型以兼容环境)
Map votes = new HashMap();


/**
 * isPureNumber() - 检查字符串是否完全由数字构成。
 * @str: 要检查的字符串。
 *
 * 如果字符串为 null、为空，或者包含任何非数字字符，则返回 false。
 * 否则返回 true。
 *
 * Return: 如果字符串是纯数字则返回 true，否则返回 false。
 */
boolean isPureNumber(String str) {
    if (str == null || str.isEmpty()) {
        return false;
    }
    for (char c : str.toCharArray()) {
        if (!Character.isDigit(c)) {
            return false;
        }
    }
    return true;
}

/**
 * onHandleMsg() - 监听并处理收到的消息。
 * @msgInfoBean: 包含消息详细信息的对象。
 *
 * 此方法是插件处理所有接收消息的核心。它会根据消息内容和状态，
 * 分发处理逻辑到发起投票、结束投票或参与投票等不同模块。
 */
void onHandleMsg(Object msgInfoBean) {
    /* 确保是群聊消息并且是文本消息 */
    // 注意: msgInfoBean 的具体方法调用方式取决于实际 API，这里用点表示方法调用
    if (!msgInfoBean.isGroupChat() || !msgInfoBean.isText()) {
        return;
    }

    String currentChatId = msgInfoBean.getTalker();
    String senderWxid = msgInfoBean.getSendTalker();
    String content = msgInfoBean.getContent().trim();
    String loginWxid = getLoginWxid(); /* 获取当前登录的微信ID */

    /* A. 处理发起/覆盖投票命令 */
    String pollCommandPrefix = "/投票 ";
    /* 发起投票的命令必须是自己发送的 */
    if (msgInfoBean.isSend() && content.startsWith(pollCommandPrefix) && senderWxid.equals(loginWxid)) {
        String commandBody = content.substring(pollCommandPrefix.length()).trim();
        if (commandBody.isEmpty()) {
            toast("发起投票失败：命令内容不能为空！");
            log("GroupPoll: Invalid /投票 command format - empty body.");
            return;
        }

        String tempPollTitle;
        List tempPollOptions = new ArrayList(); // 使用原始类型

        int firstSpaceIndex = commandBody.indexOf(' ');
        if (firstSpaceIndex == -1) { // 只有标题，没有选项部分
            tempPollTitle = commandBody;
            // tempPollOptions 保持为空
        } else {
            tempPollTitle = commandBody.substring(0, firstSpaceIndex).trim();
            String optionsStr = commandBody.substring(firstSpaceIndex + 1).trim();
            if (!optionsStr.isEmpty()) {
                // 使用正则表达式 \\s+ 来按一个或多个空白字符分割
                tempPollOptions.addAll(Arrays.asList(optionsStr.split("\\s+")));
            }
        }
        
        if (tempPollTitle.isEmpty()) {
             toast("发起投票失败：标题不能为空！");
             log("GroupPoll: Invalid /投票 command format - empty title.");
             return;
        }

        /* 验证提供的选项数量 */
        if (tempPollOptions.isEmpty()) {
            toast("发起投票失败：至少需要提供一个选项！");
            log("GroupPoll: Invalid /投票 command format - no options provided. Input: " + content);
            return;
        }

        if (tempPollOptions.size() > MAX_POLL_OPTIONS) {
            toast("发起投票失败：选项数量过多（最多 " + MAX_POLL_OPTIONS + " 个）。此限制可在插件代码中调整，但修改可能导致未知问题，请自行承担风险。");
            log("GroupPoll: Invalid /投票 command - too many options (" + tempPollOptions.size() + "). Input: " + content);
            return;
        }

        /* 更新投票状态 */
        isActivePoll = true;
        pollInitiatorWxid = loginWxid;
        pollChatId = currentChatId; // 投票发起时的群聊
        this.pollTitle = tempPollTitle;
        this.pollOptions.clear();
        this.pollOptions.addAll(tempPollOptions);
        votes.clear();

        StringBuilder pollMessageBuilder = new StringBuilder();
        pollMessageBuilder.append("【投票】").append(this.pollTitle).append("\n");
        boolean hasNumericOptionText = false;
        for (int i = 0; i < this.pollOptions.size(); i++) {
            String optionText = (String) this.pollOptions.get(i); /* 强制类型转换 */
            pollMessageBuilder.append(i + 1).append(". ").append(optionText).append("\n");
            if (isPureNumber(optionText)) {
                hasNumericOptionText = true;
            }
        }
        pollMessageBuilder.append("请直接回复选项前的数字 (例如 1, 2, ...) 或完整的选项文本参与投票。\n");
        pollMessageBuilder.append("发起人可发送 \"/结束投票\" 来结束本次投票。");
        if (hasNumericOptionText) {
            pollMessageBuilder.append("\n**注意：当回复内容为纯数字时，将优先匹配选项序号。**");
        }

        sendText(pollChatId, pollMessageBuilder.toString());
        log("GroupPoll: Poll started. Title: " + this.pollTitle + ", Options: " + this.pollOptions.size() + ", ChatID: " + pollChatId);
        return; /* 发起命令处理完毕 */
    }

    /* B. 处理结束投票命令 (也必须是自己发送的) */
    if (msgInfoBean.isSend() && isActivePoll && content.equals("/结束投票") &&
        senderWxid.equals(pollInitiatorWxid) && /* 确保是发起者 */
        currentChatId.equals(pollChatId)) {    /* 确保在发起投票的群聊 */
        log("GroupPoll: Detected /结束投票 command.");

        /* 统计票数 */
        int[] optionVoteCounts = new int[this.pollOptions.size()]; // 0-indexed counts
        for (Object voteValueObj : votes.values()) {
            if (voteValueObj instanceof Integer) {
                Integer voteIndex = (Integer) voteValueObj; // 1-based index
                if (voteIndex >= 1 && voteIndex <= this.pollOptions.size()) {
                    optionVoteCounts[voteIndex - 1]++;
                }
            }
        }

        StringBuilder resultsMessageBuilder = new StringBuilder();
        resultsMessageBuilder.append("【投票结果】").append(this.pollTitle).append("\n");
        for (int i = 0; i < this.pollOptions.size(); i++) {
            if (SHOW_FULL_POLL_RESULTS_DETAILS) {
                resultsMessageBuilder.append("- ").append((String) this.pollOptions.get(i)).append(": "); /* 强制类型转换 */
            } else {
                resultsMessageBuilder.append(i + 1).append(": ");
            }
            resultsMessageBuilder.append(optionVoteCounts[i]).append("票\n");
        }
        resultsMessageBuilder.append("投票已结束。");
        if (!SHOW_FULL_POLL_RESULTS_DETAILS && !this.pollOptions.isEmpty()) {
            resultsMessageBuilder.append("\n(各序号对应的选项请参考原始投票信息)");
        }

        sendText(pollChatId, resultsMessageBuilder.toString());
        log("GroupPoll: Poll ended. Results sent.");

        /* 重置投票状态 */
        isActivePoll = false;
        pollInitiatorWxid = null;
        pollChatId = null;
        this.pollTitle = null;
        this.pollOptions.clear();
        votes.clear();
        return; /* 结束命令处理完毕 */
    }

    /* C. 处理参与者投票 (非自己发送的消息) */
    if (!msgInfoBean.isSend() && isActivePoll && currentChatId.equals(pollChatId)) {
        // log("GroupPoll: Checking for vote. Sender: " + senderWxid + ", Content: " + content);
        boolean voted = false;
        /* 1. 优先匹配序号 */
        try {
            int numericVote = Integer.parseInt(content);
            if (numericVote >= 1 && numericVote <= this.pollOptions.size()) {
                votes.put(senderWxid, Integer.valueOf(numericVote)); /* Store 1-based index */
                voted = true;
                // log("GroupPoll: Vote registered by number for option " + numericVote + " by " + senderWxid);
            }
        } catch (NumberFormatException e) {
            /* 不是数字，继续尝试文本匹配 */
        }

        /* 2. 其次匹配文本 (如果没有通过序号投票成功) */
        if (!voted) {
            for (int i = 0; i < this.pollOptions.size(); i++) {
                if (content.equalsIgnoreCase((String) this.pollOptions.get(i))) { /* 强制类型转换 */
                    votes.put(senderWxid, Integer.valueOf(i + 1)); /* Store 1-based index */
                    voted = true;
                    // log("GroupPoll: Vote registered by text for option " + (i + 1) + " (" + (String) this.pollOptions.get(i) + ") by " + senderWxid);
                    break; 
                }
            }
        }
        /* if (voted) { // 可选：发送投票确认，目前禁用以避免刷屏 */
        /* log("GroupPoll: Vote processed for " + senderWxid); */
        /* } */
    }
}
