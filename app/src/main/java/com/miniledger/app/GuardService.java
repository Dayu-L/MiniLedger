package com.miniledger.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 常驻前台服务：既是「今日支出」的展示位，也是保活的锚点。
 * 通知监听服务本身由系统绑定，但国产 ROM 清理后台时会把进程带走，
 * 这里用前台服务 + JobScheduler + Alarm 三路互相唤醒。
 */
public class GuardService extends Service {

    static void ensure(Context c) {
        if (!Prefs.guardOn(c)) return;
        try {
            Intent i = new Intent(c, GuardService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {
        }
    }

    static void refresh(Context c) {
        if (!Prefs.guardOn(c)) return;
        try {
            Intent i = new Intent(c, GuardService.class);
            i.putExtra("refresh", true);
            c.startService(i);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        return START_STICKY;
    }

    private void goForeground() {
        try {
            Notification n = Notifier.guard(this);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_GUARD, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notifier.ID_GUARD, n);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        GuardJob.schedule(this);
        HeartbeatReceiver.schedule(this, 60 * 1000L);
        ensure(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GuardJob.schedule(this);
        HeartbeatReceiver.schedule(this, 15 * 60 * 1000L);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
