package com.europemc.paynotify;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Animated payment pop-up drawn on the HUD: slide-in, glowing border, shimmer, sparkles, countdown bar. */
public class PayToastHud implements HudElement {
    private static final List<Notif> ACTIVE = new ArrayList<>();
    private static final int W = 200, H = 46, GAP = 6, MAX_VISIBLE = 4;
    private static final long ANIM_MS = 450;

    private record Notif(double amount, String symbol, long start, long duration, String title, int accent) {}

    public static void push(double amount, String symbol, long durationMs) {
        ACTIVE.add(new Notif(amount, symbol, System.currentTimeMillis(), Math.max(1500, durationMs), null, 0));
        while (ACTIVE.size() > MAX_VISIBLE) ACTIVE.remove(0);
    }

    /** Red/orange warning pop-up (e.g. someone paid under the minimum bet). */
    public static void pushFlag(String title, int accentRgb, double amount, String symbol, long durationMs) {
        ACTIVE.add(new Notif(amount, symbol, System.currentTimeMillis(), Math.max(1500, durationMs), title, accentRgb));
        while (ACTIVE.size() > MAX_VISIBLE) ACTIVE.remove(0);
    }

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(n -> now - n.start() > n.duration());
        int slot = 0;
        for (Notif n : ACTIVE) {
            drawOne(ctx, tr, sw, n, now, slot++);
        }
    }

    private static void drawOne(DrawContext ctx, TextRenderer tr, int sw, Notif n, long now, int slot) {
        long age = now - n.start();
        float inT = Math.min(1f, age / (float) ANIM_MS);
        float outT = Math.max(0f, (age - (n.duration() - ANIM_MS)) / (float) ANIM_MS);
        float slideIn = easeOutBack(inT);
        float yBase = 8 + slot * (H + GAP);
        float hide = -(yBase + H + 6);
        int x = (sw - W) / 2;
        int y = Math.round(yBase + hide * ((1f - slideIn) + outT * outT));

        double amt = n.amount();
        int accent;
        String title;
        ItemStack icon;
        if (amt >= 1e12) { accent = rainbow(now); title = "JACKPOT!!!"; icon = new ItemStack(Items.NETHER_STAR); }
        else if (amt >= 1e9) { accent = 0x55FFFF; title = "HUGE PAYMENT!"; icon = new ItemStack(Items.DIAMOND); }
        else if (amt >= 1e6) { accent = 0x55FF55; title = "BIG PAYMENT!"; icon = new ItemStack(Items.EMERALD); }
        else if (amt >= 1e3) { accent = 0xFFAA00; title = "PAYMENT RECEIVED"; icon = new ItemStack(Items.GOLD_INGOT); }
        else { accent = 0xFFAA00; title = "PAYMENT RECEIVED"; icon = new ItemStack(Items.GOLD_NUGGET); }

        boolean flagged = n.title() != null;
        if (flagged) { accent = n.accent(); title = n.title(); icon = new ItemStack(Items.BARRIER); }

        // soft shadow + background
        ctx.fill(x + 2, y + 3, x + W + 2, y + H + 3, 0x55000000);
        ctx.fillGradient(x, y, x + W, y + H, 0xF0161626, 0xF00A0A12);

        // pulsing glowing border
        double pulse = 0.5 + 0.5 * Math.sin(now / 170.0);
        int a1 = (int) (150 + 105 * pulse);
        int a2 = (int) (30 + 50 * pulse);
        outline(ctx, x, y, x + W, y + H, argb(a1, accent));
        outline(ctx, x - 1, y - 1, x + W + 1, y + H + 1, argb(a2, accent));

        // accent bar on the left
        ctx.fill(x + 1, y + 1, x + 4, y + H - 1, argb(255, accent));

        // shimmer sweep
        float p = (age % 1400) / 1400f;
        int sx1 = x - 20 + Math.round(p * (W + 40));
        int sx2 = sx1 + 16;
        sx1 = Math.max(sx1, x + 4);
        sx2 = Math.min(sx2, x + W - 1);
        if (sx2 > sx1) ctx.fill(sx1, y + 1, sx2, y + H - 1, 0x22FFFFFF);

        // icon (bobbing) on a dark tile
        int bob = (int) Math.round(Math.sin(now / 220.0) * 1.5);
        ctx.fill(x + 9, y + 12, x + 31, y + 34, 0x55000000);
        outline(ctx, x + 9, y + 12, x + 31, y + 34, argb(90, accent));
        ctx.drawItem(icon, x + 12, y + 15 + bob);

        // title
        ctx.drawText(tr, Text.literal(title).formatted(Formatting.BOLD), x + 38, y + 7, argb(255, accent), true);

        // big abbreviated amount
        String amountText = (flagged ? "" : "+") + n.symbol() + AmountFormat.abbreviate(amt);
        float scale = Math.min(2f, 150f / Math.max(1, tr.getWidth(amountText)));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + 38, y + 19);
        m.scale(scale, scale);
        ctx.drawText(tr, amountText, 0, 0, 0xFFFFFFFF, true);
        m.popMatrix();

        // sparkles
        for (int i = 0; i < 6; i++) {
            float life = ((age + i * 260L) % 1500) / 1500f;
            int spx = x + 40 + (i * 37) % (W - 50);
            int spy = y + H - 6 - Math.round(life * (H - 10));
            int sa = (int) (220 * (1f - life));
            if (sa > 12) ctx.fill(spx, spy, spx + 2, spy + 2, argb(sa, 0xFFFFFF));
        }

        // countdown bar
        float remaining = Math.max(0f, 1f - age / (float) n.duration());
        ctx.fill(x + 4, y + H - 4, x + W - 4, y + H - 2, 0x33FFFFFF);
        ctx.fill(x + 4, y + H - 4, x + 4 + Math.round((W - 8) * remaining), y + H - 2, argb(255, accent));
    }

    private static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int rainbow(long now) {
        float h = (now % 2400) / 2400f * 6f;
        int i = (int) h;
        float f = h - i, q = 1f - f;
        float r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; }
            case 1 -> { r = q; g = 1; }
            case 2 -> { g = 1; b = f; }
            case 3 -> { g = q; b = 1; }
            case 4 -> { r = f; b = 1; }
            default -> { r = 1; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
