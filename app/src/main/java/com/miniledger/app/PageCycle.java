package com.miniledger.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 生活周期（账期）：从「领到生活费」那天起算，一个周期一结。
 *
 * 单独一页，按天分组，日期精确到日：
 *   11月24日 · 周一   支出 ¥86.00
 *     食堂     餐饮 · 现金 · 12:10   -¥12.00
 *
 * 周期长度两种：按自然月对齐起始日（11月24日 → 12月24日），或固定 30 天。
 */
class PageCycle extends Page {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final LinearLayout setupCard;
    private final TextView setupHint;
    private final LinearLayout cycleCard;
    private final TextView settingsBtn;
    private final TextView prevTv;
    private final TextView nextTv;
    private final TextView rangeTv;
    private final TextView dayTv;
    private final TextView outTv;
    private final TextView inTv;
    private final TextView paceTv;
    private final TextView compareTv;
    private final TextView newCycleBtn;
    private final LinearLayout budgetBox;
    private final LinearLayout cardWrap;
    private final FrameLayout cardInner;
    private final ListView list;
    private final ArrayList<Tx> data = new ArrayList<>();
    private final DayAdapter adapter;
    private final TextView emptyTv;
    private int offset = 0;

    PageCycle(MainActivity a) {
        super(a);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        // ---- 顶栏
        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.d(a, 20), Ui.d(a, 14), Ui.d(a, 20), 0);
        top.addView(Ui.tvBold(a, "生活周期", 20, Ui.INK),
                new LinearLayout.LayoutParams(0, WRAP, 1f));

        settingsBtn = Ui.tv(a, "起始日", 12, Ui.SUB);
        settingsBtn.setGravity(Gravity.CENTER);
        settingsBtn.setPadding(Ui.d(a, 12), Ui.d(a, 8), Ui.d(a, 12), Ui.d(a, 8));
        settingsBtn.setBackground(Ui.chipBgDrawable(a));
        settingsBtn.setOnClickListener(v -> settings());
        top.addView(settingsBtn);
        col.addView(top);

        // ---- 还没设起始日：先引导
        setupCard = Ui.card(a);
        setupCard.setPadding(Ui.d(a, 18), Ui.d(a, 16), Ui.d(a, 18), Ui.d(a, 18));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        scLp.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), 0);
        col.addView(setupCard, scLp);

        setupCard.addView(Ui.tvBold(a, "先定一个账期起点", 15, Ui.INK));
        TextView desc = Ui.tv(a, "按「领到生活费」那天起算，一个周期一结。"
                + "默认按自然月对齐（11月24日 → 12月24日），也可以固定 30 天。", 12.5f, Ui.FAINT);
        desc.setLineSpacing(Ui.d(a, 3), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        descLp.topMargin = Ui.d(a, 8);
        setupCard.addView(desc, descLp);

        LinearLayout btns = Ui.row(a);
        btns.setPadding(0, Ui.d(a, 14), 0, 0);
        TextView startToday = Ui.tv(a, "从今天开始", 13, Ui.ON_BRAND);
        startToday.setGravity(Gravity.CENTER);
        startToday.setBackground(Ui.round(Ui.BRAND, 10, a));
        startToday.setOnClickListener(v -> {
            Prefs.setCycleStart(act, Span.dayStart(0));
            offset = 0;
            act.toast("账期从今天起算");
            act.refreshAll();
        });
        btns.addView(startToday, new LinearLayout.LayoutParams(0, Ui.d(a, 40), 1f));

        TextView pickDate = Ui.tv(a, "选个日期", 13, Ui.INK);
        pickDate.setGravity(Gravity.CENTER);
        pickDate.setBackground(Ui.chipBgDrawable(a));
        final long[] start = {Span.dayStart(0)};
        pickDate.setOnClickListener(v -> pickDate(start, () -> {
            Prefs.setCycleStart(act, start[0]);
            offset = 0;
            act.toast("账期从 " + Span.md(start[0]) + " 起算");
            act.refreshAll();
        }));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, Ui.d(a, 40), 1f);
        plp.leftMargin = Ui.d(a, 8);
        btns.addView(pickDate, plp);
        setupCard.addView(btns);

        setupHint = Ui.tv(a, "设好之后，这里会按天列出这个周期的每一笔。", 12.5f, Ui.FAINT);
        setupHint.setPadding(Ui.d(a, 24), Ui.d(a, 16), Ui.d(a, 24), 0);
        setupHint.setLineSpacing(Ui.d(a, 3), 1f);
        col.addView(setupHint, new LinearLayout.LayoutParams(MATCH, WRAP));

        // ---- 当前账期
        cycleCard = Ui.card(a);
        cycleCard.setPadding(Ui.d(a, 18), Ui.d(a, 6), Ui.d(a, 18), Ui.d(a, 16));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hp.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), 0);
        col.addView(cycleCard, hp);

        LinearLayout rangeRow = Ui.row(a);
        prevTv = arrow("‹");
        nextTv = arrow("›");
        rangeTv = Ui.tv(a, "", 13, Ui.SUB);
        rangeTv.setGravity(Gravity.CENTER);
        rangeRow.addView(prevTv, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
        rangeRow.addView(rangeTv, new LinearLayout.LayoutParams(0, WRAP, 1f));
        rangeRow.addView(nextTv, new LinearLayout.LayoutParams(Ui.d(a, 40), Ui.d(a, 34)));
        cycleCard.addView(rangeRow);

        dayTv = Ui.tv(a, "", 12, Ui.FAINT);
        LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        dtLp.topMargin = Ui.d(a, 8);
        cycleCard.addView(dayTv, dtLp);

        LinearLayout amtRow = Ui.row(a);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        arLp.topMargin = Ui.d(a, 10);
        cycleCard.addView(amtRow, arLp);

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.tv(a, "本周期支出", 12, Ui.FAINT));
        outTv = Ui.tvBold(a, "¥0.00", 26, Ui.OUT);
        LinearLayout.LayoutParams otl = new LinearLayout.LayoutParams(WRAP, WRAP);
        otl.topMargin = Ui.d(a, 2);
        left.addView(outTv, otl);
        amtRow.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.addView(Ui.tv(a, "本周期收入", 12, Ui.FAINT));
        inTv = Ui.tvBold(a, "¥0.00", 16, Ui.IN);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(WRAP, WRAP);
        ilp.topMargin = Ui.d(a, 6);
        right.addView(inTv, ilp);
        amtRow.addView(right);

        budgetBox = new LinearLayout(a);
        budgetBox.setOrientation(LinearLayout.VERTICAL);
        cycleCard.addView(budgetBox);

        paceTv = Ui.tv(a, "", 12, Ui.FAINT);
        LinearLayout.LayoutParams paceLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        paceLp.topMargin = Ui.d(a, 10);
        cycleCard.addView(paceTv, paceLp);

        compareTv = Ui.tv(a, "", 12, Ui.SUB);
        LinearLayout.LayoutParams cmpLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cmpLp.topMargin = Ui.d(a, 4);
        cycleCard.addView(compareTv, cmpLp);

        newCycleBtn = Ui.tv(a, "生活费到账了？从今天开新周期 ›", 12.5f, Ui.BRAND);
        newCycleBtn.setPadding(0, Ui.d(a, 12), 0, 0);
        newCycleBtn.setOnClickListener(v -> askNewCycle());
        cycleCard.addView(newCycleBtn, new LinearLayout.LayoutParams(MATCH, WRAP));

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

        // ---- 账目（按天分组）
        cardWrap = new LinearLayout(a);
        cardWrap.setOrientation(LinearLayout.VERTICAL);
        cardWrap.setBackground(Ui.round(Ui.CARD, 18, a));
        cardWrap.setClipToOutline(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        clp.setMargins(Ui.d(a, 16), Ui.d(a, 12), Ui.d(a, 16), Ui.d(a, 16));
        col.addView(cardWrap, clp);

        FrameLayout fl = new FrameLayout(a);
        cardInner = fl;
        cardWrap.addView(fl, new LinearLayout.LayoutParams(MATCH, MATCH));

        list = new ListView(a);
        list.setDivider(new ColorDrawable(Ui.LINE));
        list.setDividerHeight(1);
        list.setPadding(Ui.d(a, 16), 0, Ui.d(a, 16), Ui.d(a, 8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        adapter = new DayAdapter(a, data);
        list.setAdapter(adapter);
        fl.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));

        emptyTv = Ui.tv(a, "这个周期还没有账\n点「账单」右下角 ＋ 记一笔", 13, Ui.FAINT);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setLineSpacing(Ui.d(a, 4), 1f);
        fl.addView(emptyTv, new FrameLayout.LayoutParams(MATCH, MATCH));
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
    }

    private TextView arrow(String s) {
        TextView t = Ui.tv(act, s, 18, Ui.FAINT);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void menu(final Tx t) {
        new AlertDialog.Builder(act)
                .setTitle(t.merchant + "  " + Money.yuan(t.amount))
                .setItems(new String[]{"编辑 / 改日期", "删除这笔"}, (d, which) -> {
                    if (which == 0) {
                        AddDialog.show(act, t);
                    } else {
                        new AlertDialog.Builder(act)
                                .setMessage("删除这笔 " + Money.yuan(t.amount) + " ？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (dd, w) -> {
                                    Db.get(act).del(t.id);
                                    act.refreshAll();
                                    act.snack("已删除 " + Money.yuan(t.amount), "撤销", () -> {
                                        Db.get(act).restore(t);
                                        act.refreshAll();
                                    });
                                })
                                .show();
                    }
                })
                .show();
    }

    /** 生活费到了：直接以今天为新周期起点（账不动，只是重新切周期）。 */
    private void askNewCycle() {
        long today = Span.dayStart(0);
        long base = Prefs.cycleStart(act);
        if (today == base) {
            act.toast("今天就是本周期第一天");
            return;
        }
        new AlertDialog.Builder(act)
                .setTitle("从今天开新周期")
                .setMessage("账目不会动，只是把周期起点改成今天（"
                        + Span.ymd(today) + "）。\n\n上一段账还留在「全部时间」里。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开新周期", (d, w) -> {
                    Prefs.setCycleStart(act, today);
                    offset = 0;
                    act.refreshAll();
                    act.toast("新周期从今天开始");
                })
                .show();
    }

    // ---------------------------------------------------------------- 设置

    /** 起始日 + 周期长度。 */
    private void settings() {
        final long[] day = {Prefs.cycleStart(act)};
        final int[] len = {Prefs.cycleDays(act)};

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.d(act, 20), Ui.d(act, 4), Ui.d(act, 20), 0);

        LinearLayout dateRow = Ui.row(act);
        dateRow.addView(Ui.tv(act, "起始日", 13, Ui.SUB),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView dateTv = Ui.tv(act, "", 13, Ui.INK);
        dateTv.setPadding(Ui.d(act, 12), Ui.d(act, 8), Ui.d(act, 12), Ui.d(act, 8));
        dateTv.setBackground(Ui.chipBgDrawable(act));
        dateRow.addView(dateTv);
        LinearLayout.LayoutParams drl = new LinearLayout.LayoutParams(MATCH, WRAP);
        drl.topMargin = Ui.d(act, 8);
        box.addView(dateRow, drl);

        final Runnable paintDate = () -> dateTv.setText(
                day[0] > 0 ? Span.ymd(day[0]) + " " + Span.weekday(day[0]) : "未设置");
        paintDate.run();
        dateTv.setOnClickListener(v -> pickDate(day, paintDate));

        LinearLayout lenRow = Ui.row(act);
        LinearLayout.LayoutParams lrl = new LinearLayout.LayoutParams(MATCH, WRAP);
        lrl.topMargin = Ui.d(act, 14);
        box.addView(lenRow, lrl);
        lenRow.addView(Ui.tv(act, "周期长度", 13, Ui.SUB),
                new LinearLayout.LayoutParams(0, WRAP, 1f));

        final TextView monthChip = chip("自然月");
        final TextView daysChip = chip("30 天");
        final Runnable paintLen = () -> {
            boolean monthly = len[0] <= 0;
            monthChip.setTextColor(monthly ? Ui.ON_BRAND : Ui.SUB);
            monthChip.setBackground((monthly ? Ui.round(Ui.BRAND, 10, act) : Ui.chipBgDrawable(act)));
            daysChip.setTextColor(!monthly ? Ui.ON_BRAND : Ui.SUB);
            daysChip.setBackground((!monthly ? Ui.round(Ui.BRAND, 10, act) : Ui.chipBgDrawable(act)));
        };
        monthChip.setOnClickListener(v -> {
            len[0] = 0;
            paintLen.run();
        });
        daysChip.setOnClickListener(v -> {
            len[0] = 30;
            paintLen.run();
        });
        paintLen.run();
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.d(act, 64), Ui.d(act, 34));
        mlp.leftMargin = Ui.d(act, 8);
        lenRow.addView(monthChip, mlp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.d(act, 64), Ui.d(act, 34));
        dlp.leftMargin = Ui.d(act, 8);
        lenRow.addView(daysChip, dlp);

        TextView tip = Ui.tv(act, "从起始日算一个周期，结束后自动进入下一个周期。"
                + "换起始日只影响周期的划分，账目本身不动。", 12, Ui.FAINT);
        tip.setLineSpacing(Ui.d(act, 3), 1f);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tipLp.topMargin = Ui.d(act, 16);
        box.addView(tip, tipLp);

        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("账期设置")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (day[0] <= 0) {
                        act.toast("先选一个起始日");
                        return;
                    }
                    Prefs.setCycleStart(act, day[0]);
                    Prefs.setCycleDays(act, len[0]);
                    offset = 0;
                    dlg.dismiss();
                    act.refreshAll();
                }));

        dlg.show();
    }

    private TextView chip(String s) {
        TextView t = Ui.tv(act, s, 12.5f, Ui.SUB);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void pickDate(final long[] holder, final Runnable after) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(holder[0] > 0 ? holder[0] : Span.dayStart(0));
        new DatePickerDialog(act, (view, y, m, d) -> {
            holder[0] = dayOf(y, m, d);
            if (after != null) after.run();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static long dayOf(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(y, month, day, 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return Span.dayStartOf(c.getTimeInMillis());
    }

    // ---------------------------------------------------------------- 刷新

    @Override
    void refresh() {
        long base = Prefs.cycleStart(act);
        int len = Prefs.cycleDays(act);
        boolean on = base > 0;

        setupCard.setVisibility(on ? View.GONE : View.VISIBLE);
        setupHint.setVisibility(on ? View.GONE : View.VISIBLE);
        cycleCard.setVisibility(on ? View.VISIBLE : View.GONE);
        cardWrap.setVisibility(on ? View.VISIBLE : View.GONE);
        settingsBtn.setVisibility(on ? View.VISIBLE : View.GONE);

        if (!on) {
            data.clear();
            adapter.regroup();
            return;
        }

        long nowTs = System.currentTimeMillis();
        int nowIdx = Span.cycleIndex(base, len, nowTs);
        if (offset > 0) offset = 0;
        if (offset < -nowIdx) offset = -nowIdx;

        Span.Cycle cyc = Span.cycle(base, len, nowIdx + offset);
        if (cyc == null) return;

        int total = cyc.days();
        rangeTv.setText(Span.rangeLabel(cyc.start, cyc.end));
        prevTv.setAlpha(nowIdx + offset <= 0 ? 0.3f : 1f);
        nextTv.setAlpha(offset < 0 ? 1f : 0.3f);

        int elapsed;
        if (nowTs < cyc.start) elapsed = 0;
        else if (nowTs >= cyc.end) elapsed = total;
        else elapsed = Math.min(total, Span.dayDiff(cyc.start, nowTs) + 1);

        if (nowTs < cyc.start) {
            dayTv.setText("还没开始 · 共 " + total + " 天");
        } else if (nowTs >= cyc.end) {
            dayTv.setText("已结束 · 共 " + total + " 天");
        } else {
            dayTv.setText("第 " + elapsed + " 天 / 共 " + total + " 天 · 还剩 "
                    + (total - elapsed) + " 天");
        }

        Db db = Db.get(act);
        long out = db.sumKind(Tx.OUT, cyc.start, cyc.end);
        long in = db.sumKind(Tx.IN, cyc.start, cyc.end);
        outTv.setText(Money.yuan(out));
        inTv.setText("+" + Money.yuan(in));

        long budget = Prefs.budget(act);
        refreshBudget(out, budget);

        long leftDays = Math.max(0, total - elapsed);
        String pace = elapsed > 0 ? "日均 " + Money.yuan(out / elapsed) : "还没开始花钱";
        if (budget > 0 && nowTs < cyc.end) {
            long perDay = Math.max(0, budget - out) / Math.max(1, leftDays);
            pace += "　·　按预算每天还能花 " + Money.yuan(perDay);
        }
        paceTv.setText(pace);

        // 环比上一个周期
        if (cyc.index > 0) {
            Span.Cycle prev = Span.cycle(base, len, cyc.index - 1);
            if (prev != null) {
                long prevOut = db.sumKind(Tx.OUT, prev.start, prev.end);
                long diff = out - prevOut;
                if (prevOut <= 0) {
                    compareTv.setText("上个周期没有支出记录");
                } else {
                    int pct = (int) Math.round(diff * 100.0 / prevOut);
                    String verb = diff == 0 ? "和上个周期一样"
                            : diff < 0 ? "比上个周期少花 " + Money.yuan(-diff)
                            : "比上个周期多花 " + Money.yuan(diff);
                    String arrow = diff == 0 ? "" : (diff < 0 ? " ↓" : " ↑");
                    compareTv.setText(verb + (diff == 0 ? "" : "（" + Math.abs(pct) + "%" + arrow + "）"));
                }
                compareTv.setVisibility(View.VISIBLE);
            } else {
                compareTv.setVisibility(View.GONE);
            }
        } else {
            compareTv.setVisibility(View.GONE);
        }

        boolean atCurrent = offset == 0;
        newCycleBtn.setVisibility(atCurrent && cyc.start != Span.dayStart(0)
                ? View.VISIBLE : View.GONE);

        List<Tx> l = db.bills(cyc.start, cyc.end);
        data.clear();
        data.addAll(l);
        adapter.regroup();
        list.setSelection(0);
        emptyTv.setText("这个周期还没有账\n点「账单」右下角 ＋ 记一笔");
    }

    private void refreshBudget(long spent, long budget) {
        budgetBox.removeAllViews();
        if (budget <= 0) return;

        boolean over = spent > budget;
        int color = over ? Ui.WARN : Ui.BRAND;

        LinearLayout row = Ui.row(act);
        String left = over
                ? "已超预算 " + Money.yuan(spent - budget)
                : "预算 " + Money.yuan(budget) + " · 还剩 " + Money.yuan(budget - spent);
        row.addView(Ui.tv(act, left, 12, over ? Ui.WARN : Ui.FAINT),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(Ui.tv(act, Math.round(spent * 100.0 / budget) + "%", 12,
                over ? Ui.WARN : Ui.FAINT));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = Ui.d(act, 12);
        budgetBox.addView(row, rlp);

        LinearLayout bar = Ui.bar(act, (float) spent / budget, color, 6);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, Ui.d(act, 6));
        blp.topMargin = Ui.d(act, 7);
        budgetBox.addView(bar, blp);
    }
}
