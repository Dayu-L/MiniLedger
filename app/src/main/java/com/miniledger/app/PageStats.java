package com.miniledger.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

class PageStats extends Page {

    private final LinearLayout col;
    private final TrendView trend;

    PageStats(MainActivity a) {
        super(a);

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.d(a, 24));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        trend = new TrendView(a);
    }

    @Override
    void refresh() {
        col.removeAllViews();

        LinearLayout top = Ui.row(act);
        top.setPadding(Ui.d(act, 20), Ui.d(act, 18), Ui.d(act, 20), 0);
        top.addView(Ui.tvBold(act, "统计", 20, Ui.INK));
        col.addView(top);

        long from = Span.monthStart(0);
        long to = Span.monthEnd(0);
        Db db = Db.get(act);
        long out = db.sumKind(Tx.OUT, from, to);
        long in = db.sumKind(Tx.IN, from, to);

        // ---- 本月概览
        LinearLayout card = Ui.card(act);
        card.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 18), Ui.d(act, 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(card, cp);

        card.addView(Ui.tv(act, Span.monthLabel(0) + " 概览", 12, Ui.FAINT));

        LinearLayout big = Ui.row(act);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.d(act, 8);
        card.addView(big, blp);

        big.addView(statBlock("支出", Money.yuan(out), Ui.OUT), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        big.addView(statBlock("收入", Money.yuan(in), Ui.IN), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        long net = in - out;
        long budget = Prefs.budget(act);
        String netText;
        int netColor = Ui.SUB;
        if (budget > 0 && out > budget) {
            netText = "超出预算 " + Money.yuan(out - budget);
            netColor = Ui.WARN;
        } else {
            netText = (net >= 0 ? "结余 " : "净支出 ") + Money.yuan(Math.abs(net));
        }
        TextView netTv = Ui.tv(act, netText, 12, netColor);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.d(act, 12);
        card.addView(netTv, nlp);

        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_MONTH);
        long avg = day > 0 ? out / day : 0;
        TextView avgTv = Ui.tv(act, "日均支出 " + Money.yuan(avg) + "　·　已过 " + day + " 天",
                12, Ui.FAINT);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.d(act, 4);
        card.addView(avgTv, alp);

        // ---- 最近 7 天
        LinearLayout tcard = Ui.card(act);
        tcard.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 14), Ui.d(act, 10));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(tcard, tlp);

        tcard.addView(Ui.tv(act, "最近 7 天", 12, Ui.FAINT));
        trend.setData(db.last7Days());
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 120));
        tvlp.topMargin = Ui.d(act, 10);
        tcard.addView(trend, tvlp);

        // ---- 分类排行
        LinearLayout ccard = Ui.card(act);
        ccard.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 18), Ui.d(act, 8));
        LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cclp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(ccard, cclp);

        ccard.addView(Ui.tv(act, "本月支出分类", 12, Ui.FAINT));

        List<String[]> cats = db.catStats(Tx.OUT, from, to);
        if (cats.isEmpty()) {
            TextView none = Ui.tv(act, "还没有支出记录", 13, Ui.FAINT);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = Ui.d(act, 14);
            ccard.addView(none, nl);
        } else {
            for (String[] row : cats) {
                String cat = row[0];
                long sum = Long.parseLong(row[1]);
                ccard.addView(catRow(cat, sum, out));
            }
        }
    }

    private LinearLayout statBlock(String label, String value, int color) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(Ui.tv(act, label, 11, Ui.FAINT));
        TextView v = Ui.tvBold(act, value, 19, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.d(act, 3);
        l.addView(v, lp);
        return l;
    }

    private View catRow(String cat, long sum, long total) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.d(act, 12);
        box.setLayoutParams(blp);

        LinearLayout head = Ui.row(act);
        int color = Ui.catColor(cat);
        TextView dot = new TextView(act);
        dot.setBackground(Ui.circle(color, act));
        head.addView(dot, new LinearLayout.LayoutParams(Ui.d(act, 8), Ui.d(act, 8)));

        TextView name = Ui.tv(act, "  " + cat, 13.5f, Ui.INK);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int pct = total > 0 ? (int) Math.round(sum * 100.0 / total) : 0;
        head.addView(Ui.tv(act, Money.yuan(sum) + "  " + pct + "%", 12.5f, Ui.SUB));
        box.addView(head);

        LinearLayout track = new LinearLayout(act);
        track.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 6));
        tlp.topMargin = Ui.d(act, 6);
        box.addView(track, tlp);

        float ratio = total > 0 ? (float) sum / total : 0f;
        if (ratio <= 0f) ratio = 0.01f;
        if (ratio > 1f) ratio = 1f;

        View fill = new View(act);
        fill.setBackground(Ui.round(Ui.BRAND, 3, act));
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.d(act, 6), ratio));

        View rest = new View(act);
        rest.setBackground(Ui.round(Skin.glass ? 0x1A11151A : 0xFFEDEFF2, 3, act));
        track.addView(rest, new LinearLayout.LayoutParams(0, Ui.d(act, 6), 1f - ratio));

        return box;
    }

    /** 最近 7 天支出柱状图。 */
    static class TrendView extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] vals = new long[7];
        private final float dens;

        TrendView(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void setData(long[] v) {
            if (v != null && v.length > 0) vals = v;
            invalidate();
        }

        private float dp(float v) {
            return v * dens;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int n = vals.length;
            float padL = dp(2), padR = dp(2), padB = dp(16), padT = dp(4);
            float w = getWidth() - padL - padR;
            float h = getHeight() - padB - padT;
            if (w <= 0 || h <= 0) return;

            long max = 1;
            for (long v : vals) if (v > max) max = v;

            float slot = w / n;
            float bw = slot * 0.42f;

            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -(n - 1));

            txt.setTextSize(dp(9));

            for (int i = 0; i < n; i++) {
                float bh = (float) vals[i] / (float) max * (h - dp(2));
                if (vals[i] > 0 && bh < dp(4)) bh = dp(4);
                float left = padL + slot * i + (slot - bw) / 2f;
                float top = padT + (h - bh);

                if (vals[i] > 0) {
                    boolean today = i == n - 1;
                    bar.setColor(today ? Ui.BRAND : Color.argb(70, 15, 118, 110));
                    RectF r = new RectF(left, top, left + bw, padT + h);
                    canvas.drawRoundRect(r, dp(3), dp(3), bar);
                } else {
                    bar.setColor(0xFFEDEFF2);
                    RectF r = new RectF(left, padT + h - dp(3), left + bw, padT + h);
                    canvas.drawRoundRect(r, dp(2), dp(2), bar);
                }

                txt.setColor(i == n - 1 ? Ui.BRAND : Ui.FAINT);
                canvas.drawText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)),
                        left + bw / 2f, getHeight() - dp(3), txt);
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }
}
