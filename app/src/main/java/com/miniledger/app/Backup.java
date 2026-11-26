package com.miniledger.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 自动备份：每周把全部账单导出成 CSV，放在应用外部目录（不需要权限），
 * 保留最近 8 份。恢复时按「单号」去重，重复恢复不会翻倍。
 *
 * 注意：应用外部目录 = /Android/data/com.miniledger.app/files/backup，
 * 卸载会被一起清掉，重要的那份请用「导出全部账单」放去 下载/简账。
 */
final class Backup {

    private static final long WEEK = 7 * 86400000L;
    private static final int KEEP = 8;

    private Backup() {}

    static File dir(Context c) {
        File base = c.getExternalFilesDir(null);
        return new File(base == null ? c.getFilesDir() : base, "backup");
    }

    /** 立即备份，返回给用户看的说明。 */
    static String run(Context c) throws Exception {
        File d = dir(c);
        if (!d.exists() && !d.mkdirs()) throw new IOException("建不了备份目录");

        String name = "简账-备份-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(new Date())
                + ".csv";
        File f = new File(d, name);
        FileOutputStream fo = new FileOutputStream(f);
        try {
            fo.write(("\uFEFF" + CsvImport.buildCsv(c)).getBytes("UTF-8"));
        } finally {
            fo.close();
        }
        Prefs.setLastBackup(c, System.currentTimeMillis());
        prune(d);
        return "已备份 " + name + "（" + files(c).length + " 份）";
    }

    /** 每周一次，悄悄做；没账就不备份。 */
    static void autoIfDue(Context c) {
        long last = Prefs.lastBackup(c);
        long now = System.currentTimeMillis();
        if (last > 0 && now - last < WEEK) return;
        if (Db.get(c).all().isEmpty()) return;
        try {
            run(c);
        } catch (Throwable ignored) {
        }
    }

    static String lastLabel(Context c) {
        long ts = Prefs.lastBackup(c);
        return ts == 0 ? "还没备份过" : Span.stamp(ts);
    }

    static int count(Context c) {
        return files(c).length;
    }

    static File latest(Context c) {
        File[] fs = files(c);
        return fs.length == 0 ? null : fs[0];
    }

    /** 恢复最近一份。 */
    static CsvImport.Result restoreLatest(Context c) throws Exception {
        File f = latest(c);
        if (f == null) throw new Exception("还没有备份文件");
        return CsvImport.importFrom(c, Uri.fromFile(f));
    }

    private static File[] files(Context c) {
        File[] fs = dir(c).listFiles((d, n) -> n.endsWith(".csv"));
        if (fs == null) fs = new File[0];
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return fs;
    }

    private static void prune(File dir) {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".csv"));
        if (fs == null) return;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < fs.length; i++) fs[i].delete();
    }
}
