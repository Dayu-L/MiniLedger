package com.miniledger.app;

import android.content.Context;

/** 月度预算与阈值提醒。 */
final class Budget {

    private Budget() {}

    static long monthly(Context c) {
        return Prefs.budget(c);
    }

    /** 本月已花 / 预算，返回 {spent, budget}。 */
    static long[] state(Context c) {
        long b = Prefs.budget(c);
        long spent = Db.get(c).sumKind(Tx.OUT, Span.monthStart(0), Span.monthEnd(0));
        return new long[]{spent, b};
    }

    /** 每次入账后调用；80% 和 100% 各提醒一次，按月去重。 */
    static void check(Context c) {
        long b = Prefs.budget(c);
        if (b <= 0) return;
        long spent = Db.get(c).sumKind(Tx.OUT, Span.monthStart(0), Span.monthEnd(0));
        String key = Span.monthKey();

        if (spent >= b) {
            if (!Prefs.alerted(c, "100-" + key)) {
                Prefs.setAlerted(c, "100-" + key);
                Notifier.budgetAlert(c, spent, b, true);
            }
        } else if (spent * 10 >= b * 8) {
            if (!Prefs.alerted(c, "80-" + key)) {
                Prefs.setAlerted(c, "80-" + key);
                Notifier.budgetAlert(c, spent, b, false);
            }
        }
    }
}
