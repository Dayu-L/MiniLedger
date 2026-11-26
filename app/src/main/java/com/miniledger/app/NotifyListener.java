package com.miniledger.app;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;

public class NotifyListener extends NotificationListenerService {

    private static CharSequence first(CharSequence a, CharSequence b) {
        return a != null && a.length() > 0 ? a : b;
    }

    private static void add(StringBuilder sb, CharSequence s) {
        if (s == null) return;
        String v = s.toString().trim();
        if (v.isEmpty()) return;
        if (sb.indexOf(v) >= 0) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(v);
    }

    static boolean isEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(),
                "enabled_notification_listeners");
        if (flat == null) return false;
        return flat.contains(c.getPackageName());
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (Prefs.guardOn(this)) GuardService.ensure(this);
        GuardJob.schedule(this);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        try {
            requestRebind(new ComponentName(this, NotifyListener.class));
        } catch (Throwable ignored) {
        }
        GuardJob.schedule(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!Parser.PKG_WECHAT.equals(pkg) && !Parser.PKG_ALIPAY.equals(pkg)) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        Bundle ex = n.extras;

        CharSequence title = first(ex.getCharSequence(Notification.EXTRA_TITLE),
                ex.getCharSequence(Notification.EXTRA_TITLE_BIG));
        StringBuilder body = new StringBuilder();
        add(body, ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(body, ex.getCharSequence(Notification.EXTRA_TEXT));
        add(body, ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(body, ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));

        ArrayList<CharSequence> lines =
                ex.getCharSequenceArrayList(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence l : lines) add(body, l);
        }

        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null) {
            for (Parcelable p : msgs) {
                if (p instanceof Bundle) {
                    Bundle mb = (Bundle) p;
                    add(body, mb.getCharSequence("sender"));
                    add(body, mb.getCharSequence("text"));
                }
            }
        }

        if (body.length() == 0) return;

        Tx t = Parser.parse(pkg, title, body, sbn.getPostTime());
        if (t == null) return;

        long id = Db.get(this).insert(t, Prefs.autoConfirm(this));
        if (id <= 0) return;   // 重复推送，忽略

        GuardService.refresh(this);
        Budget.check(this);
        WidgetProvider.refresh(this);
        if (t.status == Tx.PENDING) Notifier.captured(this, t);
    }
}
