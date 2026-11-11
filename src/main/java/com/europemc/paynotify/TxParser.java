package com.europemc.paynotify;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Turns an ECONOMY chat line into "who paid whom how much". */
public final class TxParser {
    public record Parsed(boolean incoming, String player, double amount) {}

    private TxParser() {}

    public static Parsed parse(String raw, PayConfig cfg) {
        if (raw == null) return null;
        String msg = raw.replaceAll("\u00a7.", "").trim();
        if (!msg.toUpperCase(Locale.ROOT).contains(cfg.prefix.toUpperCase(Locale.ROOT))) return null;

        String lower = msg.toLowerCase(Locale.ROOT);
        boolean outgoing = containsAny(lower, cfg.outgoingKeywords);
        boolean incoming = !outgoing && (cfg.incomingKeywords.isEmpty() || containsAny(lower, cfg.incomingKeywords));
        if (!outgoing && !incoming) return null;

        OptionalDouble amount = PaymentParser.extractAmount(msg, cfg);
        if (amount.isEmpty()) return null;

        String body = msg.replaceFirst("(?i)" + Pattern.quote(cfg.prefix), " ");
        String player = findPlayer(body, incoming ? cfg.incomingPlayerPatterns : cfg.outgoingPlayerPatterns);
        return new Parsed(incoming, player, amount.getAsDouble());
    }

    private static boolean containsAny(String lower, List<String> keys) {
        for (String k : keys) {
            if (k != null && !k.isEmpty() && lower.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String findPlayer(String body, List<String> patterns) {
        for (String p : patterns) {
            try {
                Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(body);
                while (m.find()) {
                    if (m.groupCount() < 1) continue;
                    String name = m.group(1);
                    if (name == null) continue;
                    String n = name.toLowerCase(Locale.ROOT);
                    if (n.equals("you") || n.equals("your") || n.equals("paid") || n.equals("sent")) continue;
                    return name;
                }
            } catch (PatternSyntaxException ignored) {
                // bad user regex in the config: skip it
            }
        }
        return "Unknown";
    }
}
