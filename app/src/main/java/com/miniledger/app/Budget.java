package com.miniledger.app;

import android.content.Context;

import java.util.Calendar;

/**
 * 预算：账期开着就按账期算，否则按自然月。
 *
 * 每次入账后 check()：
 *   80% / 100% 各提醒一次（按窗口去重）
 *   18 点后当天花超「日均额度」，轻提醒一次（按天去重）
 */
final class Budget {

    private Budget() {}

    /** 当前预算窗口：[from, to)。 */
    static final class Window {
        long from, to;
        int days;
        boolean cycle;
        String label = "";
    }

    static Window window(Context c) {
        Window w = new Window();
        long base = Prefs.cycleStart(c);
        if (base > 0) {
            int len = Prefs.cycleDays(c);
            long now = System.currentTimeMillis();
            Span.Cycle cyc = Span.cycle(base, len, Span.cycleIndex(base, len, now));
            if (cyc != null) {
                w.from = cyc.start;
                w.to = cyc.end;
                w.days = cyc.days();
                w.cycle = true;
                w.label = Span.rangeLabel(cyc.start, cyc.end);
                return w;
            }
        }
        w.from = Span.monthStart(0);
        w.to = Span.monthEnd(0);
        w.days = Math.max(1, Span.dayDiff(w.from, w.to));
        w.label = Span.monthLabel(0);
        return w;
    }

    static long monthly(Context c) {
        return Prefs.budget(c);
    }

    /** 当前窗口已花 / 预算，返回 {spent, budget}。 */
    static long[] state(Context c) {
        Window w = window(c);
        return new long[]{Db.get(c).sumKind(Tx.OUT, w.from, w.to), Prefs.budget(c)};
    }

    static void check(Context c) {
        long b = Prefs.budget(c);
        if (b <= 0) return;

        Window w = window(c);
        long spent = Db.get(c).sumKind(Tx.OUT, w.from, w.to);
        String key = (w.cycle ? "c" + w.from : Span.monthKey()) + "-b";

        if (spent >= b) {
            if (!Prefs.alerted(c, "100-" + key)) {
                Prefs.setAlerted(c, "100-" + key);
                Notifier.budgetAlert(c, w, spent, b, true);
            }
        } else if (spent * 10 >= b * 8) {
            if (!Prefs.alerted(c, "80-" + key)) {
                Prefs.setAlerted(c, "80-" + key);
                Notifier.budgetAlert(c, w, spent, b, false);
            }
        }

        // 日均节奏：今天有没有花超
        long perDay = Math.max(1, b / Math.max(1, w.days));
        long today = Db.get(c).todayExpense();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String dayKey = "pace-" + Span.dayStartOf(System.currentTimeMillis());
        if (today > perDay && hour >= 18 && !Prefs.alerted(c, dayKey)) {
            Prefs.setAlerted(c, dayKey);
            Notifier.paceAlert(c, today, perDay);
        }
    }
}
