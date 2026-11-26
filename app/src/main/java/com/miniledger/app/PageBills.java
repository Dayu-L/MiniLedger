package com.miniledger.app;

import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class PageBills extends Page {

    private final ListView list;
    private final ArrayList<Tx> data = new ArrayList<>();
    private final DayAdapter adapter;
    private final TextView monthTv;
    private final TextView outTv;
    private final TextView inTv;
    private final TextView statusTv;
    private final LinearLayout budgetBox;
    private final LinearLayout cardWrap;
    private final FrameLayout cardInner;
    private final LinearLayout filterBar;
    private final EditText kwEt;
    private final EditText minEt;
    private final EditText maxEt;
    private final TextView prevTv;
    private final TextView nextTv;
    private final TextView emptyTv;
    private int offset = 0;
    private String cat = "";
    private String catShown = "";

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

        TextView searchBtn = Ui.tv(a, "搜索", 12, Ui.SUB);
        searchBtn.setPadding(Ui.d(a, 10), Ui.d(a, 5), Ui.d(a, 10), Ui.d(a, 5));
        searchBtn.setBackground(Ui.chipBgDrawable(a));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.leftMargin = Ui.d(a, 10);
        top.addView(searchBtn, sbLp);
        col.addView(top);

        // ---- 搜索条（默认收起）
        filterBar = new LinearLayout(a);
        filterBar.setOrientation(LinearLayout.VERTICAL);
        filterBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams fbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fbLp.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), 0);
        col.addView(filterBar, fbLp);

        LinearLayout fRow = Ui.row(a);
        kwEt = new EditText(a);
        kwEt.setHint("搜商户 / 备注 / 分类");
        kwEt.setTextSize(14);
        kwEt.setSingleLine(true);
        kwEt.setBackground(Ui.chipBgDrawable(a));
        kwEt.setPadding(Ui.d(a, 12), Ui.d(a, 6), Ui.d(a, 12), Ui.d(a, 6));
        fRow.addView(kwEt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        minEt = amountEt(a, "金额≥");
        maxEt = amountEt(a, "≤");
        fRow.addView(minEt, new LinearLayout.LayoutParams(Ui.d(a, 76),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        fRow.addView(maxEt, new LinearLayout.LayoutParams(Ui.d(a, 66),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        filterBar.addView(fRow);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.d(a, 8), 0, 0);
        for (String c : allCats()) {
            chips.addView(catChip(a, chips, c));
        }
        hs.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        filterBar.addView(hs);

        searchBtn.setOnClickListener(v -> {
            boolean show = filterBar.getVisibility() != View.VISIBLE;
            filterBar.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) kwEt.requestFocus();
            else clearFilter();
            refresh();
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (filterBar.getVisibility() == View.VISIBLE) refresh();
            }
        };
        kwEt.addTextChangedListener(watcher);
        minEt.addTextChangedListener(watcher);
        maxEt.addTextChangedListener(watcher);

        // ---- 月度卡片
        LinearLayout head = Ui.card(a);
        head.setPadding(Ui.d(a, 18), Ui.d(a, 6), Ui.d(a, 18), Ui.d(a, 16));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), 0);
        col.addView(head, hp);

        LinearLayout monthRow = Ui.row(a);
        prevTv = arrow("‹");
        nextTv = arrow("›");
        monthTv = Ui.tv(a, "", 13, Ui.SUB);
        monthTv.setGravity(Gravity.CENTER);
        monthRow.addView(prevTv, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
        monthRow.addView(monthTv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        monthRow.addView(nextTv, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
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

        prevTv.setOnClickListener(v -> {
            offset--;
            refresh();
        });
        nextTv.setOnClickListener(v -> {
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
        adapter = new DayAdapter(a, data);
        list.setAdapter(adapter);
        fl.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyTv = Ui.tv(a, "这个月还没有账\n点右下角 ＋ 记一笔，或去「设置」导入账单", 13, Ui.FAINT);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setLineSpacing(Ui.d(a, 4), 1f);
        fl.addView(emptyTv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(emptyTv);

        list.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = adapter.item(pos);
            if (o instanceof Tx) AddDialog.show(act, (Tx) o);
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            Object o = adapter.item(pos);
            if (o instanceof Tx) menu((Tx) o);
            return o instanceof Tx;
        });

        // ---- 悬浮按钮
        TextView fab = Ui.tv(a, "＋", 26, Ui.FAB_FG);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.circle(Ui.FAB_BG, a));
        fab.setElevation(Ui.d(a, 10));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                Ui.d(a, 56), Ui.d(a, 56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.d(a, 24), Ui.d(a, 24));
        addView(fab, fp);
        fab.setOnClickListener(v -> AddDialog.show(act, null));
    }

    private EditText amountEt(MainActivity a, String hint) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setTextSize(13);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setBackground(Ui.chipBgDrawable(a));
        et.setPadding(Ui.d(a, 8), Ui.d(a, 6), Ui.d(a, 8), Ui.d(a, 6));
        et.setTextColor(Ui.INK);
        et.setHintTextColor(Ui.FAINT);
        return et;
    }

    private static List<String> allCats() {
        List<String> l = new ArrayList<>();
        l.add("全部");
        for (String s : Categories.OUT) l.add(s);
        for (String s : Categories.IN) l.add(s);
        return l;
    }

    private TextView catChip(MainActivity a, final LinearLayout group, final String name) {
        final boolean on = name.equals(catShown.isEmpty() ? "全部" : catShown);
        TextView t = Ui.tv(a, name, 12.5f, on ? Ui.ON_BRAND : Ui.SUB);
        t.setPadding(Ui.d(a, 12), Ui.d(a, 6), Ui.d(a, 12), Ui.d(a, 6));
        t.setBackground((on ? Ui.round(Ui.BRAND, 10, a) : Ui.chipBgDrawable(a)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.d(a, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            catShown = "全部".equals(name) ? "" : name;
            cat = catShown;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView c = (TextView) group.getChildAt(i);
                boolean sel = c.getText().toString().equals(name);
                c.setTextColor(sel ? Ui.ON_BRAND : Ui.SUB);
                c.setBackground((sel ? Ui.round(Ui.BRAND, 10, a) : Ui.chipBgDrawable(a)));
            }
            refresh();
        });
        return t;
    }

    private TextView arrow(String s) {
        TextView t = Ui.tv(act, s, 18, Ui.FAINT);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private boolean filtering() {
        if (filterBar.getVisibility() != View.VISIBLE) return false;
        return !kwEt.getText().toString().trim().isEmpty()
                || !cat.isEmpty()
                || !minEt.getText().toString().trim().isEmpty()
                || !maxEt.getText().toString().trim().isEmpty();
    }

    private void clearFilter() {
        kwEt.setText("");
        minEt.setText("");
        maxEt.setText("");
        cat = "";
        catShown = "";
    }

    private void menu(final Tx t) {
        String[] items = {"编辑金额 / 分类 / 日期", "删除这笔", "查看原始通知"};
        new AlertDialog.Builder(act)
                .setTitle(t.merchant + "  " + Money.yuan(t.amount))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        AddDialog.show(act, t);
                    } else if (which == 1) {
                        new AlertDialog.Builder(act)
                                .setMessage("删除这笔 " + Money.yuan(t.amount) + " ？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (dd, w) -> delete(t))
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

    private void delete(final Tx t) {
        Db.get(act).del(t.id);
        refresh();
        act.refreshAll();
        act.snack("已删除 " + Money.yuan(t.amount), "撤销", () -> {
            Db.get(act).restore(t);
            act.refreshAll();
        });
    }

    @Override
    void refresh() {
        Db db = Db.get(act);
        boolean filtering = filtering();

        long out;
        long in;
        List<Tx> l;

        if (filtering) {
            long min = Money.parse(minEt.getText().toString());
            long max = Money.parse(maxEt.getText().toString());
            l = db.search(kwEt.getText().toString(), cat, Math.max(0, min), Math.max(0, max));
            long o = 0, i = 0;
            for (Tx t : l) {
                if (t.kind == Tx.IN) i += t.amount;
                else o += t.amount;
            }
            out = o;
            in = i;
            monthTv.setText("全部时间 · " + l.size() + " 笔");
            prevTv.setVisibility(View.INVISIBLE);
            nextTv.setVisibility(View.INVISIBLE);
            budgetBox.removeAllViews();
        } else {
            long from = Span.monthStart(offset);
            long to = Span.monthEnd(offset);
            monthTv.setText(Span.monthLabel(offset));
            prevTv.setVisibility(View.VISIBLE);
            nextTv.setVisibility(View.VISIBLE);
            out = db.sumKind(Tx.OUT, from, to);
            in = db.sumKind(Tx.IN, from, to);
            l = db.bills(from, to);
            refreshBudget(out);
        }

        outTv.setText(Money.yuan(out));
        inTv.setText("+" + Money.yuan(in));

        data.clear();
        data.addAll(l);
        adapter.regroup();
        list.setSelection(0);
        resizeCard();

        emptyTv.setText(filtering
                ? "没有匹配的账单\n换个关键词，或者点「搜索」关掉筛选"
                : "这个月还没有账\n点右下角 ＋ 记一笔，或去「设置」导入账单");

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

    /** 预算条只在「本月」显示，看历史月份和搜索结果时没有意义。 */
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
        if (Prefs.cycleStart(act) > 0) left += "（月度口径）";
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
