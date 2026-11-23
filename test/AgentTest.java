package com.miniledger.app;

/** 记账 agent 本地解析自测：纯 JVM，不需要 Android。 */
public class AgentTest {

    static int pass = 0, fail = 0;
    static long NOW;

    public static void main(String[] args) throws Exception {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, java.util.Calendar.NOVEMBER, 13, 15, 30, 0);   // 2026-11-13 周五 15:30
        c.set(java.util.Calendar.MILLISECOND, 0);
        NOW = c.getTimeInMillis();

        // ---- 单笔
        one("午饭 25", Tx.OUT, 2500, "餐饮", null);
        one("午饭 25 微信", Tx.OUT, 2500, "餐饮", "微信");
        one("昨天打车 38", Tx.OUT, 3800, "交通", null);
        one("今天中午和同事吃饭 128", Tx.OUT, 12800, "餐饮", null);
        one("美团外卖 35 支付宝", Tx.OUT, 3500, "餐饮", "支付宝");
        one("11月3号充话费100", Tx.OUT, 10000, "通讯", null);
        one("上周五买鞋 399", Tx.OUT, 39900, "购物", null);
        one("收到工资 8000", Tx.IN, 800000, "工资", null);
        one("退款 59", Tx.IN, 5900, "退款", null);
        one("房租 3000 银行卡", Tx.OUT, 300000, "居住", "银行卡");
        one("买了2杯咖啡 40", Tx.OUT, 4000, "餐饮", null);
        one("AA 后我付 64，一共 128", Tx.OUT, 6400, null, null);
        one("星巴克 32", Tx.OUT, 3200, "餐饮", null);
        one("看电影 45 支付宝", Tx.OUT, 4500, "娱乐", "支付宝");

        // ---- 多笔
        multi("午饭25、打车38、咖啡19", 3);
        multi("早饭12 午饭28 晚饭36", 3);

        // ---- 日期
        Agent.Result r = Agent.local("昨天打车 38", NOW);
        long expect = Agent.startOfDay(NOW) - 86400000L + 12 * 3600000L;
        if (r.items.size() == 1 && Math.abs(r.items.get(0).ts - expect) < 1000) {
            pass++;
            System.out.println("ok    日期 昨天 -> " + new java.util.Date(r.items.get(0).ts));
        } else {
            fail++;
            System.out.println("FAIL  日期 昨天 -> " + (r.items.isEmpty() ? "无" : new java.util.Date(r.items.get(0).ts)));
        }

        r = Agent.local("11月3号 买菜 42", NOW);
        java.util.Calendar d = java.util.Calendar.getInstance();
        d.setTimeInMillis(r.items.isEmpty() ? 0 : r.items.get(0).ts);
        if (r.items.size() == 1 && d.get(java.util.Calendar.MONTH) == java.util.Calendar.NOVEMBER
                && d.get(java.util.Calendar.DAY_OF_MONTH) == 3) {
            pass++;
            System.out.println("ok    日期 11月3号");
        } else {
            fail++;
            System.out.println("FAIL  日期 11月3号");
        }

        // ---- 今天用当前时刻
        r = Agent.local("咖啡 19", NOW);
        if (r.items.size() == 1 && r.items.get(0).ts == NOW) {
            pass++;
            System.out.println("ok    今天保持当前时刻");
        } else {
            fail++;
            System.out.println("FAIL  今天保持当前时刻");
        }

        // ---- 认不出金额
        r = Agent.local("今天天气不错", NOW);
        if (r.items.isEmpty() && r.text.contains("金额")) {
            pass++;
            System.out.println("ok    无金额 -> 追问");
        } else {
            fail++;
            System.out.println("FAIL  无金额 -> " + r.text);
        }

        // ---- 意图
        intent("这个月花了多少", "spend");
        intent("预算还剩多少", "budget");
        intent("最近几笔", "recent");
        intent("删掉刚才那笔", "delete");
        intent("把昨天打车那笔改成 45", "edit");
        intent("把打车那笔记成交通", "edit");
        intent("午饭 25", "record");

        // ---- 改账指令
        edit("把昨天打车那笔改成 45", Agent.Edit.AMOUNT, 4500, null, null);
        edit("把打车那笔记成交通", Agent.Edit.CATEGORY, 0, "交通", null);
        edit("这笔算餐饮", Agent.Edit.CATEGORY, 0, "餐饮", null);
        edit("删掉昨天打车那笔", Agent.Edit.DELETE, 0, null, null);
        edit("把昨天那笔改成支付宝", Agent.Edit.ACCOUNT, 0, null, "支付宝");
        edit("午饭 25", Agent.Edit.NONE, 0, null, null);

        // ---- 找目标
        long today = Agent.startOfDay(NOW);
        long yest = today - 86400000L;
        java.util.List<Tx> pool = new java.util.ArrayList<>();
        pool.add(mk(1, "打车", "交通", 3800, yest));
        pool.add(mk(2, "午饭", "餐饮", 2500, today));
        pool.add(mk(3, "淘宝", "购物", 18900, yest));
        target("把昨天打车那笔改成 45", pool, 1);
        target("把淘宝那个改成 200", pool, 3);
        target("今天午饭那笔", pool, 2);

        System.out.println();
        System.out.println("pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    static void one(String text, int kind, long cents, String cat, String account) {
        Agent.Result r = Agent.local(text, NOW);
        if (r.items.size() != 1) {
            fail++;
            System.out.println("FAIL  [" + text + "] 期望 1 笔，得到 " + r.items.size() + "  —— " + r.text);
            return;
        }
        Tx t = r.items.get(0);
        boolean ok = t.kind == kind && t.amount == cents;
        if (cat != null && !cat.equals(t.category)) ok = false;
        if (account != null && !account.equals(t.account)) ok = false;
        if (ok) {
            pass++;
            System.out.println("ok    [" + text + "]  " + (t.kind == Tx.IN ? "+" : "−")
                    + Money.yuan(t.amount) + "  " + t.category + "  " + t.account + "  [" + t.merchant + "]");
        } else {
            fail++;
            System.out.println("FAIL  [" + text + "]  " + t.kind + " " + t.amount + " "
                    + t.category + " " + t.account + " [" + t.merchant + "]");
        }
    }

    static void multi(String text, int n) {
        Agent.Result r = Agent.local(text, NOW);
        if (r.items.size() == n) {
            pass++;
            StringBuilder sb = new StringBuilder();
            for (Tx t : r.items) sb.append(t.category).append(Money.yuan(t.amount)).append(' ');
            System.out.println("ok    [" + text + "]  " + r.items.size() + " 笔  " + sb);
        } else {
            fail++;
            System.out.println("FAIL  [" + text + "] 期望 " + n + " 笔，得到 " + r.items.size());
        }
    }

    static Tx mk(long id, String merchant, String cat, long cents, long ts) {
        Tx t = new Tx();
        t.id = id;
        t.merchant = merchant;
        t.category = cat;
        t.amount = cents;
        t.ts = ts + 12 * 3600000L;
        return t;
    }

    static void edit(String text, int kind, long cents, String cat, String account) {
        Agent.Edit e = Agent.detectEdit(text);
        boolean ok = e.kind == kind;
        if (kind == Agent.Edit.AMOUNT && e.amount != cents) ok = false;
        if (kind == Agent.Edit.CATEGORY && (cat == null || !cat.equals(e.category))) ok = false;
        if (kind == Agent.Edit.ACCOUNT && (account == null || !account.equals(e.account))) ok = false;
        if (ok) {
            pass++;
            System.out.println("ok    改账 [" + text + "] kind=" + kind
                    + (kind == Agent.Edit.AMOUNT ? " " + Money.yuan(e.amount) : "")
                    + (kind == Agent.Edit.CATEGORY ? " " + e.category : ""));
        } else {
            fail++;
            System.out.println("FAIL  改账 [" + text + "] 得到 kind=" + e.kind
                    + " amount=" + e.amount + " cat=" + e.category + " acc=" + e.account);
        }
    }

    static void target(String text, java.util.List<Tx> pool, long expectId) {
        Tx t = Agent.findTarget(text, pool, NOW);
        if (t != null && t.id == expectId) {
            pass++;
            System.out.println("ok    找目标 [" + text + "] -> " + t.merchant);
        } else {
            fail++;
            System.out.println("FAIL  找目标 [" + text + "] -> "
                    + (t == null ? "没找到" : t.merchant) + "（期望 id=" + expectId + "）");
        }
    }

    static void intent(String text, String expect) {        String got = Agent.detectIntent(text);
        if (expect.equals(got)) {
            pass++;
            System.out.println("ok    意图 [" + text + "] -> " + got);
        } else {
            fail++;
            System.out.println("FAIL  意图 [" + text + "] -> " + got + "（期望 " + expect + "）");
        }
    }
}
