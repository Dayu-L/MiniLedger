package com.miniledger.app;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 记一笔 / 编辑一笔。 */
final class AddDialog {

    private AddDialog() {}

    static void show(final MainActivity act, final Tx edit) {
        final Db db = Db.get(act);
        final int[] kind = {edit == null ? Tx.OUT : edit.kind};
        final String[] cat = {edit == null ? Categories.def(kind[0]) : edit.category};

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
            outBtn.setTextColor(isOut ? 0xFFFFFFFF : Ui.SUB);
            outBtn.setBackground(Ui.round(isOut ? Ui.BRAND : Ui.chipBg(act), 10, act));
            inBtn.setTextColor(!isOut ? 0xFFFFFFFF : Ui.SUB);
            inBtn.setBackground(Ui.round(!isOut ? Ui.BRAND : Ui.chipBg(act), 10, act));
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
                    if (edit == null) t.ts = System.currentTimeMillis();
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
                }));

        dlg.show();
    }

    private static TextView segItem(Context c, String s) {
        TextView t = Ui.tv(c, s, 14, Ui.SUB);
        t.setGravity(Gravity.CENTER);
        return t;
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
                    TextView chip = Ui.tv(c, name, 13, on ? 0xFFFFFFFF : Ui.INK);
                    chip.setGravity(Gravity.CENTER);
                    chip.setBackground(Ui.round(on ? Ui.BRAND : Ui.chipBg(c), 10, c));
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
