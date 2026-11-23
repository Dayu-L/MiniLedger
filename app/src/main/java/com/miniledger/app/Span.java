package com.miniledger.app;

import java.util.Calendar;

/** 时间区间工具。 */
final class Span {
    private Span() {}

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

    static String monthLabel(int monthOffset) {
        Calendar c = cal();
        c.setTimeInMillis(monthStart(monthOffset));
        return c.get(Calendar.YEAR) + "年" + (c.get(Calendar.MONTH) + 1) + "月";
    }

    /** 2026-11，用作提醒去重键。 */
    static String monthKey() {
        Calendar c = cal();
        c.setTimeInMillis(monthStart(0));
        int m = c.get(Calendar.MONTH) + 1;
        return c.get(Calendar.YEAR) + "-" + (m < 10 ? "0" + m : String.valueOf(m));
    }

    private static final java.text.SimpleDateFormat HM =
            new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
    private static final java.text.SimpleDateFormat MD =
            new java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA);

    static synchronized String hm(long ts) {
        return HM.format(new java.util.Date(ts));
    }

    static synchronized String md(long ts) {
        return MD.format(new java.util.Date(ts));
    }

    /** 今天显示时间，其他显示日期。 */
    static String friendly(long ts) {
        Calendar c = cal();
        int today = c.get(Calendar.DAY_OF_YEAR);
        int year = c.get(Calendar.YEAR);
        boolean isToday = false;
        Calendar d = cal();
        d.setTimeInMillis(ts);
        isToday = d.get(Calendar.YEAR) == year && d.get(Calendar.DAY_OF_YEAR) == today;
        if (isToday) return hm(ts);
        return md(ts) + " " + hm(ts);
    }
}
