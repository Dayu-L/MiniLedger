package com.miniledger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Prefs.guardOn(c)) return;
        GuardService.ensure(c);
        GuardJob.schedule(c);
        HeartbeatReceiver.schedule(c, 30 * 1000L);
    }
}
