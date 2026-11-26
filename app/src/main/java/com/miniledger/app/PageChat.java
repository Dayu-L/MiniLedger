package com.miniledger.app;

import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 记账助手：说一句话 → 出草稿卡片 → 你点「记入」。 */
class PageChat extends Page {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final ScrollView sv;
    private final LinearLayout col;
    private final EditText input;
    private final TextView modeChip;
    private final ArrayList<Msg> msgs = new ArrayList<>();
    private boolean busy = false;

    PageChat(MainActivity a) {
        super(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));

        // 标题
        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.d(a, 20), Ui.d(a, 18), Ui.d(a, 20), 0);
        top.addView(Ui.tvBold(a, "助手", 20, Ui.INK),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        modeChip = Ui.tv(a, "", 11, Ui.BRAND);
        modeChip.setPadding(Ui.d(a, 9), Ui.d(a, 3), Ui.d(a, 9), Ui.d(a, 3));
        modeChip.setBackground(Ui.round(Ui.soft(Ui.BRAND), 8, a));
        top.addView(modeChip);
        root.addView(top);

        // 消息区
        sv = new ScrollView(a);
        sv.setFillViewport(true);
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.d(a, 16), Ui.d(a, 14), Ui.d(a, 16), Ui.d(a, 8));
        sv.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));

        // 快捷说法
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(a);
        chips.setPadding(Ui.d(a, 16), 0, Ui.d(a, 16), 0);
        hs.addView(chips, new HorizontalScrollView.LayoutParams(WRAP, WRAP));
        String[] quick = {"午饭 25", "打车 38", "昨天超市 120", "工资到账 8000", "这个月花了多少"};
        for (String q : quick) {
            TextView chip = Ui.tv(a, q, 12, Ui.SUB);
            chip.setPadding(Ui.d(a, 12), Ui.d(a, 6), Ui.d(a, 12), Ui.d(a, 6));
            chip.setBackground(Ui.chipBgDrawable(a, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = Ui.d(a, 8);
            chips.addView(chip, lp);
            chip.setOnClickListener(v -> send(q));
        }
        root.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));

        // 输入
        LinearLayout inputWrap = Ui.row(a);
        inputWrap.setBackground(Ui.round(Skin.glass
                ? (Skin.dark ? 0xCC1B2126 : 0xCCFFFFFF) : Ui.CARD, 14, a));
        if (Skin.glass) inputWrap.setBackground(Ui.roundStroke(
                Skin.dark ? 0xCC1B2126 : 0xCCFFFFFF, 14,
                Skin.dark ? 0x1FFFFFFF : 0x1F11151A, 1, a));
        else inputWrap.setBackground(Ui.roundStroke(Ui.CARD, 14, Ui.LINE, 1, a));
        inputWrap.setPadding(Ui.d(a, 14), Ui.d(a, 5), Ui.d(a, 5), Ui.d(a, 5));
        LinearLayout.LayoutParams iw = new LinearLayout.LayoutParams(MATCH, WRAP);
        iw.setMargins(Ui.d(a, 16), Ui.d(a, 10), Ui.d(a, 16), Ui.d(a, 10));
        root.addView(inputWrap, iw);

        input = new EditText(a);
        input.setHint("说一句，比如：午饭 25 微信");
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(null);
        input.setPadding(0, Ui.d(a, 6), 0, Ui.d(a, 6));
        inputWrap.addView(input, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView send = Ui.tv(a, "发送", 13, Ui.ON_BRAND);
        send.setGravity(Gravity.CENTER);
        send.setPadding(Ui.d(a, 16), Ui.d(a, 8), Ui.d(a, 16), Ui.d(a, 8));
        send.setBackground(Ui.round(Ui.BRAND, 10, a));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
        slp.leftMargin = Ui.d(a, 8);
        inputWrap.addView(send, slp);
        send.setOnClickListener(v -> send(input.getText().toString()));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send(input.getText().toString());
                return true;
            }
            return false;
        });

        // 开场白
        Msg hello = new Msg();
        hello.role = 1;
        hello.text = "说一句话就能记账。\n刚说「午饭 25 微信」「昨天打车 38」「工资到账 8000」都行。\n"
                + "我只记听清楚的，记错不了——每笔都要你点「记入」。";
        msgs.add(hello);

        render();
    }

    @Override
    void refresh() {
        Llm.Cfg cfg = Prefs.llmCfg(act);
        boolean llm = Prefs.llmOn(act) && cfg.baseUrl != null && !cfg.baseUrl.trim().isEmpty();
        modeChip.setText(llm ? "大模型" : "本地规则");
    }

    // ------------------------------------------------------------------ 消息

    static class Msg {
        int role;                     // 0 用户 / 1 助手
        String text = "";
        String tip = "";
        String mode = "local";
        boolean thinking = false;
        List<Tx> items = new ArrayList<>();
        List<Boolean> done = new ArrayList<>();

        static Msg user(String t) {
            Msg m = new Msg();
            m.role = 0;
            m.text = t;
            return m;
        }

        static Msg bot(Agent.Result r) {
            Msg m = new Msg();
            m.role = 1;
            m.text = r.text;
            m.tip = r.tip;
            m.mode = r.mode;
            m.items = r.items;
            for (int i = 0; i < r.items.size(); i++) m.done.add(false);
            return m;
        }
    }

    private void send(final String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || busy) return;
        input.setText("");
        msgs.add(Msg.user(text));
        render();

        final String intent = Agent.detectIntent(text);
        if (!"record".equals(intent)) {
            Msg m = new Msg();
            m.role = 1;
            m.text = ("edit".equals(intent) || "delete".equals(intent))
                    ? applyEdit(text)
                    : answer(intent, text);
            msgs.add(m);
            render();
            return;
        }

        Llm.Cfg cfg = Prefs.llmCfg(act);
        boolean useLlm = Prefs.llmOn(act) && cfg.baseUrl != null && !cfg.baseUrl.trim().isEmpty();

        if (!useLlm) {
            msgs.add(Msg.bot(Agent.local(text, System.currentTimeMillis())));
            render();
            return;
        }

        final Msg thinking = new Msg();
        thinking.role = 1;
        thinking.thinking = true;
        thinking.text = "…";
        msgs.add(thinking);
        render();
        busy = true;

        final Llm.Cfg fc = cfg;
        new Thread(() -> {
            Agent.Result r;
            String note = null;
            try {
                Db db = Db.get(act);
                long out = db.sumKind(Tx.OUT, Span.monthStart(0), Span.monthEnd(0));
                String sys = Agent.systemPrompt(today(), out, Prefs.budget(act), recentLines());
                r = Llm.respond(text, fc, sys, historyList(), System.currentTimeMillis());
            } catch (Throwable e) {
                r = Agent.local(text, System.currentTimeMillis());
                String why = e.getMessage() == null ? "连不上" : e.getMessage();
                note = "大模型没用上（" + why + "），这次走本地规则。";
            }
            final Agent.Result fr = r;
            final String fnote = note;
            act.runOnUiThread(() -> {
                msgs.remove(thinking);
                Msg b = Msg.bot(fr);
                if (fnote != null) b.tip = fnote;
                if (!fr.actions.isEmpty()) {
                    String done = applyActions(fr.actions);
                    if (!done.isEmpty()) {
                        b.text = (b.text == null || b.text.isEmpty() ? "" : b.text + "\n") + done;
                    }
                }
                msgs.add(b);
                busy = false;
                render();
            });
        }).start();
    }

    private List<String[]> historyList() {
        List<String[]> out = new ArrayList<>();
        int from = Math.max(0, msgs.size() - 7);
        for (int i = from; i < msgs.size(); i++) {
            Msg m = msgs.get(i);
            if (m.thinking || m.items.isEmpty() && m.role == 1) continue;
            out.add(new String[]{m.role == 0 ? "user" : "assistant", m.text});
        }
        return out;
    }

    private String recentLines() {
        List<Tx> all = Db.get(act).all();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = all.size() - 1; i >= 0 && n < 8; i--, n++) {
            Tx t = all.get(i);
            sb.append("  id=").append(t.id).append(' ').append(Span.md(t.ts)).append(' ')
                    .append(t.kind == Tx.IN ? "收入" : "支出").append(' ')
                    .append(Money.yuan(t.amount)).append(' ')
                    .append(t.category).append(' ').append(t.merchant).append('\n');
        }
        return sb.toString();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    // ------------------------------------------------------------------ 改账

    private String applyEdit(String text) {
        Db db = Db.get(act);
        List<Tx> all = db.all();
        if (all.isEmpty()) return "账本还是空的，先记一笔？";

        Agent.Edit e = Agent.detectEdit(text);
        if (e.kind == Agent.Edit.NONE) {
            return "要改哪一笔、改成什么？比如「把昨天打车那笔改成 45」。";
        }

        String norm = Agent.normalize(text);
        String probe = norm;
        if (e.end > e.start && e.end <= norm.length()) {
            probe = norm.substring(0, e.start) + " " + norm.substring(e.end);
        }

        Tx target = Agent.findTarget(probe, all, System.currentTimeMillis());
        if (target == null && (probe.contains("刚才") || probe.contains("上一笔")
                || probe.contains("最近") || probe.contains("最后"))) {
            target = all.get(all.size() - 1);
        }
        if (target == null) {
            return "没找到对得上的账。说具体点，比如「把昨天打车那笔改成 45」。";
        }

        String name = target.merchant == null || target.merchant.isEmpty()
                ? (target.category.isEmpty() ? "这笔" : target.category) : target.merchant;

        switch (e.kind) {
            case Agent.Edit.AMOUNT:
                target.amount = e.amount;
                db.update(target);
                break;
            case Agent.Edit.CATEGORY:
                target.category = e.category;
                db.update(target);
                break;
            case Agent.Edit.ACCOUNT:
                target.account = e.account;
                db.update(target);
                break;
            case Agent.Edit.DELETE:
                db.del(target.id);
                break;
            default:
                return "这句话我没听懂。";
        }
        act.refreshAll();

        switch (e.kind) {
            case Agent.Edit.DELETE:
                return "删掉了：" + name + " " + Money.yuan(target.amount)
                        + "（" + Span.friendly(target.ts) + "）。";
            case Agent.Edit.CATEGORY:
                return "改好了：" + name + " 归到「" + e.category + "」。";
            case Agent.Edit.ACCOUNT:
                return "改好了：" + name + " 换成「" + e.account + "」。";
            default:
                return "改好了：" + name + " → " + Money.yuan(e.amount)
                        + "（" + Span.friendly(target.ts) + "）。";
        }
    }

    private String applyActions(List<Agent.Action> actions) {
        Db db = Db.get(act);
        int n = 0;
        for (Agent.Action a : actions) {
            Tx t = db.byId(a.id);
            if (t == null) continue;
            if ("delete".equals(a.type)) {
                db.del(a.id);
                n++;
                continue;
            }
            if (a.amount != null) t.amount = a.amount;
            if (a.category != null && !a.category.isEmpty()) t.category = a.category;
            if (a.note != null && !a.note.isEmpty()) t.note = a.note;
            db.update(t);
            n++;
        }
        if (n > 0) act.refreshAll();
        return n == 0 ? "" : "已改动 " + n + " 笔。";
    }

    // ------------------------------------------------------------------ 查询

    private String answer(String intent, String text) {
        Db db = Db.get(act);
        long from = Span.monthStart(0);
        long to = Span.monthEnd(0);
        String label = "本月";

        if (text.contains("今天")) {
            from = Span.dayStart(0);
            to = Span.dayStart(1);
            label = "今天";
        } else if (text.contains("昨天")) {
            from = Span.dayStart(-1);
            to = Span.dayStart(0);
            label = "昨天";
        } else if (text.contains("上周")) {
            from = Span.dayStart(-7 - weekdayBack());
            to = from + 7L * Agent.DAY;
            label = "上周";
        }

        switch (intent) {
            case "spend": {
                long out = db.sumKind(Tx.OUT, from, to);
                List<String[]> cats = db.catStats(Tx.OUT, from, to);
                StringBuilder sb = new StringBuilder(label + "支出 " + Money.yuan(out) + "。");
                if (!cats.isEmpty()) {
                    sb.append("\n最多的是 ");
                    for (int i = 0; i < Math.min(3, cats.size()); i++) {
                        if (i > 0) sb.append("、");
                        sb.append(cats.get(i)[0]).append(" ").append(Money.yuan(Long.parseLong(cats.get(i)[1])));
                    }
                }
                return sb.toString();
            }
            case "budget": {
                long b = Prefs.budget(act);
                if (b <= 0) return "还没设预算。去「设置 → 月度预算」填一个数，花到 80% 和 100% 我会提醒。";
                long spent = db.sumKind(Tx.OUT, Span.monthStart(0), Span.monthEnd(0));
                int pct = (int) Math.round(spent * 100.0 / b);
                if (spent > b) return "本月已花 " + Money.yuan(spent) + "，超支 " + Money.yuan(spent - b) + "。";
                return "本月已花 " + Money.yuan(spent) + "，预算 " + Money.yuan(b)
                        + "，用了 " + pct + "%，还剩 " + Money.yuan(b - spent) + "。";
            }
            case "recent": {
                List<Tx> all = db.all();
                if (all.isEmpty()) return "还没有账。";
                StringBuilder sb = new StringBuilder("最近几笔：");
                int n = 0;
                for (int i = all.size() - 1; i >= 0 && n < 5; i--, n++) {
                    Tx t = all.get(i);
                    sb.append("\n").append(Span.md(t.ts)).append("  ")
                            .append(t.category.isEmpty() ? "未分类" : t.category).append("  ")
                            .append(t.kind == Tx.IN ? "+" : "−").append(Money.yuan(t.amount));
                }
                return sb.toString();
            }
            case "undo": {
                List<Tx> all = db.all();
                if (all.isEmpty()) return "还没有账可以删。";
                Tx t = all.get(all.size() - 1);
                db.del(t.id);
                act.refreshAll();
                return "删掉了一笔：" + t.category + " " + Money.yuan(t.amount)
                        + "（" + Span.md(t.ts) + "）。";
            }
            default:
                return "这句话我没听懂。";
        }
    }

    private int weekdayBack() {
        return (Span.cal().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    // ------------------------------------------------------------------ 渲染

    private int maxBubbleWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
    }

    private void render() {
        col.removeAllViews();
        for (Msg m : msgs) {
            View v = m.role == 0 ? userBubble(m) : botBubble(m);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = Ui.d(act, 10);
            col.addView(v, lp);
        }
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    private View userBubble(Msg m) {
        FrameLayout wrap = new FrameLayout(act);
        TextView t = Ui.tv(act, m.text, 14, Ui.FAB_FG);
        t.setLineSpacing(Ui.d(act, 3), 1f);
        t.setPadding(Ui.d(act, 14), Ui.d(act, 10), Ui.d(act, 14), Ui.d(act, 10));
        t.setBackground(Ui.round(Ui.FAB_BG, 16, act));
        t.setMaxWidth(maxBubbleWidth());
        wrap.addView(t, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END));
        return wrap;
    }

    private View botBubble(Msg m) {
        FrameLayout wrap = new FrameLayout(act);

        if (m.items.isEmpty()) {
            LinearLayout bx = new LinearLayout(act);
            bx.setOrientation(LinearLayout.VERTICAL);
            bx.setPadding(Ui.d(act, 14), Ui.d(act, 11), Ui.d(act, 14), Ui.d(act, 11));
            if (Skin.glass) {
                bx.setBackground(Ui.roundStroke(Skin.dark ? 0xCC1B2126 : 0x96FFFFFF, 16,
                        Skin.dark ? 0x1FFFFFFF : 0x1F11151A, 1, act));
                bx.setElevation(Ui.d(act, 3));
            } else {
                bx.setBackground(Ui.roundStroke(Ui.CARD, 16, Ui.LINE, 1, act));
            }

            TextView t = Ui.tv(act, m.text, 14, Ui.INK);
            t.setLineSpacing(Ui.d(act, 4), 1f);
            t.setMaxWidth(maxBubbleWidth());
            bx.addView(t);

            if (m.tip != null && !m.tip.isEmpty()) {
                TextView tip = Ui.tv(act, m.tip, 12, Ui.FAINT);
                tip.setLineSpacing(Ui.d(act, 3), 1f);
                tip.setMaxWidth(maxBubbleWidth());
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
                tlp.topMargin = Ui.d(act, 7);
                bx.addView(tip, tlp);
            }

            wrap.addView(bx, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START));
            return wrap;
        }

        LinearLayout box = Ui.card(act);
        box.setPadding(Ui.d(act, 14), Ui.d(act, 12), Ui.d(act, 14), Ui.d(act, 12));

        TextView head = Ui.tv(act, m.text, 14, Ui.INK);
        head.setLineSpacing(Ui.d(act, 4), 1f);
        box.addView(head);

        for (int i = 0; i < m.items.size(); i++) {
            box.addView(draftRow(m, i));
        }

        boolean anyLeft = false;
        for (boolean d : m.done) if (!d) anyLeft = true;
        if (m.items.size() > 1 && anyLeft) {
            TextView all = Ui.tv(act, "全部记入", 12, Ui.BRAND);
            all.setGravity(Gravity.CENTER);
            all.setPadding(0, Ui.d(act, 9), 0, Ui.d(act, 3));
            box.addView(all);
            all.setOnClickListener(v -> {
                for (int i = 0; i < m.items.size(); i++) commit(m, i, false);
                act.refreshAll();
                render();
            });
        }

        if (m.tip != null && !m.tip.isEmpty()) {
            TextView tip = Ui.tv(act, m.tip, 12, Ui.FAINT);
            tip.setLineSpacing(Ui.d(act, 3), 1f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
            tlp.topMargin = Ui.d(act, 8);
            box.addView(tip, tlp);
        }

        wrap.addView(box, new FrameLayout.LayoutParams(maxBubbleWidth(), WRAP, Gravity.START));
        return wrap;
    }

    private View draftRow(Msg m, final int idx) {
        final Tx t = m.items.get(idx);
        boolean committed = m.done.get(idx);

        LinearLayout row = Ui.row(act);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = Ui.d(act, 10);
        row.setLayoutParams(rlp);

        int color = Ui.catColor(t.category);
        TextView badge = Ui.tv(act, t.category.isEmpty() ? "?" : t.category.substring(0, 1),
                12, Ui.deep(color));
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(Ui.soft(color), 9, act));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.d(act, 30), Ui.d(act, 30)));

        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.d(act, 10), 0, Ui.d(act, 8), 0);
        String name = t.merchant == null || t.merchant.isEmpty()
                ? (t.category.isEmpty() ? "未分类" : t.category) : t.merchant;
        mid.addView(Ui.tv(act, name, 14, committed ? Ui.FAINT : Ui.INK));
        String sub = (t.category.isEmpty() ? "未分类" : t.category) + " · " + Span.friendly(t.ts);
        if (!t.account.isEmpty()) sub += " · " + t.account;
        mid.addView(Ui.tv(act, sub, 12, Ui.FAINT));
        row.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);

        TextView amt = Ui.tvBold(act, (t.kind == Tx.IN ? "+" : "−") + Money.yuan(t.amount), 15,
                committed ? Ui.FAINT : (t.kind == Tx.IN ? Ui.IN : Ui.INK));
        amt.setSingleLine(true);
        amt.setGravity(Gravity.END);
        right.addView(amt, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView btn = Ui.tv(act, committed ? "已记入" : "记入", 11,
                committed ? Ui.FAINT : Ui.BRAND);
        btn.setPadding(Ui.d(act, 10), Ui.d(act, 3), Ui.d(act, 10), Ui.d(act, 3));
        btn.setBackground(Ui.round(committed ? Ui.chipBg(act) : Ui.soft(Ui.BRAND), 8, act));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(WRAP, WRAP);
        blp.topMargin = Ui.d(act, 4);
        blp.gravity = Gravity.END;
        right.addView(btn, blp);

        row.addView(right, new LinearLayout.LayoutParams(Ui.d(act, 96), WRAP));

        if (!committed) {
            btn.setOnClickListener(v -> {
                commit(m, idx, true);
                act.refreshAll();
                render();
            });
        }
        return row;
    }

    private void commit(Msg m, int idx, boolean check) {
        if (check && m.done.get(idx)) return;
        if (m.done.get(idx)) return;
        long id = Db.get(act).insert(m.items.get(idx), true);
        m.done.set(idx, true);
        if (id == -2) {
            act.toast("刚记过一笔一样的，没有重复写入");
        } else if (id > 0) {
            Budget.check(act);
        }
    }
}
