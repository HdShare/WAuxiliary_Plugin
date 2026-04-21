import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// 你原有的 GroupInfo 类
import me.hd.wauxv.data.bean.info.GroupInfo;

// === 顶部独立声明的类与接口 ===

// 定义一个内部类来封装卡片消息
class MediaMessage {
    private String title;
    private String description;
    private String thumbUrl;
    private String contentUrl;

    public void setTitle(String title) { this.title = title; }
    public String getTitle() { return title; }
    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }
    public String getThumbUrl() { return thumbUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public String getContentUrl() { return contentUrl; }
}

// 帮助程序类，用于对发送操作进行排序
class SendTask {
    private final Runnable action;
    private final long delayMs;

    SendTask(Runnable action, long delayMs) {
        this.action = action;
        this.delayMs = delayMs;
    }

    Runnable getAction() {
        return action;
    }

    long getDelay() {
        return delayMs;
    }
}

// 文件选择回调接口
interface MediaSelectionCallback {
    void onSelected(ArrayList<String> selectedFiles);
}


// === 存储 Key 定义 ===
private final String LISTEN_GROUPS_KEY = "listen_groups";
private final String DELAY_KEY = "send_delay";
private final int DEFAULT_DELAY = 10;
private final String JOIN_TOGGLE_KEY = "join_toggle";
private final String LEFT_TOGGLE_KEY = "left_toggle";
private final String PROMPT_TYPE_KEY = "prompt_type";

// 全局提示语Key
private final String JOIN_TEXT_PROMPT_KEY = "join_text_prompt";
private final String LEFT_TEXT_PROMPT_KEY = "left_text_prompt";
private final String JOIN_CARD_TITLE_KEY = "join_card_title";
private final String LEFT_CARD_TITLE_KEY = "left_card_title";
private final String JOIN_CARD_DESC_KEY = "join_card_desc";
private final String LEFT_CARD_DESC_KEY = "left_card_desc";

// 媒体发送设置
private final String JOIN_IMAGE_PATHS_KEY = "join_image_paths";
private final String LEFT_IMAGE_PATHS_KEY = "left_image_paths";
private final String JOIN_EMOJI_PATHS_KEY = "join_emoji_paths";
private final String LEFT_EMOJI_PATHS_KEY = "left_emoji_paths";
private final String JOIN_VOICE_PATHS_KEY = "join_voice_paths";
private final String LEFT_VOICE_PATHS_KEY = "left_voice_paths";
private final String JOIN_VIDEO_PATHS_KEY = "join_video_paths";
private final String LEFT_VIDEO_PATHS_KEY = "left_video_paths";
private final String JOIN_FILE_PATHS_KEY = "join_file_paths";
private final String LEFT_FILE_PATHS_KEY = "left_file_paths";
private final String SEND_MEDIA_ORDER_KEY = "send_media_order";
private final String SEND_MEDIA_SEQUENCE_KEY = "send_media_sequence";

// 精细化延迟设置 (单位: 毫秒)
private final String PROMPT_DELAY_KEY = "prompt_delay_ms";
private final String IMAGE_DELAY_KEY = "image_delay_ms";
private final String VOICE_DELAY_KEY = "voice_delay_ms";
private final String EMOJI_DELAY_KEY = "emoji_delay_ms";
private final String VIDEO_DELAY_KEY = "video_delay_ms";
private final String FILE_DELAY_KEY = "file_delay_ms";

// 文本+卡片模式的发送顺序
private final String PROMPT_BOTH_ORDER_KEY = "prompt_both_order";

// 记录当前获得焦点的输入框，用于快捷插入变量
private EditText currentFocusedEdit = null;

// 辅助方法：获取当前焦点的EditText（通过遍历视图层级查找）
private EditText getCurrentFocusedEditText() {
    Activity topActivity = getTopActivity();
    if (topActivity == null) return currentFocusedEdit;
    
    // 直接从Activity获取当前焦点
    View focusedView = topActivity.getCurrentFocus();
    if (focusedView instanceof EditText) {
        return (EditText) focusedView;
    }
    
    // 如果当前焦点不是EditText，尝试在视图树中查找获得焦点的EditText
    View rootView = topActivity.getWindow().getDecorView();
    EditText foundEdit = findFocusedEditText(rootView);
    if (foundEdit != null) {
        return foundEdit;
    }
    
    // 最后回退到记录的currentFocusedEdit
    return currentFocusedEdit;
}

// 递归查找获得焦点的EditText
private EditText findFocusedEditText(View view) {
    if (view == null) return null;
    
    if (view instanceof EditText) {
        if (view.hasFocus()) {
            return (EditText) view;
        }
    }
    
    if (view instanceof ViewGroup) {
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            EditText result = findFocusedEditText(child);
            if (result != null) {
                return result;
            }
        }
    }
    
    return null;
}

// 丰富的随机提示语库（每次填充时随机选一条）
private final String[] RANDOM_JOIN_TEXTS_ARRAY = new String[] {
    "[AtWx=%userWxid%] 欢迎 %userName% 加入 %groupName%～ 🎉",
    "热烈欢迎新朋友 %userName% (群名片: %groupNickname%)，大家请多关照！",
    "又来了一位大佬，欢迎 %userName%！记得看群公告哦~",
    "捕捉到一只小萌新 %userName%，来打个招呼吧～",
    "欢迎 %userName%，愿在 %groupName% 玩得开心～"
};
private final String[] RANDOM_LEFT_TEXTS_ARRAY = new String[] {
    "有缘再会，祝 %userName% 前程似锦。",
    "悄悄地他走了，正如他悄悄地来。再见，%userName% (群名片: %groupNickname%)。",
    "%userName% 已离开群聊，愿一切安好。",
    "青山不改，绿水长流，后会有期。",
    "我们会想念你的，%userName%。"
};
private final String[] RANDOM_JOIN_CARD_TITLES_ARRAY = new String[] {
    "🎊 欢迎：%userName%",
    "群聊因你而精彩",
    "新成员到来：%userName%"
};
private final String[] RANDOM_JOIN_CARD_DESCS_ARRAY = new String[] {
    "常来聊天哦~",
    "群名称：%groupName% \n名片：%groupNickname%\n进群时间：%time%",
    "快来和大家一起玩耍吧！\nID: %userWxid%"
};
private final String[] RANDOM_LEFT_CARD_TITLES_ARRAY = new String[] {
    "成员离群通知",
    "%userName% 已离开",
    "祝你前程似锦"
};
private final String[] RANDOM_LEFT_CARD_DESCS_ARRAY = new String[] {
    "我们有缘再见",
    "群名称：%groupName% \n名片：%groupNickname%\n离群时间：%time%",
    "相逢是缘，祝君安好。"
};

// === 按钮点击事件处理 ===
public boolean onClickSendBtn(String text) {
    if ("进退群设置".equals(text)) {
        currentFocusedEdit = null; // 打开新界面时重置焦点状态
        showUnifiedSettingsDialog();
        return true;
    }
    return false;
}

// === 核心功能：成员变动处理 ===
public void onMemberChange(final String type, final String groupWxid, final String userWxid, final String userName) {
    Set<String> listenGroups = getStringSet(LISTEN_GROUPS_KEY, new HashSet<String>());
    if (!listenGroups.contains(groupWxid)) {
        return;
    }

    Set<String> disabledJoinToggles = getStringSet(JOIN_TOGGLE_KEY, new HashSet<String>());
    Set<String> disabledLeftToggles = getStringSet(LEFT_TOGGLE_KEY, new HashSet<String>());

    final boolean shouldSendJoin = "join".equals(type) && !disabledJoinToggles.contains(groupWxid);
    final boolean shouldSendLeft = "left".equals(type) && !disabledLeftToggles.contains(groupWxid);

    if (!shouldSendJoin && !shouldSendLeft) {
        return;
    }

    final int delaySeconds = getInt(DELAY_KEY, DEFAULT_DELAY);

    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            // 1. 准备所有需要的数据和配置
            final String groupName = getGroupNameById(groupWxid);
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            final String currentTime = sdf.format(new Date());
            
            // 智能判断当前群的提示语类型（文本/卡片），如果有专属设置优先使用专属
            String customPromptType = getString(PROMPT_TYPE_KEY + "_" + groupWxid, "global");
            final String finalPromptType = "global".equals(customPromptType) ? getString(PROMPT_TYPE_KEY, "text") : customPromptType;
            
            // 智能判断当前群的媒体发送模式 (global/custom/none)
            final String mediaMode = getString("media_mode_" + groupWxid, "global");
            final String mediaSuffix = "custom".equals(mediaMode) ? "_" + groupWxid : "";

            final String mediaOrder;
            final String mediaSequence;
            if ("none".equals(mediaMode)) {
                mediaOrder = "none";
                mediaSequence = "";
            } else {
                mediaOrder = getString(SEND_MEDIA_ORDER_KEY + mediaSuffix, getString(SEND_MEDIA_ORDER_KEY, "none"));
                mediaSequence = getString(SEND_MEDIA_SEQUENCE_KEY + mediaSuffix, getString(SEND_MEDIA_SEQUENCE_KEY, "image,voice,emoji,video,file"));
            }

            // 2. 智能判断当前群的延迟模式 (global/custom)
            final String delayMode = getString("delay_mode_" + groupWxid, "global");
            final String delaySuffix = "custom".equals(delayMode) ? "_" + groupWxid : "";

            // 获取精细化的延迟设置 (如果专属没有配置，就回退到读取全局配置的值)
            final long promptDelay = getInt(PROMPT_DELAY_KEY + delaySuffix, getInt(PROMPT_DELAY_KEY, 0));
            final long imageDelay  = getInt(IMAGE_DELAY_KEY + delaySuffix, getInt(IMAGE_DELAY_KEY, 100));
            final long voiceDelay  = getInt(VOICE_DELAY_KEY + delaySuffix, getInt(VOICE_DELAY_KEY, 100));
            final long emojiDelay  = getInt(EMOJI_DELAY_KEY + delaySuffix, getInt(EMOJI_DELAY_KEY, 100));
            final long videoDelay  = getInt(VIDEO_DELAY_KEY + delaySuffix, getInt(VIDEO_DELAY_KEY, 100));
            final long fileDelay   = getInt(FILE_DELAY_KEY + delaySuffix, getInt(FILE_DELAY_KEY, 100));

            // 3. 创建所有可能的发送任务
            SendTask promptTask;
            if ("both".equals(finalPromptType)) {
                // 文本+卡片模式，根据顺序设置决定发送顺序
                // 优先读取专属设置，如果没有则使用全局设置
                final String customBothOrder = getString(PROMPT_BOTH_ORDER_KEY + "_" + groupWxid, "");
                final String bothOrder = TextUtils.isEmpty(customBothOrder) ? getString(PROMPT_BOTH_ORDER_KEY, "text_first") : customBothOrder;
                final String finalType = type;
                final String finalGroupWxid = groupWxid;
                final String finalUserWxid = userWxid;
                final String finalUserName = userName;
                final String finalGroupName = groupName;
                final String finalCurrentTime = currentTime;
                
                promptTask = new SendTask(new Runnable() {
                    public void run() {
                        if ("card_first".equals(bothOrder)) {
                            // 先卡片后文本
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    handleCardSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                                }
                            });
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                public void run() {
                                    handleTextSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                                }
                            }, 100);
                        } else {
                            // 先文本后卡片
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    handleTextSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                                }
                            });
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                public void run() {
                                    handleCardSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                                }
                            }, 100);
                        }
                    }
                }, promptDelay);
            } else if ("card".equals(finalPromptType)) {
                final String finalType = type;
                final String finalGroupWxid = groupWxid;
                final String finalUserWxid = userWxid;
                final String finalUserName = userName;
                final String finalGroupName = groupName;
                final String finalCurrentTime = currentTime;
                promptTask = new SendTask(new Runnable() {
                    public void run() {
                        handleCardSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                    }
                }, promptDelay);
            } else {
                final String finalType = type;
                final String finalGroupWxid = groupWxid;
                final String finalUserWxid = userWxid;
                final String finalUserName = userName;
                final String finalGroupName = groupName;
                final String finalCurrentTime = currentTime;
                promptTask = new SendTask(new Runnable() {
                    public void run() {
                        handleTextSending(finalType, finalGroupWxid, finalUserWxid, finalUserName, finalGroupName, finalCurrentTime);
                    }
                }, promptDelay);
            }

            // 媒体任务
            Map<String, SendTask> mediaTasks = new HashMap<>();

            // 只有当 mediaMode 不是 none 时，才读取并调度媒体任务
            if (!"none".equals(mediaMode)) {
                final String imagePaths = getString(("join".equals(type) ? JOIN_IMAGE_PATHS_KEY : LEFT_IMAGE_PATHS_KEY) + mediaSuffix, "");
                if (!TextUtils.isEmpty(imagePaths)) {
                    Runnable imageAction = new Runnable() {
                        public void run() {
                            for (String p : imagePaths.split(",")) {
                                if (!TextUtils.isEmpty(p.trim())) {
                                    try { sendImage(groupWxid, p.trim()); } catch (Exception e) {}
                                }
                            }
                        }
                    };
                    mediaTasks.put("image", new SendTask(imageAction, imageDelay));
                }

                final String voicePaths = getString(("join".equals(type) ? JOIN_VOICE_PATHS_KEY : LEFT_VOICE_PATHS_KEY) + mediaSuffix, "");
                if (!TextUtils.isEmpty(voicePaths)) {
                    Runnable voiceAction = new Runnable() {
                        public void run() {
                            for (String p : voicePaths.split(",")) {
                                if (!TextUtils.isEmpty(p.trim())) {
                                    try { sendVoice(groupWxid, p.trim()); } catch (Exception e) {}
                                }
                            }
                        }
                    };
                    mediaTasks.put("voice", new SendTask(voiceAction, voiceDelay));
                }

                final String emojiPaths = getString(("join".equals(type) ? JOIN_EMOJI_PATHS_KEY : LEFT_EMOJI_PATHS_KEY) + mediaSuffix, "");
                if (!TextUtils.isEmpty(emojiPaths)) {
                    Runnable emojiAction = new Runnable() {
                        public void run() {
                            for (String p : emojiPaths.split(",")) {
                                if (!TextUtils.isEmpty(p.trim())) {
                                    try { sendEmoji(groupWxid, p.trim()); } catch (Exception e) {}
                                }
                            }
                        }
                    };
                    mediaTasks.put("emoji", new SendTask(emojiAction, emojiDelay));
                }

                final String videoPaths = getString(("join".equals(type) ? JOIN_VIDEO_PATHS_KEY : LEFT_VIDEO_PATHS_KEY) + mediaSuffix, "");
                if (!TextUtils.isEmpty(videoPaths)) {
                    Runnable videoAction = new Runnable() {
                        public void run() {
                            for (String p : videoPaths.split(",")) {
                                if (!TextUtils.isEmpty(p.trim())) {
                                    try { sendVideo(groupWxid, p.trim()); } catch (Exception e) {}
                                }
                            }
                        }
                    };
                    mediaTasks.put("video", new SendTask(videoAction, videoDelay));
                }

                final String filePaths = getString(("join".equals(type) ? JOIN_FILE_PATHS_KEY : LEFT_FILE_PATHS_KEY) + mediaSuffix, "");
                if (!TextUtils.isEmpty(filePaths)) {
                    Runnable fileAction = new Runnable() {
                        public void run() {
                            for (String p : filePaths.split(",")) {
                                if (!TextUtils.isEmpty(p.trim())) {
                                    try {
                                        String fileName = new java.io.File(p.trim()).getName();
                                        shareFile(groupWxid, fileName, p.trim(), "");
                                    } catch (Exception e) {}
                                }
                            }
                        }
                    };
                    mediaTasks.put("file", new SendTask(fileAction, fileDelay));
                }
            }

            // 4. 根据 mediaOrder 和 mediaSequence 构建最终的发送任务链
            List<SendTask> finalTaskChain = new ArrayList<>();
            List<SendTask> orderedMediaTasks = new ArrayList<>();
            if (!"none".equals(mediaOrder) && !"none".equals(mediaMode)) {
                for (String seq : mediaSequence.split(",")) {
                    String mediaType = seq.trim().toLowerCase();
                    if (mediaTasks.containsKey(mediaType)) {
                        orderedMediaTasks.add(mediaTasks.get(mediaType));
                    }
                }
            }

            if ("before".equals(mediaOrder)) {
                finalTaskChain.addAll(orderedMediaTasks);
                finalTaskChain.add(promptTask);
            } else { // "after" or "none"
                finalTaskChain.add(promptTask);
                finalTaskChain.addAll(orderedMediaTasks);
            }

            // 5. 启动任务链
            if (!finalTaskChain.isEmpty()) {
                executeSendChain(finalTaskChain, 0);
            }
        }
    }, delaySeconds * 1000L);
}

private void executeSendChain(final List<SendTask> tasks, final int index) {
    if (index >= tasks.size()) {
        return;
    }
    final SendTask currentTask = tasks.get(index);
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            if (currentTask.getAction() != null) {
                currentTask.getAction().run();
            }
            executeSendChain(tasks, index + 1);
        }
    }, currentTask.getDelay());
}

private void handleTextSending(String type, String groupWxid, String userWxid, String userName, String groupName, String currentTime) {
    String textToSend = "";
    String prompts;

    // 获取当前群的模式，如果是"global"，则无视专属文本框内容，强制使用全局配置
    boolean useGlobalContent = "global".equals(getString(PROMPT_TYPE_KEY + "_" + groupWxid, "global"));

    if ("join".equals(type)) {
        String globalPrompt = getString(JOIN_TEXT_PROMPT_KEY, "[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者微信昵称：%userName%\n进群者群内昵称：%groupNickname%\n进群者ID：%userWxid%");
        if (useGlobalContent) {
            prompts = globalPrompt;
        } else {
            String customPrompt = getString(JOIN_TEXT_PROMPT_KEY + "_" + groupWxid, globalPrompt);
            prompts = TextUtils.isEmpty(customPrompt) ? globalPrompt : customPrompt;
        }
    } else { // left
        String globalPrompt = getString(LEFT_TEXT_PROMPT_KEY, "退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者微信昵称：%userName%\n退群者群内昵称：%groupNickname%\n退群者ID：%userWxid%");
        if (useGlobalContent) {
            prompts = globalPrompt;
        } else {
            String customPrompt = getString(LEFT_TEXT_PROMPT_KEY + "_" + groupWxid, globalPrompt);
            prompts = TextUtils.isEmpty(customPrompt) ? globalPrompt : customPrompt;
        }
    }

    if (!TextUtils.isEmpty(prompts)) {
        if (prompts.contains("||")) {
            String[] options = prompts.split("\\|\\|");
            textToSend = options[new Random().nextInt(options.length)].trim();
        } else {
            textToSend = prompts.trim();
        }

        // 1. 获取微信昵称 (好友昵称)
        String wxName = userName;
        if (TextUtils.isEmpty(wxName) || wxName.startsWith("wxid_")) {
            String fName = getFriendName(userWxid);
            if (!TextUtils.isEmpty(fName) && !"未设置".equals(fName)) {
                wxName = fName;
            } else {
                wxName = "未知成员";
            }
        }

        // 2. 获取群内昵称
        String groupNick = getFriendName(userWxid, groupWxid);
        if (TextUtils.isEmpty(groupNick) || "未设置".equals(groupNick)) {
            groupNick = "未设置"; 
        }

        textToSend = textToSend.replace("%userName%", wxName)
                               .replace("%groupNickname%", groupNick)
                               .replace("%userWxid%", userWxid)
                               .replace("%groupName%", groupName)
                               .replace("%time%", currentTime);
                               
        sendText(groupWxid, textToSend);
    }
}

private void handleCardSending(String type, String groupWxid, String userWxid, String userName, String groupName, String currentTime) {
    String titlePrompts, descPrompts;

    // 获取当前群的模式，如果是"global"，则强制使用全局卡片配置
    boolean useGlobalContent = "global".equals(getString(PROMPT_TYPE_KEY + "_" + groupWxid, "global"));

    if ("join".equals(type)) {
        String globalTitle = getString(JOIN_CARD_TITLE_KEY, "🎊 欢迎：%userName%");
        String globalDesc = getString(JOIN_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
        if (useGlobalContent) {
            titlePrompts = globalTitle;
            descPrompts = globalDesc;
        } else {
            String customTitle = getString(JOIN_CARD_TITLE_KEY + "_" + groupWxid, globalTitle);
            titlePrompts = TextUtils.isEmpty(customTitle) ? globalTitle : customTitle;

            String customDesc = getString(JOIN_CARD_DESC_KEY + "_" + groupWxid, globalDesc);
            descPrompts = TextUtils.isEmpty(customDesc) ? globalDesc : customDesc;
        }
    } else { // left
        String globalTitle = getString(LEFT_CARD_TITLE_KEY, "💔 离群：%userName%");
        String globalDesc = getString(LEFT_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
        if (useGlobalContent) {
            titlePrompts = globalTitle;
            descPrompts = globalDesc;
        } else {
            String customTitle = getString(LEFT_CARD_TITLE_KEY + "_" + groupWxid, globalTitle);
            titlePrompts = TextUtils.isEmpty(customTitle) ? globalTitle : customTitle;

            String customDesc = getString(LEFT_CARD_DESC_KEY + "_" + groupWxid, globalDesc);
            descPrompts = TextUtils.isEmpty(customDesc) ? globalDesc : customDesc;
        }
    }

    String titleTemplate;
    if (titlePrompts.contains("||")) {
        String[] titleOptions = titlePrompts.split("\\|\\|");
        titleTemplate = titleOptions[new Random().nextInt(titleOptions.length)].trim();
    } else {
        titleTemplate = titlePrompts.trim();
    }

    String descTemplate;
    if (descPrompts.contains("||")) {
        String[] descOptions = descPrompts.split("\\|\\|");
        descTemplate = descOptions[new Random().nextInt(descOptions.length)].trim();
    } else {
        descTemplate = descPrompts.trim();
    }

    // 1. 获取微信昵称 (好友昵称)
    String wxName = userName;
    if (TextUtils.isEmpty(wxName) || wxName.startsWith("wxid_")) {
        String fName = getFriendName(userWxid);
        if (!TextUtils.isEmpty(fName) && !"未设置".equals(fName)) {
            wxName = fName;
        } else {
            wxName = "未知成员";
        }
    }

    // 2. 获取群内昵称
    String groupNick = getFriendName(userWxid, groupWxid);
    if (TextUtils.isEmpty(groupNick) || "未设置".equals(groupNick)) {
        groupNick = "未设置";
    }

    String title = titleTemplate.replace("%userName%", wxName)
                                .replace("%groupNickname%", groupNick)
                                .replace("%userWxid%", userWxid)
                                .replace("%groupName%", groupName)
                                .replace("%time%", currentTime);
                                
    String description = descTemplate.replace("%userName%", wxName)
                                     .replace("%groupNickname%", groupNick)
                                     .replace("%userWxid%", userWxid)
                                     .replace("%groupName%", groupName)
                                     .replace("%time%", currentTime);

    String avatarUrl = getAvatarUrl(userWxid, false);
    String bigAvatarUrl = getAvatarUrl(userWxid, true);

    MediaMessage mediaMsg = new MediaMessage();
    mediaMsg.setTitle(title);
    mediaMsg.setDescription(description);
    mediaMsg.setThumbUrl(avatarUrl);
    mediaMsg.setContentUrl(bigAvatarUrl);

    sendWXMediaMsg(groupWxid, mediaMsg, "");
}

private void sendWXMediaMsg(final String groupWxid, final MediaMessage mediaMsg, final String appId) {
    new Thread(new Runnable() {
        public void run() {
            final byte[] thumbData = getImageBytesFromUrl(mediaMsg.getThumbUrl());
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    try {
                        WXWebpageObject webpageObj = new WXWebpageObject();
                        webpageObj.webpageUrl = mediaMsg.getContentUrl();
                        WXMediaMessage wxMsg = new WXMediaMessage(webpageObj);
                        wxMsg.title = mediaMsg.getTitle();
                        wxMsg.description = mediaMsg.getDescription();
                        if (thumbData != null && thumbData.length > 0) {
                            wxMsg.thumbData = thumbData;
                        } else {
                            wxMsg.thumbData = new byte[0];
                        }
                        sendMediaMsg(groupWxid, wxMsg, appId);
                    } catch (Exception e) {
                        toast("发送媒体消息异常: " + e.getMessage());
                        sendText(groupWxid, mediaMsg.getTitle() + "\n" + mediaMsg.getDescription());
                    }
                }
            });
        }
    }).start();
}

private byte[] getImageBytesFromUrl(String imageUrl) {
    if (TextUtils.isEmpty(imageUrl)) return null;
    try {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() == 200) {
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}

// === 文件/文件夹浏览与多选（单例模式）===

final String DEFAULT_LAST_FOLDER_SP = "last_folder_for_media";
final String ROOT_FOLER = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

// 全局变量保存文件夹浏览对话框和数据
AlertDialog gFolderDialog_JQ = null;
ArrayAdapter gFolderAdapter_JQ = null;
ArrayList gFolderNames_JQ = new ArrayList();
ArrayList gFolderFiles_JQ = new ArrayList();
File gCurrentFolder_JQ = null;
String gExtFilter_JQ = "";
String gCurrentSelection_JQ = "";
MediaSelectionCallback gCallback_JQ = null;
boolean gAllowFolderSelect_JQ = false;

void openFolderBrowser_JQ(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback) {
    openFolderBrowser_JQ(startFolder, wantedExtFilter, currentSelection, callback, false);
}

void openFolderBrowser_JQ(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    gCurrentFolder_JQ = startFolder;
    gExtFilter_JQ = wantedExtFilter;
    gCurrentSelection_JQ = currentSelection;
    gCallback_JQ = callback;
    gAllowFolderSelect_JQ = allowFolderSelect;

    if (startFolder != null && startFolder.exists() && startFolder.isDirectory()) {
        putString(DEFAULT_LAST_FOLDER_SP, startFolder.getAbsolutePath());
    }

    // 如果已存在对话框，先关闭
    if (gFolderDialog_JQ != null && gFolderDialog_JQ.isShowing()) {
        gFolderDialog_JQ.dismiss();
        gFolderDialog_JQ = null;
    }

    // 准备数据
    refreshFolderList_JQ(startFolder);

    // 创建对话框
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());

    gFolderAdapter_JQ = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, gFolderNames_JQ);
    final ListView list = new ListView(getTopActivity());
    list.setAdapter(gFolderAdapter_JQ);
    builder.setView(list);

    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            Object obj = gFolderFiles_JQ.get(pos);
            if (!(obj instanceof File)) return;
            File selected = (File) obj;
            if (selected.equals(gCurrentFolder_JQ) && gFolderNames_JQ.get(pos).toString().startsWith("⚠")) {
                toast("该目录不可读，请使用手动输入路径");
                return;
            }
            if (selected.isDirectory()) {
                gCurrentFolder_JQ = selected;
                putString(DEFAULT_LAST_FOLDER_SP, selected.getAbsolutePath());
                refreshFolderList_JQ(selected);
                gFolderAdapter_JQ.notifyDataSetChanged();
                if (gFolderDialog_JQ != null) {
                    gFolderDialog_JQ.setTitle("浏览：" + selected.getAbsolutePath());
                }
            }
        }
    });

    builder.setPositiveButton("在此目录选择文件", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialog_JQ = null;
            scanFilesMulti_JQ(gCurrentFolder_JQ, gExtFilter_JQ, gCurrentSelection_JQ, gCallback_JQ);
        }
    });

    if (allowFolderSelect) {
        builder.setNeutralButton("选择此文件夹", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
                gFolderDialog_JQ = null;
                ArrayList<String> sel = new ArrayList<String>();
                sel.add(gCurrentFolder_JQ.getAbsolutePath());
                gCallback_JQ.onSelected(sel);
            }
        });
    }

    builder.setNegativeButton("手动输入路径", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialog_JQ = null;
            showManualPathDialog_JQ(gExtFilter_JQ, gCurrentSelection_JQ, gCallback_JQ, gAllowFolderSelect_JQ);
        }
    });

    gFolderDialog_JQ = builder.create();
    gFolderDialog_JQ.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setCornerRadius(48);
            dialogBg.setColor(Color.parseColor("#FAFBF9"));
            gFolderDialog_JQ.getWindow().setBackgroundDrawable(dialogBg);
            styleDialogButtons(gFolderDialog_JQ);
        }
    });
    gFolderDialog_JQ.show();
}

void refreshFolderList_JQ(File folder) {
    gFolderNames_JQ.clear();
    gFolderFiles_JQ.clear();

    if (folder == null || !folder.exists() || !folder.isDirectory()) {
        gFolderNames_JQ.add("⚠ 路径无效或不可访问");
        gFolderFiles_JQ.add(gCurrentFolder_JQ);
        return;
    }

    // 只要有父目录就允许上一级
    if (folder.getParentFile() != null) {
        gFolderNames_JQ.add("⬆ 上一级");
        gFolderFiles_JQ.add(folder.getParentFile());
    }

    File[] subs = null;
    try {
        subs = folder.listFiles();
    } catch (Exception e) {
        subs = null;
    }

    if (subs == null) {
        gFolderNames_JQ.add("⚠ 当前目录不可读，请点手动输入路径");
        gFolderFiles_JQ.add(folder);
        return;
    }

    // 目录优先排序
    java.util.Arrays.sort(subs, new java.util.Comparator<File>() {
        public int compare(File a, File b) {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        }
    });

    boolean hasDir = false;
    for (int i = 0; i < subs.length; i++) {
        File f = subs[i];
        if (f.isDirectory()) {
            hasDir = true;
            gFolderNames_JQ.add("📁 " + f.getName());
            gFolderFiles_JQ.add(f);
        }
    }

    if (!hasDir) {
        gFolderNames_JQ.add("（此目录无子文件夹，可点在此目录选择文件）");
        gFolderFiles_JQ.add(folder);
    }
}

private void showManualPathDialog_JQ(final String wantedExtFilter, final String currentSelection,
                                     final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    android.app.Activity act = getTopActivity();
    if (act == null) return;

    LinearLayout layout = new LinearLayout(act);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);

    final EditText pathEdit = new EditText(act);
    pathEdit.setHint("输入目录路径（如 /storage/emulated/0/Download）");
    pathEdit.setText(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
    layout.addView(pathEdit);

    AlertDialog.Builder b = new AlertDialog.Builder(act);
    b.setTitle("手动输入路径");
    b.setView(layout);
    b.setPositiveButton("跳转", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) {
            String p = pathEdit.getText().toString().trim();
            File f = new File(p);
            if (f.exists() && f.isDirectory()) {
                openFolderBrowser_JQ(f, wantedExtFilter, currentSelection, callback, allowFolderSelect);
            } else {
                toast("路径无效或不可访问");
            }
        }
    });
    b.setNegativeButton("取消", null);
    final AlertDialog d = b.create();
    d.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface di) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setCornerRadius(48);
            dialogBg.setColor(Color.parseColor("#FAFBF9"));
            d.getWindow().setBackgroundDrawable(dialogBg);
            styleDialogButtons(d);
        }
    });
    d.show();
}

void scanFilesMulti_JQ(final File folder, final String extFilter, final String currentSelection, final MediaSelectionCallback callback) {
    final ArrayList<String> names = new ArrayList<>();
    final ArrayList<File> files = new ArrayList<>();
    
    File[] list = folder.listFiles();
    if (list != null) {
        for (File f : list) {
            if (f.isFile()) {
                if (TextUtils.isEmpty(extFilter) || f.getName().toLowerCase().endsWith(extFilter.toLowerCase())) {
                    names.add(f.getName());
                    files.add(f);
                }
            }
        }
    }
    
    if (names.isEmpty()) {
        toast("该目录无匹配文件");
        return;
    }
    
    final Set<String> selectedPathsSet = new HashSet<>();
    if (!TextUtils.isEmpty(currentSelection)) {
        selectedPathsSet.addAll(Arrays.asList(currentSelection.split(",")));
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件（可多选）：" + folder.getAbsolutePath());
    final ListView listView = new ListView(getTopActivity());
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, names));
    builder.setView(listView);
    
    for (int i = 0; i < files.size(); i++) {
        if (selectedPathsSet.contains(files.get(i).getAbsolutePath())) {
            listView.setItemChecked(i, true);
        }
    }
    
    builder.setPositiveButton("确认选择", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            ArrayList<String> selectedPaths = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (listView.isItemChecked(i)) {
                    selectedPaths.add(files.get(i).getAbsolutePath());
                }
            }
            callback.onSelected(selectedPaths);
        }
    });
    
    builder.setNegativeButton("取消", null);
    final AlertDialog scanDialog = builder.create();
    scanDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setCornerRadius(48);
            dialogBg.setColor(Color.parseColor("#FAFBF9"));
            scanDialog.getWindow().setBackgroundDrawable(dialogBg);
            styleDialogButtons(scanDialog);
        }
    });
    scanDialog.show();
}

// 兼容旧接口
void browseFolderForSelection(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback) {
    openFolderBrowser_JQ(startFolder, wantedExtFilter, currentSelection, callback, false);
}

void scanFilesMulti(final File folder, final String extFilter, final String currentSelection, final MediaSelectionCallback callback) {
    final ArrayList<String> names = new ArrayList<>();
    final ArrayList<File> files = new ArrayList<>();

    File[] list = folder.listFiles();
    if (list != null) {
        for (File f : list) {
            if (f.isFile()) {
                if (TextUtils.isEmpty(extFilter) || f.getName().toLowerCase().endsWith(extFilter.toLowerCase())) {
                    names.add(f.getName());
                    files.add(f);
                }
            }
        }
    }

    if (names.isEmpty()) {
        toast("该目录无匹配文件");
        return;
    }

    final Set<String> selectedPathsSet = new HashSet<>();
    if (!TextUtils.isEmpty(currentSelection)) {
        selectedPathsSet.addAll(Arrays.asList(currentSelection.split(",")));
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件（可多选）：" + folder.getAbsolutePath());
    final ListView listView = new ListView(getTopActivity());
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, names));
    builder.setView(listView);

    for (int i = 0; i < files.size(); i++) {
        if (selectedPathsSet.contains(files.get(i).getAbsolutePath())) {
            listView.setItemChecked(i, true);
        }
    }

    builder.setPositiveButton("确认选择", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            ArrayList<String> selectedPaths = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (listView.isItemChecked(i)) {
                    selectedPaths.add(files.get(i).getAbsolutePath());
                }
            }
            callback.onSelected(selectedPaths);
        }
    });

    builder.setNegativeButton("取消", null);
    builder.create().show();
}

// === UI生成方法：快捷插入变量栏 ===
private View createVariableBar() {
    LinearLayout container = new LinearLayout(getTopActivity());
    container.setOrientation(LinearLayout.VERTICAL);

    TextView tip = new TextView(getTopActivity());
    tip.setText("💡 点击下方变量快速插入到光标处：");
    tip.setTextSize(12);
    tip.setTextColor(Color.parseColor("#666666"));
    tip.setPadding(0, 0, 0, 16);
    container.addView(tip);

    HorizontalScrollView hsv = new HorizontalScrollView(getTopActivity());
    hsv.setHorizontalScrollBarEnabled(false);
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.HORIZONTAL);
    layout.setPadding(0, 0, 0, 24);

    String[] vars = {"%userName%", "%groupNickname%", "%userWxid%", "%groupName%", "%time%", "[AtWx=%userWxid%]", "[AtWx=]"};
    String[] labels = {"+ 微信昵称", "+ 群内昵称", "+ Wxid", "+ 群名", "+ 时间", "+ @新人", "+ @其他人"};

    for (int i=0; i<vars.length; i++) {
        final int index = i;
        TextView chip = new TextView(getTopActivity());
        chip.setText(labels[i]);
        chip.setTag(vars[i]);
        chip.setTextSize(13);
        chip.setTextColor(Color.parseColor("#4A90E2"));
        chip.setPadding(32, 16, 32, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(30);
        bg.setColor(Color.parseColor("#EBF3FA"));
        bg.setStroke(2, Color.parseColor("#BBD7E6"));
        chip.setBackground(bg);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 16, 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (currentFocusedEdit != null) {
                    String clickedTag = (String) v.getTag();
                    
                    if ("[AtWx=]".equals(clickedTag)) {
                        showAtOtherInputDialog();
                    } else {

                        String realVarToInsert = clickedTag;
                        int start = Math.max(currentFocusedEdit.getSelectionStart(), 0);
                        int end = Math.max(currentFocusedEdit.getSelectionEnd(), 0);
                        Editable editable = currentFocusedEdit.getText();
                        if (editable != null) {
                            editable.replace(Math.min(start, end), Math.max(start, end), realVarToInsert);
                        }
                    }
                } else {
                    toast("请先点击选中一个输入框");
                }
            }
        });
        layout.addView(chip);
    }
    hsv.addView(layout);
    container.addView(hsv);
    return container;
}

// === 艾特其他人的输入对话框 ===
private void showAtOtherInputDialog() {
    if (currentFocusedEdit == null) {
        toast("请先点击选中一个输入框");
        return;
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("艾特其他人");
    builder.setMessage("请输入要艾特的人的微信ID (wxid开头)：");

    final EditText input = new EditText(getTopActivity());
    input.setHint("例如: wxid_xxxxxx");
    input.setPadding(40, 30, 40, 30);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    
    // 设置输入框样式
    GradientDrawable inputBg = new GradientDrawable();
    inputBg.setShape(GradientDrawable.RECTANGLE);
    inputBg.setCornerRadius(20);
    inputBg.setColor(Color.parseColor("#F8F9FA"));
    inputBg.setStroke(2, Color.parseColor("#E6E9EE"));
    input.setBackground(inputBg);
    
    LinearLayout container = new LinearLayout(getTopActivity());
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(40, 20, 40, 0);
    container.addView(input);
    
    // 添加提示
    TextView hintText = new TextView(getTopActivity());
    hintText.setText("💡 提示: 可输入群成员的wxid，输入后将插入 [AtWx=wxid] 格式");
    hintText.setTextSize(12);
    hintText.setTextColor(Color.parseColor("#999999"));
    hintText.setPadding(40, 10, 40, 0);
    container.addView(hintText);
    
    builder.setView(container);

    builder.setPositiveButton("插入变量", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String wxid = input.getText().toString().trim();
            if (!TextUtils.isEmpty(wxid)) {
                String atVar = "[AtWx=" + wxid + "]";
                // 直接使用保存的currentFocusedEdit
                if (currentFocusedEdit != null) {
                    int start = Math.max(currentFocusedEdit.getSelectionStart(), 0);
                    int end = Math.max(currentFocusedEdit.getSelectionEnd(), 0);
                    Editable editable = currentFocusedEdit.getText();
                    if (editable != null) {
                        editable.replace(Math.min(start, end), Math.max(start, end), atVar);
                        toast("已插入艾特变量: " + atVar);
                    }
                } else {
                    toast("插入失败：无法获取输入框焦点");
                }
            } else {
                toast("请输入微信ID");
            }
        }
    });
    
    builder.setNegativeButton("取消", null);
    
    AlertDialog dialog = builder.create();
    dialog.show();
}

// === 通用媒体选择按钮绑定工具 ===
private void setupMediaButton(Button btn, final TextView countTv, final String key, final String extFilter, final String typeName) {
    styleMediaSelectionButton(btn);
    styleCountTextView(countTv);
    
    btn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
            String currentSelection = getString(key, "");
            browseFolderForSelection(last, extFilter, currentSelection, new MediaSelectionCallback() {
                public void onSelected(ArrayList<String> selectedFiles) {
                    putString(key, joinPaths(selectedFiles));
                    countTv.setText(selectedFiles.size() + " 个已选");
                    toast("已保存" + typeName + " (" + selectedFiles.size() + ")");
                }
            });
        }
    });
    countTv.setText(countFromString(getString(key, "")) + " 个已选");
}

// === 新的统一设置界面 ===
private void showUnifiedSettingsDialog() {
    try {
        currentFocusedEdit = null; // 重置焦点状态
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        // --- 卡片1: 主要功能管理 ---
        LinearLayout managementCard = createCardLayout();
        managementCard.addView(createSectionTitle("⚙️ 主要功能管理"));
        Button groupManagementButton = new Button(getTopActivity());
        groupManagementButton.setText("管理监听群组和专属进退群设置");
        styleUtilityButton(groupManagementButton);
        managementCard.addView(groupManagementButton);
        rootLayout.addView(managementCard);

        // --- 卡片2: 核心设置 ---
        LinearLayout coreSettingsCard = createCardLayout();
        coreSettingsCard.addView(createSectionTitle("🚀 全局核心设置"));
        coreSettingsCard.addView(newTextView("触发后整体延迟（秒）:"));
        final EditText delayEditText = createStyledEditText("0-600秒", String.valueOf(getInt(DELAY_KEY, DEFAULT_DELAY)));
        delayEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        coreSettingsCard.addView(delayEditText);
        coreSettingsCard.addView(newTextView("选择提示语类型:"));
        RadioGroup promptTypeGroup = new RadioGroup(getTopActivity());
        promptTypeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton textTypeButton = new RadioButton(getTopActivity()); textTypeButton.setText("文本");
        final RadioButton bothTypeButton = new RadioButton(getTopActivity()); bothTypeButton.setText("文本+卡片");
        final RadioButton cardTypeButton = new RadioButton(getTopActivity()); cardTypeButton.setText("卡片");
        promptTypeGroup.addView(textTypeButton);
        promptTypeGroup.addView(bothTypeButton);
        promptTypeGroup.addView(cardTypeButton);
        String currentPromptType = getString(PROMPT_TYPE_KEY, "text");
        if ("card".equals(currentPromptType)) {
            cardTypeButton.setChecked(true);
        } else if ("both".equals(currentPromptType)) {
            bothTypeButton.setChecked(true);
        } else {
            textTypeButton.setChecked(true);
        }
        coreSettingsCard.addView(promptTypeGroup);
        
        // 当选择"文本+卡片"时显示顺序选择
        final LinearLayout bothOrderContainer = new LinearLayout(getTopActivity());
        bothOrderContainer.setOrientation(LinearLayout.VERTICAL);
        bothOrderContainer.setVisibility(View.GONE);
        
        TextView bothOrderLabel = new TextView(getTopActivity());
        bothOrderLabel.setText("文本+卡片发送顺序:");
        bothOrderLabel.setTextSize(14);
        bothOrderLabel.setTextColor(Color.parseColor("#666666"));
        bothOrderLabel.setPadding(0, 16, 0, 8);
        bothOrderContainer.addView(bothOrderLabel);
        
        RadioGroup bothOrderGroup = new RadioGroup(getTopActivity());
        bothOrderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton bothTextFirstButton = new RadioButton(getTopActivity()); bothTextFirstButton.setText("先文本后卡片");
        final RadioButton bothCardFirstButton = new RadioButton(getTopActivity()); bothCardFirstButton.setText("先卡片后文本");
        bothOrderGroup.addView(bothTextFirstButton);
        bothOrderGroup.addView(bothCardFirstButton);
        String currentBothOrder = getString(PROMPT_BOTH_ORDER_KEY, "text_first");
        if ("card_first".equals(currentBothOrder)) {
            bothCardFirstButton.setChecked(true);
        } else {
            bothTextFirstButton.setChecked(true);
        }
        bothOrderContainer.addView(bothOrderGroup);
        coreSettingsCard.addView(bothOrderContainer);
        
        // 监听提示语类型选择，控制顺序选择的显示
        promptTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == bothTypeButton.getId()) {
                    bothOrderContainer.setVisibility(View.VISIBLE);
                } else {
                    bothOrderContainer.setVisibility(View.GONE);
                }
            }
        });
        // 初始化显示状态
        if (bothTypeButton.isChecked()) {
            bothOrderContainer.setVisibility(View.VISIBLE);
        }
        
        rootLayout.addView(coreSettingsCard);

        // --- 卡片3: 文本提示语设置 ---
        LinearLayout textPromptCard = createCardLayout();
        textPromptCard.addView(createSectionTitle("📝 全局文本提示语"));
        textPromptCard.addView(createVariableBar()); // 添加变量插入栏
        final EditText joinPromptEditText = createStyledEditText("设置进群欢迎语", getString(JOIN_TEXT_PROMPT_KEY, "[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者微信昵称：%userName%\n进群者群内昵称：%groupNickname%\n进群者ID：%userWxid%"));
        joinPromptEditText.setLines(5);
        joinPromptEditText.setGravity(Gravity.TOP);
        textPromptCard.addView(joinPromptEditText);
        final EditText leftPromptEditText = createStyledEditText("设置退群通知", getString(LEFT_TEXT_PROMPT_KEY, "退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者微信昵称：%userName%\n退群者群内昵称：%groupNickname%\n退群者ID：%userWxid%"));
        leftPromptEditText.setLines(5);
        leftPromptEditText.setGravity(Gravity.TOP);
        textPromptCard.addView(leftPromptEditText);

        Button fillRandomTextButton = new Button(getTopActivity());
        fillRandomTextButton.setText("💡 随机填充一条欢迎/退群语");
        styleFillButton(fillRandomTextButton);
        textPromptCard.addView(fillRandomTextButton);

        Button restoreTextDefaultsButton = new Button(getTopActivity());
        restoreTextDefaultsButton.setText("🔄 恢复文本默认");
        styleRestoreButton(restoreTextDefaultsButton);
        textPromptCard.addView(restoreTextDefaultsButton);
        rootLayout.addView(textPromptCard);

        // --- 卡片4: 卡片提示语设置 ---
        LinearLayout cardPromptCard = createCardLayout();
        cardPromptCard.addView(createSectionTitle("🖼️ 全局卡片提示语"));
        cardPromptCard.addView(createVariableBar()); // 添加变量插入栏
        final EditText joinTitleEditText = createStyledEditText("进群卡片标题", getString(JOIN_CARD_TITLE_KEY, "🎊 欢迎：%userName%"));
        final EditText joinDescEditText = createStyledEditText("进群卡片描述", getString(JOIN_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%"));
        joinDescEditText.setLines(3); joinDescEditText.setGravity(Gravity.TOP);
        cardPromptCard.addView(joinTitleEditText);
        cardPromptCard.addView(joinDescEditText);
        final EditText leftTitleEditText = createStyledEditText("退群卡片标题", getString(LEFT_CARD_TITLE_KEY, "💔 离群：%userName%"));
        final EditText leftDescEditText = createStyledEditText("退群卡片描述", getString(LEFT_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%"));
        leftDescEditText.setLines(3); leftDescEditText.setGravity(Gravity.TOP);
        cardPromptCard.addView(leftTitleEditText);
        cardPromptCard.addView(leftDescEditText);

        Button fillRandomCardButton = new Button(getTopActivity());
        fillRandomCardButton.setText("💡 随机填充卡片内容");
        styleFillButton(fillRandomCardButton);
        cardPromptCard.addView(fillRandomCardButton);

        Button restoreCardDefaultsButton = new Button(getTopActivity());
        restoreCardDefaultsButton.setText("🔄 恢复卡片默认");
        styleRestoreButton(restoreCardDefaultsButton);
        cardPromptCard.addView(restoreCardDefaultsButton);
        rootLayout.addView(cardPromptCard);

        // --- 卡片5: 媒体设置 (通用) ---
        LinearLayout mediaCard = createCardLayout();
        mediaCard.addView(createSectionTitle("📂 全局媒体文件配置"));
        mediaCard.addView(newTextView("媒体发送顺序 (英文逗号隔开):"));
        final EditText mediaSequenceEdit = createStyledEditText("如: image,voice,video...", getString(SEND_MEDIA_SEQUENCE_KEY, "image,voice,emoji,video,file"));
        mediaCard.addView(mediaSequenceEdit);

        RadioGroup mediaOrderGroup = new RadioGroup(getTopActivity());
        mediaOrderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton noneButton = new RadioButton(getTopActivity()); noneButton.setText("不发送");
        final RadioButton beforeButton = new RadioButton(getTopActivity()); beforeButton.setText("先媒体,后提示");
        final RadioButton afterButton = new RadioButton(getTopActivity()); afterButton.setText("先提示,后媒体");
        mediaOrderGroup.addView(noneButton); mediaOrderGroup.addView(beforeButton); mediaOrderGroup.addView(afterButton);
        String currentOrder = getString(SEND_MEDIA_ORDER_KEY, "none");
        if ("before".equals(currentOrder)) beforeButton.setChecked(true);
        else if ("after".equals(currentOrder)) afterButton.setChecked(true);
        else noneButton.setChecked(true);
        mediaCard.addView(mediaOrderGroup);

        mediaCard.addView(createSectionTitle("🗂️ 媒体文件选择（支持多选）"));

        Button btnSelectJoinImages = new Button(getTopActivity()); btnSelectJoinImages.setText("选择进群图片");
        TextView tvJoinImagesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectJoinImages, tvJoinImagesCount, JOIN_IMAGE_PATHS_KEY, ".png", "进群图片");

        Button btnSelectLeftImages = new Button(getTopActivity()); btnSelectLeftImages.setText("选择退群图片");
        TextView tvLeftImagesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectLeftImages, tvLeftImagesCount, LEFT_IMAGE_PATHS_KEY, ".png", "退群图片");

        Button btnSelectJoinVoices = new Button(getTopActivity()); btnSelectJoinVoices.setText("选择进群语音");
        TextView tvJoinVoicesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectJoinVoices, tvJoinVoicesCount, JOIN_VOICE_PATHS_KEY, "", "进群语音");

        Button btnSelectLeftVoices = new Button(getTopActivity()); btnSelectLeftVoices.setText("选择退群语音");
        TextView tvLeftVoicesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectLeftVoices, tvLeftVoicesCount, LEFT_VOICE_PATHS_KEY, "", "退群语音");

        Button btnSelectJoinEmojis = new Button(getTopActivity()); btnSelectJoinEmojis.setText("选择进群表情");
        TextView tvJoinEmojisCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectJoinEmojis, tvJoinEmojisCount, JOIN_EMOJI_PATHS_KEY, ".png", "进群表情");

        Button btnSelectLeftEmojis = new Button(getTopActivity()); btnSelectLeftEmojis.setText("选择退群表情");
        TextView tvLeftEmojisCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectLeftEmojis, tvLeftEmojisCount, LEFT_EMOJI_PATHS_KEY, ".png", "退群表情");

        Button btnSelectJoinVideos = new Button(getTopActivity()); btnSelectJoinVideos.setText("选择进群视频");
        TextView tvJoinVideosCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectJoinVideos, tvJoinVideosCount, JOIN_VIDEO_PATHS_KEY, ".mp4", "进群视频");

        Button btnSelectLeftVideos = new Button(getTopActivity()); btnSelectLeftVideos.setText("选择退群视频");
        TextView tvLeftVideosCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectLeftVideos, tvLeftVideosCount, LEFT_VIDEO_PATHS_KEY, ".mp4", "退群视频");

        Button btnSelectJoinFiles = new Button(getTopActivity()); btnSelectJoinFiles.setText("选择进群文件");
        TextView tvJoinFilesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectJoinFiles, tvJoinFilesCount, JOIN_FILE_PATHS_KEY, "", "进群文件");

        Button btnSelectLeftFiles = new Button(getTopActivity()); btnSelectLeftFiles.setText("选择退群文件");
        TextView tvLeftFilesCount = new TextView(getTopActivity());
        setupMediaButton(btnSelectLeftFiles, tvLeftFilesCount, LEFT_FILE_PATHS_KEY, "", "退群文件");

        mediaCard.addView(horizontalRow(btnSelectJoinImages, tvJoinImagesCount));
        mediaCard.addView(horizontalRow(btnSelectLeftImages, tvLeftImagesCount));
        mediaCard.addView(horizontalRow(btnSelectJoinVoices, tvJoinVoicesCount));
        mediaCard.addView(horizontalRow(btnSelectLeftVoices, tvLeftVoicesCount));
        mediaCard.addView(horizontalRow(btnSelectJoinEmojis, tvJoinEmojisCount));
        mediaCard.addView(horizontalRow(btnSelectLeftEmojis, tvLeftEmojisCount));
        mediaCard.addView(horizontalRow(btnSelectJoinVideos, tvJoinVideosCount));
        mediaCard.addView(horizontalRow(btnSelectLeftVideos, tvLeftVideosCount));
        mediaCard.addView(horizontalRow(btnSelectJoinFiles, tvJoinFilesCount));
        mediaCard.addView(horizontalRow(btnSelectLeftFiles, tvLeftFilesCount));

        rootLayout.addView(mediaCard);

        // --- 卡片6: 精细延迟设置 (通用) ---
        LinearLayout delayCard = createCardLayout();
        delayCard.addView(createSectionTitle("⏱️ 全局精细延迟设置 (毫秒)"));
        delayCard.addView(newTextView("提示语延迟:"));
        final EditText promptDelayEdit = createStyledEditText("0", String.valueOf(getInt(PROMPT_DELAY_KEY, 0)));
        delayCard.addView(promptDelayEdit);
        delayCard.addView(newTextView("图片延迟:"));
        final EditText imageDelayEdit = createStyledEditText("100", String.valueOf(getInt(IMAGE_DELAY_KEY, 100)));
        delayCard.addView(imageDelayEdit);
        delayCard.addView(newTextView("语音延迟:"));
        final EditText voiceDelayEdit = createStyledEditText("100", String.valueOf(getInt(VOICE_DELAY_KEY, 100)));
        delayCard.addView(voiceDelayEdit);
        delayCard.addView(newTextView("表情延迟:"));
        final EditText emojiDelayEdit = createStyledEditText("100", String.valueOf(getInt(EMOJI_DELAY_KEY, 100)));
        delayCard.addView(emojiDelayEdit);
        delayCard.addView(newTextView("视频延迟:"));
        final EditText videoDelayEdit = createStyledEditText("100", String.valueOf(getInt(VIDEO_DELAY_KEY, 100)));
        delayCard.addView(videoDelayEdit);
        delayCard.addView(newTextView("文件延迟:"));
        final EditText fileDelayEdit = createStyledEditText("100", String.valueOf(getInt(FILE_DELAY_KEY, 100)));
        delayCard.addView(fileDelayEdit);
        rootLayout.addView(delayCard);

        // --- 对话框构建 ---
        final AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
            .setTitle("✨ 全局进退群设置 ✨")
            .setView(scrollView)
            .setPositiveButton("✅ 保存全部", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        int newDelay = Integer.parseInt(delayEditText.getText().toString());
                        if (newDelay >= 0 && newDelay <= 600) putInt(DELAY_KEY, newDelay); else { toast("总延迟应在0-600秒之间"); return; }
                        
                        // 先校验全部数字再保存
                        int pDelay = Integer.parseInt(promptDelayEdit.getText().toString());
                        int iDelay = Integer.parseInt(imageDelayEdit.getText().toString());
                        int vDelay = Integer.parseInt(voiceDelayEdit.getText().toString());
                        int eDelay = Integer.parseInt(emojiDelayEdit.getText().toString());
                        int viDelay = Integer.parseInt(videoDelayEdit.getText().toString());
                        int fDelay = Integer.parseInt(fileDelayEdit.getText().toString());

                        // 保存提示语类型
                        String promptTypeToSave = "text";
                        if (cardTypeButton.isChecked()) {
                            promptTypeToSave = "card";
                        } else if (bothTypeButton.isChecked()) {
                            promptTypeToSave = "both";
                        }
                        putString(PROMPT_TYPE_KEY, promptTypeToSave);
                        
                        // 保存文本+卡片顺序
                        String bothOrder = bothTextFirstButton.isChecked() ? "text_first" : "card_first";
                        putString(PROMPT_BOTH_ORDER_KEY, bothOrder);

                        putString(JOIN_TEXT_PROMPT_KEY, joinPromptEditText.getText().toString());
                        putString(LEFT_TEXT_PROMPT_KEY, leftPromptEditText.getText().toString());
                        putString(JOIN_CARD_TITLE_KEY, joinTitleEditText.getText().toString());
                        putString(JOIN_CARD_DESC_KEY, joinDescEditText.getText().toString());
                        putString(LEFT_CARD_TITLE_KEY, leftTitleEditText.getText().toString());
                        putString(LEFT_CARD_DESC_KEY, leftDescEditText.getText().toString());

                        putString(SEND_MEDIA_SEQUENCE_KEY, mediaSequenceEdit.getText().toString());
                        putString(SEND_MEDIA_ORDER_KEY, noneButton.isChecked() ? "none" : beforeButton.isChecked() ? "before" : "after");

                        putInt(PROMPT_DELAY_KEY, pDelay);
                        putInt(IMAGE_DELAY_KEY, iDelay);
                        putInt(VOICE_DELAY_KEY, vDelay);
                        putInt(EMOJI_DELAY_KEY, eDelay);
                        putInt(VIDEO_DELAY_KEY, viDelay);
                        putInt(FILE_DELAY_KEY, fDelay);

                        toast("全局设置已保存！");
                    } catch (NumberFormatException e) {
                        toast("保存失败：所有延迟时间必须是有效数字!");
                    } catch (Exception ex) {
                        toast("保存失败: " + ex.getMessage());
                    }
                }
            })
            .setNegativeButton("❌ 取消", null)
            .setNeutralButton("🔄 恢复默认", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    delayEditText.setText(String.valueOf(DEFAULT_DELAY));
                    textTypeButton.setChecked(true);
                    joinPromptEditText.setText("[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者微信昵称：%userName%\n进群者群内昵称：%groupNickname%\n进群者ID：%userWxid%");
                    leftPromptEditText.setText("退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者微信昵称：%userName%\n退群者群内昵称：%groupNickname%\n退群者ID：%userWxid%");
                    joinTitleEditText.setText("🎊 欢迎：%userName%");
                    joinDescEditText.setText("🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
                    leftTitleEditText.setText("💔 离群：%userName%");
                    leftDescEditText.setText("🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
                    noneButton.setChecked(true);
                    mediaSequenceEdit.setText("image,voice,emoji,video,file");
                    putString(JOIN_IMAGE_PATHS_KEY, ""); putString(LEFT_IMAGE_PATHS_KEY, "");
                    putString(JOIN_VOICE_PATHS_KEY, ""); putString(LEFT_VOICE_PATHS_KEY, "");
                    putString(JOIN_EMOJI_PATHS_KEY, ""); putString(LEFT_EMOJI_PATHS_KEY, "");
                    putString(JOIN_VIDEO_PATHS_KEY, ""); putString(LEFT_VIDEO_PATHS_KEY, "");
                    putString(JOIN_FILE_PATHS_KEY, ""); putString(LEFT_FILE_PATHS_KEY, "");
                    promptDelayEdit.setText("0"); imageDelayEdit.setText("100");
                    voiceDelayEdit.setText("100"); emojiDelayEdit.setText("100");
                    videoDelayEdit.setText("100"); fileDelayEdit.setText("100");
                    toast("已恢复全局所有默认设置");
                }
            })
            .create();

        groupManagementButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showGroupManagementDialog();
            }
        });

        fillRandomTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String randomJoin = RANDOM_JOIN_TEXTS_ARRAY[new Random().nextInt(RANDOM_JOIN_TEXTS_ARRAY.length)];
                String randomLeft = RANDOM_LEFT_TEXTS_ARRAY[new Random().nextInt(RANDOM_LEFT_TEXTS_ARRAY.length)];
                joinPromptEditText.setText(randomJoin);
                leftPromptEditText.setText(randomLeft);
                toast("已随机填充欢迎/退群语");
            }
        });

        restoreTextDefaultsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinPromptEditText.setText("[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者微信昵称：%userName%\n进群者群内昵称：%groupNickname%\n进群者ID：%userWxid%");
                leftPromptEditText.setText("退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者微信昵称：%userName%\n退群者群内昵称：%groupNickname%\n退群者ID：%userWxid%");
                toast("已恢复文本默认");
            }
        });

        fillRandomCardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String title = RANDOM_JOIN_CARD_TITLES_ARRAY[new Random().nextInt(RANDOM_JOIN_CARD_TITLES_ARRAY.length)];
                String desc = RANDOM_JOIN_CARD_DESCS_ARRAY[new Random().nextInt(RANDOM_JOIN_CARD_DESCS_ARRAY.length)];
                joinTitleEditText.setText(title);
                joinDescEditText.setText(desc);

                String ltitle = RANDOM_LEFT_CARD_TITLES_ARRAY[new Random().nextInt(RANDOM_LEFT_CARD_TITLES_ARRAY.length)];
                String ldesc = RANDOM_LEFT_CARD_DESCS_ARRAY[new Random().nextInt(RANDOM_LEFT_CARD_DESCS_ARRAY.length)];
                leftTitleEditText.setText(ltitle);
                leftDescEditText.setText(ldesc);

                toast("已随机填充卡片内容");
            }
        });

        restoreCardDefaultsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinTitleEditText.setText("🎊 欢迎：%userName%");
                joinDescEditText.setText("🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
                leftTitleEditText.setText("💔 离群：%userName%");
                leftDescEditText.setText("🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
                toast("已恢复卡片默认");
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                GradientDrawable dialogBg = new GradientDrawable();
                dialogBg.setCornerRadius(48);
                dialogBg.setColor(Color.parseColor("#FAFBF9"));
                dialog.getWindow().setBackgroundDrawable(dialogBg);

                styleDialogButtons(dialog);
            }
        });

        dialog.show();

    } catch (Exception e) {
        toast("打开设置界面失败: " + e.getMessage());
    }
}

private String joinPaths(ArrayList<String> paths) {
    if (paths == null) return "";
    StringBuilder sb = new StringBuilder();
    for (String p : paths) {
        if (sb.length() > 0) sb.append(",");
        sb.append(p);
    }
    return sb.toString();
}

private int countFromString(String s) {
    if (TextUtils.isEmpty(s)) return 0;
    String[] parts = s.split(",");
    int cnt = 0;
    for (String p : parts) if (!TextUtils.isEmpty(p.trim())) cnt++;
    return cnt;
}

// UI 美化辅助方法与布局构建

private LinearLayout createCardLayout() {
    LinearLayout layout = new LinearLayout(getTopActivity());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 16);
    layout.setLayoutParams(params);

    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(32);
    shape.setColor(Color.parseColor("#FFFFFF"));
    layout.setBackground(shape);
    try { layout.setElevation(8); } catch (Exception e) {}
    return layout;
}

private TextView createSectionTitle(String text) {
    TextView textView = new TextView(getTopActivity());
    textView.setText(text);
    textView.setTextSize(16);
    textView.setTextColor(Color.parseColor("#333333"));
    try { textView.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 0, 0, 24);
    textView.setLayoutParams(params);
    return textView;
}

private EditText createStyledEditText(String hint, String initialText) {
    EditText editText = new EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setPadding(32, 28, 32, 28);
    editText.setTextSize(14);
    editText.setTextColor(Color.parseColor("#555555"));
    editText.setHintTextColor(Color.parseColor("#999999"));
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(24);
    shape.setColor(Color.parseColor("#F8F9FA"));
    shape.setStroke(2, Color.parseColor("#E6E9EE"));
    editText.setBackground(shape);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 16);
    editText.setLayoutParams(params);
    
    // 跟踪获得焦点的EditText用于变量快捷插入
    editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            if (hasFocus) {
                bg.setStroke(3, Color.parseColor("#7AA6C2")); 
                currentFocusedEdit = (EditText) v;
            } else {
                bg.setStroke(2, Color.parseColor("#E6E9EE"));
            }
        }
    });
    return editText;
}

private LinearLayout horizontalRow(View left, View right) {
    LinearLayout row = new LinearLayout(getTopActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 8); 
    row.setLayoutParams(params);

    LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    left.setLayoutParams(lpLeft);
    right.setLayoutParams(lpRight);
    row.addView(left);
    row.addView(right);
    return row;
}

private void styleDialogButtons(AlertDialog dialog) {
    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (positiveButton != null) {
        positiveButton.setTextColor(Color.WHITE);
        GradientDrawable positiveShape = new GradientDrawable();
        positiveShape.setShape(GradientDrawable.RECTANGLE);
        positiveShape.setCornerRadius(20);
        positiveShape.setColor(Color.parseColor("#70A1B8"));
        positiveButton.setBackground(positiveShape);
        positiveButton.setAllCaps(false);
    }
    Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (negativeButton != null) {
        negativeButton.setTextColor(Color.parseColor("#333333"));
        GradientDrawable negativeShape = new GradientDrawable();
        negativeShape.setShape(GradientDrawable.RECTANGLE);
        negativeShape.setCornerRadius(20);
        negativeShape.setColor(Color.parseColor("#F1F3F5"));
        negativeButton.setBackground(negativeShape);
        negativeButton.setAllCaps(false);
    }
    Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        neutralButton.setTextColor(Color.parseColor("#4A90E2"));
        neutralButton.setBackgroundColor(Color.TRANSPARENT);
        neutralButton.setAllCaps(false);
    }
}

private void styleUtilityButton(Button button) {
    button.setTextColor(Color.parseColor("#4A90E2"));
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(20);
    shape.setStroke(3, Color.parseColor("#BBD7E6"));
    shape.setColor(Color.TRANSPARENT);
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 8);
    button.setLayoutParams(params);
}

private void styleFillButton(Button button) {
    button.setTextColor(Color.parseColor("#2E7D32"));
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(20);
    shape.setStroke(3, Color.parseColor("#AED581"));
    shape.setColor(Color.parseColor("#F1F8E9"));
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.gravity = Gravity.END;
    params.setMargins(0, 8, 0, 16);
    button.setLayoutParams(params);
}

private void styleRestoreButton(Button button) {
    button.setTextColor(Color.parseColor("#444444"));
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(20);
    shape.setStroke(2, Color.parseColor("#DDDDDD"));
    shape.setColor(Color.parseColor("#FFFFFF"));
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.gravity = Gravity.END;
    params.setMargins(0, 8, 0, 16);
    button.setLayoutParams(params);
}

private void styleMediaSelectionButton(Button button) {
    button.setTextColor(Color.parseColor("#3B82F6")); 
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(20);
    shape.setColor(Color.parseColor("#EFF6FF")); 
    shape.setStroke(2, Color.parseColor("#BFDBFE")); 
    button.setBackground(shape);
    button.setAllCaps(false);
    button.setPadding(20, 12, 20, 12);
}

private void styleCountTextView(TextView tv) {
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setTextSize(14);
    tv.setPadding(16, 0, 8, 0);
    tv.setGravity(Gravity.CENTER_VERTICAL);
}

// === 辅助方法和群组管理 ===

private TextView newTextView(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setPadding(0, 10, 0, 0);
    tv.setTextColor(Color.parseColor("#333333"));
    return tv;
}

private String getGroupNameById(String groupWxid) {
    List<GroupInfo> allGroupList = getGroupList();
    if (allGroupList != null) {
        for (GroupInfo groupInfo : allGroupList) {
            if (groupInfo.getRoomId() != null && groupInfo.getRoomId().equals(groupWxid)) {
                return TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
            }
        }
    }
    return "未知群聊";
}

private void showGroupManagementDialog() {
    LinearLayout initialLayout = new LinearLayout(getTopActivity());
    initialLayout.setOrientation(LinearLayout.HORIZONTAL);
    initialLayout.setPadding(50, 50, 50, 50);
    initialLayout.setGravity(Gravity.CENTER_VERTICAL);
    initialLayout.addView(new ProgressBar(getTopActivity()));
    TextView loadingText = new TextView(getTopActivity());
    loadingText.setText("  正在加载群聊列表...");
    loadingText.setPadding(20, 0, 0, 0);
    initialLayout.addView(loadingText);

    final AlertDialog loadingDialog = new AlertDialog.Builder(getTopActivity())
        .setTitle("🌟 群组管理 🌟")
        .setView(initialLayout)
        .setNegativeButton("❌ 取消", null)
        .setCancelable(false)
        .create();
    loadingDialog.show();

    new Thread(new Runnable() {
        public void run() {
            final List<GroupInfo> allGroupList = getGroupList();
            final Map<String, Integer> groupMemberCounts = new HashMap<>();
            if (allGroupList != null) {
                for (GroupInfo groupInfo : allGroupList) {
                    String groupId = groupInfo.getRoomId();
                    if (groupId != null) {
                        groupMemberCounts.put(groupId, getGroupMemberCount(groupId));
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    loadingDialog.dismiss();
                    if (allGroupList == null || allGroupList.isEmpty()) {
                        toast("未获取到群聊列表");
                        return;
                    }
                    showActualGroupManagementDialog(allGroupList, groupMemberCounts);
                }
            });
        }
    }).start();
}

private void showActualGroupManagementDialog(final List<GroupInfo> allGroupList, final Map<String, Integer> groupMemberCounts) {
    try {
        final Set<String> selectedGroups = new HashSet<>(getStringSet(LISTEN_GROUPS_KEY, new HashSet<String>()));
        final List<String> currentFilteredRoomIds = new ArrayList<>();
        final List<String> currentFilteredNames = new ArrayList<>();

        LinearLayout dialogLayout = new LinearLayout(getTopActivity());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(16, 16, 16, 16);

        final EditText searchEditText = createStyledEditText("🔍 搜索群聊...", "");
        dialogLayout.addView(searchEditText);

        TextView infoText = new TextView(getTopActivity());
        infoText.setText("💡 勾选开启监听。长按群聊可单独设置群的进/退提示和内容。");
        infoText.setPadding(8, 0, 8, 16);
        dialogLayout.addView(infoText);

        final ListView groupListView = new ListView(getTopActivity());
        groupListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        dialogLayout.addView(groupListView);

        AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
        builder.setTitle("🌟 群组管理 🌟");
        builder.setView(dialogLayout);

        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentFilteredRoomIds.clear();
                currentFilteredNames.clear();
                List<String> tempGroupIds = new ArrayList<>();
                List<String> tempGroupNames = new ArrayList<>();
                for (GroupInfo groupInfo : allGroupList) {
                    String groupId = groupInfo.getRoomId();
                    if (groupId == null) continue;
                    String groupName = TextUtils.isEmpty(groupInfo.getName()) ? "未知群聊" : groupInfo.getName();
                    if (searchText.isEmpty() || groupName.toLowerCase().contains(searchText) || groupId.toLowerCase().contains(searchText)) {
                        tempGroupIds.add(groupId);
                        Integer memberCount = groupMemberCounts.get(groupId);
                        String displayName = "🏠 " + groupName + " (" + (memberCount != null ? memberCount : 0) + "人)\n🆔 " + groupId;
                        tempGroupNames.add(displayName);
                    }
                }
                currentFilteredRoomIds.addAll(tempGroupIds);
                currentFilteredNames.addAll(tempGroupNames);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredNames);
                groupListView.setAdapter(adapter);
                for (int i = 0; i < currentFilteredRoomIds.size(); i++) {
                    groupListView.setItemChecked(i, selectedGroups.contains(currentFilteredRoomIds.get(i)));
                }
            }
        };

        groupListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= currentFilteredRoomIds.size()) return;
                String selectedId = currentFilteredRoomIds.get(position);

                if (groupListView.isItemChecked(position)) {
                    selectedGroups.add(selectedId);
                } else {
                    selectedGroups.remove(selectedId);
                }
            }
        });

        groupListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= currentFilteredRoomIds.size()) return false;

                String selectedId = currentFilteredRoomIds.get(position);
                String fullItemText = currentFilteredNames.get(position);
                String displayGroupName = fullItemText.split("\n")[0].replace("🏠 ", "").replaceAll(" \\(.*\\)", "").trim();

                currentFocusedEdit = null; // 重置焦点状态
                showIndividualGroupPromptToggleDialog(selectedId, displayGroupName);
                return true;
            }
        });

        builder.setPositiveButton("✅ 保存监听列表", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                putStringSet(LISTEN_GROUPS_KEY, selectedGroups);
                toast("已保存设置，共监听" + selectedGroups.size() + "个群聊");
            }
        });
        builder.setNegativeButton("❌ 关闭", null);
        builder.setNeutralButton("✨ 全选", null); 

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                GradientDrawable dialogBg = new GradientDrawable();
                dialogBg.setCornerRadius(48);
                dialogBg.setColor(Color.parseColor("#FAFBF9"));
                dialog.getWindow().setBackgroundDrawable(dialogBg);

                styleDialogButtons(dialog);

                final Button selectAllButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (selectAllButton != null) {
                    boolean allSelected = !currentFilteredRoomIds.isEmpty() && selectedGroups.containsAll(currentFilteredRoomIds);
                    selectAllButton.setText(allSelected ? "✨ 取消全选" : "✨ 全选");

                    selectAllButton.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            boolean allSelectedCurrently = !currentFilteredRoomIds.isEmpty() && selectedGroups.containsAll(currentFilteredRoomIds);

                            if (allSelectedCurrently) {
                                selectedGroups.removeAll(currentFilteredRoomIds);
                                for (int i = 0; i < groupListView.getCount(); i++) {
                                    groupListView.setItemChecked(i, false);
                                }
                                toast("已取消全选");
                                selectAllButton.setText("✨ 全选"); 
                            } else {
                                selectedGroups.addAll(currentFilteredRoomIds);
                                for (int i = 0; i < groupListView.getCount(); i++) {
                                    groupListView.setItemChecked(i, true);
                                }
                                toast("已全选当前列表中的 " + currentFilteredRoomIds.size() + " 个群组");
                                selectAllButton.setText("✨ 取消全选"); 
                            }
                        }
                    });
                }
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            private Handler searchHandler = new Handler(Looper.getMainLooper());
            private Runnable searchRunnable;
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
            }
            public void afterTextChanged(Editable s) {
                searchRunnable = updateListRunnable;
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        dialog.show();
        updateListRunnable.run();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
    }
}

// === 更新的全面专属进退群设置弹窗 (包含全部媒体+精细延迟功能) ===
private void showIndividualGroupPromptToggleDialog(final String groupWxid, final String groupName) {
    try {
        // SharedPreferences#getStringSet 在部分环境返回不可修改集合，先拷贝避免保存时抛异常
        final Set<String> disabledJoinToggles = new HashSet<String>(getStringSet(JOIN_TOGGLE_KEY, new HashSet<String>()));
        final Set<String> disabledLeftToggles = new HashSet<String>(getStringSet(LEFT_TOGGLE_KEY, new HashSet<String>()));

        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout rootLayout = new LinearLayout(getTopActivity());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 24, 24, 24);
        rootLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(rootLayout);

        // --- 获取全局默认文本，用于专属设置页面的兜底填充 ---
        final String globalJoinText = getString(JOIN_TEXT_PROMPT_KEY, "[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者微信昵称：%userName%\n进群者群内昵称：%groupNickname%\n进群者ID：%userWxid%");
        final String globalLeftText = getString(LEFT_TEXT_PROMPT_KEY, "退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者微信昵称：%userName%\n退群者群内昵称：%groupNickname%\n退群者ID：%userWxid%");
        
        final String globalJoinCardTitle = getString(JOIN_CARD_TITLE_KEY, "🎊 欢迎：%userName%");
        final String globalJoinCardDesc = getString(JOIN_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");
        final String globalLeftCardTitle = getString(LEFT_CARD_TITLE_KEY, "💔 离群：%userName%");
        final String globalLeftCardDesc = getString(LEFT_CARD_DESC_KEY, "🆔：%userWxid%\n名片：%groupNickname%\n⏰：%time%");

        // --- 卡片1: 专属基础开关与模式 ---
        LinearLayout baseCard = createCardLayout();
        baseCard.addView(createSectionTitle("⚙️ 本群专属提示设置"));
        
        final Switch joinSwitch = new Switch(getTopActivity());
        joinSwitch.setText("开启本群进群提示  ");
        joinSwitch.setTextSize(16);
        joinSwitch.setPadding(8, 16, 8, 24);
        joinSwitch.setChecked(!disabledJoinToggles.contains(groupWxid));
        baseCard.addView(joinSwitch);
        
        final Switch leftSwitch = new Switch(getTopActivity());
        leftSwitch.setText("开启本群退群提示  ");
        leftSwitch.setTextSize(16);
        leftSwitch.setPadding(8, 16, 8, 24);
        leftSwitch.setChecked(!disabledLeftToggles.contains(groupWxid));
        baseCard.addView(leftSwitch);

        baseCard.addView(newTextView("本群专属文本模式:"));
        RadioGroup promptTypeGroup = new RadioGroup(getTopActivity());
        promptTypeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton inheritBtn = new RadioButton(getTopActivity()); inheritBtn.setText("继承全局");
        final RadioButton textBtn = new RadioButton(getTopActivity()); textBtn.setText("文本");
        final RadioButton bothBtn = new RadioButton(getTopActivity()); bothBtn.setText("文本+卡片");
        final RadioButton cardBtn = new RadioButton(getTopActivity()); cardBtn.setText("卡片");
        promptTypeGroup.addView(inheritBtn); promptTypeGroup.addView(textBtn); promptTypeGroup.addView(bothBtn); promptTypeGroup.addView(cardBtn);
        
        String currentCustomType = getString(PROMPT_TYPE_KEY + "_" + groupWxid, "global");
        if ("text".equals(currentCustomType)) textBtn.setChecked(true);
        else if ("card".equals(currentCustomType)) cardBtn.setChecked(true);
        else if ("both".equals(currentCustomType)) bothBtn.setChecked(true);
        else inheritBtn.setChecked(true);
        baseCard.addView(promptTypeGroup);
        
        // 当选择"文本+卡片"时显示顺序选择
        final LinearLayout bothOrderContainer = new LinearLayout(getTopActivity());
        bothOrderContainer.setOrientation(LinearLayout.VERTICAL);
        bothOrderContainer.setVisibility(View.GONE);
        
        TextView bothOrderLabel = new TextView(getTopActivity());
        bothOrderLabel.setText("本群文本+卡片发送顺序:");
        bothOrderLabel.setTextSize(14);
        bothOrderLabel.setTextColor(Color.parseColor("#666666"));
        bothOrderLabel.setPadding(0, 16, 0, 8);
        bothOrderContainer.addView(bothOrderLabel);
        
        RadioGroup bothOrderGroup = new RadioGroup(getTopActivity());
        bothOrderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton bothTextFirstBtn = new RadioButton(getTopActivity()); bothTextFirstBtn.setText("先文本后卡片");
        final RadioButton bothCardFirstBtn = new RadioButton(getTopActivity()); bothCardFirstBtn.setText("先卡片后文本");
        bothOrderGroup.addView(bothTextFirstBtn);
        bothOrderGroup.addView(bothCardFirstBtn);
        
        // 读取本群专属的顺序设置，如果没有则使用全局设置
        String customBothOrder = getString(PROMPT_BOTH_ORDER_KEY + "_" + groupWxid, "");
        if (TextUtils.isEmpty(customBothOrder)) {
            customBothOrder = getString(PROMPT_BOTH_ORDER_KEY, "text_first");
        }
        if ("card_first".equals(customBothOrder)) {
            bothCardFirstBtn.setChecked(true);
        } else {
            bothTextFirstBtn.setChecked(true);
        }
        bothOrderContainer.addView(bothOrderGroup);
        baseCard.addView(bothOrderContainer);
        
        // 监听提示语类型选择，控制顺序选择的显示
        promptTypeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == bothBtn.getId()) {
                    bothOrderContainer.setVisibility(View.VISIBLE);
                } else {
                    bothOrderContainer.setVisibility(View.GONE);
                }
            }
        });
        // 初始化显示状态
        if (bothBtn.isChecked()) {
            bothOrderContainer.setVisibility(View.VISIBLE);
        }
        
        rootLayout.addView(baseCard);

        // --- 卡片2: 专属文本提示语 ---
        LinearLayout textPromptCard = createCardLayout();
        textPromptCard.addView(createSectionTitle("📝 本群专属文本提示语"));
        textPromptCard.addView(createVariableBar()); 
        
        // 关键修复：直接读取群设置，如果没有则使用最新的全局设置进行预填
        final EditText joinTextEdit = createStyledEditText("自定义进群文本", getString(JOIN_TEXT_PROMPT_KEY + "_" + groupWxid, globalJoinText));
        joinTextEdit.setLines(4);
        joinTextEdit.setGravity(Gravity.TOP);
        textPromptCard.addView(joinTextEdit);

        final EditText leftTextEdit = createStyledEditText("自定义退群文本", getString(LEFT_TEXT_PROMPT_KEY + "_" + groupWxid, globalLeftText));
        leftTextEdit.setLines(4);
        leftTextEdit.setGravity(Gravity.TOP);
        textPromptCard.addView(leftTextEdit);

        Button fillRandomTextButton = new Button(getTopActivity());
        fillRandomTextButton.setText("💡 随机填充专属欢迎/退群语");
        styleFillButton(fillRandomTextButton);
        textPromptCard.addView(fillRandomTextButton);

        Button clearTextButton = new Button(getTopActivity());
        clearTextButton.setText("🔄 恢复全局");
        styleRestoreButton(clearTextButton);
        textPromptCard.addView(clearTextButton);
        
        rootLayout.addView(textPromptCard);

        // --- 卡片3: 专属卡片提示语 ---
        LinearLayout cardPromptCard = createCardLayout();
        cardPromptCard.addView(createSectionTitle("🖼️ 本群专属卡片提示语"));
        cardPromptCard.addView(createVariableBar());
        
        final EditText joinCardTitleEdit = createStyledEditText("自定义进群卡片标题", getString(JOIN_CARD_TITLE_KEY + "_" + groupWxid, globalJoinCardTitle));
        cardPromptCard.addView(joinCardTitleEdit);
        final EditText joinCardDescEdit = createStyledEditText("自定义进群卡片描述", getString(JOIN_CARD_DESC_KEY + "_" + groupWxid, globalJoinCardDesc));
        joinCardDescEdit.setLines(2); joinCardDescEdit.setGravity(Gravity.TOP);
        cardPromptCard.addView(joinCardDescEdit);

        final EditText leftCardTitleEdit = createStyledEditText("自定义退群卡片标题", getString(LEFT_CARD_TITLE_KEY + "_" + groupWxid, globalLeftCardTitle));
        cardPromptCard.addView(leftCardTitleEdit);
        final EditText leftCardDescEdit = createStyledEditText("自定义退群卡片描述", getString(LEFT_CARD_DESC_KEY + "_" + groupWxid, globalLeftCardDesc));
        leftCardDescEdit.setLines(2); leftCardDescEdit.setGravity(Gravity.TOP);
        cardPromptCard.addView(leftCardDescEdit);

        Button fillRandomCardButton = new Button(getTopActivity());
        fillRandomCardButton.setText("💡 随机填充专属卡片内容");
        styleFillButton(fillRandomCardButton);
        cardPromptCard.addView(fillRandomCardButton);

        Button clearCardButton = new Button(getTopActivity());
        clearCardButton.setText("🔄 恢复全局");
        styleRestoreButton(clearCardButton);
        cardPromptCard.addView(clearCardButton);

        rootLayout.addView(cardPromptCard);

        // --- 卡片4: 专属媒体设置 ---
        LinearLayout mediaCard = createCardLayout();
        mediaCard.addView(createSectionTitle("📂 本群专属媒体设置"));
        
        RadioGroup mediaModeGroup = new RadioGroup(getTopActivity());
        mediaModeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton inheritMediaBtn = new RadioButton(getTopActivity()); inheritMediaBtn.setText("继承全局");
        final RadioButton customMediaBtn = new RadioButton(getTopActivity()); customMediaBtn.setText("独立配置");
        final RadioButton noneMediaBtn = new RadioButton(getTopActivity()); noneMediaBtn.setText("不发媒体");
        mediaModeGroup.addView(inheritMediaBtn); mediaModeGroup.addView(customMediaBtn); mediaModeGroup.addView(noneMediaBtn);
        
        String currentMediaMode = getString("media_mode_" + groupWxid, "global");
        if ("custom".equals(currentMediaMode)) customMediaBtn.setChecked(true);
        else if ("none".equals(currentMediaMode)) noneMediaBtn.setChecked(true);
        else inheritMediaBtn.setChecked(true);
        mediaCard.addView(mediaModeGroup);
        
        // 只有选择“独立配置”时，才展示这些媒体选项
        final LinearLayout customMediaContainer = new LinearLayout(getTopActivity());
        customMediaContainer.setOrientation(LinearLayout.VERTICAL);
        customMediaContainer.setVisibility("custom".equals(currentMediaMode) ? View.VISIBLE : View.GONE);
        
        customMediaContainer.addView(newTextView("媒体发送顺序 (英文逗号隔开):"));
        final EditText customMediaSequenceEdit = createStyledEditText("如: image,voice,video...", getString(SEND_MEDIA_SEQUENCE_KEY + "_" + groupWxid, "image,voice,emoji,video,file"));
        customMediaContainer.addView(customMediaSequenceEdit);

        RadioGroup customMediaOrderGroup = new RadioGroup(getTopActivity());
        customMediaOrderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton orderNoneBtn = new RadioButton(getTopActivity()); orderNoneBtn.setText("不发媒体");
        final RadioButton orderBeforeBtn = new RadioButton(getTopActivity()); orderBeforeBtn.setText("先媒体后提示");
        final RadioButton orderAfterBtn = new RadioButton(getTopActivity()); orderAfterBtn.setText("先提示后媒体");
        customMediaOrderGroup.addView(orderNoneBtn); customMediaOrderGroup.addView(orderBeforeBtn); customMediaOrderGroup.addView(orderAfterBtn);
        
        String currentCustomOrder = getString(SEND_MEDIA_ORDER_KEY + "_" + groupWxid, "none");
        if ("before".equals(currentCustomOrder)) orderBeforeBtn.setChecked(true);
        else if ("after".equals(currentCustomOrder)) orderAfterBtn.setChecked(true);
        else orderNoneBtn.setChecked(true);
        customMediaContainer.addView(customMediaOrderGroup);
        
        customMediaContainer.addView(createSectionTitle("🗂️ 本群专属媒体文件选择"));

        Button btnSelJoinImages = new Button(getTopActivity()); btnSelJoinImages.setText("进群图片");
        TextView tvJoinImgCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelJoinImages, tvJoinImgCnt, JOIN_IMAGE_PATHS_KEY + "_" + groupWxid, ".png", "专属进群图片");

        Button btnSelLeftImages = new Button(getTopActivity()); btnSelLeftImages.setText("退群图片");
        TextView tvLeftImgCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelLeftImages, tvLeftImgCnt, LEFT_IMAGE_PATHS_KEY + "_" + groupWxid, ".png", "专属退群图片");

        Button btnSelJoinVoices = new Button(getTopActivity()); btnSelJoinVoices.setText("进群语音");
        TextView tvJoinVoiceCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelJoinVoices, tvJoinVoiceCnt, JOIN_VOICE_PATHS_KEY + "_" + groupWxid, "", "专属进群语音");

        Button btnSelLeftVoices = new Button(getTopActivity()); btnSelLeftVoices.setText("退群语音");
        TextView tvLeftVoiceCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelLeftVoices, tvLeftVoiceCnt, LEFT_VOICE_PATHS_KEY + "_" + groupWxid, "", "专属退群语音");

        Button btnSelJoinEmojis = new Button(getTopActivity()); btnSelJoinEmojis.setText("进群表情");
        TextView tvJoinEmojiCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelJoinEmojis, tvJoinEmojiCnt, JOIN_EMOJI_PATHS_KEY + "_" + groupWxid, ".png", "专属进群表情");

        Button btnSelLeftEmojis = new Button(getTopActivity()); btnSelLeftEmojis.setText("退群表情");
        TextView tvLeftEmojiCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelLeftEmojis, tvLeftEmojiCnt, LEFT_EMOJI_PATHS_KEY + "_" + groupWxid, ".png", "专属退群表情");

        Button btnSelJoinVideos = new Button(getTopActivity()); btnSelJoinVideos.setText("进群视频");
        TextView tvJoinVideoCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelJoinVideos, tvJoinVideoCnt, JOIN_VIDEO_PATHS_KEY + "_" + groupWxid, ".mp4", "专属进群视频");

        Button btnSelLeftVideos = new Button(getTopActivity()); btnSelLeftVideos.setText("退群视频");
        TextView tvLeftVideoCnt = new TextView(getTopActivity());
        // ！！！这里修复了拼写错误 tvLeftVideosCount -> tvLeftVideoCnt ！！！
        setupMediaButton(btnSelLeftVideos, tvLeftVideoCnt, LEFT_VIDEO_PATHS_KEY + "_" + groupWxid, ".mp4", "专属退群视频");

        Button btnSelJoinFiles = new Button(getTopActivity()); btnSelJoinFiles.setText("进群文件");
        TextView tvJoinFileCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelJoinFiles, tvJoinFileCnt, JOIN_FILE_PATHS_KEY + "_" + groupWxid, "", "专属进群文件");

        Button btnSelLeftFiles = new Button(getTopActivity()); btnSelLeftFiles.setText("退群文件");
        TextView tvLeftFileCnt = new TextView(getTopActivity());
        setupMediaButton(btnSelLeftFiles, tvLeftFileCnt, LEFT_FILE_PATHS_KEY + "_" + groupWxid, "", "专属退群文件");

        customMediaContainer.addView(horizontalRow(btnSelJoinImages, tvJoinImgCnt));
        customMediaContainer.addView(horizontalRow(btnSelLeftImages, tvLeftImgCnt));
        customMediaContainer.addView(horizontalRow(btnSelJoinVoices, tvJoinVoiceCnt));
        customMediaContainer.addView(horizontalRow(btnSelLeftVoices, tvLeftVoiceCnt));
        customMediaContainer.addView(horizontalRow(btnSelJoinEmojis, tvJoinEmojiCnt));
        customMediaContainer.addView(horizontalRow(btnSelLeftEmojis, tvLeftEmojiCnt));
        customMediaContainer.addView(horizontalRow(btnSelJoinVideos, tvJoinVideoCnt));
        customMediaContainer.addView(horizontalRow(btnSelLeftVideos, tvLeftVideoCnt));
        customMediaContainer.addView(horizontalRow(btnSelJoinFiles, tvJoinFileCnt));
        customMediaContainer.addView(horizontalRow(btnSelLeftFiles, tvLeftFileCnt));

        mediaCard.addView(customMediaContainer);

        mediaModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == customMediaBtn.getId()) {
                    customMediaContainer.setVisibility(View.VISIBLE);
                } else {
                    customMediaContainer.setVisibility(View.GONE);
                }
            }
        });

        rootLayout.addView(mediaCard);

        // --- 卡片5: 专属精细延迟设置 ---
        LinearLayout delayCard = createCardLayout();
        delayCard.addView(createSectionTitle("⏱️ 本群专属延迟设置"));
        
        RadioGroup delayModeGroup = new RadioGroup(getTopActivity());
        delayModeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton inheritDelayBtn = new RadioButton(getTopActivity()); inheritDelayBtn.setText("继承全局");
        final RadioButton customDelayBtn = new RadioButton(getTopActivity()); customDelayBtn.setText("独立配置");
        delayModeGroup.addView(inheritDelayBtn); delayModeGroup.addView(customDelayBtn);
        
        String currentDelayMode = getString("delay_mode_" + groupWxid, "global");
        if ("custom".equals(currentDelayMode)) customDelayBtn.setChecked(true);
        else inheritDelayBtn.setChecked(true);
        delayCard.addView(delayModeGroup);

        final LinearLayout customDelayContainer = new LinearLayout(getTopActivity());
        customDelayContainer.setOrientation(LinearLayout.VERTICAL);
        customDelayContainer.setVisibility("custom".equals(currentDelayMode) ? View.VISIBLE : View.GONE);

        customDelayContainer.addView(newTextView("提示语延迟 (毫秒):"));
        final EditText customPromptDelayEdit = createStyledEditText("0", String.valueOf(getInt(PROMPT_DELAY_KEY + "_" + groupWxid, getInt(PROMPT_DELAY_KEY, 0))));
        customDelayContainer.addView(customPromptDelayEdit);
        
        customDelayContainer.addView(newTextView("图片延迟 (毫秒):"));
        final EditText customImageDelayEdit = createStyledEditText("100", String.valueOf(getInt(IMAGE_DELAY_KEY + "_" + groupWxid, getInt(IMAGE_DELAY_KEY, 100))));
        customDelayContainer.addView(customImageDelayEdit);

        customDelayContainer.addView(newTextView("语音延迟 (毫秒):"));
        final EditText customVoiceDelayEdit = createStyledEditText("100", String.valueOf(getInt(VOICE_DELAY_KEY + "_" + groupWxid, getInt(VOICE_DELAY_KEY, 100))));
        customDelayContainer.addView(customVoiceDelayEdit);

        customDelayContainer.addView(newTextView("表情延迟 (毫秒):"));
        final EditText customEmojiDelayEdit = createStyledEditText("100", String.valueOf(getInt(EMOJI_DELAY_KEY + "_" + groupWxid, getInt(EMOJI_DELAY_KEY, 100))));
        customDelayContainer.addView(customEmojiDelayEdit);

        customDelayContainer.addView(newTextView("视频延迟 (毫秒):"));
        final EditText customVideoDelayEdit = createStyledEditText("100", String.valueOf(getInt(VIDEO_DELAY_KEY + "_" + groupWxid, getInt(VIDEO_DELAY_KEY, 100))));
        customDelayContainer.addView(customVideoDelayEdit);

        customDelayContainer.addView(newTextView("文件延迟 (毫秒):"));
        final EditText customFileDelayEdit = createStyledEditText("100", String.valueOf(getInt(FILE_DELAY_KEY + "_" + groupWxid, getInt(FILE_DELAY_KEY, 100))));
        customDelayContainer.addView(customFileDelayEdit);

        delayCard.addView(customDelayContainer);

        delayModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == customDelayBtn.getId()) {
                    customDelayContainer.setVisibility(View.VISIBLE);
                } else {
                    customDelayContainer.setVisibility(View.GONE);
                }
            }
        });

        rootLayout.addView(delayCard);

        // ---- 按钮逻辑 ----
        fillRandomTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String randomJoin = RANDOM_JOIN_TEXTS_ARRAY[new Random().nextInt(RANDOM_JOIN_TEXTS_ARRAY.length)];
                String randomLeft = RANDOM_LEFT_TEXTS_ARRAY[new Random().nextInt(RANDOM_LEFT_TEXTS_ARRAY.length)];
                joinTextEdit.setText(randomJoin);
                leftTextEdit.setText(randomLeft);
                toast("已随机填充本群专属欢迎/退群语");
            }
        });
        clearTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinTextEdit.setText(globalJoinText);
                leftTextEdit.setText(globalLeftText);
                toast("文本已重置为全局默认");
            }
        });

        fillRandomCardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String title = RANDOM_JOIN_CARD_TITLES_ARRAY[new Random().nextInt(RANDOM_JOIN_CARD_TITLES_ARRAY.length)];
                String desc = RANDOM_JOIN_CARD_DESCS_ARRAY[new Random().nextInt(RANDOM_JOIN_CARD_DESCS_ARRAY.length)];
                joinCardTitleEdit.setText(title);
                joinCardDescEdit.setText(desc);

                String ltitle = RANDOM_LEFT_CARD_TITLES_ARRAY[new Random().nextInt(RANDOM_LEFT_CARD_TITLES_ARRAY.length)];
                String ldesc = RANDOM_LEFT_CARD_DESCS_ARRAY[new Random().nextInt(RANDOM_LEFT_CARD_DESCS_ARRAY.length)];
                leftCardTitleEdit.setText(ltitle);
                leftCardDescEdit.setText(ldesc);
                toast("已随机填充本群专属卡片内容");
            }
        });
        clearCardButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinCardTitleEdit.setText(globalJoinCardTitle);
                joinCardDescEdit.setText(globalJoinCardDesc);
                leftCardTitleEdit.setText(globalLeftCardTitle);
                leftCardDescEdit.setText(globalLeftCardDesc);
                toast("卡片内容已重置为全局默认");
            }
        });


        AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
            .setTitle("🔧 " + groupName)
            .setView(scrollView)
            .setPositiveButton("✅ 保存设置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    try {
                    
                    // --- 先做前置检查，防崩溃 ---
                    int pDelay = 0, iDelay = 0, voDelay = 0, eDelay = 0, viDelay = 0, fDelay = 0;
                    if (customDelayBtn.isChecked()) {
                        try {
                            pDelay = Integer.parseInt(customPromptDelayEdit.getText().toString());
                            iDelay = Integer.parseInt(customImageDelayEdit.getText().toString());
                            voDelay = Integer.parseInt(customVoiceDelayEdit.getText().toString());
                            eDelay = Integer.parseInt(customEmojiDelayEdit.getText().toString());
                            viDelay = Integer.parseInt(customVideoDelayEdit.getText().toString());
                            fDelay = Integer.parseInt(customFileDelayEdit.getText().toString());
                        } catch (NumberFormatException e) {
                            toast("保存失败：专属延迟必须填写有效的纯数字!");
                            return; 
                        }
                    }

                    // 1. 保存开关状态
                    if (joinSwitch.isChecked()) {
                        disabledJoinToggles.remove(groupWxid);
                    } else {
                        disabledJoinToggles.add(groupWxid);
                    }
                    if (leftSwitch.isChecked()) {
                        disabledLeftToggles.remove(groupWxid);
                    } else {
                        disabledLeftToggles.add(groupWxid);
                    }
                    putStringSet(JOIN_TOGGLE_KEY, disabledJoinToggles);
                    putStringSet(LEFT_TOGGLE_KEY, disabledLeftToggles);

                    // 2. 保存提示语类型
                    String selectedType = "global";
                    if (textBtn.isChecked()) selectedType = "text";
                    else if (bothBtn.isChecked()) selectedType = "both";
                    else if (cardBtn.isChecked()) selectedType = "card";
                    putString(PROMPT_TYPE_KEY + "_" + groupWxid, selectedType);
                    
                    // 3. 保存文本+卡片顺序
                    String bothOrder = bothTextFirstBtn.isChecked() ? "text_first" : "card_first";
                    putString(PROMPT_BOTH_ORDER_KEY + "_" + groupWxid, bothOrder);

                    // 3. 保存专属内容
                    putString(JOIN_TEXT_PROMPT_KEY + "_" + groupWxid, joinTextEdit.getText().toString());
                    putString(JOIN_CARD_TITLE_KEY + "_" + groupWxid, joinCardTitleEdit.getText().toString());
                    putString(JOIN_CARD_DESC_KEY + "_" + groupWxid, joinCardDescEdit.getText().toString());

                    putString(LEFT_TEXT_PROMPT_KEY + "_" + groupWxid, leftTextEdit.getText().toString());
                    putString(LEFT_CARD_TITLE_KEY + "_" + groupWxid, leftCardTitleEdit.getText().toString());
                    putString(LEFT_CARD_DESC_KEY + "_" + groupWxid, leftCardDescEdit.getText().toString());

                    // 4. 保存媒体配置
                    String mMode = "global";
                    if (customMediaBtn.isChecked()) mMode = "custom";
                    else if (noneMediaBtn.isChecked()) mMode = "none";
                    putString("media_mode_" + groupWxid, mMode);
                    
                    if ("custom".equals(mMode)) {
                        putString(SEND_MEDIA_SEQUENCE_KEY + "_" + groupWxid, customMediaSequenceEdit.getText().toString());
                        String oMode = "none";
                        if (orderBeforeBtn.isChecked()) oMode = "before";
                        else if (orderAfterBtn.isChecked()) oMode = "after";
                        putString(SEND_MEDIA_ORDER_KEY + "_" + groupWxid, oMode);
                    }

                    // 5. 保存延迟配置
                    String dMode = "global";
                    if (customDelayBtn.isChecked()) dMode = "custom";
                    putString("delay_mode_" + groupWxid, dMode);

                    if ("custom".equals(dMode)) {
                        putInt(PROMPT_DELAY_KEY + "_" + groupWxid, pDelay);
                        putInt(IMAGE_DELAY_KEY + "_" + groupWxid, iDelay);
                        putInt(VOICE_DELAY_KEY + "_" + groupWxid, voDelay);
                        putInt(EMOJI_DELAY_KEY + "_" + groupWxid, eDelay);
                        putInt(VIDEO_DELAY_KEY + "_" + groupWxid, viDelay);
                        putInt(FILE_DELAY_KEY + "_" + groupWxid, fDelay);
                    }

                    toast("已保存 " + groupName + " 的专属设置");
                    } catch (Exception e) {
                        toast("保存失败: " + e.getMessage());
                    }
                }
            })
            .setNegativeButton("❌ 取消", null)
            .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                GradientDrawable dialogBg = new GradientDrawable();
                dialogBg.setCornerRadius(48);
                dialogBg.setColor(Color.parseColor("#FAFBF9"));
                dialog.getWindow().setBackgroundDrawable(dialogBg);
                styleDialogButtons(dialog);
            }
        });

        dialog.show();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
    }
}
