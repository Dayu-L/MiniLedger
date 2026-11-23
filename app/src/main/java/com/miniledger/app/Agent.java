package com.miniledger.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记账 agent 的本地规则解析：秒出、离线、零依赖。
 *
 * 和 DayFlow 的 agent.js 一个套路：
 *   1. 先认「查询 / 删除」这类意图（由 PageChat 处理，需要数据）
 *   2. 剩下的当记账语句，抽取 时间 / 金额 / 方向 / 分类 / 账户
 *   3. 一句话里多个金额就拆成多笔
 *
 * 这个类是纯 Java，不碰 Android，所以可以拿 JVM 直接跑单测。
 */
final class Agent {

    private Agent() {}

    static final long DAY = 86400000L;

    static final class Result {
        String text = "";
        List<Tx> items = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        String tip = "";
        boolean changed = false;
        String mode = "local";
    }

    /** 大模型返回的改账动作。 */
    static final class Action {
        String type = "";      // update | delete
        long id;
        Long amount;           // null 表示不改
        String category;       // null 表示不改
        String note;
    }

    /** 意图：记账 / 改账 / 删账 / 查支出 / 查预算 / 看最近。 */
    static String detectIntent(String raw) {
        String t = normalize(raw);
        if (t.matches(".*(改成|改为|改到|记成|归到|记到|算\\s*(餐饮|交通|购物|日用|娱乐|居住|通讯|医疗|学习|人情|其他|工资|红包|退款|理财)).*")) return "edit";
        if (t.matches(".*(删掉|删除|撤销|去掉|记错了).*")) return "delete";
        if (t.matches(".*(花了多少|花多少|支出多少|一共花|这月花|本月花|上周花|今天花|统计|汇总).*")) return "spend";
        if (t.matches(".*(还剩多少|预算).*")) return "budget";
        if (t.matches(".*(最近|最后几笔|刚才记).*")) return "recent";
        return "record";
    }

    // ------------------------------------------------------------------ 改账

    static final class Edit {
        static final int NONE = 0, AMOUNT = 1, CATEGORY = 2, DELETE = 3, ACCOUNT = 4;
        int kind = NONE;
        long amount;
        String category = "";
        String account = "";
        int start, end;      // 被识别成「修改指令」的那一段，找目标时挖掉
    }

    private static final Pattern P_EDIT =
            Pattern.compile("(?:改成|改为|改到|记成|归到|记到|应该是|其实是|算)\\s*([^，,。；;\\s]{1,8})");

    static Edit detectEdit(String raw) {
        String t = normalize(raw);
        Edit e = new Edit();

        if (t.matches(".*(删掉|删除|撤销|去掉|记错了).*")) {
            Matcher dm = Pattern.compile("删掉|删除|撤销|去掉|记错了").matcher(t);
            if (dm.find()) {
                e.kind = Edit.DELETE;
                e.start = dm.start();
                e.end = dm.end();
            }
            return e;
        }

        Matcher m = P_EDIT.matcher(t);
        if (!m.find()) return e;

        String v = m.group(1).trim();
        e.start = m.start();
        e.end = m.end();

        if (v.matches(".*\\d.*")) {
            long cents = Money.parse(v.replaceAll("[^0-9.]", ""));
            if (cents > 0) {
                e.kind = Edit.AMOUNT;
                e.amount = cents;
                return e;
            }
        }
        String cat = detectCategory(v);
        if (!cat.isEmpty()) {
            e.kind = Edit.CATEGORY;
            e.category = cat;
            return e;
        }
        String acc = detectAccount(v);
        if (!acc.isEmpty()) {
            e.kind = Edit.ACCOUNT;
            e.account = acc;
            return e;
        }
        e.kind = Edit.NONE;
        return e;
    }

    /**
     * 在一堆已有账单里找出用户指的那一笔。
     * 打分：商户命中 > 分类命中 > 日期命中 > 金额命中，取最高分。
     */
    static Tx findTarget(String text, List<Tx> candidates, long now) {
        String norm = normalize(text);
        Span date = findDate(norm, now);
        String masked = mask(mask(norm, date), findClock(norm));

        Tx best = null;
        int bestScore = 0;
        for (Tx t : candidates) {
            int s = 0;

            String merchant = t.merchant == null ? "" : t.merchant;
            if (merchant.length() >= 2 && masked.contains(merchant)) s += 6;
            else {
                for (int len = Math.min(4, merchant.length()); len >= 2; len--) {
                    if (masked.contains(merchant.substring(0, len))) {
                        s += 4;
                        break;
                    }
                }
            }

            if (!t.category.isEmpty() && masked.contains(t.category)) s += 3;

            if (date != null && date.ts == startOfDay(t.ts)) s += 4;

            String amt = Money.fmt(t.amount).replace(",", "").replace(".00", "");
            if (amt.length() >= 1 && masked.contains(amt)) s += 2;

            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        return bestScore >= 3 ? best : null;
    }

    // ------------------------------------------------------------------ 文本

    static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '０' && c <= '９') c = (char) (c - 0xFEE0);
            if (c == '￥') c = '¥';
            if (c == '：') c = ':';
            if (c == '，') c = ',';
            if (c == '　') c = ' ';
            sb.append(c);
        }
        return sb.toString().replaceAll("[ \\t]+", " ").replace('\n', ' ').trim();
    }

    // ------------------------------------------------------------------ 时间

    static final class Span {
        int start, end;
        long ts;
        Span(int s, int e, long t) { start = s; end = e; ts = t; }
    }

    private static final Pattern P_FULL_DATE =
            Pattern.compile("(\\d{4})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern P_MD =
            Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]");
    private static final Pattern P_DAY =
            Pattern.compile("(\\d{1,2})\\s*[日号](?!\\s*(?:星期|周))");
    private static final Pattern P_REL_DAYS =
            Pattern.compile("(\\d{1,2})\\s*天前");
    private static final Pattern P_WEEKDAY =
            Pattern.compile("(?:上|本|这)?\\s*(?:周|星期|礼拜)\\s*([一二三四五六日天1-7])");
    private static final Pattern P_CLOCK =
            Pattern.compile("(\\d{1,2})\\s*[:点]\\s*(\\d{1,2})?\\s*分?");

    private static final String[][] REL = {
            {"大前天", "-3"}, {"前天", "-2"}, {"昨天", "-1"}, {"昨晚", "-1"},
            {"今天", "0"}, {"今日", "0"}, {"刚才", "0"}, {"刚刚", "0"}, {"明天", "1"}
    };

    static long startOfDay(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long shiftDays(long dayStart, int delta) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, delta);
        return c.getTimeInMillis();
    }

    private static int weekdayOf(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        int w = c.get(Calendar.DAY_OF_WEEK) - 1;   // 1=周一 … 0=周日
        return w;
    }

    /** 找出句子里的日期表达（只取第一个有效的），返回 null 表示没提日期。 */
    static Span findDate(String text, long now) {
        // 完整日期
        Matcher m = P_FULL_DATE.matcher(text);
        if (m.find()) {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2)) - 1;
            int d = Integer.parseInt(m.group(3));
            return new Span(m.start(), m.end(), buildDate(y, mo, d, now));
        }
        // X月X日
        m = P_MD.matcher(text);
        if (m.find()) {
            int mo = Integer.parseInt(m.group(1)) - 1;
            int d = Integer.parseInt(m.group(2));
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            long ts = buildDate(c.get(Calendar.YEAR), mo, d, now);
            if (ts > startOfDay(now)) ts = buildDate(c.get(Calendar.YEAR) - 1, mo, d, now);
            return new Span(m.start(), m.end(), ts);
        }
        // 相对日
        for (String[] rel : REL) {
            int i = text.indexOf(rel[0]);
            if (i >= 0) {
                long ts = shiftDays(startOfDay(now), Integer.parseInt(rel[1]));
                return new Span(i, i + rel[0].length(), ts);
            }
        }
        // N 天前
        m = P_REL_DAYS.matcher(text);
        if (m.find()) {
            long ts = shiftDays(startOfDay(now), -Integer.parseInt(m.group(1)));
            return new Span(m.start(), m.end(), ts);
        }
        // 周X
        m = P_WEEKDAY.matcher(text);
        if (m.find()) {
            int target = weekIndex(m.group(1));
            int today = weekdayOf(now);
            int back = (today - target + 7) % 7;
            long ts = shiftDays(startOfDay(now), -back);
            return new Span(m.start(), m.end(), ts);
        }
        // 单 X 号
        m = P_DAY.matcher(text);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            long ts = buildDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), d, now);
            if (ts > startOfDay(now)) {
                c.add(Calendar.MONTH, -1);
                ts = buildDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), d, now);
            }
            return new Span(m.start(), m.end(), ts);
        }
        return null;
    }

    private static int weekIndex(String s) {
        switch (s) {
            case "一": case "1": return 1;
            case "二": case "2": return 2;
            case "三": case "3": return 3;
            case "四": case "4": return 4;
            case "五": case "5": return 5;
            case "六": case "6": return 6;
            default: return 0;   // 日 / 天 / 7 / 0
        }
    }

    private static long buildDate(int y, int month, int day, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, Math.max(1, Math.min(day, 28)));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 时段词 -> 小时。 */
    private static int periodHour(String text) {
        if (text.contains("凌晨")) return 3;
        if (text.contains("早上") || text.contains("早晨") || text.contains("上午")) return 9;
        if (text.contains("中午")) return 12;
        if (text.contains("下午")) return 15;
        if (text.contains("傍晚")) return 18;
        if (text.contains("晚上")) return 20;
        if (text.contains("夜里") || text.contains("半夜")) return 23;
        return -1;
    }

    // ------------------------------------------------------------------ 金额

    static final class Amt {
        int start, end;
        long cents;
        boolean explicitUnit;
        Amt(int s, int e, long c, boolean u) { start = s; end = e; cents = c; explicitUnit = u; }
    }

    private static final Pattern P_AMOUNT =
            Pattern.compile("(?:[¥￥]\\s*)?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?:\\s*(元|块))?");

    private static final String CLASSIFIERS = "杯个斤份瓶包张次人件双只盒碗袋箱根条支套台部本页";
    private static final String[] SKIP_BEFORE = {"一共", "总共", "合计", "原价", "总价", "人均", "每人", "第"};

    static List<Amt> findAmounts(String text) {
        List<Amt> out = new ArrayList<>();
        Matcher m = P_AMOUNT.matcher(text);
        while (m.find()) {
            int s = m.start();
            int e = m.end();

            String after = text.substring(e, Math.min(text.length(), e + 1));
            if (!after.isEmpty() && CLASSIFIERS.indexOf(after.charAt(0)) >= 0) continue;

            String before = text.substring(Math.max(0, s - 3), s);
            boolean skip = false;
            for (String b : SKIP_BEFORE) if (before.contains(b)) skip = true;
            if (skip) continue;

            long cents = Money.parse(m.group(1));
            if (cents <= 0) continue;

            boolean unit = m.group(2) != null || (s > 0 && text.charAt(s - 1) == '¥');
            out.add(new Amt(s, e, cents, unit));
        }
        return out;
    }

    /** 按标点、以及「数字 + 空格 + 汉字」切子句：三笔挤在一句话里也能分开。 */
    private static List<int[]> splitClauses(String text) {
        List<int[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("[、,，;；/]|(?<=[0-9])\\s+(?=\\D)|\\s+和\\s+").matcher(text);
        int from = 0;
        while (m.find()) {
            if (m.start() > from) out.add(new int[]{from, m.start()});
            from = m.end();
        }
        if (from < text.length()) out.add(new int[]{from, text.length()});
        return out;
    }

    private static String mask(String s, Span sp) {
        if (sp == null) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = Math.max(0, sp.start); i < Math.min(sb.length(), sp.end); i++) {
            sb.setCharAt(i, ' ');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 语义

    private static final String[] KW_IN = {
            "收到", "收入", "进账", "到账", "工资", "薪水", "薪资", "奖金", "报销",
            "退款", "退了", "退货", "退回", "返现", "收益", "利息", "分红", "转入"
    };

    private static final String[][] CATS = {
            {"餐饮", "早饭", "早餐", "午饭", "午餐", "中饭", "晚饭", "晚餐", "夜宵", "吃饭", "外卖", "咖啡",
                    "奶茶", "星巴克", "瑞幸", "麦当劳", "肯德基", "火锅", "烧烤", "食堂", "聚餐", "请客",
                    "小吃", "面包", "蛋糕", "美团", "饿了么"},
            {"交通", "打车", "滴滴", "出租车", "地铁", "公交", "高铁", "火车", "机票", "飞机", "加油",
                    "停车", "单车", "骑行", "车费", "路费", "12306"},
            {"购物", "淘宝", "京东", "拼多多", "天猫", "唯品会", "衣服", "鞋", "裤子", "包", "耳机",
                    "数码", "手机", "电脑", "家具", "商城", "得物"},
            {"日用", "超市", "便利店", "纸巾", "洗衣", "洗发", "牙膏", "日用", "盒马", "买菜", "菜",
                    "生鲜", "水果", "牛奶"},
            {"娱乐", "电影", "游戏", "唱歌", "ktv", "演出", "门票", "健身", "旅游", "剧本杀", "按摩", "会员"},
            {"居住", "房租", "物业", "水电", "电费", "水费", "燃气", "宽带", "装修", "取暖"},
            {"通讯", "话费", "流量", "充话费", "电话费", "移动", "联通", "电信"},
            {"医疗", "医院", "药店", "买药", "挂号", "体检", "看病", "牙医", "药"},
            {"学习", "书", "课程", "培训", "学费", "考试", "教材", "网课", "资料"},
            {"人情", "红包", "礼金", "份子", "送礼", "随礼", "转给"},
            {"工资", "工资", "薪水", "薪资", "奖金"},
            {"退款", "退款", "退货", "返现"},
            {"理财", "收益", "利息", "余额宝", "基金", "分红"}
    };

    private static final String[][] ACCOUNTS = {
            {"微信", "微信", "零钱"},
            {"支付宝", "支付宝", "花呗", "余额宝"},
            {"信用卡", "信用卡", "花呗"},
            {"银行卡", "银行卡", "储蓄卡", "借记卡", "招行", "建行", "工行", "中行", "农行", "交行"},
            {"现金", "现金", "纸币"}
    };

    private static String detectCategory(String s) {
        String t = s.toLowerCase();
        for (String[] row : CATS) {
            // 分类名本身也算命中：「记成交通」里的「交通」
            if (t.contains(row[0].toLowerCase())) return row[0];
            for (int i = 1; i < row.length; i++) {
                if (t.contains(row[i].toLowerCase())) return row[0];
            }
        }
        return "";
    }

    private static String detectAccount(String s) {
        for (String[] row : ACCOUNTS) {
            for (int i = 1; i < row.length; i++) {
                if (s.contains(row[i])) return row[0];
            }
        }
        return "";
    }

    private static boolean isIncome(String s) {
        for (String k : KW_IN) if (s.contains(k)) return true;
        return false;
    }

    private static final String[] FILLER = {
            "花了", "付了", "买了", "用了", "消费", "支出", "花费", "吃饭", "大概", "左右",
            "花", "付", "买", "元", "块", "钱", "了", "的", "给", "在", "和", "跟", "与",
            "请", "一起"
    };

    private static final String[] TIME_WORDS = {
            "今天", "今日", "昨天", "昨晚", "昨日", "前天", "大前天", "刚才", "刚刚", "明天",
            "早上", "早晨", "上午", "中午", "下午", "傍晚", "晚上", "夜里", "半夜", "凌晨",
            "上周", "本周", "这周", "下个", "星期", "礼拜", "周"
    };

    private static String cleanMerchant(String s, String category) {
        String t = s;
        for (String[] acc : ACCOUNTS) {
            for (int i = 1; i < acc.length; i++) t = t.replace(acc[i], " ");
        }
        // 「一共 128」「2杯」这类不算商户
        t = t.replaceAll("(一共|总共|合计|原价|总价|人均|每人)\\s*[0-9.]+", " ");
        t = t.replaceAll("[0-9.]+\\s*[杯个斤份瓶包张次人件双只盒碗袋箱根条支套台部本页]", " ");
        for (String w : TIME_WORDS) t = t.replace(w, " ");
        for (String f : FILLER) t = t.replace(f, " ");
        t = t.replaceAll("[\\s,，。;；、:：!！?？~\\-]+", " ").trim();
        if (t.length() > 12) t = t.substring(0, 12);
        if (t.isEmpty()) return category.isEmpty() ? "" : category;
        return t;
    }

    // ------------------------------------------------------------------ 主流程

    static Result local(String raw, long now) {
        Result r = new Result();
        String text = normalize(raw);
        if (text.isEmpty()) {
            r.text = "说一句就行，比如「午饭 25 微信」。";
            r.tip = "也可以一次说几笔：午饭25、打车38、咖啡19。";
            return r;
        }

        Span date = findDate(text, now);
        Span clock = findClock(text);

        long dayStart = date != null ? date.ts : startOfDay(now);
        boolean isToday = dayStart == startOfDay(now);
        int hour = periodHour(text);
        if (clock != null) hour = (int) ((clock.ts - startOfDay(clock.ts)) / 3600000L);

        // 把时间表达挖空（保持长度），避免「昨天打车」里的「昨天」混进商户名
        String masked = mask(text, date);
        masked = mask(masked, clock);

        // 先切子句，再把没有金额的尾巴（比如「支付宝」）并回上一笔
        List<String> segs = new ArrayList<>();
        for (int[] rg : splitClauses(masked)) {
            String seg = masked.substring(rg[0], rg[1]).trim();
            if (seg.isEmpty()) continue;
            if (findAmounts(seg).isEmpty() && !segs.isEmpty()) {
                segs.set(segs.size() - 1, segs.get(segs.size() - 1) + " " + seg);
            } else {
                segs.add(seg);
            }
        }

        for (String seg : segs) {
            List<Amt> as = findAmounts(seg);
            for (Amt a : as) {
                String noAmount = seg.substring(0, Math.max(0, a.start))
                        + " " + seg.substring(Math.min(seg.length(), a.end));

                Tx t = new Tx();
                t.amount = a.cents;
                t.kind = isIncome(seg) ? Tx.IN : Tx.OUT;
                t.category = detectCategory(seg);
                t.account = detectAccount(seg);
                t.merchant = cleanMerchant(noAmount, t.category);
                t.source = "agent";
                t.status = Tx.DONE;

                long ts = dayStart;
                if (isToday) {
                    ts = now;
                } else {
                    ts += (hour >= 0 ? hour : 12) * 3600000L;
                }
                t.ts = ts;
                r.items.add(t);
            }
        }

        if (r.items.isEmpty()) {
            r.text = "没听出金额。";
            r.tip = "试试「昨天打车 38 微信」或「工资到账 8000」。";
            return r;
        }

        r.changed = true;
        r.text = summary(r.items);
        r.tip = "确认后才会写进账本。也可以接着说「这笔算交通」。";
        return r;
    }

    private static Span findClock(String text) {
        Matcher m = P_CLOCK.matcher(text);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h > 23) continue;
            if (m.end() < text.length() && text.charAt(m.end()) == '元') continue;
            int min = 0;
            if (m.group(2) != null) min = Math.min(59, Integer.parseInt(m.group(2)));
            return new Span(m.start(), m.end(), (h * 60L + min) * 60000L);
        }
        return null;
    }

    private static String summary(List<Tx> items) {
        if (items.size() == 1) return "记这一笔：";
        return "记 " + items.size() + " 笔：";
    }

    // ------------------------------------------------------------------ 大模型

    /** 给模型用的系统提示：说清身份、当前数据、只输出 JSON。 */
    static String systemPrompt(String today, long monthOut, long budget, String recentLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「简账」的记账助手，把用户的一句话变成记账条目。\n");
        sb.append("今天：").append(today).append("。\n");
        sb.append("本月已支出 ").append(Money.yuan(monthOut));
        if (budget > 0) sb.append("，月度预算 ").append(Money.yuan(budget));
        sb.append("。\n");
        if (recentLines != null && !recentLines.isEmpty()) {
            sb.append("最近几笔（带 id，改账用）：\n").append(recentLines).append("\n");
        }
        sb.append("\n只输出一个 JSON 对象，不要 markdown 代码块，不要解释：\n");
        sb.append("{\"reply\":\"给用户看的一句话\",\"items\":[{")
                .append("\"amount\":25.5,\"kind\":\"out\",\"category\":\"餐饮\",")
                .append("\"merchant\":\"美团\",\"account\":\"微信\",\"date\":\"YYYY-MM-DD\",\"note\":\"\"}],")
                .append("\"actions\":[]}\n");
        sb.append("kind 只能是 out(支出) 或 in(收入)。\n");
        sb.append("category 支出从「餐饮 交通 购物 日用 娱乐 居住 通讯 医疗 学习 人情 其他」里选，");
        sb.append("收入从「工资 红包 退款 理财 其他收入」里选。\n");
        sb.append("account 从「微信 支付宝 银行卡 信用卡 现金」里选，不确定就留空。\n");
        sb.append("金额一律是正数。一句话有多笔就放多个 item。没有金额就返回 items 为空数组。\n");
        sb.append("\n要改已有的账，用 actions，id 取自上面「最近几笔」里的 id：\n");
        sb.append("{\"type\":\"update\",\"id\":12,\"amount\":45,\"category\":\"交通\"}  只写要改的字段\n");
        sb.append("{\"type\":\"delete\",\"id\":12}\n");
        sb.append("只记新账时 actions 给空数组或省略；不要凭空编 id。\n");
        return sb.toString();
    }
}
