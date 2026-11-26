package com.miniledger.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 微信 / 支付宝 账单 CSV 导入 + 全量导出。 */
final class CsvImport {

    private CsvImport() {}

    static class Result {
        int added;
        int skipped;
        String msg;
    }

    // ------------------------------------------------------------- import

    static Result importFrom(Context c, Uri uri) throws Exception {
        String text = readText(c, uri);
        String[] lines = text.split("\r?\n");

        // 简账自己导出的备份/账单（恢复用）
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String head = lines[i].replace("\uFEFF", "").trim();
            if (head.startsWith("时间,类型,金额,分类,对象,账户")) {
                return importOwn(c, lines, i);
            }
        }

        Map<String, Integer> col = null;
        int headerIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            List<String> cells = split(lines[i]);
            if (!cells.isEmpty() && cells.get(0).replace("\uFEFF", "").trim().equals("交易时间")) {
                col = new HashMap<>();
                for (int j = 0; j < cells.size(); j++) {
                    String name = cells.get(j).replace("\uFEFF", "").trim();
                    if (!name.isEmpty()) col.put(name, j);
                }
                headerIdx = i;
                break;
            }
        }
        if (col == null) {
            throw new Exception("没找到「交易时间」表头，这不是微信/支付宝导出的账单 CSV");
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        Result r = new Result();

        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cells = split(line);

            String io = v(cells, col, "收/支", "收支");
            int kind;
            if (io.contains("支出")) kind = Tx.OUT;
            else if (io.contains("收入")) kind = Tx.IN;
            else {
                r.skipped++;
                continue;
            }

            String status = v(cells, col, "当前状态", "交易状态", "状态");
            if (status.contains("关闭") || status.contains("失败")
                    || status.contains("撤销") || status.contains("未支付")) {
                r.skipped++;
                continue;
            }

            long amount = Money.parse(v(cells, col, "金额(元)", "金额（元）", "金额"));
            if (amount <= 0) {
                r.skipped++;
                continue;
            }

            String time = v(cells, col, "交易时间");
            long ts = System.currentTimeMillis();
            try {
                ts = fmt.parse(time).getTime();
            } catch (Exception ignored) {
            }

            String peer = v(cells, col, "交易对方");
            String product = v(cells, col, "商品", "商品说明");
            String pay = v(cells, col, "支付方式", "收/付款方式");
            String txid = v(cells, col, "交易单号", "交易订单号", "订单号");

            Tx t = new Tx();
            t.ts = ts;
            t.kind = kind;
            t.amount = amount;
            t.merchant = peer.isEmpty() ? (product.isEmpty() ? "未知" : product) : peer;
            t.account = accountOf(pay, c);
            t.note = v(cells, col, "备注");
            t.source = "csv";
            t.txid = txid;
            t.raw = line.length() > 300 ? line.substring(0, 300) : line;
            t.status = Tx.DONE;

            if (Db.get(c).insert(t, true) > 0) r.added++;
            else r.skipped++;
        }

        r.msg = "导入完成：新增 " + r.added + " 笔，跳过 " + r.skipped + " 笔";
        return r;
    }

    /** 简账自己的 CSV：时间,类型,金额,分类,对象,账户,备注,来源,单号 */
    private static Result importOwn(Context c, String[] lines, int headerIdx) throws Exception {
        List<String> head = split(lines[headerIdx].replace("\uFEFF", ""));
        Map<String, Integer> col = new HashMap<>();
        for (int j = 0; j < head.size(); j++) col.put(head.get(j).trim(), j);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        Result r = new Result();

        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cells = split(line);

            long amount = Money.parse(v(cells, col, "金额"));
            if (amount <= 0) {
                r.skipped++;
                continue;
            }

            Tx t = new Tx();
            try {
                t.ts = fmt.parse(v(cells, col, "时间")).getTime();
            } catch (Exception e) {
                t.ts = System.currentTimeMillis();
            }
            t.kind = v(cells, col, "类型").contains("收入") ? Tx.IN : Tx.OUT;
            t.amount = amount;
            t.category = v(cells, col, "分类");
            t.merchant = v(cells, col, "对象");
            t.account = v(cells, col, "账户");
            t.note = v(cells, col, "备注");
            t.source = "csv";
            t.txid = v(cells, col, "单号");
            t.status = Tx.DONE;

            if (Db.get(c).insert(t, true) > 0) r.added++;
            else r.skipped++;
        }
        r.msg = "恢复完成：新增 " + r.added + " 笔，跳过 " + r.skipped + " 笔";
        return r;
    }

    private static String accountOf(String pay, Context c) {
        if (pay == null) return "微信/支付宝";
        if (pay.contains("零钱")) return "微信零钱";
        if (pay.contains("微信")) return "微信";
        if (pay.contains("余额宝")) return "支付宝余额宝";
        if (pay.contains("花呗")) return "花呗";
        if (pay.contains("余额")) return "支付宝余额";
        if (pay.contains("银行")) return "银行卡";
        return "微信/支付宝";
    }

    private static String v(List<String> cells, Map<String, Integer> col, String... names) {
        for (String n : names) {
            Integer idx = col.get(n);
            if (idx != null && idx >= 0 && idx < cells.size()) {
                String s = cells.get(idx).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static String readText(Context c, Uri uri) throws Exception {
        InputStream is = c.getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("打不开文件");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        byte[] b = bo.toByteArray();
        String utf8 = new String(b, "UTF-8");
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(b, Charset.forName("GB18030"));
        }
        return utf8;
    }

    /** 支持双引号包裹、逗号转义的简易 CSV 行解析。 */
    private static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        q = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else {
                if (ch == '"') q = true;
                else if (ch == ',') {
                    out.add(sb.toString().trim());
                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
            }
        }
        out.add(sb.toString().trim());
        return out;
    }

    // ------------------------------------------------------------- export

    /** 全部账单，跟「导出」用的是同一份格式，备份/恢复也用它。 */
    static String buildCsv(Context c) {
        List<Tx> all = Db.get(c).all();
        StringBuilder sb = new StringBuilder();
        sb.append("时间,类型,金额,分类,对象,账户,备注,来源,单号\n");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        for (Tx t : all) {
            sb.append(f.format(new Date(t.ts))).append(',')
                    .append(t.kind == Tx.IN ? "收入" : "支出").append(',')
                    .append(Money.fmt(t.amount).replace(",", "")).append(',')
                    .append(q(t.category)).append(',')
                    .append(q(t.merchant)).append(',')
                    .append(q(t.account)).append(',')
                    .append(q(t.note)).append(',')
                    .append(t.source).append(',')
                    .append(q(t.txid)).append('\n');
        }
        return sb.toString();
    }

    static String export(Context c) throws Exception {
        int count = Db.get(c).all().size();
        String csv = buildCsv(c);

        String name = "简账-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                .format(new Date()) + ".csv";
        byte[] data = ("\uFEFF" + csv).getBytes("UTF-8");

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/简账");
            Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("创建文件失败");
            OutputStream os = c.getContentResolver().openOutputStream(u);
            if (os == null) throw new Exception("写入失败");
            os.write(data);
            os.close();
            return "已导出到 下载/简账/" + name + "（" + count + " 笔）";
        }

        File dir = c.getExternalFilesDir(null);
        if (dir == null) throw new Exception("没有可写目录");
        File out = new File(dir, name);
        FileOutputStream fo = new FileOutputStream(out);
        fo.write(data);
        fo.close();
        return "已导出到 " + out.getAbsolutePath() + "（" + count + " 笔）";
    }

    private static String q(String s) {
        if (s == null) return "";
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
