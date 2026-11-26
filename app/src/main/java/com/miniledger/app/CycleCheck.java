package com.miniledger.app;

import android.content.Context;

import java.util.Calendar;

/** 周期收尾：最后一天 20:00 之后推一条结算通知，一个周期只响一次。 */
final class CycleCheck {

    private CycleCheck() {}

    static void run(Context c) {
        long base = Prefs.cycleStart(c);
        if (base <= 0) return;

        int len = Prefs.cycleDays(c);
        long now = System.currentTimeMillis();
        Span.Cycle cyc = Span.cycle(base, len, Span.cycleIndex(base, len, now));
        if (cyc == null) return;

        boolean lastDay = now >= Span.addDays(cyc.end, -1);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (!lastDay || hour < 20) return;

        String key = "cycle-" + cyc.start;
        if (Prefs.alerted(c, key)) return;
        Prefs.setAlerted(c, key);

        long out = Db.get(c).sumKind(Tx.OUT, cyc.start, cyc.end);
        long in = Db.get(c).sumKind(Tx.IN, cyc.start, cyc.end);
        Notifier.cycleAlert(c, cyc, out, in, Prefs.budget(c));
    }
}
