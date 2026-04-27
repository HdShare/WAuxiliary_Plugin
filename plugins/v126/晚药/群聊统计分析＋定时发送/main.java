// ==================== 全局配置 ====================
java.util.Map chatMessageMap = new java.util.HashMap();
int MAX_MESSAGE_PER_GROUP = 2000;
java.util.Timer timer = null;
String KEY_SILICON_API_KEY = "silicon_flow_api_key";
String KEY_DAILY_SEND_TIME = "daily_send_time";
String KEY_DAILY_SEND_GROUPS = "daily_send_groups";
String KEY_STAT_PERIOD = "stat_period_hour";
String KEY_AI_PROMPT = "ai_analysis_prompt";
String SILICON_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
String DEFAULT_AI_MODEL = "deepseek-ai/DeepSeek-V3";
String DEFAULT_PROMPT = "你是一个专业的群聊分析助手，根据下面的群聊统计数据和聊天记录，分析这个群的活跃情况、核心话题、发言用户特点、群聊氛围，给出简洁清晰的分析报告，分点说明，不要太冗长。";

// ==================== 网络请求工具（修复报错专用） ====================
String post(String url, java.util.Map params, java.util.Map headers) {
    try {
        org.json.JSONObject json = new org.json.JSONObject();
        for (java.util.Iterator it = params.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            Object val = params.get(key);
            json.put(key, val);
        }
        java.net.URL u = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        if (headers != null) {
            for (java.util.Iterator it = headers.keySet().iterator(); it.hasNext(); ) {
                String key = (String) it.next();
                String val = (String) headers.get(key);
                conn.setRequestProperty(key, val);
            }
        }
        java.io.OutputStream os = conn.getOutputStream();
        os.write(json.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
        java.io.InputStream is = conn.getInputStream();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        is.close();
        conn.disconnect();
        return sb.toString();
    } catch (Exception e) {
        log("POST请求异常：" + e.getMessage());
        return null;
    }
}

// ==================== 核心回调方法 ====================
void onLoad() {
    log("群聊统计&AI分析插件加载成功");
    timer = new java.util.Timer();
    timer.scheduleAtFixedRate(new java.util.TimerTask() {
        public void run() {
            checkDailySendTask();
        }
    }, 0, 60 * 1000);
    initDefaultConfig();
}

void onUnload() {
    log("群聊统计&AI分析插件已卸载");
    if (timer != null) {
        timer.cancel();
        timer = null;
    }
    chatMessageMap.clear();
}

boolean onClickSendBtn(String text) {
    String trimText = text.trim();
    String currentTalker = getTargetTalker();
    if (trimText.equals("统计")) {
        generateAndSendStatReport(currentTalker);
        return true;
    }
    if (trimText.equals("分析")) {
        generateAndSendAiReport(currentTalker);
        return true;
    }
    if (trimText.equals("统计设置")) {
        android.app.Activity activity = getTopActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    openSettingsUI();
                }
            });
        } else {
            toast("获取页面失败，请重试");
        }
        return true;
    }
    return false;
}

void onHandleMsg(Object msgInfoBean) {
    try {
        Class msgClass = msgInfoBean.getClass();
        boolean isGroupChat = (Boolean) msgClass.getMethod("isGroupChat").invoke(msgInfoBean);
        if (!isGroupChat) return;
        String groupWxid = (String) msgClass.getMethod("getTalker").invoke(msgInfoBean);
        String sendTalker = (String) msgClass.getMethod("getSendTalker").invoke(msgInfoBean);
        String content = (String) msgClass.getMethod("getContent").invoke(msgInfoBean);
        int msgType = (Integer) msgClass.getMethod("getType").invoke(msgInfoBean);
        long createTime = (Long) msgClass.getMethod("getCreateTime").invoke(msgInfoBean);
        String sendNickName = getFriendName(sendTalker, groupWxid);
        java.util.Map msgMap = new java.util.HashMap();
        msgMap.put("groupWxid", groupWxid);
        msgMap.put("sendWxid", sendTalker);
        msgMap.put("sendNickName", sendNickName);
        msgMap.put("content", content);
        msgMap.put("msgType", msgType);
        msgMap.put("createTime", createTime);
        if (!chatMessageMap.containsKey(groupWxid)) {
            chatMessageMap.put(groupWxid, new java.util.ArrayList());
        }
        java.util.List groupMsgList = (java.util.List) chatMessageMap.get(groupWxid);
        groupMsgList.add(msgMap);
        while (groupMsgList.size() > MAX_MESSAGE_PER_GROUP) {
            groupMsgList.remove(0);
        }
    } catch (Exception e) {}
}

// ==================== 初始化默认配置 ====================
void initDefaultConfig() {
    if (getString(KEY_STAT_PERIOD, "").isEmpty()) {
        putString(KEY_STAT_PERIOD, "24");
    }
    if (getString(KEY_AI_PROMPT, "").isEmpty()) {
        putString(KEY_AI_PROMPT, DEFAULT_PROMPT);
    }
    if (getString(KEY_DAILY_SEND_TIME, "").isEmpty()) {
        putString(KEY_DAILY_SEND_TIME, "22:00");
    }
}

// ==================== 多选对话框工具方法 ====================
private int dpToPx(int dp) {
    return (int) (getTopActivity().getResources().getDisplayMetrics().density * dp);
}

private void setupListViewTouchForScroll(android.widget.ListView listView) {
    listView.setOnTouchListener(new android.view.View.OnTouchListener() {
        public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN: v.getParent().requestDisallowInterceptTouchEvent(true); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: v.getParent().requestDisallowInterceptTouchEvent(false); break;
            }
            return false; 
        }
    });
}

private void adjustListViewHeight(android.widget.ListView listView, int itemCount) {
    if (itemCount <= 0) {
        listView.getLayoutParams().height = dpToPx(50); 
    } else {
        int itemHeight = dpToPx(50); 
        int calculatedHeight = Math.min(itemCount * itemHeight, dpToPx(300));
        listView.getLayoutParams().height = calculatedHeight;
    }
    listView.requestLayout();
}

private boolean shouldSelectAll(java.util.List currentFilteredIds, java.util.Set selectedIds) {
    int selectableCount = currentFilteredIds.size();
    int checkedCount = 0;
    for (int i = 0; i < selectableCount; i++) {
        if (selectedIds.contains(currentFilteredIds.get(i))) checkedCount++;
    }
    return selectableCount > 0 && checkedCount < selectableCount;
}

private void updateSelectAllButton(android.app.AlertDialog dialog, java.util.List currentFilteredIds, java.util.Set selectedIds) {
    if (dialog == null) return;
    android.widget.Button neutralButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        if (shouldSelectAll(currentFilteredIds, selectedIds)) neutralButton.setText("全选");
        else neutralButton.setText("取消全选");
    }
}

private void styleDialogButtons(android.app.AlertDialog dialog) {
    android.widget.Button positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
    if (positiveButton != null) {
        positiveButton.setTextColor(android.graphics.Color.WHITE);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(android.graphics.Color.parseColor("#FF6699"));
        positiveButton.setBackground(shape);
        positiveButton.setAllCaps(false);
    }
    android.widget.Button negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
    if (negativeButton != null) {
        negativeButton.setTextColor(android.graphics.Color.parseColor("#333333"));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(android.graphics.Color.parseColor("#F1F3F5"));
        negativeButton.setBackground(shape);
        negativeButton.setAllCaps(false);
    }
    android.widget.Button neutralButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
    if (neutralButton != null) {
        neutralButton.setTextColor(android.graphics.Color.parseColor("#FF6699"));
        neutralButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        neutralButton.setAllCaps(false);
    }
}

private android.app.AlertDialog buildCommonAlertDialog(android.content.Context context, String title, android.view.View view, String positiveBtnText, android.content.DialogInterface.OnClickListener positiveListener, String negativeBtnText, android.content.DialogInterface.OnClickListener negativeListener, String neutralBtnText, android.content.DialogInterface.OnClickListener neutralListener) {
    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
    builder.setTitle(title);
    builder.setView(view);
    if (positiveBtnText != null) builder.setPositiveButton(positiveBtnText, positiveListener);
    if (negativeBtnText != null) builder.setNegativeButton(negativeBtnText, negativeListener);
    if (neutralBtnText != null) builder.setNeutralButton(neutralBtnText, neutralListener);
    final android.app.AlertDialog dialog = builder.create();
    dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
        public void onShow(android.content.DialogInterface d) {
            setupUnifiedDialog(dialog);
        }
    });
    return dialog;
}

private void setupUnifiedDialog(android.app.AlertDialog dialog) {
    android.graphics.drawable.GradientDrawable dialogBg = new android.graphics.drawable.GradientDrawable();
    dialogBg.setCornerRadius(48);
    dialogBg.setColor(android.graphics.Color.parseColor("#FFF5F8"));
    dialog.getWindow().setBackgroundDrawable(dialogBg);
    styleDialogButtons(dialog);
}

private android.widget.EditText createStyledEditText(String hint, String initialText) {
    android.widget.EditText editText = new android.widget.EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setPadding(32, 28, 32, 28);
    editText.setTextSize(14);
    editText.setTextColor(android.graphics.Color.parseColor("#555555"));
    editText.setHintTextColor(android.graphics.Color.parseColor("#999999"));
    android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
    shape.setCornerRadius(24);
    shape.setColor(android.graphics.Color.parseColor("#FFFFFF"));
    shape.setStroke(2, android.graphics.Color.parseColor("#FFE0EB"));
    editText.setBackground(shape);
    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 8, 0, 16);
    editText.setLayoutParams(params);
    return editText;
}

private void showMultiSelectDialog(String title, java.util.List allItems, java.util.List idList, java.util.Set selectedIds, String searchHint, final Runnable onConfirm, final Runnable updateList) {
    try {
        final java.util.Set tempSelected = new java.util.HashSet(selectedIds);
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getTopActivity());
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(getTopActivity());
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 24, 24, 24);
        mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFF5F8"));
        scrollView.addView(mainLayout);
        final android.widget.EditText searchEditText = createStyledEditText(searchHint, "");
        searchEditText.setSingleLine(true);
        mainLayout.addView(searchEditText);
        final android.widget.ListView listView = new android.widget.ListView(getTopActivity());
        setupListViewTouchForScroll(listView);
        listView.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
        android.widget.LinearLayout.LayoutParams listParams = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50));
        listView.setLayoutParams(listParams);
        mainLayout.addView(listView);
        final java.util.List currentFilteredIds = new java.util.ArrayList();
        final java.util.List currentFilteredNames = new java.util.ArrayList();
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
                android.widget.ArrayAdapter adapter = new android.widget.ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_multiple_choice, currentFilteredNames);
                listView.setAdapter(adapter);
                listView.clearChoices();
                for (int j = 0; j < currentFilteredIds.size(); j++) {
                    listView.setItemChecked(j, tempSelected.contains(currentFilteredIds.get(j)));
                }
                adjustListViewHeight(listView, currentFilteredIds.size());
                if (updateList != null) updateList.run();
                final android.app.AlertDialog currentDialog = (android.app.AlertDialog) searchEditText.getTag();
                if (currentDialog != null) updateSelectAllButton(currentDialog, currentFilteredIds, tempSelected);
            }
        };
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
                if (updateList != null) updateList.run();
                final android.app.AlertDialog currentDialog = (android.app.AlertDialog) searchEditText.getTag();
                if (currentDialog != null) updateSelectAllButton(currentDialog, currentFilteredIds, tempSelected);
            }
        });
        final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable searchRunnable = new Runnable() {
            public void run() { updateListRunnable.run(); }
        };
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable); }
            public void afterTextChanged(android.text.Editable s) { searchHandler.postDelayed(searchRunnable, 300); }
        });
        final android.content.DialogInterface.OnClickListener fullSelectListener = new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface dialog, int which) {
                boolean shouldSelectAll = shouldSelectAll(currentFilteredIds, tempSelected);
                for (int i = 0; i < currentFilteredIds.size(); i++) {
                    if (shouldSelectAll) tempSelected.add(currentFilteredIds.get(i));
                    else tempSelected.remove(currentFilteredIds.get(i));
                    listView.setItemChecked(i, shouldSelectAll);
                }
                listView.getAdapter().notifyDataSetChanged();
                listView.requestLayout();
                updateSelectAllButton((android.app.AlertDialog) dialog, currentFilteredIds, tempSelected);
            }
        };
        final android.app.AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, "✅ 保存设置", new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface dialog, int which) {
                selectedIds.clear();
                selectedIds.addAll(tempSelected);
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            }
        }, "❌ 取消", new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface dialog, int which) { dialog.dismiss(); }
        }, "全选", fullSelectListener);
        searchEditText.setTag(dialog);
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface dialogInterface) {
                setupUnifiedDialog((android.app.AlertDialog) dialogInterface);
                android.widget.Button neutralBtn = ((android.app.AlertDialog) dialogInterface).getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
                if (neutralBtn != null) {
                    neutralBtn.setOnClickListener(new android.view.View.OnClickListener() {
                        public void onClick(View v) { fullSelectListener.onClick(dialog, android.app.AlertDialog.BUTTON_NEUTRAL); }
                    });
                }
            }
        });
        dialog.show();
        updateListRunnable.run();
    } catch (Exception e) {
        toast("打开多选对话框失败");
    }
}

// ==================== 勾选式选择定时群聊 ====================
void openSelectGroupForTimer() {
    android.app.Activity act = getTopActivity();
    if (act == null) {
        toast("无法打开页面");
        return;
    }
    java.util.List groupList = getGroupList();
    if (groupList == null || groupList.isEmpty()) {
        toast("暂无群聊数据");
        return;
    }
    final java.util.List wxidList = new java.util.ArrayList();
    final java.util.List nameList = new java.util.ArrayList();
    int failCount = 0;
    for (int i = 0; i < groupList.size(); i++) {
        Object groupInfo = groupList.get(i);
        try {
            Class groupClass = groupInfo.getClass();
            String groupWxid = null;
            String groupName = null;
            try {
                groupWxid = groupClass.getMethod("getRoomId").invoke(groupInfo).toString();
            } catch (Exception e1) {
                try {
                    groupWxid = groupClass.getMethod("getWxid").invoke(groupInfo).toString();
                } catch (Exception e2) {
                    groupWxid = groupClass.getMethod("getUserName").invoke(groupInfo).toString();
                }
            }
            try {
                groupName = groupClass.getMethod("getName").invoke(groupInfo).toString();
            } catch (Exception e1) {
                groupName = groupClass.getMethod("getTitle").invoke(groupInfo).toString();
            }
            if (groupWxid != null && groupName != null) {
                wxidList.add(groupWxid);
                nameList.add("🏠 " + groupName);
            }
        } catch (Exception e) {
            failCount++;
        }
    }
    if (wxidList.isEmpty()) {
        toast("群聊解析失败");
        return;
    }
    final java.util.Set selectedIds = new java.util.HashSet(getDailySendGroups());
    showMultiSelectDialog("选择定时发送群聊", nameList, wxidList, selectedIds, "搜索群聊",
        new Runnable() {
            public void run() {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (java.util.Iterator it = selectedIds.iterator(); it.hasNext(); ) {
                        arr.put(it.next().toString());
                    }
                    putString(KEY_DAILY_SEND_GROUPS, arr.toString());
                    toast("已保存：" + selectedIds.size() + "个群");
                } catch (Exception e) {
                    toast("保存失败");
                }
            }
        }, null);
}

void addGroupToDailySend(String groupWxid) {
    try {
        String json = getString(KEY_DAILY_SEND_GROUPS, "[]");
        org.json.JSONArray arr = new org.json.JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            if (arr.getString(i).equals(groupWxid)) {
                toast("已在列表中");
                return;
            }
        }
        arr.put(groupWxid);
        putString(KEY_DAILY_SEND_GROUPS, arr.toString());
    } catch (Exception e) {}
}

java.util.List getDailySendGroups() {
    java.util.List list = new java.util.ArrayList();
    try {
        org.json.JSONArray arr = new org.json.JSONArray(getString(KEY_DAILY_SEND_GROUPS, "[]"));
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getString(i));
        }
    } catch (Exception e) {}
    return list;
}

// ==================== 统计报告 ====================
void generateAndSendStatReport(String groupWxid) {
    if (!chatMessageMap.containsKey(groupWxid)) {
        sendText(groupWxid, "⚠️ 暂无记录");
        return;
    }
    java.util.List groupMsgList = (java.util.List) chatMessageMap.get(groupWxid);
    if (groupMsgList.isEmpty()) {
        sendText(groupWxid, "⚠️ 暂无记录");
        return;
    }
    int statPeriodHour = Integer.parseInt(getString(KEY_STAT_PERIOD, "24"));
    long currentTime = System.currentTimeMillis();
    long statStartTime = currentTime - statPeriodHour * 60 * 60 * 1000;
    int totalMsgCount = 0;
    java.util.Map userMsgCountMap = new java.util.HashMap();
    java.util.Map msgTypeCountMap = new java.util.HashMap();
    java.util.Map wordCountMap = new java.util.HashMap();
    for (int i = 0; i < groupMsgList.size(); i++) {
        java.util.Map msgMap = (java.util.Map) groupMsgList.get(i);
        long msgTime = (Long) msgMap.get("createTime");
        if (msgTime < statStartTime) continue;
        totalMsgCount++;
        String sendNickName = (String) msgMap.get("sendNickName");
        int msgType = (Integer) msgMap.get("msgType");
        String content = (String) msgMap.get("content");
        if (userMsgCountMap.containsKey(sendNickName)) {
            userMsgCountMap.put(sendNickName, (Integer) userMsgCountMap.get(sendNickName) + 1);
        } else {
            userMsgCountMap.put(sendNickName, 1);
        }
        String typeName = getMsgTypeName(msgType);
        if (msgTypeCountMap.containsKey(typeName)) {
            msgTypeCountMap.put(typeName, (Integer) msgTypeCountMap.get(typeName) + 1);
        } else {
            msgTypeCountMap.put(typeName, 1);
        }
        if (content != null && !content.isEmpty() && msgType == 1) {
            String[] words = content.split("[\\s\\p{Punct}]+");
            for (int j = 0; j < words.length; j++) {
                String word = words[j];
                if (word.length() < 2) continue;
                if (wordCountMap.containsKey(word)) {
                    wordCountMap.put(word, (Integer) wordCountMap.get(word) + 1);
                } else {
                    wordCountMap.put(word, 1);
                }
            }
        }
    }
    if (totalMsgCount == 0) {
        sendText(groupWxid, "⚠️ 周期内无消息");
        return;
    }
    StringBuffer report = new StringBuffer();
    report.append("📊 群聊统计报告\n");
    report.append("统计周期：近" + statPeriodHour + "小时\n");
    report.append("====================\n");
    report.append("总消息：" + totalMsgCount + "条\n");
    report.append("发言人数：" + userMsgCountMap.size() + "人\n");
    report.append("====================\n");
    report.append("🏆 发言TOP5\n");
    java.util.List userEntryList = new java.util.ArrayList(userMsgCountMap.entrySet());
    java.util.Collections.sort(userEntryList, new java.util.Comparator() {
        public int compare(Object o1, Object o2) {
            return ((Integer) ((java.util.Map.Entry) o2).getValue()) - ((Integer) ((java.util.Map.Entry) o1).getValue());
        }
    });
    for (int i = 0; i < Math.min(5, userEntryList.size()); i++) {
        java.util.Map.Entry entry = (java.util.Map.Entry) userEntryList.get(i);
        report.append((i+1) + ". " + entry.getKey() + "：" + entry.getValue() + "条\n");
    }
    report.append("====================\n");
    report.append("📑 消息类型\n");
    java.util.Iterator typeIterator = msgTypeCountMap.entrySet().iterator();
    while (typeIterator.hasNext()) {
        java.util.Map.Entry entry = (java.util.Map.Entry) typeIterator.next();
        report.append(entry.getKey() + "：" + entry.getValue() + "条\n");
    }
    sendText(groupWxid, report.toString());
}

String getMsgTypeName(int msgType) {
    switch (msgType) {
        case 1: return "文本";
        case 3: return "图片";
        case 34: return "语音";
        case 43: return "视频";
        case 47: return "表情";
        case 49: return "文件/链接";
        default: return "其他";
    }
}

// ==================== AI分析（已修复无PluginCallBack） ====================
void generateAndSendAiReport(String groupWxid) {
    final String apiKey = getString(KEY_SILICON_API_KEY, "");
    if (apiKey.isEmpty()) {
        sendText(groupWxid, "⚠️ 请填写硅基API Key");
        return;
    }
    if (!chatMessageMap.containsKey(groupWxid)) {
        sendText(groupWxid, "⚠️ 暂无记录");
        return;
    }
    sendText(groupWxid, "🤖 分析中，请稍候...");

    new Thread(new Runnable() {
        public void run() {
            try {
                java.util.List groupMsgList = (java.util.List) chatMessageMap.get(groupWxid);
                int statPeriodHour = Integer.parseInt(getString(KEY_STAT_PERIOD, "24"));
                long currentTime = System.currentTimeMillis();
                long statStartTime = currentTime - statPeriodHour * 60 * 60 * 1000;
                StringBuffer statContent = new StringBuffer();
                StringBuffer chatContent = new StringBuffer();
                int totalMsgCount = 0;
                java.util.Map userMsgCountMap = new java.util.HashMap();

                for (int i = 0; i < groupMsgList.size(); i++) {
                    java.util.Map msgMap = (java.util.Map) groupMsgList.get(i);
                    long msgTime = (Long) msgMap.get("createTime");
                    if (msgTime < statStartTime) continue;
                    totalMsgCount++;
                    String sendNickName = (String) msgMap.get("sendNickName");
                    String content = (String) msgMap.get("content");
                    if (userMsgCountMap.containsKey(sendNickName)) {
                        userMsgCountMap.put(sendNickName, (Integer) userMsgCountMap.get(sendNickName) + 1);
                    } else {
                        userMsgCountMap.put(sendNickName, 1);
                    }
                    if (chatContent.length() < 3000 && content != null && !content.isEmpty()) {
                        chatContent.append(sendNickName).append("：").append(content).append("\n");
                    }
                }

                statContent.append("统计周期：近" + statPeriodHour + "小时\n");
                statContent.append("总消息：" + totalMsgCount + "条\n");
                statContent.append("发言人数：" + userMsgCountMap.size() + "人\n");
                statContent.append("\n聊天记录：\n").append(chatContent.toString());
                String userPrompt = getString(KEY_AI_PROMPT, DEFAULT_PROMPT);
                String fullPrompt = userPrompt + "\n\n数据：\n" + statContent.toString();

                java.util.Map headerMap = new java.util.HashMap();
                headerMap.put("Authorization", "Bearer " + apiKey);
                headerMap.put("Content-Type", "application/json");

                java.util.Map paramMap = new java.util.HashMap();
                paramMap.put("model", DEFAULT_AI_MODEL);
                paramMap.put("temperature", 0.7);
                paramMap.put("max_tokens", 2000);

                org.json.JSONArray arr = new org.json.JSONArray();
                org.json.JSONObject msg = new org.json.JSONObject();
                msg.put("role", "user");
                msg.put("content", fullPrompt);
                arr.put(msg);
                paramMap.put("messages", arr);

                String result = post(SILICON_API_URL, paramMap, headerMap);
                org.json.JSONObject resJson = new org.json.JSONObject(result);
                String aiContent = resJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                sendText(groupWxid, "🤖 AI群聊分析\n====================\n" + aiContent);
            } catch (Exception e) {
                sendText(groupWxid, "⚠️ 分析失败：" + e.getMessage());
            }
        }
    }).start();
}

// ==================== 定时任务 ====================
void checkDailySendTask() {
    java.util.Calendar calendar = java.util.Calendar.getInstance();
    int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
    int currentMinute = calendar.get(java.util.Calendar.MINUTE);
    String currentTime = String.format("%02d:%02d", currentHour, currentMinute);
    String setTime = getString(KEY_DAILY_SEND_TIME, "22:00");
    if (currentTime.equals(setTime)) {
        java.util.List groupList = getDailySendGroups();
        for (int i = 0; i < groupList.size(); i++) {
            String groupWxid = (String) groupList.get(i);
            generateAndSendStatReport(groupWxid);
        }
    }
}

// ==================== 设置UI ====================
void openSettingsUI() {
    final android.app.Activity activity = getTopActivity();
    if (activity == null) {
        toast("获取页面失败");
        return;
    }
    android.widget.LinearLayout rootLayout = new android.widget.LinearLayout(activity);
    rootLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
    rootLayout.setPadding(40, 40, 40, 40);
    android.graphics.drawable.GradientDrawable rootBg = new android.graphics.drawable.GradientDrawable();
    rootBg.setCornerRadius(40);
    rootBg.setColor(android.graphics.Color.parseColor("#FFF5F8"));
    rootLayout.setBackground(rootBg);

    android.widget.TextView titleTv = new android.widget.TextView(activity);
    titleTv.setText("群聊统计&AI分析设置");
    titleTv.setTextSize(22);
    titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
    titleTv.setTextColor(android.graphics.Color.parseColor("#FF6699"));
    titleTv.setGravity(android.view.Gravity.CENTER);
    titleTv.setPadding(0, 0, 0, 30);
    rootLayout.addView(titleTv);

    android.widget.Button selectGroupBtn = new android.widget.Button(activity);
    selectGroupBtn.setText("📋 选择定时发送群聊");
    selectGroupBtn.setTextSize(16);
    selectGroupBtn.setTypeface(null, android.graphics.Typeface.BOLD);
    selectGroupBtn.setTextColor(android.graphics.Color.WHITE);
    android.graphics.drawable.GradientDrawable selectBtnBg = new android.graphics.drawable.GradientDrawable();
    selectBtnBg.setCornerRadius(20);
    selectBtnBg.setColor(android.graphics.Color.parseColor("#FF6699"));
    selectGroupBtn.setBackground(selectBtnBg);
    selectGroupBtn.setPadding(0, 20, 0, 20);
    selectGroupBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(View v) {
            openSelectGroupForTimer();
        }
    });
    rootLayout.addView(selectGroupBtn);

    android.view.View lineView = new android.view.View(activity);
    lineView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));
    lineView.setBackgroundColor(android.graphics.Color.parseColor("#FFE0EB"));
    lineView.setPadding(0, 20, 0, 20);
    rootLayout.addView(lineView);

    android.widget.TextView apiTitle = new android.widget.TextView(activity);
    apiTitle.setText("硅基流动API Key");
    apiTitle.setTextSize(16);
    apiTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    apiTitle.setTextColor(android.graphics.Color.parseColor("#333333"));
    apiTitle.setPadding(0, 10, 0, 10);
    rootLayout.addView(apiTitle);

    final android.widget.EditText apiEt = new android.widget.EditText(activity);
    apiEt.setText(getString(KEY_SILICON_API_KEY, ""));
    apiEt.setHint("请输入API Key");
    apiEt.setPadding(20, 15, 20, 15);
    android.graphics.drawable.GradientDrawable etBg = new android.graphics.drawable.GradientDrawable();
    etBg.setCornerRadius(15);
    etBg.setColor(android.graphics.Color.WHITE);
    apiEt.setBackground(etBg);
    rootLayout.addView(apiEt);

    android.widget.TextView periodTitle = new android.widget.TextView(activity);
    periodTitle.setText("统计周期（小时）");
    periodTitle.setTextSize(16);
    periodTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    periodTitle.setTextColor(android.graphics.Color.parseColor("#333333"));
    periodTitle.setPadding(0, 20, 0, 10);
    rootLayout.addView(periodTitle);

    final android.widget.EditText periodEt = new android.widget.EditText(activity);
    periodEt.setText(getString(KEY_STAT_PERIOD, "24"));
    periodEt.setHint("例如24");
    periodEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    periodEt.setPadding(20, 15, 20, 15);
    periodEt.setBackground(etBg);
    rootLayout.addView(periodEt);

    android.widget.TextView timeTitle = new android.widget.TextView(activity);
    timeTitle.setText("每日定时发送时间（HH:mm）");
    timeTitle.setTextSize(16);
    timeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    timeTitle.setTextColor(android.graphics.Color.parseColor("#333333"));
    timeTitle.setPadding(0, 20, 0, 10);
    rootLayout.addView(timeTitle);

    final android.widget.EditText timeEt = new android.widget.EditText(activity);
    timeEt.setText(getString(KEY_DAILY_SEND_TIME, "22:00"));
    timeEt.setHint("例如22:00");
    timeEt.setPadding(20, 15, 20, 15);
    timeEt.setBackground(etBg);
    rootLayout.addView(timeEt);

    android.widget.LinearLayout btnLayout = new android.widget.LinearLayout(activity);
    btnLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    btnLayout.setGravity(android.view.Gravity.CENTER);
    btnLayout.setPadding(0, 30, 0, 0);

    android.widget.Button saveBtn = new android.widget.Button(activity);
    saveBtn.setText("保存设置");
    saveBtn.setTextColor(android.graphics.Color.WHITE);
    saveBtn.setTextSize(16);
    android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
    saveBg.setCornerRadius(20);
    saveBg.setColor(android.graphics.Color.parseColor("#FF6699"));
    saveBtn.setBackground(saveBg);
    saveBtn.setPadding(30, 15, 30, 15);
    saveBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(View v) {
            putString(KEY_SILICON_API_KEY, apiEt.getText().toString().trim());
            putString(KEY_STAT_PERIOD, periodEt.getText().toString().trim());
            putString(KEY_DAILY_SEND_TIME, timeEt.getText().toString().trim());
            toast("保存成功");
        }
    });
    btnLayout.addView(saveBtn);

    android.widget.Button clearBtn = new android.widget.Button(activity);
    clearBtn.setText("清空数据");
    clearBtn.setTextColor(android.graphics.Color.WHITE);
    clearBtn.setTextSize(16);
    android.graphics.drawable.GradientDrawable clearBg = new android.graphics.drawable.GradientDrawable();
    clearBg.setCornerRadius(20);
    clearBg.setColor(android.graphics.Color.parseColor("#FF6666"));
    clearBtn.setBackground(clearBg);
    clearBtn.setPadding(30, 15, 30, 15);
    clearBtn.setOnClickListener(new android.view.View.OnClickListener() {
        public void onClick(View v) {
            chatMessageMap.clear();
            toast("数据已清空");
        }
    });
    btnLayout.addView(clearBtn);
    rootLayout.addView(btnLayout);

    android.app.AlertDialog.Builder dialogBuilder = new android.app.AlertDialog.Builder(activity);
    final android.app.AlertDialog dialog = dialogBuilder.create();
    dialog.setView(rootLayout);
    dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "关闭", new android.content.DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialogInterface, int i) {
            dialog.dismiss();
        }
    });
    dialog.show();
    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
}
