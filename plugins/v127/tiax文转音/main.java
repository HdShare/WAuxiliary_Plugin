// 音色映射表 (ID -> 名称) 基于 mode.txt 生成
private static final Map<Integer, String> VOICE_NAME_MAP = new HashMap<>();
static {
    VOICE_NAME_MAP.put(1, "2学长");
    VOICE_NAME_MAP.put(2, "AD学姐");
    VOICE_NAME_MAP.put(3, "alex克隆");
    VOICE_NAME_MAP.put(4, "【红色警戒2】苏联情报员语音");
    VOICE_NAME_MAP.put(5, "【宣传片】（大气悠扬浑厚）");
    VOICE_NAME_MAP.put(6, "阿蕾奇诺");
    VOICE_NAME_MAP.put(7, "爱莉");
    VOICE_NAME_MAP.put(8, "爱丽丝（中配）");
    VOICE_NAME_MAP.put(9, "安倍晋三");
    VOICE_NAME_MAP.put(10, "八戒");
    VOICE_NAME_MAP.put(11, "白领御姐音");
    VOICE_NAME_MAP.put(12, "白鹿的声音");
    VOICE_NAME_MAP.put(13, "白岩松");
    VOICE_NAME_MAP.put(14, "北方口音LY");
    VOICE_NAME_MAP.put(15, "北京地铁黄华（1号线、2号线等中文）报站");
    VOICE_NAME_MAP.put(16, "贝利亚");
    VOICE_NAME_MAP.put(17, "毕业季温情女学生");
    VOICE_NAME_MAP.put(18, "菠萝宝宝yuna");
    VOICE_NAME_MAP.put(19, "伯纳德");
    VOICE_NAME_MAP.put(20, "采访女生");
    VOICE_NAME_MAP.put(21, "曹操");
    VOICE_NAME_MAP.put(22, "茶文化班章老茶头");
    VOICE_NAME_MAP.put(23, "陈赫");
    VOICE_NAME_MAP.put(24, "陳章 台灣男聲 活力親切 高品質");
    VOICE_NAME_MAP.put(25, "陈奕恒");
    VOICE_NAME_MAP.put(26, "重音TETO SV");
    VOICE_NAME_MAP.put(27, "重音teto");
    VOICE_NAME_MAP.put(28, "吹牛逼呢");
    VOICE_NAME_MAP.put(29, "磁性电台女生");
    VOICE_NAME_MAP.put(30, "达叔");
    VOICE_NAME_MAP.put(31, "大气宣传片配音");
    VOICE_NAME_MAP.put(32, "大健康男医生");
    VOICE_NAME_MAP.put(33, "丹恒，饮月");
    VOICE_NAME_MAP.put(34, "三角洲蜂医");
    VOICE_NAME_MAP.put(35, "道士别人的");
    VOICE_NAME_MAP.put(36, "德俊7");
    VOICE_NAME_MAP.put(37, "邓紫琪");
    VOICE_NAME_MAP.put(38, "丁真");
    VOICE_NAME_MAP.put(39, "丁真（锐刻五代版）");
    VOICE_NAME_MAP.put(40, "东北雨姐");
    VOICE_NAME_MAP.put(41, "東急の声 日本語");
    VOICE_NAME_MAP.put(42, "東武 車内放送");
    VOICE_NAME_MAP.put(43, "董卿");
    VOICE_NAME_MAP.put(44, "董璇");
    VOICE_NAME_MAP.put(45, "董宇辉");
    VOICE_NAME_MAP.put(46, "动物科普");
    VOICE_NAME_MAP.put(47, "抖兜");
    VOICE_NAME_MAP.put(48, "豆包");
    VOICE_NAME_MAP.put(49, "短视频");
    VOICE_NAME_MAP.put(50, "方言");
    VOICE_NAME_MAP.put(51, "仿真人（常用男配音、语速快）");
    VOICE_NAME_MAP.put(52, "仿真人（男声、情绪平稳、常用与历史开头，故事类）");
    VOICE_NAME_MAP.put(53, "仿真人（男声、偏用于广告、捏造事实类）");
    VOICE_NAME_MAP.put(54, "粉丝直播间喊麦");
    VOICE_NAME_MAP.put(55, "冯宝宝");
    VOICE_NAME_MAP.put(56, "フリーザ様あああああああ");
    VOICE_NAME_MAP.put(57, "母女对话（妈妈）");
    VOICE_NAME_MAP.put(58, "甘雨");
    VOICE_NAME_MAP.put(59, "感性女生配音");
    VOICE_NAME_MAP.put(60, "钢镚");
    VOICE_NAME_MAP.put(61, "高启强1");
    VOICE_NAME_MAP.put(62, "高启强正常音色");
    VOICE_NAME_MAP.put(63, "工作刘");
    VOICE_NAME_MAP.put(64, "工商旅游001女");
    VOICE_NAME_MAP.put(65, "关晓彤");
    VOICE_NAME_MAP.put(66, "广告波音");
    VOICE_NAME_MAP.put(67, "广告端庄女声");
    VOICE_NAME_MAP.put(68, "郭德纲");
    VOICE_NAME_MAP.put(69, "国恒");
    VOICE_NAME_MAP.put(70, "台湾女");
    VOICE_NAME_MAP.put(71, "夯大力");
    VOICE_NAME_MAP.put(72, "郝邵文3");
    VOICE_NAME_MAP.put(73, "合肥女主持人");
    VOICE_NAME_MAP.put(74, "河源木桃香普通话");
    VOICE_NAME_MAP.put(75, "黑卡蒂");
    VOICE_NAME_MAP.put(76, "黑手");
    VOICE_NAME_MAP.put(77, "嘿嘿温柔文艺女声");
    VOICE_NAME_MAP.put(78, "弘历");
    VOICE_NAME_MAP.put(79, "户晨风");
    VOICE_NAME_MAP.put(80, "户山香澄");
    VOICE_NAME_MAP.put(81, "花火导演1");
    VOICE_NAME_MAP.put(82, "熊的研究所");
    VOICE_NAME_MAP.put(83, "激进仔仔");
    VOICE_NAME_MAP.put(84, "贾小军");
    VOICE_NAME_MAP.put(85, "贾小军终极版");
    VOICE_NAME_MAP.put(86, "贾维斯2");
    VOICE_NAME_MAP.put(87, "间桐樱");
    VOICE_NAME_MAP.put(88, "囁き声がちなメイドロボ");
    VOICE_NAME_MAP.put(89, "让子弹飞-姜文");
    VOICE_NAME_MAP.put(90, "蒋介石");
    VOICE_NAME_MAP.put(91, "角栄");
    VOICE_NAME_MAP.put(92, "今田りくさん音声");
    VOICE_NAME_MAP.put(93, "金正恩");
    VOICE_NAME_MAP.put(94, "近鉄 有田さん 車内放送");
    VOICE_NAME_MAP.put(95, "九安");
    VOICE_NAME_MAP.put(96, "军事纪录片配音");
    VOICE_NAME_MAP.put(97, "卡卡西");
    VOICE_NAME_MAP.put(98, "凯尔希中文");
    VOICE_NAME_MAP.put(99, "科比（牢大）");
    VOICE_NAME_MAP.put(100, "科普-小奇");
    VOICE_NAME_MAP.put(101, "空");
    VOICE_NAME_MAP.put(102, "扣比不");
    VOICE_NAME_MAP.put(103, "懒羊羊");
    VOICE_NAME_MAP.put(104, "老八");
    VOICE_NAME_MAP.put(105, "老黑");
    VOICE_NAME_MAP.put(106, "老蒋");
    VOICE_NAME_MAP.put(107, "老马说车");
    VOICE_NAME_MAP.put(108, "老人");
    VOICE_NAME_MAP.put(109, "老三国关羽");
    VOICE_NAME_MAP.put(110, "老女人快速版");
    VOICE_NAME_MAP.put(111, "乐哥");
    VOICE_NAME_MAP.put(112, "雷军");
    VOICE_NAME_MAP.put(113, "磊哥");
    VOICE_NAME_MAP.put(114, "李丽红舌尖");
    VOICE_NAME_MAP.put(115, "李玫瑾");
    VOICE_NAME_MAP.put(116, "李云龙");
    VOICE_NAME_MAP.put(117, "丽丽");
    VOICE_NAME_MAP.put(118, "历史纪录片-2006");
    VOICE_NAME_MAP.put(119, "刘备-正常");
    VOICE_NAME_MAP.put(120, "刘（王俊凯）");
    VOICE_NAME_MAP.put(121, "刘晓燕");
    VOICE_NAME_MAP.put(122, "卢本伟");
    VOICE_NAME_MAP.put(123, "卢本伟（新模型）");
    VOICE_NAME_MAP.put(124, "绿衣女主持人爆款声");
    VOICE_NAME_MAP.put(125, "落ち着いた男性");
    VOICE_NAME_MAP.put(126, "落ち着いた女性");
    VOICE_NAME_MAP.put(127, "麦当劳");
    VOICE_NAME_MAP.put(128, "马保国");
    VOICE_NAME_MAP.put(129, "麦克阿瑟");
    VOICE_NAME_MAP.put(130, "满穗！！！");
    VOICE_NAME_MAP.put(131, "满穗（23穗）");
    VOICE_NAME_MAP.put(132, "满穗-欢快");
    VOICE_NAME_MAP.put(133, "毛老师激情版");
    VOICE_NAME_MAP.put(134, "美股南哥");
    VOICE_NAME_MAP.put(135, "美輪明宏_肉声");
    VOICE_NAME_MAP.put(136, "美輪明宏Ver.02");
    VOICE_NAME_MAP.put(137, "魔魔");
    VOICE_NAME_MAP.put(138, "蜜雪女主播");
    VOICE_NAME_MAP.put(139, "奶龙(效果最好的一个)");
    VOICE_NAME_MAP.put(140, "纳西妲 (原神)女声");
    VOICE_NAME_MAP.put(141, "男の子");
    VOICE_NAME_MAP.put(142, "男性ナレーター");
    VOICE_NAME_MAP.put(143, "男主");
    VOICE_NAME_MAP.put(144, "男声白老师");
    VOICE_NAME_MAP.put(145, "小俊");
    VOICE_NAME_MAP.put(146, "南柏先生声音（激情版）");
    VOICE_NAME_MAP.put(147, "女大学生");
    VOICE_NAME_MAP.put(148, "自分４");
    VOICE_NAME_MAP.put(149, "千年");
    VOICE_NAME_MAP.put(150, "強弱なしこまいぼいす");
    VOICE_NAME_MAP.put(151, "青浦中安报站");
    VOICE_NAME_MAP.put(152, "青子");
    VOICE_NAME_MAP.put(153, "清雅女声");
    VOICE_NAME_MAP.put(154, "祁同伟");
    VOICE_NAME_MAP.put(155, "少年2");
    VOICE_NAME_MAP.put(156, "小说抒情男");
    VOICE_NAME_MAP.put(157, "シャア・アズナブル | 平和的に");
    VOICE_NAME_MAP.put(158, "人生のたび");
    VOICE_NAME_MAP.put(159, "日本人渋い声の男性");
    VOICE_NAME_MAP.put(160, "日化发货2");
    VOICE_NAME_MAP.put(161, "日漫少女");
    VOICE_NAME_MAP.put(162, "日常营销号");
    VOICE_NAME_MAP.put(163, "赛博朋克");
    VOICE_NAME_MAP.put(164, "诗歌剧");
    VOICE_NAME_MAP.put(165, "三月七");
    VOICE_NAME_MAP.put(166, "桑海");
    VOICE_NAME_MAP.put(167, "山鸡");
    VOICE_NAME_MAP.put(168, "山田凉");
    VOICE_NAME_MAP.put(169, "少女声音");
    VOICE_NAME_MAP.put(170, "少羽（杨志平）");
    VOICE_NAME_MAP.put(171, "舌尖上的中国");
    VOICE_NAME_MAP.put(172, "沈腾");
    VOICE_NAME_MAP.put(173, "声音");
    VOICE_NAME_MAP.put(174, "说书03");
    VOICE_NAME_MAP.put(175, "盛国恒");
    VOICE_NAME_MAP.put(176, "小樱");
    VOICE_NAME_MAP.put(177, "GTI指挥官");
    VOICE_NAME_MAP.put(178, "士道");
    VOICE_NAME_MAP.put(179, "电影解说");
    VOICE_NAME_MAP.put(180, "影视解说");
    VOICE_NAME_MAP.put(181, "跳跳");
    VOICE_NAME_MAP.put(182, "游戏解说-快");
    VOICE_NAME_MAP.put(183, "黑卡蒂");
    VOICE_NAME_MAP.put(184, "台湾女女");
    VOICE_NAME_MAP.put(185, "帅帅的大男孩 +可疑");
    VOICE_NAME_MAP.put(186, "沢城みゆき");
    VOICE_NAME_MAP.put(187, "说书03");
    VOICE_NAME_MAP.put(188, "Ran Mouri (毛利 蘭, Detective Conan)");
    VOICE_NAME_MAP.put(189, "四郎生气");
    VOICE_NAME_MAP.put(190, "田中角栄の声");
    VOICE_NAME_MAP.put(191, "户外女主播");
    VOICE_NAME_MAP.put(192, "孙笑川258");
    VOICE_NAME_MAP.put(193, "孙盈盈（声音1）");
    VOICE_NAME_MAP.put(194, "孫悟空");
    VOICE_NAME_MAP.put(195, "台湾男");
    VOICE_NAME_MAP.put(196, "台湾女女");
    VOICE_NAME_MAP.put(197, "太乙真人自用版");
    VOICE_NAME_MAP.put(198, "太平盛世");
    VOICE_NAME_MAP.put(199, "唐三");
    VOICE_NAME_MAP.put(200, "唐僧");
    VOICE_NAME_MAP.put(201, "糖猫米雪儿");
    VOICE_NAME_MAP.put(202, "陶矜");
    VOICE_NAME_MAP.put(203, "陶衿");
    VOICE_NAME_MAP.put(204, "特遣队员（男）.mp3");
    VOICE_NAME_MAP.put(205, "天童爱丽丝（扫地机器人、女儿）（中配）");
    VOICE_NAME_MAP.put(206, "天宇一休");
    VOICE_NAME_MAP.put(207, "甜美女主播");
    VOICE_NAME_MAP.put(208, "甜美女声");
    VOICE_NAME_MAP.put(209, "跳跳");
    VOICE_NAME_MAP.put(210, "潮汐摆摆");
    VOICE_NAME_MAP.put(211, "万子颖");
    VOICE_NAME_MAP.put(212, "王琨");
    VOICE_NAME_MAP.put(213, "王琨声音模型10.17t1");
    VOICE_NAME_MAP.put(214, "王俊凯");
    VOICE_NAME_MAP.put(215, "王炸音频");
    VOICE_NAME_MAP.put(216, "王者李白");
    VOICE_NAME_MAP.put(217, "王一博");
    VOICE_NAME_MAP.put(218, "1204-女主持人");
    VOICE_NAME_MAP.put(219, "温柔动听女声");
    VOICE_NAME_MAP.put(220, "国恒");
    VOICE_NAME_MAP.put(221, "吴彦祖15");
    VOICE_NAME_MAP.put(222, "五条悟");
    VOICE_NAME_MAP.put(223, "小小班班班");
    VOICE_NAME_MAP.put(224, "今田りくさん音声");
    VOICE_NAME_MAP.put(225, "闲鱼配音大壮");
    VOICE_NAME_MAP.put(226, "刘备-正常");
    VOICE_NAME_MAP.put(227, "ギャル子");
    VOICE_NAME_MAP.put(228, "天童爱丽丝（扫地机器人、女儿）（中配）");
    VOICE_NAME_MAP.put(229, "小凡-激动");
    VOICE_NAME_MAP.put(230, "小孩3");
    VOICE_NAME_MAP.put(231, "小明剑魔");
    VOICE_NAME_MAP.put(232, "小俊");
    VOICE_NAME_MAP.put(233, "懒羊羊");
    VOICE_NAME_MAP.put(234, "小莹");
    VOICE_NAME_MAP.put(235, "小樱");
    VOICE_NAME_MAP.put(236, "小说抒情男");
    VOICE_NAME_MAP.put(237, "女大学生");
    VOICE_NAME_MAP.put(238, "青子");
    VOICE_NAME_MAP.put(239, "赛博朋克");
    VOICE_NAME_MAP.put(240, "户外女主播");
    VOICE_NAME_MAP.put(241, "新幹線・車内放送の声[男性]");
    VOICE_NAME_MAP.put(242, "新游戏解说");
    VOICE_NAME_MAP.put(243, "信ボイスver1.0");
    VOICE_NAME_MAP.put(244, "行善");
    VOICE_NAME_MAP.put(245, "熊二");
    VOICE_NAME_MAP.put(246, "旭旭宝宝");
    VOICE_NAME_MAP.put(247, "宣传片");
    VOICE_NAME_MAP.put(248, "雪莉酒（女）");
    VOICE_NAME_MAP.put(249, "二狗");
    VOICE_NAME_MAP.put(250, "月半猫");
    VOICE_NAME_MAP.put(251, "央视配音");
    VOICE_NAME_MAP.put(252, "杨幂");
    VOICE_NAME_MAP.put(253, "冯宝宝");
    VOICE_NAME_MAP.put(254, "仿真人（男声、偏用于广告、捏造事实类）");
    VOICE_NAME_MAP.put(255, "我叫夯大力（包像版）");
    VOICE_NAME_MAP.put(256, "仙逆1");
    VOICE_NAME_MAP.put(257, "中医提问");
    VOICE_NAME_MAP.put(258, "范志波（专家）");
    VOICE_NAME_MAP.put(259, "12.9 王鑫");
    VOICE_NAME_MAP.put(260, "李玫瑾");
    VOICE_NAME_MAP.put(261, "俄罗斯");
    VOICE_NAME_MAP.put(262, "磁性男声");
    VOICE_NAME_MAP.put(263, "爱莉");
    VOICE_NAME_MAP.put(264, "弘历");
    VOICE_NAME_MAP.put(265, "万子颖");
    VOICE_NAME_MAP.put(266, "不知火舞");
    VOICE_NAME_MAP.put(267, "甜美女声");
    VOICE_NAME_MAP.put(268, "美輪明宏Ver.02");
    VOICE_NAME_MAP.put(269, "赵阳");
    VOICE_NAME_MAP.put(270, "少羽（杨志平）");
    VOICE_NAME_MAP.put(271, "黑碎屏");
    VOICE_NAME_MAP.put(272, "户外女主播");
    VOICE_NAME_MAP.put(273, "仙逆1");
    VOICE_NAME_MAP.put(274, "闲鱼配音大壮");
    VOICE_NAME_MAP.put(275, "老三国关羽");
    VOICE_NAME_MAP.put(276, "相声?");
    VOICE_NAME_MAP.put(277, "陳章 台灣男聲 活力親切 高品質");
    VOICE_NAME_MAP.put(278, "美股南哥");
    VOICE_NAME_MAP.put(279, "赛马娘");
    VOICE_NAME_MAP.put(280, "今田りくさん音声");
    VOICE_NAME_MAP.put(281, "痞老板2");
    VOICE_NAME_MAP.put(282, "白领御姐音");
    VOICE_NAME_MAP.put(283, "董宇辉");
    VOICE_NAME_MAP.put(284, "邓紫琪");
    VOICE_NAME_MAP.put(285, "fufu大王3.0（初音未来）");
    VOICE_NAME_MAP.put(286, "有兽焉 天禄");
    VOICE_NAME_MAP.put(287, "信ボイスver1.0");
    VOICE_NAME_MAP.put(288, "鱼木123");
    VOICE_NAME_MAP.put(289, "行善");
    VOICE_NAME_MAP.put(290, "御女茉莉");
    VOICE_NAME_MAP.put(291, "熊的研究所");
    VOICE_NAME_MAP.put(292, "科普-小奇");
    VOICE_NAME_MAP.put(293, "角栄");
    VOICE_NAME_MAP.put(294, "买量解说");
    VOICE_NAME_MAP.put(295, "毕业季温情女学生");
    VOICE_NAME_MAP.put(296, "二狗");
    VOICE_NAME_MAP.put(297, "八重神子");
    VOICE_NAME_MAP.put(298, "糖猫米雪儿");
    VOICE_NAME_MAP.put(299, "中年老女性");
    VOICE_NAME_MAP.put(300, "alex克隆");
    VOICE_NAME_MAP.put(301, "眼前这位老哥");
    VOICE_NAME_MAP.put(302, "杨幂");
    VOICE_NAME_MAP.put(303, "纳西妲 (原神)女声");
    VOICE_NAME_MAP.put(304, "张颂文");
    VOICE_NAME_MAP.put(305, "女大学生");
    VOICE_NAME_MAP.put(306, "仿真人（男声、偏用于广告、捏造事实类）");
    VOICE_NAME_MAP.put(307, "九安");
    VOICE_NAME_MAP.put(308, "蔚蓝档案爱丽丝中文");
    VOICE_NAME_MAP.put(309, "游戏男解说");
    VOICE_NAME_MAP.put(310, "合肥女主持人");
    VOICE_NAME_MAP.put(311, "赛博朋克");
    VOICE_NAME_MAP.put(312, "12.9 王鑫");
    VOICE_NAME_MAP.put(313, "24.12.27违医生");
    VOICE_NAME_MAP.put(314, "白鹿的声音");
    VOICE_NAME_MAP.put(315, "大健康男医生");
    VOICE_NAME_MAP.put(316, "1204-女主持人");
    VOICE_NAME_MAP.put(317, "激进仔仔");
    VOICE_NAME_MAP.put(318, "雷军");
    VOICE_NAME_MAP.put(319, "蔡徐坤");
    VOICE_NAME_MAP.put(320, "英国海军(British Navy)G&B");
    VOICE_NAME_MAP.put(321, "琨哥");
}

String getVoiceName(int voiceNum) {
    String name = VOICE_NAME_MAP.get(voiceNum);
    return name != null ? name : "未知音色";
}

String getVoiceListText() {
    var sb = new StringBuilder();
    sb.append("🎙️ 音色列表（共321个）：\n\n");
    for (int i = 1; i <= 321; i++) {
        String name = getVoiceName(i);
        sb.append(i).append(". ").append(name).append("\n");
    }
    sb.append("\n当前默认音色：");
    var currentVoice = getString("voice", "7");
    if (isValidVoice(currentVoice)) {
        var currentVoiceNum = Integer.parseInt(currentVoice);
        sb.append(currentVoice).append(" - ").append(getVoiceName(currentVoiceNum));
    } else {
        sb.append("未设置");
    }
    sb.append("\n\n📌 使用方法：\n");
    sb.append("1. 查看帮助弹窗：\n");
    sb.append("   音色列表\n   帮助\n   说明\n\n");
    sb.append("2. 修改默认音色：\n");
    sb.append("   音色 7\n\n");
    sb.append("3. 使用默认音色生成语音：\n");
    sb.append("   #tts 你好\n\n");
    sb.append("4. 临时指定音色生成语音：\n");
    sb.append("   #tts 3 你好\n\n");
    sb.append("说明：临时指定音色不会修改默认音色。");
    return sb.toString();
}

void showVoiceListDialog() {
    var ctx = getTopActivity();
    var scrollView = new ScrollView(ctx);
    var textView = new TextView(ctx);
    textView.setText(getVoiceListText());
    textView.setTextSize(14);
    textView.setPadding(36, 24, 36, 24);
    textView.setTextIsSelectable(true);
    scrollView.addView(textView);
    new AlertDialog.Builder(ctx)
            .setTitle("音色列表与使用说明")
            .setView(scrollView)
            .setPositiveButton("知道了", null)
            .show();
}

boolean isHelpCommand(String text) {
    if (text == null) return false;
    var t = text.trim();
    return t.equals("音色列表") || t.equals("帮助") || t.equals("说明");
}

boolean isValidVoice(String voiceStr) {
    if (voiceStr == null || voiceStr.isEmpty()) return false;
    if (!voiceStr.matches("\\d{1,3}")) return false;
    var voiceNum = Integer.parseInt(voiceStr);
    return voiceNum >= 1 && voiceNum <= 321;
}

void openSettings() {
    var ctx = getTopActivity();
    var layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 0);
    var lpEdt = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    );
    lpEdt.setMargins(0, 16, 0, 16);
    var edtKey = new EditText(ctx);
    edtKey.setHint("请输入API密钥");
    edtKey.setText(getString("apikey", "4692f9ce7f1372f65887e13c7aee3421cbfabaec4307789e3023dc52bd667ea4"));
    layout.addView(edtKey, lpEdt);
    var edtVoice = new EditText(ctx);
    edtVoice.setHint("默认音色(1-321)，默认7爱莉");
    edtVoice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    edtVoice.setText(getString("voice", "7"));
    layout.addView(edtVoice, lpEdt);
    var edtTimeout = new EditText(ctx);
    edtTimeout.setHint("超时时间(秒)，默认60");
    edtTimeout.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    edtTimeout.setText(getString("timeout", "60"));
    layout.addView(edtTimeout, lpEdt);
    new AlertDialog.Builder(ctx)
            .setTitle("文转音 - 语音发送")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                var apikey = edtKey.getText().toString().trim();
                putString("apikey", apikey);
                var voice = edtVoice.getText().toString().trim();
                if (voice.isEmpty()) voice = "7";
                if (!isValidVoice(voice)) {
                    toast("音色编号无效，请输入 1-321");
                    return;
                }
                putString("voice", voice);
                var timeout = edtTimeout.getText().toString().trim();
                if (timeout.isEmpty()) timeout = "60";
                putString("timeout", timeout);
                var voiceNum = Integer.parseInt(voice);
                var voiceName = getVoiceName(voiceNum);
                toast("保存成功，默认音色：" + voice + " - " + voiceName);
            })
            .setNegativeButton("取消", null)
            .show();
}

boolean onClickSendBtn(String text) {
    // 帮助/说明/音色列表弹窗指令
    if (isHelpCommand(text)) {
        showVoiceListDialog();
        return true;
    }

    // 修改默认音色指令：音色 7
    var voiceCmd = "音色 ";
    if (text.startsWith(voiceCmd)) {
        var voiceStr = text.substring(voiceCmd.length()).trim();
        if (voiceStr.isEmpty()) {
            toast("请输入要修改的音色编号，如：音色 7");
            return true;
        }
        if (!isValidVoice(voiceStr)) {
            toast("音色编号无效，请输入 1-321，例如：音色 7");
            return true;
        }
        putString("voice", voiceStr);
        var voiceNum = Integer.parseInt(voiceStr);
        var voiceName = getVoiceName(voiceNum);
        toast("默认音色已修改为：" + voiceStr + " - " + voiceName);
        return true;
    }

    // 文转音发送指令：#tts 你好  或  #tts 2 你好
    var cmd = "#tts ";
    if (text.startsWith(cmd)) {
        var raw = text.substring(cmd.length()).trim();
        if (raw.isEmpty()) {
            toast("请输入要转换的文字，如: #tts 你好世界 或 #tts 2 你好世界");
            return true;
        }
        var apikey = getString("apikey", "4692f9ce7f1372f65887e13c7aee3421cbfabaec4307789e3023dc52bd667ea4");
        var defaultVoice = getString("voice", "7");
        var voiceStr = defaultVoice;
        var msgStr = raw;
        // 检查是否包含临时音色编号
        var idx = raw.indexOf(" ");
        if (idx > 0) {
            var firstPart = raw.substring(0, idx);
            if (firstPart.matches("\\d{1,3}")) {
                voiceStr = firstPart;
                msgStr = raw.substring(idx + 1).trim();
                if (msgStr.isEmpty()) {
                    toast("请输入要转换的文字，如: #tts 2 你好世界");
                    return true;
                }
            }
        }
        if (!isValidVoice(voiceStr)) {
            toast("音色编号无效，请输入 1-321");
            return true;
        }
        var voiceNum = Integer.parseInt(voiceStr);
        var voiceName = getVoiceName(voiceNum);

        // 编码文字并请求 MP3 音频
        var encodedText = java.net.URLEncoder.encode(msgStr, "UTF-8");
        var apiUrl = "https://www.tiax.pw/API/yuyin.php?msg=" + encodedText + "&apikey=" + apikey + "&ys=" + voiceStr;
        toast("正在生成语音（" + voiceName + "），请稍候...");

        // 下载 MP3 文件
        var mp3Path = cacheDir + "/voice_" + System.currentTimeMillis() + ".mp3";
        download(apiUrl, mp3Path, null, file -> {
            if (file != null && file.exists() && file.length() > 0) {
                // 使用 WAuxiliary 插件提供的 MP3 → SILK 转换方法
                File silkFile = mp3ToSilkFile(mp3Path);
                if (silkFile != null && silkFile.exists() && silkFile.length() > 0) {
                    var talker = getTargetTalker();
                    sendVoice(talker, silkFile.getAbsolutePath());
                    // 转换后的 SILK 文件在发送后可以删除（插件会自动清理或手动）
                    silkFile.delete();
                } else {
                    toast("语音格式转换失败，请检查系统环境");
                }
                // 删除临时 MP3
                file.delete();
            } else {
                toast("语音生成失败或返回为空，请检查网络和API密钥");
            }
        });
        return true;
    }
    return false;
}