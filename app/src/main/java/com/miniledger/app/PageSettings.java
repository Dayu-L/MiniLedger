package com.miniledger.app;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

class PageSettings extends Page {

    PageSettings(MainActivity a) {
        super(a);
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.d(a, 28));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private final LinearLayout col;

    @Override
    void refresh() {
        col.removeAllViews();

        LinearLayout top = Ui.row(act);
        top.setPadding(Ui.d(act, 20), Ui.d(act, 18), Ui.d(act, 20), 0);
        top.addView(Ui.tvBold(act, "设置", 20, Ui.INK));
        col.addView(top);

        boolean listener = NotifyListener.isEnabled(act);
        PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(act.getPackageName());

        // ---------------- 外观
        LinearLayout c0 = section("外观");
        c0.addView(skinRow());
        c0.addView(Ui.hLine(act));
        c0.addView(switchRow("深色模式",
                "晚上看不刺眼；玻璃和极简两套皮肤都有深色版",
                Skin.dark, (btn, v) -> act.applyDark(v)));

        // ---------------- 自动记账
        LinearLayout c1 = section("自动记账");
        c1.addView(item("通知使用权",
                "开启后简账才能读到微信 / 支付宝的付款通知",
                listener ? "已开启" : "去开启",
                listener ? Ui.IN : Ui.OUT,
                () -> {
                    try {
                        act.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Throwable t) {
                        act.toast("打不开设置页，请到 系统设置 → 通知 → 通知使用权 手动开启");
                    }
                }));
        c1.addView(Ui.hLine(act));
        c1.addView(item("电池优化白名单",
                "让系统不要在锁屏 / 待机时冻结简账",
                battery ? "已加入" : "去加入",
                battery ? Ui.IN : Ui.OUT,
                this::askBattery));
        c1.addView(Ui.hLine(act));
        c1.addView(item("自启动 / 后台管理",
                "国产 ROM 需要单独放行，否则清后台就断",
                "去设置",
                Ui.BRAND,
                this::openAutostart));
        c1.addView(Ui.hLine(act));

        boolean guard = Prefs.guardOn(act);
        c1.addView(switchRow("后台保活",
                "常驻通知 + 15 分钟自检 + 开机自启",
                guard, (btn, v) -> {
                    Prefs.setGuardOn(act, v);
                    if (v) {
                        GuardService.ensure(act);
                        GuardJob.schedule(act);
                        HeartbeatReceiver.schedule(act, GuardJob.PERIOD);
                    } else {
                        act.stopService(new Intent(act, GuardService.class));
                    }
                    refresh();
                }));

        // ---------------- 记账
        LinearLayout c2 = section("记账");
        c2.addView(switchRow("自动入账",
                "关掉后抓到的记录先进「待确认」，不会直接算进账本",
                Prefs.autoConfirm(act), (btn, v) -> {
                    Prefs.setAutoConfirm(act, v);
                    refresh();
                }));
        c2.addView(Ui.hLine(act));

        long budget = Prefs.budget(act);
        c2.addView(item(Prefs.cycleStart(act) > 0 ? "预算（按周期算）" : "月度预算",
                budget > 0 ? "花到 80% 和 100% 各提醒一次；18 点后超日均也会提一句"
                        : "设个数，超支前提醒你",
                budget > 0 ? Money.yuan(budget) : "未设置",
                budget > 0 ? Ui.INK : Ui.FAINT,
                this::askBudget));
        c2.addView(Ui.hLine(act));

        long cycleStart = Prefs.cycleStart(act);
        c2.addView(item("生活周期",
                cycleStart > 0
                        ? "从 " + Span.md(cycleStart) + " 起算，一个周期一结"
                        : "按领到生活费那天起算，单独一页按天看",
                cycleStart > 0 ? "已开启" : "去设置",
                cycleStart > 0 ? Ui.IN : Ui.BRAND,
                () -> act.openTab(1)));

        // ---------------- 备份与数据
        LinearLayout cb = section("备份与数据");
        cb.addView(item("自动备份",
                "每周一次，留最近 8 份，存在应用外部目录",
                "上次 " + Backup.lastLabel(act),
                Ui.INK,
                this::backupDialog));
        cb.addView(Ui.hLine(act));

        final List<Dupe.Group> dupes = Dupe.filter(
                Dupe.find(Db.get(act).done()), Prefs.dupIgnored(act));
        cb.addView(item("查找重复账单",
                "同一天、同金额、时间挨得近或同商户的，可能是一笔记了两次",
                dupes.isEmpty() ? "没有发现" : dupes.size() + " 组",
                dupes.isEmpty() ? Ui.FAINT : Ui.WARN,
                () -> showDupes(dupes)));

        cb.addView(Ui.hLine(act));
        cb.addView(item("导入账单 CSV",
                "微信 / 支付宝导出的账单，或简账自己导出的备份",
                "选择文件",
                Ui.BRAND,
                act::pickCsv));
        cb.addView(Ui.hLine(act));
        cb.addView(item("导出全部账单",
                "生成 CSV 放到 下载/简账 目录（卸载也留得住）",
                "导出",
                Ui.BRAND,
                () -> act.exportCsv()));

        // ---------------- 固定支出
        LinearLayout cr = section("固定支出");
        List<Recur> recurbills = Db.get(act).recurAll();
        if (recurbills.isEmpty()) {
            TextView none = Ui.tv(act, "房租、话费、订阅这种每月一次的，到日子会先落一笔进「待确认」",
                    12, Ui.FAINT);
            none.setLineSpacing(Ui.d(act, 3), 1f);
            none.setPadding(0, Ui.d(act, 8), 0, Ui.d(act, 10));
            cr.addView(none);
        } else {
            for (Recur r : recurbills) {
                cr.addView(item(r.title + "　每月 " + r.day + " 日",
                        Money.yuan(r.amount) + " · " + r.category
                                + (r.last.isEmpty() ? "" : " · 上次 " + r.last),
                        "编辑",
                        Ui.BRAND,
                        () -> recurDialog(r)));
                cr.addView(Ui.hLine(act));
            }
        }
        cr.addView(item("添加固定支出",
                "到日子不直接扣钱，先进「待确认」等你点头",
                "添加",
                Ui.BRAND,
                () -> recurDialog(null)));

        // ---------------- 大模型
        Llm.Cfg cfg = Prefs.llmCfg(act);
        boolean hasBase = cfg.baseUrl != null && !cfg.baseUrl.trim().isEmpty();
        boolean hasKey = cfg.apiKey != null && !cfg.apiKey.trim().isEmpty();

        LinearLayout cm = section("大模型（可选）");
        cm.addView(switchRow("用大模型理解口语",
                "关着完全离线；开着才会请求你填的接口，失败自动回落本地规则",
                Prefs.llmOn(act), (btn, v) -> {
                    Prefs.setLlmOn(act, v);
                    refresh();
                }));
        cm.addView(Ui.hLine(act));
        cm.addView(item("接口地址",
                "OpenAI 兼容即可，填到 /v1",
                hasBase ? shortUrl(cfg.baseUrl) : "未配置",
                hasBase ? Ui.INK : Ui.FAINT,
                () -> askText("接口地址", "https://api.deepseek.com/v1", cfg.baseUrl, s -> {
                    Llm.Cfg c = Prefs.llmCfg(act);
                    Prefs.setLlm(act, s, c.apiKey, c.model);
                    refresh();
                })));
        cm.addView(Ui.hLine(act));
        cm.addView(item("API Key",
                "只存在这台手机上，不会上传到别处",
                hasKey ? "已填" : "未填",
                hasKey ? Ui.INK : Ui.FAINT,
                () -> askText("API Key", "sk-…", cfg.apiKey, s -> {
                    Llm.Cfg c = Prefs.llmCfg(act);
                    Prefs.setLlm(act, c.baseUrl, s, c.model);
                    refresh();
                })));
        cm.addView(Ui.hLine(act));
        cm.addView(item("模型名", "默认 deepseek-chat", cfg.model, Ui.INK,
                () -> askText("模型名", "deepseek-chat", cfg.model, s -> {
                    Llm.Cfg c = Prefs.llmCfg(act);
                    Prefs.setLlm(act, c.baseUrl, c.apiKey, s);
                    refresh();
                })));
        cm.addView(Ui.hLine(act));
        cm.addView(item("测试连接", "发一句最短请求，验证通不通",
                "测一下", Ui.BRAND, this::testLlm));

        // ---------------- 保活说明
        LinearLayout c3 = section("保活检查清单");
        TextView tips = Ui.tv(act,
                "1. 通知使用权 —— 必须开，否则什么都抓不到\n" +
                "2. 电池优化白名单 —— 不开则锁屏半小时后可能收不到通知\n" +
                "3. 自启动 / 关联启动 —— 重启手机后自动恢复\n" +
                "4. 最近任务里把简账「锁定」或「加锁」，别一键清理\n" +
                "5. 允许简账发通知 —— 常驻通知被关了服务会被降级",
                12.5f, Ui.SUB);
        tips.setLineSpacing(Ui.d(act, 6), 1f);
        c3.addView(tips);

        TextView check = Ui.tv(act, "重新检查", 13, Ui.BRAND);
        check.setGravity(Gravity.CENTER);
        check.setPadding(0, Ui.d(act, 12), 0, Ui.d(act, 2));
        check.setOnClickListener(v -> refresh());
        c3.addView(check);

        // ---------------- 诊断
        if (Crash.has(act)) {
            LinearLayout cd = section("诊断");
            cd.addView(item("上次的崩溃日志",
                    "页面画崩了会把堆栈记在这里，方便回查",
                    "查看",
                    Ui.WARN,
                    this::showCrash));
        }

        // ---------------- 关于
        LinearLayout c4 = section("关于");
        c4.addView(item("桌面小组件",
                "长按桌面 → 小组件 → 简账；显示本周期剩余和今天还能花",
                "已内置",
                Ui.FAINT,
                null));
        c4.addView(Ui.hLine(act));
        TextView about = Ui.tv(act,
                "简账 " + versionName() + " · 纯本地记账，不联网、不上传。\n" +
                "账单存在这台手机的 SQLite 里；自动备份是兜底，\n" +
                "重要的那份请定期用「导出全部账单」放去下载目录。",
                12.5f, Ui.SUB);
        about.setLineSpacing(Ui.d(act, 5), 1f);
        c4.addView(about);
    }

    private LinearLayout section(String title) {
        LinearLayout card = Ui.card(act);
        card.setPadding(Ui.d(act, 18), Ui.d(act, 8), Ui.d(act, 18), Ui.d(act, 10));

        TextView head = Ui.tv(act, title, 12, Ui.FAINT);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(Ui.d(act, 20), Ui.d(act, 14), Ui.d(act, 20), Ui.d(act, 6));
        col.addView(head, hp);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(Ui.d(act, 16), 0, Ui.d(act, 16), 0);
        col.addView(card, cp);
        return card;
    }

    private View item(String title, String desc, String status, int statusColor, final Runnable onClick) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.d(act, 10), 0, Ui.d(act, 10));

        LinearLayout row = Ui.row(act);
        TextView t = Ui.tv(act, title, 14, Ui.INK);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView s = Ui.tv(act, status, 12.5f, statusColor);
        row.addView(s);
        box.addView(row);

        if (desc != null && !desc.isEmpty()) {
            TextView d = Ui.tv(act, desc, 12, Ui.FAINT);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = Ui.d(act, 4);
            box.addView(d, dlp);
        }

        box.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        box.setBackgroundColor(0x00000000);
        return box;
    }

    private View switchRow(String title, String desc, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, Ui.d(act, 8), 0, Ui.d(act, 8));

        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.tv(act, title, 14, Ui.INK));
        if (desc != null) {
            TextView d = Ui.tv(act, desc, 12, Ui.FAINT);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = Ui.d(act, 4);
            left.addView(d, dlp);
        }
        box.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(act);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        box.addView(sw);
        return box;
    }

    private View skinRow() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.d(act, 8), 0, Ui.d(act, 6));

        box.addView(Ui.tv(act, "界面风格", 14, Ui.INK));

        LinearLayout chips = Ui.row(act);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.d(act, 10);
        box.addView(chips, clp);

        chips.addView(skinChip("玻璃", true),
                new LinearLayout.LayoutParams(0, Ui.d(act, 36), 1f));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, Ui.d(act, 36), 1f);
        rlp.leftMargin = Ui.d(act, 8);
        chips.addView(skinChip("极简", false), rlp);

        TextView note = Ui.tv(act, Skin.glass
                        ? "半透明卡片浮在柔光上，卡片之间靠层次分开"
                        : "白面加一道细线，安静，看数字更专心",
                12, Ui.FAINT);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.d(act, 9);
        box.addView(note, nlp);
        return box;
    }

    private TextView skinChip(String name, boolean glass) {
        boolean on = Skin.glass == glass;
        TextView t = Ui.tv(act, name, 13, on ? Ui.ON_BRAND : Ui.INK);
        t.setGravity(Gravity.CENTER);
        t.setBackground((on ? Ui.round(Ui.BRAND, 10, act) : Ui.chipBgDrawable(act)));
        t.setOnClickListener(v -> {
            if (Skin.glass != glass) act.applySkin(glass);
        });
        return t;
    }

    private interface OnText {
        void ok(String s);
    }

    private void askText(String title, String hint, String current, final OnText cb) {
        EditText et = new EditText(act);
        et.setHint(hint);
        et.setText(current == null ? "" : current);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(Ui.d(act, 22), Ui.d(act, 14), Ui.d(act, 22), Ui.d(act, 6));
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> cb.ok(et.getText().toString().trim()))
                .show();
    }

    private static String shortUrl(String url) {
        if (url == null) return "";
        String s = url.replace("https://", "").replace("http://", "");
        s = s.replaceAll("/+$", "");
        return s.length() > 26 ? s.substring(0, 26) + "…" : s;
    }

    private void testLlm() {
        final Llm.Cfg cfg = Prefs.llmCfg(act);
        if (cfg.baseUrl == null || cfg.baseUrl.trim().isEmpty()) {
            act.toast("先填接口地址");
            return;
        }
        act.toast("正在测试…");
        new Thread(() -> {
            try {
                final String msg = Llm.ping(cfg);
                act.runOnUiThread(() -> act.toast("连接正常：" + msg));
            } catch (final Throwable e) {
                act.runOnUiThread(() -> act.toast("失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage())));
            }
        }).start();
    }

    /** 查重结果：先列组，再逐组处理。 */
    private void showDupes(final List<Dupe.Group> groups) {
        if (groups.isEmpty()) {
            act.toast("没有发现重复账单");
            return;
        }
        String[] titles = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            Dupe.Group g = groups.get(i);
            titles[i] = g.title() + (g.merchant().isEmpty() ? "" : " · " + g.merchant());
        }
        new AlertDialog.Builder(act)
                .setTitle("发现 " + groups.size() + " 组疑似重复")
                .setItems(titles, (d, i) -> dupeDialog(groups.get(i)))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void dupeDialog(final Dupe.Group g) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.items.size(); i++) {
            Tx t = g.items.get(i);
            sb.append(i + 1).append(") ").append(Span.hm(t.ts)).append("　")
                    .append(sourceLabel(t.source)).append(" · ")
                    .append(t.merchant.isEmpty() ? "未知" : t.merchant)
                    .append(" · ").append(t.category);
            if (!t.account.isEmpty()) sb.append(" · ").append(t.account);
            sb.append('\n');
        }
        sb.append("\n合并只保留最早那笔，其余删掉（删错了可以撤销）。");

        new AlertDialog.Builder(act)
                .setTitle(g.title())
                .setMessage(sb.toString().trim())
                .setNegativeButton("取消", null)
                .setNeutralButton("都保留", (d, w) -> {
                    Prefs.ignoreDup(act, g.signature());
                    refresh();
                    act.toast("以后不再提示这一组");
                })
                .setPositiveButton("合并", (d, w) -> {
                    final List<Tx> deleted = g.later();
                    for (Tx t : deleted) Db.get(act).del(t.id);
                    act.refreshAll();
                    refresh();
                    act.snack("已合并，删掉 " + deleted.size() + " 笔重复", "撤销", () -> {
                        for (Tx t : deleted) Db.get(act).restore(t);
                        act.refreshAll();
                        refresh();
                    });
                })
                .show();
    }

    private static String sourceLabel(String s) {
        if ("notify".equals(s)) return "通知";
        if ("csv".equals(s)) return "导入";
        if ("recur".equals(s)) return "固定支出";
        return "手动";
    }

    /** 从包里读版本号，别在文案里写死。 */
    private String versionName() {
        try {
            return act.getPackageManager()
                    .getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.1";
        }
    }

    private void showCrash() {
        new AlertDialog.Builder(act)
                .setTitle("崩溃日志")
                .setMessage(Crash.read(act))
                .setNegativeButton("清空", (d, w) -> {
                    Crash.clear(act);
                    refresh();
                })
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 备份：立即 / 恢复 / 看路径。 */
    private void backupDialog() {
        final String[] items = {
                "立即备份",
                Backup.latest(act) == null ? "还没有备份可恢复" : "从最近备份恢复",
                "备份放在哪"
        };
        new AlertDialog.Builder(act)
                .setTitle("备份与恢复")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        act.toast("正在备份…");
                        new Thread(() -> {
                            try {
                                final String m = Backup.run(act);
                                act.runOnUiThread(() -> {
                                    act.toast(m);
                                    refresh();
                                });
                            } catch (final Throwable t) {
                                act.runOnUiThread(() -> act.toast("备份失败：" + msg(t)));
                            }
                        }).start();
                    } else if (which == 1) {
                        if (Backup.latest(act) == null) {
                            act.toast("还没有备份文件");
                            return;
                        }
                        new AlertDialog.Builder(act)
                                .setTitle("恢复最近备份")
                                .setMessage("按单号去重，重复恢复不会产生重复账。\n\n"
                                        + "备份时间：" + Backup.lastLabel(act))
                                .setNegativeButton("取消", null)
                                .setPositiveButton("恢复", (dd, w) -> restore())
                                .show();
                    } else {
                        new AlertDialog.Builder(act)
                                .setTitle("备份目录")
                                .setMessage(Backup.dir(act).getAbsolutePath()
                                        + "\n\n共 " + Backup.count(act) + " 份，保留最近 8 份。")
                                .setPositiveButton("知道了", null)
                                .show();
                    }
                })
                .show();
    }

    private void restore() {
        act.toast("正在恢复…");
        new Thread(() -> {
            try {
                final CsvImport.Result r = Backup.restoreLatest(act);
                act.runOnUiThread(() -> {
                    act.toast(r.msg);
                    act.refreshAll();
                    refresh();
                });
            } catch (final Throwable t) {
                act.runOnUiThread(() -> act.toast("恢复失败：" + msg(t)));
            }
        }).start();
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? "未知错误" : t.getMessage();
    }

    /** 添加 / 编辑固定支出。 */
    private void recurDialog(final Recur old) {
        final Recur r = old == null ? new Recur() : old;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.d(act, 20), Ui.d(act, 4), Ui.d(act, 20), 0);

        final EditText title = input("名称，比如 房租 / 话费", r.title);
        box.addView(title);
        final EditText amount = input("金额", r.amount > 0 ? Money.fmt(r.amount).replace(",", "") : "");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(amount);
        final EditText day = input("每月几号（1-31，月末不够就落月末）", String.valueOf(r.day));
        day.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(day);

        final String[] cat = {r.category};
        final TextView catTv = Ui.tv(act, "分类　" + cat[0], 13, Ui.SUB);
        catTv.setPadding(Ui.d(act, 12), Ui.d(act, 10), Ui.d(act, 12), Ui.d(act, 10));
        catTv.setBackground(Ui.chipBgDrawable(act));
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catLp.topMargin = Ui.d(act, 10);
        box.addView(catTv, catLp);
        catTv.setOnClickListener(v -> {
            final String[] cats = Categories.of(Tx.OUT);
            new AlertDialog.Builder(act)
                    .setTitle("分类")
                    .setItems(cats, (d, w) -> {
                        cat[0] = cats[w];
                        catTv.setText("分类　" + cat[0]);
                    })
                    .show();
        });

        final AlertDialog.Builder bld = new AlertDialog.Builder(act)
                .setTitle(old == null ? "添加固定支出" : "编辑固定支出")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String t = title.getText().toString().trim();
                    long cents = Money.parse(amount.getText().toString());
                    int dd;
                    try {
                        dd = Integer.parseInt(day.getText().toString().trim());
                    } catch (Exception e) {
                        dd = 1;
                    }
                    if (t.isEmpty()) {
                        act.toast("给它起个名字");
                        return;
                    }
                    if (cents <= 0) {
                        act.toast("金额不对");
                        return;
                    }
                    r.title = t;
                    r.amount = cents;
                    r.day = Math.max(1, Math.min(31, dd));
                    r.category = cat[0];
                    Db.get(act).recurSave(r);
                    refresh();
                    Housekeeping.run(act);
                });
        if (old != null) {
            bld.setNeutralButton("删除", (d, w) -> {
                Db.get(act).recurDel(old.id);
                refresh();
                act.toast("已删除");
            });
        }
        bld.show();
    }

    private EditText input(String hint, String value) {
        EditText et = new EditText(act);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setPadding(Ui.d(act, 12), Ui.d(act, 10), Ui.d(act, 12), Ui.d(act, 10));
        et.setBackground(Ui.chipBgDrawable(act));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.d(act, 10);
        et.setLayoutParams(lp);
        return et;
    }

    private void askBudget() {
        EditText et = new EditText(act);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("3000");
        et.setPadding(Ui.d(act, 22), Ui.d(act, 14), Ui.d(act, 22), Ui.d(act, 6));
        long cur = Prefs.budget(act);
        if (cur > 0) et.setText(Money.fmt(cur).replace(",", ""));

        new AlertDialog.Builder(act)
                .setTitle("每月预算")
                .setView(et)
                .setNegativeButton("取消", null)
                .setNeutralButton(cur > 0 ? "清除" : "不设", (d, w) -> {
                    Prefs.setBudget(act, 0);
                    refresh();
                    act.refreshAll();
                })
                .setPositiveButton("保存", (d, w) -> {
                    long cents = Money.parse(et.getText().toString());
                    Prefs.setBudget(act, Math.max(0, cents));
                    refresh();
                    act.refreshAll();
                })
                .show();
    }

    private void askBattery() {        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
        } catch (Throwable t) {
            try {
                act.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                act.toast("请手动到 设置 → 电池 → 电池优化 里把简账设为不限制");
            }
        }
    }

    static final String[][] AUTOSTART = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"},
    };

    private void openAutostart() {
        for (String[] pair : AUTOSTART) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(pair[0], pair[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + act.getPackageName())));
        } catch (Throwable t) {
            act.toast("请手动到 系统设置 → 应用管理 → 简账 里允许自启动");
        }
    }
}
