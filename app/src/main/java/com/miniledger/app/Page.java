package com.miniledger.app;

import android.widget.FrameLayout;

abstract class Page extends FrameLayout {
    final MainActivity act;

    Page(MainActivity a) {
        super(a);
        this.act = a;
        setBackgroundColor(0x00000000);
    }

    abstract void refresh();
}
