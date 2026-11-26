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
 *
 * 两套皮肤共用同一套色板与字号，只换表面材质：
 *   玻璃 = 半透明白 + 1px 高光边 + 极光底
 *   极简 = 白面 + 1px 边线 + 平底
 *
 * 这些色值不是 final：Skin.apply() 在深色模式下会换一整套（见 Skin.java）。
 * 所有 UI 只用这里和 Skin 的颜色，页面里不要写死颜色。
 */
final class Ui {

    static int INK = 0xFF11151A;
    static int SUB = 0xFF4B5058;
    static int FAINT = 0xFF5F6368;
    static int LINE = 0xFFE7E9EC;
    static int BG = 0xFFF2F5F8;
    static int CARD = 0xFFFFFFFF;

    static int BRAND = 0xFF0F766E;
    /** 铺在 BRAND 实心块上的文字色：深色皮肤下 BRAND 变亮，白字就不够读了。 */
    static int ON_BRAND = 0xFFFFFFFF;
    /** 收入：深绿，4.5:1 以上。 */
    static int IN = 0xFF247B50;
    /** 支出：用墨色，不用红——红色只留给超支和故障。 */
    static int OUT = 0xFF11151A;
    static int WARN = 0xFFC4242A;

    static int ROW = 0xFFF4F6F8;
    static int SOFT = 0xFFF1F3F5;
    /** 卡片里再嵌一层的小卡（待确认）。 */
    static int SOFT_CARD = 0xFFFBFCFD;
    /** 进度条 / 柱状图没填满的部分。 */
    static int TRACK = 0xFFEDEFF2;
    static int FAB_BG = 0xFF1D1F20;
    static int FAB_FG = 0xFFFFFFFF;

    /** 未选中的 chip 底色。 */
    static int chipBg(Context c) {
        if (Skin.glass) return Skin.dark ? 0x1FFFFFFF : 0x33FFFFFF;
        return Skin.dark ? 0xFF1E252B : SOFT;
    }

    /** chip 底：玻璃皮肤补一道发丝边（纯白底压在卡片上会看不见边界）。 */
    static GradientDrawable chipBgDrawable(Context c) {
        return chipBgDrawable(c, 10);
    }

    static GradientDrawable chipBgDrawable(Context c, float radiusDp) {
        if (Skin.glass) {
            return roundStroke(0x33FFFFFF, radiusDp,
                    Skin.dark ? 0x24FFFFFF : 0x1F11151A, 1, c);
        }
        return round(SOFT, radiusDp, c);
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
        int a = Skin.dark ? 48 : 26;
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 同色的文字版：浅色皮肤压深，深色皮肤提亮，都保证在 soft() 底上读得清。 */
    static int deep(int color) {
        if (Skin.dark) {
            return Color.rgb(
                    Math.min(255, Math.round(Color.red(color) * 0.45f + 255 * 0.55f)),
                    Math.min(255, Math.round(Color.green(color) * 0.45f + 255 * 0.55f)),
                    Math.min(255, Math.round(Color.blue(color) * 0.45f + 255 * 0.55f)));
        }
        return Color.rgb(
                Math.round(Color.red(color) * 0.52f),
                Math.round(Color.green(color) * 0.52f),
                Math.round(Color.blue(color) * 0.52f));
    }

    static int surface(Context c) {
        if (Skin.dark) return Skin.glass ? 0xE61A2126 : 0xFF141A1F;
        return Skin.glass ? 0xD9FFFFFF : CARD;
    }

    static int rowBg(Context c) {
        if (Skin.glass) return 0x00000000;
        return CARD;
    }

    /** 进度条底 / 柱状图空白格。 */
    static int track() {
        if (Skin.glass) return Skin.dark ? 0x1AFFFFFF : 0x1A11151A;
        return TRACK;
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
            l.setBackground(roundStroke(Skin.dark ? 0xCC1B2126 : 0x90FFFFFF, 20,
                    Skin.dark ? 0x24FFFFFF : 0x2411151A, 1, c));
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
            rest.setBackground(Ui.round(track(), heightDp / 2f, c));
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
        if (Skin.glass) v.setBackgroundColor(Skin.dark ? 0x1FFFFFFF : 0x1F11151A);
        else v.setBackgroundColor(LINE);
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
