package com.miniledger.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知文本 -> 一笔账。
 * 只认「有明确金额 + 有收支方向」的通知，宁漏不误。
 */
final class Parser {

    static final String PKG_WECHAT = "com.tencent.mm";
    static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";

    private Parser() {}

    private static final Pattern P_YEN =
            Pattern.compile("[¥￥]\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)");
    private static final Pattern P_YUAN =
            Pattern.compile("(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块)");

    private static final String[] BAD_BEFORE = {
            "余额", "额度", "可用", "剩余", "优惠", "立减", "抵扣", "积分", "满减", "结算"
    };

    private static final String[] KW_IN = {
            "收款成功", "已收款", "收款到账", "到账", "退款", "已退", "退回",
            "收到红包", "抢到红包", "你收到", "收到一笔", "转入", "收益", "入账",
            "工资", "奖金", "报销", "收款", "收钱"
    };

    private static final String[] KW_OUT = {
            "付款成功", "支付成功", "已支付", "支付了", "消费", "扣款", "支出",
            "已付款", "转出", "转账给", "已转账", "缴费", "还款", "已扣", "付款", "转账"
    };

    private static final Pattern[] P_MERCHANT = {
            Pattern.compile("付款给\\s*([^，,。；;、¥￥]{1,20}?)(?=[，,。；;、¥￥\\s]|$)"),
            Pattern.compile("向\\s*([^，,。；;、¥￥]{1,20}?)\\s*付款"),
            Pattern.compile("(?:转账给|转给)\\s*([^，,。；;、¥￥]{1,20}?)(?=[，,。；;、¥￥\\s]|$)"),
            Pattern.compile("在\\s*([^，,。；;、¥￥]{1,20}?)(?:消费|的订单|店内)"),
            Pattern.compile("收款方[:：]?\\s*([^，,。；;、¥￥]{1,20}?)(?=[，,。；;、¥￥\\s]|$)"),
            Pattern.compile("商户[:：]?\\s*([^，,。；;、¥￥]{1,20}?)(?=[，,。；;、¥￥\\s]|$)")
    };

    /** 排除明显不是交易的通知。 */
    private static boolean noise(String s) {
        return s.contains("验证码") || s.contains("校验码") || s.contains("登录")
                || s.contains("红包已过期") || s.contains("已过期")
                || s.matches(".*\\d+\\s*条(新)?消息.*");
    }

    static Tx parse(String pkg, CharSequence titleCs, CharSequence bodyCs, long when) {
        String title = titleCs == null ? "" : clean(titleCs.toString());
        String body = bodyCs == null ? "" : clean(bodyCs.toString());
        String all = (title + " " + body).trim();

        if (all.length() < 3) return null;
        if (noise(all)) return null;

        // 微信：必须能确认是微信支付/收款类消息，避免把好友聊天里的「转你 100 元」记账
        if (PKG_WECHAT.equals(pkg)) {
            if (!(all.contains("微信支付") || all.contains("微信收款")
                    || all.contains("收款助手") || all.contains("零钱")
                    || all.contains("微信转账"))) {
                return null;
            }
        } else if (PKG_ALIPAY.equals(pkg)) {
            if (!(all.contains("支付宝") || all.contains("付款") || all.contains("收款")
                    || all.contains("到账") || all.contains("退款") || all.contains("花呗")
                    || all.contains("余额宝") || all.contains("转账"))) {
                return null;
            }
        } else {
            return null;
        }

        long amount = findAmount(all);
        if (amount <= 0) return null;

        int kind = findKind(all);
        if (kind < 0) return null;

        String merchant = findMerchant(all, title, pkg);

        Tx t = new Tx();
        t.ts = when > 0 ? when : System.currentTimeMillis();
        t.kind = kind;
        t.amount = amount;
        t.merchant = merchant;
        t.account = PKG_WECHAT.equals(pkg) ? "微信" : "支付宝";
        t.source = "notify";
        t.srcPkg = pkg;
        t.raw = all.length() > 300 ? all.substring(0, 300) : all;
        return t;
    }

    /** 扫描所有金额候选，跳过「余额/优惠/立减」这类上下文。 */
    private static long findAmount(String s) {
        long best = -1;
        best = scan(s, P_YEN, best);
        best = scan(s, P_YUAN, best);
        return best;
    }

    private static long scan(String s, Pattern p, long best) {
        Matcher m = p.matcher(s);
        while (m.find()) {
            int start = m.start(1);
            String before = s.substring(Math.max(0, start - 6), start);
            boolean bad = false;
            for (String b : BAD_BEFORE) {
                if (before.contains(b)) { bad = true; break; }
            }
            if (bad) continue;
            long v = Money.parse(m.group(1));
            if (v > 0) {
                if (best < 0) best = v;
            }
        }
        return best;
    }

    private static int findKind(String s) {
        int in = 0, out = 0;
        for (String k : KW_IN) if (s.contains(k)) in++;
        for (String k : KW_OUT) if (s.contains(k)) out++;
        if (in == 0 && out == 0) return -1;
        if (in > out) return Tx.IN;
        if (out > in) return Tx.OUT;
        // 打平：含「收款」且不含「付款」算收入
        if (s.contains("收款") && !s.contains("付款")) return Tx.IN;
        return Tx.OUT;
    }

    private static String findMerchant(String all, String title, String pkg) {
        for (Pattern p : P_MERCHANT) {
            Matcher m = p.matcher(all);
            if (m.find()) {
                String v = trimTail(m.group(1));
                if (v.length() >= 1 && !v.equals("你") && !v.equals("我")) return v;
            }
        }
        if (!title.isEmpty()
                && !title.equals("微信支付") && !title.equals("微信收款助手")
                && !title.equals("支付宝") && !title.equals("微信")) {
            String v = trimTail(title);
            if (v.length() >= 1 && v.length() <= 20) return v;
        }
        return PKG_WECHAT.equals(pkg) ? "微信支付" : "支付宝";
    }

    private static String trimTail(String v) {
        String r = v.trim();
        while (r.endsWith("的") || r.endsWith("了") || r.endsWith("·")) {
            r = r.substring(0, r.length() - 1);
        }
        return r.trim();
    }

    private static String clean(String s) {
        return s.replace('\n', ' ').replace('\r', ' ')
                .replace("￥", "¥")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
