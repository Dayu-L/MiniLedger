package com.miniledger.app;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Crash.install(this);
        Skin.load(this);
        Notifier.setup(this);
        if (Prefs.guardOn(this)) {
            GuardService.ensure(this);
            GuardJob.schedule(this);
            HeartbeatReceiver.schedule(this, GuardJob.PERIOD);
        }

        Housekeeping.run(this);
        Housekeeping.background(this);
    }
}
