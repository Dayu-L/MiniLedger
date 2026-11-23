package com.miniledger.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
    private static final String[] NAMES = {"账单", "助手", "待确认", "统计", "设置"};

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private FrameLayout body;
    private Page[] pages;
    private TextView[] navLabels;
    private LinearLayout navBar;
    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.setup(this);
        buildUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        switchTo(savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0);

        if (Prefs.guardOn(this)) {
            GuardService.ensure(this);
            GuardJob.schedule(this);
            HeartbeatReceiver.schedule(this, GuardJob.PERIOD);
        }

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
        FrameLayout root = new FrameLayout(this);
        setContentView(root);

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
    }

    /** Android 15 起强制 edge-to-edge，内容会画到状态栏底下，这里自己补内边距。 */
    private void applyInsets(final View content) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light, light);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
            v.setPadding(0, top, 0, 0);
            if (navBar != null) {
                navBar.setPadding(0, Ui.d(MainActivity.this, 2), 0, bottom + Ui.d(MainActivity.this, 2));
            }
            return insets;
        });
        content.requestApplyInsets();
    }

    private TextView tabItem(final int i) {
        TextView label = Ui.tv(this, NAMES[i], 12, Ui.FAINT);
        label.setGravity(Gravity.CENTER);
        label.setOnClickListener(v -> switchTo(i));
        return label;
    }

    private void switchTo(int i) {
        if (i == current) {
            pages[i].refresh();
            return;
        }
        current = i;
        body.removeAllViews();
        body.addView(pages[i], new FrameLayout.LayoutParams(MATCH, MATCH));
        pages[i].refresh();
        updateNavs();
    }

    private void updateNavs() {
        int pending = Db.get(this).pendingCount();
        for (int i = 0; i < navLabels.length; i++) {
            boolean on = i == current;
            TextView label = navLabels[i];
            label.setTextColor(on ? Ui.BRAND : Ui.FAINT);
            label.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            if (i == 2 && pending > 0) {
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
            if (p != null) p.refresh();
        }
        updateNavs();
    }

    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 切皮肤：存下来，重建整个界面。 */
    public void applySkin(boolean glass) {
        Prefs.setGlass(this, glass);
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
    protected void onResume() {        super.onResume();
        if (current >= 0 && pages != null) {
            pages[current].refresh();
            updateNavs();
        }
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            switchTo(0);
        } else {
            super.onBackPressed();
        }
    }
}
