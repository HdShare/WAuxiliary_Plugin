import java.io.File;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

void sendKitchen(String emoji1, String emoji2, String talker) {
    String url = "https://emojik.vercel.app/s/" + emoji1 + "_" + emoji2 + "?size=256";
    final String finalTalker = talker;

    download(url, pluginDir + "/emoji.png", null, new PluginCallBack.DownloadCallback() {
        public void onSuccess(File file) {
            sendEmoji(finalTalker, file.getAbsolutePath());
        }

        public void onError(Exception e) {
            sendText(finalTalker, "[Emoji Kitchen] 下载失败: " + e.getMessage());
        }
    });
}

void onHandleMsg(Object msgInfoBean) {
    if (msgInfoBean.isText()) {
        String content = msgInfoBean.getContent();
        String talker = msgInfoBean.getTalker();

        if (content.contains("+")) {
            String[] emojis = content.split("\\+");
            if (emojis.length == 2) {
                String emoji1 = emojis[0].trim();
                String emoji2 = emojis[1].trim();

                // 保持调用不变，内部逻辑已增强为精确判断
                if (isSimpleEmoji(emoji1) && isSimpleEmoji(emoji2)) {
                    sendKitchen(emoji1, emoji2, talker);
                }
            }
        }
    }
}

// ✅ 保持方法名为 isSimpleEmoji，但内部为精确 emoji 判断逻辑
boolean isSimpleEmoji(String s) {
    if (s == null || s.isEmpty()) return false;

    int[] codePoints = s.codePoints().toArray();

    if (codePoints.length > 8) return false;

    boolean hasEmojiCore = false;
    for (int cp : codePoints) {
        if (isEmojiCore(cp)) {
            hasEmojiCore = true;
        } else if (!isEmojiModifier(cp) && !isZWJ(cp) && !isVariationSelector(cp) && !isRegionalIndicator(cp)) {
            return false;
        }
    }

    return hasEmojiCore;
}

boolean isEmojiCore(int cp) {
    return
        // 表情与手势
        (cp >= 0x1F600 && cp <= 0x1F64F) ||  // Emoticons
        (cp >= 0x1F44A && cp <= 0x1F64F) ||  // Hands
        (cp >= 0x1F90C && cp <= 0x1F9FF) ||  // Supplemental Symbols

        // 人物、身体、衣物
        (cp >= 0x1F466 && cp <= 0x1F487) ||  // People
        (cp >= 0x1F9B0 && cp <= 0x1F9B9) ||  // Hair, skin tone

        // 动物与自然
        (cp >= 0x1F400 && cp <= 0x1F4D3) ||  // Animals
        (cp >= 0x1F300 && cp <= 0x1F320) ||  // Nature/weather

        // 食物和饮料
        (cp >= 0x1F34E && cp <= 0x1F37F) ||  // Food & Drink

        // 活动与运动
        (cp >= 0x1F3C0 && cp <= 0x1F3FA) ||
        cp == 0x26F9 ||

        // 音乐、工具、科技
        (cp >= 0x1F3B5 && cp <= 0x1F3FF) ||
        (cp >= 0x1F4F0 && cp <= 0x1F579) ||

        // 交通工具与地标
        (cp >= 0x1F680 && cp <= 0x1F6FF) ||

        // 杂项符号
        (cp >= 0x2600 && cp <= 0x26FF) ||
        (cp >= 0x2700 && cp <= 0x27BF);
}

boolean isEmojiModifier(int cp) {
    return cp >= 0x1F3FB && cp <= 0x1F3FF;
}

boolean isZWJ(int cp) {
    return cp == 0x200D;
}

boolean isVariationSelector(int cp) {
    return cp == 0xFE0F;
}

boolean isRegionalIndicator(int cp) {
    return cp >= 0x1F1E6 && cp <= 0x1F1FF;
}
