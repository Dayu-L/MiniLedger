package com.miniledger.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 重复项识别。
 *
 * 同一「天 + 收支 + 金额」下，满足下面任一条就当一个重复组：
 *   - 两笔时间相差 ≤ 10 分钟（同一笔钱的两次推送 / 通知 + 手动补记）
 *   - 商户名完全相同且非空（同一天同一个店同样的钱，多半是导了两次/记了两次）
 *
 * 只看已入账（status = DONE）的记录；待确认里的不动。
 * 纯 Java，可单测（test/DupeTest.java）。
 */
final class Dupe {

    static final long NEAR_MS = 10 * 60 * 1000L;

    private Dupe() {}

    /** 一组疑似重复。 */
    static final class Group {
        final long day;          // 天首
        final int kind;
        final long amount;       // 分
        final List<Tx> items = new ArrayList<>();   // 按时间升序

        Group(Tx first) {
            this.day = Span.dayStartOf(first.ts);
            this.kind = first.kind;
            this.amount = first.amount;
        }

        String title() {
            String what = kind == Tx.IN ? "收入" : "支出";
            return Span.dayLabel(day) + " " + what + " " + Money.yuan(amount)
                    + " · " + items.size() + " 笔";
        }

        String merchant() {
            for (Tx t : items) {
                if (t.merchant != null && !t.merchant.isEmpty() && !"未知".equals(t.merchant)) {
                    return t.merchant;
                }
            }
            return "";
        }

        /** 忽略/已处理的标记：同一批记录稳定。 */
        String signature() {
            StringBuilder sb = new StringBuilder();
            sb.append(day).append('|').append(kind).append('|').append(amount).append('|');
            for (Tx t : items) sb.append(t.id).append(',');
            return sb.toString();
        }

        List<Tx> later() {
            List<Tx> out = new ArrayList<>();
            for (int i = 1; i < items.size(); i++) out.add(items.get(i));
            return out;
        }
    }

    /** 找出所有重复组（同一组内按时间升序）。 */
    static List<Group> find(List<Tx> all) {
        List<Group> out = new ArrayList<>();
        if (all == null || all.size() < 2) return out;

        // 按 天+收支+金额 分桶
        java.util.LinkedHashMap<String, List<Tx>> buckets = new java.util.LinkedHashMap<>();
        for (Tx t : all) {
            if (t == null || t.status != Tx.DONE || t.amount <= 0) continue;
            String k = Span.dayStartOf(t.ts) + "|" + t.kind + "|" + t.amount;
            List<Tx> l = buckets.get(k);
            if (l == null) {
                l = new ArrayList<>();
                buckets.put(k, l);
            }
            l.add(t);
        }

        for (List<Tx> bucket : buckets.values()) {
            if (bucket.size() < 2) continue;
            java.util.Collections.sort(bucket, (a, b) -> Long.compare(a.ts, b.ts));

            Set<String> used = new HashSet<>();
            List<Group> clusters = new ArrayList<>();
            for (Tx t : bucket) {
                if (used.contains(String.valueOf(t.id))) continue;

                Group g = new Group(t);
                g.items.add(t);
                long first = t.ts;
                String m = nm(t.merchant);
                for (Tx other : bucket) {
                    if (other.id == t.id || used.contains(String.valueOf(other.id))) continue;
                    boolean near = Math.abs(other.ts - first) <= NEAR_MS;
                    boolean sameMerchant = !m.isEmpty() && m.equals(nm(other.merchant));
                    if (near || sameMerchant) {
                        g.items.add(other);
                        used.add(String.valueOf(other.id));
                    }
                }
                if (g.items.size() >= 2) {
                    used.add(String.valueOf(t.id));
                    java.util.Collections.sort(g.items, (a, b) -> Long.compare(a.ts, b.ts));
                    clusters.add(g);
                }
            }
            out.addAll(clusters);
        }
        return out;
    }

    /** 把「都保留」过/已经处理过的组剔掉。 */
    static List<Group> filter(List<Group> groups, Set<String> ignored) {
        List<Group> out = new ArrayList<>();
        for (Group g : groups) {
            if (ignored != null && ignored.contains(g.signature())) continue;
            out.add(g);
        }
        return out;
    }

    private static String nm(String s) {
        if (s == null) return "";
        String t = s.trim();
        return "未知".equals(t) ? "" : t;
    }
}
