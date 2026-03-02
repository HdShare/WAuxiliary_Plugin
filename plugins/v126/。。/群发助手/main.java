import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.widget.CheckBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.UUID;
import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ListView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.ScrollView;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import java.util.Arrays;
import android.text.InputType;
import android.content.Context;
import java.util.Random;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Calendar;
import android.widget.TimePicker;
import android.widget.DatePicker;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import android.view.MotionEvent;
import java.util.Collections;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.AbsoluteSizeSpan;
import android.os.Build;

// 引入 FastJSON 用于数据存储
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.TypeReference;

// ==========================================
// ========== 🕒 微信定时发送助手 (多任务精确版) ==========
// ==========================================

// 全局变量 - 发送配置
Set<String> massSendTargetWxids = new HashSet<>(); 
int massSendType = 0; // 0:文本, 1:图片, 2:视频, 3:文件, 4:表情
String massSendTextContent = "";
List<String> massSendMediaPaths = new ArrayList<>();
long massSendInterval = 0; // 发送对象间隔(秒)
long massSendMediaInterval = 0; // 多媒体文件间隔(秒)

// 定时任务相关 - 改为多任务支持
Map<String, JSONObject> scheduledTasks = new HashMap<>(); // taskId -> taskData
Map<String, Runnable> scheduledRunnables = new HashMap<>(); // taskId -> Runnable
boolean isTaskRunning = false;
Handler scheduleHandler = new Handler(Looper.getMainLooper());

// 常量定义
final int SEND_TYPE_TEXT = 0;
final int SEND_TYPE_IMAGE = 1;
final int SEND_TYPE_VIDEO = 2;
final int SEND_TYPE_FILE = 3;
final int SEND_TYPE_EMOJI = 4;
final int SEND_TYPE_VOICE = 5;

// 存储Key
final String CONFIG_KEY = "scheduled_send_multi_v2"; 
final String KEY_LABELS = "saved_target_labels"; 
final String KEY_TASKS = "scheduled_tasks"; // 存储多任务列表

// 缓存列表
private List sCachedFriendList = null;
private List sCachedGroupList = null;

// ==========================================
// ========== ♻️ 生命周期与核心逻辑 ==========
// ==========================================

/**
 * 插件加载时调用
 * 用于恢复所有未完成的定时任务
 */
public void onLoad() {
    // 延时一点执行，确保环境就绪
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        public void run() {
            restoreAllTasks();
        }
    }, 5000);
}

/**
 * 恢复所有定时任务
 */
private void restoreAllTasks() {
    try {
        String tasksJson = getString(CONFIG_KEY, KEY_TASKS, "{}");
        JSONObject allTasks = JSON.parseObject(tasksJson);
        
        if (allTasks == null || allTasks.isEmpty()) return;
        
        long now = System.currentTimeMillis();
        int restoredCount = 0;
        
        for (String taskId : allTasks.keySet()) {
            JSONObject task = allTasks.getJSONObject(taskId);
            long planTime = task.getLongValue("planTime");
            
            if (planTime <= 0) continue;
            
            // 恢复内存变量
            scheduledTasks.put(taskId, task);
            
            if (now >= planTime) {
                // 时间已过，检查是否在容忍范围内（10分钟）
                if (now - planTime < 10 * 60 * 1000) {
                    log("检测到错过的任务 " + taskId + "（10分钟内），准备补发...");
                    notify("定时发送补发", "检测到任务 " + taskId + " 即将补发...");
                } else {
                    // 过期任务标记为已过期
                    task.put("status", "expired");
                    log("任务 " + taskId + " 已过期");
                    continue;
                }
            } else {
                // 时间未到，重新加入定时器
                long delay = planTime - now;
                scheduleTaskWithPrecision(taskId, task, delay);
                restoredCount++;
            }
        }
        
        // 保存更新后的状态
        saveAllTasks();
        
        if (restoredCount > 0) {
            log("已恢复 " + restoredCount + " 个定时任务");
        }
    } catch (Exception e) {
        log("恢复任务失败: " + e.getMessage());
    }
}

/**
 * 高精度定时执行（精确到毫秒级，误差<100ms）
 */
private void scheduleTaskWithPrecision(final String taskId, JSONObject task, long delayMillis) {
    // 取消该任务旧的定时器
    cancelTaskTimer(taskId);
    
    final long targetTime = task.getLongValue("planTime");
    
    Runnable runnable = new Runnable() {
        public void run() {
            // 高精度唤醒：提前100ms检查，使用循环等待直到精确时间
            long now = System.currentTimeMillis();
            if (now < targetTime) {
                // 还在延迟中，设定更近的检查点
                scheduleHandler.postDelayed(this, Math.max(1, targetTime - now - 100));
                return;
            }
            
            // 精确时间到达，补偿剩余的毫秒
            while (System.currentTimeMillis() < targetTime) {
                // 忙等待到精确时间
            }
            
            // 执行发送
            executeTaskSend(taskId);
        }
    };
    
    scheduledRunnables.put(taskId, runnable);
    scheduleHandler.postDelayed(runnable, delayMillis);
}

/**
 * 精确执行单个任务（同一时间同步发送给所有目标）
 */
private void executeTaskSend(String taskId) {
    JSONObject task = scheduledTasks.get(taskId);
    if (task == null) return;
    
    // 检查是否已过期
    long planTime = task.getLongValue("planTime");
    if (System.currentTimeMillis() > planTime + 30000) {
        task.put("status", "expired");
        saveAllTasks();
        return;
    }
    
    // 加载任务配置
    int type = task.getIntValue("type");
    String content = task.getString("content");
    long interval = task.getLongValue("interval");
    long mediaInterval = task.getLongValue("mediaInterval");
    
    JSONArray targetsJson = task.getJSONArray("targets");
    List<String> targets = new ArrayList<>();
    if (targetsJson != null) {
        for (int i = 0; i < targetsJson.size(); i++) {
            targets.add(targetsJson.getString(i));
        }
    }
    
    JSONArray mediasJson = task.getJSONArray("medias");
    List<String> mediaPaths = new ArrayList<>();
    if (mediasJson != null) {
        for (int i = 0; i < mediasJson.size(); i++) {
            mediaPaths.add(mediasJson.getString(i));
        }
    }
    
    // 标记任务执行中
    task.put("status", "running");
    saveAllTasks();
    
    // 通知开始执行
    notify("定时发送开始", "任务 " + taskId.substring(0, 8) + " 开始执行\n目标数: " + targets.size());
    
    // 在后台线程执行发送
    final List<String> finalTargets = targets;
    final List<String> finalMediaPaths = mediaPaths;
    final int finalType = type;
    final String finalContent = content;
    final long finalInterval = interval;
    final long finalMediaInterval = mediaInterval;
    final JSONObject finalTask = task;
    
    new Thread(new Runnable() {
        public void run() {
            int success = 0;
            int fail = 0;
            
            // 同步发送：同一时间发送给所有选中的目标
            // 不使用间隔，让所有消息在同一时间发送出去
            for (int i = 0; i < finalTargets.size(); i++) {
                String target = finalTargets.get(i);
                String name = getContactName(target);
                
                try {
                    if (finalType == SEND_TYPE_TEXT) {
                        String contentToSend = finalContent.replace("%friendName%", name);
                        sendText(target, contentToSend);
                    } else {
                        // 多媒体发送
                        for (int j = 0; j < finalMediaPaths.size(); j++) {
                            String path = finalMediaPaths.get(j);
                            File f = new File(path);
                            if (f.exists()) {
                                switch (finalType) {
                                    case SEND_TYPE_IMAGE: sendImage(target, path); break;
                                    case SEND_TYPE_VIDEO: sendVideo(target, path); break;
                                    case SEND_TYPE_EMOJI: sendEmoji(target, path); break;
                                    case SEND_TYPE_FILE: shareFile(target, f.getName(), path, ""); break;
                                    case SEND_TYPE_VOICE: sendVoice(target, path); break;
                                }
                                // 多文件间隔
                                if (j < finalMediaPaths.size() - 1) {
                                    Thread.sleep(finalMediaInterval * 1000);
                                }
                            }
                        }
                    }
                    success++;
                } catch (Exception e) {
                    fail++;
                    log("发送失败: " + target + " - " + e.getMessage());
                }
                
                // 目标之间的小间隔，避免同时发送过多
                if (i < finalTargets.size() - 1 && finalInterval > 0) {
                    try { Thread.sleep(finalInterval * 1000); } catch (Exception e) {}
                }
            }
            
            // 完成任务
            finalTask.put("status", "completed");
            finalTask.put("successCount", success);
            finalTask.put("failCount", fail);
            finalTask.put("completedTime", System.currentTimeMillis());
            saveAllTasks();
            
            // 清理该任务的定时器
            cancelTaskTimer(taskId);
            scheduledTasks.remove(taskId);
            
            // 完成通知
            final String report = "成功: " + success + ", 失败: " + fail;
            notify("定时发送完成", "任务 " + taskId.substring(0, 8) + " 已完成\n" + report);
            
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    toast("✅ 任务执行完毕\n" + report);
                }
            });
        }
    }).start();
}

// 修正闭包变量问题

/**
 * 取消单个任务的定时器
 */
private void cancelTaskTimer(String taskId) {
    Runnable runnable = scheduledRunnables.get(taskId);
    if (runnable != null) {
        scheduleHandler.removeCallbacks(runnable);
        scheduledRunnables.remove(taskId);
    }
}

/**
 * 取消所有任务的定时器
 */
private void cancelAllTaskTimers() {
    for (String taskId : scheduledRunnables.keySet()) {
        Runnable runnable = scheduledRunnables.get(taskId);
        if (runnable != null) {
            scheduleHandler.removeCallbacks(runnable);
        }
    }
    scheduledRunnables.clear();
}

/**
 * 保存所有任务到存储
 */
private void saveAllTasks() {
    putString(CONFIG_KEY, KEY_TASKS, JSON.toJSONString(scheduledTasks));
}

// 入口函数
public boolean onClickSendBtn(String text) {
    massSendTargetWxids.clear();
    if ("群发助手".equals(text) || "定时发送".equals(text)) {
        showMainDialog();
        return true;
    }
    return false;
}

// ==========================================
// ========== 📱 UI 界面逻辑 ==========
// ==========================================

private void showMainDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    // --- 顶部：任务统计卡片 ---
    LinearLayout statsCard = createCardLayout();
    statsCard.setBackground(createGradientDrawable("#E8F5E9", 32));
    statsCard.addView(createSectionTitle("📊 任务概览"));
    
    final TextView statsTv = new TextView(getTopActivity());
    updateStatsText(statsTv);
    statsTv.setTextSize(14);
    statsCard.addView(statsTv);
    
    Button viewTasksBtn = new Button(getTopActivity());
    viewTasksBtn.setText("📋 查看所有任务");
    styleUtilityButton(viewTasksBtn);
    GradientDrawable btnBg = (GradientDrawable) viewTasksBtn.getBackground();
    btnBg.setColor(Color.parseColor("#C8E6C9"));
    btnBg.setStroke(2, Color.parseColor("#81C784"));
    viewTasksBtn.setTextColor(Color.parseColor("#2E7D32"));
    viewTasksBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showTaskListDialog();
        }
    });
    statsCard.addView(viewTasksBtn);
    root.addView(statsCard);

    // --- 任务统计刷新按钮 ---
    Button refreshTasksBtn = new Button(getTopActivity());
    refreshTasksBtn.setText("🔄 刷新任务列表");
    styleUtilityButton(refreshTasksBtn);
    refreshTasksBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            updateStatsText(statsTv);
            toast("任务统计已刷新");
        }
    });
    root.addView(refreshTasksBtn);

    // --- 1. 快速创建任务卡片 ---
    LinearLayout quickCard = createCardLayout();
    quickCard.addView(createSectionTitle("🚀 快速创建定时任务"));
    
    Button newTaskBtn = new Button(getTopActivity());
    newTaskBtn.setText("➕ 新建定时任务");
    styleMediaSelectionButton(newTaskBtn);
    newTaskBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showCreateTaskDialog();
        }
    });
    quickCard.addView(newTaskBtn);
    root.addView(quickCard);

    // --- 2. 目标选择卡片 ---
    LinearLayout targetCard = createCardLayout();
    targetCard.addView(createSectionTitle("👥 发送目标"));
    
    final TextView targetCountTv = new TextView(getTopActivity());
    updateTargetCountText(targetCountTv);
    targetCountTv.setTextSize(14);
    targetCountTv.setTextColor(Color.parseColor("#666666"));
    targetCountTv.setPadding(0, 0, 0, 16);
    targetCard.addView(targetCountTv);

    Button selectTargetBtn = new Button(getTopActivity());
    selectTargetBtn.setText("👤 手动选择 (好友/群聊)");
    styleUtilityButton(selectTargetBtn);
    selectTargetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showTargetCategoryDialog(massSendTargetWxids, new Runnable() {
                public void run() {
                    updateTargetCountText(targetCountTv);
                }
            });
        }
    });
    targetCard.addView(selectTargetBtn);
    
    Button labelManageBtn = new Button(getTopActivity());
    labelManageBtn.setText("🏷️ 标签分组管理 (新建/导入)");
    styleUtilityButton(labelManageBtn);
    GradientDrawable labelBg = (GradientDrawable) labelManageBtn.getBackground();
    labelBg.setColor(Color.parseColor("#E3F2FD"));
    labelBg.setStroke(2, Color.parseColor("#90CAF9"));
    labelManageBtn.setTextColor(Color.parseColor("#1976D2"));
    labelManageBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showLabelManagementDialog(new Runnable() {
                public void run() {
                    updateTargetCountText(targetCountTv);
                }
            });
        }
    });
    targetCard.addView(labelManageBtn);

    Button clearTargetBtn = new Button(getTopActivity());
    clearTargetBtn.setText("🗑️ 清空已选");
    styleUtilityButton(clearTargetBtn);
    clearTargetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            massSendTargetWxids.clear();
            updateTargetCountText(targetCountTv);
            toast("已清空发送目标");
        }
    });
    targetCard.addView(clearTargetBtn);
    root.addView(targetCard);

    // --- 3. 内容设置卡片 ---
    LinearLayout contentCard = createCardLayout();
    contentCard.addView(createSectionTitle("📝 编辑发送内容"));

    // 类型选择 - 使用嵌套RadioGroup实现两行互斥选择
    RadioGroup mainTypeGroup = new RadioGroup(getTopActivity());
    mainTypeGroup.setOrientation(LinearLayout.VERTICAL);
    
    RadioGroup row1Group = new RadioGroup(getTopActivity());
    row1Group.setOrientation(LinearLayout.HORIZONTAL);
    
    RadioButton rbText = createRadioButton(getTopActivity(), "📝文本");
    RadioButton rbImage = createRadioButton(getTopActivity(), "🖼️图片");
    RadioButton rbVideo = createRadioButton(getTopActivity(), "🎬视频");
    row1Group.addView(rbText);
    row1Group.addView(rbImage);
    row1Group.addView(rbVideo);
    
    RadioGroup row2Group = new RadioGroup(getTopActivity());
    row2Group.setOrientation(LinearLayout.HORIZONTAL);
    
    RadioButton rbFile = createRadioButton(getTopActivity(), "📁文件");
    RadioButton rbEmoji = createRadioButton(getTopActivity(), "😊表情");
    RadioButton rbVoice = createRadioButton(getTopActivity(), "🎤语音");
    row2Group.addView(rbFile);
    row2Group.addView(rbEmoji);
    row2Group.addView(rbVoice);
    
    // 设置行间距
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    rowParams.setMargins(0, 4, 0, 4);
    row1Group.setLayoutParams(rowParams);
    row2Group.setLayoutParams(rowParams);
    
    mainTypeGroup.addView(row1Group);
    mainTypeGroup.addView(row2Group);
    contentCard.addView(mainTypeGroup);

    // 内容容器
    final LinearLayout contentContainer = new LinearLayout(getTopActivity());
    contentContainer.setOrientation(LinearLayout.VERTICAL);
    contentContainer.setPadding(0, 16, 0, 0);
    contentCard.addView(contentContainer);
    root.addView(contentCard);

    // --- 4. 发送设置卡片 ---
    LinearLayout settingCard = createCardLayout();
    settingCard.addView(createSectionTitle("⚙️ 发送参数设置"));
    
    settingCard.addView(createTextView(getTopActivity(), "发送对象间隔 (秒):", 14, 8));
    final EditText intervalEdit = createStyledEditText("默认 0 秒（同时发送）", String.valueOf(massSendInterval));
    intervalEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
    settingCard.addView(intervalEdit);

    settingCard.addView(createTextView(getTopActivity(), "多媒体文件间隔 (秒):", 14, 8));
    final EditText mediaIntervalEdit = createStyledEditText("默认 0 秒", String.valueOf(massSendMediaInterval));
    mediaIntervalEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
    settingCard.addView(mediaIntervalEdit);
    
    root.addView(settingCard);

    // --- UI 更新逻辑 ---
    final Runnable updateContentUI = new Runnable() {
        public void run() {
            contentContainer.removeAllViews();
            if (massSendType == SEND_TYPE_TEXT) {
                EditText textEdit = createStyledEditText("请输入要群发的文本内容...", massSendTextContent);
                textEdit.setMinLines(5);
                textEdit.setGravity(Gravity.TOP);
                textEdit.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) {
                        massSendTextContent = s.toString();
                    }
                });
                contentContainer.addView(textEdit);
                contentContainer.addView(createPromptText("支持变量: %friendName% (好友昵称/备注)"));
            } else {
                TextView pathTv = new TextView(getTopActivity());
                StringBuilder sb = new StringBuilder();
                if (massSendMediaPaths.isEmpty()) {
                    sb.append("🚫 未选择文件");
                } else {
                    sb.append("✅ 已选 ").append(massSendMediaPaths.size()).append(" 个文件:\n");
                    for(int i=0; i<Math.min(massSendMediaPaths.size(), 5); i++) {
                         sb.append(new File(massSendMediaPaths.get(i)).getName()).append("\n");
                    }
                    if(massSendMediaPaths.size() > 5) sb.append("...等");
                }
                pathTv.setText(sb.toString());
                pathTv.setTextColor(Color.parseColor("#333333"));
                pathTv.setPadding(0, 0, 0, 16);
                contentContainer.addView(pathTv);
                
                // 语音类型的特殊提示
                if (massSendType == SEND_TYPE_VOICE) {
                    TextView voiceTip = createPromptText("提示: 语音文件需为 .silk 格式");
                    voiceTip.setTextColor(Color.parseColor("#FF9800"));
                    contentContainer.addView(voiceTip);
                }
                
                Button selMediaBtn = new Button(getTopActivity());
                selMediaBtn.setText("📂 选择文件 (支持多选)");
                styleMediaSelectionButton(selMediaBtn);
                
                Object[] tags = getMediaSelectTagForMassSend(massSendType);
                selMediaBtn.setTag(tags);
                
                selMediaBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Object[] tag = (Object[]) v.getTag();
                        String extFilter = (String) tag[0];
                        File lastFolder = new File(getString(DEFAULT_LAST_FOLDER_SP_AUTO, ROOT_FOLDER));
                        browseFolderForSelectionAuto(lastFolder, extFilter, "", new MediaSelectionCallback() {
                            public void onSelected(ArrayList<String> selectedFiles) {
                                massSendMediaPaths.clear();
                                massSendMediaPaths.addAll(selectedFiles);
                                updateContentUI.run(); 
                            }
                        }, false); 
                    }
                });
                contentContainer.addView(selMediaBtn);
                
                if (!massSendMediaPaths.isEmpty()) {
                     Button clearMediaBtn = new Button(getTopActivity());
                     clearMediaBtn.setText("清空已选文件");
                     styleUtilityButton(clearMediaBtn);
                     clearMediaBtn.setOnClickListener(new View.OnClickListener() {
                         public void onClick(View v) {
                             massSendMediaPaths.clear();
                             updateContentUI.run();
                         }
                     });
                     contentContainer.addView(clearMediaBtn);
                }
            }
        }
    };
    
    // 根据当前类型设置选中状态
    switch(massSendType) {
        case SEND_TYPE_TEXT: rbText.setChecked(true); break;
        case SEND_TYPE_IMAGE: rbImage.setChecked(true); break;
        case SEND_TYPE_VIDEO: rbVideo.setChecked(true); break;
        case SEND_TYPE_FILE: rbFile.setChecked(true); break;
        case SEND_TYPE_EMOJI: rbEmoji.setChecked(true); break;
        case SEND_TYPE_VOICE: rbVoice.setChecked(true); break;
    }
    updateContentUI.run();

    // 用于防止监听器死循环的标志位
    final boolean[] isProcessing = {false};

    // 类型切换监听 - 两个Group联动，确保只有一个选中
    row1Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            
            if (checkedId == rbText.getId()) massSendType = SEND_TYPE_TEXT;
            else if (checkedId == rbImage.getId()) massSendType = SEND_TYPE_IMAGE;
            else if (checkedId == rbVideo.getId()) massSendType = SEND_TYPE_VIDEO;
            
            // 清除另一行的选中状态
            row2Group.clearCheck();
            
            massSendMediaPaths.clear();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });
    
    row2Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isProcessing[0]) return;
            isProcessing[0] = true;
            
            if (checkedId == rbFile.getId()) massSendType = SEND_TYPE_FILE;
            else if (checkedId == rbEmoji.getId()) massSendType = SEND_TYPE_EMOJI;
            else if (checkedId == rbVoice.getId()) massSendType = SEND_TYPE_VOICE;
            
            // 清除另一行的选中状态
            row1Group.clearCheck();
            
            massSendMediaPaths.clear();
            updateContentUI.run();
            isProcessing[0] = false;
        }
    });

    // --- 底部按钮 ---
    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🕒 定时发送助手", scrollView, "💾 保存为新任务", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            // 验证
            if (massSendTargetWxids.isEmpty()) {
                toast("请先选择发送目标！");
                return; 
            }
            if (massSendType == SEND_TYPE_TEXT && TextUtils.isEmpty(massSendTextContent)) {
                toast("请输入文本内容！");
                return;
            }
            if (massSendType != SEND_TYPE_TEXT && massSendType != SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
                toast("请选择要发送的文件！");
                return;
            }
            if (massSendType == SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
                toast("请选择语音文件(.silk)！");
                return;
            }
            
            // 读取设置
            try {
                massSendInterval = Long.parseLong(intervalEdit.getText().toString());
                massSendMediaInterval = Long.parseLong(mediaIntervalEdit.getText().toString());
            } catch(Exception e) {}
            
            // 跳转到时间选择
            showDateTimePickerWithSeconds(new DatePickerCallback() {
                public void onTimeSelected(long timestamp) {
                    createAndSaveTask(timestamp);
                }
            });
        }
    }, "直接发送", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            // 验证
            if (massSendTargetWxids.isEmpty()) {
                toast("请先选择发送目标！");
                return; 
            }
            if (massSendType == SEND_TYPE_TEXT && TextUtils.isEmpty(massSendTextContent)) {
                toast("请输入文本内容！");
                return;
            }
            if (massSendType != SEND_TYPE_TEXT && massSendType != SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
                toast("请选择要发送的文件！");
                return;
            }
            if (massSendType == SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
                toast("请选择语音文件(.silk)！");
                return;
            }
            
            try {
                massSendInterval = Long.parseLong(intervalEdit.getText().toString());
                massSendMediaInterval = Long.parseLong(mediaIntervalEdit.getText().toString());
            } catch(Exception e) {}
            
            // 直接发送
            toast("🚀 开始立即发送...");
            executeImmediateSend();
        }
    }, "关闭", null);
    
    dialog.show();
}

/**
 * 创建并保存新任务
 */
private void createAndSaveTask(long planTime) {
    // 生成任务ID
    String taskId = UUID.randomUUID().toString();
    
    // 构建任务对象
    JSONObject task = new JSONObject();
    task.put("taskId", taskId);
    task.put("planTime", planTime);
    task.put("type", massSendType);
    task.put("content", massSendTextContent);
    task.put("interval", massSendInterval);
    task.put("mediaInterval", massSendMediaInterval);
    task.put("targets", new ArrayList<>(massSendTargetWxids));
    task.put("medias", new ArrayList<>(massSendMediaPaths));
    task.put("status", "pending");
    task.put("createdTime", System.currentTimeMillis());
    
    // 保存到内存
    scheduledTasks.put(taskId, task);
    saveAllTasks();
    
    // 启动定时器
    long now = System.currentTimeMillis();
    long delay = planTime - now;
    
    if (delay > 0) {
        scheduleTaskWithPrecision(taskId, task, delay);
        toast("✅ 任务已创建！\n将在 " + formatTimeWithSeconds(planTime) + " 执行");
    } else {
        toast("⚠️ 时间已过，请选择未来时间");
        scheduledTasks.remove(taskId);
    }
}

/**
 * 任务创建完成后刷新任务列表（如果对话框已打开）
 */
private void notifyTaskCreated(final String taskId) {
    // 可以在这里添加全局任务监听器来实时刷新
    // 目前通过 saveAllTasks() 已经保存到存储
    log("新任务已创建: " + taskId);
}

/**
 * 立即执行发送（不使用定时器）
 */
private void executeImmediateSend() {
    final List<String> targets = new ArrayList<>(massSendTargetWxids);
    final int type = massSendType;
    final String content = massSendTextContent;
    final List<String> mediaPaths = new ArrayList<>(massSendMediaPaths);
    final long interval = massSendInterval;
    final long mediaInterval = massSendMediaInterval;
    
    new Thread(new Runnable() {
        public void run() {
            int success = 0;
            int fail = 0;
            
            for (int i = 0; i < targets.size(); i++) {
                String target = targets.get(i);
                String name = getContactName(target);
                
                try {
                    if (type == SEND_TYPE_TEXT) {
                        String finalContent = content.replace("%friendName%", name);
                        sendText(target, finalContent);
                    } else {
                        for (String path : mediaPaths) {
                            File f = new File(path);
                            if (f.exists()) {
                                switch (type) {
                                    case SEND_TYPE_IMAGE: sendImage(target, path); break;
                                    case SEND_TYPE_VIDEO: sendVideo(target, path); break;
                                    case SEND_TYPE_EMOJI: sendEmoji(target, path); break;
                                    case SEND_TYPE_FILE: shareFile(target, f.getName(), path, ""); break;
                                    case SEND_TYPE_VOICE: sendVoice(target, path); break;
                                }
                                if (mediaInterval > 0) Thread.sleep(mediaInterval * 1000);
                            }
                        }
                    }
                    success++;
                } catch (Exception e) {
                    fail++;
                    log("发送失败: " + target + " - " + e.getMessage());
                }
                
                if (i < targets.size() - 1 && interval > 0) {
                    try { Thread.sleep(interval * 1000); } catch (Exception e) {}
                }
            }
            
            final String report = "成功: " + success + ", 失败: " + fail;
            notify("发送完成", report);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    toast("✅ 发送完成\n" + report);
                }
            });
        }
    }).start();
}

/**
 * 更新任务统计显示
 */
private void updateStatsText(TextView tv) {
    if (tv == null) return;
    
    int pending = 0;
    int running = 0;
    int completed = 0;
    
    for (JSONObject task : scheduledTasks.values()) {
        String status = task.getString("status");
        if ("pending".equals(status)) pending++;
        else if ("running".equals(status)) running++;
        else if ("completed".equals(status)) completed++;
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append("待执行: ").append(pending).append(" 个\n");
    sb.append("执行中: ").append(running).append(" 个\n");
    sb.append("已完成: ").append(completed).append(" 个\n");
    sb.append("━━━━━━━━━━━━━\n");
    sb.append("总计任务: ").append(scheduledTasks.size()).append(" 个");
    
    tv.setText(sb.toString());
    tv.setTextColor(Color.parseColor("#2E7D32"));
}

// ==========================================
// ========== 📋 任务列表管理 ==========
// ==========================================

private void showTaskListDialog() {
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    root.addView(createSectionTitle("📋 定时任务列表"));

    if (scheduledTasks.isEmpty()) {
        root.addView(createPromptText("暂无定时任务"));
    } else {
        // 按计划时间排序
        List<JSONObject> sortedTasks = new ArrayList<>(scheduledTasks.values());
        Collections.sort(sortedTasks, new java.util.Comparator<JSONObject>() {
            public int compare(JSONObject t1, JSONObject t2) {
                long time1 = t1.getLongValue("planTime");
                long time2 = t2.getLongValue("planTime");
                return Long.compare(time1, time2);
            }
        });

        final ListView taskListView = new ListView(getTopActivity());
        setupListViewTouchForScroll(taskListView);
        
        final List<String> taskIds = new ArrayList<>();
        final List<String> taskDisplays = new ArrayList<>();
        
        for (JSONObject task : sortedTasks) {
            String taskId = task.getString("taskId");
            long planTime = task.getLongValue("planTime");
            String status = task.getString("status");
            int targetCount = task.getJSONArray("targets").size();
            int type = task.getIntValue("type");
            
            String typeStr = "";
            switch (type) {
                case SEND_TYPE_TEXT: typeStr = "📝文本"; break;
                case SEND_TYPE_IMAGE: typeStr = "🖼️图片"; break;
                case SEND_TYPE_VIDEO: typeStr = "🎬视频"; break;
                case SEND_TYPE_FILE: typeStr = "📁文件"; break;
                case SEND_TYPE_EMOJI: typeStr = "😊表情"; break;
                case SEND_TYPE_VOICE: typeStr = "🎤语音"; break;
            }
            
            String statusEmoji = "";
            if ("pending".equals(status)) statusEmoji = "⏳";
            else if ("running".equals(status)) statusEmoji = "🔄";
            else if ("completed".equals(status)) statusEmoji = "✅";
            else if ("expired".equals(status)) statusEmoji = "❌";
            
            String display = statusEmoji + " " + formatTimeWithSeconds(planTime) + "\n" +
                           typeStr + " | " + targetCount + " 个目标 | " + status;
            
            taskIds.add(taskId);
            taskDisplays.add(display);
        }
        
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), 
            android.R.layout.simple_list_item_1, taskDisplays);
        taskListView.setAdapter(adapter);
        
        // 高度限制
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            dpToPx(Math.min(taskDisplays.size() * 80, 400))
        );
        params.setMargins(0, 16, 0, 16);
        taskListView.setLayoutParams(params);
        
        // 点击操作菜单
        taskListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String taskId = taskIds.get(position);
                JSONObject task = scheduledTasks.get(taskId);
                if (task == null) return;
                
                showTaskOperationMenu(taskId, task, adapter, taskIds, taskDisplays);
            }
        });
        
        root.addView(taskListView);
    }

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "📋 任务管理", scrollView, "关闭", null, null, null, null, null);
    dialog.show();
}

/**
 * 刷新任务列表
 */
private void refreshTaskList(final ListView taskListView, final ArrayAdapter<String> adapter, 
                             final List<String> taskIds, final List<String> taskDisplays) {
    try {
        // 重新加载任务数据
        String tasksJson = getString(CONFIG_KEY, KEY_TASKS, "{}");
        JSONObject allTasks = JSON.parseObject(tasksJson);
        
        // 更新全局变量
        scheduledTasks.clear();
        if (allTasks != null) {
            scheduledTasks.putAll(allTasks);
        }
        
        // 更新列表数据
        taskIds.clear();
        taskDisplays.clear();
        
        List<JSONObject> sortedTasks = new ArrayList<>(scheduledTasks.values());
        Collections.sort(sortedTasks, new java.util.Comparator<JSONObject>() {
            public int compare(JSONObject t1, JSONObject t2) {
                long time1 = t1.getLongValue("planTime");
                long time2 = t2.getLongValue("planTime");
                return Long.compare(time1, time2);
            }
        });
        
        for (JSONObject task : sortedTasks) {
            String taskId = task.getString("taskId");
            long planTime = task.getLongValue("planTime");
            String status = task.getString("status");
            int targetCount = task.getJSONArray("targets").size();
            int type = task.getIntValue("type");
            
            String typeStr = "";
            switch (type) {
                case SEND_TYPE_TEXT: typeStr = "📝文本"; break;
                case SEND_TYPE_IMAGE: typeStr = "🖼️图片"; break;
                case SEND_TYPE_VIDEO: typeStr = "🎬视频"; break;
                case SEND_TYPE_FILE: typeStr = "📁文件"; break;
                case SEND_TYPE_EMOJI: typeStr = "😊表情"; break;
                case SEND_TYPE_VOICE: typeStr = "🎤语音"; break;
            }
            
            String statusEmoji = "";
            if ("pending".equals(status)) statusEmoji = "⏳";
            else if ("running".equals(status)) statusEmoji = "🔄";
            else if ("completed".equals(status)) statusEmoji = "✅";
            else if ("expired".equals(status)) statusEmoji = "❌";
            
            String display = statusEmoji + " " + formatTimeWithSeconds(planTime) + "\n" +
                           typeStr + " | " + targetCount + " 个目标 | " + status;
            
            taskIds.add(taskId);
            taskDisplays.add(display);
        }
        
        // 刷新ListView
        adapter.notifyDataSetChanged();
        
    } catch (Exception e) {
        log("刷新任务列表失败: " + e.getMessage());
    }
}

/**
 * 任务操作菜单
 */
private void showTaskOperationMenu(final String taskId, JSONObject task, 
                                   final ArrayAdapter<String> adapter,
                                   final List<String> taskIds,
                                   final List<String> taskDisplays) {
    String status = task.getString("status");
    String[] options;
    
    if ("pending".equals(status)) {
        options = new String[]{"✏️ 编辑任务", "🛑 取消此任务", "▶️ 立即执行"};
    } else if ("completed".equals(status)) {
        int success = task.getIntValue("successCount");
        int fail = task.getIntValue("failCount");
        options = new String[]{"📊 查看结果 (成功" + success + " 失败" + fail + ")", "🗑️ 删除记录"};
    } else {
        options = new String[]{"🗑️ 删除记录"};
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("任务操作");
    builder.setItems(options, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String selectedOption = options[which];
            
            if (selectedOption.contains("编辑")) {
                editTask(taskId);
            } else if (selectedOption.contains("取消")) {
                cancelTask(taskId, adapter, taskIds, taskDisplays);
            } else if (selectedOption.contains("立即执行")) {
                executeTaskNow(taskId);
            } else if (selectedOption.contains("删除")) {
                deleteTask(taskId, adapter, taskIds, taskDisplays);
            } else if (selectedOption.contains("查看结果")) {
                showTaskResult(task);
            }
        }
    });
    AlertDialog menuDialog = builder.create();
    setupUnifiedDialog(menuDialog);
    menuDialog.show();
}

/**
 * 编辑任务
 */
private void editTask(String taskId) {
    // 简化版：提示用户删除后重建
    toast("请删除任务后重新创建");
}

/**
 * 取消任务
 */
private void cancelTask(String taskId, ArrayAdapter<String> adapter, List<String> taskIds, List<String> taskDisplays) {
    cancelTaskTimer(taskId);
    scheduledTasks.remove(taskId);
    saveAllTasks();
    
    int pos = taskIds.indexOf(taskId);
    if (pos >= 0) {
        taskIds.remove(pos);
        taskDisplays.remove(pos);
        adapter.notifyDataSetChanged();
    }
    
    toast("任务已取消");
}

/**
 * 立即执行任务
 */
private void executeTaskNow(String taskId) {
    JSONObject task = scheduledTasks.get(taskId);
    if (task != null) {
        task.put("planTime", System.currentTimeMillis());
        saveAllTasks();
        executeTaskSend(taskId);
    }
}

/**
 * 删除任务记录
 */
private void deleteTask(String taskId, ArrayAdapter<String> adapter, List<String> taskIds, List<String> taskDisplays) {
    scheduledTasks.remove(taskId);
    saveAllTasks();
    
    int pos = taskIds.indexOf(taskId);
    if (pos >= 0) {
        taskIds.remove(pos);
        taskDisplays.remove(pos);
        adapter.notifyDataSetChanged();
    }
    
    toast("记录已删除");
}

/**
 * 查看任务结果
 */
private void showTaskResult(JSONObject task) {
    int success = task.getIntValue("successCount");
    int fail = task.getIntValue("failCount");
    long completedTime = task.getLongValue("completedTime");
    
    String msg = "发送结果:\n成功: " + success + "\n失败: " + fail + 
                 "\n完成时间: " + formatTimeWithSeconds(completedTime);
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("📊 任务报告");
    builder.setMessage(msg);
    builder.setPositiveButton("确定", null);
    builder.show();
}

// ==========================================
// ========== 🕐 时间选择器 (支持秒) ==========
// ==========================================

interface DatePickerCallback {
    void onTimeSelected(long timestamp);
}

private void showDateTimePickerWithSeconds(final DatePickerCallback callback) {
    final Calendar calendar = Calendar.getInstance();
    
    // 1. 日期选择
    DatePickerDialog dateDialog = new DatePickerDialog(getTopActivity(), new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, final int year, final int month, final int dayOfMonth) {
            // 2. 时间选择 (小时和分钟)
            TimePickerDialog timeDialog = new TimePickerDialog(getTopActivity(), new TimePickerDialog.OnTimeSetListener() {
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    // 3. 秒选择
                    showSecondPicker(calendar, year, month, dayOfMonth, hourOfDay, minute, callback);
                }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timeDialog.show();
        }
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    
    dateDialog.show();
}

/**
 * 秒选择器
 */
private void showSecondPicker(final Calendar calendar, int year, int month, int day, int hour, int minute, final DatePickerCallback callback) {
    final String[] seconds = new String[60];
    for (int i = 0; i < 60; i++) {
        seconds[i] = String.format("%02d 秒", i);
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择秒数");
    builder.setItems(seconds, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            calendar.set(year, month, day, hour, minute, which);
            long ts = calendar.getTimeInMillis();
            
            if (ts < System.currentTimeMillis()) {
                toast("⚠️ 不能选择过去的时间");
            } else {
                callback.onTimeSelected(ts);
            }
        }
    });
    builder.show();
}

private String formatTimeWithSeconds(long ts) {
    if (ts <= 0) return "未设置";
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
}

private String formatTime(long ts) {
    if (ts <= 0) return "未设置";
    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(ts));
}

// ==========================================
// ========== 🏷️ 标签管理功能 (移植自群发助手) ==========
// ==========================================

private void showLabelManagementDialog(final Runnable onUpdateCallback) {
    try {
        String labelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
        JSONObject rawJson = JSON.parseObject(labelsJson);
        final Map<String, Object> labelsMap = (rawJson != null) ? rawJson : new JSONObject();

        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout root = new LinearLayout(getTopActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scrollView.addView(root);

        // 新建标签按钮
        Button createLabelBtn = new Button(getTopActivity());
        createLabelBtn.setText("➕ 添加新标签");
        styleMediaSelectionButton(createLabelBtn);
        GradientDrawable btnBg = (GradientDrawable) createLabelBtn.getBackground();
        btnBg.setColor(Color.parseColor("#E3F2FD"));
        btnBg.setStroke(2, Color.parseColor("#90CAF9"));
        createLabelBtn.setTextColor(Color.parseColor("#1976D2"));
        
        // 标签列表组件 - 在按钮点击前就初始化
        final ListView labelList = new ListView(getTopActivity());
        setupListViewTouchForScroll(labelList);
        
        final List<String> labelNames = new ArrayList<>(labelsMap.keySet());
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, labelNames);
        labelList.setAdapter(adapter);
        
        // 根据是否有标签设置ListView高度
        int count = labelNames.size();
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            dpToPx(Math.max(count * 50, 100))
        ); 
        listParams.setMargins(0, 16, 0, 16);
        labelList.setLayoutParams(listParams);
        
        // 如果没有标签，显示提示；否则显示列表
        if (labelNames.isEmpty()) {
            TextView noLabelTip = createPromptText("暂无标签，请先添加。");
            root.addView(noLabelTip);
        } else {
            root.addView(labelList);
        }
        
        // 刷新标签列表的方法
        final Runnable refreshLabelListRunnable = new Runnable() {
            public void run() {
                try {
                    // 重新从存储加载标签
                    String newLabelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
                    JSONObject newRawJson = JSON.parseObject(newLabelsJson);
                    Map<String, Object> newLabelsMap = (newRawJson != null) ? newRawJson : new JSONObject();
                    
                    // 更新列表数据
                    labelNames.clear();
                    labelNames.addAll(newLabelsMap.keySet());
                    
                    // 刷新ListView
                    adapter.notifyDataSetChanged();
                    
                    // 重新加载标签管理对话框以更新UI布局
                    showLabelManagementDialog(onUpdateCallback);
                    
                } catch (Exception e) {
                    log("刷新标签列表失败: " + e.getMessage());
                }
            }
        };
        
        createLabelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCreateLabelDialog(labelsMap, refreshLabelListRunnable);
            }
        });
        root.addView(createLabelBtn);
        root.addView(createPromptText("点击上方按钮，选择好友/群聊并命名以创建新标签。"));

        // 标签列表标题
        TextView listTitle = createSectionTitle("📂 已有标签列表");
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) listTitle.getLayoutParams();
        lp.setMargins(0, 32, 0, 16);
        root.addView(listTitle);
        
        // 点击标签弹出操作菜单
        labelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String labelName = labelNames.get(position);
                String currentLabelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
                JSONObject currentMap = JSON.parseObject(currentLabelsJson);
                final JSONArray labelWxidsJson = currentMap != null ? (JSONArray) currentMap.get(labelName) : new JSONArray();
                
                final List<String> labelWxids = new ArrayList<>();
                if (labelWxidsJson != null) {
                    for(int i=0; i<labelWxidsJson.size(); i++) {
                        labelWxids.add(labelWxidsJson.getString(i));
                    }
                }

                String[] options = {
                    "👁️ 管理成员 (查看/删除)",
                    "📥 导入到当前发送列表",
                    "➕ 追加成员 (选择好友/群聊)",
                    "🗑️ 删除此标签"
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
                builder.setTitle("操作标签: " + labelName);
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch(which) {
                            case 0:
                                showLabelMembers(labelName, labelWxids);
                                break;
                            case 1:
                                if (labelWxids.isEmpty()) {
                                    toast("此标签没有成员");
                                    return;
                                }
                                int importedCount = 0;
                                for(String id : labelWxids) {
                                    if(!massSendTargetWxids.contains(id)) {
                                        massSendTargetWxids.add(id);
                                        importedCount++;
                                    }
                                }
                                if (onUpdateCallback != null) onUpdateCallback.run();
                                toast("✅ 已导入 " + importedCount + " 个新目标");
                                break;
                            case 2:
                                final Set<String> tempSet = new HashSet<>(labelWxids);
                                showTargetCategoryDialog(tempSet, new Runnable() {
                                    public void run() {
                                        labelsMap.put(labelName, new ArrayList<>(tempSet));
                                        putString(CONFIG_KEY, KEY_LABELS, JSON.toJSONString(labelsMap));
                                        toast("✅ 标签 [" + labelName + "] 更新成功");
                                    }
                                });
                                break;
                            case 3:
                                labelsMap.remove(labelName);
                                putString(CONFIG_KEY, KEY_LABELS, JSON.toJSONString(labelsMap));
                                toast("标签已删除");
                                labelNames.remove(position);
                                adapter.notifyDataSetChanged();
                                break;
                        }
                    }
                });
                
                AlertDialog menuDialog = builder.create();
                setupUnifiedDialog(menuDialog);
                menuDialog.show();
            }
        });

        AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "🏷️ 标签分组管理", scrollView, "关闭", null, null, null, null, null);
        dialog.show();

    } catch (Exception e) {
        toast("无法打开标签管理: " + e.getMessage());
        e.printStackTrace();
    }
}

/**
 * 刷新标签列表
 */
private void refreshLabelList(final List<String> labelNames, final ArrayAdapter<String> adapter) {
    try {
        // 重新从存储加载标签
        String labelsJson = getString(CONFIG_KEY, KEY_LABELS, "{}");
        JSONObject rawJson = JSON.parseObject(labelsJson);
        Map<String, Object> labelsMap = (rawJson != null) ? rawJson : new JSONObject();
        
        // 更新列表数据
        labelNames.clear();
        labelNames.addAll(labelsMap.keySet());
        
        // 刷新ListView
        adapter.notifyDataSetChanged();
        
    } catch (Exception e) {
        log("刷新标签列表失败: " + e.getMessage());
    }
}

/**
 * 创建新标签
 */
private void showCreateLabelDialog(final Map<String, Object> labelsMap, final Runnable onCreated) {
    final Set<String> newLabelMembers = new HashSet<>();
    
    final ScrollView scrollView = new ScrollView(getTopActivity());
    final LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    scrollView.addView(root);
    
    root.addView(createSectionTitle("1. 添加标签成员"));
    
    final TextView countTv = createPromptText("当前已选: 0 人");
    countTv.setTextSize(14);
    countTv.setTextColor(Color.parseColor("#333333"));
    root.addView(countTv);
    
    LinearLayout btnRow = new LinearLayout(getTopActivity());
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    
    Button addFriendBtn = new Button(getTopActivity());
    addFriendBtn.setText("👤 添加好友");
    styleUtilityButton(addFriendBtn);
    LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    p1.setMargins(0,0,8,0);
    addFriendBtn.setLayoutParams(p1);
    
    Button addGroupBtn = new Button(getTopActivity());
    addGroupBtn.setText("🏠 添加群聊");
    styleUtilityButton(addGroupBtn);
    LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    p2.setMargins(8,0,0,0);
    addGroupBtn.setLayoutParams(p2);
    
    btnRow.addView(addFriendBtn);
    btnRow.addView(addGroupBtn);
    root.addView(btnRow);
    
    final Runnable updateCount = new Runnable() {
        public void run() {
            countTv.setText("当前已选: " + newLabelMembers.size() + " 人");
        }
    };
    
    addFriendBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showFriendSelectionDialog(newLabelMembers, updateCount);
        }
    });
    
    addGroupBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showGroupSelectionDialog(newLabelMembers, updateCount);
        }
    });
    
    TextView title2 = createSectionTitle("2. 命名并保存");
    LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams) title2.getLayoutParams();
    lp2.setMargins(0, 32, 0, 16);
    root.addView(title2);
    
    final EditText nameEdit = createStyledEditText("输入标签名称 (如: 家人, 客户A组)", "");
    root.addView(nameEdit);
    
    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "➕ 新建标签", scrollView, "💾 保存标签", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String name = nameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                toast("请输入标签名称");
                return; 
            }
            if (newLabelMembers.isEmpty()) {
                toast("请至少选择一个成员");
                return;
            }
            if (labelsMap.containsKey(name)) {
                toast("标签名 [" + name + "] 已存在，请换一个");
                return;
            }
            
            labelsMap.put(name, new ArrayList<>(newLabelMembers));
            putString(CONFIG_KEY, KEY_LABELS, JSON.toJSONString(labelsMap));
            toast("✅ 标签 [" + name + "] 创建成功！");
            
            if (onCreated != null) onCreated.run();
        }
    }, "取消", null, null, null);
    
    dialog.show();
}

/**
 * 显示标签成员详情
 */
private void showLabelMembers(final String labelName, final List<String> wxids) {
    showLoadingDialog("正在加载成员...", "请稍候...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    LinearLayout layout = new LinearLayout(getTopActivity());
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(32, 32, 32, 32);
                    
                    if (wxids.isEmpty()) {
                        layout.addView(createPromptText("此标签暂无成员"));
                    } else {
                        TextView tip = createPromptText("👇 点击成员可将其移除");
                        tip.setTextColor(Color.parseColor("#FF9800"));
                        layout.addView(tip);

                        final ListView listView = new ListView(getTopActivity());
                        setupListViewTouchForScroll(listView);
                        
                        final List<String> displayList = new ArrayList<>();
                        final List<String> idList = new ArrayList<>(wxids);
                        
                        for (String wxid : idList) {
                             displayList.add(formatMemberDisplay(wxid));
                        }
                        
                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            getTopActivity(), 
                            android.R.layout.simple_list_item_1, 
                            displayList
                        );
                        listView.setAdapter(adapter);
                        
                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                                final String wxidToRemove = idList.get(position);
                                String name = getContactName(wxidToRemove);
                                
                                AlertDialog.Builder confirm = new AlertDialog.Builder(getTopActivity());
                                confirm.setTitle("删除成员?");
                                confirm.setMessage("确定要从标签 [" + labelName + "] 中移除:\n" + name + " 吗?");
                                confirm.setPositiveButton("🗑️ 移除", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        idList.remove(position);
                                        displayList.remove(position);
                                        adapter.notifyDataSetChanged();
                                        
                                        try {
                                            String jsonStr = getString(CONFIG_KEY, KEY_LABELS, "{}");
                                            JSONObject map = JSON.parseObject(jsonStr);
                                            if (map != null) {
                                                map.put(labelName, idList);
                                                putString(CONFIG_KEY, KEY_LABELS, JSON.toJSONString(map));
                                                toast("已移除");
                                            }
                                        } catch(Exception e) {
                                            toast("保存失败: " + e.getMessage());
                                        }
                                    }
                                });
                                confirm.setNegativeButton("取消", null);
                                AlertDialog confirmDialog = confirm.create();
                                setupUnifiedDialog(confirmDialog);
                                confirmDialog.show();
                            }
                        });
                        
                        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 
                            dpToPx(400)
                        );
                        listView.setLayoutParams(listParams);
                        layout.addView(listView);
                    }
                    
                    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "👁️ [" + labelName + "] 成员列表", layout, "关闭", null, null, null, null, null);
                    dialog.show();
                }
            });
        }
    });
}

/**
 * 格式化成员显示文本
 */
private String formatMemberDisplay(String wxid) {
    String displayText = "";
    if (wxid.endsWith("@chatroom")) {
        String gName = getGroupName(wxid);
        displayText = "🏠 " + gName;
    } else {
        String fName = "未知"; 
        String fRemark = "";
        boolean found = false;
        if (sCachedFriendList != null) {
            for (Object obj : sCachedFriendList) {
                FriendInfo f = (FriendInfo) obj;
                if (f.getWxid().equals(wxid)) {
                    fName = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                    fRemark = f.getRemark();
                    found = true;
                    break;
                }
            }
        }
        if (!found) fName = getFriendDisplayName(wxid);
        String finalName = !TextUtils.isEmpty(fRemark) ? fName + " (" + fRemark + ")" : fName;
        displayText = "👤 " + finalName;
    }
    return displayText;
}

// ==========================================
// ========== 👥 目标选择功能 (改进版) ==========
// ==========================================

private void showTargetCategoryDialog(final Set<String> targetSet, final Runnable onFinish) {
    final String[] items = new String[]{"👤 选择好友", "🏠 选择群聊"};
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("请选择目标类型");
    builder.setItems(items, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
                showFriendSelectionDialog(targetSet, onFinish);
            } else {
                showGroupSelectionDialog(targetSet, onFinish);
            }
        }
    });
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog((AlertDialog)d);
        }
    });
    dialog.show();
}

/**
 * 选择好友 (改进版：显示昵称+备注)
 */
private void showFriendSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载好友列表", "正在获取好友...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    List<String> names = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    
                    if (sCachedFriendList != null) {
                        for (int i=0; i<sCachedFriendList.size(); i++) {
                            FriendInfo f = (FriendInfo) sCachedFriendList.get(i);
                            
                            String nickname = TextUtils.isEmpty(f.getNickname()) ? "未知昵称" : f.getNickname();
                            String remark = f.getRemark();
                            String displayName = !TextUtils.isEmpty(remark) ? nickname + " (" + remark + ")" : nickname;
                            String wxid = f.getWxid();
                            
                            // 显示格式：👤 昵称 (备注) - wxid
                            names.add("👤 " + displayName + " - " + wxid);
                            ids.add(wxid);
                        }
                    }
                    
                    showMultiSelectDialog("选择好友 (支持搜索)", names, ids, targetSet, "搜索昵称/备注...", new Runnable() {
                        public void run() {
                            if (onFinish != null) onFinish.run();
                        }
                    }, null);
                }
            });
        }
    });
}

/**
 * 选择群聊 (改进版：显示群名称)
 */
private void showGroupSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载群聊列表", "正在获取群聊...", new Runnable() {
        public void run() {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    List<String> names = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    
                    if (sCachedGroupList != null) {
                        for (int i=0; i<sCachedGroupList.size(); i++) {
                            GroupInfo g = (GroupInfo) sCachedGroupList.get(i);
                            String name = !TextUtils.isEmpty(g.getName()) ? g.getName() : "未命名群聊";
                            String roomId = g.getRoomId();
                            
                            // 显示格式：🏠 群名称 - roomid
                            names.add("🏠 " + name + " - " + roomId);
                            ids.add(roomId);
                        }
                    }
                    
                    showMultiSelectDialog("选择群聊 (支持搜索)", names, ids, targetSet, "搜索群名...", new Runnable() {
                        public void run() {
                            if (onFinish != null) onFinish.run();
                        }
                    }, null);
                }
            });
        }
    });
}

/**
 * 通用多选列表对话框 (移植自群发助手)
 */
private void showMultiSelectDialog(String title, List allItems, List idList, Set selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final Set tempSelected = new HashSet(selectedIds);
        ScrollView scrollView = new ScrollView(getTopActivity());
        LinearLayout mainLayout = new LinearLayout(getTopActivity());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 24, 24, 24);
        mainLayout.setBackgroundColor(Color.parseColor("#FAFBF9"));
        scrollView.addView(mainLayout);
        
        final EditText searchEditText = createStyledEditText(searchHint, "");
        searchEditText.setSingleLine(true);
        mainLayout.addView(searchEditText);
        
        final ListView listView = new ListView(getTopActivity());
        setupListViewTouchForScroll(listView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(300));
        listView.setLayoutParams(listParams);
        mainLayout.addView(listView);
        
        final List currentFilteredIds = new ArrayList();
        final List currentFilteredNames = new ArrayList();
        
        final Runnable updateListRunnable = new Runnable() {
            public void run() {
                String searchText = searchEditText.getText().toString().toLowerCase();
                currentFilteredIds.clear();
                currentFilteredNames.clear();
                for (int i = 0; i < allItems.size(); i++) {
                    String id = (String) idList.get(i);
                    String name = (String) allItems.get(i);
                    if (searchText.isEmpty() || name.toLowerCase().contains(searchText) || id.toLowerCase().contains(searchText)) {
                        currentFilteredIds.add(id);
                        currentFilteredNames.add(name);
                    }
                }
                ArrayAdapter adapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredNames);
                listView.setAdapter(adapter);
                listView.clearChoices();
                for (int j = 0; j < currentFilteredIds.size(); j++) {
                    listView.setItemChecked(j, tempSelected.contains(currentFilteredIds.get(j)));
                }
                if (updateList != null) updateList.run();
            }
        };
        
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
                if (updateList != null) updateList.run();
            }
        });
        
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                updateListRunnable.run();
            }
        });
        
        final DialogInterface.OnClickListener fullSelectListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                boolean allSelected = true;
                for(Object id : currentFilteredIds) {
                    if(!tempSelected.contains(id)) { allSelected = false; break; }
                }
                
                if (allSelected) {
                    for(Object id : currentFilteredIds) tempSelected.remove(id);
                } else {
                    for(Object id : currentFilteredIds) tempSelected.add(id);
                }
                updateListRunnable.run();
            }
        };
        
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, "✅ 确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                selectedIds.clear();
                selectedIds.addAll(tempSelected);
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            }
        }, "❌ 取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }, "全选/反选", fullSelectListener);
        
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
             public void onShow(DialogInterface d) {
                 setupUnifiedDialog((AlertDialog)d);
                 Button neutral = ((AlertDialog)d).getButton(AlertDialog.BUTTON_NEUTRAL);
                 neutral.setOnClickListener(new View.OnClickListener() {
                     public void onClick(View v) {
                         fullSelectListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
                     }
                 });
             }
        });
        
        dialog.show();
        updateListRunnable.run();
    } catch (Exception e) {
        toast("弹窗失败: " + e.getMessage());
        e.printStackTrace();
    }
}

// ==========================================
// ========== 📁 媒体选择功能 (移植自群发助手) ==========
// ==========================================

final String DEFAULT_LAST_FOLDER_SP_AUTO = "last_folder_for_media_auto";
final String ROOT_FOLDER = "/storage/emulated/0";

interface MediaSelectionCallback {
    void onSelected(ArrayList<String> selectedFiles);
}

void browseFolderForSelectionAuto(final File startFolder, final String wantedExtFilter, final String currentSelection, final MediaSelectionCallback callback, final boolean allowFolderSelect) {
    putString(DEFAULT_LAST_FOLDER_SP_AUTO, startFolder.getAbsolutePath());
    ArrayList<String> names = new ArrayList<String>();
    final ArrayList<Object> items = new ArrayList<Object>();

    if (!startFolder.getAbsolutePath().equals(ROOT_FOLDER)) {
        names.add("⬆ 上一级");
        items.add(startFolder.getParentFile());
    }

    File[] subs = startFolder.listFiles();
    if (subs != null) {
        Arrays.sort(subs);
        for (int i = 0; i < subs.length; i++) {
            File f = subs[i];
            if (f.isDirectory()) {
                names.add("📁 " + f.getName());
                items.add(f);
            }
        }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());
    final ListView list = new ListView(getTopActivity());
    list.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, names));
    builder.setView(list);

    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            dialog.dismiss();
            Object selected = items.get(pos);
            if (selected instanceof File) {
                File sel = (File) selected;
                if (sel.isDirectory()) {
                    browseFolderForSelectionAuto(sel, wantedExtFilter, currentSelection, callback, allowFolderSelect);
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
    final AlertDialog finalDialog = builder.create();
    finalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(finalDialog);
        }
    });
    finalDialog.show();
}

void scanFilesMulti(final File folder, final String extFilter, final String currentSelection, final MediaSelectionCallback callback) {
    final ArrayList<String> names = new ArrayList<String>();
    final ArrayList<File> files = new ArrayList<File>();

    File[] list = folder.listFiles();
    if (list != null) {
        Arrays.sort(list);
        String[] exts = TextUtils.isEmpty(extFilter) ? new String[0] : extFilter.split(",");
        for (int i = 0; i < list.length; i++) {
            File f = list[i];
            if (f.isFile()) {
                boolean matches = exts.length == 0;
                for (int j = 0; j < exts.length; j++) {
                    String e = exts[j];
                    if (f.getName().toLowerCase().endsWith(e.trim().toLowerCase())) {
                        matches = true;
                        break;
                    }
                }
                if (matches) {
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

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件（可多选）：" + folder.getAbsolutePath());
    final ListView listView = new ListView(getTopActivity());
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, names));
    builder.setView(listView);

    builder.setPositiveButton("确认选择", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            ArrayList<String> selectedPaths = new ArrayList<String>();
            for (int i = 0; i < names.size(); i++) {
                if (listView.isItemChecked(i)) {
                    selectedPaths.add(files.get(i).getAbsolutePath());
                }
            }
            callback.onSelected(selectedPaths);
        }
    });

    builder.setNegativeButton("取消", null);
    final AlertDialog finalDialog = builder.create();
    finalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(finalDialog);
        }
    });
    finalDialog.show();
}

private Object[] getMediaSelectTagForMassSend(int type) {
    String extFilter = "";
    switch (type) {
        case SEND_TYPE_IMAGE: extFilter = ".jpg,.png,.jpeg,.gif,.bmp"; break;
        case SEND_TYPE_VIDEO: extFilter = ".mp4"; break;
        case SEND_TYPE_EMOJI: extFilter = ".gif"; break;
        case SEND_TYPE_FILE: extFilter = ""; break;
        case SEND_TYPE_VOICE: extFilter = ".silk"; break;
    }
    return new Object[]{extFilter, false, false, true};
}

// ==========================================
// ========== 👤 联系人和群聊辅助功能 ==========
// ==========================================

/**
 * 获取联系人名字（支持备注）
 */
private String getContactName(String wxid) {
    try {
        if (wxid.endsWith("@chatroom")) {
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            for (Object obj : sCachedGroupList) {
                GroupInfo g = (GroupInfo) obj;
                if (g.getRoomId().equals(wxid)) return g.getName();
            }
        } else {
            return getFriendDisplayName(wxid);
        }
    } catch(Exception e) {}
    return wxid;
}

/**
 * 获取好友显示名称（优先备注）
 */
private String getFriendDisplayName(String friendWxid) {
    try {
        if (sCachedFriendList == null) sCachedFriendList = getFriendList();
        if (sCachedFriendList != null) {
            for (int i = 0; i < sCachedFriendList.size(); i++) {
                FriendInfo friendInfo = (FriendInfo) sCachedFriendList.get(i);
                if (friendWxid.equals(friendInfo.getWxid())) {
                    String remark = friendInfo.getRemark();
                    if (!TextUtils.isEmpty(remark)) return remark;
                    String nickname = friendInfo.getNickname();
                    return TextUtils.isEmpty(nickname) ? friendWxid : nickname;
                }
            }
        }
    } catch (Exception e) {
    }
    return getFriendName(friendWxid);
}

/**
 * 获取群聊名称
 */
private String getGroupName(String groupWxid) {
    try {
        if (sCachedGroupList == null) sCachedGroupList = getGroupList();
        if (sCachedGroupList != null) {
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                if (groupWxid.equals(groupInfo.getRoomId())) return groupInfo.getName();
            }
        }
    } catch (Exception e) {
    }
    return "未知群聊";
}

/**
 * 更新目标数量显示
 */
private void updateTargetCountText(TextView tv) {
    if (tv != null) {
        int count = massSendTargetWxids.size();
        tv.setText("当前已选: " + count + " 个目标 (好友/群聊混合)");
    }
}

// ==========================================
// ========== 🎨 UI 样式方法 ==========
// ==========================================

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
    layout.setBackground(createGradientDrawable("#FFFFFF", 32));
    try { layout.setElevation(8); } catch (Exception e) {}
    return layout;
}

private GradientDrawable createGradientDrawable(String colorStr, int radius) {
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(radius);
    shape.setColor(Color.parseColor(colorStr));
    return shape;
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
    return editText;
}

private void styleUtilityButton(Button button) {
    button.setTextColor(Color.parseColor("#4A90E2"));
    GradientDrawable shape = new GradientDrawable();
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

private void styleMediaSelectionButton(Button button) {
    button.setTextColor(Color.parseColor("#3B82F6"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(20);
    shape.setColor(Color.parseColor("#EFF6FF"));
    shape.setStroke(2, Color.parseColor("#BFDBFE"));
    button.setBackground(shape);
    button.setAllCaps(false);
    button.setPadding(20, 12, 20, 12);
}

private TextView createPromptText(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setPadding(0, 0, 0, 16);
    return tv;
}

private TextView createTextView(Context context, String text, int textSize, int paddingBottom) {
    TextView textView = new TextView(context);
    textView.setText(text);
    if (textSize > 0) textView.setTextSize(textSize);
    textView.setPadding(0, 0, 0, paddingBottom);
    return textView;
}

private RadioGroup createRadioGroup(Context context, int orientation) {
    RadioGroup radioGroup = new RadioGroup(context);
    radioGroup.setOrientation(orientation);
    return radioGroup;
}

private RadioButton createRadioButton(Context context, String text) {
    RadioButton radioButton = new RadioButton(context);
    radioButton.setText(text);
    radioButton.setId(View.generateViewId());
    return radioButton;
}

private AlertDialog buildCommonAlertDialog(Context context, String title, View view, String positiveBtnText, DialogInterface.OnClickListener positiveListener, String negativeBtnText, DialogInterface.OnClickListener negativeListener, String neutralBtnText, DialogInterface.OnClickListener neutralListener) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    if (positiveBtnText != null) builder.setPositiveButton(positiveBtnText, positiveListener);
    if (negativeBtnText != null) builder.setNegativeButton(negativeBtnText, negativeListener);
    if (neutralBtnText != null) builder.setNeutralButton(neutralBtnText, neutralListener);
    final AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            setupUnifiedDialog(dialog);
        }
    });
    return dialog;
}

private void setupUnifiedDialog(AlertDialog dialog) {
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(Color.parseColor("#FAFBF9"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

private void styleDialogButtons(AlertDialog dialog) {
    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (positiveButton != null) {
        positiveButton.setTextColor(Color.WHITE);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(Color.parseColor("#70A1B8"));
        positiveButton.setBackground(shape);
        positiveButton.setAllCaps(false);
    }
    Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (negativeButton != null) {
        negativeButton.setTextColor(Color.parseColor("#333333"));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(Color.parseColor("#F1F3F5"));
        negativeButton.setBackground(shape);
        negativeButton.setAllCaps(false);
    }
}

private void showLoadingDialog(String title, String message, final Runnable dataLoadTask) {
    LinearLayout initialLayout = new LinearLayout(getTopActivity());
    initialLayout.setOrientation(LinearLayout.HORIZONTAL);
    initialLayout.setPadding(50, 50, 50, 50);
    initialLayout.setGravity(Gravity.CENTER_VERTICAL);
    ProgressBar progressBar = new ProgressBar(getTopActivity());
    initialLayout.addView(progressBar);
    TextView loadingText = new TextView(getTopActivity());
    loadingText.setText(message);
    loadingText.setPadding(20, 0, 0, 0);
    initialLayout.addView(loadingText);
    final AlertDialog loadingDialog = buildCommonAlertDialog(getTopActivity(), title, initialLayout, null, null, "❌ 取消", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) {
            d.dismiss();
        }
    }, null, null);
    loadingDialog.setCancelable(false);
    loadingDialog.show();
    new Thread(new Runnable() {
        public void run() {
            try {
                dataLoadTask.run();
            } finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        loadingDialog.dismiss();
                    }
                });
            }
        }
    }).start();
}

private int dpToPx(int dp) {
    return (int) (dp * getTopActivity().getResources().getDisplayMetrics().density);
}

private void setupListViewTouchForScroll(ListView listView) {
    listView.setOnTouchListener(new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        }
    });
}

// ==========================================
// ========== 💾 配置读写方法 ==========
// ==========================================

private void putString(String setName, String itemName, String value) {
    String existingData = getString(setName, "{}");
    try {
        JSONObject json = JSON.parseObject(existingData);
        json.put(itemName, value);
        putString(setName, json.toString());
    } catch (Exception e) {
        JSONObject json = new JSONObject();
        json.put(itemName, value);
        putString(setName, json.toString());
    }
}

private String getString(String setName, String itemName, String defaultValue) {
    String data = getString(setName, "{}");
    try {
        JSONObject json = JSON.parseObject(data);
        if (json != null && json.containsKey(itemName)) {
            return json.getString(itemName);
        }
    } catch (Exception e) {
        // ignore
    }
    return defaultValue;
}

private long getLong(String setName, String itemName, long defaultValue) {
    try {
        String val = getString(setName, itemName, String.valueOf(defaultValue));
        return Long.parseLong(val);
    } catch(Exception e) {
        return defaultValue;
    }
}

private void putLong(String setName, String itemName, long value) {
    putString(setName, itemName, String.valueOf(value));
}

// 创建任务对话框（简化版入口）
private void showCreateTaskDialog() {
    // 检查是否已选择目标和内容
    if (massSendTargetWxids.isEmpty()) {
        toast("请先在主界面选择发送目标和内容");
        return;
    }
    
    if (massSendType == SEND_TYPE_TEXT && TextUtils.isEmpty(massSendTextContent)) {
        toast("请先输入文本内容");
        return;
    }
    
    if (massSendType != SEND_TYPE_TEXT && massSendType != SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
        toast("请先选择要发送的文件");
        return;
    }
    if (massSendType == SEND_TYPE_VOICE && massSendMediaPaths.isEmpty()) {
        toast("请先选择语音文件(.silk)");
        return;
    }
    
    // 读取间隔设置
    // 简化处理，使用全局变量中的值
    
    // 跳转到时间选择
    showDateTimePickerWithSeconds(new DatePickerCallback() {
        public void onTimeSelected(long timestamp) {
            createAndSaveTask(timestamp);
        }
    });
}
