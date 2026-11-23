package com.miniledger.app;

import java.text.DecimalFormat;

final class Money {
    private static final DecimalFormat F = new DecimalFormat("#,##0.00");

    static synchronized String fmt(long cents) {
        return F.format(cents / 100.0d);
    }

    static String yuan(long cents) {
        return "¥" + fmt(cents);
    }

    /** "1,024.50" / "¥25" / "25元" -> 分；非法返回 -1。 */
    static long parse(String s) {
        if (s == null) return -1;
        String t = s.replace("¥", "").replace("￥", "").replace(",", "")
                .replace("元", "").replace(" ", "").trim();
        if (t.isEmpty()) return -1;
        try {
            double d = Double.parseDouble(t);
            if (d < 0 || d > 100000000d) return -1;
            return Math.round(d * 100d);
        } catch (Exception e) {
            return -1;
        }
    }
}
