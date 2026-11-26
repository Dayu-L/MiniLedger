package com.miniledger.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

/** 重复项识别自测：纯 JVM。 */
public class DupeTest {

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        long day = at(2026, 11, 26, 0, 0);

        // ---- 同一天、同金额、4 分钟 -> 一组
        List<Tx> a = new ArrayList<>();
        a.add(tx(day + 9 * 3600000L + 15 * 60000L, Tx.OUT, 1430, "未知商户", "微信"));
        a.add(tx(day + 9 * 3600000L + 19 * 60000L, Tx.OUT, 1430, "未知商户", "支付宝"));
        eq("4 分钟内同额", 1, Dupe.find(a).size());
        eq("组内 2 笔", 2, Dupe.find(a).get(0).items.size());
        eq("保留最早", 9 * 60 + 15, minuteOf(Dupe.find(a).get(0).items.get(0)));
        eq("待删 1 笔", 1, Dupe.find(a).get(0).later().size());

        // ---- 同一天、同金额、隔 6 小时、商户不同 -> 不算
        List<Tx> b = new ArrayList<>();
        b.add(tx(day + 8 * 3600000L, Tx.OUT, 1500, "食堂", "微信"));
        b.add(tx(day + 19 * 3600000L, Tx.OUT, 1500, "瑞幸咖啡", "支付宝"));
        eq("隔太久不同商户", 0, Dupe.find(b).size());

        // ---- 同一天、同金额、隔 6 小时、同商户 -> 仍算（一天在同一家花两次同样的钱太巧）
        List<Tx> c = new ArrayList<>();
        c.add(tx(day + 8 * 3600000L, Tx.OUT, 1500, "食堂", "微信"));
        c.add(tx(day + 19 * 3600000L, Tx.OUT, 1500, "食堂", "支付宝"));
        eq("同商户跨时段", 1, Dupe.find(c).size());

        // ---- 不同天 / 不同金额 / 不同收支 -> 都不算
        List<Tx> d = new ArrayList<>();
        d.add(tx(day + 12 * 3600000L, Tx.OUT, 2000, "食堂", "微信"));
        d.add(tx(day + 86400000L + 12 * 3600000L, Tx.OUT, 2000, "食堂", "微信"));
        eq("跨天不算", 0, Dupe.find(d).size());

        List<Tx> e = new ArrayList<>();
        e.add(tx(day + 12 * 3600000L, Tx.OUT, 2000, "食堂", "微信"));
        e.add(tx(day + 12 * 3600000L + 60000L, Tx.OUT, 2100, "食堂", "微信"));
        eq("金额不同不算", 0, Dupe.find(e).size());

        List<Tx> f = new ArrayList<>();
        f.add(tx(day + 12 * 3600000L, Tx.OUT, 2000, "食堂", "微信"));
        f.add(tx(day + 12 * 3600000L + 60000L, Tx.IN, 2000, "食堂", "微信"));
        eq("收支方向不同不算", 0, Dupe.find(f).size());

        // ---- 三笔：两笔挨着 + 一笔同商户 -> 一组三笔（按时间升序）
        List<Tx> g = new ArrayList<>();
        g.add(tx(day + 9 * 3600000L, Tx.OUT, 3000, "便利店", "微信"));
        g.add(tx(day + 9 * 3600000L + 120000L, Tx.OUT, 3000, "便利店", "支付宝"));
        g.add(tx(day + 21 * 3600000L, Tx.OUT, 3000, "便利店", "现金"));
        List<Dupe.Group> gs = Dupe.find(g);
        eq("三笔成一组", 1, gs.size());
        eq("组内 3 笔", 3, gs.get(0).items.size());
        eq("按时间升序", 9, (int) ((gs.get(0).items.get(0).ts - day) / 3600000L));

        // ---- 两组互不干扰
        List<Tx> h = new ArrayList<>(a);
        h.add(tx(day + 18 * 3600000L, Tx.OUT, 2500, "滴滴出行", "支付宝"));
        h.add(tx(day + 18 * 3600000L + 180000L, Tx.OUT, 2500, "滴滴出行", "微信"));
        eq("两组", 2, Dupe.find(h).size());

        // ---- 待确认（status=0）不参与
        List<Tx> i = new ArrayList<>();
        Tx p1 = tx(day + 9 * 3600000L, Tx.OUT, 1430, "未知商户", "微信");
        Tx p2 = tx(day + 9 * 3600000L + 120000L, Tx.OUT, 1430, "未知商户", "微信");
        p1.status = Tx.PENDING;
        p2.status = Tx.PENDING;
        i.add(p1);
        i.add(p2);
        eq("待确认不参与", 0, Dupe.find(i).size());

        // ---- 金额 0 / 空表
        List<Tx> j = new ArrayList<>();
        j.add(tx(day + 9 * 3600000L, Tx.OUT, 0, "x", "微信"));
        j.add(tx(day + 9 * 3600000L + 1000L, Tx.OUT, 0, "x", "微信"));
        eq("0 元不算", 0, Dupe.find(j).size());
        eq("空表", 0, Dupe.find(new ArrayList<Tx>()).size());

        // ---- 「都保留」后过滤掉
        List<Dupe.Group> found = Dupe.find(a);
        HashSet<String> ignored = new HashSet<>();
        ignored.add(found.get(0).signature());
        eq("忽略后不再提示", 0, Dupe.filter(found, ignored).size());
        eq("签名稳定", found.get(0).signature(), Dupe.find(a).get(0).signature());

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static Tx tx(long ts, int kind, long amount, String merchant, String account) {
        Tx t = new Tx();
        t.ts = ts;
        t.kind = kind;
        t.amount = amount;
        t.merchant = merchant;
        t.account = account;
        t.category = kind == Tx.IN ? "其他收入" : "其他";
        t.status = Tx.DONE;
        t.id = ts % 1000000;
        return t;
    }

    static long at(int y, int m, int d, int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d, hh, mm, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static int minuteOf(Tx t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t.ts);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static void eq(String name, Object expect, Object got) {
        boolean same = expect == null ? got == null : expect.equals(got);
        if (same) {
            pass++;
            System.out.println("ok    " + name + " -> " + got);
        } else {
            fail++;
            System.out.println("FAIL  " + name + " -> " + got + "（期望 " + expect + "）");
        }
    }
}
