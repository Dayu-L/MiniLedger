package com.miniledger.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 控件工厂 + 配色。
 * 两套皮肤共用同一套色板与字号，只换表面材质：
 *   玻璃 = 半透明白 + 1px 高光边 + 极光底
 *   极简 = 白面 + 1px 边线 + 平底
 */
final class Ui {

    static final int INK = 0xFF11151A;
    static final int SUB = 0xFF4B5058;
    static final int FAINT = 0xFF5F6368;
    static final int LINE = 0xFFE7E9EC;
    static final int BG = 0xFFF2F5F8;
    static final int CARD = 0xFFFFFFFF;

    static final int BRAND = 0xFF0F766E;
    /** 收入：深绿，4.5:1 以上。 */
    static final int IN = 0xFF247B50;
    /** 支出：用墨色，不用红——红色只留给超支和故障。 */
    static final int OUT = 0xFF11151A;
    static final int WARN = 0xFFC4242A;

    static final int ROW = 0xFFF4F6F8;
    static final int SOFT = 0xFFF1F3F5;

    /** 未选中的 chip 底色。 */
    static int chipBg(Context c) {
        return Skin.glass ? 0x33FFFFFF : SOFT;
    }

    private static final int[] PALETTE = {
            0xFF0F766E, 0xFFC2410C, 0xFFB45309, 0xFF1D4ED8, 0xFF6D28D9,
            0xFF15803D, 0xFFBE185D, 0xFF0E7490, 0xFF4338CA, 0xFFB91C1C, 0xFF475569
    };

    private Ui() {}

    static int catColor(String cat) {
        if (cat == null || cat.isEmpty()) return PALETTE[10];
        return PALETTE[Math.abs(cat.hashCode()) % PALETTE.length];
    }

    static int soft(int color) {
        return Color.argb(26, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 同色的深墨版，给浅色标签当文字色。 */
    static int deep(int color) {
        return Color.rgb(
                Math.round(Color.red(color) * 0.52f),
                Math.round(Color.green(color) * 0.52f),
                Math.round(Color.blue(color) * 0.52f));
    }

    static int surface(Context c) {
        return Skin.glass ? 0xD9FFFFFF : CARD;
    }

    static int rowBg(Context c) {
        return Skin.glass ? 0x00000000 : CARD;
    }

    static int d(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return g;
    }

    static GradientDrawable roundStroke(int fill, float radiusDp, int strokeColor, float swDp, Context c) {
        GradientDrawable g = round(fill, radiusDp, c);
        int w = Math.max(1, Math.round(swDp * c.getResources().getDisplayMetrics().density));
        g.setStroke(w, strokeColor);
        return g;
    }

    static GradientDrawable circle(int fill, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    /** 白色卡片 / 玻璃卡片。 */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        if (Skin.glass) {
            l.setBackground(roundStroke(0x90FFFFFF, 20, 0x2411151A, 1, c));
            l.setElevation(d(c, 4));
        } else {
            l.setBackground(roundStroke(CARD, 20, LINE, 1, c));
            l.setElevation(0);
        }
        return l;
    }

    /** 一条按比例填充的进度条，返回容器。 */
    static LinearLayout bar(Context c, float ratio, int color, float heightDp) {
        LinearLayout track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        float r = Math.max(0f, Math.min(1f, ratio));
        int h = d(c, heightDp);

        if (r > 0) {
            View fill = new View(c);
            fill.setBackground(Ui.round(color, heightDp / 2f, c));
            track.addView(fill, new LinearLayout.LayoutParams(0, h, r));
        }
        if (r < 1f) {
            View rest = new View(c);
            rest.setBackground(Ui.round(Skin.glass ? 0x1A11151A : 0xFFEDEFF2, heightDp / 2f, c));
            track.addView(rest, new LinearLayout.LayoutParams(0, h, 1f - r));
        }
        return track;
    }

    static TextView tv(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView tvBold(Context c, CharSequence s, float sp, int color) {
        TextView t = tv(c, s, sp, color);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static View hLine(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Skin.glass ? 0x1F11151A : LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, d(c, 0.5f))));
        return v;
    }

    static View gap(Context c, float hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                d(c, hDp)));
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
}
