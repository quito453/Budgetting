package com.valiugt.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/**
 * With the user's permission ("Notification access"), Android shows Valiu the notifications that arrive.
 * Valiu only keeps payment ones (Google Wallet or the bank): amount and merchant go to a local queue,
 * and the app adds them as expenses the next time it opens. Every other notification is ignored and never stored.
 */
public class PaymentListener extends NotificationListenerService {
    static final String CHANNEL = "valiu_compras";
    private static final int NOTICE_ID = 4711;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Throwable t) { /* never crash the listener */ }
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || !ValiuStore.captureOn(this)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || sbn.isOngoing()) return;
        Bundle ex = n.extras;
        if (ex == null) return;
        String title = text(ex.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String body = text(big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT));
        PaymentParser.Payment p = PaymentParser.parse(pkg, title, body);
        if (p == null) return;
        long ts = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        if (!ValiuStore.addPayment(this, p.amount, p.currency, p.merchant, pkg, ts)) return;
        WidgetUpdater.updateAll(this);
        notice(this, p);
    }

    private static String text(CharSequence c) { return c == null ? "" : c.toString(); }

    /** A quiet "Anoté Q45.00 · Pollo Campero" so the user knows it worked; tapping opens Valiu. */
    private static void notice(Context ctx, PaymentParser.Payment p) {
        JSONObject data = ValiuStore.widgetData(ctx);
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!nm.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, ValiuStore.str(data, "capChannel", "Compras anotadas"), NotificationManager.IMPORTANCE_LOW);
            NotificationManager sys = ctx.getSystemService(NotificationManager.class);
            if (sys != null) sys.createNotificationChannel(ch);
        }
        String amount = WidgetUpdater.money(data, p.amount, p.currency);
        String title = ValiuStore.str(data, "capTitle", "Anoté {x}").replace("{x}", amount)
            + (p.merchant.isEmpty() ? "" : " · " + p.merchant);
        String text = ValiuStore.str(data, "capText", "Toca para verlo en Valiu.");
        Intent open = new Intent(ctx, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ValiuPlugin.EXTRA_ACTION, "today");
        PendingIntent pi = PendingIntent.getActivity(ctx, 40, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_valiu)
            .setColor(0xFF1E8150)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        try { nm.notify(NOTICE_ID, b.build()); } catch (SecurityException e) { /* notifications not allowed */ }
    }
}
