package com.miniledger.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;

/** AlarmManager 心跳，兜底 JobScheduler 被 ROM 掐掉的情况。 */
public class HeartbeatReceiver extends BroadcastReceiver {

    private static final int REQ = 9001;

    static void schedule(Context c, long delayMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, HeartbeatReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs, pi);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Prefs.guardOn(c)) return;
        GuardService.ensure(c);
        if (NotifyListener.isEnabled(c)) {
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(c, NotifyListener.class));
            } catch (Throwable ignored) {
            }
        } else {
            Notifier.listenerLost(c);
        }
        schedule(c, GuardJob.PERIOD);
    }
}
