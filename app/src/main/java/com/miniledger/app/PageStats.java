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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统计：自然月 / 本周期两种口径，分类排行带环比，另加商户排行。 */
class PageStats extends Page {

    private final LinearLayout col;
    private boolean cycleMode = false;

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
    }

    @Override
    void refresh() {
        col.removeAllViews();

        boolean cycleOn = Prefs.cycleStart(act) > 0 && cycleMode;

        LinearLayout top = Ui.row(act);
        top.setPadding(Ui.d(act, 20), Ui.d(act, 18), Ui.d(act, 20), 0);
        top.addView(Ui.tvBold(act, "统计", 20, Ui.INK),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (Prefs.cycleStart(act) > 0) {
            top.addView(segChip("自然月", !cycleOn, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.d(act, 32));
            lp.leftMargin = Ui.d(act, 6);
            top.addView(segChip("本周期", cycleOn, true), lp);
        }
        col.addView(top);

        // ---- 区间
        long from;
        long to;
        long prevFrom = 0;
        long prevTo = 0;
        String label;
        int elapsed;
        int days;

        Db db = Db.get(act);

        if (cycleOn) {
            long base = Prefs.cycleStart(act);
            int len = Prefs.cycleDays(act);
            long now = System.currentTimeMillis();
            int idx = Span.cycleIndex(base, len, now);
            Span.Cycle cyc = Span.cycle(base, len, idx);
            if (cyc == null) return;
            from = cyc.start;
            to = cyc.end;
            days = cyc.days();
            elapsed = now >= cyc.end ? days
                    : Math.max(1, Math.min(days, Span.dayDiff(from, now) + 1));
            label = Span.rangeLabel(from, to);
            if (idx > 0) {
                Span.Cycle prev = Span.cycle(base, len, idx - 1);
                if (prev != null) {
                    prevFrom = prev.start;
                    prevTo = prev.end;
                }
            }
        } else {
            from = Span.monthStart(0);
            to = Span.monthEnd(0);
            days = Math.max(1, Span.dayDiff(from, to));
            elapsed = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            label = Span.monthLabel(0);
            prevFrom = Span.monthStart(-1);
            prevTo = Span.monthEnd(-1);
        }

        long out = db.sumKind(Tx.OUT, from, to);
        long in = db.sumKind(Tx.IN, from, to);

        // ---- 概览
        LinearLayout card = Ui.card(act);
        card.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 18), Ui.d(act, 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(card, cp);

        card.addView(Ui.tv(act, label + " 概览", 12, Ui.FAINT));

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

        long avg = elapsed > 0 ? out / elapsed : 0;
        TextView avgTv = Ui.tv(act, "日均支出 " + Money.yuan(avg) + "　·　已过 " + elapsed
                + " / " + days + " 天", 12, Ui.FAINT);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.d(act, 4);
        card.addView(avgTv, alp);

        // ---- 趋势
        // 注意：TrendView 必须每次 refresh 新建。col.removeAllViews() 只摘掉直系子视图，
        // 上一轮的卡片里还挂着旧的 trend，重复 addView 会抛
        // 「The specified child already has a parent」——这就是以前来回切页签闪退的原因。
        TrendView trend = new TrendView(act);
        LinearLayout tcard = Ui.card(act);
        tcard.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 14), Ui.d(act, 10));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(tcard, tlp);

        if (cycleOn) {
            int n = Math.min(days, Span.dayDiff(from, to));
            tcard.addView(Ui.tv(act, "这个周期每天花了多少", 12, Ui.FAINT));
            String[] labels = new String[n];
            for (int i = 0; i < n; i++) {
                labels[i] = (i == 0 || (i + 1) % 5 == 0) ? String.valueOf(i + 1) : "";
            }
            trend.setData(db.dailyOut(from, n), labels);
        } else {
            tcard.addView(Ui.tv(act, "最近 7 天", 12, Ui.FAINT));
            trend.setData(db.last7Days());
        }
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 120));
        tvlp.topMargin = Ui.d(act, 10);
        tcard.addView(trend, tvlp);

        // ---- 分类排行（带环比）
        Map<String, Long> prevCats = new HashMap<>();
        if (prevTo > prevFrom) {
            for (String[] r : db.catStats(Tx.OUT, prevFrom, prevTo)) {
                prevCats.put(r[0], Long.parseLong(r[1]));
            }
        }

        LinearLayout ccard = Ui.card(act);
        ccard.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 18), Ui.d(act, 8));
        LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cclp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(ccard, cclp);

        ccard.addView(Ui.tv(act, cycleOn ? "本周期支出分类" : "本月支出分类", 12, Ui.FAINT));

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
                Long prev = prevCats.get(cat);
                ccard.addView(catRow(cat, sum, out, prev == null ? -1 : prev));
            }
        }

        // ---- 商户排行
        LinearLayout mcard = Ui.card(act);
        mcard.setPadding(Ui.d(act, 18), Ui.d(act, 16), Ui.d(act, 18), Ui.d(act, 12));
        LinearLayout.LayoutParams mclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mclp.setMargins(Ui.d(act, 16), Ui.d(act, 12), Ui.d(act, 16), 0);
        col.addView(mcard, mclp);

        mcard.addView(Ui.tv(act, "花钱最多的地方", 12, Ui.FAINT));
        List<String[]> shops = db.merchantStats(Tx.OUT, from, to, 5);
        if (shops.isEmpty()) {
            TextView none = Ui.tv(act, "还没有商户记录", 13, Ui.FAINT);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = Ui.d(act, 14);
            mcard.addView(none, nl);
        } else {
            long topSum = Long.parseLong(shops.get(0)[1]);
            for (String[] s : shops) {
                mcard.addView(shopRow(s[0], Long.parseLong(s[1]), topSum));
            }
        }
    }

    private TextView segChip(String name, boolean on, boolean cycle) {
        TextView t = Ui.tv(act, name, 12.5f, on ? Ui.ON_BRAND : Ui.SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.d(act, 12), Ui.d(act, 6), Ui.d(act, 12), Ui.d(act, 6));
        t.setBackground(Ui.round(on ? Ui.BRAND : Ui.chipBg(act), 10, act));
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.d(act, 32)));
        t.setOnClickListener(v -> {
            if (cycleMode != cycle) {
                cycleMode = cycle;
                refresh();
            }
        });
        return t;
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

    /** prevSum < 0 表示上个周期没有这个分类。 */
    private View catRow(String cat, long sum, long total, long prevSum) {
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

        String delta = "";
        if (prevSum > 0) {
            long d = sum - prevSum;
            int pct = (int) Math.round(d * 100.0 / prevSum);
            if (d == 0) delta = "　—";
            else delta = "　" + (d > 0 ? "↑" : "↓") + Math.abs(pct) + "%";
        } else if (prevSum == 0) {
            delta = "　新增";
        }
        if (!delta.isEmpty()) {
            head.addView(Ui.tv(act, delta, 11.5f, Ui.FAINT));
        }

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
        rest.setBackground(Ui.round(Ui.track(), 3, act));
        track.addView(rest, new LinearLayout.LayoutParams(0, Ui.d(act, 6), 1f - ratio));

        return box;
    }

    private View shopRow(String merchant, long sum, long top) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.d(act, 12);
        box.setLayoutParams(blp);

        LinearLayout head = Ui.row(act);
        TextView name = Ui.tv(act, merchant, 13.5f, Ui.INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.tv(act, Money.yuan(sum), 12.5f, Ui.SUB));
        box.addView(head);

        LinearLayout track = new LinearLayout(act);
        track.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 4));
        tlp.topMargin = Ui.d(act, 5);
        box.addView(track, tlp);

        float ratio = top > 0 ? (float) sum / top : 0f;
        if (ratio <= 0f) ratio = 0.01f;
        View fill = new View(act);
        fill.setBackground(Ui.round(Ui.catColor(merchant), 2, act));
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.d(act, 4), ratio));

        View rest = new View(act);
        rest.setBackground(Ui.round(Ui.track(), 2, act));
        track.addView(rest, new LinearLayout.LayoutParams(0, Ui.d(act, 4), 1f - ratio));

        return box;
    }

    /** 柱状图：支持任意根数 + 自定义刻度文案。 */
    static class TrendView extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] vals = new long[7];
        private String[] labels;
        private final float dens;

        TrendView(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void setData(long[] v) {
            setData(v, null);
        }

        void setData(long[] v, String[] lbl) {
            if (v != null && v.length > 0) vals = v;
            labels = lbl;
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
            float bw = slot * (n > 14 ? 0.5f : 0.42f);
            if (bw > dp(22)) bw = dp(22);

            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -(n - 1));

            txt.setTextSize(dp(n > 14 ? 8 : 9));

            for (int i = 0; i < n; i++) {
                float bh = (float) vals[i] / (float) max * (h - dp(2));
                if (vals[i] > 0 && bh < dp(4)) bh = dp(4);
                float left = padL + slot * i + (slot - bw) / 2f;
                float top = padT + (h - bh);

                if (vals[i] > 0) {
                    boolean last = i == n - 1;
                    bar.setColor(last ? Ui.BRAND : Color.argb(70, Color.red(Ui.BRAND),
                            Color.green(Ui.BRAND), Color.blue(Ui.BRAND)));
                    RectF r = new RectF(left, top, left + bw, padT + h);
                    canvas.drawRoundRect(r, dp(3), dp(3), bar);
                } else {
                    bar.setColor(Ui.track());
                    RectF r = new RectF(left, padT + h - dp(3), left + bw, padT + h);
                    canvas.drawRoundRect(r, dp(2), dp(2), bar);
                }

                String lbl;
                if (labels != null) {
                    lbl = i < labels.length ? labels[i] : "";
                } else {
                    lbl = String.valueOf(c.get(Calendar.DAY_OF_MONTH));
                }
                txt.setColor(i == n - 1 ? Ui.BRAND : Ui.FAINT);
                if (lbl != null && !lbl.isEmpty()) {
                    canvas.drawText(lbl, left + bw / 2f, getHeight() - dp(3), txt);
                }
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }
}
