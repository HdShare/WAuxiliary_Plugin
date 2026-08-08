import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import org.json.JSONObject;
import org.json.JSONArray;

boolean pluginEnabled=true;
ConcurrentHashMap chatHistoryMap=new ConcurrentHashMap();
ConcurrentHashMap replyCountMap=new ConcurrentHashMap();
ConcurrentHashMap replyCountResetMap=new ConcurrentHashMap();

String cfgProvider="deepseek";
String cfgApiKey="";
String cfgApiUrl="";
String cfgModel="deepseek-chat";
String cfgSystemPrompt="你是一个友好的AI助手，请用简洁自然的中文回复对方。不要透露你是AI。";
int cfgMaxTokens=1000;
double cfgTemperature=0.7;
String cfgTriggerMode="at_me";
String cfgTriggerKeyword="";
String cfgTriggerPrefix="";
boolean cfgEnableGroup=true;
String cfgBlacklistUsers="";
String cfgWhitelistUsers="";
String cfgBlacklistGroups="";
String cfgWhitelistGroups="";
int cfgMaxHistory=10;
int cfgRateLimit=10;
String cfgReplyPrefix="";
boolean cfgTypingHint=true;
int cfgTimeout=30;

void onLoad(){
    loadConfig();
    if(getString("__init__","").isEmpty()){saveConfig();putString("__init__","true");log("首次运行，已生成 config.prop");}
    log(pluginName+" v"+pluginVersion+" "+cfgProvider+"/"+cfgModel+" mode:"+cfgTriggerMode);
}

void openSettings(){
    try{
        Activity t=getTopActivity();
        if(t==null){notify(pluginName,"无法打开UI，请发送 /aihelp");return;}
        String[] items={
            "AI提供商 ["+cfgProvider+"]",
            "API Key ["+(cfgApiKey.isEmpty()?"未设置":maskKey(cfgApiKey))+"]",
            "模型 ["+cfgModel+"]",
            "AI人设 [点击修改]",
            "Token="+cfgMaxTokens+" Temp="+cfgTemperature+" 记忆="+cfgMaxHistory,
            "测试连接",
            "触发模式 ["+cfgTriggerMode+"]",
            "用户白名单 ("+countList(cfgWhitelistUsers)+"人)",
            "用户黑名单 ("+countList(cfgBlacklistUsers)+"人)",
            "群聊白名单 ("+countList(cfgWhitelistGroups)+"个)",
            "群聊黑名单 ("+countList(cfgBlacklistGroups)+"个)",
            "开关控制",
            "查看完整配置"
        };
        new android.app.AlertDialog.Builder(t)
            .setTitle("AI自动回复 v"+pluginVersion)
            .setItems(items,new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    if(w==0)showProviderPicker();
                    else if(w==1)showTextInput("api_key","API Key","sk-xxxxxxxx",true);
                    else if(w==2)showTextInput("model","模型名称","deepseek-chat",false);
                    else if(w==3)showPromptInput();
                    else if(w==4)showParamInput();
                    else if(w==5)showTestConnection();
                    else if(w==6)showModePicker();
                    else if(w==7)showFriendPicker("whitelist_users","用户白名单");
                    else if(w==8)showFriendPicker("blacklist_users","用户黑名单");
                    else if(w==9)showGroupPicker("whitelist_groups","群聊白名单");
                    else if(w==10)showGroupPicker("blacklist_groups","群聊黑名单");
                    else if(w==11)showSwitchPanel();
                    else if(w==12)showConfigView();
                }
            })
            .setNegativeButton("关闭",null)
            .show();
    }catch(Exception e){notify(pluginName,"UI失败: "+e.getMessage());}
}

void showProviderPicker(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        String[] names={"OpenAI","Claude (Anthropic)","DeepSeek","自定义兼容接口"};
        String[] vals={"openai","claude","deepseek","custom"};
        String[] defModels={"gpt-4o-mini","claude-3-5-sonnet-20241022","deepseek-chat",cfgModel};
        int sel=0;
        for(int i=0;i<vals.length;i++)if(vals[i].equals(cfgProvider)){sel=i;break;}
        final int[] picked={sel};
        final String[] fv=vals,fm=defModels;
        new android.app.AlertDialog.Builder(t)
            .setTitle("AI提供商")
            .setSingleChoiceItems(names,sel,new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){picked[0]=w;}
            })
            .setPositiveButton("确定",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    cfgProvider=fv[picked[0]];
                    cfgApiUrl="";
                    cfgModel=fm[picked[0]];
                    putString("api_url","");
                    putString("model",cfgModel);
                    saveConfig();
                    toast("已切换到 "+cfgProvider+" 模型:"+cfgModel);
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("切换失败");}
}

void showFriendPicker(String cfgKey,String title){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        List fl=getFriendList();
        if(fl==null||fl.isEmpty()){toast("没有好友数据");return;}
        int n=fl.size();
        String[] names=new String[n];
        final String[] wxids=new String[n];
        boolean[] checked=new boolean[n];
        String cur=getString(cfgKey,"");
        for(int i=0;i<n;i++){
            Object fi=fl.get(i);
            wxids[i]=fi.getWxid();
            String nick=safeNick(wxids[i]);
            names[i]=nick.isEmpty()?wxids[i]:nick;
            checked[i]=isInList(cur,wxids[i]);
        }
        final boolean[] fc=new boolean[n];
        System.arraycopy(checked,0,fc,0,n);
        final String[] fw=wxids;
        new android.app.AlertDialog.Builder(t)
            .setTitle(title)
            .setMultiChoiceItems(names,checked,new android.content.DialogInterface.OnMultiChoiceClickListener(){
                public void onClick(android.content.DialogInterface d,int i,boolean v){fc[i]=v;}
            })
            .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    StringBuilder sb=new StringBuilder();
                    for(int i=0;i<n;i++)if(fc[i]){if(sb.length()>0)sb.append(",");sb.append(fw[i]);}
                    String v=sb.toString();
                    putString(cfgKey,v);
                    if(cfgKey.equals("whitelist_users"))cfgWhitelistUsers=v;
                    else cfgBlacklistUsers=v;
                    toast(title+" 已保存 "+countList(v)+" 项");
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("加载好友失败");}
}

void showGroupPicker(String cfgKey,String title){
    Activity t=getTopActivity();
    if(t==null){toast("无法获取Activity");return;}
    List gl=getGroupList();
    if(gl==null||gl.isEmpty()){toast("没有群聊数据");return;}
    int n=gl.size();
    log("GP n="+n);
    String[] names=new String[n];
    final String[] ids=new String[n];
    boolean[] checked=new boolean[n];
    String cur=getString(cfgKey,"");
    for(int i=0;i<n;i=i+1){
        Object g=gl.get(i);
        String id=g.getRoomId();
        String gname="";
        try{gname=g.getName();}catch(Throwable e1){}
        if(gname.isEmpty())try{gname=g.getRemark();}catch(Throwable e2){}
        ids[i]=id;
        names[i]=gname.isEmpty()?id:gname+"("+id+")";
        if(isInList(cur,id))checked[i]=true;
    }
    final boolean[] fc=new boolean[n];
    for(int i=0;i<n;i=i+1)fc[i]=checked[i];
    final String[] fi=ids;
    new android.app.AlertDialog.Builder(t)
        .setTitle(title)
        .setMultiChoiceItems(names,checked,new android.content.DialogInterface.OnMultiChoiceClickListener(){
            public void onClick(android.content.DialogInterface d,int i,boolean v){fc[i]=v;}
        })
        .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
            public void onClick(android.content.DialogInterface d,int w){
                StringBuilder sb=new StringBuilder();
                for(int i=0;i<n;i=i+1)if(fc[i]){if(sb.length()>0)sb.append(",");sb.append(fi[i]);}
                String v=sb.toString();
                putString(cfgKey,v);
                if(cfgKey.equals("whitelist_groups"))cfgWhitelistGroups=v;
                else cfgBlacklistGroups=v;
                toast("已保存 "+countList(v)+" 项");
            }
        })
        .setNegativeButton("取消",null)
        .show();
    log("GP shown");
}

void showModePicker(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        String[] modes={"仅@我时回复 (at_me)","回复所有消息 (all)","包含关键词时回复 (keyword)","前缀触发 (prefix)"};
        String[] vals={"at_me","all","keyword","prefix"};
        int sel=0;
        for(int i=0;i<vals.length;i++)if(vals[i].equals(cfgTriggerMode)){sel=i;break;}
        final int[] picked={sel};
        final String[] fv=vals;
        new android.app.AlertDialog.Builder(t)
            .setTitle("触发模式")
            .setSingleChoiceItems(modes,sel,new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){picked[0]=w;}
            })
            .setPositiveButton("确定",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    cfgTriggerMode=fv[picked[0]];
                    saveConfig();
                    toast("触发模式: "+cfgTriggerMode);
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("失败");}
}

void showSwitchPanel(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        String[] sw={"AI回复: "+(pluginEnabled?"开":"关"),"群聊回复: "+(cfgEnableGroup?"开":"关"),"思考提示: "+(cfgTypingHint?"开":"关")};
        final boolean[] st={pluginEnabled,cfgEnableGroup,cfgTypingHint};
        new android.app.AlertDialog.Builder(t)
            .setTitle("开关控制")
            .setMultiChoiceItems(sw,st,new android.content.DialogInterface.OnMultiChoiceClickListener(){
                public void onClick(android.content.DialogInterface d,int i,boolean v){st[i]=v;}
            })
            .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    pluginEnabled=st[0];
                    cfgEnableGroup=st[1];
                    cfgTypingHint=st[2];
                    saveConfig();
                    toast("开关已更新");
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("失败");}
}

void showConfigView(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        String msg="API: "+cfgProvider+"\n模型: "+cfgModel+"\nKey: "+(cfgApiKey.isEmpty()?"(未设置)":maskKey(cfgApiKey))+"\n触发: "+cfgTriggerMode+" | 群聊: "+(cfgEnableGroup?"开":"关")+"\n历史: "+cfgMaxHistory+"轮 | 限频: "+cfgRateLimit+"/分\nToken: "+cfgMaxTokens+" | Temp: "+cfgTemperature+"\n白名单用户: "+(cfgWhitelistUsers.isEmpty()?"(未启用)":cfgWhitelistUsers)+"\n黑名单用户: "+(cfgBlacklistUsers.isEmpty()?"(空)":cfgBlacklistUsers)+"\n白名单群: "+(cfgWhitelistGroups.isEmpty()?"(未启用)":cfgWhitelistGroups)+"\n黑名单群: "+(cfgBlacklistGroups.isEmpty()?"(空)":cfgBlacklistGroups)+"\n\n配置: "+pluginDir+"/config.prop";
        new android.app.AlertDialog.Builder(t)
            .setTitle("完整配置")
            .setMessage(msg)
            .setPositiveButton("关闭",null)
            .show();
    }catch(Exception e){toast("失败");}
}

void showTextInput(String cfgKey,String title,String hint,boolean isPwd){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        final android.widget.EditText et=new android.widget.EditText(t);
        et.setHint(hint);
        String cur="";
        if(cfgKey.equals("api_key"))cur=cfgApiKey;
        else if(cfgKey.equals("model"))cur=cfgModel;
        else if(cfgKey.equals("api_url"))cur=cfgApiUrl;
        else if(cfgKey.equals("trigger_keyword"))cur=cfgTriggerKeyword;
        else if(cfgKey.equals("trigger_prefix"))cur=cfgTriggerPrefix;
        else if(cfgKey.equals("reply_prefix"))cur=cfgReplyPrefix;
        et.setText(cur);
        if(isPwd)et.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new android.app.AlertDialog.Builder(t)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    String v=et.getText().toString().trim();
                    if(cfgKey.equals("api_key"))cfgApiKey=v;
                    else if(cfgKey.equals("model"))cfgModel=v;
                    else if(cfgKey.equals("api_url"))cfgApiUrl=v;
                    else if(cfgKey.equals("trigger_keyword"))cfgTriggerKeyword=v;
                    else if(cfgKey.equals("trigger_prefix"))cfgTriggerPrefix=v;
                    else if(cfgKey.equals("reply_prefix"))cfgReplyPrefix=v;
                    putString(cfgKey,v);
                    saveConfig();
                    toast("已保存");
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("输入失败");}
}

void showPromptInput(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        final android.widget.EditText et=new android.widget.EditText(t);
        et.setText(cfgSystemPrompt);
        et.setMinLines(4);
        et.setMaxLines(10);
        et.setGravity(android.view.Gravity.TOP);
        new android.app.AlertDialog.Builder(t)
            .setTitle("AI人设 / 系统提示词")
            .setMessage("定义AI的角色、语气、回复风格")
            .setView(et)
            .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    cfgSystemPrompt=et.getText().toString().trim();
                    putString("system_prompt",cfgSystemPrompt);
                    saveConfig();
                    toast("人设已保存");
                }
            })
            .setNeutralButton("重置默认",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    et.setText("你是一个友好的AI助手，请用简洁自然的中文回复对方。不要透露你是AI。");
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("失败");}
}

void showParamInput(){
    try{
        Activity t=getTopActivity();
        if(t==null)return;
        android.widget.LinearLayout ll=new android.widget.LinearLayout(t);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(50,20,50,20);
        final android.widget.EditText e1=new android.widget.EditText(t);
        e1.setHint("MaxTokens (默认1000)");
        e1.setText(""+cfgMaxTokens);
        e1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ll.addView(e1);
        final android.widget.EditText e2=new android.widget.EditText(t);
        e2.setHint("Temperature (0~2, 默认0.7)");
        e2.setText(""+cfgTemperature);
        e2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ll.addView(e2);
        final android.widget.EditText e3=new android.widget.EditText(t);
        e3.setHint("对话记忆轮数 (默认10)");
        e3.setText(""+cfgMaxHistory);
        e3.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ll.addView(e3);
        new android.app.AlertDialog.Builder(t)
            .setTitle("AI参数")
            .setView(ll)
            .setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){
                    try{
                        cfgMaxTokens=Integer.parseInt(e1.getText().toString().trim());
                        cfgTemperature=Double.parseDouble(e2.getText().toString().trim());
                        cfgMaxHistory=Integer.parseInt(e3.getText().toString().trim());
                        putInt("max_tokens",cfgMaxTokens);
                        putString("temperature",""+cfgTemperature);
                        putInt("max_history",cfgMaxHistory);
                        saveConfig();
                        toast("参数已保存");
                    }catch(Exception e2){toast("请输入有效数字");}
                }
            })
            .setNegativeButton("取消",null)
            .show();
    }catch(Exception e){toast("失败");}
}

void showTestConnection(){
    Activity t=getTopActivity();
    if(t==null)return;
    if(cfgApiKey.isEmpty()){toast("请先设置 API Key");return;}
    final android.app.AlertDialog dlg=new android.app.AlertDialog.Builder(t)
        .setTitle("测试连接")
        .setMessage("正在连接 "+cfgProvider+" ...\n模型: "+cfgModel)
        .setCancelable(false)
        .show();
    new Thread(new Runnable(){public void run(){
        String result;
        try{
            JSONObject body=new JSONObject();
            body.put("model",cfgModel);
            body.put("stream",false);
            JSONArray arr=new JSONArray();
            JSONObject msg=new JSONObject();
            msg.put("role","user");
            msg.put("content","Hi");
            arr.put(msg);
            body.put("messages",arr);
            String raw=body.toString();
            Map headers=new HashMap();
            if(cfgProvider.equals("claude")){
                headers.put("x-api-key",cfgApiKey);
                headers.put("anthropic-version","2023-06-01");
            }else{
                headers.put("Authorization","Bearer "+cfgApiKey);
            }
            headers.put("Content-Type","application/json");
            long t0=System.currentTimeMillis();
            URL url=new URL(getApiUrl());
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(cfgTimeout*1000);
            c.setReadTimeout(cfgTimeout*1000);
            for(Object k:headers.keySet())c.setRequestProperty((String)k,(String)headers.get(k));
            OutputStream os=c.getOutputStream();
            os.write(raw.getBytes("UTF-8"));
            os.flush();
            os.close();
            int code=c.getResponseCode();
            long lat=System.currentTimeMillis()-t0;
            InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
            StringBuilder sb=new StringBuilder();
            BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));
            String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();
            c.disconnect();
            if(code==200){
                result="连接成功!\n\n提供商: "+cfgProvider+"\n模型: "+cfgModel+"\n延迟: "+lat+"ms";
            }else{
                String ep=sb.toString();
                if(ep.length()>300)ep=ep.substring(0,300)+"...";
                result="失败 (HTTP "+code+")\n\n"+ep;
            }
        }catch(Exception e){
            result="连接异常\n\n"+e.getClass().getSimpleName()+"\n"+(e.getMessage()!=null?e.getMessage():"");
        }
        final String fr=result;
        t.runOnUiThread(new Runnable(){public void run(){
            try{dlg.dismiss();}catch(Exception e2){}
            new android.app.AlertDialog.Builder(t)
                .setTitle("测试结果")
                .setMessage(fr)
                .setPositiveButton("关闭",null)
                .show();
        }});
    }}).start();
}

void onHandleMsg(Object m){
    if(!pluginEnabled)return;
    if(!m.isText())return;
    String talker=m.getTalker();
    String content=m.getContent().trim();
    String sender=m.getSendTalker();
    boolean isGroup=m.isGroupChat();
    if(handleCommand(talker,sender,content,isGroup))return;
    if(m.isSend())return;
    if(!shouldTrigger(m,talker,content,isGroup))return;
    if(!checkRateLimit(talker))return;
    if(content.length()>2000)content=content.substring(0,2000)+"...";
    if(cfgTypingHint&&isGroup)insertSystemMsg(talker,"AI思考中...",System.currentTimeMillis());
    String fc=content;
    new Thread(new Runnable(){public void run(){callAI(talker,sender,getDisplayNameSafe(sender,talker),fc,isGroup);}}).start();
}

boolean handleCommand(String talker,String sender,String content,boolean isGroup){
    if(isGroup&&!content.contains("@"))return false;
    String cmd=content;
    if(cmd.startsWith("@")){
        int si=cmd.indexOf(" ");
        if(si>0)cmd=cmd.substring(si+1).trim();
        else if(cmd.contains("\u2005")){int sp=cmd.indexOf("\u2005");if(sp>0)cmd=cmd.substring(sp+1).trim();}
        else return false;
    }
    if(cmd.equals("/aistatus")){sendText(talker,cfgProvider+"/"+cfgModel+" mode:"+cfgTriggerMode+" "+(pluginEnabled?"运行":"暂停"));return true;}
    if(cmd.equals("/airestart")||cmd.equals("/aireload")){reloadPlugin();return true;}
    if(cmd.equals("/aiclear")){chatHistoryMap.remove(talker);sendText(talker,"对话记忆已清除");return true;}
    if(cmd.equals("/aiconfig")){sendText(talker,"provider="+cfgProvider+"\nmodel="+cfgModel+"\nkey="+(cfgApiKey.isEmpty()?"(未设置)":maskKey(cfgApiKey))+"\nmode="+cfgTriggerMode+"\nWL:"+(cfgWhitelistUsers.isEmpty()?"(未启用)":cfgWhitelistUsers)+"\n人设:"+cfgSystemPrompt);return true;}
    if(cmd.equals("/aihelp")){sendText(talker,"命令列表:\n/aistatus 查看状态\n/aiconfig 查看配置\n/aiclear 清除记忆\n/aitest 消息 测试AI\n/aiset k=v 修改配置\n/aimode at_me|all|keyword|prefix\n/aion /aioff 开关\n/aiadduser wxid 加白名单\n/aiblockuser wxid 拉黑\n/aihelp 此帮助");return true;}
    if(cmd.startsWith("/aiset ")){handleConfigSet(talker,cmd.substring(7).trim());return true;}
    if(cmd.startsWith("/aiadduser ")){addToList("whitelist",talker,cmd.substring(11).trim());return true;}
    if(cmd.startsWith("/aideluser ")){delFromList("whitelist",talker,cmd.substring(11).trim());return true;}
    if(cmd.startsWith("/aiblockuser ")){addToList("blacklist",talker,cmd.substring(13).trim());return true;}
    if(cmd.startsWith("/aiunblockuser ")){delFromList("blacklist",talker,cmd.substring(15).trim());return true;}
    if(cmd.startsWith("/aimode ")){
        String m=cmd.substring(8).trim();
        if(m.equals("at_me")||m.equals("all")||m.equals("keyword")||m.equals("prefix")){
            cfgTriggerMode=m;saveConfig();sendText(talker,"触发模式: "+m);
        }else sendText(talker,"无效模式 可用: at_me/all/keyword/prefix");
        return true;
    }
    if(cmd.equals("/aigroupon")){cfgEnableGroup=true;saveConfig();sendText(talker,"群聊已开");return true;}
    if(cmd.equals("/aigroupoff")){cfgEnableGroup=false;saveConfig();sendText(talker,"群聊已关");return true;}
    if(cmd.equals("/aion")){pluginEnabled=true;saveConfig();sendText(talker,"AI已启用");return true;}
    if(cmd.equals("/aioff")){pluginEnabled=false;saveConfig();sendText(talker,"AI已暂停");return true;}
    if(cmd.startsWith("/aitest ")){
        sendText(talker,"测试中...");
        String tm=cmd.substring(8).trim();
        new Thread(new Runnable(){public void run(){callAI(talker,sender,getDisplayNameSafe(sender,talker),tm,isGroup);}}).start();
        return true;
    }
    return false;
}

boolean shouldTrigger(Object m,String talker,String content,boolean isGroup){
    if(isGroup&&!cfgEnableGroup)return false;
    String sender=m.getSendTalker();
    if(isInList(cfgBlacklistUsers,sender))return false;
    if(isGroup&&isInList(cfgBlacklistGroups,talker))return false;
    if(!isGroup){
        if(!cfgWhitelistUsers.isEmpty()&&!isInList(cfgWhitelistUsers,sender))return false;
    }else{
        if(!cfgWhitelistGroups.isEmpty()&&!isInList(cfgWhitelistGroups,talker))return false;
    }
    if(cfgTriggerMode.equals("at_me"))return m.isAtMe();
    if(cfgTriggerMode.equals("all"))return true;
    if(cfgTriggerMode.equals("keyword"))return!cfgTriggerKeyword.isEmpty()&&content.contains(cfgTriggerKeyword);
    if(cfgTriggerMode.equals("prefix"))return!cfgTriggerPrefix.isEmpty()&&content.startsWith(cfgTriggerPrefix);
    return m.isAtMe();
}

boolean checkRateLimit(String talker){
    if(cfgRateLimit<=0)return true;
    long now=System.currentTimeMillis();
    Long rt=(Long)replyCountResetMap.get(talker);
    if(rt==null||rt<now-60000){
        replyCountMap.put(talker,0);
        replyCountResetMap.put(talker,now+60000);
    }
    int cnt=replyCountMap.getOrDefault(talker,0);
    if(cnt>=cfgRateLimit)return false;
    replyCountMap.put(talker,cnt+1);
    return true;
}

void callAI(String talker,String senderWxid,String senderName,String userMsg,boolean isGroup){
    try{
        JSONObject root=new JSONObject();
        root.put("model",cfgModel);
        root.put("stream",false);
        if(!cfgProvider.equals("claude"))root.put("temperature",cfgTemperature);
        JSONArray arr=new JSONArray();
        JSONObject sysMsg=new JSONObject();
        sysMsg.put("role","system");
        sysMsg.put("content",cfgSystemPrompt);
        arr.put(sysMsg);
        List hist=(List)chatHistoryMap.get(talker);
        if(hist!=null){
            int st=Math.max(0,hist.size()-cfgMaxHistory*2);
            for(int i=st;i<hist.size();i++){
                Map h=(Map)hist.get(i);
                JSONObject hm=new JSONObject();
                hm.put("role",h.get("role"));
                hm.put("content",h.get("content"));
                arr.put(hm);
            }
        }
        JSONObject userMsgObj=new JSONObject();
        userMsgObj.put("role","user");
        userMsgObj.put("content",(senderName!=null&&!senderName.isEmpty())?"["+senderName+"] 说: "+userMsg:userMsg);
        arr.put(userMsgObj);
        root.put("messages",arr);
        if(cfgProvider.equals("claude"))root.put("max_tokens",cfgMaxTokens);
        String body=root.toString();
        Map headers=new HashMap();
        if(cfgProvider.equals("claude")){
            headers.put("x-api-key",cfgApiKey);
            headers.put("anthropic-version","2023-06-01");
        }else{
            headers.put("Authorization","Bearer "+cfgApiKey);
        }
        headers.put("Content-Type","application/json");
        URL url=new URL(getApiUrl());
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(cfgTimeout*1000);
        c.setReadTimeout(cfgTimeout*1000);
        for(Object k:headers.keySet())c.setRequestProperty((String)k,(String)headers.get(k));
        OutputStream os=c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code=c.getResponseCode();
        InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));
        String line;
        while((line=br.readLine())!=null)sb.append(line);
        br.close();
        c.disconnect();
        String resp=sb.toString();
        if(code!=200){
            String err=resp.length()>200?resp.substring(0,200):resp;
            log("AI API err ["+code+"]: "+resp);
            sendText(talker,"AI异常["+code+"] "+err);
            return;
        }
        JSONObject respObj=new JSONObject(resp);
        String reply;
        if(cfgProvider.equals("claude")){
            reply=respObj.getJSONArray("content").getJSONObject(0).getString("text");
        }else{
            reply=respObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        }
        if(reply==null||reply.isEmpty()){log("AI空回复");sendText(talker,"AI无回复");return;}
        saveHistory(talker,"user",userMsg);
        saveHistory(talker,"assistant",reply);
        String fr=cfgReplyPrefix.isEmpty()?reply:cfgReplyPrefix+reply;
        if(isGroup)fr="[AtWx="+senderWxid+"] "+fr;
        sendText(talker,fr);
    }catch(SocketTimeoutException e){
        log("AI超时: "+e.getMessage());
        sendText(talker,"AI响应超时");
    }catch(Exception e){
        log("AI异常: "+e.getMessage());
        sendText(talker,"AI异常: "+e.getClass().getSimpleName());
    }
}

void saveHistory(String talker,String role,String content){
    List h=(List)chatHistoryMap.get(talker);
    if(h==null){h=Collections.synchronizedList(new ArrayList());chatHistoryMap.put(talker,h);}
    Map e=new HashMap();
    e.put("role",role);
    e.put("content",content);
    h.add(e);
    int max=cfgMaxHistory*2+10;
    while(h.size()>max)h.remove(0);
}

String getApiUrl(){
    if(cfgApiUrl!=null&&!cfgApiUrl.isEmpty())return cfgApiUrl;
    if(cfgProvider.equals("openai"))return "https://api.openai.com/v1/chat/completions";
    if(cfgProvider.equals("claude"))return "https://api.anthropic.com/v1/messages";
    if(cfgProvider.equals("deepseek"))return "https://api.deepseek.com/v1/chat/completions";
    return cfgApiUrl;
}

void loadConfig(){
    cfgProvider=getString("provider",cfgProvider);
    cfgApiKey=getString("api_key",cfgApiKey);
    cfgApiUrl=getString("api_url",cfgApiUrl);
    if(!cfgProvider.equals("openai")&&cfgApiUrl.contains("api.openai.com")){cfgApiUrl="";putString("api_url","");}
    cfgModel=getString("model",cfgModel);
    cfgSystemPrompt=getString("system_prompt",cfgSystemPrompt);
    cfgMaxTokens=getInt("max_tokens",cfgMaxTokens);
    cfgTemperature=Double.parseDouble(getString("temperature",""+cfgTemperature));
    cfgTriggerMode=getString("trigger_mode",cfgTriggerMode);
    cfgTriggerKeyword=getString("trigger_keyword",cfgTriggerKeyword);
    cfgTriggerPrefix=getString("trigger_prefix",cfgTriggerPrefix);
    cfgEnableGroup=getBoolean("enable_group",cfgEnableGroup);
    cfgBlacklistUsers=getString("blacklist_users",cfgBlacklistUsers);
    cfgWhitelistUsers=getString("whitelist_users",cfgWhitelistUsers);
    cfgBlacklistGroups=getString("blacklist_groups",cfgBlacklistGroups);
    cfgWhitelistGroups=getString("whitelist_groups",cfgWhitelistGroups);
    cfgMaxHistory=getInt("max_history",cfgMaxHistory);
    cfgRateLimit=getInt("rate_limit",cfgRateLimit);
    cfgReplyPrefix=getString("reply_prefix",cfgReplyPrefix);
    cfgTypingHint=getBoolean("typing_hint",cfgTypingHint);
    cfgTimeout=getInt("timeout",cfgTimeout);
    pluginEnabled=getBoolean("enabled",pluginEnabled);
}

void saveConfig(){
    putString("provider",cfgProvider);
    putString("api_key",cfgApiKey);
    putString("api_url",cfgApiUrl);
    putString("model",cfgModel);
    putString("system_prompt",cfgSystemPrompt);
    putInt("max_tokens",cfgMaxTokens);
    putString("temperature",""+cfgTemperature);
    putString("trigger_mode",cfgTriggerMode);
    putString("trigger_keyword",cfgTriggerKeyword);
    putString("trigger_prefix",cfgTriggerPrefix);
    putBoolean("enable_group",cfgEnableGroup);
    putString("blacklist_users",cfgBlacklistUsers);
    putString("whitelist_users",cfgWhitelistUsers);
    putString("blacklist_groups",cfgBlacklistGroups);
    putString("whitelist_groups",cfgWhitelistGroups);
    putInt("max_history",cfgMaxHistory);
    putInt("rate_limit",cfgRateLimit);
    putString("reply_prefix",cfgReplyPrefix);
    putBoolean("typing_hint",cfgTypingHint);
    putInt("timeout",cfgTimeout);
    putBoolean("enabled",pluginEnabled);
}

void handleConfigSet(String talker,String args){
    int eq=args.indexOf("=");
    if(eq<0){sendText(talker,"格式: /aiset key=value");return;}
    String k=args.substring(0,eq).trim();
    String v=args.substring(eq+1).trim();
    if(k.equals("provider"))cfgProvider=v;
    else if(k.equals("api_key"))cfgApiKey=v;
    else if(k.equals("api_url"))cfgApiUrl=v;
    else if(k.equals("model"))cfgModel=v;
    else if(k.equals("system_prompt"))cfgSystemPrompt=v;
    else if(k.equals("trigger_mode"))cfgTriggerMode=v;
    else if(k.equals("trigger_keyword"))cfgTriggerKeyword=v;
    else if(k.equals("trigger_prefix"))cfgTriggerPrefix=v;
    else if(k.equals("reply_prefix"))cfgReplyPrefix=v;
    else if(k.equals("max_tokens"))cfgMaxTokens=Integer.parseInt(v);
    else if(k.equals("temperature"))cfgTemperature=Double.parseDouble(v);
    else if(k.equals("max_history"))cfgMaxHistory=Integer.parseInt(v);
    else if(k.equals("rate_limit"))cfgRateLimit=Integer.parseInt(v);
    else if(k.equals("timeout"))cfgTimeout=Integer.parseInt(v);
    else if(k.equals("enable_group"))cfgEnableGroup=Boolean.parseBoolean(v);
    else if(k.equals("typing_hint"))cfgTypingHint=Boolean.parseBoolean(v);
    else if(k.equals("enabled"))pluginEnabled=Boolean.parseBoolean(v);
    else{sendText(talker,"未知配置项: "+k);return;}
    saveConfig();
    sendText(talker,"已更新: "+k+" = "+v);
}

boolean isInList(String csv,String id){
    if(csv==null||csv.isEmpty())return false;
    for(String s:csv.split(","))if(s.trim().equals(id))return true;
    return false;
}

void addToList(String which,String talker,String id){
    if(id.isEmpty()){sendText(talker,"请提供wxid");return;}
    String cur;
    if(which.equals("whitelist"))cur=cfgWhitelistUsers;
    else if(which.equals("blacklist"))cur=cfgBlacklistUsers;
    else if(which.equals("group_whitelist"))cur=cfgWhitelistGroups;
    else cur=cfgBlacklistGroups;
    if(isInList(cur,id)){sendText(talker,"已存在");return;}
    String upd=cur.isEmpty()?id:cur+","+id;
    if(which.equals("whitelist"))cfgWhitelistUsers=upd;
    else if(which.equals("blacklist"))cfgBlacklistUsers=upd;
    else if(which.equals("group_whitelist"))cfgWhitelistGroups=upd;
    else cfgBlacklistGroups=upd;
    saveConfig();
    sendText(talker,"已添加: "+id);
}

void delFromList(String which,String talker,String id){
    if(id.isEmpty()){sendText(talker,"请提供wxid");return;}
    String cur;
    if(which.equals("whitelist"))cur=cfgWhitelistUsers;
    else if(which.equals("blacklist"))cur=cfgBlacklistUsers;
    else if(which.equals("group_whitelist"))cur=cfgWhitelistGroups;
    else cur=cfgBlacklistGroups;
    if(!isInList(cur,id)){sendText(talker,"未找到");return;}
    StringBuilder sb=new StringBuilder();
    for(String s:cur.split(",")){
        String t=s.trim();
        if(!t.equals(id)){if(sb.length()>0)sb.append(",");sb.append(t);}
    }
    String upd=sb.toString();
    if(which.equals("whitelist"))cfgWhitelistUsers=upd;
    else if(which.equals("blacklist"))cfgBlacklistUsers=upd;
    else if(which.equals("group_whitelist"))cfgWhitelistGroups=upd;
    else cfgBlacklistGroups=upd;
    saveConfig();
    sendText(talker,"已移除: "+id);
}

String getDisplayNameSafe(String wxid,String rid){
    try{String n=getFriendDisplayName(wxid,rid);if(n!=null&&!n.isEmpty())return n;}catch(Exception e){}
    try{String n=getFriendRemarkName(wxid);if(n!=null&&!n.isEmpty())return n;}catch(Exception e){}
    try{String n=getFriendNickName(wxid);if(n!=null&&!n.isEmpty())return n;}catch(Exception e){}
    return wxid;
}

String maskKey(String k){
    if(k.length()<=8)return"***";
    return k.substring(0,4)+"****"+k.substring(k.length()-4);
}

int countList(String csv){
    if(csv==null||csv.isEmpty())return 0;
    return csv.split(",").length;
}

String safeNick(String wxid){
    try{String r=getFriendRemarkName(wxid);if(r!=null&&!r.isEmpty())return r;}catch(Exception e){}
    try{String r=getFriendNickName(wxid);if(r!=null&&!r.isEmpty())return r;}catch(Exception e){}
    return "";
}
