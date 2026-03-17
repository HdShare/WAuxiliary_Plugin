/* 必需 import */
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.view.View;
import android.widget.AdapterView;
import java.io.File;
import java.util.ArrayList;

final String CACHE_DIR = cacheDir.endsWith("/") ? cacheDir : cacheDir + "/";
final String OUT_DIR   = "/storage/emulated/0/Download/";
final String SP_KEY    = "last_folder";

/* 点击触发 */
boolean onClickSendBtn(String text) {
    if (!"转换".equals(text)) return false;
    String lastPath = getString(SP_KEY, "/storage/emulated/0");
    browseFolder(new File(lastPath));
    return true;
}

/* ========== 1. 递进浏览文件夹 ========== */
void browseFolder(final File current) {
    putString(SP_KEY, current.getAbsolutePath());

    ArrayList names = new ArrayList();
    ArrayList items = new ArrayList();

    /* 上一级（根目录除外） */
    if (!current.getAbsolutePath().equals("/storage/emulated/0")) {
        names.add("⬆ 上一级");
        items.add(current.getParentFile());
    }

    /* 当前目录下的子文件夹 */
    File[] subs = current.listFiles();
    if (subs != null) {
        for (File f : subs) {
            if (f.isDirectory()) {
                names.add("📁 " + f.getName());
                items.add(f);
            }
        }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("浏览：" + current.getName());
    ListView list = new ListView(getTopActivity());
    list.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, names));
    builder.setView(list);

    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            File selected = (File) items.get(pos);
            if (selected.isDirectory()) {
                browseFolder(selected);
            }
        }
    });

    builder.setPositiveButton("使用当前目录", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface d, int which) {
            d.dismiss();
            showFunctionDialog(current);
        }
    });

    builder.create().show();
}

/* ========== 2. 选功能（3 个按钮） ========== */
void showFunctionDialog(final File folder) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getTopActivity());
    builder.setTitle("文件夹：" + folder.getName());

    ListView list = new ListView(getTopActivity());
    String[] items = {"mp3→silk 并发送", "silk→mp3 保存", "直接发送silk"};
    list.setAdapter(new ArrayAdapter(getTopActivity(), android.R.layout.simple_list_item_1, items));
    builder.setView(list);

    final AlertDialog dialog = builder.create();
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View view, int pos, long id) {
            dialog.dismiss();
            if (pos == 0)      scanFiles(folder, ".mp3", 0);
            else if (pos == 1) scanFiles(folder, ".silk", 1);
            else               scanFiles(folder, ".silk", 2); // 直接发送
        }
    });
    dialog.show();
}

/* ========== 3. 扫描文件 ========== */
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
            handleFile((File) files.get(pos), mode);
        }
    });
    dialog.show();
}

/* ========== 4. 处理文件（3 个分支） - 修复跨线程调用 ========== */
void handleFile(final File src, final int mode) {
    toast("正在处理中，请稍候...");

    // 提前在主线程获取发送目标（跨线程获取可能会报错）
    String tempTalker = "";
    try {
        if (mode == 0 || mode == 2) tempTalker = getTargetTalker();
    } catch (Throwable e) {
        showRealError("获取联系人失败", e);
        return;
    }
    final String talker = tempTalker;

    // 开启新线程进行耗时操作（音视频转换）
    new Thread(new Runnable() {
       
        public void run() {
            try {
                if (mode == 2) {
                    /* 直接发送原 silk 语音 */
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

                /* 其余两种：转换后发送 / 保存 */
                String base = src.getName().replaceFirst("\\.[^.]*$", "") + "_" + System.currentTimeMillis();
                forceClean();
                
                if (mode == 0) {
                    /* mp3 → silk 并发送 */
                    final String silk = CACHE_DIR + base + ".silk";
                    
                    // 1. 耗时操作：转换（在子线程）
                    mp3ToSilk(src.getAbsolutePath(), silk);

                    // 2. 转换完成后，切回主线程发送和弹窗
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
                } else {
                    /* silk → mp3 保存 */
                    final String mp3 = OUT_DIR + base + ".mp3";
                    
                    // 1. 耗时操作：转换（在子线程）
                    silkToMp3(src.getAbsolutePath(), mp3);
                    
                    // 2. 转换完成后，切回主线程提示
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

/* ========== 辅助方法：确保在主线程执行UI和微信API ========== */
void runOnMainThread(Runnable r) {
    if (getTopActivity() != null) {
        getTopActivity().runOnUiThread(r);
    }
}

/* ========== 辅助方法：剥离反射异常，显示真正的死因 ========== */
void showRealError(String prefix, Throwable e) {
    Throwable cause = e;
    if (e instanceof java.lang.reflect.InvocationTargetException) {
        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
    }
    toast(prefix + "：" + (cause != null ? cause.toString() : e.toString()));
}