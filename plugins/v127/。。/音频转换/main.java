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
import android.media.MediaPlayer;

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
    String[] items = {"mp3→silk 并发送", "mp3→silk 保存", "silk→mp3 保存", "直接发送silk", "mp3切割→silk保存", "mp3切割→silk并发送"};

    list.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, items));
    builder.setView(list);
    
    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            if (pos == 0)      scanFiles(folder, ".mp3", 0);
            else if (pos == 1) scanFiles(folder, ".mp3", 3);
            else if (pos == 2) scanFiles(folder, ".silk", 1);
            else if (pos == 3) scanFiles(folder, ".silk", 2);
            else if (pos == 4) scanFiles(folder, ".mp3", 4);
            else               scanFiles(folder, ".mp3", 5);
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
            if (mode == 4 || mode == 5) {
                showSplitDialog(folder, (File) files.get(pos), mode == 5);
            } else {
                handleFile(folder, (File) files.get(pos), mode);
            }
        }
    });
    applyDialogStyle(dialog);
    dialog.show();
}

/* ========== 切割设置：试听 + 自定义秒数 ========== */
void showSplitDialog(final File folder, final File src, final boolean sendAfterSplit) {
    final android.app.Activity act = getTopActivity();
    if (act == null) return;

    final MediaPlayer[] playerBox = new MediaPlayer[1];
    final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    final boolean[] tracking = new boolean[]{false};
    final long[] startMsBox = new long[]{0};
    final long[] endMsBox = new long[]{0};
    final long[] durationMsBox = new long[]{0};
    try {
        durationMsBox[0] = getDuration(src.getAbsolutePath());
        endMsBox[0] = durationMsBox[0];
    } catch (Throwable e) {}

    LinearLayout layout = new LinearLayout(act);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(28, 20, 28, 8);

    final android.widget.SeekBar seek = new android.widget.SeekBar(act);
    seek.setMax(1000);
    layout.addView(seek, new LinearLayout.LayoutParams(-1, -2));

    final android.widget.EditText nameEdit = new android.widget.EditText(act);
    nameEdit.setSingleLine(true);
    nameEdit.setHint("自定义名字");
    nameEdit.setText(src.getName().replaceFirst("\\.[^.]*$", ""));
    layout.addView(nameEdit, new LinearLayout.LayoutParams(-1, -2));

    final android.widget.Button playBtn = new android.widget.Button(act);
    playBtn.setText("试听");
    layout.addView(playBtn, new LinearLayout.LayoutParams(-1, -2));

    final android.widget.EditText startEdit = new android.widget.EditText(act);
    startEdit.setSingleLine(true);
    startEdit.setHint("开始时间（秒 或 分:秒）");
    startEdit.setText(formatMs(startMsBox[0]));
    layout.addView(startEdit, new LinearLayout.LayoutParams(-1, -2));

    android.widget.Button setStartBtn = new android.widget.Button(act);
    setStartBtn.setText("当前位置设为起点");
    layout.addView(setStartBtn, new LinearLayout.LayoutParams(-1, -2));

    final android.widget.EditText endEdit = new android.widget.EditText(act);
    endEdit.setSingleLine(true);
    endEdit.setHint("结束时间（秒 或 分:秒）");
    endEdit.setText(endMsBox[0] > 0 ? formatMs(endMsBox[0]) : "");
    layout.addView(endEdit, new LinearLayout.LayoutParams(-1, -2));

    android.widget.Button setEndBtn = new android.widget.Button(act);
    setEndBtn.setText("当前位置设为终点");
    layout.addView(setEndBtn, new LinearLayout.LayoutParams(-1, -2));

    android.widget.Button resetBtn = new android.widget.Button(act);
    resetBtn.setText("重新设置");
    layout.addView(resetBtn, new LinearLayout.LayoutParams(-1, -2));

    final android.widget.EditText secondsEdit = new android.widget.EditText(act);
    secondsEdit.setSingleLine(true);
    secondsEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    secondsEdit.setHint("自动分割（秒）");
    secondsEdit.setText(getString("split_seconds", "60"));
    layout.addView(secondsEdit, new LinearLayout.LayoutParams(-1, -2));

    final Runnable updateSeek = new Runnable() {
        public void run() {
            try {
                MediaPlayer p = playerBox[0];
                if (p != null && !tracking[0]) {
                    int duration = p.getDuration();
                    if (duration > 0) {
                        seek.setProgress((int) (p.getCurrentPosition() * 1000L / duration));
                    }
                    if (p.isPlaying()) handler.postDelayed(this, 400);
                }
            } catch (Throwable e) {}
        }
    };

    playBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                MediaPlayer p = playerBox[0];
                if (p == null) {
                    p = new MediaPlayer();
                    p.setDataSource(src.getAbsolutePath());
                    p.prepare();
                    playerBox[0] = p;
                    p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        public void onCompletion(MediaPlayer mp) {
                            playBtn.setText("试听");
                        }
                    });
                    long ms = p.getDuration();
                    durationMsBox[0] = ms;
                    if (endMsBox[0] <= 0) endMsBox[0] = ms;
                    startEdit.setText(formatMs(startMsBox[0]));
                    endEdit.setText(formatMs(endMsBox[0]));
                }
                if (p.isPlaying()) {
                    p.pause();
                    playBtn.setText("试听");
                } else {
                    p.start();
                    playBtn.setText("暂停");
                    handler.post(updateSeek);
                }
            } catch (Throwable e) {
                showRealError("试听失败", e);
            }
        }
    });

    seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
            if (!fromUser) return;
            try {
                MediaPlayer p = playerBox[0];
                if (p != null && p.getDuration() > 0) {
                    p.seekTo((int) (p.getDuration() * progress / 1000L));
                }
            } catch (Throwable e) {}
        }
        public void onStartTrackingTouch(android.widget.SeekBar bar) {
            tracking[0] = true;
        }
        public void onStopTrackingTouch(android.widget.SeekBar bar) {
            tracking[0] = false;
            handler.post(updateSeek);
        }
    });

    setStartBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                MediaPlayer p = playerBox[0];
                long pos = p != null ? p.getCurrentPosition() : 0;
                if (endMsBox[0] > 0 && pos >= endMsBox[0]) {
                    toast("开始时间必须小于结束时间");
                    return;
                }
                startMsBox[0] = pos;
                startEdit.setText(formatMs(pos));
            } catch (Throwable e) {
                showRealError("设置起点失败", e);
            }
        }
    });

    setEndBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                MediaPlayer p = playerBox[0];
                long pos = p != null ? p.getCurrentPosition() : durationMsBox[0];
                if (pos <= startMsBox[0]) {
                    toast("结束时间必须大于开始时间");
                    return;
                }
                endMsBox[0] = pos;
                endEdit.setText(formatMs(pos));
            } catch (Throwable e) {
                showRealError("设置终点失败", e);
            }
        }
    });

    resetBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                seek.setProgress(0);
                MediaPlayer p = playerBox[0];
                if (p != null) p.seekTo(0);
                startMsBox[0] = 0;
                endMsBox[0] = p != null ? p.getDuration() : durationMsBox[0];
                startEdit.setText(formatMs(startMsBox[0]));
                endEdit.setText(endMsBox[0] > 0 ? formatMs(endMsBox[0]) : "");
            } catch (Throwable e) {}
        }
    });

    AlertDialog.Builder b = new AlertDialog.Builder(act);
    b.setTitle("转码");
    b.setView(layout);
    b.setNegativeButton("取消", null);
    b.setPositiveButton("确定", null);

    final AlertDialog dialog = b.create();
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface d) {
            handler.removeCallbacks(updateSeek);
            stopPreview(playerBox[0]);
            playerBox[0] = null;
        }
    });
    applyDialogStyle(dialog);
    dialog.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            String secText = secondsEdit.getText().toString().trim();
            int seconds = 60;
            try { seconds = Integer.parseInt(secText); } catch (Throwable e) {}
            if (seconds <= 0) {
                toast("分割秒数必须大于 0");
                return;
            }
            putString("split_seconds", String.valueOf(seconds));
            String customName = nameEdit.getText().toString().trim();
            if (customName.length() == 0) customName = src.getName().replaceFirst("\\.[^.]*$", "");
            long startMs = parseTimeMs(startEdit.getText().toString(), 0);
            long endMs = parseTimeMs(endEdit.getText().toString(), durationMsBox[0]);
            if (endMs <= 0) endMs = durationMsBox[0];
            if (durationMsBox[0] > 0 && endMs > durationMsBox[0]) endMs = durationMsBox[0];
            if (startMs < 0) startMs = 0;
            if (endMs > 0 && startMs >= endMs) {
                toast("开始时间必须小于结束时间");
                return;
            }
            stopPreview(playerBox[0]);
            playerBox[0] = null;
            dialog.dismiss();
            handleSplitFile(folder, src, seconds, customName, startMs, endMs, sendAfterSplit);
        }
    });
}

void stopPreview(MediaPlayer p) {
    try {
        if (p != null) {
            if (p.isPlaying()) p.stop();
            p.release();
        }
    } catch (Throwable e) {}
}

String formatMs(long ms) {
    if (ms < 0) ms = 0;
    long total = ms / 1000L;
    long min = total / 60L;
    long sec = total % 60L;
    return String.format(java.util.Locale.US, "%02d:%02d", new Object[]{new Long(min), new Long(sec)});
}

long parseTimeMs(String text, long defaultValue) {
    if (text == null) return defaultValue;
    String s = text.trim();
    if (s.length() == 0) return defaultValue;
    try {
        if (s.indexOf(":") >= 0) {
            String[] parts = s.split(":");
            long total = 0;
            for (int i = 0; i < parts.length; i++) {
                total = total * 60L + Long.parseLong(parts[i].trim());
            }
            return total * 1000L;
        }
        return Long.parseLong(s) * 1000L;
    } catch (Throwable e) {
        return defaultValue;
    }
}

void handleSplitFile(final File folder, final File src, final int seconds, final String customName, final long startMs, final long endMs, final boolean sendAfterSplit) {
    toast(sendAfterSplit ? "正在切割并发送，请稍候..." : "正在切割并转码，请稍候...");

    String tempTalker = "";
    if (sendAfterSplit) {
        try {
            tempTalker = getTargetTalker();
        } catch (Throwable e) {
            showRealError("获取联系人失败", e);
            return;
        }
    }
    final String talker = tempTalker;

    new Thread(new Runnable() {
        public void run() {
            try {
                forceClean();
                final ArrayList paths = splitMp3ToSilkPaths(src, sendAfterSplit ? new File(CACHE_DIR) : folder, seconds, customName, startMs, endMs, sendAfterSplit);
                if (sendAfterSplit) {
                    runOnMainThread(new Runnable() {
                        public void run() {
                            try {
                                for (int i = 0; i < paths.size(); i++) {
                                    String silk = paths.get(i).toString();
                                    sendVoice(talker, silk);
                                    new File(silk).delete();
                                }
                                toast("切割并发送完成，共 " + paths.size() + " 段");
                                forceClean();
                            } catch (Throwable e) {
                                showRealError("发送失败", e);
                            }
                        }
                    });
                } else {
                    runOnMainThread(new Runnable() {
                        public void run() {
                            toast("切割完成，已生成 " + paths.size() + " 个 silk 文件");
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

/* ========== 按 MP3 帧切割为约 60 秒片段并转 silk ========== */
int splitMp3ToSilk(File src, File folder, int seconds) throws Exception {
    return splitMp3ToSilk(src, folder, seconds, src.getName().replaceFirst("\\.[^.]*$", ""));
}

int splitMp3ToSilk(File src, File folder, int seconds, String customName) throws Exception {
    return splitMp3ToSilk(src, folder, seconds, customName, 0, 0);
}

int splitMp3ToSilk(File src, File folder, int seconds, String customName, long startMs, long endMs) throws Exception {
    return splitMp3ToSilkPaths(src, folder, seconds, customName, startMs, endMs, false).size();
}

ArrayList splitMp3ToSilkPaths(File src, File folder, int seconds, String customName, long startMs, long endMs) throws Exception {
    return splitMp3ToSilkPaths(src, folder, seconds, customName, startMs, endMs, false);
}

ArrayList splitMp3ToSilkPaths(File src, File folder, int seconds, String customName, long startMs, long endMs, boolean tempOutput) throws Exception {
    long duration = getDuration(src.getAbsolutePath());
    if (duration <= 0) throw new RuntimeException("无法读取音频时长");
    if (endMs <= 0 || endMs > duration) endMs = duration;
    if (startMs < 0) startMs = 0;
    if (startMs >= endMs) throw new RuntimeException("开始时间必须小于结束时间");

    ArrayList offsets = new ArrayList();
    ArrayList sizes = new ArrayList();
    readMp3Frames(src, offsets, sizes);
    if (offsets.isEmpty()) throw new RuntimeException("未找到可切割的 MP3 音频帧");

    long partMs = seconds * 1000L;
    int rangeStartFrame = (int) (offsets.size() * startMs / duration);
    int rangeEndFrame = (int) (offsets.size() * endMs / duration);
    if (rangeStartFrame < 0) rangeStartFrame = 0;
    if (rangeEndFrame > offsets.size()) rangeEndFrame = offsets.size();
    if (rangeEndFrame <= rangeStartFrame) rangeEndFrame = rangeStartFrame + 1;

    int rangeFrames = rangeEndFrame - rangeStartFrame;
    long rangeMs = endMs - startMs;
    int partFrames = (int) (rangeFrames * partMs / rangeMs);
    if (partFrames <= 0) partFrames = rangeFrames;

    String name = sanitizeFileName(customName);
    if (name.length() == 0) name = src.getName().replaceFirst("\\.[^.]*$", "");
    ArrayList outPaths = new ArrayList();
    int index = 1;
    int start = rangeStartFrame;
    while (start < rangeEndFrame) {
        int end = start + partFrames;
        if (end > rangeEndFrame) end = rangeEndFrame;
        if (end <= start) end = start + 1;

        String partNo = String.format(java.util.Locale.US, "%03d", new Object[]{new Integer(index)});
        String tempMp3 = CACHE_DIR + "tmp_audio_split_" + System.currentTimeMillis() + "_" + partNo + ".mp3";
        String prefix = tempOutput ? "tmp_audio_send_" : "";
        String silk = folder.getAbsolutePath() + "/" + prefix + name + "_part" + partNo + ".silk";

        writeMp3Part(src, tempMp3, offsets, sizes, start, end);
        int code = mp3ToSilk(tempMp3, silk);
        new File(tempMp3).delete();
        if (code != 0) throw new RuntimeException("第 " + index + " 段转 silk 失败，返回码：" + code);
        outPaths.add(silk);

        start = end;
        index++;
    }
    return outPaths;
}

String sanitizeFileName(String name) {
    if (name == null) return "";
    return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
}

void readMp3Frames(File src, ArrayList offsets, ArrayList sizes) throws Exception {
    java.io.RandomAccessFile raf = new java.io.RandomAccessFile(src, "r");
    try {
        long pos = 0;
        long len = raf.length();

        if (len > 10) {
            raf.seek(0);
            byte[] id3 = new byte[10];
            raf.readFully(id3);
            if (id3[0] == 'I' && id3[1] == 'D' && id3[2] == '3') {
                int tagSize = ((id3[6] & 0x7F) << 21) | ((id3[7] & 0x7F) << 14) | ((id3[8] & 0x7F) << 7) | (id3[9] & 0x7F);
                pos = 10 + tagSize;
            }
        }

        while (pos + 4 < len) {
            raf.seek(pos);
            int b1 = raf.read();
            int b2 = raf.read();
            int b3 = raf.read();
            int b4 = raf.read();

            if (b1 == 0xFF && (b2 & 0xE0) == 0xE0) {
                int frameSize = getMp3FrameSize(b1, b2, b3, b4);
                if (frameSize > 0 && pos + frameSize <= len) {
                    offsets.add(new Long(pos));
                    sizes.add(new Integer(frameSize));
                    pos += frameSize;
                    continue;
                }
            }
            pos++;
        }
    } finally {
        raf.close();
    }
}

int getMp3FrameSize(int b1, int b2, int b3, int b4) {
    int version = (b2 >> 3) & 0x03;
    int layer = (b2 >> 1) & 0x03;
    int bitrateIndex = (b3 >> 4) & 0x0F;
    int sampleIndex = (b3 >> 2) & 0x03;
    int padding = (b3 >> 1) & 0x01;

    if (version == 1 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleIndex == 3) return -1;

    int[] samples = new int[]{44100, 48000, 32000};
    int sampleRate = samples[sampleIndex];
    if (version == 2) sampleRate = sampleRate / 2;
    else if (version == 0) sampleRate = sampleRate / 4;

    int bitrate = 0;
    if (layer == 3) {
        int[][] table = new int[][]{
            {0,32,64,96,128,160,192,224,256,288,320,352,384,416,448,0},
            {0,32,48,56,64,80,96,112,128,160,192,224,256,320,384,0},
            {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0}
        };
        bitrate = table[3 - layer][bitrateIndex] * 1000;
        return (12 * bitrate / sampleRate + padding) * 4;
    } else {
        if (version == 3) {
            int[][] table = new int[][]{
                {0,32,64,96,128,160,192,224,256,288,320,352,384,416,448,0},
                {0,32,48,56,64,80,96,112,128,160,192,224,256,320,384,0},
                {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0}
            };
            bitrate = table[3 - layer][bitrateIndex] * 1000;
        } else {
            int[][] table = new int[][]{
                {0,32,48,56,64,80,96,112,128,144,160,176,192,224,256,0},
                {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0},
                {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0}
            };
            bitrate = table[3 - layer][bitrateIndex] * 1000;
        }
        int coef = (version == 3) ? 144 : 72;
        return coef * bitrate / sampleRate + padding;
    }
}

void writeMp3Part(File src, String outPath, ArrayList offsets, ArrayList sizes, int start, int end) throws Exception {
    java.io.RandomAccessFile in = new java.io.RandomAccessFile(src, "r");
    java.io.FileOutputStream out = new java.io.FileOutputStream(outPath);
    try {
        byte[] buffer = new byte[8192];
        for (int i = start; i < end; i++) {
            long offset = ((Long) offsets.get(i)).longValue();
            int remain = ((Integer) sizes.get(i)).intValue();
            in.seek(offset);
            while (remain > 0) {
                int readLen = remain > buffer.length ? buffer.length : remain;
                int n = in.read(buffer, 0, readLen);
                if (n <= 0) break;
                out.write(buffer, 0, n);
                remain -= n;
            }
        }
        out.flush();
    } finally {
        try { out.close(); } catch (Throwable e) {}
        try { in.close(); } catch (Throwable e) {}
    }
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
