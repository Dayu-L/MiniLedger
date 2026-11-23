package com.miniledger.app;

import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class PageBills extends Page {

    private final ListView list;
    private final ArrayList<Tx> data = new ArrayList<>();
    private final TxAdapter adapter;
    private final TextView monthTv;
    private final TextView outTv;
    private final TextView inTv;
    private final TextView statusTv;
    private final LinearLayout budgetBox;
    private final LinearLayout cardWrap;
    private final FrameLayout cardInner;
    private int offset = 0;

    PageBills(MainActivity a) {
        super(a);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 顶栏
        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.d(a, 20), Ui.d(a, 14), Ui.d(a, 20), 0);
        TextView brand = Ui.tvBold(a, "简账", 20, Ui.INK);
        top.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusTv = Ui.tv(a, "", 12, Ui.FAINT);
        top.addView(statusTv);
        col.addView(top);

        // ---- 月度卡片
        LinearLayout head = Ui.card(a);
        head.setPadding(Ui.d(a, 18), Ui.d(a, 6), Ui.d(a, 18), Ui.d(a, 16));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), 0);
        col.addView(head, hp);

        LinearLayout monthRow = Ui.row(a);
        TextView prev = arrow("‹");
        TextView next = arrow("›");
        monthTv = Ui.tv(a, "", 13, Ui.SUB);
        monthTv.setGravity(Gravity.CENTER);
        monthRow.addView(prev, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
        monthRow.addView(monthTv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        monthRow.addView(next, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
        head.addView(monthRow);

        LinearLayout amtRow = Ui.row(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView outLabel = Ui.tv(a, "本月支出", 12, Ui.FAINT);
        outTv = Ui.tvBold(a, "¥0.00", 26, Ui.OUT);
        LinearLayout.LayoutParams otl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        otl.topMargin = Ui.d(a, 2);
        left.addView(outLabel);
        left.addView(outTv, otl);
        amtRow.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView inLabel = Ui.tv(a, "本月收入", 12, Ui.FAINT);
        inTv = Ui.tvBold(a, "¥0.00", 16, Ui.IN);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = Ui.d(a, 6);
        right.addView(inLabel);
        right.addView(inTv, ilp);
        amtRow.addView(right);
        head.addView(amtRow);

        budgetBox = new LinearLayout(a);
        budgetBox.setOrientation(LinearLayout.VERTICAL);
        head.addView(budgetBox);

        prev.setOnClickListener(v -> {
            offset--;
            refresh();
        });
        next.setOnClickListener(v -> {
            if (offset < 0) {
                offset++;
                refresh();
            }
        });

        // ---- 列表卡片
        cardWrap = new LinearLayout(a);
        cardWrap.setOrientation(LinearLayout.VERTICAL);
        cardWrap.setBackground(Ui.round(Ui.CARD, 18, a));
        cardWrap.setClipToOutline(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.setMargins(Ui.d(a, 16), Ui.d(a, 12), Ui.d(a, 16), Ui.d(a, 16));
        col.addView(cardWrap, clp);

        FrameLayout fl = new FrameLayout(a);
        cardInner = fl;
        cardWrap.addView(fl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new ListView(a);
        list.setDivider(new ColorDrawable(Ui.LINE));
        list.setDividerHeight(1);
        list.setPadding(Ui.d(a, 16), 0, Ui.d(a, 16), Ui.d(a, 8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        adapter = new TxAdapter(a, data);
        list.setAdapter(adapter);
        fl.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = Ui.tv(a, "这个月还没有账\n点右下角 ＋ 记一笔，或去「设置」导入账单", 13, Ui.FAINT);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(Ui.d(a, 4), 1f);
        fl.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(empty);

        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < data.size()) AddDialog.show(act, data.get(pos));
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (pos >= 0 && pos < data.size()) menu(data.get(pos));
            return true;
        });

        // ---- 悬浮按钮
        TextView fab = Ui.tv(a, "＋", 26, 0xFFFFFFFF);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.circle(0xFF1D1F20, a));
        fab.setElevation(Ui.d(a, 10));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                Ui.d(a, 56), Ui.d(a, 56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.d(a, 24), Ui.d(a, 24));
        addView(fab, fp);
        fab.setOnClickListener(v -> AddDialog.show(act, null));
    }

    private TextView arrow(String s) {
        TextView t = Ui.tv(act, s, 18, Ui.FAINT);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(null);
        return t;
    }

    private void menu(final Tx t) {
        String[] items = {"编辑金额 / 分类", "删除这笔", "查看原始通知"};
        new AlertDialog.Builder(act)
                .setTitle(t.merchant + "  " + Money.yuan(t.amount))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        AddDialog.show(act, t);
                    } else if (which == 1) {
                        new AlertDialog.Builder(act)
                                .setMessage("删除这笔 " + Money.yuan(t.amount) + " ？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (dd, w) -> {
                                    Db.get(act).del(t.id);
                                    refresh();
                                    act.refreshAll();
                                })
                                .show();
                    } else {
                        String raw = t.raw == null || t.raw.isEmpty() ? "（手动记账，无原始通知）" : t.raw;
                        new AlertDialog.Builder(act)
                                .setTitle("原始通知")
                                .setMessage(raw + "\n\n来源：" + t.source
                                        + (t.txid.isEmpty() ? "" : "\n单号：" + t.txid))
                                .setPositiveButton("知道了", null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    void refresh() {
        long from = Span.monthStart(offset);
        long to = Span.monthEnd(offset);
        monthTv.setText(Span.monthLabel(offset));
        long out = Db.get(act).sumKind(Tx.OUT, from, to);
        outTv.setText(Money.yuan(out));
        inTv.setText("+" + Money.yuan(Db.get(act).sumKind(Tx.IN, from, to)));

        refreshBudget(out);

        List<Tx> l = Db.get(act).bills(from, to);
        data.clear();
        data.addAll(l);
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        resizeCard();

        boolean on = NotifyListener.isEnabled(act);
        statusTv.setText(on ? "● 监听中" : "○ 未授权");
        statusTv.setTextColor(on ? Ui.IN : Ui.WARN);
    }

    /** 账少的时候别让卡片撑满整屏；账多的时候才占满并滚动。 */
    private void resizeCard() {
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) cardWrap.getLayoutParams();
        if (clp == null) return;

        int n = data.size();
        boolean wrap = n > 0 && n <= 6;

        if (n == 0) {
            clp.weight = 0f;
            clp.height = Ui.d(act, 240);
        } else if (wrap) {
            clp.weight = 0f;
            clp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            clp.weight = 1f;
            clp.height = 0;
        }
        cardWrap.setLayoutParams(clp);

        int inner = (n == 0 || !wrap) ? ViewGroup.LayoutParams.MATCH_PARENT
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (cardInner != null) {
            ViewGroup.LayoutParams p = cardInner.getLayoutParams();
            p.height = inner;
            cardInner.setLayoutParams(p);
        }
        ViewGroup.LayoutParams lp = list.getLayoutParams();
        lp.height = inner;
        list.setLayoutParams(lp);
    }

    /** 预算条只在「本月」显示，看历史月份时没有意义。 */
    private void refreshBudget(long spent) {
        budgetBox.removeAllViews();
        long budget = Prefs.budget(act);
        if (budget <= 0 || offset != 0) return;

        boolean over = spent > budget;
        int color = over ? Ui.WARN : Ui.BRAND;

        LinearLayout row = Ui.row(act);
        String left = over
                ? "已超预算 " + Money.yuan(spent - budget)
                : "预算 " + Money.yuan(budget) + " · 还剩 " + Money.yuan(budget - spent);
        row.addView(Ui.tv(act, left, 12, over ? Ui.WARN : Ui.FAINT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.tv(act, Math.round(spent * 100.0 / budget) + "%", 12,
                over ? Ui.WARN : Ui.FAINT));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.d(act, 12);
        budgetBox.addView(row, rlp);

        LinearLayout bar = Ui.bar(act, (float) spent / budget, color, 6);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 6));
        blp.topMargin = Ui.d(act, 7);
        budgetBox.addView(bar, blp);
    }
}
