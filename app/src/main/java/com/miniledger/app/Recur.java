package com.miniledger.app;

import java.util.Calendar;

/**
 * 固定支出：房租、话费、订阅这类每月一次的。
 *
 * 到日子不直接扣钱，先落一笔进「待确认」，你点头了才算进账本。
 * due() / postTime() 是纯 java.util，可单测（test/RecurTest.java）。
 */
class Recur {

    long id;
    String title = "";
    long amount;                       // 分
    String category = Categories.def(Tx.OUT);
    int day = 1;                       // 1-31，当月不够长就落到月末
    String last = "";                  // 上次入账的月份键 2026-11
    boolean enabled = true;

    /**
     * 本月的入账时刻：本月 day 日 12:00；day 超过当月天数时取月末那天。
     */
    static long postTime(int day, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int d = Math.max(1, Math.min(day, max));
        c.set(Calendar.DAY_OF_MONTH, d);
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 本月该落账了？已经落过（last = 本月键）就不再来一遍。 */
    boolean due(long now) {
        return enabled && amount > 0
                && !Span.monthKeyOf(now).equals(last)
                && now >= postTime(day, now);
    }

    /** 把所有到期的固定支出落进「待确认」，返回新增笔数。 */
    static int postDue(android.content.Context c) {
        long now = System.currentTimeMillis();
        Db db = Db.get(c);
        int n = 0;
        for (Recur r : db.recurAll()) {
            if (!r.due(now)) continue;

            Tx t = new Tx();
            t.ts = postTime(r.day, now);
            t.kind = Tx.OUT;
            t.amount = r.amount;
            t.merchant = r.title;
            t.category = r.category == null || r.category.isEmpty()
                    ? Categories.def(Tx.OUT) : r.category;
            t.account = "固定支出";
            t.note = "每月 " + r.day + " 日";
            t.source = "recur";
            t.raw = "固定支出：" + r.title;

            if (db.insert(t, false) > 0) {
                db.recurPosted(r.id, Span.monthKeyOf(now));
                n++;
            }
        }
        return n;
    }
}
