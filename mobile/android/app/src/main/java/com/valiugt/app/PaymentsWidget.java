package com.valiugt.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 4×4: this month's calendar with the payment days marked, and the next payments underneath. */
public class PaymentsWidget extends AppWidgetProvider {
    private static final int[][] NEXT = {
        { R.id.n0, R.id.n0w, R.id.n0n, R.id.n0a },
        { R.id.n1, R.id.n1w, R.id.n1n, R.id.n1a },
        { R.id.n2, R.id.n2w, R.id.n2n, R.id.n2a }
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context, manager, ids); }

    static void update(Context c, AppWidgetManager manager, int[] ids) {
        RemoteViews v = build(c);
        for (int id : ids) manager.updateAppWidget(id, v);
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_payments);
        v.setOnClickPendingIntent(R.id.p_root, WidgetUpdater.open(c, "payments", 20));
        v.setOnClickPendingIntent(R.id.p_plus, WidgetUpdater.open(c, "add", 21));

        JSONObject data = ValiuStore.widgetData(c);
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH) + 1, today = now.get(Calendar.DAY_OF_MONTH);
        JSONObject month = data == null ? null : data.optJSONObject("month");
        boolean fresh = month != null && month.optInt("y") == y && month.optInt("m") == m;

        // Header: month name from the app (its language); weekday initials too.
        String title = fresh ? month.optString("title", c.getString(R.string.w_payments)) : c.getString(R.string.w_payments);
        v.setTextViewText(R.id.p_title, title);
        JSONArray wds = month == null ? null : month.optJSONArray("wds");
        String[] dflt = { "D", "L", "M", "M", "J", "V", "S" };
        for (int k = 0; k < 7; k++) v.setTextViewText(WidgetUpdater.WEEKDAYS[k], wds != null && wds.length() == 7 ? wds.optString(k) : dflt[k]);

        // Grid: Sunday-first, like the app's calendar.
        Calendar first = Calendar.getInstance();
        first.set(y, m - 1, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int dim = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        JSONObject due = fresh ? month.optJSONObject("due") : null;
        for (int i = 0; i < WidgetUpdater.CELLS.length; i++) {
            int cell = WidgetUpdater.CELLS[i];
            int d = i - offset + 1;
            if (d < 1 || d > dim) {
                v.setTextViewText(cell, "");
                v.setInt(cell, "setBackgroundResource", 0);
                continue;
            }
            String st = due == null ? "" : due.optString(String.valueOf(d), "");
            boolean open = st.equals("due");
            int bg = 0, color = R.color.w_text;
            if (d == today) { bg = open ? R.drawable.cal_today_due : R.drawable.cal_today; color = R.color.w_on_accent; }
            else if (open && d < today) { bg = R.drawable.cal_over; color = R.color.w_danger; }
            else if (open) { bg = R.drawable.cal_due; color = R.color.w_warn; }
            else if (st.equals("paid")) { bg = R.drawable.cal_paid; color = R.color.w_accent; }
            else if (d < today) color = R.color.w_faint;
            v.setTextViewText(cell, String.valueOf(d));
            v.setInt(cell, "setBackgroundResource", bg);
            v.setTextColor(cell, c.getColor(color));
        }
        // Hide the 6th row when the month fits in 5.
        v.setViewVisibility(R.id.r5, offset + dim > 35 ? View.VISIBLE : View.GONE);

        // Next payments (unpaid), soonest first.
        List<JSONObject> next = new ArrayList<>();
        JSONArray arr = data == null ? null : data.optJSONArray("next");
        if (arr != null) for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null) next.add(o); }
        next.sort((a, b) -> a.optString("due").compareTo(b.optString("due")));
        for (int k = 0; k < NEXT.length; k++) {
            int[] row = NEXT[k];
            if (k >= next.size()) { v.setViewVisibility(row[0], View.GONE); continue; }
            JSONObject o = next.get(k);
            int n = WidgetUpdater.daysFromToday(o.optString("due"));
            String when = n < 0 ? ValiuStore.str(data, "overdue", "Vencido") : n == 0 ? ValiuStore.str(data, "today", "Hoy")
                : n == 1 ? ValiuStore.str(data, "tomorrow", "Mañana") : ValiuStore.str(data, "inDays", "{n} días").replace("{n}", String.valueOf(n));
            v.setViewVisibility(row[0], View.VISIBLE);
            v.setTextViewText(row[1], when);
            v.setInt(row[1], "setBackgroundResource", n < 0 ? R.drawable.widget_chip_danger : n <= 3 ? R.drawable.widget_chip_warn : R.drawable.widget_chip);
            v.setTextColor(row[1], c.getColor(n < 0 ? R.color.w_danger : n <= 3 ? R.color.w_warn : R.color.w_accent));
            v.setTextViewText(row[2], o.optString("name"));
            v.setTextViewText(row[3], WidgetUpdater.money(data, o.optDouble("amt", 0), ""));
        }

        String sub;
        if (data == null || !fresh) sub = ValiuStore.str(data, "open", c.getString(R.string.w_open));
        else if (next.isEmpty()) sub = ValiuStore.str(data, "allPaid", "Todo pagado este mes");
        else sub = ValiuStore.str(data, next.size() == 1 ? "pending1" : "pendingN", "{n} pagos pendientes").replace("{n}", String.valueOf(next.size()));
        v.setTextViewText(R.id.p_sub, sub);
        v.setImageViewResource(R.id.p_frog, fresh && next.isEmpty() ? R.drawable.frog_celebrando : R.drawable.frog_presupuesto);
        v.setViewVisibility(R.id.p_none, View.GONE);
        return v;
    }
}
