package com.europemc.paynotify;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Adds clickable [0.5x] [payback] [1.5x] [2x] [3x] [4x] [bal] buttons to the end of an incoming payment line. */
public final class ChatButtons {
    private ChatButtons() {}

    public static Text decorate(Text original, TxParser.Parsed p, PayConfig cfg) {
        if (!cfg.chatButtons || !p.incoming() || p.player() == null || p.player().equals("Unknown")) return original;
        MutableText out = Text.empty().append(original);
        for (String raw : cfg.buttons) {
            Text b = button(raw, p, cfg);
            if (b == null) continue;
            out.append(Text.literal(" "));
            out.append(b);
        }
        return out;
    }

    private static Text button(String raw, TxParser.Parsed p, PayConfig cfg) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String command;
        String hover;
        Formatting color;

        if (t.equals("bal")) {
            command = cfg.balCommand + " " + p.player();
            hover = "Check " + p.player() + "'s balance  (" + command + ")";
            color = Formatting.LIGHT_PURPLE;
        } else {
            double mult;
            if (t.equals("payback")) {
                mult = 1.0;
                color = Formatting.GREEN;
            } else if (t.endsWith("x")) {
                try {
                    mult = Double.parseDouble(t.substring(0, t.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
                color = mult <= 2.0 ? Formatting.GOLD : Formatting.RED;
            } else {
                return null;
            }
            if (mult <= 0) return null;
            long amount = Math.round(p.amount() * mult);
            if (amount < 1) return null;
            command = cfg.payCommand + " " + p.player() + " " + amount;
            hover = "Pay " + p.player() + " " + cfg.currencySymbol + AmountFormat.abbreviate(amount) + "  (" + command + ")";
        }

        ClickEvent click;
        if (cfg.buttonsRunCommand) click = new ClickEvent.RunCommand(command);
        else click = new ClickEvent.SuggestCommand(command);

        Style style = Style.EMPTY.withColor(color)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover)));
        return Text.literal("[" + t + "]").setStyle(style);
    }
}
