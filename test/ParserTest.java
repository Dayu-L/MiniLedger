package com.miniledger.app;

/** 纯 JVM 跑的通知解析自测：javac 只编译 Tx/Money/Parser + 本文件。 */
public class ParserTest {

    static int pass = 0, fail = 0;

    static void check(String name, Tx t, int expectKind, long expectCents, String expectMerchantPart) {
        if (t == null) {
            fail++;
            System.out.println("FAIL  " + name + "  -> null (expected kind=" + expectKind + ")");
            return;
        }
        boolean ok = t.kind == expectKind && t.amount == expectCents;
        if (expectMerchantPart != null && !t.merchant.contains(expectMerchantPart)) ok = false;
        if (ok) {
            pass++;
            System.out.println("ok    " + name + "  " + (t.kind == Tx.IN ? "+" : "-")
                    + Money.yuan(t.amount) + "  [" + t.merchant + "]  " + t.category);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  -> kind=" + t.kind + " amount=" + t.amount
                    + " merchant=" + t.merchant);
        }
    }

    static void checkNull(String name, Tx t) {
        if (t == null) {
            pass++;
            System.out.println("ok    " + name + "  -> null");
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  -> expected null, got " + t.kind + " " + t.amount);
        }
    }

    static Tx w(String title, String body) {
        return Parser.parse(Parser.PKG_WECHAT, title, body, 1700000000000L);
    }

    static Tx a(String title, String body) {
        return Parser.parse(Parser.PKG_ALIPAY, title, body, 1700000000000L);
    }

    public static void main(String[] args) {
        // ---- 微信
        check("微信 已支付", w("微信支付", "你已支付¥25.00"), Tx.OUT, 2500, null);
        check("微信 收款", w("微信支付", "微信支付收款1.00元"), Tx.IN, 100, null);
        check("微信 退款", w("微信支付", "已退款¥25.00已到零钱"), Tx.IN, 2500, null);
        check("微信 付款给", w("微信支付", "向李四付款¥88.50"), Tx.OUT, 8850, "李四");
        check("微信 转账给", w("微信支付", "转账给王五¥100.00"), Tx.OUT, 10000, "王五");
        checkNull("微信 凭证无金额", w("微信支付", "微信支付凭证"));
        checkNull("微信 好友聊天里的转账", w("张三", "我转你100元"));
        checkNull("微信 非支付类通知", w("李四", "晚上吃饭吗"));

        // ---- 支付宝
        check("支付宝 付款成功", a("支付宝", "付款成功 ¥23.50"), Tx.OUT, 2350, null);
        check("支付宝 已成功付款", a("支付宝", "你已成功付款23.50元"), Tx.OUT, 2350, null);
        check("支付宝 收款成功", a("支付宝", "收款成功¥50.00"), Tx.IN, 5000, null);
        check("支付宝 余额宝收益", a("支付宝", "余额宝昨日收益¥0.12"), Tx.IN, 12, null);
        check("支付宝 花呗还款", a("支付宝", "花呗还款成功¥1000.00"), Tx.OUT, 100000, null);
        check("支付宝 优惠不吞金额", a("支付宝", "支付成功 ¥100.00，已优惠¥5.00"), Tx.OUT, 10000, null);
        check("支付宝 转账给", a("支付宝", "付款给 张三 ¥66.00"), Tx.OUT, 6600, "张三");
        checkNull("支付宝 余额提醒不是消费", a("支付宝", "你的账户余额为¥1234.56"));
        checkNull("支付宝 验证码", a("支付宝", "验证码123456，请勿泄露"));
        checkNull("支付宝 营销推送", a("支付宝", "限时优惠，最高立减20元"));

        System.out.println();
        System.out.println("pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
