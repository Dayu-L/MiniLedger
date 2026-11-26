package com.miniledger.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 桌面小组件：本周期（没开账期就是本月）还剩多少、今天还能花多少。
 * 点一下进「周期」页。
 */
public class WidgetProvider extends AppWidgetProvider {

    /** 数据变了就刷一下（调它也安全：没放小组件就直接返回）。 */
    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            for (int id : ids) m.updateAppWidget(id, build(c, m, id));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, m, id));
    }

    private static RemoteViews build(Context c, AppWidgetManager m, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_mini);

        Budget.Window w = Budget.window(c);
        long budget = Prefs.budget(c);
        long spent = Db.get(c).sumKind(Tx.OUT, w.from, w.to);
        long today = Db.get(c).todayExpense();
        int leftDays = remainingDays(w);
        long perDay = budget > 0
                ? Math.max(0, budget - spent) / Math.max(1, leftDays)
                : 0;

        int textColor = Skin.dark ? Ui.INK : Ui.INK;
        rv.setInt(R.id.widget_root, "setBackgroundResource",
                Skin.dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg);
        rv.setTextColor(R.id.widget_title, Ui.SUB);
        rv.setTextColor(R.id.widget_big, budget > 0 && spent > budget ? Ui.WARN : textColor);
        rv.setTextColor(R.id.widget_sub, Ui.FAINT);

        String title;
        String big;
        if (budget > 0) {
            long left = budget - spent;
            title = w.cycle ? "本周期还剩" : "本月还剩";
            big = left < 0 ? "超支 " + Money.yuan(-left) : Money.yuan(left);
        } else {
            title = w.cycle ? "本周期已花" : "本月已花";
            big = Money.yuan(spent);
        }
        String sub = "今天 " + Money.yuan(today)
                + (budget > 0 ? " · 还能花 " + Money.yuan(perDay) : " · 未设预算");

        rv.setTextViewText(R.id.widget_title, title);
        rv.setTextViewText(R.id.widget_big, big);
        rv.setTextViewText(R.id.widget_sub, sub);

        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("tab", w.cycle ? 1 : 0);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        return rv;
    }

    private static int remainingDays(Budget.Window w) {
        int passed = Math.max(0, Math.min(w.days, Span.dayDiff(w.from, System.currentTimeMillis())));
        return Math.max(1, w.days - passed);
    }
}
