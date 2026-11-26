package com.miniledger.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_CSV = 41;
    private static final int REQ_NOTIF = 42;
    private static final String[] NAMES = {"账单", "周期", "助手", "待确认", "统计", "设置"};
    private static final int TAB_BILLS = 0;
    private static final int TAB_CYCLE = 1;
    private static final int TAB_PENDING = 3;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private FrameLayout root;
    private FrameLayout body;
    private Page[] pages;
    private TextView[] navLabels;
    private LinearLayout navBar;
    private int current = -1;
    private int insetBottom = 0;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private View snackView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Skin.load(this);
        setTheme(Skin.dark ? R.style.AppThemeDark : R.style.AppTheme);

        Notifier.setup(this);
        buildUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        int tab = savedInstanceState != null ? savedInstanceState.getInt("tab", 0)
                : getIntent().getIntExtra("tab", 0);
        switchTo(Math.max(0, Math.min(pages.length - 1, tab)));

        if (Prefs.guardOn(this)) {
            GuardService.ensure(this);
            GuardJob.schedule(this);
            HeartbeatReceiver.schedule(this, GuardJob.PERIOD);
        }

        Housekeeping.run(this);
        Housekeeping.background(this);

        if (!NotifyListener.isEnabled(this) && !Prefs.guideShown(this)) {
            Prefs.setGuideShown(this);
            new AlertDialog.Builder(this)
                    .setTitle("先开一个权限")
                    .setMessage("简账靠「通知使用权」读微信 / 支付宝的付款通知。\n\n"
                            + "系统会问「允许读取所有通知」——这是 Android 上唯一的授权方式。"
                            + "简账只解析带金额的支付消息，其余通知读完即丢，也不联网上传。\n\n"
                            + "顺手再把「电池优化白名单」和「自启动」开了，锁屏后也不会断。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("去开启", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                        } catch (Throwable t) {
                            toast("请到 系统设置 → 通知 → 通知使用权 手动开启");
                        }
                    })
                    .show();
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        setContentView(root);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));

        if (Skin.glass) {
            root.addView(new Aurora(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            root.setBackgroundColor(Ui.BG);
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));
        applyInsets(content);

        body = new FrameLayout(this);
        content.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(Ui.surface(this));
        navBar.setElevation(Skin.glass ? Ui.d(this, 12) : 0);
        navBar.setPadding(0, Ui.d(this, 2), 0, Ui.d(this, 2));
        content.addView(navBar, new LinearLayout.LayoutParams(MATCH, Ui.d(this, 58)));

        pages = new Page[]{
                new PageBills(this),
                new PageCycle(this),
                new PageChat(this),
                new PagePending(this),
                new PageStats(this),
                new PageSettings(this)
        };

        navLabels = new TextView[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            TextView label = tabItem(i);
            navLabels[i] = label;
            navBar.addView(label, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }

        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                top = i.top;
                bottom = i.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            insetBottom = bottom;
            v.setPadding(0, top, 0, 0);
            if (navBar != null) {
                navBar.setPadding(0, Ui.d(MainActivity.this, 2), 0,
                        bottom + Ui.d(MainActivity.this, 2));
            }
            return insets;
        });
        content.requestApplyInsets();
    }

    /** Android 15 起强制 edge-to-edge，内容会画到状态栏底下，这里自己补内边距。 */
    private void applyInsets(final View content) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = Skin.dark ? 0
                        : (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                c.setSystemBarsAppearance(light, light);
            }
        } else if (!Skin.dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        content.requestApplyInsets();
    }

    /** 单个页面画崩了不该把整个 App 带走：记进 crash.log，其他页照常用。 */
    private void safeRefresh(Page p) {
        if (p == null) return;
        try {
            p.refresh();
        } catch (Throwable t) {
            Crash.log(this, t);
        }
    }

    private TextView tabItem(final int i) {
        TextView label = Ui.tv(this, NAMES[i], 11.5f, Ui.FAINT);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setOnClickListener(v -> switchTo(i));
        return label;
    }

    private void switchTo(int i) {
        if (i >= pages.length) return;
        if (i == current) {
            safeRefresh(pages[i]);
            return;
        }
        current = i;
        body.removeAllViews();
        body.addView(pages[i], new FrameLayout.LayoutParams(MATCH, MATCH));
        safeRefresh(pages[i]);
        updateNavs();
    }

    private void updateNavs() {
        int pending = Db.get(this).pendingCount();
        for (int i = 0; i < navLabels.length; i++) {
            boolean on = i == current;
            TextView label = navLabels[i];
            label.setTextColor(on ? Ui.BRAND : Ui.FAINT);
            label.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            if (i == TAB_PENDING && pending > 0) {
                label.setText("待确认 " + pending);
                if (!on) label.setTextColor(Ui.WARN);
            } else {
                label.setText(NAMES[i]);
            }
        }
    }

    public void refreshAll() {
        if (pages == null) return;
        for (Page p : pages) {
            safeRefresh(p);
        }
        updateNavs();
        WidgetProvider.refresh(this);
    }

    /** 别的页面想跳到某个页签（比如设置里的「生活周期」）。 */
    public void openTab(int i) {
        switchTo(i);
    }

    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 底部轻提示，自带一个动作按钮（删除撤销用）。 */
    public void snack(String msg, String action, final Runnable onAction) {
        if (root == null) return;
        dismissSnack();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.d(this, 16), Ui.d(this, 12), Ui.d(this, 8), Ui.d(this, 12));
        bar.setBackground(Ui.round(Skin.dark ? 0xFFE8EBEF : 0xFF1D1F20, 14, this));
        bar.setElevation(Ui.d(this, 10));

        int fg = Skin.dark ? 0xFF11151A : 0xFFFFFFFF;
        TextView t = Ui.tv(this, msg, 13, fg);
        bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        if (action != null && onAction != null) {
            TextView a = Ui.tvBold(this, action, 13, Skin.dark ? 0xFF0F766E : 0xFF5EEAD4);
            a.setPadding(Ui.d(this, 12), Ui.d(this, 4), Ui.d(this, 12), Ui.d(this, 4));
            a.setOnClickListener(v -> {
                dismissSnack();
                onAction.run();
            });
            bar.addView(a);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM);
        lp.setMargins(Ui.d(this, 16), 0, Ui.d(this, 16),
                Ui.d(this, 68) + insetBottom);
        root.addView(bar, lp);
        snackView = bar;
        ui.postDelayed(this::dismissSnack, 4200);
    }

    private void dismissSnack() {
        if (snackView == null) return;
        final View v = snackView;
        snackView = null;
        v.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            try {
                root.removeView(v);
            } catch (Throwable ignored) {
            }
        }).start();
    }

    /** 切皮肤：存下来，重建整个界面。 */
    public void applySkin(boolean glass) {
        Prefs.setGlass(this, glass);
        Skin.load(this);
        recreate();
    }

    /** 切深色：同上。 */
    public void applyDark(boolean dark) {
        Prefs.setDark(this, dark);
        Skin.load(this);
        recreate();
    }

    // ---------------------------------------------------------------- CSV

    public void pickCsv() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_CSV);
        } catch (Throwable t) {
            toast("打不开文件选择器");
        }
    }

    public void exportCsv() {
        toast("正在导出…");
        new Thread(() -> {
            try {
                final String msg = CsvImport.export(MainActivity.this);
                runOnUiThread(() -> toast(msg));
            } catch (final Throwable t) {
                runOnUiThread(() -> toast("导出失败：" + t.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CSV || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        toast("正在导入…");
        new Thread(() -> {
            try {
                final CsvImport.Result r = CsvImport.importFrom(MainActivity.this, uri);
                runOnUiThread(() -> {
                    toast(r.msg);
                    refreshAll();
                });
            } catch (final Throwable t) {
                runOnUiThread(() -> toast("导入失败：" + t.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", Math.max(0, current));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        int tab = intent.getIntExtra("tab", -1);
        if (tab >= 0) switchTo(tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Housekeeping.run(this);
        if (current >= 0 && pages != null) {
            safeRefresh(pages[current]);
            updateNavs();
        }
    }

    @Override
    public void onBackPressed() {
        if (current != TAB_BILLS) {
            switchTo(TAB_BILLS);
        } else {
            super.onBackPressed();
        }
    }
}
