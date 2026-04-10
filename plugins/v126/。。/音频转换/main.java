import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.io.File;
import java.util.ArrayList;

final String CACHE_DIR = cacheDir.endsWith("/") ? cacheDir : cacheDir + "/";
final String OUT_DIR   = "/storage/emulated/0/Download/";
final String SP_KEY    = "last_folder";

/* 全局变量保存当前浏览对话框 */
AlertDialog gFolderDialog = null;
ArrayAdapter gFolderAdapter = null;
ArrayList gFolderNames = new ArrayList();
ArrayList gFolderFiles = new ArrayList();
File gCurrentFolder = null;

/* ========== 统一弹窗样式 ========== */
void applyDialogStyle(final AlertDialog dialog) {
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface d) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setCornerRadius(48);
            dialogBg.setColor(android.graphics.Color.parseColor("#FAFBF9"));
            dialog.getWindow().setBackgroundDrawable(dialogBg);

            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(android.graphics.Color.WHITE);
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(20);
                shape.setColor(android.graphics.Color.parseColor("#70A1B8"));
                positiveButton.setBackground(shape);
                positiveButton.setAllCaps(false);
            }
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(android.graphics.Color.parseColor("#333333"));
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(20);
                shape.setColor(android.graphics.Color.parseColor("#F1F3F5"));
                negativeButton.setBackground(shape);
                negativeButton.setAllCaps(false);
            }
            android.widget.Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutralButton != null) {
                neutralButton.setTextColor(android.graphics.Color.parseColor("#4A90E2"));
                neutralButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                neutralButton.setAllCaps(false);
            }
        }
    });
}

/* 点击触发 */
boolean onClickSendBtn(String text) {
    if (!"转换".equals(text)) return false;
    String lastPath = getString(SP_KEY, android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
    openFolderBrowser(new File(lastPath));
    return true;
}

/* ========== 打开文件夹浏览器（单例模式） ========== */
void openFolderBrowser(final File startFolder) {
    gCurrentFolder = startFolder;

    if (startFolder != null && startFolder.exists() && startFolder.isDirectory()) {
        putString(SP_KEY, startFolder.getAbsolutePath());
    }

    // 如果已存在对话框，先关闭
    if (gFolderDialog != null && gFolderDialog.isShowing()) {
        gFolderDialog.dismiss();
        gFolderDialog = null;
    }

    // 准备数据
    refreshFolderList(startFolder);

    // 创建对话框
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + startFolder.getAbsolutePath());

    gFolderAdapter = new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, gFolderNames);
    ListView list = new ListView(getTopActivity());
    list.setAdapter(gFolderAdapter);
    builder.setView(list);

    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            Object obj = gFolderFiles.get(pos);
            if (!(obj instanceof File)) return;
            File selected = (File) obj;
            if (selected.equals(gCurrentFolder) && gFolderNames.get(pos).toString().startsWith("⚠")) {
                toast("该目录不可读，请使用手动输入路径");
                return;
            }
            if (selected.isDirectory()) {
                gCurrentFolder = selected;
                putString(SP_KEY, selected.getAbsolutePath());
                refreshFolderList(selected);
                gFolderAdapter.notifyDataSetChanged();
                if (gFolderDialog != null) {
                    gFolderDialog.setTitle("浏览：" + selected.getAbsolutePath());
                }
            }
        }
    });

    builder.setPositiveButton("使用当前目录", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialog = null;
            showFunctionDialog(gCurrentFolder);
        }
    });

    builder.setNegativeButton("手动输入路径", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            gFolderDialog = null;
            showManualPathDialogAudio();
        }
    });

    gFolderDialog = builder.create();
    applyDialogStyle(gFolderDialog);
    gFolderDialog.show();
}

/* ========== 手动输入路径 ========== */
void showManualPathDialogAudio() {
    android.app.Activity act = getTopActivity();
    if (act == null) return;

    LinearLayout layout = new LinearLayout(act);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);

    final android.widget.EditText pathEdit = new android.widget.EditText(act);
    pathEdit.setHint("输入目录路径（如 /storage/emulated/0/Download）");
    pathEdit.setText(getString(SP_KEY, android.os.Environment.getExternalStorageDirectory().getAbsolutePath()));
    layout.addView(pathEdit);

    AlertDialog.Builder b = new AlertDialog.Builder(act);
    b.setTitle("手动输入路径");
    b.setView(layout);
    b.setPositiveButton("跳转", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int w) {
            String p = pathEdit.getText().toString().trim();
            File f = new File(p);
            if (f.exists() && f.isDirectory()) {
                openFolderBrowser(f);
            } else {
                toast("路径无效或不可访问");
            }
        }
    });
    b.setNegativeButton("取消", null);
    AlertDialog manualDialog = b.create();
    applyDialogStyle(manualDialog);
    manualDialog.show();
}

/* ========== 刷新文件夹列表数据 ========== */
void refreshFolderList(File folder) {
    gFolderNames.clear();
    gFolderFiles.clear();

    if (folder == null || !folder.exists() || !folder.isDirectory()) {
        gFolderNames.add("⚠ 路径无效或不可访问");
        gFolderFiles.add(gCurrentFolder);
        return;
    }

    /* 只要有父目录就允许上一级 */
    if (folder.getParentFile() != null) {
        gFolderNames.add("⬆ 上一级");
        gFolderFiles.add(folder.getParentFile());
    }

    File[] subs = null;
    try {
        subs = folder.listFiles();
    } catch (Exception e) {
        subs = null;
    }

    if (subs == null) {
        gFolderNames.add("⚠ 当前目录不可读，请点手动输入路径");
        gFolderFiles.add(folder);
        return;
    }

    /* 目录优先排序 */
    java.util.Arrays.sort(subs, new java.util.Comparator<File>() {
        public int compare(File a, File b) {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        }
    });

    boolean hasDir = false;
    for (File f : subs) {
        if (f.isDirectory()) {
            hasDir = true;
            gFolderNames.add("📁 " + f.getName());
            gFolderFiles.add(f);
        }
    }

    if (!hasDir) {
        gFolderNames.add("（此目录无子文件夹，可点使用当前目录）");
        gFolderFiles.add(folder);
    }
}

/* ========== 选功能（4 个按钮） ========== */
void showFunctionDialog(final File folder) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("文件夹：" + folder.getName());
    
    ListView list = new ListView(getTopActivity());
    String[] items = {"mp3→silk 并发送", "mp3→silk 保存", "silk→mp3 保存", "直接发送silk"};
    list.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, items));
    builder.setView(list);
    
    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            if (pos == 0)      scanFiles(folder, ".mp3", 0);
            else if (pos == 1) scanFiles(folder, ".mp3", 3);
            else if (pos == 2) scanFiles(folder, ".silk", 1);
            else               scanFiles(folder, ".silk", 2);
        }
    });
    applyDialogStyle(dialog);
    dialog.show();
}

/* ========== 扫描文件 ========== */
void scanFiles(final File folder, final String ext, final int mode) {
    ArrayList names = new ArrayList();
    ArrayList files = new ArrayList();
    
    File[] list = folder.listFiles();
    if (list != null) {
        for (File f : list) {
            if (f.getName().toLowerCase().endsWith(ext)) {
                names.add(f.getName());
                files.add(f);
            }
        }
    }
    if (names.isEmpty()) {
        toast("该目录无 " + ext + " 文件");
        return;
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("选择文件");
    ListView listView = new ListView(getTopActivity());
    listView.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, names));
    builder.setView(listView);
    
    final AlertDialog dialog = builder.create();
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            handleFile(folder, (File) files.get(pos), mode);
        }
    });
    applyDialogStyle(dialog);
    dialog.show();
}

/* ========== 处理文件（4 个分支） ========== */
void handleFile(final File folder, final File src, final int mode) {
    toast("正在处理中，请稍候...");
    
    String tempTalker = "";
    try {
        if (mode == 0 || mode == 2) tempTalker = getTargetTalker();
    } catch (Throwable e) {
        showRealError("获取联系人失败", e);
        return;
    }
    final String talker = tempTalker;
    
    new Thread(new Runnable() {
        public void run() {
            try {
                if (mode == 2) {
                    runOnMainThread(new Runnable() {
                        public void run() {
                            try {
                                sendVoice(talker, src.getAbsolutePath());
                                toast("已直接发送原 silk");
                            } catch (Throwable e) {
                                showRealError("发送失败", e);
                            }
                        }
                    });
                    return;
                }
                
                String base = src.getName().replaceFirst("\\.[^.]*$", "") + "_" + System.currentTimeMillis();
                forceClean();
                
                if (mode == 0) {
                    final String silk = CACHE_DIR + base + ".silk";
                    mp3ToSilk(src.getAbsolutePath(), silk);
                    
                    runOnMainThread(new Runnable() {
                        public void run() {
                            try {
                                sendVoice(talker, silk);
                                toast("转换并发送成功");
                                new File(silk).delete();
                                forceClean();
                            } catch (Throwable e) {
                                showRealError("发送失败", e);
                            }
                        }
                    });
                } else if (mode == 3) {
                    final String silk = folder.getAbsolutePath() + "/" + base + ".silk";
                    mp3ToSilk(src.getAbsolutePath(), silk);
                    
                    runOnMainThread(new Runnable() {
                        public void run() {
                            toast("已转换并保存到 " + silk);
                            forceClean();
                        }
                    });
                } else {
                    final String mp3 = folder.getAbsolutePath() + "/" + base + ".mp3";
                    silkToMp3(src.getAbsolutePath(), mp3);
                    
                    runOnMainThread(new Runnable() {
                        public void run() {
                            toast("已转换并保存到 " + mp3);
                            forceClean();
                        }
                    });
                }
            } catch (final Throwable e) {
                runOnMainThread(new Runnable() {
                    public void run() {
                        showRealError("后台处理失败", e);
                    }
                });
            }
        }
    }).start();
}

/* ========== 强制清理临时文件 ========== */
void forceClean() {
    File cache = new File(CACHE_DIR);
    if (!cache.exists()) return;
    File[] fs = cache.listFiles();
    if (fs != null) {
        for (File f : fs) {
            if (f.getName().startsWith("tmp_audio_")) f.delete();
        }
    }
}


void runOnMainThread(Runnable r) {
    if (getTopActivity() != null) {
        getTopActivity().runOnUiThread(r);
    }
}


void showRealError(String prefix, Throwable e) {
    Throwable cause = e;
    if (e instanceof java.lang.reflect.InvocationTargetException) {
        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
    }
    toast(prefix + "：" + (cause != null ? cause.toString() : e.toString()));
}