package com.miniledger.app;

import java.util.Calendar;
import java.util.TimeZone;

/** 时间戳 / 账期（精确到日）自测：纯 JVM，不需要 Android。 */
public class SpanTest {

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        // ---- 天首：账本上所有边界都落在 00:00
        long today = Span.dayStart(0);
        eq("天首归零", today, Span.dayStartOf(today + 13 * 3600000L + 45 * 60000L));
        eq("加 5 天", 5, Span.dayDiff(today, Span.addDays(today, 5)));
        eq("昨天差一天", 1, Span.dayDiff(Span.dayStart(-1), today));

        // ---- 显示精确到日
        eq("今天标签", "今天", Span.dayLabel(today));
        eq("昨天标签", "昨天", Span.dayLabel(Span.dayStart(-1)));
        eq("时分", "14:32", Span.hm(today + 14 * 3600000L + 32 * 60000L));
        String stamp = Span.stamp(today + 14 * 3600000L + 32 * 60000L);
        truth("时间戳带日期", stamp.startsWith("今天 ") && stamp.endsWith(":32"), stamp);
        truth("老名字一致", stamp.equals(Span.friendly(today + 14 * 3600000L + 32 * 60000L)),
                Span.friendly(today));

        // ---- 固定 30 天账期：从 2025-11-24 领生活费起算
        long base = day(2025, 11, 24);
        Span.Cycle c0 = Span.cycle(base, 30, 0);
        Span.Cycle c1 = Span.cycle(base, 30, 1);
        Span.Cycle c2 = Span.cycle(base, 30, 2);
        eq("30 天 起点", base, c0.start);
        eq("30 天 终点", day(2025, 12, 24), c0.end);
        eq("30 天 天数", 30, c0.days());
        eq("第 2 期起点", day(2025, 12, 24), c1.start);
        eq("第 3 期起点", day(2026, 1, 23), c2.start);
        eq("区间标签", "11月24日 – 12月23日", Span.rangeLabel(c0.start, c0.end));
        eq("落在周期里", true, c0.holds(base));
        eq("终点不属于本期", false, c0.holds(c0.end));

        eq("起点是第 0 期", 0, Span.cycleIndex(base, 30, base));
        eq("最后一刻还在第 0 期", 0, Span.cycleIndex(base, 30, c0.end - 60000L));
        eq("到日子进位", 1, Span.cycleIndex(base, 30, c0.end));
        eq("三个月后", 3, Span.cycleIndex(base, 30, day(2026, 2, 22)));

        // ---- 自然月账期：11月24日 → 12月24日
        Span.Cycle m0 = Span.cycle(base, 0, 0);
        Span.Cycle m1 = Span.cycle(base, 0, 1);
        eq("自然月 起点", base, m0.start);
        eq("自然月 终点", day(2025, 12, 24), m0.end);
        eq("自然月 天数", 30, m0.days());
        eq("自然月 第 2 期", day(2025, 12, 24), m1.start);
        eq("自然月 第 2 期终点", day(2026, 1, 24), m1.end);
        eq("自然月 进位", 1, Span.cycleIndex(base, 0, day(2025, 12, 24)));

        // ---- 月底起算：1月31日 → 2月28日 → 3月31日，不漂移
        long jan31 = day(2026, 1, 31);
        Span.Cycle e1 = Span.cycle(jan31, 0, 1);
        Span.Cycle e2 = Span.cycle(jan31, 0, 2);
        eq("月底 第 2 期起点", day(2026, 2, 28), e1.start);
        eq("月底 第 2 期终点", day(2026, 3, 31), e1.end);
        eq("月底 第 3 期起点", day(2026, 3, 31), e2.start);

        // ---- 跨年标签
        eq("跨年标签", "2025年12月24日 – 2026年1月23日",
                Span.rangeLabel(day(2025, 12, 24), day(2026, 1, 24)));

        // ---- 没设起始日
        eq("没设账期", null, Span.cycle(0, 30, 0));
        eq("没设账期索引", 0, Span.cycleIndex(0, 30, today));

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static long day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d, 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return Span.dayStartOf(c.getTimeInMillis());
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

    static void truth(String name, boolean ok, Object detail) {
        if (ok) {
            pass++;
            System.out.println("ok    " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + " -> " + detail);
        }
    }
}
