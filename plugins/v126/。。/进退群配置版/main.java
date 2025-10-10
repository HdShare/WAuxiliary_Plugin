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

// === 存储 Key 定义 ===
private final String LISTEN_GROUPS_KEY = "listen_groups";
private final String DELAY_KEY = "send_delay";
private final int DEFAULT_DELAY = 10;
private final String JOIN_TOGGLE_KEY = "join_toggle";
private final String LEFT_TOGGLE_KEY = "left_toggle";
private final String PROMPT_TYPE_KEY = "prompt_type";
private final String JOIN_TEXT_PROMPT_KEY = "join_text_prompt";
private final String LEFT_TEXT_PROMPT_KEY = "left_text_prompt";
private final String JOIN_CARD_TITLE_KEY = "join_card_title";
private final String LEFT_CARD_TITLE_KEY = "left_card_title";
private final String JOIN_CARD_DESC_KEY = "join_card_desc";
private final String LEFT_CARD_DESC_KEY = "left_card_desc";

// 新增媒体发送设置
private final String JOIN_IMAGE_PATHS_KEY = "join_image_paths";
private final String LEFT_IMAGE_PATHS_KEY = "left_image_paths";
private final String JOIN_EMOJI_PATHS_KEY = "join_emoji_paths";
private final String LEFT_EMOJI_PATHS_KEY = "left_emoji_paths";
private final String JOIN_VOICE_PATHS_KEY = "join_voice_paths";
private final String LEFT_VOICE_PATHS_KEY = "left_voice_paths";
private final String JOIN_VIDEO_PATHS_KEY = "join_video_paths"; // 新增：视频路径
private final String LEFT_VIDEO_PATHS_KEY = "left_video_paths"; // 新增：视频路径
private final String JOIN_FILE_PATHS_KEY = "join_file_paths"; // 新增：分享文件路径
private final String LEFT_FILE_PATHS_KEY = "left_file_paths"; // 新增：分享文件路径
private final String SEND_MEDIA_ORDER_KEY = "send_media_order"; // "none", "before", "after"
private final String SEND_MEDIA_SEQUENCE_KEY = "send_media_sequence"; // e.g., "image,voice,emoji,video,file"

// 新增：精细化延迟设置 (单位: 毫秒)
private final String PROMPT_DELAY_KEY = "prompt_delay_ms";
private final String IMAGE_DELAY_KEY = "image_delay_ms";
private final String VOICE_DELAY_KEY = "voice_delay_ms";
private final String EMOJI_DELAY_KEY = "emoji_delay_ms";
private final String VIDEO_DELAY_KEY = "video_delay_ms"; // 新增：视频延迟
private final String FILE_DELAY_KEY = "file_delay_ms"; // 新增：文件延迟

// [新增] 丰富的随机提示语库（每次填充时随机选一条）
private final String[] RANDOM_JOIN_TEXTS_ARRAY = new String[] {
    "[AtWx=%userWxid%] 欢迎 %userName% 加入 %groupName%～ 🎉",
    "热烈欢迎新朋友 %userName%，大家请多关照！",
    "又来了一位大佬，欢迎 %userName%！记得看群公告哦~",
    "捕捉到一只小萌新 %userName%，来打个招呼吧～",
    "欢迎 %userName%，愿在 %groupName% 玩得开心～"
};
private final String[] RANDOM_LEFT_TEXTS_ARRAY = new String[] {
    "有缘再会，祝 %userName% 前程似锦。",
    "悄悄地他走了，正如他悄悄地来。再见，%userName%。",
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
    "群名称：%groupName% \n进群时间：%time%",
    "快来和大家一起玩耍吧！\nID: %userWxid%"
};
private final String[] RANDOM_LEFT_CARD_TITLES_ARRAY = new String[] {
    "成员离群通知",
    "%userName% 已离开",
    "祝你前程似锦"
};
private final String[] RANDOM_LEFT_CARD_DESCS_ARRAY = new String[] {
    "我们有缘再见",
    "群名称：%groupName% \n离群时间：%time%",
    "相逢是缘，祝君安好。"
};

// === 按钮点击事件处理 ===
public boolean onClickSendBtn(String text) {
    // 将所有设置入口合并为一个命令
    if ("进退群设置".equals(text)) {
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
            final String promptType = getString(PROMPT_TYPE_KEY, "text");
            final String mediaOrder = getString(SEND_MEDIA_ORDER_KEY, "none");
            final String mediaSequence = getString(SEND_MEDIA_SEQUENCE_KEY, "image,voice,emoji,video,file");

            // 新增：获取显示名称（优先群备注，没有则用昵称）
            final String displayName = getDisplayName(userWxid, groupWxid, userName);

            // 2. 获取精细化的延迟设置
            final long promptDelay = getInt(PROMPT_DELAY_KEY, 0);
            final long imageDelay = getInt(IMAGE_DELAY_KEY, 100);
            final long voiceDelay = getInt(VOICE_DELAY_KEY, 100);
            final long emojiDelay = getInt(EMOJI_DELAY_KEY, 100);
            final long videoDelay = getInt(VIDEO_DELAY_KEY, 100);
            final long fileDelay = getInt(FILE_DELAY_KEY, 100);

            // 3. 创建所有可能的发送任务
            // 提示语任务
            Runnable promptAction = new Runnable() {
                public void run() {
                    if ("card".equals(promptType)) {
                        handleCardSending(type, groupWxid, userWxid, displayName, userName, groupWxid, groupName, currentTime);
                    } else {
                        handleTextSending(type, groupWxid, userWxid, displayName, userName, groupWxid, groupName, currentTime);
                    }
                }
            };
            SendTask promptTask = new SendTask(promptAction, promptDelay);

            // 媒体任务
            Map<String, SendTask> mediaTasks = new HashMap<>();

            // 图片任务
            final String imagePaths = getString("join".equals(type) ? JOIN_IMAGE_PATHS_KEY : LEFT_IMAGE_PATHS_KEY, "");
            if (!TextUtils.isEmpty(imagePaths)) {
                Runnable imageAction = new Runnable() {
                    public void run() {
                        for (String p : imagePaths.split(",")) {
                            if (!TextUtils.isEmpty(p.trim())) {
                                try {
                                    sendImage(groupWxid, p.trim());
                                } catch (Exception e) {
                                    toast("发送图片失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                };
                mediaTasks.put("image", new SendTask(imageAction, imageDelay));
            }

            // 语音任务
            final String voicePaths = getString("join".equals(type) ? JOIN_VOICE_PATHS_KEY : LEFT_VOICE_PATHS_KEY, "");
            if (!TextUtils.isEmpty(voicePaths)) {
                Runnable voiceAction = new Runnable() {
                    public void run() {
                        for (String p : voicePaths.split(",")) {
                            if (!TextUtils.isEmpty(p.trim())) {
                                try {
                                    sendVoice(groupWxid, p.trim());
                                } catch (Exception e) {
                                    toast("发送语音失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                };
                mediaTasks.put("voice", new SendTask(voiceAction, voiceDelay));
            }

            // 表情任务
            final String emojiPaths = getString("join".equals(type) ? JOIN_EMOJI_PATHS_KEY : LEFT_EMOJI_PATHS_KEY, "");
            if (!TextUtils.isEmpty(emojiPaths)) {
                Runnable emojiAction = new Runnable() {
                    public void run() {
                        for (String p : emojiPaths.split(",")) {
                            if (!TextUtils.isEmpty(p.trim())) {
                                try {
                                    sendEmoji(groupWxid, p.trim());
                                } catch (Exception e) {
                                    toast("发送表情失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                };
                mediaTasks.put("emoji", new SendTask(emojiAction, emojiDelay));
            }

            // 视频任务
            final String videoPaths = getString("join".equals(type) ? JOIN_VIDEO_PATHS_KEY : LEFT_VIDEO_PATHS_KEY, "");
            if (!TextUtils.isEmpty(videoPaths)) {
                Runnable videoAction = new Runnable() {
                    public void run() {
                        for (String p : videoPaths.split(",")) {
                            if (!TextUtils.isEmpty(p.trim())) {
                                try {
                                    sendVideo(groupWxid, p.trim());
                                } catch (Exception e) {
                                    toast("发送视频失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                };
                mediaTasks.put("video", new SendTask(videoAction, videoDelay));
            }

            // 分享文件任务
            final String filePaths = getString("join".equals(type) ? JOIN_FILE_PATHS_KEY : LEFT_FILE_PATHS_KEY, "");
            if (!TextUtils.isEmpty(filePaths)) {
                Runnable fileAction = new Runnable() {
                    public void run() {
                        for (String p : filePaths.split(",")) {
                            if (!TextUtils.isEmpty(p.trim())) {
                                try {
                                    // 从文件路径中提取文件名（包括后缀）
                                    String fileName = new java.io.File(p.trim()).getName();
                                    shareFile(groupWxid, fileName, p.trim(), "");
                                } catch (Exception e) {
                                    toast("分享文件失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                };
                mediaTasks.put("file", new SendTask(fileAction, fileDelay));
            }

            // 4. 根据 mediaOrder 和 mediaSequence 构建最终的发送任务链
            List<SendTask> finalTaskChain = new ArrayList<>();

            List<SendTask> orderedMediaTasks = new ArrayList<>();
            if (!"none".equals(mediaOrder)) {
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

// 新增：任务链执行器
private void executeSendChain(final List<SendTask> tasks, final int index) {
    if (index >= tasks.size()) {
        return; // 任务链执行完毕
    }
    final SendTask currentTask = tasks.get(index);
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            if (currentTask.getAction() != null) {
                currentTask.getAction().run();
            }
            // 递归调用，执行链中的下一个任务
            executeSendChain(tasks, index + 1);
        }
    }, currentTask.getDelay());
}

private void handleTextSending(String type, String groupWxid, String userWxid, String displayName, String userName, String groupWxidParam, String groupName, String currentTime) {
    String textToSend = "";
    String prompts;

    if ("join".equals(type)) {
        prompts = getString(JOIN_TEXT_PROMPT_KEY, "[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者昵称：%userName%\n进群者ID：%userWxid%");
    } else { // left
        prompts = getString(LEFT_TEXT_PROMPT_KEY, "退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者昵称：%userName%\n退群者ID：%userWxid%");
    }

    if (!TextUtils.isEmpty(prompts)) {
        // 新行为：如果存的是多选兼容格式（包含 ||），保留随机选择逻辑（兼容旧配置）
        if (prompts.contains("||")) {
            String[] options = prompts.split("\\|\\|");
            textToSend = options[new Random().nextInt(options.length)].trim();
        } else {
            // 否则直接按当前存储的内容（单条），这与“随机填充”操作配合使用：填充时已经是随机的一条
            textToSend = prompts.trim();
        }

        if ("join".equals(type)) {
            textToSend = textToSend.replace("%userWxid%", userWxid).replace("%userName%", displayName);
        } else {
            // 对于退群，同时包含昵称和群内备注昵称（格式：昵称 (备注)）
            String nickname = userName;
            String groupNickname = getFriendName(userWxid, groupWxid);
            String fullName = nickname;
            if (!TextUtils.isEmpty(groupNickname) && !"未设置".equals(groupNickname)) {
                fullName = groupNickname + " (" + nickname + ")";
            }
            textToSend = textToSend.replace("%userName%", fullName).replace("%userWxid%", userWxid);
        }

        textToSend = textToSend.replace("%groupName%", groupName).replace("%time%", currentTime);
        sendText(groupWxid, textToSend);
    }
}


private void handleCardSending(String type, String groupWxid, String userWxid, String displayName, String userName, String groupWxidParam, String groupName, String currentTime) {
    String titlePrompts, descPrompts;

    if ("join".equals(type)) {
        titlePrompts = getString(JOIN_CARD_TITLE_KEY, "🎊 欢迎：%userName%");
        descPrompts = getString(JOIN_CARD_DESC_KEY, "🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
    } else { // left
        titlePrompts = getString(LEFT_CARD_TITLE_KEY, "💔 离群：%userName%");
        descPrompts = getString(LEFT_CARD_DESC_KEY, "🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
    }

    // 新行为：支持兼容旧格式 || 随机，也支持单条（UI 随机填充会存单条）
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

    String userNameForReplace;
    if ("left".equals(type)) {
        // 对于退群，同时包含昵称和群内备注昵称（格式：昵称 (备注)）
        String nickname = userName;
        String groupNickname = getFriendName(userWxid, groupWxid);
        userNameForReplace = nickname;
        if (!TextUtils.isEmpty(groupNickname) && !"未设置".equals(groupNickname)) {
            userNameForReplace = groupNickname + " (" + nickname + ")";
        }
    } else {
        userNameForReplace = displayName;
    }

    String title = titleTemplate.replace("%userName%", userNameForReplace).replace("%userWxid%", userWxid).replace("%groupName%", groupName).replace("%time%", currentTime);
    String description = descTemplate.replace("%userName%", userNameForReplace).replace("%userWxid%", userWxid).replace("%groupName%", groupName).replace("%time%", currentTime);

    String avatarUrl = getAvatarUrl(userWxid, false); // 小头像
    String bigAvatarUrl = getAvatarUrl(userWxid, true); // 大头像作为卡片内容链接

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

// === 文件/文件夹浏览与多选（来自音频转换2，扩展为多选） ===

final String DEFAULT_LAST_FOLDER_SP = "last_folder_for_media";
final String ROOT_FOLER = "/storage/emulated/0";

// 回调接口
interface MediaSelectionCallback {
    void onSelected(ArrayList<String> selectedFiles);
}

// [V3] 递进浏览文件夹, 增加 currentSelection 参数用于传递已选中的文件
void browseFolderForSelection(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback) {
    putString(DEFAULT_LAST_FOLDER_SP, startFolder.getAbsolutePath());
    ArrayList<String> names = new ArrayList<>();
    final ArrayList<Object> items = new ArrayList<>();

    // 上一级（根目录除外）
    if (!startFolder.getAbsolutePath().equals(ROOT_FOLER)) {
        names.add("⬆ 上一级");
        items.add(startFolder.getParentFile());
    }

    // 当前目录下的子文件夹
    File[] subs = startFolder.listFiles();
    if (subs != null) {
        for (File f : subs) {
            if (f.isDirectory()) {
                names.add("📁 " + f.getName());
                items.add(f);
            }
        }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());
    final ListView list = new ListView(getTopActivity());
    list.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, names));
    builder.setView(list);

    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            Object selected = items.get(pos);
            if (selected instanceof File) {
                File sel = (File) selected;
                if (sel.isDirectory()) {
                    // 进入该目录继续浏览
                    browseFolderForSelection(sel, wantedExtFilter, currentSelection, callback);
                }
            }
        }
    });

    builder.setPositiveButton("在此目录选择文件", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            scanFilesMulti(startFolder, wantedExtFilter, currentSelection, callback);
        }
    });

    builder.setNegativeButton("取消", null);
    builder.create().show();
}

// [V3] 扫描文件并支持多选, 增加 currentSelection 参数用于恢复勾选状态
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

    // [V3] 解析当前已选中的文件路径
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

    // [V3] 恢复勾选状态
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

// === 新的统一设置界面（集成多选媒体选择） ===
private void showUnifiedSettingsDialog() {
    try {
        // --- 根布局和滚动视图 ---
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
        groupManagementButton.setText("管理监听群组和进退群开关");
        styleUtilityButton(groupManagementButton);
        managementCard.addView(groupManagementButton);
        rootLayout.addView(managementCard);

        // --- 卡片2: 核心设置 ---
        LinearLayout coreSettingsCard = createCardLayout();
        coreSettingsCard.addView(createSectionTitle("🚀 核心设置"));
        // 整体延迟
        coreSettingsCard.addView(newTextView("触发后整体延迟（秒）:"));
        final EditText delayEditText = createStyledEditText("0-600秒", String.valueOf(getInt(DELAY_KEY, DEFAULT_DELAY)));
        delayEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        coreSettingsCard.addView(delayEditText);
        // 提示语类型
        coreSettingsCard.addView(newTextView("选择提示语类型:"));
        RadioGroup promptTypeGroup = new RadioGroup(getTopActivity());
        promptTypeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton textTypeButton = new RadioButton(getTopActivity()); textTypeButton.setText("文本");
        final RadioButton cardTypeButton = new RadioButton(getTopActivity()); cardTypeButton.setText("卡片");
        promptTypeGroup.addView(textTypeButton);
        promptTypeGroup.addView(cardTypeButton);
        if ("card".equals(getString(PROMPT_TYPE_KEY, "text"))) {
            cardTypeButton.setChecked(true);
        } else {
            textTypeButton.setChecked(true);
        }
        coreSettingsCard.addView(promptTypeGroup);
        rootLayout.addView(coreSettingsCard);

        // --- 卡片3: 文本提示语设置 ---
        LinearLayout textPromptCard = createCardLayout();
        textPromptCard.addView(createSectionTitle("📝 文本提示语设置"));
        final EditText joinPromptEditText = createStyledEditText("设置进群欢迎语", getString(JOIN_TEXT_PROMPT_KEY, "[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者昵称：%userName%\n进群者ID：%userWxid%"));
        joinPromptEditText.setLines(5);
        joinPromptEditText.setGravity(Gravity.TOP);
        textPromptCard.addView(joinPromptEditText);
        final EditText leftPromptEditText = createStyledEditText("设置退群通知", getString(LEFT_TEXT_PROMPT_KEY, "退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者昵称：%userName%\n退群者ID：%userWxid%"));
        leftPromptEditText.setLines(5);
        leftPromptEditText.setGravity(Gravity.TOP);
        textPromptCard.addView(leftPromptEditText);

        // [新增] 一键随机填充（直接填入随机单条）
        Button fillRandomTextButton = new Button(getTopActivity());
        fillRandomTextButton.setText("💡 随机填充一条欢迎/退群语");
        styleFillButton(fillRandomTextButton);
        textPromptCard.addView(fillRandomTextButton);

        // [新增] 恢复默认单独按钮（恢复文本区域）
        Button restoreTextDefaultsButton = new Button(getTopActivity());
        restoreTextDefaultsButton.setText("🔄 恢复文本默认");
        styleRestoreButton(restoreTextDefaultsButton);
        textPromptCard.addView(restoreTextDefaultsButton);

        TextView textHelp = new TextView(getTopActivity());
        textHelp.setText("可用变量: %userName%, %userWxid%, %groupName%, %time%, [AtWx=%userWxid%]\n💡 点击随机填充将写入一条随机提示语（你也可以手动编辑）。");
        textHelp.setTextSize(12); textHelp.setTextColor(Color.parseColor("#666666"));
        textPromptCard.addView(textHelp);
        rootLayout.addView(textPromptCard);

        // --- 卡片4: 卡片提示语设置 ---
        LinearLayout cardPromptCard = createCardLayout();
        cardPromptCard.addView(createSectionTitle("🖼️ 卡片提示语设置"));
        final EditText joinTitleEditText = createStyledEditText("进群卡片标题", getString(JOIN_CARD_TITLE_KEY, "🎊 欢迎：%userName%"));
        final EditText joinDescEditText = createStyledEditText("进群卡片描述", getString(JOIN_CARD_DESC_KEY, "🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%"));
        joinDescEditText.setLines(3); joinDescEditText.setGravity(Gravity.TOP);
        cardPromptCard.addView(joinTitleEditText);
        cardPromptCard.addView(joinDescEditText);
        final EditText leftTitleEditText = createStyledEditText("退群卡片标题", getString(LEFT_CARD_TITLE_KEY, "💔 离群：%userName%"));
        final EditText leftDescEditText = createStyledEditText("退群卡片描述", getString(LEFT_CARD_DESC_KEY, "🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%"));
        leftDescEditText.setLines(3); leftDescEditText.setGravity(Gravity.TOP);
        cardPromptCard.addView(leftTitleEditText);
        cardPromptCard.addView(leftDescEditText);

        // [新增] 随机与恢复
        Button fillRandomCardButton = new Button(getTopActivity());
        fillRandomCardButton.setText("💡 随机填充卡片内容");
        styleFillButton(fillRandomCardButton);
        cardPromptCard.addView(fillRandomCardButton);

        Button restoreCardDefaultsButton = new Button(getTopActivity());
        restoreCardDefaultsButton.setText("🔄 恢复卡片默认");
        styleRestoreButton(restoreCardDefaultsButton);
        cardPromptCard.addView(restoreCardDefaultsButton);

        TextView cardHelp = new TextView(getTopActivity());
        cardHelp.setText("可用变量: %userName%, %userWxid%, %groupName%, %time%\n💡 随机填充将分别为标题/描述写入一条随机模板。");
        cardHelp.setTextSize(12); cardHelp.setTextColor(Color.parseColor("#666666"));
        cardPromptCard.addView(cardHelp);
        rootLayout.addView(cardPromptCard);

        // --- 卡片5: 媒体设置 (通用) ---
        LinearLayout mediaCard = createCardLayout();
        mediaCard.addView(createSectionTitle("📂 附加媒体设置 (通用)"));
        mediaCard.addView(newTextView("媒体发送顺序 (英文逗号隔开):"));
        final EditText mediaSequenceEdit = createStyledEditText("如: image,voice,video...", getString(SEND_MEDIA_SEQUENCE_KEY, "image,voice,emoji,video,file"));
        mediaCard.addView(mediaSequenceEdit);

        // 媒体顺序选项
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

        // 媒体选择行（每种媒体使用一个按钮选择多文件）
        mediaCard.addView(createSectionTitle("🗂️ 媒体文件选择（支持多选）"));

        Button btnSelectJoinImages = new Button(getTopActivity()); btnSelectJoinImages.setText("选择进群图片");
        Button btnSelectLeftImages = new Button(getTopActivity()); btnSelectLeftImages.setText("选择退群图片");
        Button btnSelectJoinVoices = new Button(getTopActivity()); btnSelectJoinVoices.setText("选择进群语音");
        Button btnSelectLeftVoices = new Button(getTopActivity()); btnSelectLeftVoices.setText("选择退群语音");
        Button btnSelectJoinEmojis = new Button(getTopActivity()); btnSelectJoinEmojis.setText("选择进群表情");
        Button btnSelectLeftEmojis = new Button(getTopActivity()); btnSelectLeftEmojis.setText("选择退群表情");
        Button btnSelectJoinVideos = new Button(getTopActivity()); btnSelectJoinVideos.setText("选择进群视频");
        Button btnSelectLeftVideos = new Button(getTopActivity()); btnSelectLeftVideos.setText("选择退群视频");
        Button btnSelectJoinFiles = new Button(getTopActivity()); btnSelectJoinFiles.setText("选择进群文件");
        Button btnSelectLeftFiles = new Button(getTopActivity()); btnSelectLeftFiles.setText("选择退群文件");

        Button[] mediaButtons = {btnSelectJoinImages, btnSelectLeftImages, btnSelectJoinVoices, btnSelectLeftVoices, btnSelectJoinEmojis, btnSelectLeftEmojis, btnSelectJoinVideos, btnSelectLeftVideos, btnSelectJoinFiles, btnSelectLeftFiles};
        for(Button btn : mediaButtons) {
            styleMediaSelectionButton(btn);
        }

        final TextView tvJoinImagesCount = new TextView(getTopActivity());
        final TextView tvLeftImagesCount = new TextView(getTopActivity());
        final TextView tvJoinVoicesCount = new TextView(getTopActivity());
        final TextView tvLeftVoicesCount = new TextView(getTopActivity());
        final TextView tvJoinEmojisCount = new TextView(getTopActivity());
        final TextView tvLeftEmojisCount = new TextView(getTopActivity());
        final TextView tvJoinVideosCount = new TextView(getTopActivity());
        final TextView tvLeftVideosCount = new TextView(getTopActivity());
        final TextView tvJoinFilesCount = new TextView(getTopActivity());
        final TextView tvLeftFilesCount = new TextView(getTopActivity());

        TextView[] countTextViews = {tvJoinImagesCount, tvLeftImagesCount, tvJoinVoicesCount, tvLeftVoicesCount, tvJoinEmojisCount, tvLeftEmojisCount, tvJoinVideosCount, tvLeftVideosCount, tvJoinFilesCount, tvLeftFilesCount};
        for (TextView tv : countTextViews) {
            styleCountTextView(tv);
        }

        // 布局：简单地依次添加按钮和计数文本
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
        delayCard.addView(createSectionTitle("⏱️ 精细延迟设置 (毫秒)"));
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
            .setTitle("✨ 进退群统一设置 ✨")
            .setView(scrollView)
            .setPositiveButton("✅ 保存全部", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        // 保存核心设置
                        int newDelay = Integer.parseInt(delayEditText.getText().toString());
                        if (newDelay >= 0 && newDelay <= 600) putInt(DELAY_KEY, newDelay); else { toast("总延迟应在0-600秒之间"); return; }
                        putString(PROMPT_TYPE_KEY, textTypeButton.isChecked() ? "text" : "card");

                        // 保存文本和卡片设置
                        putString(JOIN_TEXT_PROMPT_KEY, joinPromptEditText.getText().toString());
                        putString(LEFT_TEXT_PROMPT_KEY, leftPromptEditText.getText().toString());
                        putString(JOIN_CARD_TITLE_KEY, joinTitleEditText.getText().toString());
                        putString(JOIN_CARD_DESC_KEY, joinDescEditText.getText().toString());
                        putString(LEFT_CARD_TITLE_KEY, leftTitleEditText.getText().toString());
                        putString(LEFT_CARD_DESC_KEY, leftDescEditText.getText().toString());

                        // 保存媒体设置（已有按钮会直接 putString）
                        putString(SEND_MEDIA_SEQUENCE_KEY, mediaSequenceEdit.getText().toString());
                        putString(SEND_MEDIA_ORDER_KEY, noneButton.isChecked() ? "none" : beforeButton.isChecked() ? "before" : "after");

                        // 保存精细延迟
                        putInt(PROMPT_DELAY_KEY, Integer.parseInt(promptDelayEdit.getText().toString()));
                        putInt(IMAGE_DELAY_KEY, Integer.parseInt(imageDelayEdit.getText().toString()));
                        putInt(VOICE_DELAY_KEY, Integer.parseInt(voiceDelayEdit.getText().toString()));
                        putInt(EMOJI_DELAY_KEY, Integer.parseInt(emojiDelayEdit.getText().toString()));
                        putInt(VIDEO_DELAY_KEY, Integer.parseInt(videoDelayEdit.getText().toString()));
                        putInt(FILE_DELAY_KEY, Integer.parseInt(fileDelayEdit.getText().toString()));

                        toast("所有设置已保存！");
                    } catch (NumberFormatException e) {
                        toast("保存失败：延迟时间必须是有效数字!");
                    } catch (Exception ex) {
                        toast("保存失败: " + ex.getMessage());
                    }
                }
            })
            .setNegativeButton("❌ 取消", null)
            .setNeutralButton("🔄 恢复默认", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // 恢复全部默认（保留原来默认）
                    delayEditText.setText(String.valueOf(DEFAULT_DELAY));
                    textTypeButton.setChecked(true);
                    joinPromptEditText.setText("[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者昵称：%userName%\n进群者ID：%userWxid%");
                    leftPromptEditText.setText("退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者昵称：%userName%\n退群者ID：%userWxid%");
                    joinTitleEditText.setText("🎊 欢迎：%userName%");
                    joinDescEditText.setText("🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
                    leftTitleEditText.setText("💔 离群：%userName%");
                    leftDescEditText.setText("🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
                    noneButton.setChecked(true);
                    mediaSequenceEdit.setText("image,voice,emoji,video,file");
                    // 清空媒体选择
                    putString(JOIN_IMAGE_PATHS_KEY, ""); putString(LEFT_IMAGE_PATHS_KEY, "");
                    putString(JOIN_VOICE_PATHS_KEY, ""); putString(LEFT_VOICE_PATHS_KEY, "");
                    putString(JOIN_EMOJI_PATHS_KEY, ""); putString(LEFT_EMOJI_PATHS_KEY, "");
                    putString(JOIN_VIDEO_PATHS_KEY, ""); putString(LEFT_VIDEO_PATHS_KEY, "");
                    putString(JOIN_FILE_PATHS_KEY, ""); putString(LEFT_FILE_PATHS_KEY, "");
                    promptDelayEdit.setText("0"); imageDelayEdit.setText("100");
                    voiceDelayEdit.setText("100"); emojiDelayEdit.setText("100");
                    videoDelayEdit.setText("100"); fileDelayEdit.setText("100");
                    toast("已恢复所有默认设置");
                }
            })
            .create();

        groupManagementButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showGroupManagementDialog();
            }
        });

        // ---- 文本随机填充（直接写入单条随机提示语） ----
        fillRandomTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String randomJoin = RANDOM_JOIN_TEXTS_ARRAY[new Random().nextInt(RANDOM_JOIN_TEXTS_ARRAY.length)];
                String randomLeft = RANDOM_LEFT_TEXTS_ARRAY[new Random().nextInt(RANDOM_LEFT_TEXTS_ARRAY.length)];
                joinPromptEditText.setText(randomJoin);
                leftPromptEditText.setText(randomLeft);
                toast("已随机填充欢迎/退群语（单条）");
            }
        });

        restoreTextDefaultsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinPromptEditText.setText("[AtWx=%userWxid%]\n欢迎进群\n时间：%time%\n群昵称：%groupName%\n进群者昵称：%userName%\n进群者ID：%userWxid%");
                leftPromptEditText.setText("退群通知：\n时间：%time%\n群昵称：%groupName%\n退群者昵称：%userName%\n退群者ID：%userWxid%");
                toast("已恢复文本默认");
            }
        });

        // ---- 卡片随机填充 ----
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

                toast("已随机填充卡片内容（单条）");
            }
        });

        restoreCardDefaultsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                joinTitleEditText.setText("🎊 欢迎：%userName%");
                joinDescEditText.setText("🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
                leftTitleEditText.setText("💔 离群：%userName%");
                leftDescEditText.setText("🆔：%userWxid%\n⏰：%time%\n🏠：%groupName%");
                toast("已恢复卡片默认");
            }
        });

        // ---- 媒体选择按钮逻辑（点击后进入文件浏览并多选） ----
        btnSelectJoinImages.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(JOIN_IMAGE_PATHS_KEY, "");
                browseFolderForSelection(last, ".png", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(JOIN_IMAGE_PATHS_KEY, joinPaths(selectedFiles));
                        tvJoinImagesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存进群图片选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });
        btnSelectLeftImages.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(LEFT_IMAGE_PATHS_KEY, "");
                browseFolderForSelection(last, ".png", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(LEFT_IMAGE_PATHS_KEY, joinPaths(selectedFiles));
                        tvLeftImagesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存退群图片选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });

        btnSelectJoinVoices.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(JOIN_VOICE_PATHS_KEY, "");
                browseFolderForSelection(last, "", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(JOIN_VOICE_PATHS_KEY, joinPaths(selectedFiles));
                        tvJoinVoicesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存进群语音选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });
        btnSelectLeftVoices.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(LEFT_VOICE_PATHS_KEY, "");
                browseFolderForSelection(last, "", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(LEFT_VOICE_PATHS_KEY, joinPaths(selectedFiles));
                        tvLeftVoicesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存退群语音选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });

        btnSelectJoinEmojis.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(JOIN_EMOJI_PATHS_KEY, "");
                browseFolderForSelection(last, ".png", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(JOIN_EMOJI_PATHS_KEY, joinPaths(selectedFiles));
                        tvJoinEmojisCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存进群表情选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });
        btnSelectLeftEmojis.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(LEFT_EMOJI_PATHS_KEY, "");
                browseFolderForSelection(last, ".png", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(LEFT_EMOJI_PATHS_KEY, joinPaths(selectedFiles));
                        tvLeftEmojisCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存退群表情选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });

        btnSelectJoinVideos.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(JOIN_VIDEO_PATHS_KEY, "");
                browseFolderForSelection(last, ".mp4", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(JOIN_VIDEO_PATHS_KEY, joinPaths(selectedFiles));
                        tvJoinVideosCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存进群视频选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });
        btnSelectLeftVideos.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(LEFT_VIDEO_PATHS_KEY, "");
                browseFolderForSelection(last, ".mp4", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(LEFT_VIDEO_PATHS_KEY, joinPaths(selectedFiles));
                        tvLeftVideosCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存退群视频选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });

        btnSelectJoinFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(JOIN_FILE_PATHS_KEY, "");
                browseFolderForSelection(last, "", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(JOIN_FILE_PATHS_KEY, joinPaths(selectedFiles));
                        tvJoinFilesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存进群文件选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });
        btnSelectLeftFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                File last = new File(getString(DEFAULT_LAST_FOLDER_SP, ROOT_FOLER));
                String currentSelection = getString(LEFT_FILE_PATHS_KEY, "");
                browseFolderForSelection(last, "", currentSelection, new MediaSelectionCallback() {
                    public void onSelected(ArrayList<String> selectedFiles) {
                        putString(LEFT_FILE_PATHS_KEY, joinPaths(selectedFiles));
                        tvLeftFilesCount.setText(selectedFiles.size() + " 个已选");
                        toast("已保存退群文件选择 (" + selectedFiles.size() + ")");
                    }
                });
            }
        });

        // 初始化计数显示（读取已有配置）
        tvJoinImagesCount.setText(countFromString(getString(JOIN_IMAGE_PATHS_KEY, "")) + " 个已选");
        tvLeftImagesCount.setText(countFromString(getString(LEFT_IMAGE_PATHS_KEY, "")) + " 个已选");
        tvJoinVoicesCount.setText(countFromString(getString(JOIN_VOICE_PATHS_KEY, "")) + " 个已选");
        tvLeftVoicesCount.setText(countFromString(getString(LEFT_VOICE_PATHS_KEY, "")) + " 个已选");
        tvJoinEmojisCount.setText(countFromString(getString(JOIN_EMOJI_PATHS_KEY, "")) + " 个已选");
        tvLeftEmojisCount.setText(countFromString(getString(LEFT_EMOJI_PATHS_KEY, "")) + " 个已选");
        tvJoinVideosCount.setText(countFromString(getString(JOIN_VIDEO_PATHS_KEY, "")) + " 个已选");
        tvLeftVideosCount.setText(countFromString(getString(LEFT_VIDEO_PATHS_KEY, "")) + " 个已选");
        tvJoinFilesCount.setText(countFromString(getString(JOIN_FILE_PATHS_KEY, "")) + " 个已选");
        tvLeftFilesCount.setText(countFromString(getString(LEFT_FILE_PATHS_KEY, "")) + " 个已选");

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

// 辅助：把路径列表拼接为逗号分隔字符串
private String joinPaths(ArrayList<String> paths) {
    if (paths == null) return "";
    StringBuilder sb = new StringBuilder();
    for (String p : paths) {
        if (sb.length() > 0) sb.append(",");
        sb.append(p);
    }
    return sb.toString();
}

// 辅助：从配置的逗号分隔路径数出数量
private int countFromString(String s) {
    if (TextUtils.isEmpty(s)) return 0;
    String[] parts = s.split(",");
    int cnt = 0;
    for (String p : parts) if (!TextUtils.isEmpty(p.trim())) cnt++;
    return cnt;
}

// 新增：获取显示名称（优先群备注，没有则用昵称）
// [修复] 改进逻辑：如果传入的 userName 看起来像 wxid（以 "wxid_" 开头），则优先尝试从联系人或群成员获取真实昵称，避免直接使用 wxid 作为昵称
private String getDisplayName(String userWxid, String groupWxid, String userName) {
    // 首先检查传入的 userName 是否有效（非空且非 wxid 格式）
    if (!TextUtils.isEmpty(userName) && !userName.startsWith("wxid_")) {
        return userName;
    }

    // 如果 userName 是 wxid 或空，尝试从全局好友昵称获取
    String globalNickname = getFriendName(userWxid);
    if (!TextUtils.isEmpty(globalNickname) && !"未设置".equals(globalNickname)) {
        return globalNickname;
    }

    // 否则尝试从群内备注获取
    String groupNickname = getFriendName(userWxid, groupWxid);
    if (!TextUtils.isEmpty(groupNickname) && !"未设置".equals(groupNickname)) {
        return groupNickname;
    }

    // 最终 fallback
    return "新成员"; // 或返回 userWxid + " (新成员)" 以区分
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
    editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            if (hasFocus) {
                bg.setStroke(3, Color.parseColor("#7AA6C2")); // 焦点色
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
    // [V3] 为媒体选择行增加外边距，拉开间距
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 8, 0, 8); // 增加垂直间距
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

// [V3] 优化媒体选择按钮样式
private void styleMediaSelectionButton(Button button) {
    button.setTextColor(Color.parseColor("#3B82F6")); // 更鲜艳的蓝色
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(20);
    shape.setColor(Color.parseColor("#EFF6FF")); // 更清爽的淡蓝色背景
    shape.setStroke(2, Color.parseColor("#BFDBFE")); // 匹配的边框色
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
        final Set<String> selectedGroups = getStringSet(LISTEN_GROUPS_KEY, new HashSet<String>());
        final List<String> currentFilteredRoomIds = new ArrayList<>();
        final List<String> currentFilteredNames = new ArrayList<>();

        LinearLayout dialogLayout = new LinearLayout(getTopActivity());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(16, 16, 16, 16);

        final EditText searchEditText = createStyledEditText("🔍 搜索群聊...", "");
        dialogLayout.addView(searchEditText);

        TextView infoText = new TextView(getTopActivity());
        infoText.setText("勾选开启监听。长按群聊可单独设置进/退群开关。");
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
        builder.setNeutralButton("✨ 全选", null); // Placeholder text, real text set in onShow

        final AlertDialog dialog = builder.create();

        // [V4] 改进全选按钮，增加取消全选功能
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            
            public void onShow(DialogInterface d) {
                GradientDrawable dialogBg = new GradientDrawable();
                dialogBg.setCornerRadius(48);
                dialogBg.setColor(Color.parseColor("#FAFBF9"));
                dialog.getWindow().setBackgroundDrawable(dialogBg);

                styleDialogButtons(dialog);

                final Button selectAllButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (selectAllButton != null) {
                    // 检查初始状态并设置按钮文本
                    boolean allSelected = !currentFilteredRoomIds.isEmpty() && selectedGroups.containsAll(currentFilteredRoomIds);
                    selectAllButton.setText(allSelected ? "✨ 取消全选" : "✨ 全选");

                    selectAllButton.setOnClickListener(new View.OnClickListener() {
                        
                        public void onClick(View v) {
                            // 再次检查当前状态，以决定执行全选还是取消全选
                            boolean allSelectedCurrently = !currentFilteredRoomIds.isEmpty() && selectedGroups.containsAll(currentFilteredRoomIds);

                            if (allSelectedCurrently) {
                                // 如果已是全选状态，则执行取消全选
                                selectedGroups.removeAll(currentFilteredRoomIds);
                                for (int i = 0; i < groupListView.getCount(); i++) {
                                    groupListView.setItemChecked(i, false);
                                }
                                toast("已取消全选");
                                selectAllButton.setText("✨ 全选"); // 更新按钮文本为下一次操作
                            } else {
                                // 否则，执行全选
                                selectedGroups.addAll(currentFilteredRoomIds);
                                for (int i = 0; i < groupListView.getCount(); i++) {
                                    groupListView.setItemChecked(i, true);
                                }
                                toast("已全选当前列表中的 " + currentFilteredRoomIds.size() + " 个群组");
                                selectAllButton.setText("✨ 取消全选"); // 更新按钮文本为下一次操作
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

private void showIndividualGroupPromptToggleDialog(final String groupWxid, String groupName) {
    try {
        final Set<String> disabledJoinToggles = getStringSet(JOIN_TOGGLE_KEY, new HashSet<String>());
        final Set<String> disabledLeftToggles = getStringSet(LEFT_TOGGLE_KEY, new HashSet<String>());

        LinearLayout layout = new LinearLayout(getTopActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        final Switch joinSwitch = new Switch(getTopActivity());
        joinSwitch.setText("开启进群提示  ");
        joinSwitch.setTextSize(16);
        joinSwitch.setPadding(8, 24, 8, 24);
        joinSwitch.setChecked(!disabledJoinToggles.contains(groupWxid));
        layout.addView(joinSwitch);

        final Switch leftSwitch = new Switch(getTopActivity());
        leftSwitch.setText("开启退群提示  ");
        leftSwitch.setTextSize(16);
        leftSwitch.setPadding(8, 24, 8, 24);
        leftSwitch.setChecked(!disabledLeftToggles.contains(groupWxid));
        layout.addView(leftSwitch);

        AlertDialog dialog = new AlertDialog.Builder(getTopActivity())
            .setTitle("🔧 " + groupName)
            .setView(layout)
            .setPositiveButton("✅ 保存", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
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
                    toast("已保存 " + groupName + " 的开关设置");
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