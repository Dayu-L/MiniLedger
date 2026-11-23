package com.miniledger.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;

/** 每 15 分钟自检一次：服务在不在、授权还在不在、监听是否被系统解绑。 */
public class GuardJob extends JobService {

    static final int JOB_ID = 7001;
    static final long PERIOD = 15 * 60 * 1000L;

    static void schedule(Context c) {
        if (!Prefs.guardOn(c)) return;
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo.Builder b = new JobInfo.Builder(JOB_ID,
                new ComponentName(c, GuardJob.class))
                .setPeriodic(PERIOD)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        try {
            js.schedule(b.build());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context c = getApplicationContext();
        if (!Prefs.guardOn(c)) return false;

        GuardService.ensure(c);

        if (!NotifyListener.isEnabled(c)) {
            Notifier.listenerLost(c);
        } else {
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(c, NotifyListener.class));
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
