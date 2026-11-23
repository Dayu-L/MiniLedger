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
        c2.addView(item("月度预算",
                budget > 0 ? "花到 80% 和 100% 各提醒一次" : "设个数，超支前提醒你",
                budget > 0 ? Money.yuan(budget) : "未设置",
                budget > 0 ? Ui.INK : Ui.FAINT,
                this::askBudget));
        c2.addView(Ui.hLine(act));
        c2.addView(item("导入账单 CSV",
                "微信 / 支付宝导出的账单，用来补历史",
                "选择文件",
                Ui.BRAND,
                act::pickCsv));
        c2.addView(Ui.hLine(act));
        c2.addView(item("导出全部账单",
                "生成 CSV 放到 下载/简账 目录",
                "导出",
                Ui.BRAND,
                () -> act.exportCsv()));

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

        // ---------------- 关于
        LinearLayout c4 = section("关于");
        TextView about = Ui.tv(act,
                "简账 1.0 · 纯本地记账，不联网、不上传。\n" +
                "所有账单只存在这台手机的 SQLite 里，卸载即清空。",
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
        TextView t = Ui.tv(act, name, 13, on ? 0xFFFFFFFF : Ui.INK);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(on ? Ui.BRAND : (Skin.glass ? 0x33FFFFFF : Ui.ROW), 10, act));
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
