package com.miniledger.app;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 低置信度 / 疑似重复的记录，等用户点头。 */
class PagePending extends Page {

    private final ListView list;
    private final ArrayList<Tx> data = new ArrayList<>();
    private final Adapter adapter;
    private final TextView countTv;
    private final TextView confirmAll;

    PagePending(MainActivity a) {
        super(a);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.d(a, 20), Ui.d(a, 18), Ui.d(a, 20), 0);
        LinearLayout tl = new LinearLayout(a);
        tl.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.tvBold(a, "待确认", 20, Ui.INK);
        countTv = Ui.tv(a, "", 12, Ui.FAINT);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = Ui.d(a, 3);
        tl.addView(title);
        tl.addView(countTv, ctl);
        top.addView(tl, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        confirmAll = Ui.tv(a, "全部确认", 13, Ui.BRAND);
        confirmAll.setPadding(Ui.d(a, 14), Ui.d(a, 8), Ui.d(a, 14), Ui.d(a, 8));
        confirmAll.setBackground(Ui.round(Ui.soft(Ui.BRAND), 10, a));
        top.addView(confirmAll);
        col.addView(top);

        LinearLayout cardWrap = new LinearLayout(a);
        cardWrap.setOrientation(LinearLayout.VERTICAL);
        cardWrap.setBackground(Ui.round(Ui.CARD, 18, a));
        cardWrap.setClipToOutline(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.setMargins(Ui.d(a, 16), Ui.d(a, 12), Ui.d(a, 16), Ui.d(a, 16));
        col.addView(cardWrap, clp);

        FrameLayout fl = new FrameLayout(a);
        cardWrap.addView(fl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new ListView(a);
        list.setDivider(null);
        list.setPadding(Ui.d(a, 12), Ui.d(a, 8), Ui.d(a, 12), Ui.d(a, 8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        adapter = new Adapter(a);
        list.setAdapter(adapter);
        fl.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = Ui.tv(a, "没有待确认的账单\n\n自动记账抓到的记录会直接入账，\n只有疑似重复或没认出来的才放到这里", 13, Ui.FAINT);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(Ui.d(a, 4), 1f);
        fl.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(empty);

        confirmAll.setOnClickListener(v -> {
            Db.get(a).confirmAll();
            act.refreshAll();
        });
    }

    @Override
    void refresh() {
        List<Tx> l = Db.get(act).pending();
        data.clear();
        data.addAll(l);
        adapter.notifyDataSetChanged();
        int n = data.size();
        countTv.setText(n == 0 ? "全部处理完了" : "共 " + n + " 条等着你确认");
        confirmAll.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
    }

    private class Adapter extends BaseAdapter {
        private final Context c;

        Adapter(Context c) {
            this.c = c;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Tx t = data.get(position);

            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(Ui.round(0xFFFBFCFD, 14, c));
            box.setPadding(Ui.d(c, 12), Ui.d(c, 10), Ui.d(c, 12), Ui.d(c, 8));

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            int color = Ui.catColor(t.category);
            TextView badge = Ui.tv(c, t.category.isEmpty() ? "?" : t.category.substring(0, 1), 13, color);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.round(Ui.soft(color), 10, c));
            row.addView(badge, new LinearLayout.LayoutParams(Ui.d(c, 34), Ui.d(c, 34)));

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Ui.d(c, 10), 0, Ui.d(c, 6), 0);
            TextView name = Ui.tv(c, TextUtils.isEmpty(t.merchant) ? "未知" : t.merchant, 14.5f, Ui.INK);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(name);
            TextView sub = Ui.tv(c, t.category + " · " + t.account + " · " + Span.friendly(t.ts),
                    11, Ui.FAINT);
            sub.setSingleLine(true);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.d(c, 3);
            mid.addView(sub, slp);
            row.addView(mid, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String sign = t.kind == Tx.IN ? "+" : "-";
            row.addView(Ui.tvBold(c, sign + Money.yuan(t.amount), 15,
                    t.kind == Tx.IN ? Ui.IN : Ui.OUT));
            box.addView(row);

            LinearLayout actions = Ui.row(c);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.d(c, 6);
            box.addView(actions, alp);

            if (t.dup == 1) {
                TextView tag = Ui.tv(c, "疑似重复", 11f, 0xFFB45309);
                tag.setPadding(Ui.d(c, 8), Ui.d(c, 3), Ui.d(c, 8), Ui.d(c, 3));
                tag.setBackground(Ui.round(0x1FF59E0B, 8, c));
                actions.addView(tag);
            }

            actions.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));

            TextView edit = mini("改分类", Ui.SUB, Ui.chipBg(c));
            actions.addView(edit);
            edit.setOnClickListener(v -> AddDialog.show(act, t));

            TextView ok = mini("确认", Ui.BRAND, Ui.soft(Ui.BRAND));
            LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            okLp.leftMargin = Ui.d(c, 8);
            actions.addView(ok, okLp);
            ok.setOnClickListener(v -> {
                Db.get(act).confirm(t.id);
                act.refreshAll();
            });

            TextView del = mini("删除", Ui.FAINT, Ui.chipBg(c));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = Ui.d(c, 8);
            actions.addView(del, dlp);
            del.setOnClickListener(v -> {
                Db.get(act).del(t.id);
                act.refreshAll();
            });

            return box;
        }

        private TextView mini(String s, int fg, int bg) {
            TextView t = Ui.tv(c, s, 12, fg);
            t.setPadding(Ui.d(c, 12), Ui.d(c, 5), Ui.d(c, 12), Ui.d(c, 5));
            t.setBackground(Ui.round(bg, 9, c));
            return t;
        }
    }
}
