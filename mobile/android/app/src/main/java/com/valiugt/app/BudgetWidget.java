package com.valiugt.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** 2×2: how much you can still spend today, with the frog that matches how the day is going. */
public class BudgetWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context, manager, ids); }

    static void update(Context c, AppWidgetManager manager, int[] ids) {
        RemoteViews v = build(c);
        for (int id : ids) manager.updateAppWidget(id, v);
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_budget);
        v.setOnClickPendingIntent(R.id.w_root, WidgetUpdater.open(c, "today", 10));
        v.setOnClickPendingIntent(R.id.w_plus, WidgetUpdater.open(c, "add", 11));

        JSONObject data = ValiuStore.widgetData(c);
        JSONObject day = findToday(data);
        if (day == null) {
            v.setImageViewResource(R.id.w_frog, R.drawable.frog_pensando);
            v.setTextViewText(R.id.w_label, ValiuStore.str(data, "can", c.getString(R.string.w_can)));
            v.setTextViewText(R.id.w_amount, "—");
            v.setTextColor(R.id.w_amount, c.getColor(R.color.w_accent));
            v.setTextViewText(R.id.w_of, ValiuStore.str(data, "open", c.getString(R.string.w_open)));
            bar(v, R.id.w_bar_ok, 0);
            return v;
        }

        boolean isDataDay = WidgetUpdater.todayIso().equals(data.optString("day"));
        double daily = day.optDouble("daily", 0);
        double spent = day.optDouble("spent", 0) + WidgetUpdater.pendingToday(c, data);
        double left = daily - spent;
        double ratio = daily > 0 ? Math.max(0, Math.min(1, left / daily)) : 0;
        boolean over = left < -0.005;
        String tone = over || daily <= 0 ? "danger" : ratio < 0.3 ? "warn" : "ok";

        int frog;
        if (over) frog = R.drawable.frog_sin_fondos;
        else if (daily <= 0) frog = R.drawable.frog_preocupado;
        else if (tone.equals("warn")) frog = R.drawable.frog_en_alerta;
        else if (isDataDay) frog = frogFor(data.optString("mascot"));
        else frog = R.drawable.frog_equilibrio;
        v.setImageViewResource(R.id.w_frog, frog);

        v.setTextViewText(R.id.w_label, over ? ValiuStore.str(data, "over", "Te pasaste hoy") : ValiuStore.str(data, "can", c.getString(R.string.w_can)));
        v.setTextViewText(R.id.w_amount, WidgetUpdater.money(data, Math.abs(left), ""));
        v.setTextColor(R.id.w_amount, c.getColor(tone.equals("danger") ? R.color.w_danger : tone.equals("warn") ? R.color.w_warn : R.color.w_accent));
        v.setTextViewText(R.id.w_of, ValiuStore.str(data, "of", "de {x} para hoy").replace("{x}", WidgetUpdater.money(data, daily, "")));
        int id = tone.equals("danger") ? R.id.w_bar_danger : tone.equals("warn") ? R.id.w_bar_warn : R.id.w_bar_ok;
        bar(v, id, tone.equals("danger") ? 100 : (int) Math.round(ratio * 100));
        return v;
    }

    private static void bar(RemoteViews v, int show, int progress) {
        for (int id : new int[] { R.id.w_bar_ok, R.id.w_bar_warn, R.id.w_bar_danger }) v.setViewVisibility(id, id == show ? View.VISIBLE : View.GONE);
        v.setProgressBar(show, 100, progress, false);
    }

    /** Today's entry from the days the app sent (it sends the rest of the month, in case the app isn't opened). */
    private static JSONObject findToday(JSONObject data) {
        if (data == null) return null;
        JSONArray days = data.optJSONArray("days");
        if (days == null) return null;
        String today = WidgetUpdater.todayIso();
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d != null && today.equals(d.optString("d"))) return d;
        }
        return null;
    }

    private static int frogFor(String name) {
        if (name == null) return R.drawable.frog_equilibrio;
        switch (name) {
            case "ahorrando": return R.drawable.frog_ahorrando;
            case "en-racha": return R.drawable.frog_en_racha;
            case "nivel-superior": return R.drawable.frog_nivel_superior;
            case "en-alerta": return R.drawable.frog_en_alerta;
            case "preocupado": return R.drawable.frog_preocupado;
            case "sin-fondos": return R.drawable.frog_sin_fondos;
            default: return R.drawable.frog_equilibrio;
        }
    }
}
