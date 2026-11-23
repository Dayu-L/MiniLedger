package com.miniledger.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

class Notifier {
    static final String CH_GUARD = "guard";
    static final String CH_CAPTURE = "capture";
    static final String CH_ALERT = "alert";

    static final int ID_GUARD = 1001;
    static final int ID_ALERT = 1002;
    static final int ID_CAPTURE = 1003;
    static final int ID_BUDGET = 1004;

    static void setup(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel g = new NotificationChannel(
                    CH_GUARD, "自动记账服务", NotificationManager.IMPORTANCE_LOW);
            g.setShowBadge(false);
            g.setDescription("常驻通知，用于保持自动记账在线");
            nm.createNotificationChannel(g);

            NotificationChannel cap = new NotificationChannel(
                    CH_CAPTURE, "记账提醒", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(cap);

            NotificationChannel al = new NotificationChannel(
                    CH_ALERT, "服务中断提醒", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(al);
        }
    }

    private static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification guard(Context c) {
        long today = Db.get(c).todayExpense();
        String text = today > 0
                ? "今日支出 " + Money.yuan(today) + " · 正在监听微信/支付宝"
                : "正在监听微信 / 支付宝的付款通知";

        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CH_GUARD);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("简账 · 自动记账中")
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open(c));
        return b.build();
    }

    static void listenerLost(Context c) {
        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CH_ALERT);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("自动记账已断开")
                .setContentText("通知使用权被关闭了，点此重新开启")
                .setAutoCancel(true)
                .setContentIntent(open(c));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_ALERT, b.build());
    }

    static void captured(Context c, Tx t) {        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CH_CAPTURE);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_DEFAULT);
        }
        String amt = (t.kind == Tx.IN ? "+" : "-") + Money.yuan(t.amount);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("待确认：" + amt + " " + t.merchant)
                .setContentText("点开确认这条自动记账")
                .setAutoCancel(true)
                .setContentIntent(open(c));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_CAPTURE, b.build());
    }

    /** 预算 80% / 100%。 */
    static void budgetAlert(Context c, long spent, long budget, boolean over) {
        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CH_ALERT);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        String title = over ? "这个月的预算用完了" : "预算用了 " + (spent * 100 / budget) + "%";
        String text = over
                ? "已花 " + Money.yuan(spent) + " / 预算 " + Money.yuan(budget)
                  + "，超支 " + Money.yuan(spent - budget)
                : "已花 " + Money.yuan(spent) + " / 预算 " + Money.yuan(budget)
                  + "，还剩 " + Money.yuan(budget - spent);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open(c));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_BUDGET, b.build());
    }
}
