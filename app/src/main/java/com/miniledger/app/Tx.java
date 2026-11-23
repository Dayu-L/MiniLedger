package com.miniledger.app;

/** 一笔账。金额内部统一用「分」，避免浮点误差。 */
class Tx {
    static final int OUT = 0;   // 支出
    static final int IN = 1;    // 收入

    static final int PENDING = 0;
    static final int DONE = 1;

    long id;
    long ts;                    // 发生时间，epoch millis
    int kind = OUT;
    long amount;                // 分
    String merchant = "";
    String category = "";
    String account = "";        // 微信 / 支付宝 / 现金 …
    String note = "";
    String source = "manual";   // manual | notify | csv
    String srcPkg = "";
    String txid = "";           // 账单单号，用于去重
    String raw = "";            // 原始通知文本，便于回查
    int status = DONE;
    int dup = 0;                // 1 = 疑似重复，进待确认
}
