import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.Base64;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
String PLUGIN_TITLE = "定时发送助手";
String MEDIA_DIR = "/storage/emulated/0/Android/media/com.tencent.mm/WAuxiliary/Plugin/定时发送/";
String WEIXIN_SAFE_DIR = "/storage/emulated/0/tencent/MicroMsg/WeiXin/WAuxSched/";
String KEY_TASKS = "schedule.tasks.v2";
String KEY_MOMENT_SAFE = "moments.safe";
String KEY_LAST_TARGET_ID = "schedule.last.target.id";
String KEY_LAST_TARGET_NAME = "schedule.last.target.name";
String CMD_OPEN = "定时设置";
int TYPE_TEXT=1, TYPE_MEDIA=2, TYPE_MOMENT=3;
AtomicBoolean schedulerStarted = new AtomicBoolean(false);
long BOOT_AT_MS = System.currentTimeMillis();
long MAX_LATE_MS = 30L * 1000L; // grace window; skip tasks later than this

Timer scheduleTimer = null;
TimerTask pendingTask = null;
Object TASK_LOCK = new Object();

List LAST_IMPORTED_MEDIA = new ArrayList();
List LAST_IMPORTED_MOMENTS = new ArrayList();
Dialog currentDialog = null;
String uiTalkerId="", uiTalkerName="";
String C_BG_ROOT, C_TEXT_PRIMARY, C_TEXT_SECONDARY, C_CARD_BG, C_CARD_STROKE, C_EDIT_BG, C_EDIT_STROKE, C_DIVIDER, C_BUTTON_BG, C_BUTTON_TEXT, C_HINT_TEXT, C_ACCENT;
boolean isDarkMode(){ try{ Activity a=getTopActivity(); if(a==null)return false; int m=a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK; return m==Configuration.UI_MODE_NIGHT_YES; }catch(Throwable e){ return false; } }
void applyTheme(){
 boolean d=isDarkMode();
 C_BG_ROOT=d?"#1F1F1F":"#FFFFFF"; C_TEXT_PRIMARY=d?"#FFFFFF":"#111111"; C_TEXT_SECONDARY=d?"#A6A6A6":"#666666";
 C_CARD_BG=d?"#242424":"#FFFFFF"; C_CARD_STROKE=d?"#333333":"#EEEEEE";
 C_EDIT_BG=d?"#2A2A2A":"#FFFFFF"; C_EDIT_STROKE=d?"#3A3A3A":"#DDDDDD";
 C_DIVIDER=d?"#333333":"#EEEEEE"; C_BUTTON_BG=d?"#2E2E2E":"#F0F3F6"; C_BUTTON_TEXT=d?"#FFFFFF":"#111111"; C_HINT_TEXT=d?"#9AA0A6":"#999999"; C_ACCENT="#4C8BF5";
}
GradientDrawable shape(String color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
GradientDrawable shapeStroke(String fill,int radius,String stroke){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(fill)); g.setCornerRadius(radius); g.setStroke(1, Color.parseColor(stroke)); return g; }
Button btn(Context c,String t){ Button b=new Button(c); try{ b.setIncludeFontPadding(false); }catch(Exception __ignored){} try{ b.setMinHeight(0); b.setMinWidth(0); }catch(Exception __ignored){} try{ if(android.os.Build.VERSION.SDK_INT>=21){ b.setStateListAnimator(null); } }catch(Exception __ignored){} b.setText(t); b.setAllCaps(false); b.setPadding(24,12,24,12); try{ b.setTextColor(Color.parseColor(C_BUTTON_TEXT)); b.setBackground(shapeStroke(C_BUTTON_BG,16,C_CARD_STROKE)); }catch(Exception __ignored){} return b; }
void styleEdit(EditText et){ try{ et.setTextColor(Color.parseColor(C_TEXT_PRIMARY)); et.setHintTextColor(Color.parseColor(C_HINT_TEXT)); et.setBackground(shapeStroke(C_EDIT_BG,12,C_EDIT_STROKE)); et.setPadding(24,16,24,16); et.setTextSize(14f);}catch(Exception __ignored){} }
void styleTextPrimary(TextView tv){ try{ tv.setTextColor(Color.parseColor(C_TEXT_PRIMARY)); }catch(Exception __ignored){} }
void styleTextSecondary(TextView tv){ try{ tv.setTextColor(Color.parseColor(C_TEXT_SECONDARY)); }catch(Exception __ignored){} }
void styleHeader(TextView tv){ try{ tv.setTextSize(16f); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setTextColor(Color.parseColor(C_TEXT_PRIMARY)); tv.setPadding(0,8,0,8); android.graphics.drawable.GradientDrawable dot=new android.graphics.drawable.GradientDrawable(); dot.setShape(android.graphics.drawable.GradientDrawable.OVAL); dot.setColor(Color.parseColor(C_ACCENT)); dot.setSize(dp(8), dp(8)); dot.setBounds(0,0,dp(8),dp(8)); tv.setCompoundDrawables(dot,null,null,null); tv.setCompoundDrawablePadding(dp(8)); }catch(Exception __ignored){} }

void styleSquareCheckBox(Context c, CheckBox cb){
    try{
        // Make checkbox look square & minimal; safe no-op on older ROMs
        if(android.os.Build.VERSION.SDK_INT>=21){
            try{
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setColor(android.graphics.Color.TRANSPARENT);
                g.setCornerRadius(dp(4));
                g.setStroke(2, android.graphics.Color.parseColor(C_ACCENT));
                cb.setBackground(g);
            }catch(Exception __ignored){}
        }
    }catch(Exception __ignored){}
}
void allowWrap(TextView tv){ try{ tv.setMaxLines(10); tv.setEllipsize(null); tv.setLineSpacing(0f,1.1f);}catch(Exception __ignored){} }
void uiToast(String s){ try{ toast(s);}catch(Exception __ignored){} }
int dp(int v){
 try{
 Activity a=getTopActivity();
 float d=a.getResources().getDisplayMetrics().density;
 return (int)(v*d+0.5f);
 }catch(Throwable e){ return v; }
}
String norm(String s){ if(s==null)return ""; return s.replace('\u3000',' ').trim(); }
boolean containsCmd(String text,String key){ if(text==null||key==null) return false; text=text.replace(" ",""); key=key.replace(" ",""); return text.indexOf(key)>=0; }
boolean getBoolean(String k,boolean d){ try{ return getBool(k,d);}catch(Throwable e){ try{ return Boolean.valueOf(getString(k,d?"1":"0")).booleanValue(); }catch(Throwable e2){ return d; } } }
void putBoolean(String k,boolean v){ try{ putBool(k,v);}catch(Throwable e){ putString(k, v?"1":"0"); } }
String b64(String s){ if(s==null)s=""; try{ return new String(Base64.encode(s.getBytes("UTF-8"), Base64.NO_WRAP),"UTF-8"); }catch(Throwable e){ return ""; } }
String ub64(String s){ if(s==null)s=""; try{ return new String(Base64.decode(s.getBytes("UTF-8"), Base64.NO_WRAP),"UTF-8"); }catch(Throwable e){ return ""; } }
String join(List list){ if(list==null||list.size()==0) return ""; StringBuilder sb=new StringBuilder(); for(int i=0;i<list.size();i++){ if(i>0) sb.append(";;"); sb.append(String.valueOf(list.get(i))); } return sb.toString(); }
List splitList(String s){ List out=new ArrayList(); if(s==null||s.length()==0) return out; String[] arr=s.split(";;"); for(int i=0;i<arr.length;i++){ String a=arr[i]; if(a!=null && a.length()>0) out.add(a);} return out; }
String taskLine(String id,int type,String targetId,String targetName,long timeMs,boolean repeat,String content,List paths){
 return id+"|"+type+"|"+(targetId==null?"":targetId)+"|"+b64(targetName==null?"":targetName)+"|"+timeMs+"|"+(repeat?"1":"0")+"|"+b64(content==null?"":content)+"|"+b64(join(paths));
}
Map parseTask(String line){
 try{
 String[] a=line.split("\\|",-1); if(a.length<8) return null;
 Map m=new HashMap(); m.put("id",a[0]); m.put("type",Integer.valueOf(a[1])); m.put("targetId",a[2]); m.put("targetName",ub64(a[3]));
 m.put("time",Long.valueOf(a[4])); m.put("repeat","1".equals(a[5])?Boolean.TRUE:Boolean.FALSE); m.put("content",ub64(a[6])); m.put("paths",splitList(ub64(a[7]))); return m;
 }catch(Throwable e){ return null; }
}
List readTaskLines(){ List list=new ArrayList(); String raw=getString(KEY_TASKS,""); if(raw==null||raw.trim().length()==0) return list; String[] lines=raw.split("\n"); for(int i=0;i<lines.length;i++){ String ln=(lines[i]==null?"":lines[i].trim()); if(ln.length()>0) list.add(ln);} return list; }
void writeTaskLines(List lines){ if(lines==null||lines.size()==0){ putString(KEY_TASKS,""); return; } StringBuilder sb=new StringBuilder(); for(int i=0;i<lines.size();i++){ if(i>0) sb.append("\n"); sb.append(String.valueOf(lines.get(i))); } putString(KEY_TASKS, sb.toString()); }
boolean isImg(String n){ if(n==null) return false; n=n.toLowerCase(Locale.ROOT); return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".gif"); }

String extForMime(String mime){
    try{
        if(mime==null) return ".bin";
        mime=mime.toLowerCase();
        if(mime.contains("jpeg")) return ".jpg";
        if(mime.contains("jpg")) return ".jpg";
        if(mime.contains("png")) return ".png";
        if(mime.contains("gif")) return ".gif";
        if(mime.contains("webp")) return ".webp";
        if(mime.contains("mp4")) return ".mp4";
        if(mime.contains("3gp")) return ".3gp";
        if(mime.contains("mpeg")) return ".mpg";
        if(mime.contains("quicktime")) return ".mov";
    }catch(Exception __ignored){}
    return ".bin";
}
boolean isMp4(String n){ if(n==null) return false; n=n.toLowerCase(Locale.ROOT); return n.endsWith(".mp4"); }

boolean isVideoExt(String n){
    if(n==null) return false;
    n=n.toLowerCase(java.util.Locale.ROOT);
    return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".m4v")
        || n.endsWith(".3gp") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".avi");
}
List scanMediaFiles(){
 List out=new ArrayList();
 try{
 File dir=new File(MEDIA_DIR); if(!dir.exists()||!dir.isDirectory()) return out;
 File[] fs=dir.listFiles(); if(fs==null) return out;
 Arrays.sort(fs,new Comparator(){ public int compare(Object oa,Object ob){ File a=(File)oa,b=(File)ob; long da=a.lastModified(), db=b.lastModified(); return db>da?1:db<da?-1:0; } });
 for(int i=0;i<fs.length;i++){ File f=fs[i]; if(f==null||!f.isFile()) continue; String n=f.getName(); if(isImg(n)||isMp4(n)) out.add(f); }
 }catch(Exception __ignored){}
 return out;
}


boolean ensureDir(File d){ try{ if(!d.exists()) return d.mkdirs(); return true;}catch(Throwable e){ return false; } }
String extOf(String name){ try{ int i=name.lastIndexOf('.'); return (i>=0? name.substring(i): ""); }catch(Throwable e){ return ""; } }
String baseOf(String name){ try{ int i=name.lastIndexOf('/'); String n=(i>=0? name.substring(i+1): name); int j=n.lastIndexOf('.'); return (j>=0? n.substring(0,j): n);}catch(Throwable e){ return String.valueOf(System.currentTimeMillis()); } }
String uniqueNameInDir(File dir,String base,String ext){ String cand=base+ext; int idx=1; File f=new File(dir,cand); while(f.exists()){ cand=base+"_"+(idx++)+ext; f=new File(dir,cand); if(idx>9999) break; } return f.getAbsolutePath(); }
boolean copyFileSimple(String src,String dst){ java.io.FileInputStream in=null; java.io.FileOutputStream out=null; try{ in=new FileInputStream(new File(src)); out=new FileOutputStream(new File(dst)); byte[] buf=new byte[1024*1024]; int len; while((len=in.read(buf))>0){ out.write(buf,0,len);} out.flush(); return true; }catch(Throwable e){   return false; }finally{ try{ if(in!=null) in.close(); }catch(Exception __ignored){} try{ if(out!=null) out.close(); }catch(Exception __ignored){} } }
List prepareSafePaths(List src){
 List out=new ArrayList();
 try{
 File safe=new File(WEIXIN_SAFE_DIR); if(!ensureDir(safe)) return src;
 for(int i=0;i<src.size();i++){ String p=String.valueOf(src.get(i)); if(p==null||p.trim().length()==0) continue; File f=new File(p); if(!f.exists()||!f.isFile()) continue;
 String dst=uniqueNameInDir(safe, baseOf(p), extOf(p)); if(copyFileSimple(p,dst)) out.add(dst);
 }
 return out.size()==0? src: out;
 }catch(Throwable e){ return src; }
}
String normalizeTimeInput(String s){
 if(s==null) return ""; try{
 s=s.trim();
 String full="０１２３４５６７８９：－／．年月日时分", half="0123456789:-/.年月日时分";
 for(int i=0;i<full.length();i++){ s=s.replace(full.charAt(i), half.charAt(i)); }
 s=s.replace("年","-").replace("月","-").replace("日","").replace("时",":").replace("分","").replace("/", "-").replace(".", "-").replaceAll("\\s+"," ").trim();
 if(s.matches("^\\d{1,2}:\\d{2}$")){ SimpleDateFormat d=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT); d.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); s=d.format(new Date())+" "+s; }
 if(s.matches("^\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{2}$")){ SimpleDateFormat y=new SimpleDateFormat("yyyy",Locale.ROOT); y.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); s=y.format(new Date())+"-"+s; }
 String[] parts=s.split(" "); if(parts.length==2){ String[] ymd=parts[0].split("-"); if(ymd.length==3){ String y=ymd[0],m=ymd[1],d=ymd[2]; if(m.length()==1)m="0"+m; if(d.length()==1)d="0"+d; parts[0]=y+"-"+m+"-"+d; } String[] hm=parts[1].split(":"); if(hm.length>=2){ String h=hm[0],mi=hm[1]; if(h.length()==1)h="0"+h; parts[1]=h+":"+mi; } s=parts[0]+" "+parts[1]; }
 return s;
 }catch(Throwable e){ return s; }
}
long parseTime(String s){
 try{
 s=normalizeTimeInput(s);
 String[] pats=new String[]{"yyyy-MM-dd HH:mm","yyyy-M-d HH:mm"};
 for(int i=0;i<pats.length;i++){ try{ SimpleDateFormat sdf=new SimpleDateFormat(pats[i],Locale.ROOT); sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); return sdf.parse(s).getTime(); }catch(Exception __ignored){} }
 return -1L;
 }catch(Throwable e){ return -1L; }
}
String fmtTime(long ms){
 try{ SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT); sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); return sdf.format(new Date(ms)); }catch(Throwable e){ return String.valueOf(ms); }
}

void bringWeChatToFrontAt(long whenMs){

try{
    android.app.Activity _preAct = getTopActivity();
    if(_preAct!=null){
        try{
        S_PREV_TOP_PKG = _preAct.getPackageName();
        S_PREV_TOP_CLS = _preAct.getClass().getName();
        }catch(Exception __ignored){}
    }
}catch(Exception __ignored){}

        try{
S_EXPECT_SILENT_TS=whenMs; }catch(Exception __ignored){}
try{
        android.app.Activity act = getTopActivity();
        Context ctx = (act!=null? (Context)act : hostContext);
        if(ctx==null) return;

        android.content.Intent it = ctx.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if(it==null){

        it = new android.content.Intent();
        it.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
        it.setAction(android.content.Intent.ACTION_MAIN);
        it.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        }
        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        int flags = (android.os.Build.VERSION.SDK_INT >= 23)
        ? (android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE)
        : android.app.PendingIntent.FLAG_UPDATE_CURRENT;

        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0x577321, it, flags);

        android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if(am==null) return;

        long trigger = Math.max(System.currentTimeMillis(), whenMs - 1000);
        try{ am.cancel(pi); }catch(Exception __ignored){}
        if(android.os.Build.VERSION.SDK_INT >= 23){
        am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi);
        }else{
        am.setExact(android.app.AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }catch(Throwable e){ try{ log("/*removed_bringFront*/bringWeChatToFrontAt error: "+e); }catch(Exception __ignored){} }
}

void cancelBringFrontAlarm(){
    try{
        android.app.Activity act = getTopActivity();
        Context ctx = (act!=null? (Context)act : hostContext);
        if(ctx==null) return;
        android.content.Intent it = ctx.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if(it==null){
        it = new android.content.Intent();
        it.setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
        it.setAction(android.content.Intent.ACTION_MAIN);
        it.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        }
        int flags = (android.os.Build.VERSION.SDK_INT >= 23)
        ? (android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE)
        : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0x577321, it, flags);
        android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if(am!=null) am.cancel(pi);
    }catch(Exception __ignored){}
}



void startSchedulerIfNeed(){
 if(schedulerStarted.compareAndSet(false,true)){
 scheduleTimer = new Timer("schedule-dispatch", true);
 rescheduleDispatcher();
 }
}
void stopScheduler(){
 try{ if(pendingTask!=null) pendingTask.cancel(); }catch(Exception __ignored){}
 pendingTask=null;
 try{ if(scheduleTimer!=null) scheduleTimer.cancel(); }catch(Exception __ignored){}
 scheduleTimer=null;
}
long calcNextDaySameTime(long ms){ return ms+24L*60L*60L*1000L; }
void postMomentsSafe(String content,List orig){
 List pics=new ArrayList();
 if(orig!=null){
 for(int k=0;k<orig.size();k++){
 String p=String.valueOf(orig.get(k));
 if(p==null||p.trim().length()==0) continue;
 if(isMp4(p)) continue;

 File f=new File(p); if(!f.exists()||!f.isFile()) continue;
 pics.add(p);
 }
 }
 try{
 if(pics.size()==0){
 uploadText(content==null?"":content);
 notify("定时助手","已发布朋友圈（仅文字）");
 return;
 }
 List send= getBoolean(KEY_MOMENT_SAFE,true) ? prepareSafePaths(pics) : pics;
 try{
 uploadTextAndPicList(content==null?"":content, send);
 notify("定时助手","已发布朋友圈（图文）");
 return;
 }catch(Throwable first){
 if(!getBoolean(KEY_MOMENT_SAFE,true)){
 try{
 List again=prepareSafePaths(pics);
 uploadTextAndPicList(content==null?"":content, again);
 notify("定时助手","已发布朋友圈（图文，安全模式重试成功）");
 return;
 }catch(Exception __ignored){}
 }
 }
 }catch(Exception __ignored){}
 try{
 uploadText((content==null?"":content)+"\n（提示：朋友圈仅支持图文；如需发视频，请改为普通媒体发送）");
 notify("定时助手","已发布朋友圈（仅文字）");
 }catch(Exception __ignored){}
}

boolean BG_NATIVE_READY = false;
Class BG_NATIVE_CLS = null;

void bgInitIfPossible(){
    if(BG_NATIVE_READY) return;
    try{
        BG_NATIVE_CLS = Class.forName("me.yun.plugin.Native");
        try{ BG_NATIVE_CLS.getDeclaredMethod("init", Context.class).invoke(null, hostContext); }catch(Exception __ignored){
    // ==== Auto-inserted stubs to avoid undefined symbol errors ====
    public static String CMD_TARGET = ""; // TODO: verify real value
    public static String S_PREV_TOP_PKG = ""; // TODO: verify real value
    public static String S_PREV_TOP_CLS = ""; // TODO: verify real value
    public static long S_EXPECT_SILENT_TS = 0L; // TODO: verify real value
    // ==============================================================
}
        BG_NATIVE_READY = true;
    }catch(Exception __ignored){}
}

String ensureWxReadable(String path){
    try{
        if(path==null) return null;
        File f = new File(path);
        if(!f.exists() || !f.isFile()) return path;
        String p = f.getAbsolutePath();
        if(p.indexOf("/Android/data/com.tencent.mm/")>=0) return p;
        Activity act = getTopActivity();
        Context ctx = act!=null? (Context)act : hostContext;
        if(ctx==null) return p;
        File dstDir = new File("/sdcard/Android/data/com.tencent.mm/files/Download/");
        if(!dstDir.exists()) dstDir.mkdirs();
        String name = f.getName();
        String ext = "";
        int ix = name.lastIndexOf('.');
        if(ix>0){ ext = name.substring(ix); name = name.substring(0, ix); }
        File out = new File(dstDir, name + "_" + System.currentTimeMillis() + ext);
        FileInputStream in = new FileInputStream(f);
        FileOutputStream ou = new FileOutputStream(out);
        byte[] buf = new byte[8192]; int n;
        while((n=in.read(buf))>0){ ou.write(buf,0,n); }
        try{ in.close(); }catch(Exception __ignored){}
        try{ ou.close(); }catch(Exception __ignored){}
        return out.getAbsolutePath();
    }catch(Throwable ignore){ return path; }
}

String escapeXml(String s){
    if(s==null) return "";
    try{
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }catch(Throwable ignore){ return s; }
}

boolean bgSendText(String talker, String text){
    try{
        bgInitIfPossible();
        if(BG_NATIVE_CLS!=null){
        try{
        java.lang.reflect.Method m = BG_NATIVE_CLS.getDeclaredMethod("sendText", String.class, String.class);
        m.invoke(null, talker, text==null? "" : text);
        return true;
        }catch(Exception __ignored){}
        try{
        java.lang.reflect.Method m2 = BG_NATIVE_CLS.getDeclaredMethod("sendXml", String.class, String.class);
        String xml = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>"+escapeXml(text==null? "" : text)+"</title><des></des><type>0</type></appmsg></msg>";
        m2.invoke(null, talker, xml);
        return true;
        }catch(Exception __ignored){}
        }
    }catch(Exception __ignored){}
    try{
        String xml = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>"+escapeXml(text==null? "" : text)+"</title><des></des><type>0</type></appmsg></msg>";
        me.hd.wauxv.obf.\u16B1\u16B1\u1C88\u1C88\u16B2.\u16B1\u16B1\u16B1\u16B3\u1C00(talker, xml);
        return true;
    }catch(Exception __ignored){}
    return false;
}

boolean bgSendImage(String talker, String path){
    String p = ensureWxReadable(path);
    try{
        bgInitIfPossible();
        if(BG_NATIVE_CLS!=null){
        java.lang.reflect.Method m = BG_NATIVE_CLS.getDeclaredMethod("sendImage", String.class, String.class);
        m.invoke(null, talker, p);
        return true;
        }
    }catch(Exception __ignored){}
    return false;
}

boolean bgSendVideo(String talker, String path){
    String p = ensureWxReadable(path);
    try{
        bgInitIfPossible();
        if(BG_NATIVE_CLS!=null){
        java.lang.reflect.Method m = BG_NATIVE_CLS.getDeclaredMethod("sendVideo", String.class, String.class);
        m.invoke(null, talker, p);
        return true;
        }
    }catch(Exception __ignored){}
    return false;
}

boolean isDeviceLocked(){
    try{
        Activity act = getTopActivity();
        Context ctx = act!=null? (Context)act : hostContext;
        KeyguardManager kg = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return kg!=null && kg.isKeyguardLocked();
    }catch(Throwable ignore){ return false; }
}

void withCpuAwake(long holdMs, Runnable r){
    Activity act = getTopActivity();
    Context ctx = act!=null? (Context)act : hostContext;
    PowerManager.WakeLock wl = null;
    try{
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wa:sched");
        wl.acquire(Math.max(2000L, holdMs));
    }catch(Exception __ignored){}
    try{
        r.run();
    }catch(Exception __ignored){}
    try{
        if(wl!=null) wl.release();
    }catch(Exception __ignored){}
}
void dispatchDueTasks(){ List lines; synchronized(TASK_LOCK){ lines=readTaskLines(); }
 if(lines.size()==0) return;
 long now=System.currentTimeMillis(); boolean changed=false; List out=new ArrayList();
 for(int i=0;i<lines.size();i++){
 String ln=String.valueOf(lines.get(i)); Map t=parseTask(ln); if(t==null) continue;
 String id=String.valueOf(t.get("id")); int type=((Integer)t.get("type")).intValue();
 String targetId=(String)t.get("targetId"); String targetName=(String)t.get("targetName");
 long time=((Long)t.get("time")).longValue(); boolean repeat=((Boolean)t.get("repeat")).booleanValue();
 String content=(String)t.get("content"); List paths=(List)t.get("paths");
 if(now < time){ out.add(ln); continue; }
// --- Skip overdue/missed tasks ---
boolean tooLate = (now - time) > MAX_LATE_MS;
if (tooLate) {
    if (repeat) {
        long next = calcNextDaySameTime(time);
        while ((now - next) > MAX_LATE_MS) {
            next = calcNextDaySameTime(next);
            if (next <= 0) { break; }
        }
        out.add(taskLine(id, type, targetId, targetName, next, true, content, paths));
        changed = true;
    } else {
        changed = true;
    }
    continue;
}

 try{
 if(type==TYPE_TEXT){
 if(targetId!=null && targetId.length()>0){
 if(isDeviceLocked()){
    withCpuAwake(5000L, new Runnable(){ public void run(){ if(!bgSendText(targetId, content==null? "": content)){ if(!bgSendText(targetId, content==null? "": content)){ log("bg sendText failed -> "+targetId); } } }});
}else{
    sendText(targetId, content==null? "": content);
}

 notify("定时助手","已发送文本到："+(targetName!=null&&targetName.length()>0?targetName:targetId));
 }
 } else if(type==TYPE_MEDIA){
 if(targetId!=null && targetId.length()>0 && paths!=null){
 List real=new ArrayList();
 for(int k=0;k<paths.size();k++){ String p=String.valueOf(paths.get(k)); if(p==null||p.trim().length()==0) continue; File f=new File(p); if(f.exists() && f.isFile()) real.add(p); }
 for(int k=0;k<real.size();k++){ String p=String.valueOf(real.get(k)); String n=p.toLowerCase(Locale.ROOT); if(isImg(n)){ if(isDeviceLocked()){
    withCpuAwake(5000L, new Runnable(){ public void run(){ if(!bgSendImage(targetId, p)){ sendImage(targetId, p); } }});
}else{
    sendImage(targetId, p);
}
} else if(isMp4(n)){ if(isDeviceLocked()){
    withCpuAwake(5000L, new Runnable(){ public void run(){ if(!bgSendVideo(targetId, p)){ sendVideo(targetId, p); } }});
}else{
    sendVideo(targetId, p);
}
} }
 notify("定时助手","已发送媒体到："+(targetName!=null&&targetName.length()>0?targetName:targetId));
 }
 } else if(type==TYPE_MOMENT){
 postMomentsSafe(content, paths);
 }
 }catch(Exception __ignored){}
 if(repeat){ long next=calcNextDaySameTime(time); out.add(taskLine(id,type,targetId,targetName,next,true,content,paths)); changed=true; } else { changed=true; }
 }
 synchronized(TASK_LOCK){ writeTaskLines(out); }
}
long findMinDue(){
 long min = Long.MAX_VALUE;
 long now = System.currentTimeMillis();
 boolean hasOverdue = false;
 try{
 List lines = readTaskLines();
 for(int i=0;i<lines.size();i++){
 String ln=String.valueOf(lines.get(i)); Map t=parseTask(ln); if(t==null) continue;
 long time=((Long)t.get("time")).longValue();
 if(time>0){ boolean overdue = (now - time) > MAX_LATE_MS; if(!overdue && time<min) min=time; if(overdue) hasOverdue=true; }
 }
 }catch(Exception __ignored){}
 if(min==Long.MAX_VALUE && hasOverdue){ return now + 200L; }
 return min;
}

void cancelPending(){
 try{ if(pendingTask!=null) pendingTask.cancel(); }catch(Exception __ignored){}
 pendingTask = null;
}
void rescheduleDispatcher(){
 try{
 if(scheduleTimer==null) scheduleTimer = new Timer("schedule-dispatch", true);
 cancelPending();
 long minDue = findMinDue();
 if(minDue==Long.MAX_VALUE){ try{ cancelBringFrontAlarm(); }catch(Exception __ignored){} return; }
 long now = System.currentTimeMillis();
 long delay = minDue - now;
 if(delay < 0) delay = 0;
 pendingTask = new TimerTask(){ public void run(){
 try{ dispatchDueTasks(); }catch(Exception __ignored){}
 finally{ try{ rescheduleDispatcher(); }catch(Exception __ignored){} }
 }};
 scheduleTimer.schedule(pendingTask, delay);
 try{ if(minDue - now > 1000L) bringWeChatToFrontAt(minDue); }catch(Exception __ignored){} }catch(Throwable e){ try{  }catch(Exception __ignored){} }
}
EditText makeTimeInput(Context c){
 EditText et=new EditText(c); et.setHint("发送时间（yyyy-MM-dd HH:mm）"); et.setSingleLine(true); et.setPadding(0,12,0,0); styleEdit(et);
 try{ et.setText(fmtTime(System.currentTimeMillis()+3*60*1000L)); }catch(Exception __ignored){}
 return et;
}
void addRepeatSwitch(Context c, LinearLayout parent, final boolean[] ref){
    RadioGroup g=new RadioGroup(c);
    g.setOrientation(RadioGroup.HORIZONTAL);

    final RadioButton r1=new RadioButton(c);
    r1.setText("一次性");
    styleTextPrimary(r1);

    final RadioButton r2=new RadioButton(c);
    r2.setText("每天重复");
    styleTextPrimary(r2);

    try{
        if(android.os.Build.VERSION.SDK_INT>=21){
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(C_ACCENT));
            r1.setButtonTintList(tint);
            r2.setButtonTintList(tint);
        }
    }catch(Throwable __ignored){}

    // ensure unique IDs for proper RadioGroup behavior
    try{
        int id1 = (android.os.Build.VERSION.SDK_INT>=17) ? View.generateViewId() : (int)(System.currentTimeMillis()%100000 + Math.random()*10000);
        int id2 = id1 + 1;
        r1.setId(id1);
        r2.setId(id2);
    }catch(Throwable __ignored){}

    g.addView(r1);
    g.addView(r2);

    // default: once
    try{
        g.check(r1.getId());
    }catch(Throwable __ignored){}
    try{ ref[0]=false; }catch(Throwable __ignored){}

    g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
        public void onCheckedChanged(RadioGroup group, int checkedId){
            try{
                boolean repeat = (checkedId == r2.getId());
                ref[0] = repeat;
            }catch(Throwable __ignored){}
        }
    });

    parent.addView(g);
}

LinearLayout selectRow2(final Context c, final String path, String name, final Runnable onChanged){
 LinearLayout row=new LinearLayout(c); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(8),dp(6),dp(8),dp(6));
 row.setGravity(android.view.Gravity.CENTER_VERTICAL);
 FrameLayout box=new FrameLayout(c);
 GradientDrawable sq=new GradientDrawable(); sq.setColor(Color.TRANSPARENT); sq.setCornerRadius(dp(4)); sq.setStroke(2, Color.parseColor(C_ACCENT));
 box.setBackground(sq);
 LinearLayout.LayoutParams lpBox=new LinearLayout.LayoutParams(dp(20), dp(20));
 box.setLayoutParams(lpBox);
 TextView tick=new TextView(c); tick.setText("✓"); tick.setTextColor(Color.parseColor("#22BB66")); tick.setTextSize(14f); tick.setGravity(android.view.Gravity.CENTER);
 box.addView(tick, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
 tick.setVisibility(View.INVISIBLE);
 TextView tv=new TextView(c); tv.setText(name); styleTextPrimary(tv);
 LinearLayout.LayoutParams lpName=new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lpName.leftMargin=dp(8);
 row.addView(box);
 row.addView(tv, lpName);
 row.setTag(new Object[]{path, Boolean.FALSE, tick});

 View.OnClickListener tog=new View.OnClickListener(){ public void onClick(View v){
   try{
     Object[] tag=(Object[])row.getTag();
     boolean sel=((Boolean)tag[1]).booleanValue();
     sel=!sel;
     tag[1]=Boolean.valueOf(sel);
     row.setTag(tag);
     View t=(View)tag[2];
     t.setVisibility(sel?View.VISIBLE:View.INVISIBLE);
     if(onChanged!=null) onChanged.run();
   }catch(Exception __ignored){}
 }};
 row.setOnClickListener(tog);
 return row;
}






void showScheduleDialogSafe(){
 applyTheme();
 final int[] tries=new int[]{0};
 new Handler(Looper.getMainLooper()).post(new Runnable(){ public void run(){
 Activity act=null; try{ act=getTopActivity(); }catch(Exception __ignored){}
 if(act==null){ if(++tries[0]<=20){ new Handler(Looper.getMainLooper()).postDelayed(this,500);} else { uiToast("无法获取界面上下文，请在聊天页面重试"); } return; }
 try{ showScheduleDialogInternal(act); }
 catch(Throwable e){ if(++tries[0]<=20){ new Handler(Looper.getMainLooper()).postDelayed(this,500);} else { try{  }catch(Exception __ignored){} uiToast("面板打开失败，请稍后重试"); } }
 }});
}

void renderSelectedPreviewLive(final Activity act, final LinearLayout previewWrap, final LinearLayout listWrap, final boolean imagesOnly){
    previewWrap.removeAllViews();
    java.util.List sel=new java.util.ArrayList();
    try{
        for(int i=0;i<listWrap.getChildCount();i++){
        View v=listWrap.getChildAt(i);
        if(v instanceof LinearLayout){
        Object tag=v.getTag();
        if(tag instanceof Object[]){
        Object[] arr=(Object[])tag;
        if(arr.length>=3){
        String p=String.valueOf(arr[0]);
        boolean s=((Boolean)arr[1]).booleanValue();
        if(s){ if(imagesOnly && isMp4(p)) continue; sel.add(p); }
        }
        }
        }
        }
    }catch(Exception __ignored){}
    TextView ht=new TextView(act);
    ht.setText(sel.size()==0? "未选择任何素材" : ("已选择 "+sel.size()+" 项"));
    ht.setPadding(dp(12),dp(6),dp(12),dp(6));
    styleTextSecondary(ht);
    previewWrap.addView(ht);
    if(sel.size()==0) return;
    android.widget.GridLayout grid=new android.widget.GridLayout(act);
    grid.setColumnCount(6);
    previewWrap.setPadding(dp(12), dp(6), dp(12), dp(6));
    previewWrap.addView(grid, new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

    int container = previewWrap.getWidth();
    if(container <= 0){
        try{
        android.view.View p = (android.view.View) previewWrap.getParent();
        while(p != null && container <= 0){ container = p.getWidth(); p = (android.view.View) p.getParent(); }
        }catch(Exception __ignored){}
    }
    if(container <= 0){
        int screen=act.getResources().getDisplayMetrics().widthPixels;
        container = (int)(screen*0.9f);
    }
    int spacing=dp(6), cols=6;
    int avail = container - previewWrap.getPaddingLeft() - previewWrap.getPaddingRight();
    int edge = (avail - (cols-1)*spacing) / cols;
    if(edge < dp(28)) edge = dp(28);
    for(int i=0;i<sel.size();i++){
        String p=String.valueOf(sel.get(i));
        boolean isV=isMp4(p);
        if(imagesOnly && isV) continue;
        android.widget.FrameLayout tile=new android.widget.FrameLayout(act);
        tile.setPadding(0,0,0,0);
        android.widget.ImageView iv=new android.widget.ImageView(act);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        tile.addView(iv, new android.widget.FrameLayout.LayoutParams(edge,edge));
        loadThumbAsyncUri(iv, p, isV, edge);
        android.widget.GridLayout.LayoutParams glp=new android.widget.GridLayout.LayoutParams();
        glp.width=edge; glp.height=edge;
        int col = i % cols;
        glp.leftMargin = 0;
        glp.rightMargin = (col==cols-1)? 0 : spacing;
        glp.topMargin = spacing; glp.bottomMargin = 0;
        grid.addView(tile, glp);
    }}

void renderMediaList2(final Context c, final LinearLayout listWrap, final Runnable onChanged){
 listWrap.removeAllViews();
 List list=scanMediaFiles();
 if(list.size()==0){
  TextView empty=new TextView(c); empty.setText("目录为空或无可用媒体（支持：JPEG/PNG/GIF/MP4）"); empty.setTextColor(Color.parseColor("#ff6666"));
  listWrap.addView(empty); if(onChanged!=null) onChanged.run(); return;
 }
 for(int i=0;i<list.size();i++){
  File f=(File)list.get(i); if(f==null) continue;
  try{
   LinearLayout row=selectRow2(c, f.getAbsolutePath(), f.getName(), onChanged);
   try{
     if(LAST_IMPORTED_MEDIA!=null){
        for(int k=0;k<LAST_IMPORTED_MEDIA.size();k++){
        String pSel=String.valueOf(LAST_IMPORTED_MEDIA.get(k));
        if(pSel!=null && pSel.equals(f.getAbsolutePath())){
        Object[] tag=(Object[])row.getTag(); tag[1]=Boolean.TRUE;
        ((View)tag[2]).setVisibility(View.VISIBLE);
        }
        }
     }
   }catch(Exception __ignored){}
   listWrap.addView(row);
   View div=new View(c); div.setBackgroundColor(Color.parseColor(C_DIVIDER));
   LinearLayout.LayoutParams lpD=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); lpD.topMargin=dp(4);
   listWrap.addView(div, lpD);
  }catch(Exception __ignored){}
 }
 try{ LAST_IMPORTED_MEDIA.clear(); }catch(Exception __ignored){}
 if(onChanged!=null) onChanged.run();
}
void renderImageList2(final Context c, final LinearLayout listWrap, final Runnable onChanged){
 listWrap.removeAllViews();
 List list=scanMediaFiles();
 if(list.size()==0){
  TextView empty=new TextView(c); empty.setText("目录为空或无可用图片（支持：JPEG/PNG/GIF）"); empty.setTextColor(Color.parseColor("#ff6666"));
  listWrap.addView(empty); if(onChanged!=null) onChanged.run(); return;
 }
 for(int i=0;i<list.size();i++){
  File f=(File)list.get(i); if(f==null) continue;
  String p=f.getAbsolutePath(); if(isMp4(p)) continue;
  try{
   LinearLayout row=selectRow2(c, p, f.getName(), onChanged);
   try{
     if(LAST_IMPORTED_MOMENTS!=null){
        for(int k=0;k<LAST_IMPORTED_MOMENTS.size();k++){
        String pSel=String.valueOf(LAST_IMPORTED_MOMENTS.get(k));
        if(pSel!=null && pSel.equals(p)){
        Object[] tag=(Object[])row.getTag(); tag[1]=Boolean.TRUE;
        ((View)tag[2]).setVisibility(View.VISIBLE);
        }
        }
     }
   }catch(Exception __ignored){}
   listWrap.addView(row);
   View div=new View(c); div.setBackgroundColor(Color.parseColor(C_DIVIDER));
   LinearLayout.LayoutParams lpD=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); lpD.topMargin=dp(4);
   listWrap.addView(div, lpD);
  }catch(Exception __ignored){}
 }
 try{ LAST_IMPORTED_MOMENTS.clear(); }catch(Exception __ignored){}
 if(onChanged!=null) onChanged.run();
}
void showScheduleDialogInternal(final Activity act){
        final Runnable[] onModeChangedRef = new Runnable[1];
 new Handler(Looper.getMainLooper()).post(new Runnable(){ public void run(){
    try{
        if(currentDialog!=null && currentDialog.isShowing()){ try{ currentDialog.dismiss(); }catch(Exception __ignored){} }
        Dialog d=new Dialog(act); currentDialog=d;
        WindowManager wm=(WindowManager)act.getSystemService(Context.WINDOW_SERVICE); int width=wm.getDefaultDisplay().getWidth();
        LinearLayout root=new LinearLayout(act); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(16), dp(20), dp(16)); root.setBackground(shape(C_BG_ROOT,24));
        TextView title=new TextView(act); title.setText(PLUGIN_TITLE); title.setTextSize(20); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); styleTextPrimary(title); allowWrap(title);
        title.setGravity(Gravity.CENTER_HORIZONTAL); title.setLetterSpacing(0.02f); title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        try{
        TextView author=new TextView(act); author.setText("作者:冰块  版本:2.1  TG:@bingkuai_666"); author.setTextSize(12); styleTextSecondary(author);
        author.setGravity(Gravity.CENTER_HORIZONTAL); author.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); author.setPadding(0, dp(2), 0, dp(8)); root.addView(author);
        View __divider=new View(act); __divider.setBackgroundColor(Color.parseColor(C_DIVIDER)); LinearLayout.LayoutParams __lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); __lp.setMargins(0, dp(6), 0, dp(12)); root.addView(__divider, __lp); }catch(Exception __ignored){}
        TextView sub=new TextView(act); String showName=(uiTalkerName!=null && uiTalkerName.length()>0)? uiTalkerName: uiTalkerId; sub.setText("当前会话目标："+(showName==null||showName.length()==0?"未识别":showName));
        sub.setTextSize(12); styleTextSecondary(sub); sub.setPadding(0,dp(2),0,dp(8)); allowWrap(sub); root.addView(sub);
        LinearLayout tabs=new LinearLayout(act); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,dp(6),0,dp(8));
        final Button tabSettings=btn(act,"设置"); final Button tabTasks=btn(act,"任务");
        LinearLayout.LayoutParams lpTab=new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabSettings.setLayoutParams(lpTab); tabTasks.setLayoutParams(lpTab);
        try{
        tabSettings.setBackground(shapeStroke(C_ACCENT,16,C_CARD_STROKE)); tabSettings.setTextColor(Color.parseColor("#FFFFFF"));
        tabTasks.setBackground(shapeStroke(C_BUTTON_BG,16,C_CARD_STROKE)); tabTasks.setTextColor(Color.parseColor(C_BUTTON_TEXT));
        }catch(Exception __ignored){}
        tabs.addView(tabSettings); tabs.addView(tabTasks); root.addView(tabs);
        final ScrollView scrollSettings=new ScrollView(act); scrollSettings.setFillViewport(true);
        final ScrollView scrollTasks=new ScrollView(act); scrollTasks.setFillViewport(true);
        LinearLayout.LayoutParams lpScroll=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollSettings.setLayoutParams(lpScroll); scrollTasks.setLayoutParams(lpScroll);
        root.addView(scrollSettings); root.addView(scrollTasks); scrollTasks.setVisibility(View.GONE);
        LinearLayout body=new LinearLayout(act); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(4),dp(8),dp(4),dp(8));
        scrollSettings.addView(body);
        TextView ht=new TextView(act); ht.setText("【定时：文本消息】"); styleHeader(ht); body.addView(ht);
        LinearLayout ct=new LinearLayout(act); ct.setOrientation(LinearLayout.VERTICAL); ct.setPadding(dp(16),dp(12),dp(16),dp(12)); ct.setBackground(shapeStroke(C_CARD_BG,16,C_CARD_STROKE)); body.addView(ct);
        try{ ct.setElevation(6f); }catch(Exception __ignored){}
        final EditText etText=new EditText(act); etText.setHint("输入要发送的文本内容"); etText.setMinLines(2); etText.setMaxLines(6); styleEdit(etText); ct.addView(etText);
        final EditText etTimeT=makeTimeInput(act); ct.addView(etTimeT);
        final boolean[] repeatText=new boolean[]{false}; addRepeatSwitch(act, ct, repeatText);
        Button saveText=btn(act,"保存文本定时任务"); LinearLayout.LayoutParams lps=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lps.topMargin=dp(8); ct.addView(saveText,lps);
        saveText.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        if(uiTalkerId==null||uiTalkerId.length()==0){ uiToast("请在某个会话页面内触发“定时设置”"); return; }
        String content=String.valueOf(etText.getText()).trim(); if(content.length()==0){ uiToast("内容不能为空"); return; }
        long when=parseTime(String.valueOf(etTimeT.getText()).trim()); if(when<=0){ uiToast("时间格式不正确：yyyy-MM-dd HH:mm"); return; }
        String id="T"+System.currentTimeMillis(); String line=taskLine(id, TYPE_TEXT, uiTalkerId, uiTalkerName, when, repeatText[0], content, new ArrayList());
        synchronized(TASK_LOCK){ List ls=readTaskLines(); ls.add(line); writeTaskLines(ls); }
        rescheduleDispatcher();
        uiToast("已保存（文本→"+(uiTalkerName!=null&&uiTalkerName.length()>0?uiTalkerName:uiTalkerId)+"）："+fmtTime(when)+(repeatText[0]?"，每天重复":"，一次性"));
        }});
        TextView hint=new TextView(act); hint.setTextSize(12); styleTextSecondary(hint); hint.setText("说明：任务会绑定“当前会话”为发送目标，各会话互不影响。"); body.addView(hint);
        TextView hAll=new TextView(act); hAll.setText("【定时：媒体 / 朋友圈】"); styleHeader(hAll); body.addView(hAll);
        LinearLayout cm=new LinearLayout(act); cm.setOrientation(LinearLayout.VERTICAL); cm.setPadding(dp(16),dp(12),dp(16),dp(12)); cm.setBackground(shapeStroke(C_CARD_BG,16,C_CARD_STROKE)); body.addView(cm);
        try{ cm.setElevation(6f); }catch(Exception __ignored){}
        final CheckBox cbMedia=new CheckBox(act); styleTextPrimary(cbMedia); cbMedia.setText("图片/视频"); cbMedia.setChecked(true);
        final CheckBox cbMoment=new CheckBox(act); styleTextPrimary(cbMoment); cbMoment.setText("朋友圈（仅图片）"); cbMoment.setChecked(false);
        try{ styleSquareCheckBox(act, cbMedia); styleSquareCheckBox(act, cbMoment);}catch(Exception __ignored){}

try{ cbMedia.setVisibility(View.GONE); cbMoment.setVisibility(View.GONE); }catch(Exception __ignored){}

LinearLayout modeRow = new LinearLayout(act);
modeRow.setOrientation(LinearLayout.HORIZONTAL);
modeRow.setPadding(dp(2), dp(2), dp(2), dp(6));

LinearLayout mediaOpt = new LinearLayout(act);
mediaOpt.setOrientation(LinearLayout.HORIZONTAL);
mediaOpt.setGravity(android.view.Gravity.CENTER_VERTICAL);

FrameLayout boxM = new FrameLayout(act);
GradientDrawable sqM = new GradientDrawable();
try{
    sqM.setColor(Color.TRANSPARENT);
    sqM.setCornerRadius(dp(4));
    sqM.setStroke(2, Color.parseColor(C_ACCENT));
}catch(Exception __ignored){}
boxM.setBackground(sqM);
LinearLayout.LayoutParams lpBoxM = new LinearLayout.LayoutParams(dp(20), dp(20));
mediaOpt.addView(boxM, lpBoxM);

final TextView tickM = new TextView(act);
tickM.setText("✓");
try{ tickM.setTextColor(Color.parseColor("#22BB66")); }catch(Exception __ignored){}
tickM.setTextSize(14f);
tickM.setGravity(android.view.Gravity.CENTER);
boxM.addView(tickM, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
tickM.setVisibility(cbMoment.isChecked()? View.INVISIBLE : View.VISIBLE);

TextView labM = new TextView(act);
labM.setText("图片/视频");
styleTextPrimary(labM);
LinearLayout.LayoutParams lpLabM = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
lpLabM.leftMargin = dp(8);
mediaOpt.addView(labM, lpLabM);

LinearLayout momentOpt = new LinearLayout(act);
momentOpt.setOrientation(LinearLayout.HORIZONTAL);
momentOpt.setGravity(android.view.Gravity.CENTER_VERTICAL);

FrameLayout boxF = new FrameLayout(act);
GradientDrawable sqF = new GradientDrawable();
try{
    sqF.setColor(Color.TRANSPARENT);
    sqF.setCornerRadius(dp(4));
    sqF.setStroke(2, Color.parseColor(C_ACCENT));
}catch(Exception __ignored){}
boxF.setBackground(sqF);
LinearLayout.LayoutParams lpBoxF = new LinearLayout.LayoutParams(dp(20), dp(20));
momentOpt.addView(boxF, lpBoxF);

final TextView tickF = new TextView(act);
tickF.setText("✓");
try{ tickF.setTextColor(Color.parseColor("#22BB66")); }catch(Exception __ignored){}
tickF.setTextSize(14f);
tickF.setGravity(android.view.Gravity.CENTER);
boxF.addView(tickF, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
tickF.setVisibility(cbMoment.isChecked()? View.VISIBLE : View.INVISIBLE);

TextView labF = new TextView(act);
labF.setText("朋友圈（仅图片）");
styleTextPrimary(labF);
LinearLayout.LayoutParams lpLabF = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
lpLabF.leftMargin = dp(8);
momentOpt.addView(labF, lpLabF);

View spacerMode = new View(act);
spacerMode.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

View.OnClickListener chooseMedia = new View.OnClickListener(){ public void onClick(View v){
    try{
        cbMedia.setChecked(true); cbMoment.setChecked(false);
        tickM.setVisibility(View.VISIBLE); tickF.setVisibility(View.INVISIBLE);
        if(onModeChangedRef[0]!=null) onModeChangedRef[0].run();
    }catch(Exception __ignored){}
}};
View.OnClickListener chooseMoment = new View.OnClickListener(){ public void onClick(View v){
    try{
        cbMedia.setChecked(false); cbMoment.setChecked(true);
        tickM.setVisibility(View.INVISIBLE); tickF.setVisibility(View.VISIBLE);
        if(onModeChangedRef[0]!=null) onModeChangedRef[0].run();
    }catch(Exception __ignored){}
}};
mediaOpt.setOnClickListener(chooseMedia);
boxM.setOnClickListener(chooseMedia);
labM.setOnClickListener(chooseMedia);

momentOpt.setOnClickListener(chooseMoment);
boxF.setOnClickListener(chooseMoment);
labF.setOnClickListener(chooseMoment);

try{
    if(cbMoment.isChecked()){
        tickM.setVisibility(View.INVISIBLE); tickF.setVisibility(View.VISIBLE);
    }else{
        tickM.setVisibility(View.VISIBLE); tickF.setVisibility(View.INVISIBLE);
    }
}catch(Exception __ignored){}

modeRow.addView(mediaOpt);
modeRow.addView(spacerMode);
modeRow.addView(momentOpt);
cm.addView(modeRow);

        final LinearLayout previewUnified=new LinearLayout(act); previewUnified.setOrientation(LinearLayout.VERTICAL); previewUnified.setPadding(dp(8),dp(4),dp(8),dp(8));
        cm.addView(previewUnified);
        final LinearLayout listWrapU=new LinearLayout(act); listWrapU.setOrientation(LinearLayout.VERTICAL); cm.addView(listWrapU);
        final LinearLayout listM=new LinearLayout(act); listM.setOrientation(LinearLayout.VERTICAL); listWrapU.addView(listM);
        final LinearLayout listF=new LinearLayout(act); listF.setOrientation(LinearLayout.VERTICAL); listWrapU.addView(listF); listF.setVisibility(View.GONE);
        final Runnable[] updatePreviewRef=new Runnable[1];
        final Runnable renderM=new Runnable(){ public void run(){ renderMediaList2(act, listM, new Runnable(){ public void run(){ try{ if(updatePreviewRef[0]!=null) updatePreviewRef[0].run(); }catch(Exception __ignored){} }}); } };
        final Runnable renderF=new Runnable(){ public void run(){ renderImageList2(act, listF, new Runnable(){ public void run(){ try{ if(updatePreviewRef[0]!=null) updatePreviewRef[0].run(); }catch(Exception __ignored){} }}); } };
        renderM.run();
        LinearLayout rowU=new LinearLayout(act); rowU.setOrientation(LinearLayout.HORIZONTAL);
        Button allU=btn(act,"全选"); Button revU=btn(act,"反选"); Button delU=btn(act,"删除"); Button importU=btn(act,"从相册导入");
        rowU.addView(allU); rowU.addView(revU); rowU.addView(delU); View spU=new View(act); spU.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); rowU.addView(spU); rowU.addView(importU);
        cm.addView(rowU);
        final EditText etTimeU=makeTimeInput(act); cm.addView(etTimeU);
        final boolean[] repeatU=new boolean[]{false}; addRepeatSwitch(act, cm, repeatU);
        final EditText etMomentText=new EditText(act); etMomentText.setHint("朋友圈文字内容（可留空仅发图片）"); styleEdit(etMomentText);
        etMomentText.setMaxLines(6); final Button saveU=btn(act,"保存媒体定时任务"); cm.addView(saveU, lps);
        final Runnable updatePreview=new Runnable(){ public void run(){
        try{
        if(cbMoment.isChecked()){
        renderSelectedPreviewLive(act, previewUnified, listF, true);
        }else{
        renderSelectedPreviewLive(act, previewUnified, listM, false);
        }
        }catch(Exception __ignored){}
        }};
        updatePreviewRef[0]=updatePreview; updatePreview.run();
        onModeChangedRef[0] = new Runnable(){ public void run(){
    try{
        boolean moment = cbMoment.isChecked();
        cbMedia.setChecked(!moment);
        cbMoment.setChecked(moment);

        listM.setVisibility(moment ? View.GONE : View.VISIBLE);
        listF.setVisibility(moment ? View.VISIBLE : View.GONE);

        if(moment){

        try{
        android.view.ViewParent p = etMomentText.getParent();
        if(p instanceof android.view.ViewGroup){
        ((android.view.ViewGroup)p).removeView(etMomentText);
        }
        }catch(Exception __ignored){}
        int anchorIndex = Math.max(0, cm.indexOfChild(previewUnified));
        cm.addView(etMomentText, anchorIndex);
        saveU.setText("保存朋友圈定时任务");
        renderF.run();
        }else{

        try{ cm.removeView(etMomentText); }catch(Exception __ignored){}
        saveU.setText("保存媒体定时任务");
        renderM.run();
        }

        updatePreview.run();
    }catch(Exception __ignored){}
}};
cbMedia.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ cbMedia.setChecked(true); cbMoment.setChecked(false); onModeChangedRef[0].run(); }});
        cbMoment.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ cbMoment.setChecked(true); cbMedia.setChecked(false); onModeChangedRef[0].run(); }});
        onModeChangedRef[0].run();
        allU.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        LinearLayout L = cbMoment.isChecked()? listF : listM;
        for(int i=0;i<L.getChildCount();i++){ try{
        Object tag=L.getChildAt(i).getTag();
        if(tag instanceof Object[]){ Object[] arr=(Object[])tag; if(arr.length>=3){ arr[1]=Boolean.TRUE; try{ ((android.view.View)arr[2]).setVisibility(View.VISIBLE); }catch(Exception __ignored){} } }
        }catch(Exception __ignored){} }
        updatePreview.run();
        }});
        revU.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        LinearLayout L = cbMoment.isChecked()? listF : listM;
        for(int i=0;i<L.getChildCount();i++){ try{
        Object tag=L.getChildAt(i).getTag();
        if(tag instanceof Object[]){ Object[] arr=(Object[])tag; if(arr.length>=3){ boolean b=((Boolean)arr[1]).booleanValue(); arr[1]=Boolean.valueOf(!b); try{ ((android.view.View)arr[2]).setVisibility(!b?View.VISIBLE:View.INVISIBLE); }catch(Exception __ignored){} } }
        }catch(Exception __ignored){} }
        updatePreview.run();
        }});
        delU.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        LinearLayout L = cbMoment.isChecked()? listF : listM;
        int del=0;
        for(int i=L.getChildCount()-1;i>=0;i--){ try{
        Object tag=L.getChildAt(i).getTag();
        if(tag instanceof Object[]){ Object[] arr=(Object[])tag; if(arr.length>=3){ boolean b=((Boolean)arr[1]).booleanValue(); if(b){ String p=String.valueOf(arr[0]); try{ java.io.File f=new java.io.File(p); if(f.exists() && f.isFile()){ if(f.delete()) del++; } }catch(Exception __ignored){} } } }
        }catch(Exception __ignored){} }
        if(del>0) uiToast("已删除 "+del+" 项");
        if(cbMoment.isChecked()) renderF.run(); else renderM.run();
        updatePreview.run();
        }});
        importU.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        boolean imgOnly = cbMoment.isChecked();
        openAlbumPicker(act, imgOnly, new Runnable(){ public void run(){
        if(cbMoment.isChecked()) renderF.run(); else renderM.run();
        try{ if(updatePreviewRef[0]!=null) updatePreviewRef[0].run(); }catch(Exception __ignored){}
        }});
        }});
        saveU.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        try{
        long when=parseTime(String.valueOf(etTimeU.getText()).trim()); if(when<=0){ uiToast("时间格式不正确：yyyy-MM-dd HH:mm"); return; }
        java.util.List paths=new java.util.ArrayList();
        LinearLayout L = cbMoment.isChecked()? listF : listM;
        for(int i=0;i<L.getChildCount();i++){
        Object tag=L.getChildAt(i).getTag();
        if(tag instanceof Object[]){
        Object[] arr=(Object[])tag;
        if(arr.length>=3){
        String p=String.valueOf(arr[0]);
        boolean sel=((Boolean)arr[1]).booleanValue();
        if(sel){
        if(cbMoment.isChecked() && isMp4(p)) continue;
        java.io.File f=new java.io.File(p); if(f.exists() && f.isFile()) paths.add(p);
        }
        }
        }
        }
        if(paths.size()==0){ uiToast("请至少选择一个媒体文件"); return; }
        String id = (cbMoment.isChecked()? "F" : "M") + System.currentTimeMillis() + "_" + (int)(Math.random()*1000000);
        if(cbMoment.isChecked()){
        String content=String.valueOf(etMomentText.getText());
        String line=taskLine(id, TYPE_MOMENT, uiTalkerId, uiTalkerName, when, repeatU[0], content, paths);
        synchronized(TASK_LOCK){ java.util.List ls=readTaskLines(); ls.add(line); writeTaskLines(ls); }
        rescheduleDispatcher();
        uiToast("已保存（朋友圈）："+fmtTime(when)+(repeatU[0]?"，每天重复":"，一次性"));
        }else{
        String line=taskLine(id, TYPE_MEDIA, uiTalkerId, uiTalkerName, when, repeatU[0], "", paths);
        synchronized(TASK_LOCK){ java.util.List ls=readTaskLines(); ls.add(line); writeTaskLines(ls); }
        rescheduleDispatcher();
        uiToast("已保存（媒体）："+fmtTime(when)+(repeatU[0]?"，每天重复":"，一次性"));
        }
        }catch(Throwable e){ uiToast("保存失败"); }
        }});
        LinearLayout bodyTask=new LinearLayout(act); bodyTask.setOrientation(LinearLayout.VERTICAL); bodyTask.setPadding(dp(4),dp(8),dp(4),dp(8));
        scrollTasks.addView(bodyTask);
        LinearLayout topOps=new LinearLayout(act); topOps.setOrientation(LinearLayout.HORIZONTAL);
        bodyTask.addView(topOps);
        final LinearLayout listWrap=new LinearLayout(act); listWrap.setOrientation(LinearLayout.VERTICAL); bodyTask.addView(listWrap);
        final Runnable[] renderListRef=new Runnable[1];



void deleteTaskByLine(String lineToDel){
    if(lineToDel==null) return;
    synchronized(TASK_LOCK){
        java.util.List ls = readTaskLines();
        java.util.List out = new java.util.ArrayList();
        boolean removed = false;
        for(int j=0;j<ls.size();j++){
        String ln = String.valueOf(ls.get(j));
        if(!removed && ln!=null && ln.equals(lineToDel)){
        removed = true;
        continue;
        }
        out.add(ln);
        }
        writeTaskLines(out);
    }
    rescheduleDispatcher();
}

final Runnable renderList=new Runnable(){ public void run(){
        listWrap.removeAllViews();
        java.util.List lines = readTaskLines();
        if(lines.size()==0){
        TextView empty=new TextView(act); empty.setText("暂无任务"); styleTextSecondary(empty); listWrap.addView(empty); return;
        }
        for(int i=0;i<lines.size();i++){
        final String line=String.valueOf(lines.get(i)); final java.util.Map t=parseTask(line); if(t==null) continue;
        final String id=String.valueOf(t.get("id"));
        int type=((Integer)t.get("type")).intValue();
        final String targetId=(String)t.get("targetId");
        final String targetName=(String)t.get("targetName");
        final long time=((Long)t.get("time")).longValue();
        final boolean repeat=((Boolean)t.get("repeat")).booleanValue();
        LinearLayout row=new LinearLayout(act); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackground(shapeStroke(C_CARD_BG,14,C_CARD_STROKE)); try{ row.setElevation(5f);}catch(Exception __ignored){}
        TextView t1=new TextView(act); t1.setText(fmtTime(time)+"  ·  "+(type==1?"文本":type==2?"媒体":"朋友圈")+(repeat?"（每天）":"（一次）")); styleTextPrimary(t1); row.addView(t1);

try{

    android.view.View t1v = t1;
    try{ row.removeView(t1v); }catch(Exception __ignored){}
    LinearLayout header = new LinearLayout(act);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(android.view.Gravity.CENTER_VERTICAL);

    LinearLayout.LayoutParams lpT1 = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    header.addView(t1v, lpT1);

    LinearLayout thumbsRow = new LinearLayout(act);
    thumbsRow.setOrientation(LinearLayout.HORIZONTAL);
    int edge = dp(32);
    int spacing = dp(6);
    int shown = 0;
    try{
        java.util.List ps = (java.util.List)t.get("paths");
        if(ps!=null){
        for(int k=0;k<ps.size() && shown<3;k++){
        String pth = String.valueOf(ps.get(k));
        if(pth==null || pth.length()==0) continue;
        boolean isV = (isMp4(pth) || ("boolean isVideoExt(String n)"!=null && isVideoExt(pth)));
        boolean isI = isImg(pth);
        if(!isV && !isI) continue;
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        android.graphics.Bitmap bm = null;
        try{
        bm = isV ? frameFromVideo(pth, edge) : decodeSampledFromUri(pth, edge);
        }catch(Exception __ignored){}
        if(bm!=null){
        if(thumbsRow.getChildCount()>0){
        View sp = new View(act);
        sp.setMinimumWidth(spacing);
        thumbsRow.addView(sp, new LinearLayout.LayoutParams(spacing, 1));
        }
        thumbsRow.addView(iv, new LinearLayout.LayoutParams(edge, edge));
        iv.setImageBitmap(bm);
        shown++;
        }
        }
        }
    }catch(Exception __ignored){}
    if(thumbsRow.getChildCount()>0){
        LinearLayout.LayoutParams lpThumbs = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpThumbs.leftMargin = dp(8);
        header.addView(thumbsRow, lpThumbs);
    }

    int insertIndex = 0;
    try{ insertIndex = 0; }catch(Exception __ignored){}
    row.addView(header, insertIndex);
}catch(Exception __ignored){}

        TextView t2=new TextView(act); String tg=(targetName!=null&&targetName.length()>0)?targetName:targetId; t2.setText("目标："+(tg==null?"":tg)); t2.setTextSize(12); styleTextSecondary(t2); row.addView(t2);

try{
    String contentPrev = String.valueOf(t.get("content"));
    if(contentPrev!=null && contentPrev.trim().length()>0){
        TextView tvPreview = new TextView(act);
        tvPreview.setText(contentPrev);
        tvPreview.setMaxLines(2);
        tvPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        styleTextSecondary(tvPreview);
        LinearLayout.LayoutParams lpPrev = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpPrev.topMargin = dp(2);
        row.addView(tvPreview, lpPrev);
    }
}catch(Exception __ignored){}
        LinearLayout ops=new LinearLayout(act); ops.setOrientation(LinearLayout.HORIZONTAL);
        Button del=btn(act,"删除"); try{ del.setTag(line); }catch(Exception __ignored){} del.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        String lineToDel = String.valueOf(v.getTag()); deleteTaskByLine(lineToDel); listWrap.post(renderList);}});
        Button delay=btn(act,"延期一天"); delay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        synchronized(TASK_LOCK){
        java.util.List ls=readTaskLines(); java.util.List out=new java.util.ArrayList();
        for(int j=0;j<ls.size();j++){
        String ln=String.valueOf(ls.get(j)); java.util.Map mm=parseTask(ln); if(mm==null) continue;
        if(String.valueOf(mm.get("id")).equals(id)){
        long nt=calcNextDaySameTime(((Long)mm.get("time")).longValue());
        out.add(taskLine(id, ((Integer)mm.get("type")).intValue(), (String)mm.get("targetId"), (String)mm.get("targetName"), nt, ((Boolean)mm.get("repeat")).booleanValue(), (String)mm.get("content"), (java.util.List)mm.get("paths")));
        }else out.add(ln);
        }
        writeTaskLines(out);
        }
        rescheduleDispatcher();
        listWrap.post(renderList);
        }});
        ops.addView(del); ops.addView(delay); row.addView(ops);
        LinearLayout.LayoutParams lpRow=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpRow.bottomMargin=dp(8);
        listWrap.addView(row, lpRow);
}
        }};
        renderListRef[0]=renderList;
        View.OnClickListener tabClick=new View.OnClickListener(){ public void onClick(View v){
        boolean showTasks = (v == tabTasks);
        scrollSettings.setVisibility(showTasks?View.GONE:View.VISIBLE);
        scrollTasks.setVisibility(showTasks?View.VISIBLE:View.GONE);
        try{
        if(showTasks){
        tabSettings.setBackground(shapeStroke(C_BUTTON_BG,16,C_CARD_STROKE)); tabSettings.setTextColor(Color.parseColor(C_BUTTON_TEXT));
        tabTasks.setBackground(shapeStroke(C_ACCENT,16,C_CARD_STROKE)); tabTasks.setTextColor(Color.parseColor("#FFFFFF"));
        if(renderListRef[0]!=null) listWrap.post(renderListRef[0]);
        }else{
        tabSettings.setBackground(shapeStroke(C_ACCENT,16,C_CARD_STROKE)); tabSettings.setTextColor(Color.parseColor("#FFFFFF"));
        tabTasks.setBackground(shapeStroke(C_BUTTON_BG,16,C_CARD_STROKE)); tabTasks.setTextColor(Color.parseColor(C_BUTTON_TEXT));
        }
        }catch(Exception __ignored){}
        }};
        tabSettings.setOnClickListener(tabClick);
        tabTasks.setOnClickListener(tabClick);
        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent); d.requestWindowFeature(Window.FEATURE_NO_TITLE); d.setContentView(root);
        try{ d.show(); }catch(Throwable e){ throw e; }
        Window w=d.getWindow(); if(w!=null){ WindowManager.LayoutParams lp=w.getAttributes(); lp.width=Math.min((int)(width*0.92f), width-48); w.setAttributes(lp); }
    }catch(Throwable e){ log2("showScheduleDialog err: "+e); }
}});}
void onLoad(){ try{ putBoolean(KEY_MOMENT_SAFE, true);}catch(Exception __ignored){} startSchedulerIfNeed(); }
void onUnLoad(){
 stopScheduler();
 try{ if(THUMB_POOL!=null){ THUMB_POOL.shutdownNow(); THUMB_POOL=null; } }catch(Exception __ignored){}
 try{ if(THUMB_CACHE!=null){ THUMB_CACHE.evictAll(); THUMB_CACHE=null; } }catch(Exception __ignored){}
}
boolean onLongClickSendBtn(String text){
 String cmd=norm(text);
 if(containsCmd(cmd, CMD_OPEN) || containsCmd(cmd, "/"+CMD_OPEN)){
 try{
 String talker=""; try{ talker=getTargetTalker(); }catch(Exception __ignored){}
 uiTalkerId=talker; try{ uiTalkerName=getFriendName(uiTalkerId);}catch(Throwable ignore){ uiTalkerName=""; }
 if(uiTalkerId==null||uiTalkerId.length()==0){ String lastId=getString(KEY_LAST_TARGET_ID,""); String lastName=getString(KEY_LAST_TARGET_NAME,""); if(lastId!=null&&lastId.length()>0){ uiTalkerId=lastId; uiTalkerName=(lastName==null?"":lastName);} }
 }catch(Exception __ignored){}
 try{ if(uiTalkerId!=null&&uiTalkerId.length()>0){ putString(KEY_LAST_TARGET_ID, uiTalkerId); putString(KEY_LAST_TARGET_NAME, uiTalkerName==null?"":uiTalkerName);} }catch(Exception __ignored){}
 showScheduleDialogSafe(); return true;
 }
 if(containsCmd(cmd, CMD_TARGET)){
 String talker="", name=""; try{ talker=getTargetTalker(); name=getFriendName(talker);}catch(Exception __ignored){}
 if(talker!=null){ uiTalkerId=talker; uiTalkerName=(name==null?"":name); try{ putString(KEY_LAST_TARGET_ID, uiTalkerId); putString(KEY_LAST_TARGET_NAME, uiTalkerName);}catch(Exception __ignored){} }
 if(name==null) name=""; uiToast("本次目标会话："+(name.length()>0?name:talker)); return true;
 }
 return false;
}
void onHandleMsg(Object msgInfoBean){
 try{
 MsgInfo m=(MsgInfo)msgInfoBean; if(!m.isText()) return; String content=norm(m.getContent()); if(!m.isSend()) return;
 if(containsCmd(content, CMD_OPEN) || containsCmd(content, "/"+CMD_OPEN)){
 try{
 uiTalkerId=m.getTalker(); try{ uiTalkerName=getFriendName(uiTalkerId);}catch(Throwable ignore){ uiTalkerName=""; }
 if(uiTalkerId==null||uiTalkerId.length()==0){ String lastId=getString(KEY_LAST_TARGET_ID,""); String lastName=getString(KEY_LAST_TARGET_NAME,""); if(lastId!=null&&lastId.length()>0){ uiTalkerId=lastId; uiTalkerName=(lastName==null?"":lastName);} }
 }catch(Exception __ignored){}
 try{ if(uiTalkerId!=null&&uiTalkerId.length()>0){ putString(KEY_LAST_TARGET_ID, uiTalkerId); putString(KEY_LAST_TARGET_NAME, uiTalkerName==null?"":uiTalkerName);} }catch(Exception __ignored){}
 showScheduleDialogSafe(); try{ revokeMsg(m.getMsgId()); }catch(Exception __ignored){}
 } else if(containsCmd(content, CMD_TARGET)){
 String roomId=m.getTalker(); String name=""; try{ name=getFriendName(roomId);}catch(Exception __ignored){}
 if(roomId!=null){ uiTalkerId=roomId; uiTalkerName=(name==null?"":name); try{ putString(KEY_LAST_TARGET_ID, uiTalkerId); putString(KEY_LAST_TARGET_NAME, uiTalkerName);}catch(Exception __ignored){} }
 if(name==null) name=""; uiToast("本次目标会话："+(name.length()>0?name:roomId)); try{ revokeMsg(m.getMsgId()); }catch(Exception __ignored){}
 }
 }catch(Exception __ignored){}
}

List scanAlbumFiles(boolean imagesOnly){
 List out=new ArrayList();
 try{
  String[] roots=new String[]{"/storage/emulated/0/DCIM/Camera","/storage/emulated/0/DCIM/Screenshots","/storage/emulated/0/Pictures","/storage/emulated/0/Download","/sdcard/DCIM/Camera","/sdcard/Pictures","/sdcard/Download"};
  for(int i=0;i<roots.length;i++){
   try{
    File d=new File(roots[i]); if(!d.exists()||!d.isDirectory()) continue;
    File[] fs=d.listFiles(); if(fs==null) continue;
    for(int k=0;k<fs.length;k++){
     File f=fs[k]; if(f==null||!f.isFile()) continue;
     String n=f.getName();
     if(imagesOnly){ if(isImg(n)) out.add(f); } else { if(isImg(n)||isMp4(n)) out.add(f); }
    }
   }catch(Exception __ignored){}
  }
  Collections.sort(out,new Comparator(){ public int compare(Object a,Object b){ long da=((File)a).lastModified(), db=((File)b).lastModified(); return db>da?1:db<da?-1:0; } });
 }catch(Exception __ignored){}
 return out;
}

File copyToMediaDir(String srcPath){ return copyToMediaDir(getTopActivity(), srcPath); }
File copyToMediaDir(android.content.Context ctx, String srcPath){
    try{
        File dir=new File(MEDIA_DIR); if(!dir.exists()) dir.mkdirs();
        boolean isContent = srcPath!=null && srcPath.startsWith("content://");
        String name;
        if(isContent){
        String ext=".bin";
        try{
        android.net.Uri u=android.net.Uri.parse(srcPath);
        android.content.ContentResolver cr=(ctx!=null? ctx.getContentResolver() : null);
        if(cr==null){
        try{ android.app.Activity a=getTopActivity(); if(a!=null) cr=a.getContentResolver(); }catch(Exception __ignored){}
        }
        String mime = (cr!=null? cr.getType(u) : null);
        ext = extForMime(mime);
        }catch(Exception __ignored){}
        name="import_"+System.currentTimeMillis()+ext;
        }else{
        name=new File(srcPath).getName();
        }
        File dst=new File(dir, name);
        java.io.InputStream in=null;
        java.io.OutputStream out=null;
        try{
        if(isContent){
        android.net.Uri u=android.net.Uri.parse(srcPath);
        android.content.ContentResolver cr=(ctx!=null? ctx.getContentResolver() : null);
        if(cr==null){
        try{ android.app.Activity a=getTopActivity(); if(a!=null) cr=a.getContentResolver(); }catch(Exception __ignored){}
        }
        if(cr!=null){
        in=cr.openInputStream(u);
        }
        }else{
        in=new java.io.FileInputStream(new File(srcPath));
        }
        if(in==null){ return null; }
        out=new java.io.FileOutputStream(dst);
        byte[] buf=new byte[8192]; int n;
        while((n=in.read(buf))>0){ out.write(buf,0,n); }
        }catch(Throwable e){ return null; }
        finally{
        try{ if(in!=null) in.close(); }catch(Exception __ignored){}
        try{ if(out!=null) out.close(); }catch(Exception __ignored){}
        }
        return dst;
    }catch(Throwable e){ return null; }
}

void openAlbumPicker(final Activity act, final boolean imagesOnly, final Runnable onDone){
 try{
  final Dialog d=new Dialog(act, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
  LinearLayout root=new LinearLayout(act); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor(C_BG_ROOT));
  TextView title=new TextView(act); title.setText(imagesOnly?"导入相册（仅图片）":"导入相册（图片/视频）"); title.setPadding(dp(16),dp(16),dp(16),dp(8)); title.setTextSize(18); title.setTypeface(Typeface.DEFAULT_BOLD); styleTextPrimary(title); root.addView(title);

  final LinearLayout filterRow = new LinearLayout(act);
  filterRow.setOrientation(LinearLayout.HORIZONTAL);
  filterRow.setPadding(dp(12), dp(8), dp(12), dp(4));
  final Button btnImg = btn(act,"图片");
  final Button btnVid = btn(act,"视频");
  View spacer = new View(act);
  spacer.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f));
  filterRow.addView(btnImg);
  filterRow.addView(spacer);
  filterRow.addView(btnVid);
  root.addView(filterRow);
  final LinearLayout listWrap=new LinearLayout(act); listWrap.setOrientation(LinearLayout.VERTICAL);
  ScrollView sc=new ScrollView(act); sc.addView(listWrap); root.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
  LinearLayout ops=new LinearLayout(act); ops.setOrientation(LinearLayout.HORIZONTAL); ops.setPadding(dp(12),dp(8),dp(12),dp(12));
  Button btnCancel=btn(act,"取消"); Button btnImport=btn(act,"完成");
  ops.addView(btnCancel); View sp=new View(act); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); ops.addView(sp); ops.addView(btnImport); root.addView(ops);
  d.setContentView(root);

  LinearLayout loading=new LinearLayout(act); loading.setOrientation(LinearLayout.HORIZONTAL); loading.setGravity(android.view.Gravity.CENTER_VERTICAL);
  ProgressBar pb=new ProgressBar(act); TextView msg=new TextView(act); msg.setText("正在加载相册…"); msg.setPadding(dp(8),0,0,0); styleTextSecondary(msg);
  loading.addView(pb); loading.addView(msg); listWrap.addView(loading);
  new Thread(new Runnable(){ public void run(){
      java.util.List items=null;
      try{ items=scanAlbumItemsMediaStore(imagesOnly);}catch(Exception __ignored){}
      if(items==null || items.size()==0){
        try{
        java.util.List fs=scanAlbumFiles(imagesOnly);
        items=new java.util.ArrayList();
        for(int i=0;i<fs.size();i++){
        java.io.File f=(java.io.File)fs.get(i); if(f==null) continue;
        String u=android.net.Uri.fromFile(f).toString();
        boolean isV=isMp4(f.getName());
        items.add(new Object[]{u, Boolean.valueOf(isV), f.getName()});
        }
        }catch(Exception __ignored){}
      }
      final java.util.List fin=(items==null?new java.util.ArrayList():items);
      Activity a=getTopActivity(); if(a==null) a=act;
      final Activity ui=a;
      if(ui!=null) ui.runOnUiThread(new Runnable(){ public void run(){ try{

    final java.util.List finImg = new java.util.ArrayList();
    final java.util.List finVid = new java.util.ArrayList();
    for(int i=0;i<fin.size();i++){
        Object[] it = (Object[]) fin.get(i);
        boolean isV = ((Boolean) it[1]).booleanValue();
        if(isV) finVid.add(it); else finImg.add(it);
    }

    btnImg.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        try{ renderAlbumGridUriAuto(ui, listWrap, finImg, true); }catch(Exception __ignored){}
    }});
    btnVid.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
        try{ renderAlbumGridUriAuto(ui, listWrap, finVid, false); }catch(Exception __ignored){}
    }});

    if(imagesOnly){ try{ btnVid.setEnabled(false); }catch(Exception __ignored){} }
    renderAlbumGridUriAuto(ui, listWrap, finImg, true);
} catch(Exception __ignored){} }});
  }}).start();
  btnCancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ try{ d.dismiss(); }catch(Exception __ignored){} }});

btnImport.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
   try{
     java.util.List picked=new java.util.ArrayList();
     for(int i=0;i<listWrap.getChildCount();i++){
        View child=listWrap.getChildAt(i);
        if(child instanceof android.widget.GridLayout){
        android.widget.GridLayout grid=(android.widget.GridLayout)child;
        for(int k=0;k<grid.getChildCount();k++){
        View tile=grid.getChildAt(k);
        Object tag=tile.getTag();
        if(tag instanceof Object[]){
        Object[] arr=(Object[])tag;
        if(arr.length>=2){
        String u=String.valueOf(arr[0]);
        boolean sel=((Boolean)arr[1]).booleanValue();
        if(sel){ if(imagesOnly && isMp4(u)) continue; picked.add(u); }
        }
        }
        }
        }
     }
     if(picked.size()==0){ uiToast("请先选择要导入的媒体"); return; }
     java.util.List imported=new java.util.ArrayList();
     for(int i=0;i<picked.size();i++){
        String u=String.valueOf(picked.get(i));
        File dst=copyToMediaDir(act, u);
        if(dst!=null) imported.add(dst.getAbsolutePath());
     }
     if(imported.size()==0){ uiToast("复制素材失败"); return; }
     try{
        if(imagesOnly){ LAST_IMPORTED_MOMENTS.clear(); LAST_IMPORTED_MOMENTS.addAll(imported); }
        else{ LAST_IMPORTED_MEDIA.clear(); LAST_IMPORTED_MEDIA.addAll(imported); }
     }catch(Exception __ignored){}
     try{ d.dismiss(); }catch(Exception __ignored){}
     if(onDone!=null){ try{ onDone.run(); }catch(Throwable err){ try{ log("onDone.run error: "+err); }catch(Exception __ignored){} } }
   }catch(Throwable e){ try{ log("openAlbumPicker.import exception: "+e); }catch(Exception __ignored){} }
}});

  d.show();
 }catch(Throwable e){ uiToast("打开相册失败"); }
}

java.util.List scanAlbumItemsMediaStore(boolean imagesOnly){
    java.util.List out=new java.util.ArrayList();
    try{
        android.app.Activity act=getTopActivity(); if(act==null) return out;
        android.net.Uri uriI=android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        android.net.Uri uriV=android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projI=new String[]{android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME, android.provider.MediaStore.Images.Media.DATE_MODIFIED};
        String[] projV=new String[]{android.provider.MediaStore.Video.Media._ID, android.provider.MediaStore.Video.Media.DISPLAY_NAME, android.provider.MediaStore.Video.Media.DATE_MODIFIED};
        android.database.Cursor c=null;
        try{
        c=act.getContentResolver().query(uriI, projI, null, null, android.provider.MediaStore.Images.Media.DATE_MODIFIED+" DESC");
        if(c!=null){
        int idxId=c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID);
        int idxName=c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME);
        while(c.moveToNext()){
        long id=c.getLong(idxId);
        String name=c.getString(idxName);
        android.net.Uri u=android.content.ContentUris.withAppendedId(uriI, id);
        out.add(new Object[]{u.toString(), Boolean.FALSE, name});
        if(out.size()>=800) break;
        }
        }
        }catch(Exception __ignored){} finally{ try{ if(c!=null) c.close(); }catch(Exception __ignored){} }
        if(!imagesOnly){
        try{
        c=act.getContentResolver().query(uriV, projV, null, null, android.provider.MediaStore.Video.Media.DATE_MODIFIED+" DESC");
        if(c!=null){
        int idxId=c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID);
        int idxName=c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME);
        while(c.moveToNext()){
        long id=c.getLong(idxId);
        String name=c.getString(idxName);
        android.net.Uri u=android.content.ContentUris.withAppendedId(uriV, id);
        out.add(new Object[]{u.toString(), Boolean.TRUE, name});
        if(out.size()>=1200) break;
        }
        }
        }catch(Exception __ignored){} finally{ try{ if(c!=null) c.close(); }catch(Exception __ignored){} }
        }
    }catch(Exception __ignored){}
    return out;
}

android.view.View buildTileUri(final android.app.Activity act, final String uri, final boolean isVideo, final int edge)
{

    android.widget.FrameLayout tile = new android.widget.FrameLayout(act);
    tile.setPadding(0, 0, 0, 0);

    android.widget.LinearLayout row = new android.widget.LinearLayout(act);
    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    android.widget.FrameLayout.LayoutParams rowLp = new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, edge);
    tile.addView(row, rowLp);

    int sideBar = dp(32);

    int imgW = edge - sideBar;

    android.widget.ImageView iv = new android.widget.ImageView(act);
    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
    row.addView(iv, new android.widget.LinearLayout.LayoutParams(imgW, edge));
    loadThumbAsyncUri(iv, uri, isVideo, Math.min(imgW, edge));

    android.widget.LinearLayout col = new android.widget.LinearLayout(act);
    col.setOrientation(android.widget.LinearLayout.VERTICAL);
    col.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
    col.setPadding(0, dp(6), 0, 0);
    row.addView(col, new android.widget.LinearLayout.LayoutParams(sideBar, edge));

    android.widget.FrameLayout box = new android.widget.FrameLayout(act);
    android.graphics.drawable.GradientDrawable sq = new android.graphics.drawable.GradientDrawable();
    try{
        sq.setColor(android.graphics.Color.TRANSPARENT);
        sq.setCornerRadius(dp(4));
        sq.setStroke(2, android.graphics.Color.parseColor(C_ACCENT));
    }catch(Exception __ignored){}
    box.setBackground(sq);
    android.widget.LinearLayout.LayoutParams lpBox = new android.widget.LinearLayout.LayoutParams(dp(20), dp(20));
    col.addView(box, lpBox);

    final android.widget.TextView tick = new android.widget.TextView(act);
    tick.setText("✓");
    try{ tick.setTextColor(android.graphics.Color.parseColor("#22BB66")); }catch(Exception __ignored){}
    tick.setTextSize(14f);
    tick.setGravity(android.view.Gravity.CENTER);
    box.addView(tick, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
    tick.setVisibility(android.view.View.INVISIBLE);

    tile.setTag(new Object[]{ uri, Boolean.FALSE, tick });

    android.view.View.OnClickListener toggle = new android.view.View.OnClickListener(){ public void onClick(android.view.View v){
        try{
        Object[] arr = (Object[]) tile.getTag();
        boolean sel = !((Boolean)arr[1]).booleanValue();
        arr[1] = Boolean.valueOf(sel);
        tile.setTag(arr);
        android.view.View t = (android.view.View)arr[2];
        t.setVisibility(sel ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        }catch(Exception __ignored){}
    }};
    tile.setOnClickListener(toggle);
    box.setOnClickListener(toggle);
    iv.setOnClickListener(toggle);

    return tile;
}




java.util.concurrent.ExecutorService THUMB_POOL=null;
android.util.LruCache THUMB_CACHE=null;
android.util.LruCache getThumbCache(){
    if(THUMB_CACHE==null){
        int max=(int)(Runtime.getRuntime().maxMemory()/1024);
        int cacheSize = max/16;
        THUMB_CACHE=new android.util.LruCache(cacheSize){
        protected int sizeOf(Object key, Object value){
        if(value instanceof android.graphics.Bitmap){
        return ((android.graphics.Bitmap)value).getByteCount()/1024;
        }
        return 1;
        }
        };
    }
    return THUMB_CACHE;
}
java.util.concurrent.ExecutorService getThumbPool(){
    if(THUMB_POOL==null){
        THUMB_POOL=java.util.concurrent.Executors.newFixedThreadPool(2);
    }
    return THUMB_POOL;
}
android.graphics.Bitmap decodeSampledFromUri(String uri, int req){
    try{
        if(uri==null) return null;
        if(uri.startsWith("content://")){
        android.app.Activity act=getTopActivity(); if(act==null) return null;
        android.content.ContentResolver cr=act.getContentResolver();
        android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        java.io.InputStream is1=null;
        try{ is1=cr.openInputStream(android.net.Uri.parse(uri)); android.graphics.BitmapFactory.decodeStream(is1,null,o); }catch(Exception __ignored){} finally{ try{ if(is1!=null) is1.close(); }catch(Exception __ignored){} }
        int w=o.outWidth,h=o.outHeight; int inSample=1;
        while((w/(inSample*2))>=req && (h/(inSample*2))>=req){ inSample*=2; }
        android.graphics.BitmapFactory.Options o2=new android.graphics.BitmapFactory.Options();
        o2.inSampleSize=inSample; o2.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
        java.io.InputStream is2=null;
        try{ is2=cr.openInputStream(android.net.Uri.parse(uri)); return android.graphics.BitmapFactory.decodeStream(is2,null,o2); }catch(Throwable e){ return null; } finally{ try{ if(is2!=null) is2.close(); }catch(Exception __ignored){} }
        }else{
        android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds=true; android.graphics.BitmapFactory.decodeFile(uri, o);
        int w=o.outWidth,h=o.outHeight; int inSample=1;
        while((w/(inSample*2))>=req && (h/(inSample*2))>=req){ inSample*=2; }
        android.graphics.BitmapFactory.Options o2=new android.graphics.BitmapFactory.Options();
        o2.inSampleSize=inSample; o2.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
        return android.graphics.BitmapFactory.decodeFile(uri, o2);
        }
    }catch(Exception __ignored){}
    return null;
}
android.graphics.Bitmap frameFromVideo(String uri, int req){
    try{
        android.media.MediaMetadataRetriever mmr=new android.media.MediaMetadataRetriever();
        if(uri!=null && uri.startsWith("content://")){
        android.app.Activity act=getTopActivity(); if(act==null) return null;
        mmr.setDataSource(act, android.net.Uri.parse(uri));
        }else{
        mmr.setDataSource(uri);
        }
        android.graphics.Bitmap bm=null;
try{
    bm=mmr.getFrameAtTime(1, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
}catch(Exception __ignored){}
if(bm==null){
    try{
        if(android.os.Build.VERSION.SDK_INT>=29){
        bm=android.media.ThumbnailUtils.createVideoThumbnail(
        new java.io.File(uri),
        new android.util.Size(req, req),
        null
        );
        }else{
        bm=android.media.ThumbnailUtils.createVideoThumbnail(uri,
        android.provider.MediaStore.Images.Thumbnails.MINI_KIND);
        }
    }catch(Exception __ignored){}
}
        if(bm==null) return null;
        int w=bm.getWidth(), h=bm.getHeight();
        float scale=Math.min((float)req/w, (float)req/h);
        if(scale<1f){
        bm=android.graphics.Bitmap.createScaledBitmap(bm, (int)(w*scale), (int)(h*scale), true);
        }
        try{ mmr.release(); }catch(Exception __ignored){}
        return bm;
    }catch(Throwable e){ return null; }
}
void loadThumbAsyncUri(final android.widget.ImageView iv, final String uri, final boolean isVideo, final int req){
    try{
        iv.setTag(uri);
        Object key=uri;
        android.graphics.Bitmap cached=(android.graphics.Bitmap)getThumbCache().get(key);
        if(cached!=null){ iv.setImageBitmap(cached); return; }
        iv.setImageDrawable(null);
        getThumbPool().execute(new Runnable(){ public void run(){
        android.graphics.Bitmap bm=null;
        try{
        if(isVideo){ bm=frameFromVideo(uri, req); }
        if(bm==null) bm=decodeSampledFromUri(uri, req);
        if(bm!=null){
        getThumbCache().put(uri, bm);
        final android.graphics.Bitmap fbm=bm;
        android.app.Activity a=getTopActivity(); if(a==null) return;
        a.runOnUiThread(new Runnable(){ public void run(){
        try{
        Object tag=iv.getTag();
        if(tag!=null && String.valueOf(tag).equals(uri)) iv.setImageBitmap(fbm);
        }catch(Exception __ignored){}
        }});
        }
        }catch(Exception __ignored){}
        }});
    }catch(Exception __ignored){}
}







void pickDateTime(final Activity act, final long[] whenMs, final TextView out){
    try{
        java.util.Calendar cal=java.util.Calendar.getInstance();
        cal.setTimeInMillis(whenMs[0]);
        int y=cal.get(java.util.Calendar.YEAR);
        int m=cal.get(java.util.Calendar.MONTH);
        int d=cal.get(java.util.Calendar.DAY_OF_MONTH);
        int hh=cal.get(java.util.Calendar.HOUR_OF_DAY);
        int mm=cal.get(java.util.Calendar.MINUTE);
        android.app.DatePickerDialog dp=new android.app.DatePickerDialog(act, new android.app.DatePickerDialog.OnDateSetListener(){
        public void onDateSet(android.widget.DatePicker view, int yy, int mm0, int dd){
        android.app.TimePickerDialog tp=new android.app.TimePickerDialog(act, new android.app.TimePickerDialog.OnTimeSetListener(){
        public void onTimeSet(android.widget.TimePicker v, int h, int m2){
        java.util.Calendar c=java.util.Calendar.getInstance();
        c.set(yy, mm0, dd, h, m2, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        whenMs[0]=c.getTimeInMillis();
        out.setText(fmtTime(whenMs[0]));
        }
        }, hh, mm, true);
        tp.show();
        }
        }, y, m, d);
        dp.show();
    }catch(Throwable e){ uiToast("选择时间失败"); }
}

void renderAlbumGridUriAuto(final android.app.Activity act, final android.widget.LinearLayout container, final java.util.List items, final boolean imagesOnly){
    container.removeAllViews();
    int total = (items==null?0:items.size());
    if (total <= 0){
        android.widget.TextView empty = new android.widget.TextView(act);
        empty.setText("未读取到相册媒体\n可能是权限未授予或系统限制\n可先复制到目录："+MEDIA_DIR);
        styleTextSecondary(empty);
        empty.setPadding(dp(16),dp(16),dp(16),dp(16));
        container.addView(empty);
        return;
    }

    final android.widget.GridLayout grid = new android.widget.GridLayout(act);
    grid.setColumnCount(3);
    container.setPadding(dp(16), 0, dp(16), 0);
    container.addView(grid, new android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

    int screen = act.getResources().getDisplayMetrics().widthPixels;
    int side = dp(16), spacing = dp(8), cols = 3;
    int avail = screen - side*2;
    final int edge = (avail - (cols-1)*spacing)/cols;

    final int BATCH = 50;
    final int N = total;
    final boolean[] loading = new boolean[]{false};
    final int[] nextIndex = new int[]{0};

    final Runnable addNext = new Runnable(){ public void run(){
        if (loading[0]) return;
        loading[0] = true;
        int added = 0;
        try{
        while (added < BATCH && nextIndex[0] < N){
        Object[] it = (Object[]) items.get(nextIndex[0]++);
        String u = String.valueOf(it[0]);
        boolean isV = ((Boolean) it[1]).booleanValue();
        if (imagesOnly && isV) continue;

        android.view.View tile = buildTileUri(act, u, isV, edge);
        android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
        glp.width = edge; glp.height = edge;
        glp.setMargins(spacing/2, spacing/2, spacing/2, spacing/2);
        grid.addView(tile, glp);
        added++;
        }
        }catch(Exception __ignored){}
        finally{
        loading[0] = false;
        }
    }};

    addNext.run();

    if (nextIndex[0] < N){
        android.widget.ScrollView scTmp = null;
        try{ scTmp = (android.widget.ScrollView) container.getParent(); }catch(Throwable e){ scTmp = null; }
        final android.widget.ScrollView sc = scTmp;
        if (sc != null){
        sc.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener(){
        public void onScrollChanged(){
        try{

        if (!loading[0]
        && nextIndex[0] < N
        && sc.getScrollY() + sc.getHeight() + dp(120) >= container.getHeight()){
        addNext.run();
        if (nextIndex[0] >= N){
        try{ sc.getViewTreeObserver().removeOnScrollChangedListener(this); }catch(Exception __ignored){}
        }
        }
        }catch(Exception __ignored){}
        }
        });
        }
    }

}
