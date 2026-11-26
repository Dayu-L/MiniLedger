package com.miniledger.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 账单列表：按天分组——一天一个小标题（日期 + 当日支出），下面挂当天的账。
 * 账单页和周期页共用，行渲染只有一份。
 */
class DayAdapter extends BaseAdapter {

    /** 一天的汇总头。 */
    static final class Day {
        final long day;
        long out;

        Day(long day) {
            this.day = day;
        }
    }

    private final Context ctx;
    private final List<Tx> data;
    private final ArrayList<Object> rows = new ArrayList<>();

    DayAdapter(Context c, List<Tx> data) {
        this.ctx = c;
        this.data = data;
    }

    /** 数据变了以后重新分组。 */
    void regroup() {
        rows.clear();
        long day = -1;
        Day head = null;
        for (Tx t : data) {
            long d = Span.dayStartOf(t.ts);
            if (d != day) {
                day = d;
                head = new Day(d);
                rows.add(head);
            }
            if (t.kind == Tx.OUT) head.out += t.amount;
            rows.add(t);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return at(position);
    }

    /** ListView 的 position -> 列表项（可能是 Day 头，也可能是 Tx）。 */
    Object item(int position) {
        return at(position);
    }

    /** 列表在刷新瞬间可能被问到越界的位置，统一返回 null 而不是抛异常。 */
    private Object at(int position) {
        return position >= 0 && position < rows.size() ? rows.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return at(position) instanceof Tx ? 0 : 1;
    }

    @Override
    public boolean isEnabled(int position) {
        return at(position) instanceof Tx;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object o = at(position);
        if (o instanceof Tx) {
            Tx t = (Tx) o;
            return row(ctx, t, Span.hm(t.ts));
        }
        if (!(o instanceof Day)) return new View(ctx);

        Day h = (Day) o;
        LinearLayout row = Ui.row(ctx);
        row.setPadding(0, Ui.d(ctx, 14), 0, Ui.d(ctx, 4));
        row.setBackgroundColor(Ui.rowBg(ctx));

        row.addView(Ui.tvBold(ctx, Span.dayLabel(h.day) + " · " + Span.weekday(h.day),
                        12, Ui.SUB),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (h.out > 0) {
            row.addView(Ui.tv(ctx, "支出 " + Money.yuan(h.out), 12, Ui.FAINT));
        }
        return row;
    }

    /**
     * 一行账目。
     *
     * @param timeText 副标题里的时间部分；null = 「今天 14:32」这种完整时间戳，
     *                 按天分组时传「14:32」就够
     */
    static View row(Context c, Tx t, String timeText) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.d(c, 12), 0, Ui.d(c, 12));
        row.setBackgroundColor(Ui.rowBg(c));

        int color = Ui.catColor(t.category);
        android.widget.TextView badge = Ui.tv(c, firstChar(t.category), 13, Ui.deep(color));
        badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setBackground(Ui.round(Ui.soft(color), 10, c));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.d(c, 36), Ui.d(c, 36)));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.d(c, 12), 0, Ui.d(c, 8), 0);

        android.widget.TextView name = Ui.tv(c,
                android.text.TextUtils.isEmpty(t.merchant) ? "未知" : t.merchant, 15, Ui.INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(name);

        String sub = t.category;
        if (!t.account.isEmpty()) sub += " · " + t.account;
        sub += " · " + (timeText == null ? Span.stamp(t.ts) : timeText);
        android.widget.TextView subTv = Ui.tv(c, sub, 12, Ui.FAINT);
        subTv.setSingleLine(true);
        subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.d(c, 3);
        mid.addView(subTv, slp);

        row.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String sign = t.kind == Tx.IN ? "+" : "-";
        android.widget.TextView amt = Ui.tvBold(c, sign + Money.yuan(t.amount), 16,
                t.kind == Tx.IN ? Ui.IN : Ui.OUT);
        row.addView(amt);

        return row;
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "·";
        return s.substring(0, 1);
    }
}
