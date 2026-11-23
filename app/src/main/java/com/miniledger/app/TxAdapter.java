package com.miniledger.app;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

class TxAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Tx> data;

    TxAdapter(Context c, List<Tx> d) {
        this.ctx = c;
        this.data = d;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Tx getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Tx t = data.get(position);
        Context c = ctx;

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.d(c, 12), 0, Ui.d(c, 12));
        row.setBackgroundColor(Ui.rowBg(c));

        int color = Ui.catColor(t.category);
        TextView badge = Ui.tv(c, firstChar(t.category), 13, Ui.deep(color));
        badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(Ui.soft(color), 10, c));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.d(c, 36), Ui.d(c, 36));
        row.addView(badge, blp);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.d(c, 12), 0, Ui.d(c, 8), 0);

        TextView name = Ui.tv(c, TextUtils.isEmpty(t.merchant) ? "未知" : t.merchant, 15, Ui.INK);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);

        String sub = t.category;
        if (!t.account.isEmpty()) sub += " · " + t.account;
        sub += " · " + Span.friendly(t.ts);
        TextView subTv = Ui.tv(c, sub, 12, Ui.FAINT);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.d(c, 3);
        mid.addView(subTv, slp);

        row.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String sign = t.kind == Tx.IN ? "+" : "-";
        TextView amt = Ui.tvBold(c, sign + Money.yuan(t.amount), 16,
                t.kind == Tx.IN ? Ui.IN : Ui.OUT);
        row.addView(amt);

        return row;
    }

    private static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "·";
        return s.substring(0, 1);
    }
}
