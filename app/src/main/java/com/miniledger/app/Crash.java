package com.miniledger.app;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 闪退兜底：把未捕获异常的堆栈写到 files/crash.log（保留最近一次，超过 100KB 就重开）。
 *
 * 不吞异常——系统该结束进程还是结束，只是留下现场，设置页可以看。
 */
final class Crash {

    private static final long MAX = 100 * 1024;

    private Crash() {}

    static void install(final Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log(app, e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static synchronized void log(Context c, Throwable e) {
        if (e == null) return;
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date()));
            pw.println("thread=" + Thread.currentThread().getName()
                    + " model=" + android.os.Build.MODEL
                    + " sdk=" + android.os.Build.VERSION.SDK_INT);
            e.printStackTrace(pw);
            pw.flush();

            File f = file(c);
            if (f.exists() && f.length() > MAX) f.delete();
            FileOutputStream fo = new FileOutputStream(f, true);
            fo.write(sw.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) {
        }
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "crash.log");
    }

    static boolean has(Context c) {
        File f = file(c);
        return f.exists() && f.length() > 0;
    }

    static String read(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return "";
            long len = Math.min(f.length(), 200 * 1024);
            byte[] b = new byte[(int) len];
            FileInputStream in = new FileInputStream(f);
            int n = in.read(b);
            in.close();
            return new String(b, 0, Math.max(0, n), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    static void clear(Context c) {
        try {
            file(c).delete();
        } catch (Throwable ignored) {
        }
    }
}
