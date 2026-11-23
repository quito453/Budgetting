package com.valiugt.app;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * The bridge between the web app and Android-only features: automatic capture of payment notifications,
 * home-screen widgets and the shortcuts that open Valiu on a given screen.
 * In JavaScript: Capacitor.Plugins.ValiuAndroid.
 */
@CapacitorPlugin(name = "ValiuAndroid")
public class ValiuPlugin extends Plugin {
    static final String EXTRA_ACTION = "valiu_action";
    private static String pendingAction = null;
    private static ValiuPlugin instance = null;

    @Override
    public void load() { instance = this; }

    /** Called by MainActivity with every intent that opens the app (widget, shortcut, notice). */
    static void takeIntent(Intent intent) {
        if (intent == null) return;
        String a = intent.getStringExtra(EXTRA_ACTION);
        if (a == null || a.isEmpty()) return;
        intent.removeExtra(EXTRA_ACTION);
        pendingAction = a;
        ValiuPlugin p = instance;
        if (p != null) {
            JSObject data = new JSObject();
            data.put("action", a);
            p.notifyListeners("action", data);   // the app then calls takeAction(), which clears it
        }
    }

    @PluginMethod
    public void takeAction(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("action", pendingAction == null ? "" : pendingAction);
        pendingAction = null;
        call.resolve(ret);
    }

    // ---------- automatic capture ----------
    private boolean listenerAllowed() {
        Context c = getContext();
        return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
    }

    @PluginMethod
    public void captureStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("allowed", listenerAllowed());
        ret.put("on", ValiuStore.captureOn(getContext()));
        ret.put("notices", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        call.resolve(ret);
    }

    @PluginMethod
    public void setCapture(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        ValiuStore.setCaptureOn(getContext(), on);
        JSObject ret = new JSObject();
        ret.put("allowed", listenerAllowed());
        ret.put("on", on);
        call.resolve(ret);
    }

    /** Opens Android's "Notification access" screen (straight to Valiu's switch where the phone allows it). */
    @PluginMethod
    public void openCaptureSettings(PluginCall call) {
        Context c = getContext();
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(c, PaymentListener.class).flattenToString());
        } else {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            try { c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e2) { call.reject("settings"); return; }
        }
        call.resolve();
    }

    /** The captured payments as a JSON array string; the queue is emptied. */
    @PluginMethod
    public void takePayments(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("json", ValiuStore.takePayments(getContext()));
        call.resolve(ret);
        WidgetUpdater.updateAll(getContext());
    }

    // ---------- widgets ----------
    @PluginMethod
    public void setWidgetData(PluginCall call) {
        String json = call.getString("json");
        if (json == null) { call.reject("json"); return; }
        ValiuStore.setWidgetData(getContext(), json);
        WidgetUpdater.updateAll(getContext());
        call.resolve();
    }

    /** Asks the launcher to place a widget ("budget" or "payments"). ok=false if the launcher can't. */
    @PluginMethod
    public void pinWidget(PluginCall call) {
        Context c = getContext();
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 26) {
            AppWidgetManager m = c.getSystemService(AppWidgetManager.class);
            Class<?> cls = "payments".equals(call.getString("kind")) ? PaymentsWidget.class : BudgetWidget.class;
            if (m != null && m.isRequestPinAppWidgetSupported()) ok = m.requestPinAppWidget(new ComponentName(c, cls), null, null);
        }
        JSObject ret = new JSObject();
        ret.put("ok", ok);
        call.resolve(ret);
    }

    @PluginMethod
    public void widgetCount(PluginCall call) {
        Context c = getContext();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        JSObject ret = new JSObject();
        ret.put("budget", m.getAppWidgetIds(new ComponentName(c, BudgetWidget.class)).length);
        ret.put("payments", m.getAppWidgetIds(new ComponentName(c, PaymentsWidget.class)).length);
        call.resolve(ret);
    }
}
