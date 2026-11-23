package com.valiugt.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a payment notification (Google Wallet or a bank app) and pulls out the amount and the merchant.
 * Pure Java on purpose: no Android classes, so it can be tested on its own.
 */
public final class PaymentParser {

    public static final class Payment {
        public final double amount;
        public final String currency;   // "GTQ", "USD" or "" when the text doesn't say
        public final String merchant;   // may be empty
        Payment(double amount, String currency, String merchant) { this.amount = amount; this.currency = currency; this.merchant = merchant; }
    }

    /** Apps whose payment notifications don't need a keyword: the title is the merchant. */
    static final String[] WALLET_APPS = { "com.google.android.apps.walletnfcrel" };

    /** Chats, mail and social apps: a message that mentions "pagué Q50" is not a purchase. */
    static final String[] SKIP_APPS = {
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca",
        "com.facebook.katana", "com.instagram.android", "com.google.android.gm", "com.microsoft.office.outlook",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.discord",
        "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.google.android.apps.dynamite", "com.slack", "com.microsoft.teams", "com.linkedin.android"
    };

    // Amount with the currency in front: Q45.00 · Q. 1,250.00 · GTQ 45 · US$12.50 · USD 12.50 · $12.50
    private static final Pattern AMOUNT_PRE = Pattern.compile(
        "(?<![A-Za-z0-9])(US\\$|USD|GTQ|Q\\.?|\\$)\\s?([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)(?![0-9])",
        Pattern.CASE_INSENSITIVE);
    // ...or after it: 45.00 GTQ · 12.50 USD · 45 quetzales
    private static final Pattern AMOUNT_POST = Pattern.compile(
        "(?<![0-9.,])([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s?(GTQ|USD|quetzales|dolares|dollars)\\b",
        Pattern.CASE_INSENSITIVE);

    // Words that say money went out (checked on text without accents, in lower case).
    private static final Pattern SPEND = Pattern.compile(
        "compra|pagaste|pago con|pago en|pago por|pagado en|cargo|consumo|transaccion|debito|autoriz|aprobad|"
        + "purchase|you paid|paid |spent|charged|payment at|payment to");
    // Words that say it is not a purchase: money in, refunds, rejected, codes, ads, own transfers.
    private static final Pattern NOT_SPEND = Pattern.compile(
        "recib|deposit|acredit|abono a|abonamos|reembols|devoluc|reversi|refund|received|rechaz|declin|deneg|no autoriz|no aprob|"
        + "fallid|failed|insuficiente|codigo|code|otp|token|clave|contrasena|password|verific|promo|oferta|descuento|gana |sorteo|cashback|"
        + "pago a tu tarjeta|pago de tu tarjeta|pago de tarjeta|pago a tarjeta|pago recibido|transferencia|transfer|retiro|withdraw|"
        + "recordatorio|vence|vencimiento|fecha de pago|pago minimo|estado de cuenta|reminder|due ");

    private static final Pattern MERCHANT_ES = Pattern.compile(
        "\\b(?:en|at|a)\\s+(.+?)(?=\\s+(?:con|el|por|usando|fecha|a las|hora|tarjeta|desde|via|on|with|using|el dia)\\b|[.](?=\\s|$)|[,;:!\\n(]|$)",
        Pattern.CASE_INSENSITIVE);

    // "Comercio: Shell" · "Establecimiento: Pollo Campero"
    private static final Pattern MERCHANT_LABEL = Pattern.compile(
        "\\b(?:comercio|establecimiento|merchant|negocio)\\s*:\\s*(.+?)(?=[.](?=\\s|$)|[,;\\n]|$)", Pattern.CASE_INSENSITIVE);

    private PaymentParser() {}

    public static boolean skipApp(String pkg) {
        if (pkg == null) return true;
        for (String s : SKIP_APPS) if (s.equals(pkg)) return true;
        return false;
    }
    static boolean walletApp(String pkg) {
        for (String s : WALLET_APPS) if (s.equals(pkg)) return true;
        return false;
    }

    /** Returns the payment, or null when the notification is not a purchase. */
    public static Payment parse(String pkg, String title, String text) {
        if (skipApp(pkg)) return null;
        String t = title == null ? "" : title.trim();
        String b = text == null ? "" : text.trim();
        String all = (t + "\n" + b).trim();
        if (all.isEmpty()) return null;
        String plain = plain(all);
        boolean wallet = walletApp(pkg);
        if (NOT_SPEND.matcher(plain).find()) return null;
        if (!wallet && !SPEND.matcher(plain).find()) return null;

        double amount = -1; String cur = ""; int end = -1;
        Matcher m = AMOUNT_PRE.matcher(all);
        if (m.find()) {
            amount = num(m.group(2)); cur = currency(m.group(1)); end = m.end();
        } else {
            Matcher p = AMOUNT_POST.matcher(all);
            if (p.find()) { amount = num(p.group(1)); cur = currency(p.group(2)); end = p.end(); }
        }
        if (!(amount > 0) || amount > 1_000_000) return null;

        String merchant = "";
        if (wallet && !t.isEmpty() && !AMOUNT_PRE.matcher(t).find()) merchant = t;
        if (merchant.isEmpty()) {
            // Look after the amount first ("Compra por Q45 en Pollo Campero"), then anywhere.
            merchant = findMerchant(all.substring(Math.max(0, end)));
            if (merchant.isEmpty()) merchant = findMerchant(all);
        }
        return new Payment(Math.round(amount * 100) / 100.0, cur, tidy(merchant));
    }

    private static String findMerchant(String s) {
        Matcher l = MERCHANT_LABEL.matcher(s);
        if (l.find() && plain(l.group(1)).matches(".*[a-z].*")) return l.group(1).trim();
        Matcher m = MERCHANT_ES.matcher(s);
        while (m.find()) {
            String c = m.group(1).trim();
            String pc = plain(c);
            if (!pc.matches(".*[a-z].*")) continue;
            if (pc.startsWith("tu ") || pc.startsWith("su ") || pc.startsWith("la cuenta") || pc.startsWith("linea") || pc.startsWith("tienda en linea")
                || pc.startsWith("q") && pc.matches("q\\.?\\s?[0-9].*") || pc.startsWith("$")) continue;
            return c;
        }
        return "";
    }

    static String tidy(String s) {
        if (s == null) return "";
        String x = s.replaceAll("[\\s\\u00a0]+", " ").replaceAll("^[\\-*•·\"']+|[\\-*•·\"']+$", "").trim();
        if (x.length() > 40) x = x.substring(0, 40).trim();
        // "SUPER LA TORRE" → "Super La Torre"; mixed case is left as the bank wrote it.
        if (x.length() > 3 && x.equals(x.toUpperCase(Locale.ROOT)) && x.matches(".*[A-Z].*")) {
            StringBuilder out = new StringBuilder();
            for (String w : x.toLowerCase(Locale.ROOT).split(" ")) {
                if (w.isEmpty()) continue;
                if (out.length() > 0) out.append(' ');
                out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            x = out.toString();
        }
        return x;
    }

    static String plain(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }
    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }
    private static String currency(String s) {
        String c = plain(s).replace(".", "");
        if (c.equals("q") || c.equals("gtq") || c.equals("quetzales")) return "GTQ";
        if (c.equals("us$") || c.equals("usd") || c.equals("dolares") || c.equals("dollars")) return "USD";
        return "";   // a bare "$": the user's own currency decides
    }
}
