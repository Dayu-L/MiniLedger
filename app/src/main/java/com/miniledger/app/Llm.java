package com.miniledger.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * OpenAI 兼容接口。和 DayFlow 一样：只在开了「大模型」时用，
 * 任何失败都抛出去，由上层回落到本地规则解析。
 */
final class Llm {

    static final class Cfg {
        String baseUrl = "";
        String apiKey = "";
        String model = "deepseek-chat";
    }

    private Llm() {}

    static Agent.Result respond(String text, Cfg cfg, String system,
                                List<String[]> history, long now) throws Exception {
        if (cfg.baseUrl == null || cfg.baseUrl.trim().isEmpty()) throw new Exception("没配 Base URL");

        String base = cfg.baseUrl.trim().replaceAll("/+$", "");
        String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";

        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        if (history != null) {
            for (String[] h : history) {
                if (h == null || h.length < 2 || h[1] == null || h[1].trim().isEmpty()) continue;
                msgs.put(new JSONObject().put("role", h[0]).put("content", h[1]));
            }
        }
        msgs.put(new JSONObject().put("role", "user").put("content", text));

        JSONObject body = new JSONObject()
                .put("model", cfg.model == null || cfg.model.trim().isEmpty() ? "deepseek-chat" : cfg.model.trim())
                .put("temperature", 0.2)
                .put("stream", false)
                .put("messages", msgs);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (cfg.apiKey != null && !cfg.apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey.trim());
            }
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new Exception("接口返回 " + code + (resp.isEmpty() ? "" : "：" + cut(resp, 120)));
            }

            JSONObject json = new JSONObject(resp);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new Exception("模型没返回内容");
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            String content = msg == null ? "" : msg.optString("content", "");

            JSONObject payload = parseLoose(content);
            if (payload == null) throw new Exception("模型没返回可解析的 JSON");

            return toResult(payload, now);
        } finally {
            conn.disconnect();
        }
    }

    /** 连通性测试：发一句最短的话，只要 HTTP 200 就算通。 */
    static String ping(Cfg cfg) throws Exception {
        if (cfg.baseUrl == null || cfg.baseUrl.trim().isEmpty()) throw new Exception("没配 Base URL");
        String base = cfg.baseUrl.trim().replaceAll("/+$", "");
        String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";

        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "user").put("content", "回复 ok"));
        JSONObject body = new JSONObject()
                .put("model", cfg.model == null || cfg.model.trim().isEmpty() ? "deepseek-chat" : cfg.model.trim())
                .put("stream", false)
                .put("max_tokens", 8)
                .put("messages", msgs);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (cfg.apiKey != null && !cfg.apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey.trim());
            }
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            String resp = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + (resp.isEmpty() ? "" : "：" + cut(resp, 100)));
            }
            return "连通（" + cfg.model + "）";
        } finally {
            conn.disconnect();
        }
    }

    private static Agent.Result toResult(JSONObject payload, long now) {
        Agent.Result r = new Agent.Result();
        r.mode = "llm";
        r.text = payload.optString("reply", "好了。");

        JSONArray items = payload.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                long cents = Math.round(o.optDouble("amount", 0) * 100d);
                if (cents <= 0) continue;

                Tx t = new Tx();
                t.amount = cents;
                t.kind = "in".equalsIgnoreCase(o.optString("kind", "out")) ? Tx.IN : Tx.OUT;
                t.category = safeCategory(o.optString("category", ""), t.kind);
                t.merchant = clip(o.optString("merchant", ""), 16);
                t.account = clip(o.optString("account", ""), 8);
                t.note = clip(o.optString("note", ""), 40);
                t.source = "agent";
                t.status = Tx.DONE;
                t.ts = parseDate(o.optString("date", ""), now);
                r.items.add(t);
            }
        }

        JSONArray acts = payload.optJSONArray("actions");
        if (acts != null) {
            for (int i = 0; i < acts.length(); i++) {
                JSONObject o = acts.optJSONObject(i);
                if (o == null) continue;
                Agent.Action a = new Agent.Action();
                a.type = o.optString("type", "");
                a.id = o.optLong("id", 0);
                if (a.id <= 0) continue;
                if (o.has("amount") && !o.isNull("amount")) {
                    long c = Math.round(o.optDouble("amount", 0) * 100d);
                    if (c > 0) a.amount = c;
                }
                if (o.has("category") && !o.isNull("category")) a.category = o.optString("category", "");
                if (o.has("note") && !o.isNull("note")) a.note = o.optString("note", "");
                if ("update".equals(a.type) || "delete".equals(a.type)) r.actions.add(a);
            }
        }

        r.changed = !r.items.isEmpty() || !r.actions.isEmpty();
        if (r.items.isEmpty() && r.actions.isEmpty()) r.tip = "没听出金额，换个说法试试。";
        return r;
    }

    private static String safeCategory(String c, int kind) {
        if (c == null) return "";
        for (String s : Categories.of(kind)) if (s.equals(c)) return c;
        return "";
    }

    private static long parseDate(String d, long now) {
        if (d == null || d.length() < 8) return now;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Date date = f.parse(d.substring(0, 10));
            if (date == null) return now;
            return date.getTime() + 12L * 3600L * 1000L;   // 当天中午
        } catch (Exception e) {
            return now;
        }
    }

    private static JSONObject parseLoose(String raw) {
        String t = raw == null ? "" : raw.trim();
        t = t.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        try {
            return new JSONObject(t);
        } catch (Exception ignored) {
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                return new JSONObject(t.substring(a, b + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString().trim();
    }

    private static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= n ? t : t.substring(0, n);
    }
}
