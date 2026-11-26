package com.miniledger.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

class Db extends SQLiteOpenHelper {

    private static Db inst;
    private List<String[]> rules;

    static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) {
        super(c, "ledger.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE tx(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "kind INTEGER NOT NULL DEFAULT 0," +
                "amount INTEGER NOT NULL DEFAULT 0," +
                "merchant TEXT, category TEXT, account TEXT, note TEXT," +
                "source TEXT, src_pkg TEXT, txid TEXT, raw TEXT," +
                "status INTEGER NOT NULL DEFAULT 1," +
                "dup INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE INDEX ix_tx_ts ON tx(ts)");
        d.execSQL("CREATE UNIQUE INDEX ux_txid ON tx(txid) WHERE txid IS NOT NULL AND txid <> ''");
        d.execSQL("CREATE TABLE rule(id INTEGER PRIMARY KEY AUTOINCREMENT, kw TEXT UNIQUE, cat TEXT)");
        createRecur(d);
        seed(d);
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) createRecur(d);
    }

    /** 固定支出：每月某日自动落一笔到「待确认」。 */
    private static void createRecur(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE IF NOT EXISTS recur(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT, amount INTEGER NOT NULL DEFAULT 0," +
                "category TEXT, day INTEGER NOT NULL DEFAULT 1," +
                "last TEXT, onoff INTEGER NOT NULL DEFAULT 1)");
    }

    private static void r(SQLiteDatabase d, String cat, String... kws) {
        for (String k : kws) {
            d.execSQL("INSERT OR IGNORE INTO rule(kw,cat) VALUES(?,?)", new Object[]{k, cat});
        }
    }

    private static void seed(SQLiteDatabase d) {
        r(d, "餐饮", "美团", "饿了么", "外卖", "麦当劳", "肯德基", "星巴克", "瑞幸", "蜜雪",
                "咖啡", "奶茶", "火锅", "烧烤", "面馆", "饭店", "餐饮", "食堂", "快餐", "小吃");
        r(d, "交通", "滴滴", "高德", "地铁", "公交", "12306", "铁路", "火车", "加油", "中石化",
                "中石油", "停车", "出租车", "哈啰", "青桔", "共享单车", "充电桩", "高速", "etc");
        r(d, "购物", "淘宝", "天猫", "京东", "拼多多", "唯品会", "苏宁", "得物", "小米商城",
                "抖音商城", "商城", "专卖店", "旗舰店");
        r(d, "日用", "超市", "便利店", "盒马", "永辉", "沃尔玛", "全家", "罗森", "711",
                "美宜佳", "百货", "生鲜", "菜市场");
        r(d, "娱乐", "电影", "影院", "猫眼", "淘票票", "游戏", "steam", "网易云", "qq音乐",
                "爱奇艺", "腾讯视频", "优酷", "哔哩", "kTV", "剧本杀", "健身");
        r(d, "居住", "房租", "物业", "水费", "电费", "燃气", "取暖", "自如", "贝壳", "房东", "维修");
        r(d, "通讯", "话费", "流量", "中国移动", "中国联通", "中国电信", "宽带", "充值");
        r(d, "医疗", "医院", "药房", "药店", "诊所", "挂号", "体检", "牙科", "眼科");
        r(d, "学习", "书店", "当当", "课程", "培训", "学费", "得到", "极客时间", "考试", "教材");
        r(d, "人情", "红包", "转账给", "礼金", "份子", "随礼");
        r(d, "工资", "工资", "薪资", "薪水", "奖金", "报销");
        r(d, "退款", "退款", "已退", "退款成功");
        r(d, "理财", "收益", "利息", "余额宝", "理财", "分红");
        r(d, "红包", "收到红包", "抢到红包", "红包到账");
        r(d, "其他收入", "收款", "转入", "到账");
    }

    private List<String[]> rules() {
        if (rules == null) {
            rules = new ArrayList<>();
            Cursor c = getReadableDatabase()
                    .rawQuery("SELECT kw,cat FROM rule ORDER BY length(kw) DESC", null);
            while (c.moveToNext()) rules.add(new String[]{c.getString(0), c.getString(1)});
            c.close();
        }
        return rules;
    }

    String categorize(String text) {        if (text == null || text.isEmpty()) return null;
        String s = text.toLowerCase(java.util.Locale.CHINA);
        for (String[] r : rules()) {
            if (s.contains(r[0].toLowerCase(java.util.Locale.CHINA))) return r[1];
        }
        return null;
    }

    /**
     * 商户记忆：用户改过一次分类，就把「商户 -> 分类」写进规则表，
     * 下次同一个商户的自动记账直接用这个分类。
     */
    void learn(String merchant, String category) {
        if (merchant == null || category == null) return;
        String kw = merchant.trim();
        if (kw.length() < 2 || kw.length() > 12) return;
        if (kw.equals("微信支付") || kw.equals("支付宝") || kw.equals("未知")) return;
        try {
            getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO rule(kw,cat) VALUES(?,?)",
                    new Object[]{kw, category});
            rules = null;
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- write

    /**
     * 写入一笔。
     * @return 新 id；-1 = 单号重复；-2 = 手动记账 2 分钟内记过一样的
     */
    long insert(Tx t, boolean autoConfirm) {
        SQLiteDatabase d = getWritableDatabase();

        if (t.txid != null && !t.txid.isEmpty()) {
            Cursor c = d.rawQuery("SELECT id FROM tx WHERE txid=? LIMIT 1", new String[]{t.txid});
            boolean has = c.moveToFirst();
            c.close();
            if (has) return -1;
        }

        if ("manual".equals(t.source)) {
            // 手动的防抖：同一天+同金额+同商户，2 分钟内已经有一笔 → 不再写
            if (sameSoon(t, 120000L) != null) return -2;
        }

        if ("notify".equals(t.source)) {
            // 同一来源、同金额、2.5 分钟内 -> 认为是同一条重复推送
            long near = near(t.amount, t.ts, t.srcPkg, 150000L);
            if (near > 0) return -1;
            // 不同来源同金额 -> 疑似重复，交给用户判断
            if (near(t.amount, t.ts, null, 150000L) > 0) t.dup = 1;
        }

        if (t.category == null || t.category.isEmpty()) {
            t.category = categorize(t.merchant + " " + t.raw);
        }
        if (t.category == null || t.category.isEmpty()) t.category = Categories.def(t.kind);

        if ("notify".equals(t.source)) {
            t.status = (autoConfirm && t.dup == 0) ? Tx.DONE : Tx.PENDING;
        } else if ("recur".equals(t.source)) {
            // 固定支出不静默扣钱，先落「待确认」
            t.status = Tx.PENDING;
        } else {
            t.status = Tx.DONE;
        }

        ContentValues v = new ContentValues();
        v.put("ts", t.ts);
        v.put("kind", t.kind);
        v.put("amount", t.amount);
        v.put("merchant", t.merchant);
        v.put("category", t.category);
        v.put("account", t.account);
        v.put("note", t.note);
        v.put("source", t.source);
        v.put("src_pkg", t.srcPkg);
        v.put("txid", t.txid);
        v.put("raw", t.raw);
        v.put("status", t.status);
        v.put("dup", t.dup);
        try {
            return d.insert("tx", null, v);
        } catch (Exception e) {
            return -1;
        }
    }

    private long near(long amount, long ts, String pkg, long windowMs) {
        String sql = "SELECT id FROM tx WHERE amount=? AND ABS(ts-?)<?";
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(amount));
        args.add(String.valueOf(ts));
        args.add(String.valueOf(windowMs));
        if (pkg != null) {
            sql += " AND src_pkg=?";
            args.add(pkg);
        }
        sql += " LIMIT 1";
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    void update(Tx t) {
        Tx old = byId(t.id);
        if (old != null && !old.category.equals(t.category)) {
            learn(old.merchant, t.category);
        }
        ContentValues v = new ContentValues();
        v.put("ts", t.ts);
        v.put("kind", t.kind);
        v.put("amount", t.amount);
        v.put("merchant", t.merchant);
        v.put("category", t.category);
        v.put("account", t.account);
        v.put("note", t.note);
        v.put("status", t.status);
        v.put("dup", t.dup);
        getWritableDatabase().update("tx", v, "id=?", new String[]{String.valueOf(t.id)});
    }

    void confirm(long id) {
        ContentValues v = new ContentValues();
        v.put("status", Tx.DONE);
        v.put("dup", 0);
        getWritableDatabase().update("tx", v, "id=?", new String[]{String.valueOf(id)});
    }

    void confirmAll() {
        ContentValues v = new ContentValues();
        v.put("status", Tx.DONE);
        v.put("dup", 0);
        SQLiteDatabase d = getWritableDatabase();
        d.update("tx", v, "status=0", null);
        // 顺便把还没分类的补一下
        Cursor c = d.rawQuery("SELECT id, merchant, raw FROM tx WHERE category IS NULL OR category=''", null);
        while (c.moveToNext()) {
            String cat = categorize(c.getString(1) + " " + c.getString(2));
            if (cat != null) {
                ContentValues cv = new ContentValues();
                cv.put("category", cat);
                d.update("tx", cv, "id=?", new String[]{c.getString(0)});
            }
        }
        c.close();
    }

    void del(long id) {
        getWritableDatabase().delete("tx", "id=?", new String[]{String.valueOf(id)});
    }

    /** 删错了再放回去：所有字段原样写回（新 id）。 */
    long restore(Tx t) {
        ContentValues v = new ContentValues();
        v.put("ts", t.ts);
        v.put("kind", t.kind);
        v.put("amount", t.amount);
        v.put("merchant", t.merchant);
        v.put("category", t.category);
        v.put("account", t.account);
        v.put("note", t.note);
        v.put("source", t.source);
        v.put("src_pkg", t.srcPkg);
        v.put("txid", t.txid);
        v.put("raw", t.raw);
        v.put("status", t.status);
        v.put("dup", t.dup);
        try {
            return getWritableDatabase().insert("tx", null, v);
        } catch (Exception e) {
            // txid 唯一索引碰了：去掉单号再插一次
            v.put("txid", "");
            try {
                return getWritableDatabase().insert("tx", null, v);
            } catch (Exception e2) {
                return -1;
            }
        }
    }

    /** 已入账的全部记录，按时间升序（查重用）。 */
    List<Tx> done() {
        return list("status=1", null, "ts ASC", null);
    }

    /**
     * 手动记账的防抖：同一天、同收支、同金额、同商户，且时间窗口内的已有记录。
     * 双击保存 / 回头又记一遍同一笔时用它挡住。
     */
    Tx sameSoon(Tx t, long windowMs) {
        long dayFrom = Span.dayStartOf(t.ts);
        long from = Math.max(dayFrom, t.ts - windowMs);
        long to = Math.min(Span.addDays(dayFrom, 1), t.ts + windowMs);
        List<Tx> l = list("status=1 AND kind=? AND amount=? AND ts>=? AND ts<? AND merchant=?",
                new String[]{String.valueOf(t.kind), String.valueOf(t.amount),
                        String.valueOf(from), String.valueOf(to),
                        t.merchant == null ? "" : t.merchant},
                "ts ASC", "1");
        return l.isEmpty() ? null : l.get(0);
    }

    /** 同商户所有待确认（批量处理用）。 */
    List<Tx> pendingOf(String merchant) {
        return list("status=0 AND merchant=?", new String[]{merchant}, "ts DESC", null);
    }

    // ----------------------------------------------------------------- read

    private Tx read(Cursor c) {
        Tx t = new Tx();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
        t.kind = c.getInt(c.getColumnIndexOrThrow("kind"));
        t.amount = c.getLong(c.getColumnIndexOrThrow("amount"));
        t.merchant = nz(c.getString(c.getColumnIndexOrThrow("merchant")));
        t.category = nz(c.getString(c.getColumnIndexOrThrow("category")));
        t.account = nz(c.getString(c.getColumnIndexOrThrow("account")));
        t.note = nz(c.getString(c.getColumnIndexOrThrow("note")));
        t.source = nz(c.getString(c.getColumnIndexOrThrow("source")));
        t.srcPkg = nz(c.getString(c.getColumnIndexOrThrow("src_pkg")));
        t.txid = nz(c.getString(c.getColumnIndexOrThrow("txid")));
        t.raw = nz(c.getString(c.getColumnIndexOrThrow("raw")));
        t.status = c.getInt(c.getColumnIndexOrThrow("status"));
        t.dup = c.getInt(c.getColumnIndexOrThrow("dup"));
        return t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private List<Tx> list(String where, String[] args, String order, String limit) {
        List<Tx> out = new ArrayList<>();
        String sql = "SELECT * FROM tx" + (where == null ? "" : " WHERE " + where)
                + (order == null ? " ORDER BY ts DESC" : " ORDER BY " + order)
                + (limit == null ? "" : " LIMIT " + limit);
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    List<Tx> bills(long from, long to) {
        return list("status=1 AND ts>=? AND ts<?",
                new String[]{String.valueOf(from), String.valueOf(to)}, null, null);
    }

    List<Tx> pending() {
        return list("status=0", null, "ts DESC", null);
    }

    int pendingCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tx WHERE status=0", null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    Tx byId(long id) {
        List<Tx> l = list("id=?", new String[]{String.valueOf(id)}, null, "1");
        return l.isEmpty() ? null : l.get(0);
    }

    List<Tx> all() {
        return list(null, null, "ts ASC", null);
    }

    long sumKind(int kind, long from, long to) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT IFNULL(SUM(amount),0) FROM tx WHERE status=1 AND kind=? AND ts>=? AND ts<?",
                new String[]{String.valueOf(kind), String.valueOf(from), String.valueOf(to)});
        long v = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return v;
    }

    long todayExpense() {
        return sumKind(Tx.OUT, Span.dayStart(0), Span.dayStart(1));
    }

    /** 分类汇总，按金额降序：[分类, 金额分]。 */
    List<String[]> catStats(int kind, long from, long to) {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT category, IFNULL(SUM(amount),0) s FROM tx WHERE status=1 AND kind=? " +
                        "AND ts>=? AND ts<? GROUP BY category ORDER BY s DESC",
                new String[]{String.valueOf(kind), String.valueOf(from), String.valueOf(to)});
        while (c.moveToNext()) out.add(new String[]{nz(c.getString(0)), String.valueOf(c.getLong(1))});
        c.close();
        return out;
    }

    /** 最近 7 天（含今天）每日支出，下标 0 = 6 天前。 */
    long[] last7Days() {
        long[] v = new long[7];
        for (int i = 0; i < 7; i++) {
            v[i] = sumKind(Tx.OUT, Span.dayStart(i - 6), Span.dayStart(i - 5));
        }
        return v;
    }

    /** 任意区间内逐日支出，下标 0 = from 当天。 */
    long[] dailyOut(long from, int days) {
        long[] v = new long[days];
        for (int i = 0; i < days; i++) {
            long d = Span.addDays(from, i);
            v[i] = sumKind(Tx.OUT, d, Span.addDays(d, 1));
        }
        return v;
    }

    /** 搜索：关键词（商户/备注/分类/原文）+ 分类 + 金额区间，跨全部时间。 */
    List<Tx> search(String kw, String cat, long minCents, long maxCents) {
        StringBuilder w = new StringBuilder("status=1");
        List<String> args = new ArrayList<>();

        String k = kw == null ? "" : kw.trim();
        if (!k.isEmpty()) {
            w.append(" AND (merchant LIKE ? OR note LIKE ? OR category LIKE ? OR raw LIKE ?)");
            String like = "%" + k + "%";
            for (int i = 0; i < 4; i++) args.add(like);
        }
        if (cat != null && !cat.isEmpty()) {
            w.append(" AND category=?");
            args.add(cat);
        }
        if (minCents > 0) {
            w.append(" AND amount>=?");
            args.add(String.valueOf(minCents));
        }
        if (maxCents > 0) {
            w.append(" AND amount<=?");
            args.add(String.valueOf(maxCents));
        }
        return list(w.toString(), args.toArray(new String[0]), null, null);
    }

    /** 商户排行：[商户, 金额分]，按金额降序。 */
    List<String[]> merchantStats(int kind, long from, long to, int limit) {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT merchant, IFNULL(SUM(amount),0) s FROM tx WHERE status=1 AND kind=? " +
                        "AND ts>=? AND ts<? AND merchant IS NOT NULL AND merchant<>'' " +
                        "GROUP BY merchant ORDER BY s DESC LIMIT " + Math.max(1, limit),
                new String[]{String.valueOf(kind), String.valueOf(from), String.valueOf(to)});
        while (c.moveToNext()) out.add(new String[]{nz(c.getString(0)), String.valueOf(c.getLong(1))});
        c.close();
        return out;
    }

    // ------------------------------------------------------------ 固定支出

    List<Recur> recurAll() {
        List<Recur> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,amount,category,day,last,onoff FROM recur ORDER BY day", null);
        while (c.moveToNext()) {
            Recur r = new Recur();
            r.id = c.getLong(0);
            r.title = nz(c.getString(1));
            r.amount = c.getLong(2);
            r.category = nz(c.getString(3));
            r.day = c.getInt(4);
            r.last = nz(c.getString(5));
            r.enabled = c.getInt(6) != 0;
            out.add(r);
        }
        c.close();
        return out;
    }

    long recurSave(Recur r) {
        ContentValues v = new ContentValues();
        v.put("title", r.title);
        v.put("amount", r.amount);
        v.put("category", r.category);
        v.put("day", r.day);
        v.put("last", r.last);
        v.put("onoff", r.enabled ? 1 : 0);
        if (r.id > 0) {
            getWritableDatabase().update("recur", v, "id=?", new String[]{String.valueOf(r.id)});
            return r.id;
        }
        return getWritableDatabase().insert("recur", null, v);
    }

    void recurDel(long id) {
        getWritableDatabase().delete("recur", "id=?", new String[]{String.valueOf(id)});
    }

    void recurPosted(long id, String monthKey) {
        ContentValues v = new ContentValues();
        v.put("last", monthKey);
        getWritableDatabase().update("recur", v, "id=?", new String[]{String.valueOf(id)});
    }
}
