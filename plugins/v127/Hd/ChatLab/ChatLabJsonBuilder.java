
/**
 * https://chatlab.fun/cn/standard/chatlab-format.html
 */

class ChatLabJsonBuilder {
    JSONObject root
    
    ChatLabJsonBuilder() {
        root = new JSONObject()
    }
    
    ChatLabJsonBuilder setChatlab(
        String version, // 格式版本号，当前为 "0.0.2"
        long exportedAt // 导出时间（秒级 Unix 时间戳）
    ) {
        var chatlab = new JSONObject()
        chatlab.put("version", version)
        chatlab.put("exportedAt", exportedAt)
        root.put("chatlab", chatlab)
        return this
    }
    
    ChatLabJsonBuilder setMeta(
        String name, // 群名或对话名
        String platform, // 平台标识，如 qq / wechat / discord / whatsapp 等
        String type // 聊天类型：group（群聊）/ private（私聊）
    ) {
        var meta = new JSONObject()
        meta.put("name", name)
        meta.put("platform", platform)
        meta.put("type", type)
        root.put("meta", meta)
        return this
    }
    
    ChatLabJsonBuilder addMember(
        String platformId, // 用户唯一标识
        String accountName // 账号名称
    ) {
        var members = root.optJSONArray("members")
        if (members == null) {
            members = new JSONArray()
            root.put("members", members)
        }
        var member = new JSONObject()
        member.put("platformId", platformId)
        member.put("accountName", accountName)
        members.put(member)
        return this
    }
    
    ChatLabJsonBuilder addMessage(
        String sender, // 发送者的 platformId
        String accountName, // 发送时的账号名称
        long timestamp, // 秒级 Unix 时间戳
        int type, // 消息类型（见下方对照表）
        String content // 消息内容（非文本消息可为 null）
    ) {
        var messages = root.optJSONArray("messages");
        if (messages == null) {
            messages = new JSONArray()
            root.put("messages", messages)
        }
        var message = new JSONObject()
        message.put("sender", sender)
        message.put("accountName", accountName)
        message.put("timestamp", timestamp)
        message.put("type", type)
        message.put("content", content)
        messages.put(message)
        return this
    }
    
    String build() {
        return root.toString()
    }
    
    String buildPretty() {
        return root.toString(2)
    }
}
