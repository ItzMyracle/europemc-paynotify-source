package com.europemc.paynotify;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** 1500 -> 1.5K, 10000000 -> 10M, 1000000000 -> 1B, 1000000000000 -> 1T */
public final class AmountFormat {
    private static final double[] DIV = {1e12, 1e9, 1e6, 1e3};
    private static final String[] SUF = {"T", "B", "M", "K"};
    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);

    private AmountFormat() {}

    /** +$1.5M / -$500 / $0 */
    public static String signed(double amount, String symbol) {
        String sign = amount > 0 ? "+" : amount < 0 ? "-" : "";
        return sign + symbol + abbreviate(Math.abs(amount));
    }

    public static String abbreviate(double amount) {
        double abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (abs < 1000) return sign + new DecimalFormat("#,##0.##", US).format(abs);

        int i = 0;
        while (i < DIV.length - 1 && abs < DIV[i]) i++;
        double v = Math.round(abs / DIV[i] * 100.0) / 100.0;
        if (v >= 1000 && i > 0) { // 999,999 -> 1M instead of 1000K
            i--;
            v = Math.round(abs / DIV[i] * 100.0) / 100.0;
        }
        return sign + new DecimalFormat("0.##", US).format(v) + SUF[i];
    }
}
