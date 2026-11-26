package com.miniledger.app;

import java.util.Calendar;
import java.util.TimeZone;

/** 固定支出的到期判断自测：纯 JVM（android.jar 只在编译期用到）。 */
public class RecurTest {

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        long nov13 = at(2026, 11, 13, 15, 30);

        // ---- 本月入账时刻：当月该日 12:00
        eq("1 日的入账时刻", at(2026, 11, 1, 12, 0), Recur.postTime(1, nov13));
        eq("15 日（还没到）", at(2026, 11, 15, 12, 0), Recur.postTime(15, nov13));
        // 2026 年 2 月只有 28 天，31 号落到月末
        eq("31 号遇到 2 月", at(2026, 2, 28, 12, 0), Recur.postTime(31, at(2026, 2, 10, 9, 0)));
        eq("31 号遇到 4 月", at(2026, 4, 30, 12, 0), Recur.postTime(31, at(2026, 4, 1, 9, 0)));

        // ---- 到期判断
        Recur r = new Recur();
        r.amount = 200000;      // 2000 元
        r.day = 1;
        r.last = "2026-10";

        eq("本月没落过 + 已到日子 -> 到期", true, r.due(nov13));
        r.last = "2026-11";
        eq("本月已落过 -> 不再落", false, r.due(nov13));

        r.last = "2026-10";
        r.day = 20;
        eq("日子还没到 -> 不到期", false, r.due(nov13));
        r.day = 13;
        eq("当天 12 点后 -> 到期", true, r.due(at(2026, 11, 13, 12, 1)));

        r.day = 1;
        r.enabled = false;
        eq("关掉了 -> 不到期", false, r.due(nov13));
        r.enabled = true;
        r.amount = 0;
        eq("金额 0 -> 不到期", false, r.due(nov13));

        // ---- 月份键（去重用）
        eq("月末 23:59 还是本月", "2026-11", Span.monthKeyOf(at(2026, 11, 30, 23, 59)));

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d, h, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static void eq(String name, Object expect, Object got) {
        boolean same = expect == null ? got == null : expect.equals(got);
        if (same) {
            pass++;
            System.out.println("ok    " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + " -> " + got + "（期望 " + expect + "）");
        }
    }
}
