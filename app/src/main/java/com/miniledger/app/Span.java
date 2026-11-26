package com.miniledger.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间工具。
 *
 * 账本按「天」记账：内部还是毫秒时间戳，但所有边界（月、日、账期）都落在当地 00:00，
 * 显示精确到日（今天 / 昨天 / 11月24日 周日），时间只作辅助。
 */
final class Span {
    private Span() {}

    static final long DAY = 86400000L;

    static Calendar cal() {
        return Calendar.getInstance();
    }

    /** monthOffset=0 本月，-1 上月。返回月首 00:00。 */
    static long monthStart(int monthOffset) {
        Calendar c = cal();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (monthOffset != 0) c.add(Calendar.MONTH, monthOffset);
        return c.getTimeInMillis();
    }

    static long monthEnd(int monthOffset) {
        Calendar c = cal();
        c.setTimeInMillis(monthStart(monthOffset));
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    static long dayStart(int dayOffset) {
        Calendar c = cal();
        c.add(Calendar.DAY_OF_MONTH, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 任意时刻落到当天 00:00。 */
    static long dayStartOf(long ts) {
        Calendar c = cal();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 在某个天首上加减天数，结果还是天首。 */
    static long addDays(long dayStart, int days) {
        Calendar c = cal();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    /**
     * 在某个天首上加减月份，结果还是天首。
     * 1 月 31 日 + 1 个月 = 2 月 28/29 日；+ 2 个月仍从 1 月 31 日算，回到 3 月 31 日。
     */
    static long addMonths(long dayStart, int months) {
        Calendar c = cal();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    /** a → b 相差几天（按天首算，跨夏令时也不会差一天）。 */
    static int dayDiff(long a, long b) {
        return (int) Math.round((dayStartOf(b) - dayStartOf(a)) / (double) DAY);
    }

    // ------------------------------------------------------------ 账期 / 生活周期

    /** 一个账期：[start, end)，两头都是天首。 */
    static final class Cycle {
        final int index;      // 第几个周期，0 = 第一个
        final long start;
        final long end;       // 不含

        Cycle(int index, long start, long end) {
            this.index = index;
            this.start = start;
            this.end = end;
        }

        int days() {
            return Math.max(1, dayDiff(start, end));
        }

        boolean holds(long ts) {
            return ts >= start && ts < end;
        }
    }

    /**
     * 第 index 个账期。
     *
     * @param baseDayStart 账期起始日（天首），0 表示没设
     * @param lenDays      0 = 按自然月对齐起始日；&gt;0 = 固定 N 天
     */
    static Cycle cycle(long baseDayStart, int lenDays, int index) {
        if (baseDayStart <= 0) return null;
        if (lenDays > 0) {
            long s = addDays(baseDayStart, lenDays * index);
            return new Cycle(index, s, addDays(s, lenDays));
        }
        return new Cycle(index,
                addMonths(baseDayStart, index),
                addMonths(baseDayStart, index + 1));
    }

    /** now 落在第几个账期里；起始日在未来（或没设）时返回 0。 */
    static int cycleIndex(long baseDayStart, int lenDays, long now) {
        if (baseDayStart <= 0 || now < baseDayStart) return 0;
        int i = 0;
        while (i < 100000) {
            Cycle next = cycle(baseDayStart, lenDays, i + 1);
            if (next == null || next.start > now) return i;
            i++;
        }
        return i;
    }

    // ---------------------------------------------------------------- 格式化

    static String monthLabel(int monthOffset) {
        Calendar c = cal();
        c.setTimeInMillis(monthStart(monthOffset));
        return c.get(Calendar.YEAR) + "年" + (c.get(Calendar.MONTH) + 1) + "月";
    }

    /** 2026-11，用作提醒去重键。 */
    static String monthKey() {
        return monthKeyOf(System.currentTimeMillis());
    }

    /** 任意时刻所在月份的键，2026-11。 */
    static String monthKeyOf(long ts) {
        Calendar c = cal();
        c.setTimeInMillis(ts);
        int m = c.get(Calendar.MONTH) + 1;
        return c.get(Calendar.YEAR) + "-" + (m < 10 ? "0" + m : String.valueOf(m));
    }

    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private static final SimpleDateFormat HM =
            new SimpleDateFormat("HH:mm", Locale.CHINA);
    private static final SimpleDateFormat MD =
            new SimpleDateFormat("M月d日", Locale.CHINA);
    private static final SimpleDateFormat YMD =
            new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);

    static synchronized String hm(long ts) {
        return HM.format(new Date(ts));
    }

    /** 11月24日 */
    static synchronized String md(long ts) {
        return MD.format(new Date(ts));
    }

    /** 2025年11月24日 */
    static synchronized String ymd(long ts) {
        return YMD.format(new Date(ts));
    }

    /** 周一 */
    static String weekday(long ts) {
        Calendar c = cal();
        c.setTimeInMillis(ts);
        return WEEK[(c.get(Calendar.DAY_OF_WEEK) - 1) % 7];
    }

    /** 今天 / 昨天 / 11月23日 / 2025年11月23日 —— 精确到日。 */
    static String dayLabel(long ts) {
        long d = dayStartOf(ts);
        long today = dayStart(0);
        if (d == today) return "今天";
        if (d == addDays(today, -1)) return "昨天";
        Calendar a = cal();
        a.setTimeInMillis(d);
        Calendar b = cal();
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) ? md(d) : ymd(d);
    }

    /** 今天 14:32 / 昨天 09:05 / 11月23日 20:11 —— 账目行的完整时间戳。 */
    static String stamp(long ts) {
        return dayLabel(ts) + " " + hm(ts);
    }

    /** 旧名字，账目行用的就是它。 */
    static String friendly(long ts) {
        return stamp(ts);
    }

    /** 11月24日 – 12月23日；跨年时两边都带年份。 */
    static String rangeLabel(long start, long endExclusive) {
        long last = addDays(endExclusive, -1);
        if (last < start) last = start;
        Calendar a = cal();
        a.setTimeInMillis(start);
        Calendar b = cal();
        b.setTimeInMillis(last);
        if (a.get(Calendar.YEAR) != b.get(Calendar.YEAR)) return ymd(start) + " – " + ymd(last);
        if (a.get(Calendar.MONTH) == b.get(Calendar.MONTH)) {
            return a.get(Calendar.DAY_OF_MONTH) + " – " + md(last);
        }
        return md(start) + " – " + md(last);
    }
}
