package com.miniledger.app;

import android.content.Context;

/**
 * 低频巡检：固定支出落账、周期结算提醒、预算节奏、小组件刷新。
 *
 * 入口有三处：App 启动、GuardJob 每 15 分钟、MainActivity 回到前台。
 * 每一步都自己判「是不是该做了」，重复调用没有副作用。
 */
final class Housekeeping {

    private Housekeeping() {}

    static void run(Context c) {
        try {
            Recur.postDue(c);
        } catch (Throwable ignored) {
        }
        try {
            CycleCheck.run(c);
        } catch (Throwable ignored) {
        }
        try {
            Budget.check(c);
        } catch (Throwable ignored) {
        }
        try {
            WidgetProvider.refresh(c);
        } catch (Throwable ignored) {
        }
    }

    /** 备份要写文件，扔到后台线程。 */
    static void background(final Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                Backup.autoIfDue(app);
            } catch (Throwable ignored) {
            }
        }, "miniledger-backup").start();
    }
}
