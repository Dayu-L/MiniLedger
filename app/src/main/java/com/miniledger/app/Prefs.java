package com.miniledger.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static boolean autoConfirm(Context c) {
        return sp(c).getBoolean("auto_confirm", true);
    }

    static void setAutoConfirm(Context c, boolean v) {
        sp(c).edit().putBoolean("auto_confirm", v).apply();
    }

    static boolean guideShown(Context c) {
        return sp(c).getBoolean("guide_shown", false);
    }

    static void setGuideShown(Context c) {
        sp(c).edit().putBoolean("guide_shown", true).apply();
    }

    static boolean guardOn(Context c) {
        return sp(c).getBoolean("guard_on", true);
    }

    static void setGuardOn(Context c, boolean v) {
        sp(c).edit().putBoolean("guard_on", v).apply();
    }

    /** true = 玻璃皮肤，false = 极简白。 */
    static boolean glass(Context c) {
        return sp(c).getBoolean("glass", true);
    }

    static void setGlass(Context c, boolean v) {
        sp(c).edit().putBoolean("glass", v).apply();
    }

    /** 深色皮肤，和玻璃/极简是两个维度。 */
    static boolean dark(Context c) {
        return sp(c).getBoolean("dark", false);
    }

    static void setDark(Context c, boolean v) {
        sp(c).edit().putBoolean("dark", v).apply();
    }

    /** 查重里选过「都保留」的组标记。 */
    static java.util.Set<String> dupIgnored(Context c) {
        return sp(c).getStringSet("dup_ignore", new java.util.HashSet<String>());
    }

    static void ignoreDup(Context c, String sig) {
        java.util.HashSet<String> s = new java.util.HashSet<>(dupIgnored(c));
        s.add(sig);
        sp(c).edit().putStringSet("dup_ignore", s).apply();
    }

    /** 上次自动备份的时间戳。 */
    static long lastBackup(Context c) {
        return sp(c).getLong("last_backup", 0L);
    }

    static void setLastBackup(Context c, long ts) {
        sp(c).edit().putLong("last_backup", ts).apply();
    }

    /** 月度预算，单位分；0 表示没设。 */
    static long budget(Context c) {
        return sp(c).getLong("budget", 0L);
    }

    static void setBudget(Context c, long cents) {
        sp(c).edit().putLong("budget", cents).apply();
    }

    /** 账期（生活周期）起始日，当天 00:00 的毫秒；0 = 没设。 */
    static long cycleStart(Context c) {
        return sp(c).getLong("cycle_start", 0L);
    }

    static void setCycleStart(Context c, long dayStart) {
        sp(c).edit().putLong("cycle_start", dayStart).apply();
    }

    /** 账期长度：0 = 按自然月对齐起始日；>0 = 固定 N 天。 */
    static int cycleDays(Context c) {
        return sp(c).getInt("cycle_days", 0);
    }

    static void setCycleDays(Context c, int days) {
        sp(c).edit().putInt("cycle_days", Math.max(0, days)).apply();
    }

    static boolean alerted(Context c, String key) {
        return sp(c).getBoolean("alert_" + key, false);
    }

    static void setAlerted(Context c, String key) {
        sp(c).edit().putBoolean("alert_" + key, true).apply();
    }

    // ------------------------------------------------------------ 大模型

    static boolean llmOn(Context c) {
        return sp(c).getBoolean("llm_on", false);
    }

    static void setLlmOn(Context c, boolean v) {
        sp(c).edit().putBoolean("llm_on", v).apply();
    }

    static Llm.Cfg llmCfg(Context c) {
        Llm.Cfg cfg = new Llm.Cfg();
        cfg.baseUrl = sp(c).getString("llm_base", "");
        cfg.apiKey = sp(c).getString("llm_key", "");
        cfg.model = sp(c).getString("llm_model", "deepseek-chat");
        return cfg;
    }

    static void setLlm(Context c, String base, String key, String model) {
        sp(c).edit()
                .putString("llm_base", base == null ? "" : base.trim())
                .putString("llm_key", key == null ? "" : key.trim())
                .putString("llm_model", model == null || model.trim().isEmpty() ? "deepseek-chat" : model.trim())
                .apply();
    }
}
