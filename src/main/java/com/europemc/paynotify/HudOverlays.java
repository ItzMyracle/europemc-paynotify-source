package com.europemc.paynotify;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Top-right corner panel and the slim bar above the hotbar. Both can be switched off in the B menu. */
public final class HudOverlays {
    private static final int GREEN = 0xFF4ADE80, RED = 0xFFF87171, MUTED = 0xFF8A90AB, TEXT = 0xFFEAECF8, GOLD = 0xFFFFC83D;

    private HudOverlays() {}

    static String streak(int cur) {
        return cur > 0 ? "W" + cur : (cur < 0 ? "L" + (-cur) : "-");
    }

    static int streakColor(int cur) {
        return cur > 0 ? GREEN : (cur < 0 ? RED : MUTED);
    }

    static int profitColor(double p) {
        return p > 0 ? GREEN : (p < 0 ? RED : TEXT);
    }

    private static void rrect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    // ------------------------------------------------------------ corner panel

    public static class Corner implements HudElement {
        @Override
        public void render(DrawContext c, RenderTickCounter tick) {
            PayConfig cfg = PayNotifyMod.config;
            if (cfg == null || !cfg.showCornerHud || !cfg.trackingEnabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            int sw = mc.getWindow().getScaledWidth();

            Session act = Tracker.active();
            Stats all = Tracker.allTime();
            int w = 124;
            int x = sw - w - 6, y = 6;

            if (act == null) {
                rrect(c, x, y, w, 24, 0xB0101220);
                c.fill(x + 1, y + 2, x + 3, y + 22, 0xFF3A4060);
                c.drawText(tr, "No running session", x + 8, y + 4, MUTED, false);
                c.drawText(tr, "Press B to start one", x + 8, y + 14, 0xFF5A6080, false);
                return;
            }

            Stats ss = Tracker.stats(act);
            int accent = profitColor(ss.profit);
            int h = 52;
            rrect(c, x, y, w, h, 0xC0101220);
            c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, (accent & 0x00FFFFFF) | 0x20000000, 0x00000000);
            c.fill(x + 1, y + 3, x + 3, y + h - 3, accent);

            double blink = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 280.0);
            c.fill(x + 8, y + 5, x + 12, y + 9, ((int) (140 + 115 * blink) << 24) | 0x4ADE80);
            c.drawText(tr, "SESSION", x + 16, y + 4, MUTED, false);

            String profit = AmountFormat.signed(ss.profit, cfg.currencySymbol);
            var m = c.getMatrices();
            m.pushMatrix();
            m.translate(x + 8, y + 15);
            m.scale(1.4f, 1.4f);
            c.drawText(tr, profit, 0, 0, accent, true);
            m.popMatrix();

            int ly = y + 31;
            c.drawText(tr, "Streak ", x + 8, ly, MUTED, false);
            int px = x + 8 + tr.getWidth("Streak ");
            String s1 = streak(ss.curStreak);
            c.drawText(tr, s1, px, ly, streakColor(ss.curStreak), true);
            px += tr.getWidth(s1);
            c.drawText(tr, "  All-time ", px, ly, MUTED, false);
            px += tr.getWidth("  All-time ");
            c.drawText(tr, streak(all.curStreak), px, ly, streakColor(all.curStreak), true);

            c.drawText(tr, "Rounds " + ss.wins + "W " + ss.losses + "L", x + 8, y + 41, MUTED, false);
            int open = ss.openFlags();
            if (open > 0) {
                String f = open + " flagged";
                c.drawText(tr, f, x + w - 6 - tr.getWidth(f), y + 41, 0xFFFB923C, false);
            }
        }
    }

    // --------------------------------------------------------- bar over hotbar

    public static class HotbarBar implements HudElement {
        @Override
        public void render(DrawContext c, RenderTickCounter tick) {
            PayConfig cfg = PayNotifyMod.config;
            if (cfg == null || !cfg.showHotbarBar || !cfg.trackingEnabled) return;
            Session act = Tracker.active();
            if (act == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            Stats ss = Tracker.stats(act);
            Stats all = Tracker.allTime();
            String profit = AmountFormat.signed(ss.profit, cfg.currencySymbol);
            String sStreak = streak(ss.curStreak);
            String aStreak = streak(all.curStreak);

            String l1 = "SESSION ", l2 = "   STREAK ", l3 = "   ALL-TIME ";
            int total = tr.getWidth(l1) + tr.getWidth(profit) + tr.getWidth(l2) + tr.getWidth(sStreak)
                    + tr.getWidth(l3) + tr.getWidth(aStreak);
            int w = total + 16, h = 14;
            int x = (sw - w) / 2, y = sh - 66;

            rrect(c, x, y, w, h, 0xB0101220);
            c.fill(x + 3, y + h - 1, x + w - 3, y + h, (profitColor(ss.profit) & 0x00FFFFFF) | 0xE0000000);

            int px = x + 8, ty = y + 3;
            c.drawText(tr, l1, px, ty, MUTED, false);
            px += tr.getWidth(l1);
            c.drawText(tr, profit, px, ty, profitColor(ss.profit), true);
            px += tr.getWidth(profit);
            c.drawText(tr, l2, px, ty, MUTED, false);
            px += tr.getWidth(l2);
            c.drawText(tr, sStreak, px, ty, streakColor(ss.curStreak), true);
            px += tr.getWidth(sStreak);
            c.drawText(tr, l3, px, ty, MUTED, false);
            px += tr.getWidth(l3);
            c.drawText(tr, aStreak, px, ty, streakColor(all.curStreak), true);
        }
    }
}
