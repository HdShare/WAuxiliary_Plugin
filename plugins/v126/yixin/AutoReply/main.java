
import me.hd.wauxv.plugin.api.callback.PluginCallBack;

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