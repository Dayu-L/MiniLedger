package com.miniledger.app;

import android.content.Context;

/** 当前皮肤。App 启动时读一次，切换时重建界面。 */
final class Skin {
    static boolean glass = true;

    private Skin() {}

    static void load(Context c) {
        glass = Prefs.glass(c);
    }
}
