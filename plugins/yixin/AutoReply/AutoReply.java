import org.json.JSONArray;
import org.json.JSONObject;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

/// 自动回复插件：
/// 在任意聊天输入框输入「自动回复 XXXX」后，长按发送按钮即可开启，注意自动回复后面有空格
/// 输入其他任意内容后长按发送按钮即可关闭

String autoReplyContent = "";

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isSend()) return;
    if (msgInfoBean.getTalker().contains("@chatroom")) return;
    if (autoReplyContent.isEmpty()) return;
    sendText(msgInfoBean.getTalker(), autoReplyContent);
}

boolean onLongClickSendBtn(String text) {
    if (text.startsWith("自动回复 ")) {
        autoReplyContent = text.replaceFirst("自动回复 ", "");
        toast("已开启自动回复");
    } else {
        autoReplyContent = "";
        toast("已关闭自动回复");
    }
}