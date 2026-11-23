package com.miniledger.app;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Skin.load(this);
        Notifier.setup(this);
        if (Prefs.guardOn(this)) {
            GuardService.ensure(this);
            GuardJob.schedule(this);
            HeartbeatReceiver.schedule(this, GuardJob.PERIOD);
        }
    }
}
