package com.miniledger.app;

final class Categories {
    static final String[] OUT = {
            "餐饮", "交通", "购物", "日用", "娱乐",
            "居住", "通讯", "医疗", "学习", "人情", "其他"
    };

    static final String[] IN = {
            "工资", "红包", "退款", "理财", "其他收入"
    };

    static String[] of(int kind) {
        return kind == Tx.IN ? IN : OUT;
    }

    static String def(int kind) {
        return kind == Tx.IN ? "其他收入" : "其他";
    }
}
