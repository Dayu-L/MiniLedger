package com.miniledger.app;

import android.content.Context;

/**
 * 当前皮肤：玻璃 / 极简 × 浅色 / 深色。
 *
 * 配色只有一套 token（在 Ui 里），切换时 apply() 把 Ui 的静态色值改掉，
 * 界面颜色全部走 Ui.*，所以各页面不需要知道深色模式的存在。
 */
final class Skin {
    static boolean glass = true;
    static boolean dark = false;

    private Skin() {}

    static void load(Context c) {
        glass = Prefs.glass(c);
        dark = Prefs.dark(c);
        apply();
    }

    static void apply() {
        if (dark) {
            Ui.BG = 0xFF0D1114;
            Ui.CARD = 0xFF171C21;
            Ui.INK = 0xFFE8EBEF;
            Ui.SUB = 0xFFAFB6BF;
            Ui.FAINT = 0xFF959CA5;
            Ui.LINE = 0xFF2A3138;
            Ui.BRAND = 0xFF35B3A6;
            Ui.ON_BRAND = 0xFF06211D;
            Ui.IN = 0xFF4BD08A;
            Ui.OUT = Ui.INK;
            Ui.WARN = 0xFFF4716F;
            Ui.ROW = 0xFF1A2025;
            Ui.SOFT = 0xFF1D242A;
            Ui.SOFT_CARD = 0xFF1B2127;
            Ui.FAB_BG = 0xFFE8EBEF;
            Ui.FAB_FG = 0xFF11151A;
            Ui.TRACK = 0xFF242B32;
        } else {
            Ui.BG = 0xFFF2F5F8;
            Ui.CARD = 0xFFFFFFFF;
            Ui.INK = 0xFF11151A;
            Ui.SUB = 0xFF4B5058;
            Ui.FAINT = 0xFF5F6368;
            Ui.LINE = 0xFFE7E9EC;
            Ui.BRAND = 0xFF0F766E;
            Ui.ON_BRAND = 0xFFFFFFFF;
            Ui.IN = 0xFF247B50;
            Ui.OUT = 0xFF11151A;
            Ui.WARN = 0xFFC4242A;
            Ui.ROW = 0xFFF4F6F8;
            Ui.SOFT = 0xFFF1F3F5;
            Ui.SOFT_CARD = 0xFFFBFCFD;
            Ui.FAB_BG = 0xFF1D1F20;
            Ui.FAB_FG = 0xFFFFFFFF;
            Ui.TRACK = 0xFFEDEFF2;
        }
    }
}
