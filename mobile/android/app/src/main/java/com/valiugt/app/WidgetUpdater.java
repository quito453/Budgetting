package com.valiugt.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Helpers shared by both widgets: refresh, dates, money format, taps that open Valiu. */
final class WidgetUpdater {
    private WidgetUpdater() {}

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] b = m.getAppWidgetIds(new ComponentName(c, BudgetWidget.class));
            if (b.length > 0) BudgetWidget.update(c, m, b);
            int[] p = m.getAppWidgetIds(new ComponentName(c, PaymentsWidget.class));
            if (p.length > 0) PaymentsWidget.update(c, m, p);
        } catch (Exception e) { /* launcher not ready */ }
    }

    static String iso(Date d) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d); }
    static String todayIso() { return iso(new Date()); }

    /** Whole days from today to an ISO date (negative when it already passed). */
    static int daysFromToday(String isoDate) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate);
            if (d == null) return 0;
            Calendar a = Calendar.getInstance(); midnight(a);
            Calendar b = Calendar.getInstance(); b.setTime(d); midnight(b);
            return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
        } catch (Exception e) { return 0; }
    }
    static void midnight(Calendar c) { c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); }

    /** Money the way the app writes it (the app sends its prefix, suffix and separators). */
    static String money(JSONObject data, double v, String currency) {
        JSONObject nf = data == null ? null : data.optJSONObject("nf");
        String own = data == null ? "" : data.optString("cur", "GTQ");
        String pre = nf == null ? "Q" : nf.optString("pre", "");
        String post = nf == null ? "" : nf.optString("post", "");
        String dec = nf == null ? "." : nf.optString("dec", ".");
        String grp = nf == null ? "," : nf.optString("grp", ",");
        if (currency != null && !currency.isEmpty() && !currency.equals(own)) { pre = currency.equals("USD") ? "US$" : currency + " "; post = ""; }
        String s = String.format(Locale.US, "%,.2f", Math.abs(v));
        s = s.replace(",", "\u0001").replace(".", dec).replace("\u0001", grp);
        return (v < 0 ? "-" : "") + pre + s + post;
    }

    /** Today's payments the listener caught that the app hasn't picked up yet (in the app's own currency). */
    static double pendingToday(Context c, JSONObject data) {
        JSONArray q = ValiuStore.pendingPayments(c);
        String own = data == null ? "GTQ" : data.optString("cur", "GTQ");
        String today = todayIso();
        double sum = 0;
        for (int i = 0; i < q.length(); i++) {
            JSONObject p = q.optJSONObject(i);
            if (p == null) continue;
            String cur = p.optString("currency", "");
            if (!cur.isEmpty() && !cur.equals(own)) continue;
            if (today.equals(iso(new Date(p.optLong("ts"))))) sum += p.optDouble("amount", 0);
        }
        return sum;
    }

    static PendingIntent open(Context c, String action, int requestCode) {
        Intent i = new Intent(c, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ValiuPlugin.EXTRA_ACTION, action);
        return PendingIntent.getActivity(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static final int[] CELLS = { R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6, R.id.c7, R.id.c8, R.id.c9, R.id.c10, R.id.c11, R.id.c12, R.id.c13, R.id.c14, R.id.c15, R.id.c16, R.id.c17, R.id.c18, R.id.c19, R.id.c20, R.id.c21, R.id.c22, R.id.c23, R.id.c24, R.id.c25, R.id.c26, R.id.c27, R.id.c28, R.id.c29, R.id.c30, R.id.c31, R.id.c32, R.id.c33, R.id.c34, R.id.c35, R.id.c36, R.id.c37, R.id.c38, R.id.c39, R.id.c40, R.id.c41 };
    static final int[] WEEKDAYS = { R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5, R.id.w6 };
}
