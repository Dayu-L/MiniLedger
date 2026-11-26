package com.miniledger.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** 记一笔 / 编辑一笔。 */
final class AddDialog {

    private AddDialog() {}

    static void show(final MainActivity act, final Tx edit) {
        final Db db = Db.get(act);
        final int[] kind = {edit == null ? Tx.OUT : edit.kind};
        final String[] cat = {edit == null ? Categories.def(kind[0]) : edit.category};
        // 时间戳精确到日：只改日期，时:分沿用原来那一刻
        final long[] ts = {edit == null ? System.currentTimeMillis() : edit.ts};

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.d(act, 22), Ui.d(act, 4), Ui.d(act, 22), 0);

        final EditText amt = new EditText(act);
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amt.setTextSize(32);
        amt.setGravity(Gravity.CENTER);
        amt.setHint("0.00");
        amt.setBackground(null);
        amt.setPadding(0, Ui.d(act, 8), 0, Ui.d(act, 12));
        if (edit != null) amt.setText(Money.fmt(edit.amount).replace(",", ""));
        box.addView(amt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 收支切换
        final LinearLayout seg = new LinearLayout(act);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        final TextView outBtn = segItem(act, "支出");
        final TextView inBtn = segItem(act, "收入");
        seg.addView(outBtn, new LinearLayout.LayoutParams(0, Ui.d(act, 36), 1f));
        seg.addView(inBtn, new LinearLayout.LayoutParams(0, Ui.d(act, 36), 1f));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = Ui.d(act, 4);
        box.addView(seg, segLp);

        // ---- 分类
        final LinearLayout grid = new LinearLayout(act);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = Ui.d(act, 10);
        box.addView(grid, gridLp);

        // ---- 日期（精确到日）
        final LinearLayout dateRow = new LinearLayout(act);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams drLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.d(act, 40));
        drLp.topMargin = Ui.d(act, 14);
        box.addView(dateRow, drLp);

        final TextView dateTv = Ui.tv(act, "", 13.5f, Ui.INK);
        dateTv.setSingleLine(true);
        dateTv.setEllipsize(TextUtils.TruncateAt.END);
        dateTv.setGravity(Gravity.CENTER_VERTICAL);
        dateTv.setPadding(Ui.d(act, 12), 0, Ui.d(act, 12), 0);
        dateTv.setBackground(Ui.chipBgDrawable(act));
        dateRow.addView(dateTv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        final Runnable paintDate = () -> dateTv.setText(
                Span.ymd(ts[0]) + " " + Span.weekday(ts[0])
                        + (ts[0] >= Span.dayStart(0) ? " · 今天"
                        : ts[0] >= Span.dayStart(-1) ? " · 昨天" : ""));
        paintDate.run();

        dateTv.setOnClickListener(v -> pickDate(act, ts, paintDate));

        dateRow.addView(quickDay(act, "今天", ts, 0, paintDate),
                quickLp(act));
        dateRow.addView(quickDay(act, "昨天", ts, -1, paintDate),
                quickLp(act));

        // ---- 备注
        final EditText note = new EditText(act);
        note.setHint("备注（选填）");
        note.setTextSize(14);
        note.setSingleLine(true);
        note.setBackground(null);
        note.setText(edit == null ? "" : edit.note);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = Ui.d(act, 12);
        box.addView(note, noteLp);

        final Runnable paint = () -> {
            boolean isOut = kind[0] == Tx.OUT;
            outBtn.setTextColor(isOut ? Ui.ON_BRAND : Ui.SUB);
            outBtn.setBackground((isOut ? Ui.round(Ui.BRAND, 10, act) : Ui.chipBgDrawable(act)));
            inBtn.setTextColor(!isOut ? Ui.ON_BRAND : Ui.SUB);
            inBtn.setBackground((!isOut ? Ui.round(Ui.BRAND, 10, act) : Ui.chipBgDrawable(act)));
        };

        final Runnable refill = () -> fillGrid(act, grid, kind[0], cat);

        outBtn.setOnClickListener(v -> {
            if (kind[0] == Tx.OUT) return;
            kind[0] = Tx.OUT;
            cat[0] = Categories.def(Tx.OUT);
            paint.run();
            refill.run();
        });
        inBtn.setOnClickListener(v -> {
            if (kind[0] == Tx.IN) return;
            kind[0] = Tx.IN;
            cat[0] = Categories.def(Tx.IN);
            paint.run();
            refill.run();
        });

        paint.run();
        refill.run();

        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle(edit == null ? "记一笔" : "编辑")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long cents = Money.parse(amt.getText().toString());
                    if (cents <= 0) {
                        amt.setError("请输入有效金额");
                        return;
                    }
                    Tx t = edit != null ? edit : new Tx();
                    t.ts = ts[0];
                    t.kind = kind[0];
                    t.amount = cents;
                    t.category = (cat[0] == null || cat[0].isEmpty())
                            ? Categories.def(kind[0]) : cat[0];
                    t.note = note.getText().toString().trim();
                    if (edit == null) {
                        t.source = "manual";
                        t.account = "现金";
                        t.status = Tx.DONE;
                        db.insert(t, true);
                    } else {
                        db.update(t);
                    }
                    Budget.check(act);
                    dlg.dismiss();
                    act.refreshAll();
                    maybeNewCycle(act, t);
                }));

        dlg.show();
    }

    /**
     * 记了一笔收入（工资 / 生活费 / 红包这种），问一句要不要把账期起点挪到这天。
     * 退款、理财收益不打扰。
     */
    private static void maybeNewCycle(final MainActivity act, final Tx t) {
        if (t.kind != Tx.IN) return;
        String cat = t.category == null ? "" : t.category;
        if (cat.equals("退款") || cat.equals("理财")) return;

        long base = Prefs.cycleStart(act);
        if (base <= 0) return;
        final long day = Span.dayStartOf(t.ts);
        if (day <= base) return;

        new AlertDialog.Builder(act)
                .setTitle("以这天为新账期起点？")
                .setMessage("记了一笔收入 " + Money.yuan(t.amount) + "。\n\n"
                        + "把账期起点改成 " + Span.ymd(day) + "？账目不会动，只是重新切周期。")
                .setNegativeButton("不用", null)
                .setPositiveButton("开新周期", (d, w) -> {
                    Prefs.setCycleStart(act, day);
                    act.refreshAll();
                    act.toast("新周期从 " + Span.md(day) + " 开始");
                })
                .show();
    }

    private static TextView segItem(Context c, String s) {
        TextView t = Ui.tv(c, s, 14, Ui.SUB);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static LinearLayout.LayoutParams quickLp(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                Ui.d(c, 48), Ui.d(c, 40));
        p.leftMargin = Ui.d(c, 6);
        return p;
    }

    /** 「今天 / 昨天」快捷按钮。 */
    private static TextView quickDay(Context c, String s, final long[] ts, final int dayOffset,
                                     final Runnable after) {
        TextView t = Ui.tv(c, s, 12.5f, Ui.SUB);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.chipBgDrawable(c));
        t.setOnClickListener(v -> {
            long keep = ts[0];
            long target = Span.dayStart(dayOffset);
            // 保留原来的时:分
            ts[0] = target + (keep - Span.dayStartOf(keep));
            after.run();
        });
        return t;
    }

    /** 选日期：只改年月日，时分不动。 */
    private static void pickDate(final Context c, final long[] ts, final Runnable after) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(ts[0]);
        DatePickerDialog d = new DatePickerDialog(c, (view, y, m, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(ts[0]);
            picked.set(Calendar.YEAR, y);
            picked.set(Calendar.MONTH, m);
            picked.set(Calendar.DAY_OF_MONTH, day);
            ts[0] = picked.getTimeInMillis();
            if (after != null) after.run();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private static void fillGrid(Context c, LinearLayout grid, int kind, String[] sel) {
        grid.removeAllViews();
        String[] cats = Categories.of(kind);
        int rows = (cats.length + 3) / 4;
        for (int i = 0; i < rows; i++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 4; j++) {
                int idx = i * 4 + j;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.d(c, 36), 1f);
                p.setMargins(Ui.d(c, 3), Ui.d(c, 3), Ui.d(c, 3), Ui.d(c, 3));
                if (idx < cats.length) {
                    final String name = cats[idx];
                    boolean on = name.equals(sel[0]);
                    TextView chip = Ui.tv(c, name, 13, on ? Ui.ON_BRAND : Ui.INK);
                    chip.setGravity(Gravity.CENTER);
                    chip.setBackground((on ? Ui.round(Ui.BRAND, 10, c) : Ui.chipBgDrawable(c)));
                    chip.setOnClickListener(v -> {
                        sel[0] = name;
                        fillGrid(c, grid, kind, sel);
                    });
                    row.addView(chip, p);
                } else {
                    row.addView(new View(c), p);
                }
            }
            grid.addView(row);
        }
    }
}
