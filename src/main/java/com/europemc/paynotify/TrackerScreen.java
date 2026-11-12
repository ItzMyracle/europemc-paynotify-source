package com.europemc.paynotify;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The big gamble tracker window (press B). Everything is drawn by hand for a custom look. */
public class TrackerScreen extends Screen {
    private static final int VW = 500, VH = 310;

    static final int PANEL = 0xFF0C0E17, CARD = 0xFF1B1F31, CARD2 = 0xFF262B45, LINE = 0xFF2E3454;
    static final int GOLD = 0xFFFFC83D, GREEN = 0xFF4ADE80, RED = 0xFFF87171, MUTED = 0xFF8A90AB;
    static final int TEXT = 0xFFEAECF8, BLUE = 0xFF60A5FA, ORANGE = 0xFFFB923C, GRAY = 0xFF3A4060;

    private enum Tab { OVERVIEW, TRANSACTIONS, FLAGGED, SETTINGS }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    private static final class Field {
        final boolean numeric;
        final int max;
        final Supplier<String> source;
        final Consumer<String> commit;
        String text = "";

        Field(boolean numeric, int max, Supplier<String> source, Consumer<String> commit) {
            this.numeric = numeric;
            this.max = max;
            this.source = source;
            this.commit = commit;
        }
    }

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH);

    private TextRenderer tr;
    private final List<Hit> hits = new ArrayList<>();
    private Tab tab = Tab.OVERVIEW;
    private Session selected;
    private boolean allTime;
    private int sessScroll, txScroll, flagScroll;
    private final long openStart = System.currentTimeMillis();
    private int vmx, vmy;
    private float sc = 1f, ox, oy;
    private double displayProfit;
    private boolean snapProfit = true;
    private Field focus;
    private long confirmDeleteUntil, copiedAt;
    private String copiedKey = "";
    private final Field fMin, fMax, fNotify, fName;

    public TrackerScreen() {
        super(Text.literal("Gamble Tracker"));
        Session a = Tracker.active();
        if (a != null) selected = a;
        else if (!Tracker.sessions.isEmpty()) selected = Tracker.sessions.get(Tracker.sessions.size() - 1);
        allTime = selected == null;

        fMin = new Field(true, 16, () -> AmountFormat.abbreviate(cfg().betMin), t -> {
            OptionalDouble v = PaymentParser.parseAmount(t);
            if (v.isPresent()) { cfg().betMin = v.getAsDouble(); changed(); }
        });
        fMax = new Field(true, 16, () -> cfg().betMax <= 0 ? "0" : AmountFormat.abbreviate(cfg().betMax), t -> {
            OptionalDouble v = PaymentParser.parseAmount(t);
            if (v.isPresent()) { cfg().betMax = v.getAsDouble(); changed(); }
        });
        fNotify = new Field(true, 16, () -> AmountFormat.abbreviate(cfg().minAmount), t -> {
            OptionalDouble v = PaymentParser.parseAmount(t);
            if (v.isPresent()) { cfg().minAmount = v.getAsDouble(); changed(); }
        });
        fName = new Field(false, 22, () -> selected != null ? selected.name : "(All-Time)", t -> {
            if (selected != null && !t.isBlank()) { selected.name = t.trim(); changed(); }
        });
    }

    private static PayConfig cfg() { return PayNotifyMod.config; }

    private void changed() {
        cfg().save();
        Tracker.invalidate();
        Tracker.save();
        snapProfit = true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        commitFocus();
        Tracker.save();
        super.close();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        commitFocus();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (vmx >= h.x1() && vmx < h.x2() && vmy >= h.y1() && vmy < h.y2()) {
                h.action().run();
                break;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int dir = vertical > 0 ? -1 : 1;
        if (vmx >= 10 && vmx < 150) sessScroll += dir;
        else if (tab == Tab.TRANSACTIONS) txScroll += dir * 2;
        else if (tab == Tab.FLAGGED) flagScroll += dir;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.getKeycode();
        if (focus != null) {
            if (input.isEscape()) { focus = null; return true; }
            if (input.isEnter()) { commitFocus(); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!focus.text.isEmpty()) focus.text = focus.text.substring(0, focus.text.length() - 1);
                return true;
            }
            if (input.isPaste()) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) for (char ch : clip.toCharArray()) append(ch);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_B) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focus == null || !input.isValidChar()) return super.charTyped(input);
        for (char ch : input.asString().toCharArray()) append(ch);
        return true;
    }

    private void append(char ch) {
        if (focus == null || focus.text.length() >= focus.max) return;
        if (focus.numeric) {
            if ("0123456789.,kKmMbBtT".indexOf(ch) < 0) return;
        } else if (ch < 32 || ch == 127) {
            return;
        }
        focus.text += ch;
    }

    private void commitFocus() {
        Field f = focus;
        focus = null;
        if (f != null) f.commit.accept(f.text);
    }

    private void setTab(Tab t) {
        commitFocus();
        tab = t;
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        tr = MinecraftClient.getInstance().textRenderer;
        long now = System.currentTimeMillis();
        float open = Math.min(1f, (now - openStart) / 240f);
        float e = 1f - (1f - open) * (1f - open) * (1f - open);

        c.fillGradient(0, 0, width, height, ((int) (170 * e) << 24) | 0x05060C, ((int) (215 * e) << 24) | 0x0B0D18);

        float base = Math.min(1f, Math.min((width - 12) / (float) VW, (height - 12) / (float) VH));
        sc = base * (0.93f + 0.07f * e);
        ox = (width - VW * sc) / 2f;
        oy = (height - VH * sc) / 2f;
        vmx = (int) ((mouseX - ox) / sc);
        vmy = (int) ((mouseY - oy) / sc);
        hits.clear();

        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(sc, sc);
        drawAll(c, now);
        m.popMatrix();
    }

    private void drawAll(DrawContext c, long now) {
        PayConfig cfg = cfg();

        // frame + glow
        rrect(c, 0, 0, VW, VH, PANEL);
        double pulse = 0.5 + 0.5 * Math.sin(now / 600.0);
        outline(c, -1, -1, VW + 1, VH + 1, a((int) (60 + 90 * pulse), GOLD));

        // header
        c.fillGradient(1, 1, VW - 1, 34, 0xFF2A2E4A, 0xFF171A2B);
        c.fill(1, 34, VW - 1, 35, a(220, GOLD));
        big(c, "EUROPEMC", 14, 9, GOLD, 1.4f);
        txt(c, "GAMBLE TRACKER", 14 + (int) (tr.getWidth("EUROPEMC") * 1.4f) + 7, 13, MUTED);

        Session act = Tracker.active();
        int pillX = 232;
        rrect(c, pillX, 10, 190, 16, CARD);
        if (act != null) {
            double blink = 0.5 + 0.5 * Math.sin(now / 280.0);
            c.fill(pillX + 6, 15, pillX + 12, 21, a((int) (140 + 115 * blink), 0x4ADE80));
            txt(c, "LIVE  " + trim(act.name, 150), pillX + 17, 14, GREEN);
        } else {
            c.fill(pillX + 6, 15, pillX + 12, 21, GRAY);
            txt(c, "No running session", pillX + 17, 14, MUTED);
        }
        btn(c, VW - 30, 9, 20, 18, "X", 0xFF7F2D3A, this::close);

        drawSidebar(c, now);

        // tabs
        String[] names = {"OVERVIEW", "TRANSACTIONS", "FLAGGED", "SETTINGS"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = 158 + i * 84;
            boolean on = tab == tabs[i];
            boolean hov = inside(tx, 44, 80, 18);
            rrect(c, tx, 44, 80, 18, on ? CARD2 : (hov ? CARD : 0xFF12141F));
            if (on) c.fill(tx + 4, 60, tx + 76, 62, GOLD);
            String label = names[i];
            if (tabs[i] == Tab.FLAGGED) {
                int open = openFlagCount();
                if (open > 0) label += " (" + open + ")";
            }
            txtC(c, label, tx + 40, 49, on ? TEXT : MUTED);
            final Tab target = tabs[i];
            hit(tx, 44, 80, 18, () -> setTab(target));
        }

        Stats st = viewStats();
        switch (tab) {
            case OVERVIEW -> drawOverview(c, st, now);
            case TRANSACTIONS -> drawTransactions(c, st);
            case FLAGGED -> drawFlagged(c, st, now);
            case SETTINGS -> drawSettings(c, cfg);
        }
    }

    private Stats viewStats() {
        if (allTime || selected == null) return Tracker.allTime();
        return Tracker.stats(selected);
    }

    private int openFlagCount() {
        return viewStats().openFlags();
    }

    // ---------------------------------------------------------------- sidebar

    private void drawSidebar(DrawContext c, long now) {
        PayConfig cfg = cfg();
        rrect(c, 10, 44, 140, 256, CARD);
        txt(c, "SESSIONS", 18, 52, MUTED);
        btn(c, 18, 64, 124, 18, "+ New Session", 0xFF1F8F52, () -> {
            selected = Tracker.startSession(null);
            allTime = false;
            snapProfit = true;
            txScroll = flagScroll = 0;
            Tracker.save();
        });

        // all-time row
        boolean atSel = allTime;
        rowBg(c, 18, 86, 124, 20, atSel, inside(18, 86, 124, 20));
        txtS(c, "All-Time", 26, 92, atSel ? GOLD : TEXT);
        double allProfit = Tracker.allTime().profit;
        txtR(c, AmountFormat.signed(allProfit, cfg.currencySymbol), 138, 92, colorFor(allProfit));
        hit(18, 86, 124, 20, () -> { allTime = true; snapProfit = true; txScroll = flagScroll = 0; });

        List<Session> list = new ArrayList<>(Tracker.sessions);
        Collections.reverse(list);
        int rows = 6;
        sessScroll = Math.max(0, Math.min(sessScroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && i + sessScroll < list.size(); i++) {
            Session s = list.get(i + sessScroll);
            int y = 110 + i * 26;
            boolean sel = !allTime && s == selected;
            rowBg(c, 18, y, 124, 24, sel, inside(18, y, 124, 24));
            if (s.isActive()) c.fill(21, y + 4, 24, y + 20, GREEN);
            txtS(c, trim(s.name, 70), 28, y + 3, sel ? GOLD : TEXT);
            txt(c, DATE.format(new Date(s.start)), 28, y + 13, MUTED);
            double p = Tracker.stats(s).profit;
            txtR(c, AmountFormat.signed(p, cfg.currencySymbol), 138, y + 8, colorFor(p));
            hit(18, y, 124, 24, () -> {
                selected = s;
                allTime = false;
                snapProfit = true;
                txScroll = flagScroll = 0;
            });
        }
        if (list.isEmpty()) {
            txtC(c, "No sessions yet", 80, 160, MUTED);
            txtC(c, "Press New Session", 80, 172, MUTED);
        }
        if (list.size() > rows) {
            int trackH = rows * 26;
            int barH = Math.max(12, trackH * rows / list.size());
            int barY = 110 + (trackH - barH) * sessScroll / Math.max(1, list.size() - rows);
            c.fill(145, 110, 148, 110 + trackH, 0xFF12141F);
            c.fill(145, barY, 148, barY + barH, GRAY);
        }

        // end / delete
        boolean canEnd = selected != null && selected.isActive() && !allTime;
        if (canEnd) btn(c, 18, 274, 58, 18, "End", 0xFF8A5A14, () -> {
            Tracker.endSession();
            Tracker.save();
        });
        else btnOff(c, 18, 274, 58, 18, "End");

        boolean canDel = selected != null && !allTime;
        boolean confirm = System.currentTimeMillis() < confirmDeleteUntil;
        if (canDel) btn(c, 82, 274, 60, 18, confirm ? "Sure?" : "Delete", confirm ? 0xFFB02A3C : 0xFF7F2D3A, () -> {
            if (System.currentTimeMillis() < confirmDeleteUntil) {
                Session d = selected;
                Tracker.delete(d);
                selected = Tracker.active() != null ? Tracker.active()
                        : (Tracker.sessions.isEmpty() ? null : Tracker.sessions.get(Tracker.sessions.size() - 1));
                allTime = selected == null;
                confirmDeleteUntil = 0;
                snapProfit = true;
                Tracker.save();
            } else {
                confirmDeleteUntil = System.currentTimeMillis() + 3000;
            }
        });
        else btnOff(c, 82, 274, 60, 18, "Delete");
    }

    // --------------------------------------------------------------- overview

    private void drawOverview(DrawContext c, Stats st, long now) {
        PayConfig cfg = cfg();
        String sym = cfg.currencySymbol;
        final int X = 158;

        if (snapProfit) { displayProfit = st.profit; snapProfit = false; }
        displayProfit += (st.profit - displayProfit) * 0.18;
        if (Math.abs(st.profit - displayProfit) < 0.5) displayProfit = st.profit;

        // row 1
        card(c, X, 70, 106, 46, colorFor(st.profit));
        txt(c, allTime ? "PROFIT (ALL-TIME)" : "PROFIT", X + 8, 76, MUTED);
        big(c, AmountFormat.signed(displayProfit, sym), X + 8, 89, colorFor(st.profit), 1.6f);
        txt(c, "in - out", X + 8, 105, MUTED);

        card(c, X + 113, 70, 106, 46, GREEN);
        txt(c, "MONEY IN", X + 121, 76, MUTED);
        big(c, sym + AmountFormat.abbreviate(st.in), X + 121, 89, GREEN, 1.6f);
        txt(c, cfg.isHost() ? st.bets + " bets received" : "payouts received", X + 121, 105, MUTED);

        card(c, X + 226, 70, 106, 46, RED);
        txt(c, "MONEY OUT", X + 234, 76, MUTED);
        big(c, sym + AmountFormat.abbreviate(st.out), X + 234, 89, RED, 1.6f);
        txt(c, cfg.isHost() ? "payouts + refunds" : st.bets + " bets sent", X + 234, 105, MUTED);

        // row 2
        card(c, X, 122, 106, 46, BLUE);
        txt(c, "ROUNDS", X + 8, 128, MUTED);
        big(c, st.wins + "W  " + st.losses + "L", X + 8, 141, TEXT, 1.4f);
        txt(c, st.pushes + " push, " + st.pending + " pending", X + 8, 157, MUTED);

        card(c, X + 113, 122, 106, 46, GOLD);
        txt(c, "WIN RATE", X + 121, 128, MUTED);
        big(c, String.format(Locale.US, "%.0f%%", st.winRate()), X + 121, 141, GOLD, 1.4f);
        int bw = 90;
        c.fill(X + 121, 160, X + 121 + bw, 163, GRAY);
        if (st.wins + st.losses > 0) c.fill(X + 121, 160, X + 121 + (int) (bw * st.winRate() / 100.0), 163, GREEN);

        card(c, X + 226, 122, 106, 46, ORANGE);
        txt(c, "BIGGEST WIN", X + 234, 128, MUTED);
        txt(c, "+" + sym + AmountFormat.abbreviate(st.biggestWin), X + 234, 139, GREEN);
        txt(c, "BIGGEST LOSS", X + 234, 149, MUTED);
        txt(c, "-" + sym + AmountFormat.abbreviate(st.biggestLoss), X + 234, 159, RED);

        // streaks
        Session sessForStreak = selected != null && !allTime ? selected : Tracker.active();
        Stats sessStats = sessForStreak != null ? Tracker.stats(sessForStreak) : new Stats();
        streakCard(c, X, 174, 162, 56, "SESSION STREAK", sessStats);
        streakCard(c, X + 170, 174, 162, 56, "ALL-TIME STREAK", Tracker.allTime());

        // form strip
        txt(c, "FORM (last 24 rounds)", X, 237, MUTED);
        int n = st.form.size();
        int from = Math.max(0, n - 24);
        for (int i = from; i < n; i++) {
            int v = st.form.get(i);
            int col = v > 0 ? GREEN : (v < 0 ? RED : GRAY);
            int px = X + (i - from) * 13;
            rrect(c, px, 249, 11, 11, col);
        }
        if (n == 0) txt(c, "No finished rounds yet", X, 251, GRAY);

        // footer
        int under = 0, over = 0;
        for (Stats.Flag f : st.flags) { if (f.under) under++; else over++; }
        String limits = "Limits: min " + sym + AmountFormat.abbreviate(cfg.betMin) + "  max "
                + (cfg.betMax <= 0 ? "none" : sym + AmountFormat.abbreviate(cfg.betMax)) + "  ("
                + (cfg.isHost() ? "HOST" : "PLAYER") + ")";
        txt(c, limits, X, 270, MUTED);
        if (under + over > 0) {
            txt(c, "Flagged: " + under + " under, " + over + " over, " + st.openFlags() + " need refund", X, 283, ORANGE);
        } else {
            txt(c, "No payments outside your limits", X, 283, GRAY);
        }
    }

    private void streakCard(DrawContext c, int x, int y, int w, int h, String title, Stats s) {
        int cur = s.curStreak;
        int col = cur > 0 ? GREEN : (cur < 0 ? RED : MUTED);
        card(c, x, y, w, h, col);
        txt(c, title, x + 8, y + 6, MUTED);
        String big = cur > 0 ? "W" + cur : (cur < 0 ? "L" + (-cur) : "-");
        big(c, big, x + 8, y + 18, col, 2.2f);
        txt(c, cur > 0 ? "WIN STREAK" : (cur < 0 ? "LOSING STREAK" : "NO STREAK"), x + 8, y + h - 12, col);
        txtR(c, "Best win " + s.bestWin, x + w - 8, y + 18, GREEN);
        txtR(c, "Worst loss " + s.worstLoss, x + w - 8, y + 30, RED);
        int blocks = Math.min(10, Math.abs(cur));
        for (int i = 0; i < blocks; i++) {
            rrect(c, x + w - 8 - 7 * (blocks - i), y + h - 12, 5, 6, col);
        }
    }

    // ----------------------------------------------------------- transactions

    private void drawTransactions(DrawContext c, Stats st) {
        PayConfig cfg = cfg();
        final int X = 158;
        rrect(c, X, 70, 332, 230, CARD);
        txt(c, "TIME", X + 8, 76, MUTED);
        txt(c, "TYPE", X + 62, 76, MUTED);
        txt(c, "PLAYER", X + 100, 76, MUTED);
        txtR(c, "AMOUNT", X + 252, 76, MUTED);
        txt(c, "TAG", X + 266, 76, MUTED);
        c.fill(X + 4, 87, X + 328, 88, LINE);

        List<Stats.TxView> list = new ArrayList<>(st.views);
        Collections.reverse(list);
        int rows = 12, rh = 17;
        txScroll = Math.max(0, Math.min(txScroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            txtC(c, "No transactions yet", X + 166, 170, MUTED);
            txtC(c, "Payments that say " + cfg.prefix + " show up here", X + 166, 183, GRAY);
        }
        for (int i = 0; i < rows && i + txScroll < list.size(); i++) {
            Stats.TxView v = list.get(i + txScroll);
            Tx t = v.tx();
            int y = 90 + i * rh;
            if (i % 2 == 0) c.fill(X + 4, y - 1, X + 328, y + rh - 2, 0x22FFFFFF);
            txt(c, TIME.format(new Date(t.time)), X + 8, y + 3, MUTED);
            chip(c, X + 60, y + 1, 30, t.incoming ? "IN" : "OUT", t.incoming ? GREEN : RED);
            txt(c, trim(t.player, 90), X + 100, y + 3, TEXT);
            txtR(c, (t.incoming ? "+" : "-") + cfg.currencySymbol + AmountFormat.abbreviate(t.amount), X + 252, y + 3,
                    t.incoming ? GREEN : RED);
            chip(c, X + 264, y + 1, 54, v.tag(), tagColor(v.tag()));
        }
        scrollbar(c, X + 328, 90, rows * rh, list.size(), rows, txScroll);
    }

    // ---------------------------------------------------------------- flagged

    private void drawFlagged(DrawContext c, Stats st, long now) {
        PayConfig cfg = cfg();
        String sym = cfg.currencySymbol;
        final int X = 158;
        int under = 0, over = 0;
        double underSum = 0, overSum = 0;
        for (Stats.Flag f : st.flags) {
            if (f.under) { under++; underSum += f.tx.amount; } else { over++; overSum += f.tx.amount; }
        }
        card(c, X, 70, 106, 36, ORANGE);
        txt(c, "UNDER MIN (< " + sym + AmountFormat.abbreviate(cfg.betMin) + ")", X + 8, 75, MUTED);
        txt(c, under + "x  " + sym + AmountFormat.abbreviate(underSum), X + 8, 90, ORANGE);
        card(c, X + 113, 70, 106, 36, RED);
        txt(c, cfg.betMax <= 0 ? "OVER MAX (no limit)" : "OVER MAX (> " + sym + AmountFormat.abbreviate(cfg.betMax) + ")", X + 121, 75, MUTED);
        txt(c, over + "x  " + sym + AmountFormat.abbreviate(overSum), X + 121, 90, RED);
        card(c, X + 226, 70, 106, 36, GOLD);
        txt(c, "NEED REFUND", X + 234, 75, MUTED);
        txt(c, st.openFlags() + " open", X + 234, 90, GOLD);

        rrect(c, X, 112, 332, 188, CARD);
        if (!cfg.isHost()) {
            txtC(c, "Flagging only works in HOST mode", X + 166, 190, MUTED);
            txtC(c, "(Settings > Role)", X + 166, 203, GRAY);
            return;
        }
        List<Stats.Flag> list = new ArrayList<>(st.flags);
        Collections.reverse(list);
        int rows = 8, rh = 22;
        flagScroll = Math.max(0, Math.min(flagScroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            txtC(c, "Nobody paid outside your limits", X + 166, 195, MUTED);
            txtC(c, "Change limits in Settings", X + 166, 208, GRAY);
        }
        for (int i = 0; i < rows && i + flagScroll < list.size(); i++) {
            Stats.Flag f = list.get(i + flagScroll);
            int y = 116 + i * rh;
            boolean hov = inside(X + 4, y, 324, rh - 2);
            rrect(c, X + 4, y, 324, rh - 2, hov && !f.refunded ? CARD2 : 0xFF161A2A);
            chip(c, X + 9, y + 4, 40, f.under ? "UNDER" : "OVER", f.under ? ORANGE : RED);
            txtS(c, trim(f.tx.player, 78), X + 56, y + 3, TEXT);
            txt(c, TIME.format(new Date(f.tx.time)), X + 56, y + 12, MUTED);
            txtR(c, sym + AmountFormat.abbreviate(f.tx.amount), X + 190, y + 3, f.under ? ORANGE : RED);
            double diff = f.under ? f.limit - f.tx.amount : f.tx.amount - f.limit;
            txtR(c, (f.under ? "-" : "+") + sym + AmountFormat.abbreviate(diff) + (f.under ? " short" : " over"), X + 190, y + 12, MUTED);
            String key = f.tx.time + f.tx.player;
            if (f.refunded) {
                chip(c, X + 238, y + 5, 80, "REFUNDED", GREEN);
            } else {
                boolean copied = key.equals(copiedKey) && System.currentTimeMillis() - copiedAt < 1500;
                chip(c, X + 238, y + 5, 80, copied ? "COPIED!" : "COPY /pay", copied ? GREEN : GOLD);
                final Stats.Flag ff = f;
                hit(X + 4, y, 324, rh - 2, () -> {
                    MinecraftClient.getInstance().keyboard.setClipboard("/pay " + ff.tx.player + " " + plain(ff.tx.amount));
                    copiedKey = key;
                    copiedAt = System.currentTimeMillis();
                });
            }
        }
        scrollbar(c, X + 328, 116, rows * rh, list.size(), rows, flagScroll);
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    // --------------------------------------------------------------- settings

    private void drawSettings(DrawContext c, PayConfig cfg) {
        final int X = 158;
        rrect(c, X, 70, 332, 230, CARD);

        // left column: fields
        txt(c, "BET LIMITS", X + 10, 77, GOLD);
        labelField(c, "Minimum bet", fMin, X + 10, 90, 150);
        labelField(c, "Maximum bet (0 = no limit)", fMax, X + 10, 124, 150);
        labelField(c, "Pop-up only from", fNotify, X + 10, 158, 150);
        labelField(c, "Session name", fName, X + 10, 192, 150);

        txt(c, "Payout window (s)", X + 10, 226, MUTED);
        btn(c, X + 10, 238, 20, 16, "-", GRAY, () -> { cfg.payoutWindowSeconds = Math.max(5, cfg.payoutWindowSeconds - 5); changed(); });
        txtC(c, String.valueOf(cfg.payoutWindowSeconds), X + 62, 242, TEXT);
        btn(c, X + 95, 238, 20, 16, "+", GRAY, () -> { cfg.payoutWindowSeconds = Math.min(300, cfg.payoutWindowSeconds + 5); changed(); });
        txt(c, "A payout to the same player within", X + 10, 260, GRAY);
        txt(c, "this time belongs to their bet.", X + 10, 271, GRAY);

        // right column: toggles
        int rx = X + 176;
        txt(c, "OPTIONS", rx, 77, GOLD);
        btn(c, rx, 88, 146, 18, cfg.isHost() ? "Role: HOST (you take bets)" : "Role: PLAYER (you bet)", 0xFF2C3566, () -> {
            cfg.role = cfg.isHost() ? "PLAYER" : "HOST";
            changed();
        });
        int y = 112;
        toggle(c, rx, y, "Track payments", cfg.trackingEnabled, () -> { cfg.trackingEnabled = !cfg.trackingEnabled; changed(); });
        toggle(c, rx, y + 20, "Auto-start sessions", cfg.autoStartSession, () -> { cfg.autoStartSession = !cfg.autoStartSession; changed(); });
        toggle(c, rx, y + 40, "Pop-up notifications", cfg.enabled, () -> { cfg.enabled = !cfg.enabled; changed(); });
        toggle(c, rx, y + 60, "Alert on flagged bets", cfg.flagAlerts, () -> { cfg.flagAlerts = !cfg.flagAlerts; changed(); });
        toggle(c, rx, y + 80, "Corner HUD (top right)", cfg.showCornerHud, () -> { cfg.showCornerHud = !cfg.showCornerHud; changed(); });
        toggle(c, rx, y + 100, "Bar above hotbar", cfg.showHotbarBar, () -> { cfg.showHotbarBar = !cfg.showHotbarBar; changed(); });
        toggle(c, rx, y + 120, "Sounds", cfg.playSound, () -> { cfg.playSound = !cfg.playSound; changed(); });
        toggle(c, rx, y + 140, "Chat pay buttons", cfg.chatButtons, () -> { cfg.chatButtons = !cfg.chatButtons; changed(); });
        toggle(c, rx, y + 160, "Click = send now", cfg.buttonsRunCommand, () -> { cfg.buttonsRunCommand = !cfg.buttonsRunCommand; changed(); });
        txt(c, "Amounts: 250k, 5M, 1B, 1T + Enter", X + 10, 283, GRAY);
    }

    private void labelField(DrawContext c, String label, Field f, int x, int y, int w) {
        txt(c, label, x, y, MUTED);
        boolean active = focus == f;
        if (!active) f.text = f.source.get();
        rrect(c, x, y + 11, w, 16, active ? 0xFF0A0C14 : 0xFF12141F);
        outline(c, x, y + 11, x + w, y + 27, active ? GOLD : LINE);
        String shown = f.text;
        if (active && shown.isEmpty()) shown = "";
        txt(c, trim(shown, w - 12), x + 5, y + 15, TEXT);
        if (active) {
            if ((System.currentTimeMillis() / 450) % 2 == 0) {
                int cx = x + 5 + tr.getWidth(trim(shown, w - 12));
                c.fill(cx, y + 14, cx + 1, y + 24, GOLD);
            }
        }
        hit(x, y + 11, w, 16, () -> {
            focus = f;
            if (f.numeric) f.text = "";
        });
    }

    private void toggle(DrawContext c, int x, int y, String label, boolean on, Runnable click) {
        boolean hov = inside(x, y, 146, 16);
        if (hov) rrect(c, x - 2, y - 1, 150, 16, CARD2);
        txt(c, label, x + 2, y + 3, on ? TEXT : MUTED);
        int sx = x + 120;
        rrect(c, sx, y + 2, 24, 11, on ? 0xFF1F8F52 : GRAY);
        int knob = on ? sx + 14 : sx + 1;
        rrect(c, knob, y + 3, 9, 9, 0xFFFFFFFF);
        hit(x - 2, y - 1, 150, 16, click);
    }

    // ---------------------------------------------------------------- helpers

    private void hit(int x, int y, int w, int h, Runnable r) {
        hits.add(new Hit(x, y, x + w, y + h, r));
    }

    private boolean inside(int x, int y, int w, int h) {
        return vmx >= x && vmx < x + w && vmy >= y && vmy < y + h;
    }

    private void btn(DrawContext c, int x, int y, int w, int h, String label, int color, Runnable r) {
        boolean hov = inside(x, y, w, h);
        rrect(c, x, y, w, h, hov ? lighten(color) : color);
        c.fill(x + 1, y, x + w - 1, y + 1, 0x33FFFFFF);
        txtCS(c, label, x + w / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF);
        hit(x, y, w, h, r);
    }

    private void btnOff(DrawContext c, int x, int y, int w, int h, String label) {
        rrect(c, x, y, w, h, 0xFF14172A);
        txtC(c, label, x + w / 2, y + (h - 8) / 2 + 1, GRAY);
    }

    private void rowBg(DrawContext c, int x, int y, int w, int h, boolean selected, boolean hover) {
        if (selected) {
            rrect(c, x, y, w, h, CARD2);
            c.fill(x, y + 2, x + 2, y + h - 2, GOLD);
        } else if (hover) {
            rrect(c, x, y, w, h, 0xFF20253C);
        }
    }

    private void card(DrawContext c, int x, int y, int w, int h, int accent) {
        rrect(c, x, y, w, h, CARD);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, a(26, accent), 0x00000000);
        c.fill(x + 3, y, x + w - 3, y + 2, a(230, accent));
    }

    private void chip(DrawContext c, int x, int y, int w, String label, int color) {
        rrect(c, x, y, w, 12, a(60, color));
        txtC(c, label, x + w / 2, y + 2, color);
    }

    private void scrollbar(DrawContext c, int x, int y, int h, int total, int visible, int scroll) {
        if (total <= visible) return;
        int barH = Math.max(12, h * visible / total);
        int barY = y + (h - barH) * scroll / Math.max(1, total - visible);
        c.fill(x - 2, y, x + 1, y + h, 0xFF12141F);
        c.fill(x - 2, barY, x + 1, barY + barH, GRAY);
    }

    private static int tagColor(String tag) {
        return switch (tag) {
            case "BET" -> GOLD;
            case "PAYOUT" -> BLUE;
            case "UNDER" -> ORANGE;
            case "OVER" -> RED;
            default -> GREEN;
        };
    }

    private static int colorFor(double v) {
        return v > 0 ? GREEN : (v < 0 ? RED : TEXT);
    }

    private static int lighten(int col) {
        int r = Math.min(255, ((col >> 16) & 0xFF) + 28);
        int g = Math.min(255, ((col >> 8) & 0xFF) + 28);
        int b = Math.min(255, (col & 0xFF) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int a(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private String trim(String s, int maxW) {
        if (s == null) return "";
        if (tr.getWidth(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && tr.getWidth(t + "..") > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    private static void rrect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    private static void outline(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1, y1, x2, y1 + 1, col);
        c.fill(x1, y2 - 1, x2, y2, col);
        c.fill(x1, y1, x1 + 1, y2, col);
        c.fill(x2 - 1, y1, x2, y2, col);
    }

    private void txt(DrawContext c, String s, int x, int y, int col) { c.drawText(tr, s, x, y, col, false); }

    private void txtS(DrawContext c, String s, int x, int y, int col) { c.drawText(tr, s, x, y, col, true); }

    private void txtR(DrawContext c, String s, int rightX, int y, int col) {
        c.drawText(tr, s, rightX - tr.getWidth(s), y, col, false);
    }

    private void txtC(DrawContext c, String s, int cx, int y, int col) {
        c.drawText(tr, s, cx - tr.getWidth(s) / 2, y, col, false);
    }

    private void txtCS(DrawContext c, String s, int cx, int y, int col) {
        c.drawText(tr, s, cx - tr.getWidth(s) / 2, y, col, true);
    }

    private void big(DrawContext c, String s, int x, int y, int col, float scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr, s, 0, 0, col, true);
        m.popMatrix();
    }
}
