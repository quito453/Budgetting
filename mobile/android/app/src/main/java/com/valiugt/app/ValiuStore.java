package com.valiugt.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Small on-device store shared by the app, the payment listener and the widgets.
 * Nothing here leaves the phone; the web app reads the queue and saves the expenses itself.
 */
final class ValiuStore {
    private static final String FILE = "valiu_native";
    private static final String K_QUEUE = "payQueue", K_SEEN = "paySeen", K_CAPTURE = "captureOn", K_WIDGET = "widgetData";
    private static final Object LOCK = new Object();

    private ValiuStore() {}

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    // ---------- automatic capture switch (the user turns it on inside Valiu) ----------
    static boolean captureOn(Context c) { return sp(c).getBoolean(K_CAPTURE, false); }
    static void setCaptureOn(Context c, boolean on) { sp(c).edit().putBoolean(K_CAPTURE, on).apply(); }

    // ---------- captured payments waiting for the app ----------
    /** Adds a payment unless the same amount was already captured in the last 3 minutes (Wallet + bank both notify). */
    static boolean addPayment(Context c, double amount, String currency, String merchant, String pkg, long ts) {
        synchronized (LOCK) {
            try {
                SharedPreferences p = sp(c);
                JSONArray seen = new JSONArray(p.getString(K_SEEN, "[]"));
                JSONArray keep = new JSONArray();
                for (int i = 0; i < seen.length(); i++) {
                    JSONObject s = seen.getJSONObject(i);
                    long when = s.optLong("ts");
                    if (ts - when > 24L * 3600 * 1000) continue;   // forget after a day
                    if (Math.abs(s.optDouble("amount") - amount) < 0.005 && Math.abs(ts - when) < 3 * 60 * 1000) return false;
                    keep.put(s);
                }
                JSONObject item = new JSONObject();
                item.put("id", Long.toString(ts, 36) + Integer.toString((int) Math.round(amount * 100), 36));
                item.put("amount", amount);
                item.put("currency", currency == null ? "" : currency);
                item.put("merchant", merchant == null ? "" : merchant);
                item.put("app", pkg == null ? "" : pkg);
                item.put("ts", ts);
                JSONArray q = new JSONArray(p.getString(K_QUEUE, "[]"));
                if (q.length() < 200) q.put(item);
                JSONObject s = new JSONObject();
                s.put("amount", amount); s.put("ts", ts);
                keep.put(s);
                p.edit().putString(K_QUEUE, q.toString()).putString(K_SEEN, keep.toString()).apply();
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Hands the queue to the app and empties it. */
    static String takePayments(Context c) {
        synchronized (LOCK) {
            SharedPreferences p = sp(c);
            String q = p.getString(K_QUEUE, "[]");
            p.edit().putString(K_QUEUE, "[]").apply();
            return q;
        }
    }

    /** Payments still waiting (not yet in the app): the widget subtracts today's ones. */
    static JSONArray pendingPayments(Context c) {
        try { return new JSONArray(sp(c).getString(K_QUEUE, "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    // ---------- widget data written by the web app ----------
    static void setWidgetData(Context c, String json) { sp(c).edit().putString(K_WIDGET, json).apply(); }
    static JSONObject widgetData(Context c) {
        String s = sp(c).getString(K_WIDGET, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (JSONException e) { return null; }
    }

    /** A text the web app sent (so widgets and notices follow the app's language), or the Spanish default. */
    static String str(JSONObject data, String key, String dflt) {
        if (data == null) return dflt;
        JSONObject s = data.optJSONObject("str");
        String v = s == null ? null : s.optString(key, null);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
