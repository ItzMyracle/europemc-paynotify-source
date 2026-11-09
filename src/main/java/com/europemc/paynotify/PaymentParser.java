package com.europemc.paynotify;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentParser {
    private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kKmMbBtT])?(?![\\w])";
    private static final Pattern ANY_NUMBER = Pattern.compile("(?<![\\w.])" + NUM);

    private PaymentParser() {}

    /** Returns the incoming amount if this message is an incoming ECONOMY payment, otherwise empty. */
    public static OptionalDouble parseIncoming(String raw, PayConfig cfg) {
        if (raw == null) return OptionalDouble.empty();
        String msg = raw.replaceAll("§.", "").trim();

        if (!msg.toUpperCase(Locale.ROOT).contains(cfg.prefix.toUpperCase(Locale.ROOT))) {
            return OptionalDouble.empty();
        }

        if (!cfg.incomingKeywords.isEmpty()) {
            String lower = msg.toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String k : cfg.incomingKeywords) {
                if (!k.isEmpty() && lower.contains(k.toLowerCase(Locale.ROOT))) { match = true; break; }
            }
            if (!match) return OptionalDouble.empty();
        }

        // Remove the prefix so it can't interfere
        String body = msg.replaceFirst("(?i)" + Pattern.quote(cfg.prefix), " ");

        // 1) Prefer a number right next to the currency symbol ($1,500 / 1,500$)
        if (cfg.currencySymbol != null && !cfg.currencySymbol.isEmpty()) {
            String sym = Pattern.quote(cfg.currencySymbol);
            Matcher m = Pattern.compile(sym + "\\s*" + NUM).matcher(body);
            if (m.find()) return OptionalDouble.of(toDouble(m.group(1), m.group(2)));
            m = Pattern.compile("(?<![\\w.])" + NUM.replace("(?![\\w])", "") + "\\s*" + sym).matcher(body);
            if (m.find()) return OptionalDouble.of(toDouble(m.group(1), m.group(2)));
        }

        // 2) Otherwise the first standalone number
        Matcher m = ANY_NUMBER.matcher(body);
        if (m.find()) return OptionalDouble.of(toDouble(m.group(1), m.group(2)));

        return OptionalDouble.empty();
    }

    /** Parses a typed amount like 5000, 250k, 1.5M, 2B, 1T. */
    public static OptionalDouble parseAmount(String text) {
        if (text == null) return OptionalDouble.empty();
        Matcher m = Pattern.compile("^\\s*" + NUM.replace("(?![\\w])", "") + "\\s*$").matcher(text);
        if (!m.matches()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(toDouble(m.group(1), m.group(2)));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private static double toDouble(String num, String suffix) {
        double v = Double.parseDouble(num.replace(",", ""));
        if (suffix != null) {
            switch (Character.toLowerCase(suffix.charAt(0))) {
                case 'k' -> v *= 1_000;
                case 'm' -> v *= 1_000_000;
                case 'b' -> v *= 1_000_000_000;
                case 't' -> v *= 1_000_000_000_000.0;
            }
        }
        return v;
    }
}
