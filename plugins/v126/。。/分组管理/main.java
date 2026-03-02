import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import android.view.Gravity;
import android.widget.TextView;
import android.widget.ScrollView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.view.MotionEvent;
import android.os.Environment;

import me.hd.wauxv.data.bean.info.FriendInfo;
import me.hd.wauxv.data.bean.info.GroupInfo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;


// 全局变量缓存
private List sCachedFriendList = null;
private List sCachedGroupList = null;

/**
 * 自动获取并分辨当前是主体微信还是分身微信的配置路径
 */
private String getGroupFilePath() {
    String basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    String defaultPath = basePath + "/Android/media/com.tencent.mm/WAuxiliary/Resource/Group/groupItems.json";
    
    File defaultFile = new File(defaultPath);
    if (defaultFile.exists()) {
        return defaultPath;
    }
    
    String clonePath = "/storage/emulated/999/Android/media/com.tencent.mm/WAuxiliary/Resource/Group/groupItems.json";
    if (new File(clonePath).exists()) {
        return clonePath;
    }
    
    return defaultPath;
}


public void onLoad() {
    new Thread(new Runnable() {
        public void run() {
            try {
                if (sCachedFriendList == null) sCachedFriendList = getFriendList();
                if (sCachedGroupList == null) sCachedGroupList = getGroupList();
            } catch (Exception e) {
                log("预加载联系人失败: " + e.getMessage());
            }
        }
    }).start();
    log("分组管理插件已加载，当前路径: " + getGroupFilePath());
}


public boolean onClickSendBtn(String text) {
    if ("分组管理".equals(text) || "修改分组".equals(text) || "分组设置".equals(text)) {

        String currentTalker = getTargetTalker(); 
        
        
        showMainDialog(currentTalker);
        
        return true; 
    }
    return false;
}

// ==========================================
// ========== 💾 文件读写逻辑 ==========
// ==========================================

private JSONArray readGroupConfig() {
    File file = new File(getGroupFilePath());
    if (!file.exists()) {
        return new JSONArray();
    }
    try {
        FileInputStream fis = new FileInputStream(file);
        int length = fis.available();
        byte[] buffer = new byte[length];
        fis.read(buffer);
        fis.close();
        String jsonStr = new String(buffer, "UTF-8");
        JSONArray array = JSON.parseArray(jsonStr);
        if (array == null) return new JSONArray();
        return array;
    } catch (Exception e) {
        log("读取分组配置失败: " + e.getMessage());
        return new JSONArray();
    }
}

private void saveGroupConfig(JSONArray array) {
    File file = new File(getGroupFilePath());
    try {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(file);
        String jsonOutput = JSON.toJSONString(array);
        fos.write(jsonOutput.getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (Exception e) {
        log("保存分组配置失败: " + e.getMessage());
        toast("保存失败: " + e.getMessage());
    }
}

// ==========================================
// ========== 📱 UI 界面与交互逻辑 ==========
// ==========================================

private void showMainDialog(final String currentTalker) {
    final JSONArray groupArray = readGroupConfig();
    
    ScrollView scrollView = new ScrollView(getTopActivity());
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    root.setBackgroundColor(Color.parseColor("#FAFBF9"));
    scrollView.addView(root);

    root.addView(createSectionTitle("📁 自定义分组管理"));
    
    // 提示当前处于哪个聊天界面
    if (!TextUtils.isEmpty(currentTalker)) {
        TextView talkerTip = createPromptText("📍 当前聊天: " + formatMemberDisplay(currentTalker));
        talkerTip.setTextColor(Color.parseColor("#4CAF50"));
        root.addView(talkerTip);
    }
    
    root.addView(createPromptText("👇 点击列表项可管理成员或修改分组名称"));
    
    final ListView listView = new ListView(getTopActivity());
    setupListViewTouchForScroll(listView);
    
    final List<String> displayList = new ArrayList<>();
    for (int i = 0; i < groupArray.size(); i++) {
        JSONObject group = groupArray.getJSONObject(i);
        String title = group.getString("title");
        JSONArray idList = group.getJSONArray("idList");
        int count = (idList != null) ? idList.size() : 0;
        
        Boolean enableObj = group.getBoolean("enable");
        boolean isEnabled = (enableObj == null) ? true : enableObj;
        String statusText = isEnabled ? "" : " [已停用]";
        
        displayList.add("📂 " + title + statusText + " (共 " + count + " 项)");
    }
    
    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, displayList);
    listView.setAdapter(adapter);
    
    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 
        dpToPx(Math.min(Math.max(displayList.size() * 55, 150), 450))
    );
    listParams.setMargins(0, 8, 0, 16);
    listView.setLayoutParams(listParams);
    root.addView(listView);

    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            showGroupOperationMenu(groupArray, position, adapter, displayList, currentTalker);
        }
    });

    Button addBtn = new Button(getTopActivity());
    addBtn.setText("➕ 添加新分组");
    styleUtilityButton(addBtn);
    addBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            showCreateGroupDialog(groupArray, adapter, displayList);
        }
    });
    root.addView(addBtn);

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "⚙️ 分组设置", scrollView, "关闭", null, null, null, null, null);
    dialog.show();
}

private void showGroupOperationMenu(final JSONArray groupArray, final int position, final ArrayAdapter<String> adapter, final List<String> displayList, final String currentTalker) {
    final JSONObject group = groupArray.getJSONObject(position);
    final String title = group.getString("title");
    
    Boolean enableObj = group.getBoolean("enable");
    final boolean isEnabled = (enableObj == null) ? true : enableObj;
    String enableOption = isEnabled ? "🚫 停用此分组" : "✅ 启用此分组";

    String[] options = {"👥 管理分组成员", "✏️ 重命名分组", enableOption, "🔼 上移此分组", "🔽 下移此分组", "🗑️ 删除此分组"};
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("操作分组: " + title);
    builder.setItems(options, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
                showManageMembersDialog(groupArray, position, adapter, displayList, currentTalker);
            } else if (which == 1) {
                showRenameGroupDialog(groupArray, position, adapter, displayList);
            } else if (which == 2) {
                group.put("enable", !isEnabled);
                saveGroupConfig(groupArray);
                
                String statusText = !isEnabled ? "" : " [已停用]";
                JSONArray idList = group.getJSONArray("idList");
                int count = idList != null ? idList.size() : 0;
                displayList.set(position, "📂 " + title + statusText + " (共 " + count + " 项)");
                adapter.notifyDataSetChanged();
                toast((!isEnabled ? "已启用" : "已停用") + "分组: " + title);
            } else if (which == 3) {
                if (position == 0) {
                    toast("已经是第一个了，无法上移");
                    return;
                }
                swapGroupItem(groupArray, position, position - 1, adapter, displayList);
            } else if (which == 4) {
                if (position == groupArray.size() - 1) {
                    toast("已经是最后一个了，无法下移");
                    return;
                }
                swapGroupItem(groupArray, position, position + 1, adapter, displayList);
            } else if (which == 5) {
                if ("全部".equals(title) || "群聊".equals(title) || "好友".equals(title) || "官方".equals(title)) {
                    toast("内置基础分组不能删除");
                    return;
                }
                AlertDialog.Builder confirm = new AlertDialog.Builder(getTopActivity());
                confirm.setTitle("删除确认");
                confirm.setMessage("确定要删除分组 [" + title + "] 吗？");
                confirm.setPositiveButton("🗑️ 删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        groupArray.remove(position);
                        for(int i = 0; i < groupArray.size(); i++) {
                            groupArray.getJSONObject(i).put("order", i);
                        }
                        saveGroupConfig(groupArray);
                        displayList.remove(position);
                        adapter.notifyDataSetChanged();
                        toast("删除成功");
                    }
                });
                confirm.setNegativeButton("取消", null);
                confirm.show();
            }
        }
    });
    AlertDialog menuDialog = builder.create();
    setupUnifiedDialog(menuDialog);
    menuDialog.show();
}

/**
 * 🌟 辅助方法：交换分组顺序并重写 JSON 文件
 */
private void swapGroupItem(JSONArray groupArray, int pos1, int pos2, ArrayAdapter<String> adapter, List<String> displayList) {
    Object tempObj = groupArray.get(pos1);
    groupArray.set(pos1, groupArray.get(pos2));
    groupArray.set(pos2, tempObj);

    for(int i = 0; i < groupArray.size(); i++) {
        groupArray.getJSONObject(i).put("order", i);
    }
    saveGroupConfig(groupArray);

    String tempStr = displayList.get(pos1);
    displayList.set(pos1, displayList.get(pos2));
    displayList.set(pos2, tempStr);

    adapter.notifyDataSetChanged();
}

private void showManageMembersDialog(final JSONArray groupArray, final int position, final ArrayAdapter<String> mainAdapter, final List<String> mainDisplayList, final String currentTalker) {
    showLoadingDialog("正在加载", "读取成员中...", new Runnable() {
        public void run() {
            if (sCachedFriendList == null) sCachedFriendList = getFriendList();
            if (sCachedGroupList == null) sCachedGroupList = getGroupList();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    final JSONObject group = groupArray.getJSONObject(position);
                    final String title = group.getString("title");
                    
                    JSONArray idListArray = group.getJSONArray("idList");
                    if (idListArray == null) {
                        idListArray = new JSONArray();
                        group.put("idList", idListArray);
                    }
                    
                    final List<String> idList = new ArrayList<>();
                    for (int i = 0; i < idListArray.size(); i++) {
                        String id = idListArray.getString(i);
                        if (!TextUtils.isEmpty(id)) idList.add(id);
                    }

                    ScrollView scrollView = new ScrollView(getTopActivity());
                    LinearLayout root = new LinearLayout(getTopActivity());
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setPadding(32, 32, 32, 32);
                    scrollView.addView(root);

                    // --- 第一排按钮：加好友、加群聊 ---
                    LinearLayout btnRow1 = new LinearLayout(getTopActivity());
                    btnRow1.setOrientation(LinearLayout.HORIZONTAL);
                    
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
                    
                    btnRow1.addView(addFriendBtn);
                    btnRow1.addView(addGroupBtn);
                    root.addView(btnRow1);

                    final Runnable saveAndRefresh = new Runnable() {
                        public void run() {
                            JSONArray newIdListArray = new JSONArray();
                            for (String id : idList) {
                                newIdListArray.add(id);
                            }
                            group.put("idList", newIdListArray);
                            saveGroupConfig(groupArray);
                        }
                    };

                    // --- 第二排按钮：手动输入 ---
                    Button addManualBtn = new Button(getTopActivity());
                    addManualBtn.setText("✍️ 手动输入 wxid (支持公众号)");
                    styleUtilityButton(addManualBtn);
                    GradientDrawable btnBg = (GradientDrawable) addManualBtn.getBackground();
                    btnBg.setColor(Color.parseColor("#FFF3E0"));
                    btnBg.setStroke(2, Color.parseColor("#FFE0B2"));
                    addManualBtn.setTextColor(Color.parseColor("#E65100"));
                    root.addView(addManualBtn);

                    // --- 🌟 第三个超级按钮：一键添加当前上下文对象 ---
                    if (!TextUtils.isEmpty(currentTalker)) {
                        Button addCurrentBtn = new Button(getTopActivity());
                        addCurrentBtn.setText("📌 添加当前聊天: " + formatMemberDisplay(currentTalker) + " 到此分组");
                        styleUtilityButton(addCurrentBtn);
                        GradientDrawable curBg = (GradientDrawable) addCurrentBtn.getBackground();
                        curBg.setColor(Color.parseColor("#E8F5E9"));
                        curBg.setStroke(2, Color.parseColor("#A5D6A7"));
                        addCurrentBtn.setTextColor(Color.parseColor("#2E7D32"));
                        
                        addCurrentBtn.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                if (!idList.contains(currentTalker)) {
                                    idList.add(currentTalker);
                                    saveAndRefresh.run();
                                    toast("✅ 添加成功！关闭重新打开界面即可看到。");
                                } else {
                                    toast("⚠️ 该对象已在分组中！");
                                }
                            }
                        });
                        root.addView(addCurrentBtn);
                    }

                    root.addView(createPromptText("👇 点击下方列表中的成员可将其移除"));

                    // 成员列表
                    final ListView listView = new ListView(getTopActivity());
                    setupListViewTouchForScroll(listView);
                    final List<String> memberDisplayList = new ArrayList<>();
                    for (String id : idList) {
                        memberDisplayList.add(formatMemberDisplay(id));
                    }
                    
                    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getTopActivity(), android.R.layout.simple_list_item_1, memberDisplayList);
                    listView.setAdapter(adapter);
                    LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        dpToPx(350)
                    );
                    listView.setLayoutParams(listParams);
                    root.addView(listView);

                    final Runnable fullSaveAndRefresh = new Runnable() {
                        public void run() {
                            saveAndRefresh.run();
                            
                            memberDisplayList.clear();
                            for (String id : idList) {
                                memberDisplayList.add(formatMemberDisplay(id));
                            }
                            adapter.notifyDataSetChanged();
                            
                            Boolean enableObj = group.getBoolean("enable");
                            boolean isEnabled = (enableObj == null) ? true : enableObj;
                            String statusText = isEnabled ? "" : " [已停用]";
                            
                            mainDisplayList.set(position, "📂 " + title + statusText + " (共 " + idList.size() + " 项)");
                            mainAdapter.notifyDataSetChanged();
                        }
                    };

                    addFriendBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Set<String> currentSet = new HashSet<>(idList);
                            showFriendSelectionDialog(currentSet, new Runnable() {
                                public void run() {
                                    idList.clear();
                                    idList.addAll(currentSet);
                                    fullSaveAndRefresh.run();
                                }
                            });
                        }
                    });

                    addGroupBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Set<String> currentSet = new HashSet<>(idList);
                            showGroupSelectionDialog(currentSet, new Runnable() {
                                public void run() {
                                    idList.clear();
                                    idList.addAll(currentSet);
                                    fullSaveAndRefresh.run();
                                }
                            });
                        }
                    });

                    addManualBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            showManualAddDialog(currentTalker, new Runnable() {
                                public void run() {} 
                            }, new ManualAddCallback() {
                                public void onAdd(String wxid) {
                                    if (!idList.contains(wxid)) {
                                        idList.add(wxid);
                                        fullSaveAndRefresh.run();
                                    } else {
                                        toast("该 wxid 已在分组中！");
                                    }
                                }
                            });
                        }
                    });

                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView<?> parent, View view, final int pos, long id) {
                            final String idToRemove = idList.get(pos);
                            AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
                            builder.setTitle("移除成员");
                            builder.setMessage("确定要从该分组中移除:\n" + formatMemberDisplay(idToRemove) + " 吗？");
                            builder.setPositiveButton("移除", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    idList.remove(pos);
                                    fullSaveAndRefresh.run();
                                    toast("已移除");
                                }
                            });
                            builder.setNegativeButton("取消", null);
                            AlertDialog removeDialog = builder.create();
                            setupUnifiedDialog(removeDialog);
                            removeDialog.show();
                        }
                    });

                    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "👥 管理 [" + title + "]", scrollView, "完成", null, null, null, null, null);
                    dialog.show();
                }
            });
        }
    });
}

interface ManualAddCallback {
    void onAdd(String wxid);
}

private void showManualAddDialog(final String defaultWxid, final Runnable onFinish, final ManualAddCallback callback) {
    String preset = TextUtils.isEmpty(defaultWxid) ? "" : defaultWxid;
    final EditText idEdit = createStyledEditText("请输入 wxid (如 gh_xxxxx)", preset);
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setPadding(32, 32, 32, 32);
    root.setOrientation(LinearLayout.VERTICAL);
    root.addView(idEdit);
    
    TextView tipView = createPromptText("提示：如果您不知道wxid，可以直接在目标公众号的聊天界面输入“分组管理”即可一键抓取添加！");
    tipView.setTextColor(Color.parseColor("#E65100"));
    root.addView(tipView);

    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✍️ 手动添加目标", root, "添加", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String wxid = idEdit.getText().toString().trim();
            if (TextUtils.isEmpty(wxid)) {
                toast("wxid 不能为空");
                return;
            }
            if (callback != null) callback.onAdd(wxid);
            if (onFinish != null) onFinish.run();
            toast("尝试添加: " + wxid);
        }
    }, "取消", null, null, null);
    dialog.show();
}

private void showCreateGroupDialog(final JSONArray groupArray, final ArrayAdapter<String> adapter, final List<String> displayList) {
    final EditText nameEdit = createStyledEditText("输入分组名称 (如: 家人, 同事)", "");
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setPadding(32, 32, 32, 32);
    root.addView(nameEdit);
    
    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "➕ 新建分组", root, "保存", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String name = nameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                toast("请输入分组名称");
                return;
            }
            
            int maxOrder = 0;
            for (int i=0; i<groupArray.size(); i++) {
                int o = groupArray.getJSONObject(i).getIntValue("order");
                if (o > maxOrder) maxOrder = o;
            }
            
            JSONObject newGroup = new JSONObject();
            newGroup.put("title", name);
            newGroup.put("order", maxOrder + 1);
            newGroup.put("icon", "ic_group.png");
            newGroup.put("enable", true);
            newGroup.put("idList", new JSONArray());
            
            groupArray.add(newGroup);
            saveGroupConfig(groupArray);
            
            displayList.add("📂 " + name + " (共 0 项)");
            adapter.notifyDataSetChanged();
            toast("分组创建成功");
        }
    }, "取消", null, null, null);
    dialog.show();
}

private void showRenameGroupDialog(final JSONArray groupArray, final int position, final ArrayAdapter<String> adapter, final List<String> displayList) {
    final JSONObject group = groupArray.getJSONObject(position);
    final String oldName = group.getString("title");
    
    final EditText nameEdit = createStyledEditText("输入新名称", oldName);
    LinearLayout root = new LinearLayout(getTopActivity());
    root.setPadding(32, 32, 32, 32);
    root.addView(nameEdit);
    
    AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), "✏️ 重命名分组", root, "保存", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            String name = nameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                toast("名称不能为空");
                return;
            }
            group.put("title", name);
            saveGroupConfig(groupArray);
            
            JSONArray idList = group.getJSONArray("idList");
            int count = idList != null ? idList.size() : 0;
            
            Boolean enableObj = group.getBoolean("enable");
            boolean isEnabled = (enableObj == null) ? true : enableObj;
            String statusText = isEnabled ? "" : " [已停用]";
            
            displayList.set(position, "📂 " + name + statusText + " (共 " + count + " 项)");
            adapter.notifyDataSetChanged();
            toast("重命名成功");
        }
    }, "取消", null, null, null);
    dialog.show();
}

// ==========================================
// ========== 👥 成员选择核心算法 ==========
// ==========================================

private void showFriendSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载中", "正在获取好友列表...", new Runnable() {
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
                            
                            // 列表选择时也显示 wxid
                            names.add("👤 " + displayName + "[" + wxid + "]");
                            ids.add(wxid);
                        }
                    }
                    showMultiSelectDialog("添加好友", names, ids, targetSet, "搜索昵称/备注/wxid...", onFinish);
                }
            });
        }
    });
}

private void showGroupSelectionDialog(final Set<String> targetSet, final Runnable onFinish) {
    showLoadingDialog("加载中", "正在获取群聊列表...", new Runnable() {
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
                            
                            // 列表选择时也显示 roomId(wxid)
                            names.add("🏠 " + name + "(" + roomId + ")");
                            ids.add(roomId);
                        }
                    }
                    showMultiSelectDialog("添加群聊", names, ids, targetSet, "搜索群名/wxid...", onFinish);
                }
            });
        }
    });
}

private void showMultiSelectDialog(String title, final List allItems, final List idList, final Set selectedIds, String searchHint, final Runnable onConfirm) {
    try {
        final Set tempSelected = new HashSet();
        for (Object id : selectedIds) {
            if (idList.contains(id)) {
                tempSelected.add(id);
            }
        }
        
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
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(350));
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
                    // 现在的 name 里面已经包含了 wxid，搜索非常方便
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
            }
        };
        
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) currentFilteredIds.get(pos);
                if (listView.isItemChecked(pos)) tempSelected.add(selected);
                else tempSelected.remove(selected);
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
        
        final AlertDialog dialog = buildCommonAlertDialog(getTopActivity(), title, scrollView, "✅ 确定保存", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                for (Object candId : idList) {
                    selectedIds.remove(candId);
                }
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
// ========== 🔧 辅助工具方法 ==========
// ==========================================

/**
 * 🌟 格式化显示名称，现在统一加上了 wxid 显示，防止重名混淆
 */
private String formatMemberDisplay(String wxid) {
    if (wxid == null) return "未知";
    if (wxid.endsWith("@chatroom")) {
        return "🏠 " + getGroupName(wxid) + "[" + wxid + "]";
    } else if (wxid.startsWith("gh_") || "weixin".equals(wxid)) {
        String name = getFriendName(wxid);
        if (TextUtils.isEmpty(name) || name.equals(wxid)) {
            return "📢 公众号" + "[" + wxid + "]";
        } else {
            return "📢 " + name + "[" + wxid + "]";
        }
    } else {
        return "👤 " + getFriendDisplayName(wxid) + "[" + wxid + "]";
    }
}

private String getFriendDisplayName(String friendWxid) {
    try {
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
    } catch (Exception e) {}
    return getFriendName(friendWxid);
}

private String getGroupName(String groupWxid) {
    try {
        if (sCachedGroupList != null) {
            for (int i = 0; i < sCachedGroupList.size(); i++) {
                GroupInfo groupInfo = (GroupInfo) sCachedGroupList.get(i);
                if (groupWxid.equals(groupInfo.getRoomId())) return groupInfo.getName();
            }
        }
    } catch (Exception e) {}
    return "未知群聊";
}

// ==========================================
// ========== 🎨 现代 UI 组件构建 ==========
// ==========================================

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
    params.setMargins(0, 0, 0, 16);
    textView.setLayoutParams(params);
    return textView;
}

private TextView createPromptText(String text) {
    TextView tv = new TextView(getTopActivity());
    tv.setText(text);
    tv.setTextSize(13);
    tv.setTextColor(Color.parseColor("#666666"));
    tv.setPadding(0, 0, 0, 16);
    return tv;
}

private EditText createStyledEditText(String hint, String initialText) {
    EditText editText = new EditText(getTopActivity());
    editText.setHint(hint);
    editText.setText(initialText);
    editText.setPadding(32, 28, 32, 28);
    editText.setTextSize(14);
    editText.setTextColor(Color.parseColor("#333333"));
    editText.setHintTextColor(Color.parseColor("#999999"));
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(24);
    shape.setColor(Color.parseColor("#FFFFFF"));
    shape.setStroke(2, Color.parseColor("#E0E0E0"));
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
    shape.setColor(Color.parseColor("#F5FBFF"));
    button.setBackground(shape);
    button.setAllCaps(false);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.setMargins(0, 16, 0, 8);
    button.setLayoutParams(params);
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
    try {
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setCornerRadius(48);
        dialogBg.setColor(Color.parseColor("#FAFBF9"));
        dialog.getWindow().setBackgroundDrawable(dialogBg);
    } catch(Exception e) {}
    
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
    final AlertDialog loadingDialog = buildCommonAlertDialog(getTopActivity(), title, initialLayout, null, null, "取消", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) { d.dismiss(); }
    }, null, null);
    loadingDialog.setCancelable(false);
    loadingDialog.show();
    new Thread(new Runnable() {
        public void run() {
            try {
                dataLoadTask.run();
            } finally {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() { loadingDialog.dismiss(); }
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


